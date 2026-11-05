package com.seeyou.see_you.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 危机词匹配器的单元测试。
 *
 * <p>这是全项目最不能出错的一个类：漏判 = 最高危的表达被静默丢弃；
 * 误判 = 把「笑死掉了」这类口语夸张当成真危机打扰用户。
 * 历史上真实出现过一次高危漏报 bug（「控制不住想死」被否定词误判），
 * 本测试类就是那次修复的回归防线——词库/否定逻辑再怎么改，这些用例必须永远绿。</p>
 */
class CrisisWordMatcherTest {

    /** 与 application.yml 的 crisis-words 同步的代表性子集（测试不依赖配置文件，改词库时这里同步即可） */
    private static final List<String> WORDS = List.of(
            "不想活了", "不想活", "活不下去", "活着没意思", "活够了",
            "想死", "死了算了", "一了百了", "想自杀", "自杀", "自残", "轻生",
            "结束生命", "伤害自己", "割腕", "跳楼", "跳河", "吞药",
            "不想醒来", "消失就好了"
    );

    /** 与 application.yml 的 crisis-excludes 同步的子集 */
    private static final List<String> EXCLUDES = List.of(
            "想死你", "想死我", "想死啦", "想死啊", "想死嘞",
            "笑死掉", "累死掉", "热死掉", "吓死掉", "忙死掉", "饿死掉", "困死掉"
    );

    private static List<String> hit(String text) {
        return CrisisWordMatcher.hit(text, WORDS, EXCLUDES);
    }

    // ======================= 漏报防线（修复回归）：失控型表达必须命中 =======================

    @Test
    @DisplayName("「控制不住想死」是失控不是否定——曾因否定词误判被静默丢弃，绝不允许回归")
    void 失控型表达_控制不住() {
        assertEquals(List.of("想死"), hit("我控制不住想死"));
    }

    @Test
    @DisplayName("「忍不住想死」同样是失控型高危表达")
    void 失控型表达_忍不住() {
        assertEquals(List.of("想死"), hit("我忍不住想死"));
    }

    @Test
    @DisplayName("「受不了了想死」同样不允许被「不」字误判吞掉")
    void 失控型表达_受不了() {
        assertEquals(List.of("想死"), hit("受不了了想死"));
    }

    @Test
    @DisplayName("否定句里夹着真表达：我不会自杀，但我想死 → 只报后半句")
    void 复合句_前否后真() {
        assertEquals(List.of("想死"), hit("我不会自杀，但我想死"));
    }

    // ======================= 误报防线：否定句与口语夸张不能报 =======================

    @Test
    @DisplayName("「我不想死，我只是很累」是否定，不能报")
    void 否定句_不想死() {
        assertTrue(hit("我不想死，我只是很累").isEmpty());
    }

    @Test
    @DisplayName("「我不想活了」本身就在词库里（表达的就是不想活），必须命中")
    void 词库自带否定式_不想活了() {
        assertEquals(List.of("不想活了"), hit("我不想活了"));
    }

    @Test
    @DisplayName("「我真的不想死」带状语的否定句同样不报")
    void 否定句_带状语() {
        assertTrue(hit("我真的不想死").isEmpty());
    }

    @Test
    @DisplayName("「我没有想死」是否定")
    void 否定句_没有() {
        assertTrue(hit("我没有想死").isEmpty());
    }

    @Test
    @DisplayName("「我不会自杀」前半句不报")
    void 否定句_不会自杀() {
        assertTrue(hit("我真的不会自杀").isEmpty());
    }

    @Test
    @DisplayName("「笑死掉了」是口语夸张，白名单拦截")
    void 夸张用法_笑死掉了() {
        assertTrue(hit("这个视频笑死掉了").isEmpty());
    }

    @Test
    @DisplayName("「累死掉了」同样是夸张")
    void 夸张用法_累死掉了() {
        assertTrue(hit("今天加班累死掉了").isEmpty());
    }

    @Test
    @DisplayName("「想死你了」是亲昵表达，白名单拦截")
    void 亲昵用法_想死你了() {
        assertTrue(hit("好久不见，想死你了").isEmpty());
    }

    // ======================= 命中与去重规则 =======================

    @Test
    @DisplayName("直接表达命中：我想自杀")
    void 直接命中_想自杀() {
        assertEquals(List.of("想自杀"), hit("我想自杀"));
    }

    @Test
    @DisplayName("长词优先：命中「想自杀」就不再重复报更短的「自杀」")
    void 长词优先去重() {
        List<String> r = hit("脑子里全是想自杀的念头");
        assertEquals(1, r.size(), "同一处命中不应既报想自杀又报自杀");
        assertEquals("想自杀", r.get(0));
    }

    @Test
    @DisplayName("同一词条重复出现只计一次")
    void 重复出现去重() {
        List<String> r = hit("想死，真的想死，每天都想死");
        assertEquals(List.of("想死"), r);
    }

    @Test
    @DisplayName("空白/换行不能绕过匹配：「想\\n死」跨行拆开仍要命中")
    void 空白归一化() {
        assertEquals(List.of("想死"), hit("今天太累了，想\n死"),
                "归一化去空白后应照常命中，防止用换行绕过");
    }

    @Test
    @DisplayName("多词命中全部返回")
    void 多词命中() {
        // 「活不下去」与「想死」同现
        List<String> r = hit("真的活不下去了，想死");
        assertEquals(2, r.size());
        assertTrue(r.contains("活不下去"));
        assertTrue(r.contains("想死"));
    }

    // ======================= 边界 =======================

    @Test
    @DisplayName("空文本 / 空词库安全返回")
    void 边界_空输入() {
        assertTrue(CrisisWordMatcher.hit(null, WORDS, EXCLUDES).isEmpty());
        assertTrue(CrisisWordMatcher.hit("   ", WORDS, EXCLUDES).isEmpty());
        assertTrue(CrisisWordMatcher.hit("想死", List.of(), EXCLUDES).isEmpty());
        assertEquals(List.of("想死"), CrisisWordMatcher.hit("想死", WORDS, null),
                "白名单为 null 时按无白名单处理（宁多报不漏报）");
    }
}
