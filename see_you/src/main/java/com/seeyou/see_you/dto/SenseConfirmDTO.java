package com.seeyou.see_you.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采用推测时提交的内容。
 *
 * 设计要点：**用户可以对推测结果做任意修改**。
 * emotion / intensity 可以和推测值不同，系统会把用户最终的选择记进 confirm_emotion，
 * 用于计算命中率 —— 这才是「用户拥有最终决定权」在数据结构上的体现。
 */
@Data
public class SenseConfirmDTO {
    /** 用户最终确认的情绪（可与推测不同） */
    private Integer emotion;
    /** 情绪强度 1~3 */
    private Integer intensity;
    /** 随笔正文 */
    private String content;
    /** 睡眠时长（小时） */
    private BigDecimal sleepHours;
    /** 压力自评 1~5 */
    private Integer stressLevel;
    /** 运动时长（分钟） */
    private Integer exerciseMin;
    /** 归属日期，默认今天 */
    private LocalDate diaryDate;
}
