package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_test_record")
public class PsyRecord {
    @TableId(type = IdType.AUTO)
    private Long recordId;
    private Long userId;
    private Long testId;
    private Integer totalScore;
    private Integer score;      // 实际得分
    private String level;       // 结果等级（如：状态良好 / 中度关注）
    private String tip;         // 结果建议
    private Integer resultEmotion;
    private LocalDateTime createTime;
}