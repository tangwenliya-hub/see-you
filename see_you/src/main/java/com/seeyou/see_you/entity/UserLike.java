package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_like")
public class UserLike {
    @TableId(type = IdType.AUTO)
    private Long likeId;
    private Long userId;
    private Integer resourceType;
    private Long resourceId;
    private LocalDateTime createTime;
}
