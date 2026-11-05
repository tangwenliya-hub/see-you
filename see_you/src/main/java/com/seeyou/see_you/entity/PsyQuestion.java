package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_test_question")
public class PsyQuestion {
    @TableId(type = IdType.AUTO)
    private Long questionId;
    private Long testId;
    private String questionContent;
    private LocalDateTime createTime;
}