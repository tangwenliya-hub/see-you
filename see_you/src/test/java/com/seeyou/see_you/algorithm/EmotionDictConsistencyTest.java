package com.seeyou.see_you.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 情绪字典跨层一致性测试。
 *
 * <p>背景：本项目历史上出现过前后端情绪映射错位、导致全站统计错误的 Bug。
 * 情绪编号/名称/指数在【后端 2 处 + 前端 4 处】各有一份，靠人工同步必然出错。
 * 这个测试直接读取前端源文件比对，任何一端改漏了都会在 CI 里立刻失败。
 *
 * <p>约定真源：`EmotionConst`（后端）+ `frontend/src/utils/constants.js`（前端）。
 */
class EmotionDictConsistencyTest {

    private static final Path FRONTEND_CONST =
            Path.of(System.getProperty("user.dir")).getParent()
                    .resolve("frontend/src/utils/constants.js");

    private String frontend() throws Exception {
        assertTrue(Files.exists(FRONTEND_CONST), "找不到前端常量文件：" + FRONTEND_CONST);
        return Files.readString(FRONTEND_CONST, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("情绪名称：后端 EmotionConst 与前端 EMOTIONS 必须完全一致")
    void namesMatchFrontend() throws Exception {
        String src = frontend();
        // 匹配 { val: 1, label: '开心', ... }
        Matcher m = Pattern.compile("val:\\s*(\\d+),\\s*label:\\s*'([^']+)'").matcher(src);
        Map<Integer, String> fe = new HashMap<>();
        while (m.find()) fe.put(Integer.parseInt(m.group(1)), m.group(2));

        assertEquals(9, fe.size(), "前端应有 9 类情绪");
        for (int i = 1; i <= 9; i++) {
            assertEquals(EmotionConst.NAMES[i], fe.get(i),
                    "情绪 " + i + " 的名称前后端不一致");
        }
    }

    @Test
    @DisplayName("情绪指数：后端 EmotionConst.INDEX 与前端 EMOTION_INDEX 必须完全一致")
    void indexMatchFrontend() throws Exception {
        String src = frontend();
        Matcher m = Pattern.compile("(\\d+):\\s*(\\d+)").matcher(
                src.substring(src.indexOf("EMOTION_INDEX"), src.indexOf("emotionScore")));
        Map<Integer, Integer> fe = new HashMap<>();
        while (m.find()) fe.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));

        assertEquals(9, fe.size(), "前端情绪指数应有 9 项");
        for (int i = 1; i <= 9; i++) {
            assertEquals(EmotionConst.INDEX[i], fe.get(i),
                    "情绪 " + i + " 的指数前后端不一致（会直接导致统计错误）");
        }
    }

    @Test
    @DisplayName("积极情绪集合：前后端必须一致")
    void positiveSetMatchFrontend() throws Exception {
        String src = frontend();
        List<Integer> fe = parseArray(src, "POSITIVE_EMOTIONS");
        assertEquals(new HashSet<>(fe), EmotionConst.POSITIVE, "积极情绪集合前后端不一致");
    }

    @Test
    @DisplayName("消极情绪集合：前后端必须完全一致（2026-10-29 起统一为 {3,4,5,6}）")
    void negativeSetMatchesFrontend() throws Exception {
        String src = frontend();
        List<Integer> fe = parseArray(src, "NEGATIVE_EMOTIONS");

        /*
         * 历史上这里是包含关系断言：前端/统计层用「明确负面」{4,5}，算法层用
         * 「低落倾向」{3,4,5,6}，导致看板「消极 2 天」与预警「负面 5 天」互相矛盾。
         * 现已统一为 {3,4,5,6}：积极(1,2,7,8) + 消极(3,4,5,6) + 其他(9) 覆盖全部九类，
         * 占比合计才可能接近 100%。因此这里改为严格相等断言，任一端再改漏就会失败。
         */
        assertEquals(EmotionConst.NEGATIVE, new HashSet<>(fe),
                "消极情绪集合前后端不一致：后端=" + EmotionConst.NEGATIVE + " 前端=" + fe);
    }

    @Test
    @DisplayName("SQL 片段：EmotionConst.SQL_* 必须与字典生成结果逐字相同")
    void sqlFragmentsMatchDict() {
        // 注解里的 SQL 只能写字面量（编译期常量），所以留了一份字面量；
        // 这个断言保证「改字典忘改 SQL」会立刻失败，而不是上线后统计悄悄错。
        assertEquals(EmotionConst.buildIndexSql("emotion"), EmotionConst.SQL_INDEX,
                "SQL_INDEX 与 INDEX 生成结果不一致（统计平均值会全错）");
        assertEquals(EmotionConst.buildInSql("emotion", EmotionConst.POSITIVE),
                EmotionConst.SQL_POSITIVE, "SQL_POSITIVE 与 POSITIVE 不一致");
        assertEquals(EmotionConst.buildInSql("emotion", EmotionConst.NEGATIVE),
                EmotionConst.SQL_NEGATIVE, "SQL_NEGATIVE 与 NEGATIVE 不一致");
    }

    @Test
    @DisplayName("字典自洽：编号 1~9 连续、名称非空、指数在 0~100")
    void dictSelfConsistent() {
        assertEquals(10, EmotionConst.NAMES.length);
        assertEquals(10, EmotionConst.INDEX.length);
        for (int i = 1; i <= 9; i++) {
            assertNotNull(EmotionConst.NAMES[i]);
            assertFalse(EmotionConst.NAMES[i].isBlank());
            assertTrue(EmotionConst.INDEX[i] > 0 && EmotionConst.INDEX[i] <= 100);
            assertEquals(EmotionConst.NAMES[i], EmotionConst.nameOf(i));
            assertEquals(EmotionConst.INDEX[i], EmotionConst.indexOf(i));
        }
        assertEquals(0, EmotionConst.indexOf(null), "未知情绪的指数应兜底为 0");
        assertEquals("未知", EmotionConst.nameOf(null));
    }

    private static List<Integer> parseArray(String src, String name) {
        int i = src.indexOf(name);
        int s = src.indexOf('[', i);
        int e = src.indexOf(']', s);
        String body = src.substring(s + 1, e);
        List<Integer> out = new ArrayList<>();
        for (String p : body.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(Integer.parseInt(t));
        }
        return out;
    }
}
