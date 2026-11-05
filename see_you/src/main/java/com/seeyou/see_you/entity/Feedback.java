package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long feedbackId;
    private Long userId;
    private String title;          // 反馈标题
    private String type;           // 类型：suggest/bug/ui/content/other
    private String feedbackContent;// 详细内容
    private String replyText;      // 管理员回复
    private String userSupplement; // 用户补充回复（回复管理员）
    private LocalDateTime supplementTime; // 用户补充时间
    private Integer status;        // 0未读 1处理中 2已完成
    private Integer deleted;       // 0正常 1用户已删（软删）
    private LocalDateTime createTime;
}