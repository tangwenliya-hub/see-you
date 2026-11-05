package com.seeyou.see_you.service;

import com.seeyou.see_you.dto.BehaviorReportDTO;
import com.seeyou.see_you.dto.SenseConfigDTO;
import com.seeyou.see_you.dto.SenseConfirmDTO;
import com.seeyou.see_you.entity.BehaviorFeature;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 无感记录模块服务。
 *
 * 【隐私红线 · 修改本接口前必读】
 * 1. 所有方法都以 userId 作为首个入参，且该 userId **只能由 Controller 从 JWT 里取**，
 *    任何 ResponseEntity 都不接受来自前端的用户标识；管理员亦无权查询他人的行为与推测数据。
 * 2. 行为数据只在本用户自己的数据范围内可见、可导出、可删除。
 * 3. 文本分析原文不入任何表、不进日志。
 */
public interface SenseService {

    /** 读取我的无感记录配置 + 数据概况 */
    Map<String, Object> getConfig(Long userId);

    /** 修改开关（分级：总开关关 → 子开关全部失效） */
    void updateConfig(Long userId, SenseConfigDTO dto);

    /** 批量上报脱敏行为特征（幂等累加） */
    int report(Long userId, List<BehaviorReportDTO> items);

    /** 我的行为数据（自查 + 导出用） */
    List<BehaviorFeature> myBehavior(Long userId, LocalDate from, LocalDate to);

    /** 一键清空我的全部行为数据 */
    int clearBehavior(Long userId);

    /** 生成今日推测（内部统筹三通路） */
    Map<String, Object> generate(Long userId, Integer triggerType, String text);

    /** 获取今日待确认推测；无推荐时返回 hasSuggestion=false 与原因 */
    Map<String, Object> today(Long userId);

    /** 采用推测 → 生成正式日记（source=2） */
    Long confirm(Long userId, Long predictionId, SenseConfirmDTO dto);

    /**
     * 系统自动代记（无感补齐打卡）：当天有行为痕迹、且还没有任何日记时，
     * 直接把推测落成一条 source=3 的日记，用户不打开 App 也能保住连续记录。
     *
     * @param force true=用户本人在页面上主动点「帮我代记」，此时不要求当天有行为数据
     * @return null=已写入；非 null=没写的原因（已有记录 / 无行为数据 / 置信度不足），供前端原样提示
     */
    String autoFill(Long userId, boolean force);

    /** 定时任务用：给所有开启无感记录、当天有痕迹但没写日记的用户自动代记，返回成功人数 */
    int autoFillForAll();

    /** 忽略该推测 */
    void reject(Long userId, Long predictionId);

    /** 我的推测历史（分页） */
    Map<String, Object> history(Long userId, long page, long size);

    /** 命中率统计（采纳率 + 精确命中率 + 分类别） */
    Map<String, Object> accuracy(Long userId);

    /** 短文本即时情感分析（原文不落库） */
    Map<String, Object> analyzeText(Long userId, String text);

    /** 特征-情绪关联数据（散点 / 热力 / 雷达） */
    Map<String, Object> correlation(Long userId, int days);

    /** 手动录入 vs 系统推测 双序列（图表分色渲染用） */
    Map<String, Object> timeline(Long userId, int days);

    /** 把过期未处理的候选置为已过期（定时任务） */
    int expireOld();

    /** 保留期到期物理清理（定时任务） */
    int cleanRetention();

    /** 词典重新加载（后台改了词典后调用） */
    void reloadLexicon();

    /** 词典规模（管理端展示用） */
    int lexiconSize();
}
