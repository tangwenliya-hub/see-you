package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("psy_ai_words")
public class PsyAiWords {
    @TableId(type = IdType.AUTO)
    private Long wordId;
    private String keyword;
    private String category;
    private String matchExample;
    private String replyText;
    private Integer status;
    private Integer sort;
}
