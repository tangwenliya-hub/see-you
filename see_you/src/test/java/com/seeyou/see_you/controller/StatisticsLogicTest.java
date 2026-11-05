package com.seeyou.see_you.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计核心算法测试：情绪字典映射 / 连续打卡 / 中文二元分词。
 *
 * <p>这些都是不依赖数据库与 Spring 容器的纯函数，直接在本包内调用（因此 StatisticsController
 * 里对应的方法刻意没有声明成 private）。
 */
class StatisticsLogicTest {

    // ==================== 1. 情绪字典 ====================

    @Nested
    @DisplayName("情绪字典映射")
    class EmotionDict {

        @Test
        @DisplayName("9 种情绪的指数与前端 utils/constants.js 保持一致")
        void indexMapping() {
            int[] expected = {0, 100, 70, 50, 20, 25, 40, 90, 85, 55};
            for (int i = 1; i < expected.length; i++) {
                assertEquals(expected[i], StatisticsController.indexOf(i), "情绪 " + i + " 的指数与前端不一致");
            }
        }

        @Test
        @DisplayName("null / 0 / 越界值一律按 0 处理，不抛异常")
        void indexBoundary() {
            assertEquals(0, StatisticsController.indexOf(null));
            assertEquals(0, StatisticsController.indexOf(0));
            assertEquals(0, StatisticsController.indexOf(-1));
            assertEquals(0, StatisticsController.indexOf(10));
            assertEquals(0, StatisticsController.indexOf(999));
        }

        @Test
        @DisplayName("情绪名称映射正确，非法值回落为「未知」")
        void nameMapping() {
            assertEquals("开心", StatisticsController.nameOf(1));
            assertEquals("满足", StatisticsController.nameOf(8));
            assertEquals("其他", StatisticsController.nameOf(9));
            assertEquals("未知", StatisticsController.nameOf(null));
            assertEquals("未知", StatisticsController.nameOf(0));
            assertEquals("未知", StatisticsController.nameOf(10));
        }
    }

    // ==================== 2. 连续打卡 ====================

    @Nested
    @DisplayName("连续打卡统计 streakInfo")
    class Streak {

        private final LocalDate today = LocalDate.now();

        private Map<String, Object> streakOf(LocalDate... days) {
            return StatisticsController.streakInfo(Arrays.asList(days));
        }

        @Test
        @DisplayName("没有任何记录时 current / longest 都是 0")
        void empty() {
            Map<String, Object> m = StatisticsController.streakInfo(Collections.emptyList());
            assertEquals(0, m.get("current"));
            assertEquals(0, m.get("longest"));
        }

        @Test
        @DisplayName("只有今天一条记录：连续 1 天")
        void onlyToday() {
            Map<String, Object> m = streakOf(today);
            assertEquals(1, m.get("current"));
            assertEquals(1, m.get("longest"));
        }

        @Test
        @DisplayName("今天 / 昨天 / 前天连续：连续 3 天")
        void consecutiveThreeDays() {
            Map<String, Object> m = streakOf(today.minusDays(2), today.minusDays(1), today);
            assertEquals(3, m.get("current"));
            assertEquals(3, m.get("longest"));
        }

        @Test
        @DisplayName("最后一次记录在 3 天前：当前连续归 0，但最长纪录保留")
        void brokenStreak() {
            Map<String, Object> m = streakOf(today.minusDays(5), today.minusDays(4), today.minusDays(3));
            assertEquals(0, m.get("current"), "断签后 current 应归零");
            assertEquals(3, m.get("longest"), "历史最长纪录不应被清零");
        }

        @Test
        @DisplayName("中途断签再续：longest 取最长的一段，current 取最后一段")
        void brokenThenResumed() {
            Map<String, Object> m = streakOf(
                    today.minusDays(6), today.minusDays(5),
                    today.minusDays(2), today.minusDays(1), today);
            assertEquals(3, m.get("current"));
            assertEquals(3, m.get("longest"));
        }

        @Test
        @DisplayName("同一天写多篇日记不中断连续天数")
        void sameDayMultipleEntries() {
            Map<String, Object> m = streakOf(
                    today.minusDays(2), today.minusDays(2), today.minusDays(2),
                    today.minusDays(1), today);
            assertEquals(3, m.get("current"));
            assertEquals(3, m.get("longest"));
        }

