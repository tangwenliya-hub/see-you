package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_session")
public class SysUserSession {
    @TableId(type = IdType.AUTO)
    private Long sessionId;
    private Long userId;
    private String token;
    private String device;
    private String ip;
    private String userAgent;
    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;
    private Integer status; // 1=在线 0=已下线
}
