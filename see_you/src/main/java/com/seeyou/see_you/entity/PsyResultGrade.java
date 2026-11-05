package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_test_result_grade")
public class PsyResultGrade {
    @TableId(type = IdType.AUTO)
    private Long gradeId;
    private Long testId;
    private Integer minScore;
    private Integer maxScore;
    private String gradeName;
    private String description;
    private String suggestion;
    private LocalDateTime createTime;
}
