package com.seeyou.see_you.evaluation;

import com.seeyou.see_you.algorithm.BehaviorPredictor;
import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.entity.BehaviorFeature;
import com.seeyou.see_you.entity.RiskAlert;
import com.seeyou.see_you.service.RiskAlertService;
import com.seeyou.see_you.service.SenseService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 无感记录链路端到端验证：行为特征 -> 情绪推测 -> 风险预警。
 *
 * <p><b>数据依赖</b>：需要 {@code psy_behavior_feature} 中有数据。
 * 演示环境执行 {@code sql/migration/20261028_seed_sense_demo.sql} 生成 60 天合成特征；
 * 若表为空（未播种或答辩后已清理），测试自动跳过而不是误报失败。
 *
 * <p><b>测试口径说明</b>（重要）：合成特征与真实日记情绪之间存在构造性关联，
 * 因此本测试只验证「链路可跑通、输出结构正确、低置信度时拒绝推荐」，
 * <b>不验证准确率</b>。准确率需待真实埋点采集后用 {@link ModelEvaluationTest} 重测。
 */
@SpringBootTest
@DisplayName("无感记录链路端到端验证")
class SensePipelineTest {

    /** 播种脚本默认写入的用户（test） */
    private static final long DEMO_USER = 1L;

    @Resource private SenseService senseService;
    @Resource private RiskAlertService riskAlertService;
    @Resource private JdbcTemplate jdbc;

    private List<BehaviorFeature> features(LocalDate from, LocalDate to) {
        return senseService.myBehavior(DEMO_USER, from, to);
    }

    private Map<LocalDate, Integer> loadLabels(LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> labels = new LinkedHashMap<>();
        jdbc.query("SELECT diary_date, MAX(emotion) e FROM psy_diary "
                        + "WHERE user_id=? AND source=1 AND diary_date BETWEEN ? AND ? GROUP BY diary_date",
                rs -> {
                    while (rs.next()) labels.put(rs.getDate(1).toLocalDate(), rs.getInt(2));
                    return null;
                },
                DEMO_USER, from.toString(), to.toString());
        return labels;
    }

    @Test
    @DisplayName("链路一：行为特征可读取，覆盖多天多场景，且不含任何文本字段")
    void behaviorReadable() {
        LocalDate today = LocalDate.now();
        List<BehaviorFeature> list = features(today.minusDays(60), today);
        Assumptions.assumeFalse(list.isEmpty(), "行为特征表为空，跳过（先执行播种脚本）");

        long days = list.stream().map(BehaviorFeature::getStatDate).distinct().count();
        long scenes = list.stream().map(BehaviorFeature::getSceneCode).distinct().count();
        System.out.printf("[SENSE] feature rows=%d days=%d scenes=%d%n", list.size(), days, scenes);

        assertTrue(days >= 7, "演示至少应覆盖一周以上行为");
        assertTrue(scenes >= 5, "演示至少应覆盖 5 个场景");
        // 隐私红线：行为数据只存聚合数值，不得携带任何自由文本
        assertTrue(list.stream().allMatch(f -> f.getSceneCode() != null && f.getSceneCode().length() <= 32),
                "场景码应为受约束的枚举值，而非自由文本");
    }

