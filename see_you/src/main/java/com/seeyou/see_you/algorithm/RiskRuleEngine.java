package com.seeyou.see_you.algorithm;

import com.seeyou.see_you.config.RiskProperties;

import java.time.LocalDate;
import java.util.*;

/**
 * 情绪风险判定引擎（**统计学规则，不是医疗诊断**）。
 *
 * <h3>判定逻辑（窗口 N 天，默认 7 天）</h3>
 * <pre>
 *   窗口内每日情绪 →
 *     ① 有记录天数 recordDays &lt; minRecordDays     → 数据不足，不判定
 *     ② 低落天数 downcastDays ≥ level3DowncastDays  → 三级（高度关注）
 *        或 负面占比 ≥ 75% 且平均指数 ≤ 35          → 三级
 *     ③ downcastDays ≥ level2DowncastDays           → 二级（中度提醒）
 *     ④ 负面占比 ≥ 50% 且指数斜率持续下滑            → 一级（轻度关注）
 * </pre>
 *
 * <h3>为什么判据是「窗口内低落天数」而不是「最长连续低落天数」</h3>
 * 早期实现写的是「最长连续低落」，但情绪日记天然存在漏记：用户可能连续几天
 * 状态都不好，却只写了 2 篇。此时有两个选择，两个都不成立：
 * <ul>
 *   <li><b>严格要求日期相邻</b>：真实数据里「连续 5 天低落」几乎不可能出现，
 *       预警会长期不触发，属于漏报；</li>
 *   <li><b>允许跨越缺失日累加</b>：那它算出来的已经不是「连续」，
 *       而旧实现（{@code run = prev == null ? 1 : run + 1}）正是这种——
 *       变量 {@code prev} 赋值后从未参与判断，实际语义是「永不打断」，
 *       却对用户输出「你已经连续 N 天情绪低落」。同一个词在两处含义不同，
 *       无法向用户解释，且构成对用户的事实性错误陈述。</li>
 * </ul>
 * 故统一改为<b>窗口内低落天数</b>：语义单一、可直接复现、与 negativeRatio 同源
 * （两者都是「窗口内计数」，只是情绪集合不同）。
 *
 * <p>两个集合的区别（见 {@link EmotionConst}）：
 * <ul>
 *   <li>{@code DOWNCAST} = 疲惫 / 难过 / 焦虑 → 判定「低落」，
 *       对应 PHQ-9 的情绪低落 / 精力减退 / 精神性焦虑三个维度；</li>
 *   <li>{@code NEGATIVE} = 疲惫 / 难过 / 生气 / 焦虑 → 判定「负面占比」，
 *       生气是外化的愤怒，不计入「低落」。</li>
 * </ul>
 *
 * <h3>非医疗边界</h3>
 * 本类只输出等级与关怀文案，绝不输出「抑郁症」「患病」之类的结论性判断。
 */
public final class RiskRuleEngine {

    private RiskRuleEngine() {}

    /** 窗口统计 */
    public record WindowStats(LocalDate date,
                              int recordDays,
                              int negativeDays,
                              int downcastDays,
                              double negativeRatio,
                              double avgIndex,
                              double slope,
                              List<Double> indexSeries) {}

    /** 判定结果：level=0 表示未触发 */
    public record Verdict(int level, String rule, String detail, String advice) {}

    /** 未触发时的空结果 */
    public static final Verdict NONE = new Verdict(0, null, null, null);

    /**
     * 从「日期 -> 情绪」映射计算窗口统计。
     *
     * @param emotions 窗口内的情绪；value 可为 null（当天没记录）
     */
    public static WindowStats stats(LocalDate date, Map<LocalDate, Integer> emotions) {
        int recordDays = 0, negativeDays = 0, downcastDays = 0;
        List<Double> series = new ArrayList<>();
        double sum = 0;

        // 按日期升序：slope 依赖顺序，不能依赖 Map 的遍历顺序
        List<LocalDate> sorted = emotions.keySet().stream().sorted().toList();
        for (LocalDate d : sorted) {
            Integer e = emotions.get(d);
            if (e == null) continue;   // 缺失日不参与计数（既不算低落，也不算正常）
            recordDays++;
            int idx = EmotionConst.indexOf(e);
            sum += idx;
            series.add((double) idx);
            if (EmotionConst.isNegative(e)) negativeDays++;
            if (EmotionConst.isDowncast(e)) downcastDays++;
        }
        double avg = recordDays == 0 ? 0 : sum / recordDays;
        double ratio = recordDays == 0 ? 0 : negativeDays * 1.0 / recordDays;
        return new WindowStats(date, recordDays, negativeDays, downcastDays,
                ratio, avg, BehaviorPredictor.slope(series), series);
    }

    /** 按配置判定等级 */
    public static Verdict judge(WindowStats s, RiskProperties p) {
        if (s.recordDays() < p.getMinRecordDays()) return NONE;

        // 三级：高度关注 —— 低落天数达标，或整体状态已经很差
        boolean strongDowncast = s.downcastDays() >= p.getLevel3DowncastDays();
        boolean strongOverall = s.negativeRatio() >= p.getLevel3Ratio()
                && s.avgIndex() <= p.getLevel3AvgIndex();
        if (strongDowncast || strongOverall) {
            return new Verdict(3, strongDowncast ? "L3_DOWNCAST_DAYS" : "L3_OVERALL",
                    detail(s, p), advice(3, s, p));
        }

        // 二级：中度提醒
        if (s.downcastDays() >= p.getLevel2DowncastDays()) {
            return new Verdict(2, "L2_DOWNCAST_DAYS", detail(s, p), advice(2, s, p));
        }

        // 一级：轻度关注（要求明确的下滑趋势，且可选要求低落天数达标）
        boolean multiDayOk = !p.isRequireMultiDay() || s.downcastDays() >= 2;
        if (s.negativeRatio() >= p.getLevel1Ratio() && s.slope() <= p.getLevel1Slope() && multiDayOk) {
            return new Verdict(1, "L1_TREND", detail(s, p), advice(1, s, p));
        }
        return NONE;
    }

