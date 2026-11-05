package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_ai_chat")
public class AiChat {
    @TableId(type = IdType.AUTO)
    private Long chatId;
    private Long userId;
    /** 会话 ID：一次连续对话一个 id，前端多会话与后端历史以此为单位对齐 */
    private String sessionId;
    private String userMsg;
    private String aiReply;
    private LocalDateTime createTime;
}