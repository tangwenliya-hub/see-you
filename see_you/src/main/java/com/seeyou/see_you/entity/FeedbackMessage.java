package com.seeyou.see_you.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_feedback_message")
public class FeedbackMessage {
    @TableId(type = IdType.AUTO)
    private Long messageId;
    private Long feedbackId;
    /** user=用户反馈/补充, admin=管理员回复 */
    private String senderType;
    private String content;
    private LocalDateTime createTime;
}