package com.seeyou.see_you.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 情绪风险预警的阈值配置（application.yml 的 app.alert 下）。
 *
 * ⚠️ 非医疗声明：本模块做的是「情绪状态的统计提示」，
 *    不是抑郁症筛查量表，不构成任何形式的医疗诊断。
 *    所有对外文案必须携带 disclaimer 字段的内容。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.alert")
public class RiskProperties {

    /** 预警总开关 */
    private boolean enabled = true;

    /** 判定窗口天数（滑动窗口） */
    private int windowDays = 7;

    /** 窗口内至少要有多少天有记录才参与判定（数据太少不妄下结论） */
    private int minRecordDays = 4;

    /**
     * 一级（轻度关注）是否要求低落天数 ≥ 2：
     * true 时单日低落即使占比与斜率都达标也不预警（避免一次性的坏心情造成打扰）。
     * 注意这里是「窗口内低落天数」，不是「连续天数」——判据见 RiskRuleEngine 类注释。
     */
    private boolean requireMultiDay = true;

    /** 二级（中度提醒）触发所需的窗口内低落天数（疲惫/难过/焦虑，不含生气） */
    private int level2DowncastDays = 3;

    /** 三级（高度关注）触发所需的窗口内低落天数 */
    private int level3DowncastDays = 5;

    /** 三级触发的负面占比阈值 */
    private double level3Ratio = 0.75;

    /** 三级触发的窗口平均情绪指数上限（低于此值说明整体状态很差） */
    private double level3AvgIndex = 35.0;

    /** 一级（轻度关注）触发的负面占比阈值 */
    private double level1Ratio = 0.50;

    /** 一级触发的情绪指数斜率上限（负值代表持续下滑） */
    private double level1Slope = -1.5;

    /** 冷静期：同一等级预警多少天内不重复打扰用户 */
    private int cooldownDays = 3;

    // ==================== 危机通道（关键词命中即提醒，不等统计窗口） ====================
    // 趋势通道要求窗口内 4 天以上记录才判定，这对"第一次写下不想活了"的用户是延迟；
    // 危机通道单独走一条：正文命中即生成提示，判据可解释、可复现、词库可配置。

    /** 危机通道开关（关闭后退化为只有趋势通道） */
    private boolean crisisEnabled = true;

    /** 危机词命中时给出的等级（默认 3 = 高度关注） */
    private int crisisLevel = 3;

    /**
     * 预警词命中时给出的等级（默认 2 = 中度提醒）。
     *
     * <p>为什么单拆一档：「撑不下去了」这类表达既可能是真的危机，也常出现在日常抱怨里
     * （「这活我撑不下去了」）。弹三级红色「危机关怀」会明显吓到用户（误报代价高），
     * 但完全不报又可能漏掉真需要接住的人 —— 所以降为二级中度提醒：仍然立刻提醒，
     * 只是文案更温和、不进红色通道。二级走 WARNING_WORD 规则，与三级危机词共用一张表。</p>
     */
    private int warningLevel = 2;

    /** AI 精判开关（两级漏斗的第二级）：危机词未命中时，把正文交给 LLM 做风险分类。
     * 发给 LLM 的只有正文、不带任何身份信息；AI 失败/未配 key 时静默降级为纯规则模式。
     */
    private boolean aiJudgeEnabled = true;

    /** AI 精判每日成本上限：每人每天最多几次 LLM 调用（默认 3，yml 可配） */
    private int aiJudgeDailyLimit = 3;

    /**
     * 危机词库：命中任意一条即触发三级。
     * 只在配置里维护，不入库 —— 词库属于敏感内容，改它需要重启，避免后台误操作。
     */
    private List<String> crisisWords = new ArrayList<>(List.of(
            "不想活了", "不想活", "活不下去", "活着没意思", "活着没意义", "活够了",
            "想死", "死了算了", "一了百了", "想自杀", "自杀", "自残", "轻生",
            "结束生命", "放弃生命", "伤害自己", "割腕", "跳楼", "跳河", "吞药",
            "不想醒来", "再也不想醒来", "消失就好了"
    ));

    /**
     * 预警词库：命中触发二级（中度提醒）。
     * 收录「语义偏危机、但日常口语里也常作抱怨用」的两可表达；危机词库保持高纯度。
     */
    private List<String> warningWords = new ArrayList<>(List.of(
            "撑不下去了"
    ));

    /** 例外短语：命中词的邻域里出现这些，判为亲昵/夸张用法，不触发（如"想死你了"） */
    private List<String> crisisExcludes = new ArrayList<>(List.of(
            "想死你", "想死我", "想死啦", "想死啊", "想死嘞", "笑死", "美死", "甜死"
    ));

    /**
     * 求助渠道清单（危机文案与前端弹窗共用，可在 yml 改）。
     * 格式约定：「名称 + 空格 + 号码」，前端据此把号码加粗；
     * 没有号码的条目（如「学校心理咨询中心」）整条加粗显示。
     * 之前前端 RiskAlertDialog 把这几条写死在组件里，与后端文案各改各的会不一致 —— 统一到配置。
     */
    private List<String> crisisHotlines = new ArrayList<>(List.of(
            "全国 24 小时心理援助热线 12356",
            "北京心理危机研究与干预中心 010-82951332",
            "希望 24 热线 400-161-9995",
            "学校心理咨询中心 / 当地精神卫生中心"));

    /** 主热线：危机正文里「现在就拨」只给第一条，避免一次罗列四条把正在难受的用户淹掉 */
    public String primaryHotline() {
        return (crisisHotlines == null || crisisHotlines.isEmpty())
                ? "全国 24 小时心理援助热线 12356"
                : crisisHotlines.get(0);
    }

    /** 免责声明，所有预警文案必须携带 */
    private String disclaimer = "本提示仅基于你自行记录的情绪数据做统计参考，不属于医疗诊断，不能替代专业评估。";
}
