package com.seeyou.see_you.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long commentId;
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer resourceType;
    private Long resourceId;
    private String commentText;
    private Integer status;
    private LocalDateTime createTime;
}
