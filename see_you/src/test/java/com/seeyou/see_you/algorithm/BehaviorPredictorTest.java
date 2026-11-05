package com.seeyou.see_you.algorithm;

import com.seeyou.see_you.entity.BehaviorFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 情绪推测算法（KNN + AR(1) 融合）的单元测试。
 *
 * <p>算法层全部是静态纯函数（无 IO、无状态），因此可以直接单测，
 * 这也是把算法从 Service 里拆出来的主要收益之一。
 */
class BehaviorPredictorTest {

    // ---------- 工具：造一条行为特征 ----------
    private static BehaviorFeature feat(LocalDate d, String scene, long stayMs, int clicks, int visits, long mask) {
        BehaviorFeature f = new BehaviorFeature();
        f.setUserId(1L);
        f.setStatDate(d);
        f.setSceneCode(scene);
        f.setStayMs(stayMs);
        f.setClickCnt(clicks);
        f.setVisitCnt(visits);
        f.setHourMask(mask);
        return f;
    }

    @Test
    @DisplayName("聚合：同一天多个场景要合并停留时长与小时掩码")
    void aggregateMergesSameDay() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        List<BehaviorFeature> list = List.of(
                feat(d, "diary", 1000L, 10, 1, 0b0011L),
                feat(d, "fm", 2000L, 5, 2, 0b1100L));

