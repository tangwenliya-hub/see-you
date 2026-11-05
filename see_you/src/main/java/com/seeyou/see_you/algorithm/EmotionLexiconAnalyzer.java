package com.seeyou.see_you.algorithm;

import com.seeyou.see_you.entity.EmotionLexicon;

import java.util.*;

/**
 * 面向九类情绪的中文词典情感分析器（**纯内存计算，无深度学习**）。
 *
 * <h3>为什么不用 BERT / LSTM？</h3>
 * 1. 毕业设计需在单机环境复现，预训练模型依赖体积大、推理慢；
 * 2. 词典法的每一次打分都能指出「是因为哪个词」，可解释性远强于黑盒模型；
 * 3. 情绪日记的用词高度集中在个人化口语词上，领域词典比通用语料更准。
 *
 * <h3>三条语法规则</h3>
 * <ol>
 *   <li>最大正向匹配：长词优先，避免长词条被短词条截断</li>
 *   <li>否定词翻转：情感词前 3 字内出现否定词，该词不计入正向得分（"不开心" ≠ 开心）</li>
 *   <li>程度副词缩放：情感词前 2 字内出现程度副词，得分乘以对应倍数（"非常累" × 1.6）</li>
 * </ol>
 *
 * <h3>隐私约束</h3>
 * 本类只接收 String 做即时计算，**不持有、不落库、不打印原文**：
 * 调用方必须保证 text 不写入任何表与日志。
 */
public final class EmotionLexiconAnalyzer {

    /** 情感词：按长度降序，保证最大正向匹配 */
    private final List<EmotionLexicon> words;
    /** 否定词 */
    private final Set<String> negations;
    /** 程度副词 word -> 倍数 */
    private final Map<String, Double> degrees;

    public EmotionLexiconAnalyzer(List<EmotionLexicon> entries) {
        this.words = new ArrayList<>();
        this.negations = new HashSet<>();
        this.degrees = new HashMap<>();
        for (EmotionLexicon e : entries) {
            if (e.getWord() == null || e.getWord().isBlank()) continue;
            String w = e.getWord().trim();
            if (Integer.valueOf(1).equals(e.getWordType())) {
                negations.add(w);
            } else if (Integer.valueOf(2).equals(e.getWordType())) {
                degrees.put(w, e.getWeight() == null ? 1.0 : e.getWeight().doubleValue());
            } else if (Integer.valueOf(0).equals(e.getWordType())) {
                words.add(e);
            }
        }
        // 长词优先：否则「兴奋」这样的长词会先被「兴」之类的短词抢占匹配位置
        words.sort((a, b) -> b.getWord().length() - a.getWord().length());
    }

    /** 分析结果 */
    public record TextScore(
            /** emotion -> 归一化概率，各项之和为 1；无命中时为 null */
            Map<Integer, Double> distribution,
            /** 命中的原始词条及其权重，用于给用户解释（不含用户原文） */
            List<String> hitWords,
            /** 原始累计分 */
            double rawTotal
    ) {}

    /**
     * 分析一段短文本。
     *
     * @param text 用户随笔（**不得落库**）
     * @return 九类情绪的概率分布；一个词都没命中时返回 null（宁可不猜）
     */
    public TextScore analyze(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.toLowerCase(Locale.ROOT);

        Map<Integer, Double> raw = new HashMap<>();
        List<String> hits = new ArrayList<>();

        int i = 0;
        int max = t.length();
        while (i < max) {
            EmotionLexicon matched = null;
            for (EmotionLexicon w : words) {
                String word = w.getWord().toLowerCase(Locale.ROOT);
                if (word.isEmpty()) continue;
                if (t.startsWith(word, i)) { matched = w; break; }
            }
            if (matched == null) { i++; continue; }

            String hit = matched.getWord();
            int start = i;
            i += matched.getWord().length();

            // 否定词：往前看 3 个字符窗口
            int negStart = Math.max(0, start - 3);
            String prefix = t.substring(negStart, start);
            if (containsAny(prefix, negations)) continue; // 否定：直接丢弃该词，不产生情绪指向

            // 程度副词：往前看 2 个字符窗口
            int degStart = Math.max(0, start - 2);
            double mult = degreeMultiplier(t.substring(degStart, start));

            double w = (matched.getWeight() == null ? 1.0 : matched.getWeight().doubleValue()) * mult;
            raw.merge(matched.getEmotion(), w, Double::sum);
            hits.add(hit + "(×" + String.format("%.1f", mult) + ")");
        }

        if (raw.isEmpty()) return null;

        double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return null;
        Map<Integer, Double> dist = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> e : raw.entrySet()) {
            dist.put(e.getKey(), e.getValue() / total);
        }
        return new TextScore(dist, hits, total);
    }

    private double degreeMultiplier(String prefix) {
        double m = 1.0;
        for (Map.Entry<String, Double> d : degrees.entrySet()) {
            if (prefix.contains(d.getKey())) m *= d.getValue();
        }
        return m;
    }

    private boolean containsAny(String prefix, Set<String> set) {
        for (String s : set) {
            if (!s.isEmpty() && prefix.contains(s)) return true;
        }
        return false;
    }

    /** 词典规模（论文里可以写"自建词典共 N 条"） */
    public int size() {
        return words.size() + negations.size() + degrees.size();
    }
}
