package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_history")
public class PsyHistory {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long userId;
    private Integer resourceType; // 1音频 2视频
    private Long resourceId;
    private Integer playProgress;  // 上次播放到第几秒
    private Integer isFinished;    // 是否播放完 0否 1是
    private Integer duration;     // 总时长(秒)
    private LocalDateTime createTime;
}