        Map<LocalDate, BehaviorPredictor.DayAgg> agg = BehaviorPredictor.aggregateByDate(list);
        assertEquals(1, agg.size());
        BehaviorPredictor.DayAgg a = agg.get(d);
        assertEquals(3000.0, a.stayMs());      // 停留累加
        assertEquals(15.0, a.clicks());        // 点击累加
        assertEquals(3, a.visits());           // 访问累加
        assertEquals(0b1111L, a.hourMask());   // 掩码按位或
        assertEquals(1000.0, a.stayByScene().get("diary"));
        assertEquals(2000.0, a.stayByScene().get("fm"));
    }

    @Test
    @DisplayName("向量化：每天输出 7 维特征，且相对量以本人均值为基准")
    void vectorizeProducesSevenDims() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        Map<LocalDate, double[]> v = BehaviorPredictor.vectorize(BehaviorPredictor.aggregateByDate(List.of(
                feat(d1, "diary", 1000L, 10, 2, 0b1111L),
                feat(d2, "fm", 3000L, 20, 4, 0b1111L))));

        assertEquals(2, v.size());
        for (double[] arr : v.values()) {
            assertEquals(7, arr.length, "特征向量必须是 7 维");
        }
        // 活跃强度是「当日 / 本人均值」，两天均值 2000 → 分别为 0.5 与 1.5
        assertEquals(0.5, v.get(d1)[0], 1e-6);
        assertEquals(1.5, v.get(d2)[0], 1e-6);
    }

    @Test
    @DisplayName("KNN：有标签样本不足 2 天时必须返回 null（宁可不猜）")
    void knnReturnsNullWhenInsufficient() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        Map<LocalDate, double[]> vectors = Map.of(d1, new double[]{1, 1, 1, 0.2, 0.3, 0.1, 1});
        Map<LocalDate, Integer> labels = Map.of(d1, 1);   // 只有 1 天有标签
        assertNull(BehaviorPredictor.knn(vectors, labels, new double[]{1, 1, 1, 0.2, 0.3, 0.1, 1}, 5));

        Map<LocalDate, Integer> empty = Map.of();
        assertNull(BehaviorPredictor.knn(vectors, empty, new double[]{1, 1, 1, 0.2, 0.3, 0.1, 1}, 5));
    }

    @Test
    @DisplayName("KNN：输出是归一化的概率分布，且与最近邻标签一致")
    void knnOutputIsNormalized() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        Map<LocalDate, double[]> vectors = Map.of(
                d1, new double[]{1, 1, 1, 0.2, 0.3, 0.1, 1},
                d2, new double[]{5, 5, 2, 0.8, 0.1, 0.6, 3});
        Map<LocalDate, Integer> labels = Map.of(d1, 1, d2, 4);

        Map<Integer, Double> dist = BehaviorPredictor.knn(vectors, labels,
                new double[]{1, 1, 1, 0.2, 0.3, 0.1, 1}, 5);

        assertNotNull(dist);
        double sum = dist.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "概率分布必须归一化");
        // 目标与 d1 完全相同 → d1 的标签应占绝对优势
        assertEquals(1, argmax(dist));
    }

    @Test
    @DisplayName("KNN：目标日自身不得作为邻居（否则会「猜中」用户刚填的答案）")
    void knnExcludesTargetDayItself() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        LocalDate today = LocalDate.of(2026, 1, 3);

        // 今天与 d2 很近（但不完全相同），与 d1 很远
        double[] todayVec = {5, 5, 2, 0.8, 0.1, 0.6, 3.4};
        Map<LocalDate, double[]> vectors = Map.of(
                d1, new double[]{1, 1, 1, 0.2, 0.3, 0.1, 1},
                d2, new double[]{5, 5, 2, 0.8, 0.1, 0.6, 3},
                today, todayVec);
        // 用户今天已经写过日记 → 今天的真值也在 labels 里（难过=4）
        Map<LocalDate, Integer> labels = Map.of(d1, 1, d2, 7, today, 4);

        // 不排除：距离 0 自匹配，结果被今天自己的标签主导
        Map<Integer, Double> leak = BehaviorPredictor.knn(vectors, labels, todayVec, 5);
        assertEquals(4, argmax(leak), "不排除目标日时确实会退化自匹配（本用例用于说明风险）");

        // 排除后：只剩 d1(1) 与 d2(7) 两个邻居，d2 更近 → 应为 7，且 4 完全不出现在分布里
        Map<Integer, Double> fixed = BehaviorPredictor.knn(vectors, labels, todayVec, 5, today);
        assertNotNull(fixed);
        assertEquals(7, argmax(fixed), "排除目标日后应由真正相似的历史日决定");
        assertFalse(fixed.containsKey(4), "目标日的标签不应参与投票");

        // nearest 同理：不能返回「今天和今天最像」
        assertEquals(d2, BehaviorPredictor.nearest(vectors, labels, todayVec, today));
    }

    @Test
    @DisplayName("AR(1)：常数序列预测值等于该常数；序列过短返回 null")
    void ar1HandlesConstantSeries() {
        assertEquals(70.0, BehaviorPredictor.ar1Predict(List.of(70.0, 70.0, 70.0, 70.0)), 1e-6);
        assertNull(BehaviorPredictor.ar1Predict(List.of()));
        assertNull(BehaviorPredictor.ar1Predict(List.of(70.0)));
    }

    @Test
    @DisplayName("斜率：递增为正、递减为负、持平为零")
    void slopeSign() {
        assertTrue(BehaviorPredictor.slope(List.of(20.0, 40.0, 60.0, 80.0)) > 0);
        assertTrue(BehaviorPredictor.slope(List.of(80.0, 60.0, 40.0, 20.0)) < 0);
        assertEquals(0.0, BehaviorPredictor.slope(List.of(50.0, 50.0, 50.0)), 1e-6);
    }

    @Test
    @DisplayName("融合：通路越少置信度折扣越大（孤证不立）")
    void fuseDiscountsSingleRoad() {
        Map<Integer, Double> beh = Map.of(1, 0.8, 2, 0.2);
        Map<Integer, Double> ser = Map.of(1, 0.6, 3, 0.4);

        BehaviorPredictor.FuseResult single = BehaviorPredictor.fuse(beh, null, null);
        BehaviorPredictor.FuseResult both = BehaviorPredictor.fuse(beh, null, ser);

        assertNotNull(single);
        assertNotNull(both);
        assertEquals(1, single.roads());
        assertEquals(2, both.roads());
        // 单通路置信度 = p * √(1/3) ≈ 0.577p，双通路 = p * √(2/3) ≈ 0.816p
        assertTrue(single.confidence() < both.confidence(),
                "同样高的 top1 概率下，参与通路越少置信度应越低");

        // 三通路权重之和必须为 1
        Map<String, Double> w = BehaviorPredictor.weightsFor(true, true, true);
        assertEquals(1.0, w.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
    }

    @Test
    @DisplayName("融合：全空输入返回 null，调用方需自行兜底")
    void fuseReturnsNullWhenEmpty() {
        assertNull(BehaviorPredictor.fuse(null, null, null));
        assertNull(BehaviorPredictor.fuse(Map.of(), Map.of(), Map.of()));
    }

    @Test
    @DisplayName("情绪指数分布：归一化且覆盖九类")
    void indexToDistributionNormalized() {
        Map<Integer, Double> dist = BehaviorPredictor.indexToDistribution(70.0);
        assertEquals(9, dist.size());
        assertEquals(1.0, dist.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
    }

    private static Integer argmax(Map<Integer, Double> m) {
        return m.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
}
