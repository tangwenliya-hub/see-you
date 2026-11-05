package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 无感记录 - 情绪推测候选。
 *
 * 【为什么单独一张表，而不是直接写 psy_diary？】
 * 推测结果属于「待用户确认的草稿」。直接入库会让系统替用户造数据，
 * 既违背「用户拥有最终决定权」的设计原则，又会污染后续统计与再次推测的样本。
 * 只有 confirm_status 变为 1（已采用）时，才会生成一条 source=2 的正式日记。
 */
@Data
@TableName("psy_emotion_prediction")
public class EmotionPrediction {
    @TableId(type = IdType.AUTO)
    private Long predictionId;
    private Long userId;
    private LocalDate targetDate;
    /** 1=系统定时 2=用户主动 3=文本即时 */
    private Integer triggerType;
    /** Top1 推测情绪 1~9 */
    private Integer suggestEmotion;
    /** 推测情绪强度 1~3 */
    private Integer suggestIntensity;
    /** 置信度 0~1，低于阈值不产生推荐 */
    private java.math.BigDecimal confidence;
    /** Top3 候选 JSON */
    private String candidates;
    /** 给用户看的可解释依据 */
    private String reason;
    /** 三路贡献明细 JSON */
    private String detail;
    /** 本次使用的 7 维行为特征快照 JSON */
    private String featureSnapshot;
    /** 0=待确认 1=已采用 2=已忽略 3=已过期 */
    private Integer confirmStatus;
    /** 用户最终确认的情绪（可能与推测不同，用于计算命中率） */
    private Integer confirmEmotion;
    private Long diaryId;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
