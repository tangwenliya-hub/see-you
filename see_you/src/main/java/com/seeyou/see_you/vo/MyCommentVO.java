package com.seeyou.see_you.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 「我的评论」列表项：在评论基础上补上所属资源（音频 / 视频）的名称与封面，
 * 让用户能在个人中心一处看到自己所有评论的审核状态。
 */
@Data
public class MyCommentVO {
    private Long commentId;
    /** 1=音频（绪见 FM） 2=视频（绪见学堂） */
    private Integer resourceType;
    private Long resourceId;
    private String resourceName;
    private String resourceCover;
    private String commentText;
    /** 0=待审核 1=已通过 -1=未通过 */
    private Integer status;
    private LocalDateTime createTime;
}
