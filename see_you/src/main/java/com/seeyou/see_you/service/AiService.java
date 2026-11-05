package com.seeyou.see_you.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface AiService {
    /**
     * 调用AI大模型获取回复（无上下文）
     * @param userMessage 用户消息
     * @param userId 用户ID
     * @return AI回复内容
     */
    String chat(String userMessage, Long userId);

    /**
     * 调用AI大模型获取回复（带上下文，上下文由调用方从数据库按会话读出）
     * @param userMessage 用户消息
     * @param userId 用户ID
     * @param history 历史消息，按时间正序，元素为 {role: user|assistant, content: 文本}
     * @return AI回复内容
     */
    String chat(String userMessage, Long userId, List<Map<String, String>> history);
}
