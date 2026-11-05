package com.seeyou.see_you.service;

import com.seeyou.see_you.entity.RiskAlert;

import java.util.Map;

/**
 * 情绪风险预警服务。
 *
 * ⚠️ 非医疗边界（三处必须同时体现：代码注释、接口返回 disclaim字段、论文正文）：
 * 本模块基于用户自行记录的情绪数据做**统计层面的情绪状态提示**，
 * 不是抑郁障碍筛查工具，不构成任何形式的医疗诊断、治疗建议或用药指导。
 * 对外文案一律使用「注意」「建议关注」「可以考虑」这类提示性措辞，
 * 禁止出现「诊断」「患病」「抑郁倾向确诊」等结论性表述。
 */
public interface RiskAlertService {

    /**
     * 对指定用户做一次即时判定（写库去重由 (user_id, alert_date) 唯一键保证）。
     * @return 新生成/更新的预警；未触发则返回 null
     */
    RiskAlert evaluate(Long userId);

    /**
     * 危机通道：扫描日记正文，命中危机词库则<b>立即</b>生成关怀提示，不再等统计窗口攒够数据。
     *
     * @param text 日记正文；只做内存比对，落库的只有「命中的词条」，不存用户原文
     * @return 命中时返回预警记录；未命中 / 开关关闭 / 当天已确认过则返回 null（或既有记录）
     */
    RiskAlert evaluateCrisis(Long userId, String text);

    /** 最近一条「尚未确认」的预警，用于登录后的弹窗提醒 */
    Map<String, Object> latest(Long userId);

    /** 历史预警记录（分页） */
    Map<String, Object> list(Long userId, long page, long size);

    /** 用户点「我知道了」：标记已确认 */
    void ack(Long userId, Long alertId);

    /** 标记已读（不一定确认，比如用户划掉了卡片） */
    void markRead(Long userId, Long alertId);

    /**
     * 用户标记「这不是我」（误报反馈）。
     * 幂等；危机词通道（CRISIS_KEYWORD）拒绝标记——安全方向宁可错弹不可漏接。
     * 标记后同时视为已确认（不再重复弹窗打扰），但保留误报记录供后台统计准确率。
     */
    void markMisreport(Long userId, Long alertId);

    /** 概览：未读数、累计次数、最高等级、当前阈值 */
    Map<String, Object> overview(Long userId);

    /** 定时任务：扫描所有近期有记录的用户 */
    int scanAll();
}
