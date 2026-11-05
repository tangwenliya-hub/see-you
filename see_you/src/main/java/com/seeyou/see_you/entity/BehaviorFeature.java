package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 无感记录 - 脱敏行为特征。
 *
 * 【隐私红线】本实体对应的表**只有整数与枚举列**：
 *  · 不含 URL、不含点击目标（DOM 节点）、不含任何用户输入文本；
 *  · hour_mask 用 24 位掩码表达「哪些小时活跃过」，而不是逐小时存一条明细；
 *  · userId 列由服务端用 JWT 里的身份写入，绝不接受前端传入。
 * 任何给本表加 String 列的改动都属于破坏隐私设计，务必先讨论。
 */
@Data
@TableName("psy_behavior_feature")
public class BehaviorFeature {
    @TableId(type = IdType.AUTO)
    private Long featureId;
    private Long userId;
    private LocalDate statDate;
    /** 场景枚举：home / diary / write / fm / test / classroom / profile / assistant */
    private String sceneCode;
    /** 该场景当日累计停留毫秒数 */
    private Long stayMs;
    /** 当日进入该场景次数 */
    private Integer visitCnt;
    /** 当日该场景点击次数（仅计数） */
    private Integer clickCnt;
    /** 24 小时活跃位图，bit i = 1 表示第 i 小时活跃过 */
    private Long hourMask;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
