package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪风险预警记录。
 *
 * ⚠️ 非医疗声明（务必保留，代码、接口返回、论文三处都要有）：
 * 本模块基于用户自行记录的情绪数据做统计层面的状态提示，
 * **不属于抑郁障碍筛查，不构成任何医疗诊断、治疗建议或用药指导**。
 * 系统给出的永远是「关心式提示 + 求助引导」，而不是结论。
 */
@Data
@TableName("psy_risk_alert")
public class RiskAlert {
    @TableId(type = IdType.AUTO)
    private Long alertId;
    private Long userId;
    private LocalDate alertDate;
    /** 1=轻度关注 2=中度提醒 3=高度关注 */
    private Integer alertLevel;
    /** 判定窗口天数 */
    private Integer windowDays;
    /** 窗口内有记录的天数 */
    private Integer recordDays;
    /** 窗口内负面情绪天数 */
    private Integer negativeDays;
    /**
     * 窗口内处于「低落」状态的天数（疲惫/难过/焦虑，不含生气）。
     *
     * <p>数据库列名仍是 {@code max_continuous}（历史遗留）：早期实现声称计算
     * 「最长连续低落天数」，实际因未校验日期相邻而等同于「永不打断的累计」。
     * 现已改为窗口内低落天数，为避免线上库迁移风险保留原列名，
     * 用 {@code @TableField} 显式映射，Java 侧不再使用误导性的 maxContinuous。
     */
    @TableField("max_continuous")
    private Integer maxDowncastDays;
    /** 窗口内负面占比 0~1 */
    private BigDecimal negativeRatio;
    /** 窗口内平均情绪指数 */
    private BigDecimal avgIndex;
    /** 情绪指数最小二乘斜率（负数代表持续下滑） */
    private BigDecimal trendSlope;
    /** 命中的规则名，便于追溯阈值配置 */
    private String triggerRule;
    /** 判定依据明细（中文） */
    private String triggerDetail;
    /** 自我调节建议 + 求助引导 */
    private String advice;
    /** 0=未读 1=已读 */
    private Integer isRead;
    /** 0=未确认 1=用户已确认知晓 */
    private Integer ackStatus;
    private LocalDateTime ackTime;
    /**
     * 管理员处置方式（已电话联系 / 已通知辅导员 / 已上门面谈）。
     * 只存最新一次结果（轻量版闭环）：预警从「发现 → 查看 → 处置」走完最后一公里，
     * NULL = 还未处置。多次跟进的时间线版没做，属于明确的功能取舍。
     */
    private String disposition;
    /** 处置备注：联系到了谁、用户状态如何等自由记录 */
    private String dispositionNote;
    /** 处置时间 */
    private LocalDateTime dispositionTime;
    /**
     * 误报标记：0=有效预警 1=用户标记「这不是我」。
     * 论文量化指标用：准确率 = (触发总数 - 误报数) / 触发总数。
     * 危机词通道（CRISIS_KEYWORD）不允许标误报——安全方向宁可错弹不可漏接。
     */
    private Integer misreport;
    /** 误报标记时间 */
    private LocalDateTime misreportTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
