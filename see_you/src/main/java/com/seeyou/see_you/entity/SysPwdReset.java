package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_pwd_reset")
public class SysPwdReset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String token;
    private String code;
    private LocalDateTime expireTime;
    private Integer used;
    private LocalDateTime createTime;
}
