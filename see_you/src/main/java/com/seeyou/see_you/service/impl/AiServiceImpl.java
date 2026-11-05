package com.seeyou.see_you.service.impl;

import com.seeyou.see_you.service.AiService;
import com.seeyou.see_you.service.PsyAiWordsService;
import com.seeyou.see_you.entity.PsyAiWords;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private PsyAiWordsService psyAiWordsService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    /** 带连接/读取超时的 RestTemplate，避免大模型无响应时前端一直转圈 */
    private RestTemplate restTemplate;

    // 从配置文件读取 AI API 配置
    @Value("${ai.api-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:Qwen/Qwen2.5-7B-Instruct}")
    private String model;

    @Value("${ai.timeout:15000}")
    private int timeout;

    @PostConstruct
    public void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(3000, timeout));
        factory.setReadTimeout(Math.max(3000, timeout));
        this.restTemplate = new RestTemplate(factory);
    }

    // 系统提示词：设定AI为温柔的情绪陪伴助手
    private static final String SYSTEM_PROMPT = """
        你是一个温柔、耐心、有同理心的情绪陪伴助手，名字叫"绪见AI"。
        你的角色是用户的知心朋友，擅长倾听和给予温暖的情绪支持。
        
        回复要求：
        1. 语气温柔治愈，像好朋友一样聊天，不要太官方
        2. 先共情，再给建议（如果有的话）
        3. 回复不要太长，100字以内，分1-2段
        4. 适当使用emoji表情，但不要太多
        5. 不要说"作为AI"之类的话，就像真人朋友一样
        6. 如果用户提到自杀、自残等严重问题，要认真对待并建议寻求专业帮助
        7. 记住用户说的话，上下文连贯
        """;

    // 关键词库本地缓存（5 分钟），避免每次降级都全表扫描
    private volatile List<PsyAiWords> wordsCache = null;
    private volatile long wordsCacheTime = 0;
    private static final long WORDS_CACHE_TTL = 5 * 60 * 1000L;

    // 默认回复兜底
    private static final String[] DEFAULT_REPLIES = {
        "我在听呢，慢慢说~ 🤗",
        "嗯嗯，我能感受到你的心情。",
        "谢谢你愿意和我分享，有什么我能帮到你的吗？",
        "每个人的情绪都值得被认真对待，你已经很棒了！💪",
        "我一直在这里陪着你，不用着急。",
        "听起来你最近经历了不少，辛苦了。",
        "无论发生什么，你都值得被温柔对待。🌸"
    };

    @Override
    public String chat(String userMessage, Long userId) {
        return chat(userMessage, userId, null);
    }

    @Override
    public String chat(String userMessage, Long userId, List<Map<String, String>> history) {
        // 1. 优先尝试调用大模型API（上下文由调用方按会话从数据库读出后传入）
        try {
            String aiReply = callAiApi(userMessage, history);
            if (aiReply != null && !aiReply.isBlank()) {
                return aiReply;
            }
        } catch (Exception e) {
            log.warn("调用AI API失败，降级到关键词匹配: {}", e.getMessage());
        }

        // 2. 失败降级：关键词匹配
        String keywordReply = matchKeywordReply(userMessage);
        if (keywordReply != null && !keywordReply.isBlank()) {
            return keywordReply;
        }

        // 3. 最终兜底：随机默认回复
        return DEFAULT_REPLIES[random.nextInt(DEFAULT_REPLIES.length)];
    }

    /**
     * 调用AI大模型API（DeepSeek / 硅基流动，OpenAI 兼容协议）
     * 上下文不再放内存：由 Controller 按 sessionId 从 psy_ai_chat 读出后传入，
     * 服务重启、换机器都不会失忆，不同会话之间也不会串台。
     */
    private String callAiApi(String userMessage, List<Map<String, String>> history) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI API Key 未配置，跳过API调用");
            return null;
        }

        try {
            // 构建消息列表
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

            // 添加上下文（已由调用方裁剪为最近 N 轮，按时间正序）
            if (history != null && !history.isEmpty()) {
                messages.addAll(history);
            }

            // 添加当前用户消息
            messages.add(Map.of("role", "user", "content", userMessage));

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            log.info("调用AI API: model={}, historySize={}, userMsg={}", model,
                    history == null ? 0 : history.size(), userMessage);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    String content = choices.get(0).path("message").path("content").asText();
                    log.info("AI API 调用成功，回复: {}", content);
                    return content == null ? null : content.trim();
                }
                log.warn("AI API 返回体缺少 choices: {}", response.getBody());
            } else {
                log.warn("AI API 返回非200状态码: {}, body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("AI API调用异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 关键词匹配回复（按 sort 升序取第一个命中；自杀/自残类 sort 最小，优先生效）
     */
    private String matchKeywordReply(String userMsg) {
        if (userMsg == null || userMsg.isBlank()) return null;
        try {
            List<PsyAiWords> wordsList = loadWords();
            if (wordsList.isEmpty()) return null;

            for (PsyAiWords words : wordsList) {
                String keyword = words.getKeyword();
                if (keyword != null && !keyword.isBlank() && userMsg.contains(keyword)) {
                    return words.getReplyText();
                }
            }
        } catch (Exception e) {
            log.warn("关键词匹配异常: {}", e.getMessage());
        }
        return null;
    }

    private List<PsyAiWords> loadWords() {
        long now = System.currentTimeMillis();
        if (wordsCache != null && now - wordsCacheTime < WORDS_CACHE_TTL) {
            return wordsCache;
        }
        List<PsyAiWords> list = psyAiWordsService.list(
            new LambdaQueryWrapper<PsyAiWords>()
                .eq(PsyAiWords::getStatus, 1)
                .orderByAsc(PsyAiWords::getSort)
        );
        list = list == null ? Collections.emptyList()
                : list.stream().filter(w -> w.getKeyword() != null && !w.getKeyword().isBlank())
                       .collect(Collectors.toList());
        wordsCache = list;
        wordsCacheTime = now;
        return list;
    }
}
