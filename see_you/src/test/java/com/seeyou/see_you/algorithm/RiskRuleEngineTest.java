package com.seeyou.see_you.algorithm;

import com.seeyou.see_you.config.RiskProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 情绪风险预警规则引擎的单元测试。
 *
 * <p>预警是高敏感功能，规则改动必须有测试兜住：
 * 漏判 = 该关怀的人没被关怀；误判 = 打扰正常用户。
 */
class RiskRuleEngineTest {

    private static final LocalDate D0 = LocalDate.of(2026, 3, 1);

    private static RiskProperties props() {
        RiskProperties p = new RiskProperties();
        p.setWindowDays(7);
        p.setMinRecordDays(4);
        p.setRequireMultiDay(true);
        p.setLevel2DowncastDays(3);
        p.setLevel3DowncastDays(5);
        p.setLevel3Ratio(0.75);
        p.setLevel3AvgIndex(35.0);
        p.setLevel1Ratio(0.5);
        p.setLevel1Slope(-1.5);
        return p;
    }

    /** 从情绪数组造窗口（日期连续） */
    private static Map<LocalDate, Integer> window(int... emotions) {
        Map<LocalDate, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < emotions.length; i++) m.put(D0.plusDays(i), emotions[i]);
        return m;
    }

    @Test
    @DisplayName("样本不足：记录天数低于阈值时不妄下结论")
    void noVerdictWhenTooFewRecords() {
        // 只记录 2 天（阈值 4 天），即使全是难过也不能判定
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(4, 4));
        assertEquals(RiskRuleEngine.NONE.level(), RiskRuleEngine.judge(s, props()).level());
    }

    @Test
    @DisplayName("三级：窗口内低落 5 天 → 高度关注")
    void level3OnFiveDowncastDays() {
        // 4=难过 6=焦虑 3=疲惫 都属于 DOWNCAST
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(4, 4, 6, 3, 4));
        RiskRuleEngine.Verdict v = RiskRuleEngine.judge(s, props());
        assertEquals(3, v.level());
        assertEquals("L3_DOWNCAST_DAYS", v.rule());
        assertEquals(5, s.downcastDays());
    }

    @Test
    @DisplayName("二级：窗口内低落 3 天 → 中度提醒")
    void level2OnThreeDowncastDays() {
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(4, 4, 6, 1));
        RiskRuleEngine.Verdict v = RiskRuleEngine.judge(s, props());
        assertEquals(2, v.level());
        assertEquals("L2_DOWNCAST_DAYS", v.rule());
    }

    @Test
    @DisplayName("判据是「天数计数」而非「连续段」：积极日会把起伏切开，但天数照样累计")
    void downcastDaysAreCountedNotSegmented() {
        // 低落 / 开心 / 低落 / 平静 / 焦虑 —— 没有任何 2 天以上的连续段，但低落共 3 天
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(4, 1, 6, 2, 3));
        assertEquals(3, s.downcastDays(), "低落天数应与日期是否相邻无关");
        assertEquals(2, RiskRuleEngine.judge(s, props()).level(),
                "按天数计数时，分散的 3 天低落同样达到二级阈值（这是刻意的语义选择）");
    }

    @Test
    @DisplayName("三级：负面占比与均值双高时同样触发（不要求连续）")
    void level3ByOverallRatio() {
        // 6 天里 5 天消极且均值很低 → 即便不连续也应高度关注
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(4, 5, 4, 5, 4, 5));
        RiskRuleEngine.Verdict v = RiskRuleEngine.judge(s, props());
        assertEquals(3, v.level());
        assertEquals("L3_OVERALL", v.rule());
    }

    @Test
    @DisplayName("不打扰：情绪健康的用户不触发任何预警")
    void noVerdictForHealthyUser() {
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(1, 2, 7, 8, 1, 2));
        RiskRuleEngine.Verdict v = RiskRuleEngine.judge(s, props());
        assertEquals(0, v.level(), "情绪正常的用户不应该收到预警");
    }

    @Test
    @DisplayName("统计口径：缺失日不计入记录天数，也不影响低落天数（按天计数，无「连续」概念）")
    void missingDaysDoNotAffectCount() {
        Map<LocalDate, Integer> m = new LinkedHashMap<>();
        m.put(D0, 4);                    // 第 1 天 难过
        m.put(D0.plusDays(3), 4);        // 中间空 2 天，第 4 天 难过
        m.put(D0.plusDays(4), 6);        // 第 5 天 焦虑
        m.put(D0.plusDays(5), 3);        // 第 6 天 疲惫

        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, m);
        assertEquals(4, s.recordDays(), "只有 4 天有记录");
        assertEquals(4, s.downcastDays(), "低落天数按天计数，与间隔无关");
    }

    @Test
    @DisplayName("文案一致性：判据不是「连续」，对外文案就不许出现「连续」二字")
    void adviceMustNotClaimContinuity() {
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(4, 4, 6, 3, 4));
        for (int level = 1; level <= 3; level++) {
            String advice = RiskRuleEngine.advice(level, s, props());
            assertFalse(advice.contains("连续"),
                    "判据是窗口内低落天数，文案出现「连续」就是言行不一（level=" + level + "）");
        }
        String detail = RiskRuleEngine.judge(s, props()).detail();
        assertFalse(detail.contains("连续"), "判定依据同样不得声称连续");
    }

    @Test
    @DisplayName("关怀文案：三级必须包含求助渠道与非医疗免责声明")
    void adviceContainsDisclaimer() {
        RiskRuleEngine.WindowStats s = RiskRuleEngine.stats(D0, window(4, 4, 6, 3, 4));
        String advice = RiskRuleEngine.advice(3, s, props());
        assertNotNull(advice);
        assertTrue(advice.contains("医疗") || advice.contains("专业"),
                "三级文案必须提示这不是医疗诊断");
        assertFalse(advice.contains("你有病"), "文案不得出现诊断性表述");
    }

    @Test
    @DisplayName("等级名称：0~3 级都有中文名")
    void levelNames() {
        for (int i = 0; i <= 3; i++) {
            assertNotNull(RiskRuleEngine.levelName(i));
        }
    }
}
