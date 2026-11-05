package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("psy_test_option")
public class PsyOption {
    @TableId(type = IdType.AUTO)
    private Long optionId;
    private Long questionId;
    private String optionText;
    private Integer score;
}