        @Test
        @DisplayName("输入乱序也能得到正确结果（内部先排序）")
        void unsortedInput() {
            Map<String, Object> m = streakOf(today, today.minusDays(2), today.minusDays(1));
            assertEquals(3, m.get("current"));
        }
    }

    // ==================== 3. 中文二元分词 ====================

    @Nested
    @DisplayName("日记高频词（词云）")
    class Keywords {

        @Test
        @DisplayName("正确切出「今天」「心情」「工作」「压力」这类双音节词")
        void extractWords() {
            Map<String, Integer> freq = StatisticsController.wordFreq(
                    Collections.singletonList("今天心情不错，工作压力有点大。"));
            assertTrue(freq.containsKey("今天"));
            assertTrue(freq.containsKey("心情"));
            assertTrue(freq.containsKey("工作"));
            assertTrue(freq.containsKey("压力"));
        }

        @Test
        @DisplayName("含停用字（的 / 了）的二元组被过滤，跨词的噪声不进词云")
        void stopWordsFiltered() {
            Map<String, Integer> freq = StatisticsController.wordFreq(
                    Collections.singletonList("今天的天气很好，我去了公园。"));
            assertTrue(freq.containsKey("天气"));
            assertTrue(freq.containsKey("公园"));
            assertFalse(freq.containsKey("的天"), "「的」是停用字，含它的二元组应被过滤");
            assertFalse(freq.containsKey("去了"), "「了」是停用字，含它的二元组应被过滤");
        }

        @Test
        @DisplayName("回归保护：停用字表不得包含「今天心情」等情绪日记高频字")
        void stopCharsNotTooBroad() {
            // 历史 bug：停用字表由停用「词」拼接而成，却按字符匹配，导致「今 / 天 / 心 / 情 / 觉 / 得」
            // 等大量常用字被误杀，词云里看不到「今天」「心情」。
            for (String mustKeep : new String[]{"今", "天", "心", "情", "压", "力", "工", "作", "开", "心"}) {
                assertFalse(StatisticsController.STOP_CHARS.contains(mustKeep),
                        "停用字表不应包含「" + mustKeep + "」，否则词云会漏掉高频情绪词");
            }
            for (String mustDrop : new String[]{"的", "了", "是", "在", "很", "吧"}) {
                assertTrue(StatisticsController.STOP_CHARS.contains(mustDrop),
                        "停用字表缺少虚词「" + mustDrop + "」");
            }
        }

        @Test
        @DisplayName("英文 / 数字 / 标点不参与构词")
        void nonCjkIgnored() {
            Map<String, Integer> freq = StatisticsController.wordFreq(
                    Arrays.asList("abc def", "2026-01-01", "Hello!!"));
            assertTrue(freq.isEmpty());
        }

        @Test
        @DisplayName("同一词跨多篇日记正确累加")
        void frequencyAccumulated() {
            Map<String, Integer> freq = StatisticsController.wordFreq(
                    Arrays.asList("工作压力大", "工作压力好大", "工作压力"));
            assertEquals(3, freq.get("工作"));
            assertEquals(3, freq.get("压力"));
        }

        @Test
        @DisplayName("null 列表与单字文本不会抛异常")
        void nullSafe() {
            assertTrue(StatisticsController.wordFreq(null).isEmpty());
            assertTrue(StatisticsController.wordFreq(Collections.singletonList("啊")).isEmpty());
            assertTrue(StatisticsController.wordFreq(Arrays.asList(null, "")).isEmpty());
        }
    }

    // ==================== 4. 字典一致性 ====================

    @Test
    @DisplayName("情绪名称表与指数表长度一致（历史上曾因长度不一导致前后端错位）")
    void dictionaryAligned() {
        List<Integer> all = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
        for (Integer e : all) {
            assertNotEquals("未知", StatisticsController.nameOf(e), "情绪 " + e + " 缺少名称");
            assertTrue(StatisticsController.indexOf(e) > 0, "情绪 " + e + " 缺少指数");
        }
    }
}
