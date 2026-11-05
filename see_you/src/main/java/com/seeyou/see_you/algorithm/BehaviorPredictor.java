package com.seeyou.see_you.algorithm;

import com.seeyou.see_you.entity.BehaviorFeature;

import java.time.LocalDate;
import java.util.*;

/**
 * 轻量情绪推测算法集合：**加权 K 近邻 + 一阶自回归 + 自适应权重融合**。
 *
 * <h3>为什么是这三个而不是深度学习？</h3>
 * 1. 无训练过程：KNN 直接用本人历史当样本，换一个用户即时生效，不需要离线训练；
 * 2. 可解释：KNN 能指出「你和哪一天最像」，AR(1) 能给出斜率——两者都能翻译成人话；
 * 3. 算力友好：单次推理是几十次向量距离计算，实测 < 5ms，单机即可。
 *
 * <h3>7 维行为特征（每日一条）</h3>
 * x1 活跃强度 / x2 操作密度 / x3 沉浸度 / x4 夜间占比 / x5 日记场景占比 / x6 FM 场景占比 / x7 打开频次
 *
 * <h3>防自证循环</h3>
 * 传入的历史标签**只能来自 source=1 的手动记录**（由调用方保证）：
 * 若把系统推测出来的数据再喂回去训练推测模型，误差会被不断放大。
 *
 * 本类所有方法都是纯函数，便于单元测试直接调用（参考 StatisticsController 的同样约定）。
 */
public final class BehaviorPredictor {

    /** 特征维度 */
    public static final int DIM = 7;
    /** KNN 邻居数 */
    public static final int DEFAULT_K = 5;
    /** 夜间时段掩码：22:00 ~ 次日 05:59 */
    public static final long NIGHT_MASK = (0b11L << 22) | 0b111111L;

    private BehaviorPredictor() {}

    /** 单日行为聚合结果 */
    public record DayAgg(double stayMs, double clicks, int visits, long hourMask, Map<String, Double> stayByScene) {}

    /** 融合结果 */
    public record FuseResult(
            Integer bestEmotion,
            double bestP,
            /** 最终的九类概率分布 */
            Map<Integer, Double> distribution,
            /** 各通路实际使用的权重 */
            Map<String, Double> weights,
            /** 参与融合的通路数 */
            int roads,
            /** 融合后的置信度 */
            double confidence
    ) {}

    // ============ 1. 聚合：特征行 -> 按天 ============

    @SuppressWarnings("unchecked")
    public static Map<LocalDate, DayAgg> aggregateByDate(List<BehaviorFeature> list) {
        // 用可变数组暂存，避免 record 不可变导致的反复重建
        Map<LocalDate, double[]> num = new LinkedHashMap<>();   // {stay, clicks, visits}
        Map<LocalDate, Long> masks = new HashMap<>();
        Map<LocalDate, Map<String, Double>> scenes = new HashMap<>();

        for (BehaviorFeature f : list) {
            if (f.getStatDate() == null) continue;
            LocalDate d = f.getStatDate();
            double[] cur = num.computeIfAbsent(d, k -> new double[3]);
            cur[0] += nz(f.getStayMs());
            cur[1] += nz(f.getClickCnt());
            cur[2] += nz(f.getVisitCnt());
            masks.merge(d, safeMask(f.getHourMask()), (a, b) -> a | b);
            scenes.computeIfAbsent(d, k -> new HashMap<>())
                  .merge(scene(f.getSceneCode()), nz(f.getStayMs()), Double::sum);
        }

        Map<LocalDate, DayAgg> out = new LinkedHashMap<>();
        for (LocalDate d : num.keySet()) {
            double[] v = num.get(d);
            out.put(d, new DayAgg(v[0], v[1], (int) v[2], masks.getOrDefault(d, 0L),
                    scenes.getOrDefault(d, Map.of())));
        }
        return out;
    }

    // ============ 2. 向量化 ============

