package com.seeyou.see_you.algorithm;

import java.util.Set;

/**
 * 情绪字典的唯一真源。
 *
 * ⚠️ 前后端一致性：这里的序号、名称、指数必须与前端
 *    Diary.vue / Profile.vue / Main.vue / Assistant.vue 四份 emotions 数组完全同步。
 *    历史上后端曾按 1~5 映射，导致图表与前端错位，故统一收口到此常量类。
 *
 * index 说明：0~100 的「情绪指数」，用于折线图、坡度计算与预警里的平均值统计。
 */
public final class EmotionConst {

    /** 下标即数据库 emotion 值，第 0 位占位 */
    public static final String[] NAMES = {"", "开心", "平静", "疲惫", "难过", "生气", "焦虑", "兴奋", "满足", "其他"};

    /** 情绪指数（0~100）：与前端 scoreMap 一致 */
    public static final int[] INDEX = {0, 100, 70, 50, 20, 25, 40, 90, 85, 55};

    /** 积极情绪：开心 平静 兴奋 满足 */
    public static final Set<Integer> POSITIVE = Set.of(1, 2, 7, 8);

    /** 负面情绪：疲惫 难过 生气 焦虑 */
    public static final Set<Integer> NEGATIVE = Set.of(3, 4, 5, 6);

    /**
     * 「低落/抑郁倾向」子集：疲惫 难过 焦虑。
     *
     * 与 NEGATIVE 刻意区分：生气(5)属于外化的愤怒，不属于抑郁情绪的核心表现。
     * 本集合参照 PHQ-9 的三个维度映射到本系统的九类标签：
     *   情绪低落(4 难过) / 精力减退(3 疲惫) / 精神性焦虑(6 焦虑)
     */
    public static final Set<Integer> DOWNCAST = Set.of(3, 4, 6);

    // =========================================================
    // SQL 片段（供 @Select 注解使用）
    // =========================================================
    /*
     * 为什么是「字面量 + 测试守护」而不是直接在注解里调用 buildIndexSql()：
     * Java 注解的属性值必须是**编译期常量**，方法调用不满足，@Select 里无法拼接。
     * 因此这里保留一份字面量，同时提供 buildIndexSql()/buildInSql() 作为「标准答案」，
     * 由 EmotionDictConsistencyTest 断言两者完全相等——
     * 改 INDEX / POSITIVE / NEGATIVE 而忘了改 SQL 字面量时，测试会立刻失败。
     */

    /** SQL 情绪指数表达式（列名固定为 emotion） */
    public static final String SQL_INDEX =
            "CASE emotion WHEN 1 THEN 100 WHEN 2 THEN 70 WHEN 3 THEN 50 WHEN 4 THEN 20 WHEN 5 THEN 25"
                    + " WHEN 6 THEN 40 WHEN 7 THEN 90 WHEN 8 THEN 85 WHEN 9 THEN 55 ELSE NULL END";

    /** SQL 积极情绪集合 */
    public static final String SQL_POSITIVE = "emotion IN (1,2,7,8)";

    /** SQL 负面情绪集合（与 {@link #NEGATIVE} 同集合） */
    public static final String SQL_NEGATIVE = "emotion IN (3,4,5,6)";

    private EmotionConst() {}

    /** SQL 情绪指数表达式的「标准答案」，仅供测试与代码生成参考 */
    public static String buildIndexSql(String column) {
        StringBuilder sb = new StringBuilder("CASE ").append(column);
        for (int i = 1; i < INDEX.length; i++) {
            sb.append(" WHEN ").append(i).append(" THEN ").append(INDEX[i]);
        }
        return sb.append(" ELSE NULL END").toString();
    }

    /** SQL {@code IN (...)} 片段的「标准答案」，仅供测试与代码生成参考 */
    public static String buildInSql(String column, Set<Integer> set) {
        StringBuilder sb = new StringBuilder(column).append(" IN (");
        boolean first = true;
        for (int v : set.stream().sorted().toList()) {
            if (!first) sb.append(',');
            sb.append(v);
            first = false;
        }
        return sb.append(')').toString();
    }

    public static int indexOf(Integer emotion) {
        if (emotion == null || emotion < 1 || emotion >= INDEX.length) return 0;
        return INDEX[emotion];
    }

    public static String nameOf(Integer emotion) {
        if (emotion == null || emotion < 1 || emotion >= NAMES.length) return "未知";
        return NAMES[emotion];
    }

    public static boolean isPositive(Integer emotion) {
        return emotion != null && POSITIVE.contains(emotion);
    }

    public static boolean isNegative(Integer emotion) {
        return emotion != null && NEGATIVE.contains(emotion);
    }

    public static boolean isDowncast(Integer emotion) {
        return emotion != null && DOWNCAST.contains(emotion);
    }
}
