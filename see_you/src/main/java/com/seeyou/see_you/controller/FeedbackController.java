package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.Feedback;
import com.seeyou.see_you.entity.FeedbackMessage;
import com.seeyou.see_you.entity.SysUser;
import com.seeyou.see_you.mapper.FeedbackMessageMapper;
import com.seeyou.see_you.service.FeedbackService;
import com.seeyou.see_you.service.FeedbackWriteService;
import com.seeyou.see_you.service.SysUserService;
import com.seeyou.see_you.websocket.FeedbackWebSocketHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.util.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.seeyou.see_you.util.PageLimit;
import com.seeyou.see_you.config.RequireAdmin;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {
    @Resource
    private FeedbackService feedbackService;
    @Resource
    private SysUserService sysUserService;
    @Resource
    private FeedbackWebSocketHandler wsHandler;
    @Resource
    private FeedbackMessageMapper msgMapper;
    /** 多步写入统一走它，保证主表与消息表同事务（B16） */
    @Resource
    private FeedbackWriteService feedbackWriteService;

    /** 🆕 获取某条反馈的完整聊天记录 */
    @GetMapping("/{id}/messages")
    public Result<java.util.List<FeedbackMessage>> getMessages(@PathVariable Long id, HttpServletRequest request){
        // 越权防护：只有反馈本人（或管理员）能看往来消息
        Feedback owner = feedbackService.getById(id);
        if (owner == null) return Result.error(404, "反馈不存在");
        Long myId = (Long) request.getAttribute("userId");
        Integer myRole = (Integer) request.getAttribute("role");
        boolean isAdmin = Integer.valueOf(1).equals(myRole);
        if (!isAdmin && (myId == null || !myId.equals(owner.getUserId()))) {
            return Result.error(403, "无权查看该反馈消息");
        }
        java.util.List<FeedbackMessage> msgs = msgMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FeedbackMessage>()
                .eq("feedback_id", id).orderByAsc("create_time")
        );
        // 如果消息表为空（老数据），把 Feedback 主表的内容组装进去
        if (msgs.isEmpty()) {
            Feedback f = feedbackService.getById(id);
            if (f != null) {
                // 用户初始反馈
                FeedbackMessage m1 = new FeedbackMessage();
                m1.setFeedbackId(id);
                m1.setSenderType("user");
                m1.setContent(f.getFeedbackContent());
                m1.setCreateTime(f.getCreateTime());
                msgs.add(m1);
                // 管理员回复
                if (f.getReplyText() != null) {
                    FeedbackMessage m2 = new FeedbackMessage();
                    m2.setFeedbackId(id);
                    m2.setSenderType("admin");
                    m2.setContent(f.getReplyText());
                    // reply 没有单独存时间，用 createTime + 1s
                    m2.setCreateTime(f.getCreateTime() != null
                        ? f.getCreateTime().plusSeconds(1) : LocalDateTime.now());
                    msgs.add(m2);
                }
                // 用户补充
                if (f.getUserSupplement() != null) {
                    FeedbackMessage m3 = new FeedbackMessage();
                    m3.setFeedbackId(id);
                    m3.setSenderType("user");
                    m3.setContent(f.getUserSupplement());
                    m3.setCreateTime(f.getSupplementTime());
                    msgs.add(m3);
                }
            }
        }
        return Result.success(msgs);
    }

    @GetMapping("/{id:\\d+}")
    public Result<Feedback> getById(@PathVariable Long id, HttpServletRequest request){
        // 越权防护：非管理员只能看自己的反馈
        Feedback f = feedbackService.getById(id);
        if (f == null) return Result.error(404, "反馈不存在");
        Long myId = (Long) request.getAttribute("userId");
        Integer myRole = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(myRole) && (myId == null || !myId.equals(f.getUserId()))) {
            return Result.error(403, "无权查看该反馈");
        }
        return Result.success(f);
    }

    /** 列表（带用户名）- 支持按 userId 过滤 + keyword 搜索 + 时间范围 */
    @RequireAdmin
    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer size,
                                                  @RequestParam(required = false) Boolean showDeleted,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Feedback> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        // 按 userId 过滤（Profile 页面用）
        if (userId != null) qw.eq("user_id", userId);
        // 软删除过滤：默认仅展示未删除数据
        if (showDeleted == null || !showDeleted) {
            qw.eq("deleted", 0);
        }
        // 关键词搜索（反馈内容 / 管理员回复 / 用户补充）
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("feedback_content", keyword).or().like("reply_text", keyword).or().like("user_supplement", keyword));
        }
        // 日期范围（一天的起止时刻）
        if (startDate != null) qw.ge("create_time", startDate.atStartOfDay());
        if (endDate != null) qw.le("create_time", endDate.plusDays(1).atStartOfDay());
        qw.orderByDesc("create_time");
        Page<Feedback> fbPage = feedbackService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 50)), qw);
        // 批量查用户
        java.util.List<Long> userIds = fbPage.getRecords().stream()
                .map(Feedback::getUserId).distinct().collect(Collectors.toList());
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            sysUserService.listByIds(userIds).forEach(u -> userMap.put(u.getUserId(), u));
        }
        // 组装 VO
        Page<Map<String, Object>> voPage = new Page<>(fbPage.getCurrent(), fbPage.getSize(), fbPage.getTotal());
        voPage.setRecords(fbPage.getRecords().stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            SysUser u = userMap.get(f.getUserId());
            m.put("feedbackId", f.getFeedbackId());
            m.put("userId", f.getUserId());
            m.put("username", u != null ? u.getUsername() : "-");
            m.put("userAvatar", u != null && u.getAvatar() != null ? u.getAvatar() : "/img/1 (1).png");
            m.put("title", f.getTitle());
            m.put("type", f.getType());
            m.put("content", f.getFeedbackContent());
            m.put("replyText", f.getReplyText());
            m.put("userSupplement", f.getUserSupplement());
            m.put("supplementTime", f.getSupplementTime());
            m.put("status", f.getStatus());
            m.put("createTime", f.getCreateTime());
            return m;
        }).collect(Collectors.toList()));
        return Result.success(voPage);
    }

    /**
     * 「我的反馈」：只返回当前登录用户自己的记录。
     * 单独开接口是因为 /feedback/list 在 JwtInterceptor 里被判为管理员专属，普通用户访问会 403。
     * userId 一律从 token 取，不接受前端传参，杜绝越权。
     */
    @GetMapping("/my")
    public Result<Page<Map<String, Object>>> myList(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "50") Integer size,
                                                    jakarta.servlet.http.HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("userId");
        if (uid == null) return Result.error(401, "未登录");

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Feedback> qw =
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.eq("user_id", uid).eq("deleted", 0).orderByDesc("create_time");
        Page<Feedback> fbPage = feedbackService.page(new Page<>(page, Math.min(size, 200)), qw);

        Page<Map<String, Object>> voPage = new Page<>(fbPage.getCurrent(), fbPage.getSize(), fbPage.getTotal());
        voPage.setRecords(fbPage.getRecords().stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("feedbackId", f.getFeedbackId());
            m.put("userId", f.getUserId());
            m.put("title", f.getTitle());
            m.put("type", f.getType());
            m.put("content", f.getFeedbackContent());
            m.put("replyText", f.getReplyText());
            m.put("userSupplement", f.getUserSupplement());
            m.put("supplementTime", f.getSupplementTime());
            m.put("status", f.getStatus());
            m.put("createTime", f.getCreateTime());
            return m;
        }).collect(Collectors.toList()));
        return Result.success(voPage);
    }

    /** 用户提交反馈
     *  越权防护：普通用户提交的反馈强制归属本人，防止在请求体里伪造 userId 冒充他人。
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody Feedback feedback, HttpServletRequest request){
        Long myId = (Long) request.getAttribute("userId");
        Integer myRole = (Integer) request.getAttribute("role");
        boolean isAdmin = Integer.valueOf(1).equals(myRole);
        if (myId != null && (!isAdmin || feedback.getUserId() == null)) {
            feedback.setUserId(myId);
        }
        // 自动填充
        if (feedback.getStatus() == null) feedback.setStatus(0); // 未读
        if (feedback.getCreateTime() == null) feedback.setCreateTime(LocalDateTime.now());
        // 主表 + 首条消息在同一事务里写入（FeedbackWriteService），避免"有反馈没消息"
        boolean b = feedbackWriteService.submit(feedback);
        if (b) {
            // 实时推送给所有管理员（推送放在事务提交之后，推失败不影响已落库数据）
            java.util.Map<String, Object> push = new java.util.HashMap<>();
            push.put("feedbackId", feedback.getFeedbackId());
            push.put("userId", feedback.getUserId());
            push.put("title", feedback.getTitle());
            push.put("content", feedback.getFeedbackContent());
            push.put("type", feedback.getType());
            push.put("createTime", feedback.getCreateTime());
            SysUser u = feedback.getUserId() != null ? sysUserService.getById(feedback.getUserId()) : null;
            push.put("username", u != null ? u.getUsername() : "-" );
            wsHandler.sendToAdmins("new_feedback", push);
        }
        return b ? Result.success() : Result.error(500, "保存失败");
    }

    /** 通用更新（状态/内容） */
    @RequireAdmin
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody Feedback feedback){
        boolean b = feedbackService.updateById(feedback);
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    /** 管理员回复（专门接口，同时把状态改成已完成） */
    @RequireAdmin
    @PutMapping("/reply")
    public Result<Boolean> reply(@RequestBody Map<String, Object> body){
        Long id = Long.valueOf(body.get("feedbackId").toString());
        String reply = (String) body.get("replyText");
        Feedback old = feedbackService.getById(id);
        // 改状态 + 写管理员消息同事务（B16）
        boolean b = feedbackWriteService.reply(id, reply);
        if (b && old != null) {
            // 实时推送给提交反馈的用户
            java.util.Map<String, Object> push = new java.util.HashMap<>();
            push.put("feedbackId", id);
            push.put("replyText", reply);
            wsHandler.sendToUser(old.getUserId(), "reply", push);
        }
        return b ? Result.success() : Result.error(500, "回复失败");
    }

    /** 🆕 用户端软删除（deleted=1，后台还能看到） */
    @PutMapping("/soft-delete/{id}")
    public Result<Boolean> softDelete(@PathVariable Long id, HttpServletRequest request){
        // 越权防护：只能删除自己的反馈
        Feedback old = feedbackService.getById(id);
        if (old == null) return Result.error(404, "反馈不存在");
        Long myId = (Long) request.getAttribute("userId");
        Integer myRole = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(myRole) && (myId == null || !myId.equals(old.getUserId()))) {
            return Result.error(403, "无权删除他人反馈");
        }
        Feedback f = new Feedback();
        f.setFeedbackId(id);
        f.setDeleted(1);
        boolean b = feedbackService.updateById(f);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /** 管理员硬删除（含往来消息，事务保证） */
    @RequireAdmin
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id){
        boolean b = feedbackService.removeCascade(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /** 用户补充回复（回复管理员）
     *  越权防护：只有反馈本人（或管理员）才能补充内容，避免陌生人往别人工单里灌水并触发管理员推送。
     */
    @PutMapping("/supplement")
    public Result<Boolean> supplement(@RequestBody Map<String, Object> body,
                                      HttpServletRequest request){
        Long id = Long.valueOf(body.get("feedbackId").toString());
        String supplement = (String) body.get("userSupplement");
        Feedback old = feedbackService.getById(id);
        if (old == null) return Result.error(404, "反馈不存在");

        Long myId = (Long) request.getAttribute("userId");
        Integer myRole = (Integer) request.getAttribute("role");
        boolean isAdmin = Integer.valueOf(1).equals(myRole);
        if (!isAdmin && (myId == null || !myId.equals(old.getUserId()))) {
            return Result.error(403, "只能补充自己的反馈");
        }
        // 改状态 + 写用户消息同事务（B16）
        boolean b = feedbackWriteService.supplement(id, supplement);
        if (b) {
            // 实时推送给管理员：用户补充了新信息
            java.util.Map<String, Object> push = new java.util.HashMap<>();
            push.put("feedbackId", id);
            push.put("username", old.getUserId() != null
                ? Objects.toString(sysUserService.getById(old.getUserId()) != null
                    ? sysUserService.getById(old.getUserId()).getUsername() : null, "-") : "-");
            push.put("content", supplement);
            push.put("type", "supplement");
            wsHandler.sendToAdmins("user_supplement", push);
        }
        return b ? Result.success() : Result.error(500, "补充失败");
    }
}