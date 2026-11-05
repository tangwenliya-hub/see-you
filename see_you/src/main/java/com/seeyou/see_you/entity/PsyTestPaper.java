package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_test")
public class PsyTestPaper {
    @TableId(type = IdType.AUTO)
    private Long testId;
    private String testName;
    private String testDesc;
    private Integer minScore;
    private Integer maxScore;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private Integer questionCount;
    @TableField(exist = false)
    private Integer takenCount;
    private Integer status;
}