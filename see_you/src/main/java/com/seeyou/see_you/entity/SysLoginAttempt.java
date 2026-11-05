package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_login_attempt")
public class SysLoginAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private Integer failCount;
    private LocalDateTime lockUntil;
    private LocalDateTime updateTime;
}
