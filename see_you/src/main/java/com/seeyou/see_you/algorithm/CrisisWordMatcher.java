package com.seeyou.see_you.algorithm;

import java.util.*;

/**
 * 危机词匹配器：日记正文里出现「需要被立刻接住」的表达时，命中即触发关怀提示。
 *
 * <h3>为什么要和趋势预警分开</h3>
 * {@link RiskRuleEngine} 走的是「7 天窗口统计」通道，要求窗口内至少 4 天有记录、
 * 低落天数 / 负面占比 / 指数斜率共同越界才提示 —— 这是为了避免单次坏心情误报。
 * 但像「我不想活了」这类表达，等 7 天窗口攒够数据再提示是<b>不负责任的延迟</b>：
 * 用户第一次写就可能只有这一篇。所以单独开一条<b>危机通道</b>：
 * <pre>
 *   趋势通道：统计口径 → 慢，但严谨，避免打扰
 *   危机通道：关键词命中 → 快，宁可多关心一次
 * </pre>
 * 两条通道共用同一张 psy_risk_alert 表，用 trigger_rule 区分。
 *
 * <h3>误报与漏报的取舍（论文里要如实写）</h3>
 * <ul>
 *   <li><b>误报（假阳性）</b>：口语里「想死你了」是亲昵表达。用 {@code excludes}
 *       白名单把它挡掉，但挡不干净（例如「笑死我了」不会命中，因为词库里没有单字「死」）；</li>
 *   <li><b>漏报（假阴性）</b>：用户换一种说法就命中不了，词典法必然存在这个上限。</li>
 * </ul>
 * 因此本模块的定位是「<b>宁可多关心一次</b>的提示」，不是筛查工具：
 * 误报的代价是多弹一次关怀卡片，漏报的代价才是真正危险的 —— 所以阈值向召回率倾斜。
 *
 * <h3>隐私约束（红线）</h3>
 * 本类只在内存中比对，**不持有、不落库、不打印用户原文**；
 * 落库的只有「命中的词条」（来自配置，不是用户写的内容）。
 */
public final class CrisisWordMatcher {

    private CrisisWordMatcher() {}

    /** 否定词：危机词前 2 字内出现这些字，说明是否定用法（"我不会自杀"） */
    private static final List<String> NEGATIONS = List.of("不", "没", "别", "无", "非", "未");

    /**
     * 「假的否定」词尾：否定字后面跟这些字时，它其实是程度副词的一部分，不是否定。
     *
     * <p>这一条是修正一次<b>高危漏报</b>：旧实现只看「命中词前 2 字里有没有否定字」，
     * 于是「控制<u>不住</u>想死」「忍<u>不住</u>想死」「受<u>不了</u>想死」全部被判成否定句，
     * 静默丢弃 —— 而这恰恰是最高危、最需要被接住的表达方式（"忍不住"是失控，不是否认）。
     * 判据改为：否定字之后如果还剩「住/了/停/断/行/由/禁/受/放/得」这类词尾，
     * 说明否定字属于「控制不住」这一整块，不构成否定。</p>
     */
    private static final List<String> NON_NEGATION_TAILS =
            List.of("住", "了", "停", "断", "行", "由", "禁", "受", "放", "得");

    /**
     * 扫描文本，返回命中的词条（来自配置，去重，保持配置顺序）。
     *
     * @param text     日记正文（不落库）
     * @param words    危机词库
     * @param excludes 例外短语（命中词的邻域内出现则判为误报，如"想死你了"）
     */
    public static List<String> hit(String text, Collection<String> words, Collection<String> excludes) {
        if (text == null || text.isBlank() || words == null || words.isEmpty()) return List.of();
        // 归一化：去掉所有空白，避免"不想 活了"这种换行/空格绕过匹配
        String t = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        // 长词优先：命中「想自杀」时就不必再报一个更短的「自杀」，避免同一处重复计数
        List<String> sorted = words.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(w -> !w.isEmpty())
                .sorted((a, b) -> b.length() - a.length())
                .toList();
        for (String raw : sorted) {
            String w = raw.toLowerCase(Locale.ROOT);
            int from = 0;
            while (from <= t.length() - w.length()) {
                int idx = t.indexOf(w, from);
                if (idx < 0) break;
                from = idx + w.length();
                if (negated(t, idx)) continue;
                if (excluded(t, idx, w.length(), excludes)) continue;
                final String cand = w;
                if (out.stream().anyMatch(h -> h.contains(cand))) continue;
                out.add(w);
                break;   // 同一个词条重复出现只计一次
            }
        }
        return out;
    }

    /**
     * 命中词前 2 字内是否出现否定词。
     *
     * <p>从离命中词最近的否定字往前找：否定字紧贴命中词（"不|想死"）→ 否定；
     * 否定字后面还跟着「住/了/停…」（"不|住" 想死）→ 是"控制不住"这类失控表达，不算否定。</p>
     */
    private static boolean negated(String t, int idx) {
        String prefix = t.substring(Math.max(0, idx - 2), idx);
        for (int i = prefix.length() - 1; i >= 0; i--) {
            if (!NEGATIONS.contains(String.valueOf(prefix.charAt(i)))) continue;
            String tail = prefix.substring(i + 1);
            if (tail.isEmpty()) return true;                       // "不想死""别自杀"
            if (NON_NEGATION_TAILS.contains(tail)) return false;    // "控制不住想死""受不了想死"
            return true;                                            // "没有想死""不会自杀"
        }
        return false;
    }

    /** 命中词邻域内是否出现例外短语（亲昵 / 夸张用法） */
    private static boolean excluded(String t, int idx, int len, Collection<String> excludes) {
        if (excludes == null || excludes.isEmpty()) return false;
        int s = Math.max(0, idx - 2);
        int e = Math.min(t.length(), idx + len + 2);
        String region = t.substring(s, e);
        for (String raw : excludes) {
            if (raw == null || raw.isBlank()) continue;
            if (region.contains(raw.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
