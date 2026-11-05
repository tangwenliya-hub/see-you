package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.AiChat;
import com.seeyou.see_you.service.AiChatService;
import com.seeyou.see_you.service.AiService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;
import com.seeyou.see_you.util.PageLimit;

/**
 * AI 对话：以「会话 sessionId」为单位组织。
 * 一轮对话 = 一条记录（userMsg + aiReply），同一个 sessionId 下的记录构成一次连续对话。
 * 上下文按 sessionId 从数据库重建，不再依赖进程内存，因此重启不失忆、会话之间不串台。
 */
@RestController
@RequestMapping("/aiChat")
public class AiChatController {
    @Resource
    private AiChatService aiChatService;
    @Resource
    private AiService aiService;

    /** 单会话最多纳入上下文的轮数 */
    private static final int CONTEXT_ROUNDS = 10;

    /** 图片消息在库中的存储前缀：userMsg = 前缀 + 图片 URL */
    private static final String IMAGE_PREFIX = "[图片] ";

    /**
     * 图片无法直接喂给大模型，转成一段文字描述再发过去；
     * 库里仍保存原始 "[图片] url"，前端据此还原成图片气泡。
     */
    private String toPrompt(String userMsg) {
        if (userMsg != null && userMsg.startsWith(IMAGE_PREFIX)) {
            return "（用户发来了一张图片，请自然地回应，温柔地邀请ta说说图片里的场景和当时的心情。不要说你看不到图片。）";
        }
        return userMsg;
    }

    // ========== 会话级接口（前端 AI 页使用）==========

    /**
     * 会话列表：按最后一条消息时间倒序
     */
    @GetMapping("/session/list")
    public Result<List<Map<String, Object>>> sessionList(HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");

        List<AiChat> all = aiChatService.list(
            new LambdaQueryWrapper<AiChat>()
                .eq(AiChat::getUserId, uid)
                .orderByAsc(AiChat::getCreateTime)
                .orderByAsc(AiChat::getChatId)
        );

        // 按 sessionId 分组（保持插入顺序，稍后按最后时间排序）
        Map<String, List<AiChat>> groups = new LinkedHashMap<>();
        for (AiChat c : all) {
            String sid = c.getSessionId() == null || c.getSessionId().isBlank() ? "default" : c.getSessionId();
            groups.computeIfAbsent(sid, k -> new ArrayList<>()).add(c);
        }

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (Map.Entry<String, List<AiChat>> e : groups.entrySet()) {
            List<AiChat> list = e.getValue();
            AiChat first = list.get(0);
            AiChat last = list.get(list.size() - 1);
            String title = trim(first.getUserMsg(), 12, "新对话");

            Map<String, Object> m = new HashMap<>();
            m.put("sessionId", e.getKey());
            m.put("title", title);
            m.put("lastTime", last.getCreateTime());
            m.put("preview", trim(last.getAiReply() != null && !last.getAiReply().isBlank()
                    ? last.getAiReply() : last.getUserMsg(), 20, ""));
            m.put("rounds", list.size());
            sessions.add(m);
        }
        sessions.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("lastTime");
            LocalDateTime tb = (LocalDateTime) b.get("lastTime");
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return Result.success(sessions);
    }

    /**
     * 新建会话：只分配一个 sessionId，真正落库在第一次发言时（避免空会话刷屏）
     */
    @PostMapping("/session/create")
    public Result<Map<String, String>> sessionCreate(HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        String sessionId = newSessionId(uid);
        return Result.success(Map.of("sessionId", sessionId));
    }

    /**
     * 某个会话的全部消息（按时间正序）
     */
    @GetMapping("/session/messages")
    public Result<List<AiChat>> sessionMessages(@RequestParam String sessionId, HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        if (!ownSession(sessionId, uid)) return Result.error(403, "无权访问该会话");

        List<AiChat> list = aiChatService.list(
            new LambdaQueryWrapper<AiChat>()
                .eq(AiChat::getSessionId, sessionId)
                .eq(AiChat::getUserId, uid)
                .orderByAsc(AiChat::getCreateTime)
                .orderByAsc(AiChat::getChatId)
        );
        return Result.success(list);
    }

    /**
     * 删除整个会话及其消息
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Boolean> sessionRemove(@PathVariable String sessionId, HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        if (!ownSession(sessionId, uid)) return Result.error(403, "无权删除该会话");

        aiChatService.remove(new LambdaQueryWrapper<AiChat>()
            .eq(AiChat::getSessionId, sessionId)
            .eq(AiChat::getUserId, uid));
        return Result.success(true);
    }

    /**
     * 清空会话消息（保留会话本身，前端置空继续用）
     */
    @DeleteMapping("/session/{sessionId}/clear")
    public Result<Boolean> sessionClear(@PathVariable String sessionId, HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        if (!ownSession(sessionId, uid)) return Result.error(403, "无权操作该会话");

        aiChatService.remove(new LambdaQueryWrapper<AiChat>()
            .eq(AiChat::getSessionId, sessionId)
            .eq(AiChat::getUserId, uid));
        return Result.success(true);
    }

