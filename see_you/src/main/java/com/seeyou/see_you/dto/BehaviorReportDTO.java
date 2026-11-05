package com.seeyou.see_you.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * 前端上报的脱敏行为特征条目。
 *
 * 【隐私约束】本 dto **刻意没有任何 String 型内容字段**：
 * sceneCode 是枚举字符串，date 是日期，其余全是数值。
 * 一旦有人给这里加 content / url / title 之类的字段，
 * 就等于把「无感」变成「有感采集」，必须先过隐私评审。
 */
@Data
public class BehaviorReportDTO {
    /** 场景枚举：home/diary/write/fm/test/classroom/profile/assistant */
    private String sceneCode;
    /** 归属日期；为空时按服务端当天处理 */
    private LocalDate date;
    /** 停留毫秒数 */
    private Long stayMs;
    /** 进入次数 */
    private Integer visitCnt;
    /** 点击次数（只计数） */
    private Integer clickCnt;
    /** 24 小时活跃位图 */
    private Long hourMask;
}