    @Test
    @DisplayName("链路二：KNN 通路可产出合法的概率分布")
    void knnProducesDistribution() {
        LocalDate today = LocalDate.now();
        List<BehaviorFeature> list = features(today.minusDays(90), today);
        Assumptions.assumeFalse(list.isEmpty(), "行为特征表为空，跳过");

        Map<LocalDate, double[]> vectors =
                BehaviorPredictor.vectorize(BehaviorPredictor.aggregateByDate(list));
        Map<LocalDate, Integer> labels = loadLabels(today.minusDays(90), today);

        double[] target = vectors.get(today);
        Assumptions.assumeTrue(target != null, "今日无行为特征，跳过");

        Map<Integer, Double> dist = BehaviorPredictor.knn(vectors, labels, target, 5);
        assertNotNull(dist, "KNN 应产出分布（需至少 2 天有标签的历史）");
        assertFalse(dist.isEmpty(), "KNN 分布不应为空");

        double sum = dist.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "KNN 输出应为归一化概率分布");
        int top = dist.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(-1);
        assertTrue(top >= 1 && top <= 9, "情绪编号应在 1~9，实际=" + top);
        assertNotNull(EmotionConst.nameOf(top), "情绪编号应能在情绪字典中查到名称");
        System.out.printf("[SENSE] knn top1=%s(%s) p=%.3f%n",
                top, EmotionConst.nameOf(top), dist.get(top));
    }

    @Test
    @DisplayName("链路三：推测接口结构正确；给出建议时置信度必须过阈值，否则必须给出人话解释")
    void generateReturnsValidStructure() {
        Assumptions.assumeFalse(features(LocalDate.now().minusDays(30), LocalDate.now()).isEmpty(),
                "行为特征表为空，跳过");

        Map<String, Object> res = senseService.generate(DEMO_USER, 1, "今天好累，什么都不想干，有点难过");
        assertNotNull(res, "推测结果不应为空");
        System.out.println("[SENSE] generate -> hasSuggestion=" + res.get("hasSuggestion")
                + " conf=" + res.get("confidence") + " emotion=" + res.get("emotion"));

        if (Boolean.FALSE.equals(res.get("hasSuggestion"))) {
            // 设计意图：孤证不立、或通路互相冲突时，宁可留空也不误导 —— 但必须给出解释，不能静默失败
            assertNotNull(res.get("reason"), "未给出建议时必须附带解释文案");
            assertFalse(String.valueOf(res.get("reason")).isBlank(), "解释文案不得为空");
        } else {
            Object conf = res.get("confidence");
            assertNotNull(conf, "给出建议时必须带置信度");
            double c = ((Number) conf).doubleValue();
            assertTrue(c >= 0.45, "对外推荐必须满足 min-confidence=0.45，实际=" + c);
            Object emo = res.get("emotion");
            assertNotNull(emo, "给出建议时必须带情绪编号");
            assertTrue(((Number) emo).intValue() >= 1 && ((Number) emo).intValue() <= 9,
                    "情绪编号应在 1~9");
        }
    }

    @Test
    @DisplayName("链路三·补充（演示动线）：当三通路指向一致时应给出建议，且置信度过阈值")
    void demoPathSuggests() {
        LocalDate today = LocalDate.now();
        List<BehaviorFeature> list = features(today.minusDays(90), today);
        Assumptions.assumeFalse(list.isEmpty(), "行为特征表为空，跳过");

        // 1) 先算出行为通路认为今天像哪种情绪
        Map<LocalDate, double[]> vectors =
                BehaviorPredictor.vectorize(BehaviorPredictor.aggregateByDate(list));
        Map<LocalDate, Integer> labels = loadLabels(today.minusDays(90), today);
        double[] target = vectors.get(today);
        Assumptions.assumeTrue(target != null, "今日无行为特征，跳过");
        Map<Integer, Double> dist = BehaviorPredictor.knn(vectors, labels, target, 5);
        int behTop = dist.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(-1);

        // 2) 取该情绪在词典里的一个代表词作为日记文本 —— 这正是真实演示动线：
        //    用户在写日记时写下的内容与当天状态一致，三通路自然同向
        String word = jdbc.queryForObject(
                "SELECT word FROM psy_emotion_lexicon WHERE emotion=? ORDER BY weight DESC LIMIT 1",
                String.class, behTop);
        Assumptions.assumeTrue(word != null, "词典中没有该情绪的词，跳过");

        Map<String, Object> res = senseService.generate(DEMO_USER, 1, "今天感觉" + word);
        System.out.println("[SENSE] demoPath behTop=" + EmotionConst.nameOf(behTop)
                + " text=今天感觉" + word
                + " -> hasSuggestion=" + res.get("hasSuggestion") + " conf=" + res.get("confidence"));

        assertTrue(!Boolean.FALSE.equals(res.get("hasSuggestion")),
                "三通路同向时应给出建议，实际=" + res);
        double c = ((Number) res.get("confidence")).doubleValue();
        assertTrue(c >= 0.45, "三通路同向时置信度应过 0.45 阈值，实际=" + c);
    }

    @Test
    @DisplayName("链路四：风险预警可判定，等级 1~3、可回溯触发条件、守住非医疗红线")
    void riskEvaluates() {
        RiskAlert alert = riskAlertService.evaluate(DEMO_USER);
        // 未触发也是正常结果（规则未命中），只验证不抛异常且结构正确
        if (alert == null) {
            System.out.println("[SENSE] risk not triggered（正常：规则未命中）");
            return;
        }
        System.out.printf("[SENSE] risk triggered level=%s rule=%s downcastDays=%s%n",
                alert.getAlertLevel(), alert.getTriggerRule(), alert.getMaxDowncastDays());

        assertNotNull(alert.getAlertLevel());
        assertTrue(alert.getAlertLevel() >= 1 && alert.getAlertLevel() <= 3, "风险等级应在 1~3");
        // 非医疗边界：文案必须是提示性措辞，不能出现结论性医疗表述
        String advice = alert.getAdvice() == null ? "" : alert.getAdvice();
        assertFalse(advice.contains("诊断") || advice.contains("患病") || advice.contains("确诊"),
                "预警文案不得出现医疗结论性表述（非医疗红线），实际=" + advice);
        // 可解释性：必须能回溯触发条件
        assertNotNull(alert.getTriggerRule(), "预警必须能回溯触发规则");
        assertNotNull(alert.getMaxDowncastDays(), "预警应给出窗口内低落天数等判定依据");
    }

    @Test
    @DisplayName("诊断：打印特征向量与三通路分布（排查演示数据可用性用，不做断言）")
    void diagnose() {
        LocalDate today = LocalDate.now();
        List<BehaviorFeature> list = features(today.minusDays(90), today);
        if (list.isEmpty()) {
            System.out.println("[SENSE-DIAG] no behavior data at all");
            return;
        }
        Map<LocalDate, double[]> vectors =
                BehaviorPredictor.vectorize(BehaviorPredictor.aggregateByDate(list));
        System.out.println("[SENSE-DIAG] rows=" + list.size() + " days=" + vectors.size()
                + " hasTodayVec=" + (vectors.get(today) != null));
        vectors.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .limit(3)
                .forEach(e -> System.out.println("[SENSE-DIAG] " + e.getKey() + " -> "
                        + Arrays.toString(e.getValue())));

        Map<LocalDate, Integer> labels = loadLabels(today.minusDays(90), today);
        System.out.println("[SENSE-DIAG] labeledDays=" + labels.size() + " todayLabel=" + labels.get(today));

        double[] tv = vectors.get(today);
        if (tv != null) {
            System.out.println("[SENSE-DIAG] knn=" + BehaviorPredictor.knn(vectors, labels, tv, 5));
            List<Double> series = new ArrayList<>();
            labels.entrySet().stream()
                    .filter(e -> !e.getKey().isAfter(today.minusDays(1)))
                    .sorted(Map.Entry.comparingByKey())
                    .limit(30)
                    .forEach(e -> series.add((double) EmotionConst.indexOf(e.getValue())));
            Double ar1 = BehaviorPredictor.ar1Predict(series);
            System.out.println("[SENSE-DIAG] ar1=" + ar1 + " dist="
                    + (ar1 == null ? null : BehaviorPredictor.indexToDistribution(ar1)));
        }
    }
}
