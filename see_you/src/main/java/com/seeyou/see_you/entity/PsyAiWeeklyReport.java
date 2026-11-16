package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 情绪周报（每人每周一条，按 (user_id, week_start) 唯一）。
 * 缓存意义：同一天内重复打开看板不重复调 LLM（成本控制）；点「刷新」才重新生成覆盖。
 */
@Data
@TableName("psy_ai_weekly_report")
public class PsyAiWeeklyReport {
    @TableId(type = IdType.AUTO)
    private Long reportId;
    private Long userId;
    /** 报告覆盖区间起点（近 7 天，含当天），也是与用户联合唯一的业务键 */
    private LocalDate weekStart;
    private LocalDate weekEnd;
    /** AI 生成的周报正文 */
    private String content;
    /** 生成时纳入的日记篇数（0 = 当周暂无日记） */
    private Integer diaryCount;
    private LocalDateTime createTime;
}