    /**
     * 删除某一轮对话
     */
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id, HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        AiChat exist = aiChatService.getById(id);
        if (exist == null) return Result.error(404, "记录不存在");
        if (!Objects.equals(exist.getUserId(), uid) && !isAdmin(request)) {
            return Result.error(403, "无权删除该消息");
        }
        return aiChatService.removeById(id) ? Result.success(true) : Result.error(500, "删除失败");
    }

    /**
     * 重新生成某一轮的 AI 回复：沿用原问题 + 该轮之前的历史
     */
    @PostMapping("/{id:\\d+}/regenerate")
    public Result<AiChat> regenerate(@PathVariable Long id, HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        AiChat exist = aiChatService.getById(id);
        if (exist == null) return Result.error(404, "记录不存在");
        if (!Objects.equals(exist.getUserId(), uid)) return Result.error(403, "无权操作该消息");

        String sessionId = exist.getSessionId();
        List<Map<String, String>> history = buildHistory(sessionId, uid, exist.getChatId());
        String aiReply = aiService.chat(toPrompt(exist.getUserMsg()), uid, history);

        AiChat update = new AiChat();
        update.setChatId(id);
        update.setAiReply(aiReply);
        update.setCreateTime(LocalDateTime.now());
        aiChatService.updateById(update);

        exist.setAiReply(aiReply);
        exist.setCreateTime(update.getCreateTime());
        return Result.success(exist);
    }

    /**
     * 发送消息并获取AI回复（必须带 sessionId）
     */
    @PostMapping("/chat")
    public Result<AiChat> chat(@RequestBody AiChat chat, HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        if (chat == null || chat.getUserMsg() == null || chat.getUserMsg().isBlank()) {
            return Result.error(400, "消息不能为空");
        }
        String sessionId = chat.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return Result.error(400, "缺少 sessionId");
        }
        // 归属校验：sessionId 必须属于当前登录用户，杜绝改 userId 越权读写他人会话
        if (!ownSession(sessionId, uid)) return Result.error(403, "无权访问该会话");

        String userMsg = chat.getUserMsg().trim();
        List<Map<String, String>> history = buildHistory(sessionId, uid, null);
        String aiReply = aiService.chat(toPrompt(userMsg), uid, history);

        AiChat record = new AiChat();
        record.setUserId(uid);
        record.setSessionId(sessionId);
        record.setUserMsg(userMsg);
        record.setAiReply(aiReply);
        record.setCreateTime(LocalDateTime.now());
        aiChatService.save(record);

        return Result.success(record);
    }

    // ========== 管理用接口（保留）==========
    // 说明：原 getById / save / update 三个裸接口无归属校验且全项目无调用，已删除（防止越权读写他人会话）

    /**
     * 获取用户的聊天历史记录：非管理员强制只能查自己的
     */
    @GetMapping("/user/list")
    public Result<List<AiChat>> getUserList(@RequestParam(required = false) Long userId,
                                            @RequestParam(defaultValue = "50") Integer limit,
                                            HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");
        Long target = (userId != null && isAdmin(request)) ? userId : uid;

        LambdaQueryWrapper<AiChat> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChat::getUserId, target)
               .orderByDesc(AiChat::getCreateTime)
               .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        List<AiChat> list = aiChatService.list(wrapper);
        Collections.reverse(list);
        return Result.success(list);
    }

    /**
     * 后台分页查询：非管理员强制只能看自己的
     */
    @GetMapping("/list")
    public Result<Page<AiChat>> list(@RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "10") Integer size,
                                      @RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                      HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");

        LambdaQueryWrapper<AiChat> wrapper = new LambdaQueryWrapper<>();
        Long target = (userId != null && isAdmin(request)) ? userId : uid;
        wrapper.eq(AiChat::getUserId, target);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AiChat::getUserMsg, keyword).or().like(AiChat::getAiReply, keyword));
        }
        if (startTime != null) wrapper.ge(AiChat::getCreateTime, startTime);
        if (endTime != null) wrapper.le(AiChat::getCreateTime, endTime);
        wrapper.orderByDesc(AiChat::getCreateTime);
        return Result.success(aiChatService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    // ========== 私有工具 ==========

    /**
     * 构造上下文：取该会话最近 CONTEXT_ROUNDS 轮；excludeChatId 非空时（重新生成）只取它之前的
     */
    private List<Map<String, String>> buildHistory(String sessionId, Long uid, Long excludeChatId) {
        LambdaQueryWrapper<AiChat> wrapper = new LambdaQueryWrapper<AiChat>()
            .eq(AiChat::getSessionId, sessionId)
            .eq(AiChat::getUserId, uid);
        if (excludeChatId != null) {
            wrapper.lt(AiChat::getChatId, excludeChatId);
        }
        wrapper.orderByDesc(AiChat::getChatId).last("LIMIT " + CONTEXT_ROUNDS);
        List<AiChat> recent = aiChatService.list(wrapper);
        Collections.reverse(recent);

        List<Map<String, String>> history = new ArrayList<>();
        for (AiChat c : recent) {
            if (c.getUserMsg() != null && !c.getUserMsg().isBlank()) {
                history.add(Map.of("role", "user", "content", toPrompt(c.getUserMsg())));
            }
            if (c.getAiReply() != null && !c.getAiReply().isBlank()) {
                history.add(Map.of("role", "assistant", "content", c.getAiReply()));
            }
        }
        return history;
    }

    /**
     * 会话归属校验：新会话由本接口分配 id，格式为 s_{userId}_{时间戳}_{随机}
     */
    private boolean ownSession(String sessionId, Long uid) {
        return sessionId != null && sessionId.startsWith("s_" + uid + "_");
    }

    private String newSessionId(Long uid) {
        return "s_" + uid + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof String s && s.matches("\\d+")) return Long.valueOf(s);
        return null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        Object v = request.getAttribute("role");
        return v instanceof Integer i && i == 1;
    }

    private String trim(String s, int max, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
