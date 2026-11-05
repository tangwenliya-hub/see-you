package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private String bio;        // 个人简介/备注
    private Integer role;
    private Integer status;  // 1=正常 0=禁用
    private String theme;      // 主题色名称（green/blue/purple...）
    private Integer darkMode;  // 深色模式 0=关闭 1=开启
    private String avatarFrame; // 头像装饰框名称
    private String profileBg;  // 个人中心背景图

    // ===== 无感记录模块开关（隐私设计：默认全关 = opt-in 知情授权）=====
    /** 无感记录总开关 0=关闭（默认） 1=开启 */
    private Integer senseEnabled;
    /** 行为特征采集子开关 0=关 1=开 */
    private Integer senseBehavior;
    /** 随笔文本情感分析子开关 0=关 1=开 */
    private Integer senseText;
    /** 用户授权时间（隐私审计用） */
    private java.time.LocalDateTime senseGrantAt;
    /** 最近一次关闭时间 */
    private java.time.LocalDateTime senseRevokeAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}