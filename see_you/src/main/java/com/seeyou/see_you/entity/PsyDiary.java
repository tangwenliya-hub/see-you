package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("psy_diary")
public class PsyDiary {
    @TableId(type = IdType.AUTO)
    private Long diaryId;
    private Long userId;
    private String diaryContent;
    private Integer emotion;
    private Integer intensity; // 情绪强度 1=轻度 2=中度 3=重度
    private BigDecimal sleepHours;   // 睡眠时长（小时），生活因子
    private Integer stressLevel;     // 压力自评 1~5，生活因子
    private Integer exerciseMin;     // 运动时长（分钟），生活因子
    private Integer isPublic;  // 0=私密 1=公开
    /**
     * 数据来源：
     * 1=用户手动录入（唯一计入情绪画像与经验值）
     * 2=系统推测后用户确认
     * 3=系统自动代记（用户当天没打开 App，由定时任务补齐的打卡记录，可改写为 1）
     * 图表据此分色渲染
     */
    private Integer source;
    /** 来源为 2 时关联的推测记录 ID，用于溯源推测 quality */
    private Long predictionId;
    private String imageUrl;   // 日记配图 URL
    private LocalDate diaryDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}