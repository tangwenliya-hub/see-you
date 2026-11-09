package com.seeyou.see_you.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 危机通道第二级：LLM 情绪研判（两级漏斗的「精判」层）。
 *
 * <p><b>架构定位（答辩用）</b>：危机词表是「召回」层——毫秒级、零成本，但词典法天然有漏报
 * （用户换个说法就匹配不上，如「死掉就不难受了」早期没收录「死掉」）。本组件是「精判」层：
 * 词表未命中时，把正文交给大模型做风险分类，补上语义层的盲区。两层互补：误报由词表白名单
 * + LLM 把关，漏报由 LLM 兜底。
 *
 * <h3>隐私与安全红线</h3>
 * <ul>
 *   <li>发给 LLM 的<b>只有日记正文本身</b>，不携带 user_id、昵称等任何身份信息；</li>
 *   <li>日志只记录判定结果（风险/安全），<b>不落库、不打印用户原文</b>（与 CrisisWordMatcher 同红线）；</li>
 *   <li>LLM 只是辅助研判，结论仍走 RiskAlert 的三级预警体系，且文案必带免责声明——不诊断、不替代专业帮助；</li>
 *   <li>API 未配置 key、超时、返回异常 → 一律静默返回 null，预警链路<b>永不因 AI 故障而失效</b>（降级为纯规则模式）。</li>
 * </ul>
 */
@Component
public class CrisisAiJudge {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CrisisAiJudge.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 带 8 秒连接/读取超时的独立 RestTemplate：预警链路不能被慢 LLM 拖住 */
    private RestTemplate restTemplate;

    @Value("${ai.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @PostConstruct
    public void initRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(f);
    }

    /** 判定结果：risk=是否需要生成关怀预警；reason=给管理员看的判定理由（不含原文） */
    public record Verdict(boolean risk, String reason) {}

    private static final String SYSTEM_PROMPT = """
            你是心理健康产品的安全辅助研判模块。用户写了一条日记，请判断这段表达是否包含
            需要被认真对待的危机信号。

            判定为需要关怀（risk=true）的信号：
            - 自伤/自杀意念，强烈的绝望感或「消失/解脱」倾向
            - 伤害他人的意念或威胁：扬言要持械伤人、报复暴力、针对现实中的具体对象表达行凶打算
              （如「准备拿刀砍人」「想捅了他」）——即使是愤怒宣泄的语气也要标记，
              我们要推送的是情绪疏导关怀，不是报警
            - 明显的痛苦求助信号

            判定为安全（risk=false）：
            - 日常吐槽、夸张修辞（如「笑死掉了」「累死了」「想砍了这堆代码」——对象是事物不是人）
            - 普通负面情绪宣泄（生气/委屈/烦躁，但未涉及暴力威胁）

            只输出一行 JSON，不要输出任何其它内容、不要用 markdown 代码块：
            {"risk": true或false, "reason": "不超过30字的判定理由"}

            注意：你不是医生，不做任何诊断或结论性判断，只做「是否需要推送关怀提示」的分类。
            """;

    /**
     * 研判一条日记正文。
     *
     * @return 判定结果；任何失败（未配 key / 超时 / 返回异常 / 解析失败）返回 null = 放弃精判，走纯规则
     */
    public Verdict judge(String content) {
        if (content == null || content.isBlank()) return null;
        if (apiKey == null || apiKey.isBlank()) return null;   // 未配 key：静默降级

        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", content)));
            body.put("temperature", 0.1);   // 分类任务要稳定，不要发散
            body.put("max_tokens", 100);    // 只需要一行 JSON
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> resp =
                    restTemplate.postForEntity(apiUrl, new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return null;

            String out = extractContent(resp.getBody());
            if (out == null) return null;
            return parseVerdict(out);
        } catch (Exception e) {
            // 降级红线：AI 失败绝不影响预警主流程，也不把异常抛给日记保存链路。
            // 只记录失败事实，不打印正文（隐私红线）
            log.warn("[AI研判] 调用失败，本次放弃精判（预警降级为纯规则模式）: {}", e.getMessage());
            return null;
        }
    }

    /** 从 OpenAI 兼容返回体里取 message.content */
    private String extractContent(String respBody) {
        try {
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String c = choices.get(0).path("message").path("content").asText(null);
                return c == null ? null : c.trim();
            }
        } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
        return null;
    }

    /** 解析模型输出：容忍 ```json 围栏、前后杂文字（取第一个 {...} 片段） */
    private Verdict parseVerdict(String out) {
        try {
            String s = out.replace("```json", "").replace("```", "").trim();
            int s0 = s.indexOf('{'), s1 = s.lastIndexOf('}');
            if (s0 < 0 || s1 <= s0) return null;
            JsonNode n = objectMapper.readTree(s.substring(s0, s1 + 1));
            return new Verdict(n.path("risk").asBoolean(false), n.path("reason").asText(""));
        } catch (Exception e) {
            return null;
        }
    }
}