    /**
     * 把每天的聚合转成 7 维特征向量。
     * x1/x2/x7 用「当日值 / 本人近期均值」表达相对波动，避免不同用户使用习惯差异导致的不可比。
     */
    public static Map<LocalDate, double[]> vectorize(Map<LocalDate, DayAgg> agg) {
        double meanStay = agg.values().stream().mapToDouble(DayAgg::stayMs).average().orElse(0);
        double meanClick = agg.values().stream().mapToDouble(DayAgg::clicks).average().orElse(0);
        double meanVisit = agg.values().stream().mapToDouble(DayAgg::visits).average().orElse(0);
        if (meanStay <= 0) meanStay = 1;
        if (meanClick <= 0) meanClick = 1;
        if (meanVisit <= 0) meanVisit = 1;

        Map<LocalDate, double[]> out = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, DayAgg> e : agg.entrySet()) {
            DayAgg a = e.getValue();
            double total = a.stayMs() <= 0 ? 1 : a.stayMs();
            int activeHours = Math.max(1, Long.bitCount(a.hourMask()));
            int nightHours = Math.max(1, Long.bitCount(a.hourMask() & NIGHT_MASK));
            double[] v = new double[DIM];
            v[0] = a.stayMs() / meanStay;                                  // x1 活跃强度
            v[1] = a.clicks() / meanClick;                                 // x2 操作密度
            v[2] = Math.min(3.0, a.stayMs() / (activeHours * 3_600_000.0)); // x3 沉浸度 0~3
            v[3] = nightHours * 1.0 / activeHours;                          // x4 夜间占比 0~1
            v[4] = a.stayByScene().getOrDefault("diary", 0.0) / total;      // x5 日记场景占比
            v[5] = a.stayByScene().getOrDefault("fm", 0.0) / total;         // x6 FM 场景占比
            v[6] = a.visits() / meanVisit;                                  // x7 打开频次
            out.put(e.getKey(), v);
        }
        return out;
    }

    // ============ 3. 加权 K 近邻 ============

    public static Map<Integer, Double> knn(Map<LocalDate, double[]> vectors,
                                    Map<LocalDate, Integer> labels,
                                    double[] target, int k) {
        return knn(vectors, labels, target, k, null);
    }

    /**
     * @param vectors 历史每天的特征向量
     * @param labels  历史每天的真实情绪（**只应为手动记录**）
     * @param target  今日向量
     * @param k       邻居数
     * @param exclude 需要从邻居里排除的日期（通常是目标日自身）
     * @return 九类情绪的概率分布；样本不足返回 null
     */
    public static Map<Integer, Double> knn(Map<LocalDate, double[]> vectors,
                                    Map<LocalDate, Integer> labels,
                                    double[] target, int k,
                                    LocalDate exclude) {
        if (target == null || target.length != DIM) return null;

        List<double[]> distLabel = new ArrayList<>();
        for (Map.Entry<LocalDate, double[]> e : vectors.entrySet()) {
            // 只保留有真实情绪标签的历史日
            Integer label = labels.get(e.getKey());
            if (label == null) continue;
            // ⚠️ 必须排除目标日自身：用户若已写过今天的日记，labels 里就有今天的真值，
            // 距离会算成 0、权重 1/(0+0.35) 远大于其它邻居，推测退化成「把用户刚填的答案念回去」。
            // 调用方传 exclude=targetDate；为 null 时不做排除（等价旧行为，仅供纯函数测试使用）。
            if (exclude != null && exclude.equals(e.getKey())) continue;
            distLabel.add(new double[]{distance(target, e.getValue()), label});
        }
        if (distLabel.size() < 2) return null;   // 至少需要 2 天有标签的历史

        distLabel.sort(Comparator.comparingDouble(a -> a[0]));
        int take = Math.min(k, distLabel.size());

        Map<Integer, Double> vote = new HashMap<>();
        for (int i = 0; i < take; i++) {
            double d = distLabel.get(i)[0];
            int label = (int) distLabel.get(i)[1];
            double w = 1.0 / (d + 0.35);   // 平滑项避免距离为 0 时权重爆炸（越近权重越大）
            vote.merge(label, w, Double::sum);
        }
        return normalize(vote);
    }

    /**
     * 找出与 target 最相似的那一天（用于给用户解释「你和哪天最像」）。
     */
    public static LocalDate nearest(Map<LocalDate, double[]> vectors, Map<LocalDate, Integer> labels, double[] target) {
        return nearest(vectors, labels, target, null);
    }

    /**
     * @param exclude 需要排除的日期（目标日自身）。否则会返回「今天和今天最像」这种无意义结论。
     */
    public static LocalDate nearest(Map<LocalDate, double[]> vectors, Map<LocalDate, Integer> labels,
                                    double[] target, LocalDate exclude) {
        LocalDate best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<LocalDate, double[]> e : vectors.entrySet()) {
            if (labels.get(e.getKey()) == null) continue;   // 没有情绪标签的历史日不能用于解释
            if (exclude != null && exclude.equals(e.getKey())) continue;  // 目标日自身不参与
            double d = distance(target, e.getValue());
            if (d < bestD) { bestD = d; best = e.getKey(); }
        }
        return best;
    }

    /** 欧氏距离 */
    public static double distance(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < DIM; i++) {
            double diff = a[i] - b[i];
            s += diff * diff;
        }
        return Math.sqrt(s);
    }

    // ============ 4. 一阶自回归 AR(1) ============

    /** 一阶自相关系数 φ（样本 < 4 时返回 0，表示无法估计惯性） */
    public static double phi(List<Double> y) {
        int n = y.size();
        if (n < 4) return 0;
        double mu = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double num = 0, den = 0;
        for (int i = 1; i < n; i++) num += (y.get(i) - mu) * (y.get(i - 1) - mu);
        for (double v : y) den += (v - mu) * (v - mu);
        if (den <= 0) return 0;
        return Math.max(-0.99, Math.min(0.99, num / den));
    }

    /**
     * AR(1) 外推：ŷ(t+1) = μ + φ·(y(t) − μ)
     * @return 预测的情绪指数（0~100）；样本不足返回 null
     */
    public static Double ar1Predict(List<Double> y) {
        int n = y.size();
        if (n < 4) return null;
        double mu = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double p = phi(y);
        double next = mu + p * (y.get(n - 1) - mu);
        return Math.max(0, Math.min(100, next));
    }

    /** 把连续的预测指数转成九类情绪的软分布（按指数距离取倒数权重） */
    public static Map<Integer, Double> indexToDistribution(double predicted) {
        Map<Integer, Double> dist = new LinkedHashMap<>();
        for (int e = 1; e < EmotionConst.INDEX.length; e++) {
            dist.put(e, 1.0 / (1.0 + Math.abs(EmotionConst.INDEX[e] - predicted)));
        }
        return normalize(dist);
    }

    // ============ 5. 最小二乘斜率（趋势用，预警模块也会调用） ============

    /** 对 (x=i, y=series[i]) 做最小二乘，返回斜率 b */
    public static double slope(List<Double> series) {
        int n = series.size();
        if (n < 2) return 0;
        double mx = (n - 1) / 2.0;
        double my = series.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - mx) * (series.get(i) - my);
            den += (i - mx) * (i - mx);
        }
        return den == 0 ? 0 : num / den;
    }

    // ============ 6. 三路融合 ============

    /**
     * 自适应权重：哪一路缺失，就把它的权重按余下通路的比例重新分配。
     * 文本通路权重最高，因为它直接反映用户当下的主观表述。
     */
    public static Map<String, Double> weightsFor(boolean beh, boolean txt, boolean ser) {
        if (beh && txt && ser) return Map.of("behavior", 0.30, "text", 0.45, "series", 0.25);
        if (txt && ser)        return Map.of("text", 0.60, "series", 0.40);
        if (beh && txt)        return Map.of("behavior", 0.40, "text", 0.60);
        if (beh && ser)        return Map.of("behavior", 0.55, "series", 0.45);
        if (txt)               return Map.of("text", 1.0);
        if (beh)               return Map.of("behavior", 1.0);
        if (ser)               return Map.of("series", 1.0);
        return Map.of();
    }

    /**
     * 加权融合并算出置信度。
     * confidence = P(top1) × √(通路数 / 3)
     * —— 参与通路越少，即便 top1 概率很高，置信度也要打折（孤证不立）。
     */
    public static FuseResult fuse(Map<Integer, Double> beh,
                                  Map<Integer, Double> txt,
                                  Map<Integer, Double> ser) {
        boolean hasBeh = beh != null && !beh.isEmpty();
        boolean hasTxt = txt != null && !txt.isEmpty();
        boolean hasSer = ser != null && !ser.isEmpty();
        Map<String, Double> w = weightsFor(hasBeh, hasTxt, hasSer);
        if (w.isEmpty()) return null;

        Map<Integer, Double> merged = new HashMap<>();
        for (int e = 1; e < EmotionConst.INDEX.length; e++) {
            double s = 0;
            if (hasBeh) s += beh.getOrDefault(e, 0.0) * w.getOrDefault("behavior", 0.0);
            if (hasTxt) s += txt.getOrDefault(e, 0.0) * w.getOrDefault("text", 0.0);
            if (hasSer) s += ser.getOrDefault(e, 0.0) * w.getOrDefault("series", 0.0);
            merged.put(e, s);
        }
        int roads = (hasBeh ? 1 : 0) + (hasTxt ? 1 : 0) + (hasSer ? 1 : 0);
        int best = merged.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(9);
        double bestP = merged.get(best);
        double conf = bestP * Math.sqrt(roads / 3.0);
        return new FuseResult(best, bestP, merged, w, roads, Math.max(0, Math.min(1, conf)));
    }

    /** 取概率最高的前 n 个情绪，用于返回 Top3 候选 */
    public static List<Map<String, Object>> topN(Map<Integer, Double> dist, int n) {
        List<Map<String, Object>> out = new ArrayList<>();
        dist.entrySet().stream()
            .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
            .limit(n)
            .forEach(e -> out.add(Map.of(
                    "emotion", e.getKey(),
                    "name", EmotionConst.nameOf(e.getKey()),
                    "p", Math.round(e.getValue() * 1000) / 1000.0)));
        return out;
    }

    // ============ 小工具 ============

    public static Map<Integer, Double> normalize(Map<Integer, Double> m) {
        double total = m.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return Map.of();
        Map<Integer, Double> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, v / total));
        return out;
    }

    private static double nz(Number v) {
        return v == null ? 0 : v.doubleValue();
    }

    /** hour_mask 只允许低 24 位，超出位丢弃（防御非法入参把其它比特写进来） */
    public static long safeMask(Long mask) {
        if (mask == null) return 0L;
        return mask & 0xFFFFFFL;
    }

    private static String scene(String raw) {
        if (raw == null || raw.isBlank()) return "home";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** 允许/支持的场景枚举，非法值统一归并到 home，避免无限膨胀 */
    public static final Set<String> SCENES = Set.of(
            "home", "diary", "write", "fm", "test", "classroom", "profile", "assistant");

    public static String normalizeScene(String raw) {
        String s = scene(raw);
        return SCENES.contains(s) ? s : "home";
    }
}
