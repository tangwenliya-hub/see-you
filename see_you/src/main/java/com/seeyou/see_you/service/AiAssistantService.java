package com.seeyou.see_you.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AI 生活助手：情绪周报 / 日记润色 / 语音写日记 三个贴近生活场景的统一 LLM/ASR 调用通道。
 *
 * <p><b>架构定位（答辩用）</b>：AI 陪伴（AiServiceImpl）是被动应答，无感推测 + 预警是主动洞察，
 * 本组件补上第三个环节——「生活化入口」：把大模型能力渗进写日记、看数据的日常动作里。
 * 三个功能共用同一通道，新增场景只需拼不同的 prompt，不再各写一套 HTTP 调用。
 *
 * <h3>隐私与降级红线（与 CrisisAiJudge 同标准）</h3>
 * <ul>
 *   <li>发给大模型的只有日记内容与统计摘要，不携带 user_id、昵称等身份信息；</li>
 *   <li>语音识别只上传音频片段，转写失败不影响手动输入主链路；</li>
 *   <li>key 未配置 / 超时 / 返回异常 → 抛出带友好提示的 {@link AiUnavailableException}，
 *       由 Controller 转成业务错误码，前端显式透出，绝不静默吞掉误导用户。</li>
 * </ul>
 */
@Slf4j
@Service
public class AiAssistantService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 周报/润色生成耗时可能到 30 秒以上，读超时放宽到 60 秒（对话接口 15 秒不适用） */
    private RestTemplate restTemplate;

    @Value("${ai.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    /** 语音识别（ASR）配置：默认硅基流动 SenseVoice（免费模型），也可换任何 OpenAI 兼容转写接口 */
    @Value("${ai.asr.api-url:https://api.siliconflow.cn/v1/audio/transcriptions}")
    private String asrUrl;

    @Value("${ai.asr.api-key:}")
    private String asrKey;

    @Value("${ai.asr.model:FunAudioLLM/SenseVoiceSmall}")
    private String asrModel;

    @PostConstruct
    public void initRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(f);
    }

    /** AI 能力不可用的专用异常：message 直接给前端透出（不带技术细节堆栈） */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) { super(message); }
    }

    // ==================== 周报 ====================

    private static final String WEEKLY_SYSTEM_PROMPT = """
            你是心理健康应用「绪见」的 AI 周报生成助手。根据用户最近一周的日记与生活数据，写一份温暖的个人情绪周报。

            要求：
            1. 用第二人称「你」，语气温柔真诚，像懂你的朋友，不要官方腔
            2. 结构固定四段，用【】小标题：【本周概览】【情绪起伏】【生活习惯小观察】【下周小建议】
            3. 概览给出记录天数与主要情绪；起伏点出最开心和最低落的一天（结合原始日记内容概括）；习惯观察结合睡眠/压力/运动数据——某项数据缺失就跳过那部分，不要编数字；建议给 1-2 条具体、当天就能做的小事，不说「早睡早起多喝水」这类空话
            4. 总长 250~400 字，可以少量使用 emoji
            5. 你不是医生：不使用任何诊断性词汇，不做医疗结论，只做情绪记录的回顾与陪伴
            """;

    /**
     * 生成情绪周报正文。
     *
     * @param digest 数据摘要（由 Controller 从 psy_diary 组装，纯文本，不含身份信息）
     */
    public String weeklyReport(String digest) {
        return complete(WEEKLY_SYSTEM_PROMPT, digest, 0.7, 800);
    }

    // ==================== 日记润色 ====================

    private static final String POLISH_SYSTEM_PROMPT = """
            你是「绪见」App 的日记润色助手。用户随手写了两句流水账，请你帮忙整理成一段通顺的日记。

            要求：
            1. 忠实于原文：保留用户写下的全部事实、情绪和语气，不编造没发生的事，不把难过改成开心
            2. 像自己写的日记：自然口语化，禁止华丽辞藻和排比堆砌
            3. 长度不超过 250 字；原文很短就润色得很短
            4. 只输出润色后的日记正文，不要任何解释、引号或「好的，以下是润色后的日记」之类的前缀
            """;

    public String polishDiary(String content) {
        return complete(POLISH_SYSTEM_PROMPT, content, 0.6, 400);
    }

    // ==================== 语音转文字 ====================

    /**
     * 上传音频片段到 ASR 接口转写为文字。
     *
     * @param audio    音频字节（前端已统一转成 16kHz 单声道 WAV）
     * @param filename 原始文件名（仅用于表单回显，不参与识别）
     * @return 转写出的文字
     */
    public String transcribe(byte[] audio, String filename) {
        if (asrKey == null || asrKey.isBlank()) {
            throw new AiUnavailableException("语音识别服务未配置（需要 ASR_API_KEY），请先在环境变量中配置后再使用语音输入");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(asrKey);

            ByteArrayResource filePart = new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename == null || filename.isBlank() ? "voice.wav" : filename;
                }
            };
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", filePart);
            form.add("model", asrModel);

            ResponseEntity<String> resp = restTemplate.postForEntity(asrUrl,
                    new HttpEntity<>(form, headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AiUnavailableException("语音识别服务返回异常，请稍后再试或直接手动输入");
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            String text = root.path("text").asText("").trim();
            if (text.isEmpty()) {
                throw new AiUnavailableException("没有听清你说的话，可以靠近一点麦克风再试一次");
            }
            return text;
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI助手] 语音转写失败: {}", e.getMessage());
            throw new AiUnavailableException("语音识别暂时不可用，请稍后再试或直接手动输入");
        }
    }

    // ==================== 底层：OpenAI 兼容 chat completions ====================

    /**
     * 通用补全调用。
     *
     * @return 模型输出正文；key 未配置或调用失败抛 {@link AiUnavailableException}
     */
    private String complete(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiUnavailableException("AI 服务未配置（需要 AI_API_KEY），该功能暂不可用");
        }
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)));
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> resp = restTemplate.postForEntity(apiUrl,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AiUnavailableException("AI 服务返回异常，请稍后再试");
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText("");
                if (!content.isBlank()) return content.trim();
            }
            throw new AiUnavailableException("AI 没有返回有效内容，请稍后再试");
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI助手] LLM 调用失败: {}", e.getMessage());
            throw new AiUnavailableException("AI 服务暂时不可用，请稍后再试");
        }
    }
}