    /** 给用户看的判定依据（把统计翻译成人话，窗口天数取自配置而非硬编码） */
    private static String detail(WindowStats s, RiskProperties p) {
        return String.format(
                "最近 %d 天里有记录 %d 天，其中低落状态 %d 天、负面情绪 %d 天（占比 %.0f%%），"
                        + "平均情绪指数 %.1f，趋势斜率 %+.2f。",
                p.getWindowDays(), s.recordDays(), s.downcastDays(), s.negativeDays(),
                s.negativeRatio() * 100, s.avgIndex(), s.slope());
    }

    // ==================== 关怀文案（分级） ====================
    // 设计原则：
    //   1. 永远是"我注意到"，不是"你有病"；
    //   2. 必给可执行的自我调节建议（不是空话）；
    //   3. 必给求助渠道，但不能写成"你该去看医生"这种诊断口吻；
    //   4. 必带免责声明；
    //   5. 陈述的事实必须与判据一致 —— 判据是"窗口内低落天数"，文案就不许说"连续"。

    public static String advice(int level, WindowStats s, RiskProperties p) {
        StringBuilder sb = new StringBuilder();
        switch (level) {
            case 3 -> sb.append("绪见注意到，最近 ").append(p.getWindowDays())
                    .append(" 天里，有 ").append(s.downcastDays())
                    .append(" 天你记录到的情绪偏低落。这段时间一定很不容易，请先别责怪自己。\n\n")
                    .append("也许可以试试：\n")
                    .append("· 今天就只做一件小事 —— 吃饭、晒十分钟太阳、或者好好睡一觉；\n")
                    .append("· 把心里那句话写进日记里，不用在乎通不通顺；\n")
                    .append("· 找一位信任的人说说话，哪怕只是「我最近不太好」这一句。\n\n")
                    .append("如果这种状态已经影响到你的睡眠、饮食或日常作息，")
                    .append("建议寻求专业心理咨询的帮助 —— ")
                    .append("学校心理咨询中心、当地精神卫生中心，或全国 24 小时心理援助热线都是可以联系的资源。\n\n");
            case 2 -> sb.append("绪见发现最近 ").append(p.getWindowDays())
                    .append(" 天里，有 ").append(s.downcastDays())
                    .append(" 天的情绪偏低落。累了就允许自己慢下来，不用一直撑着。\n\n")
                    .append("也许可以试试：\n")
                    .append("· 今晚提前一小时放下手机，给自己一段完整的休息；\n")
                    .append("· 听一期「绪见FM」里舒缓的音频，或做几分钟腹式呼吸；\n")
                    .append("· 记录一件今天还算顺利的小事，哪怕很小。\n\n")
                    .append("如果之后几天仍然没有好转，可以考虑和专业心理咨询师聊聊。\n\n");
            case 1 -> sb.append("最近这段时间的情绪记录里，低落的日子比平时多了一些。\n\n")
                    .append("也许可以试试：\n")
                    .append("· 给自己安排一件期待中的小事；\n")
                    .append("· 出门走十五分钟，换个环境常常会有新感受；\n")
                    .append("· 继续记录情绪，这些记录本身就是照顾自己的证据。\n\n");
            default -> sb.append("继续保持记录，这是照顾自己的好习惯。\n\n");
        }
        sb.append("【重要】").append(p.getDisclaimer());
        return sb.toString();
    }

    /** 等级的名称（接口返回给前端展示） */
    public static String levelName(int level) {
        return switch (level) {
            case 3 -> "高度关注";
            case 2 -> "中度提醒";
            case 1 -> "轻度关注";
            default -> "正常";
        };
    }

    /**
     * 触发规则的中文名。
     *
     * <p>数据库存的是英文枚举（稳定、可检索），但<strong>任何面向人展示的地方都不许直接吐枚举名</strong>——
     * 管理员看到 {@code L3_DOWNCAST_DAYS} 不知道是什么。接口统一多带一个 triggerRuleName 字段，
     * 前端展示用 name，检索/存储用原值。</p>
     */
    public static String ruleName(String rule) {
        if (rule == null || rule.isBlank()) return null;
        return switch (rule) {
            case "CRISIS_KEYWORD"     -> "危机词命中（即时关怀）";
            case "AI_JUDGE"           -> "AI 情绪研判（辅助判断）";
            case "WARNING_WORD"       -> "预警词命中（中度提醒）";
            case "L3_DOWNCAST_DAYS"   -> "低落天数达到三级阈值";
            // 旧版规则枚举（改名前落库的历史数据），映射到现规则的中文，避免老记录显示英文原值
            case "L3_CONTINUOUS"      -> "连续低落天数达到三级阈值";
            case "L3_OVERALL"         -> "整体负面程度达到三级阈值";
            case "L2_DOWNCAST_DAYS"   -> "低落天数达到二级阈值";
            case "L1_TREND"           -> "负面占比偏高且情绪持续下滑";
            default -> rule;   // 未知规则兜底：宁可显示原值也不显示空白
        };
    }
}
