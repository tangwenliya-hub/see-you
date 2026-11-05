package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 首页轮播 / 系统公告位。
 * 后台配置，前台 Home 页公开读取（不需要登录）。
 */
@Data
@TableName("psy_banner")
public class PsyBanner {
    @TableId(type = IdType.AUTO)
    private Long bannerId;
    private String title;
    private String subtitle;
    private String coverImg;
    /** none-不跳转 diary-写日记 test-测评 video-视频 audio-音频 url-外链 */
    private String linkType;
    private String linkUrl;
    /** home-欢迎页 main-首页顶部 */
    private String position;
    private Integer sort;
    /** 1=启用 0=停用 */
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
