package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_audio")
public class PsyAudio {
    @TableId(type = IdType.AUTO)
    private Long audioId;
    private String audioName;
    private String audioUrl;
    private String category;
    private String coverImg;
    private Integer playCount;
    private String description;
    private String lyric;
    private LocalDateTime createTime;
}