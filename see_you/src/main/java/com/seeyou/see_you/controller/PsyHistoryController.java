package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyHistory;
import com.seeyou.see_you.service.PsyHistoryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import com.seeyou.see_you.util.PageLimit;

@RestController
@RequestMapping("/history")
public class PsyHistoryController {
    @Resource
    private PsyHistoryService psyHistoryService;

    @GetMapping("/{id:\\d+}")
    public Result<PsyHistory> getById(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        PsyHistory h = psyHistoryService.getById(id);
        if (h == null) return Result.error(404, "记录不存在");
        // 安全：播放历史纯属个人数据，非管理员不可看他人
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !h.getUserId().equals(uid)) return Result.error(403, "无权查看他人记录");
        return Result.success(h);
    }

    @GetMapping("/list")
    public Result<Page<PsyHistory>> list(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          @RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) Integer resourceType,
                                          jakarta.servlet.http.HttpServletRequest request) {
        LambdaQueryWrapper<PsyHistory> wrapper = new LambdaQueryWrapper<>();
        // 安全：userId 以登录态为准；仅管理员可显式按 userId 代查
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (Integer.valueOf(1).equals(role) && userId != null) wrapper.eq(PsyHistory::getUserId, userId);
        else wrapper.eq(PsyHistory::getUserId, uid);
        if (resourceType != null) wrapper.eq(PsyHistory::getResourceType, resourceType);
        wrapper.orderByDesc(PsyHistory::getCreateTime);
        return Result.success(psyHistoryService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    // 首次写入 / upsert（同 user+resourceType+resourceId 只保留一条，更新 createTime + playProgress）
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody PsyHistory history, jakarta.servlet.http.HttpServletRequest request) {
        // 安全：归属强制为当前登录用户
        history.setUserId((Long) request.getAttribute("userId"));
        history.setHistoryId(null);
        LambdaQueryWrapper<PsyHistory> w = new LambdaQueryWrapper<>();
        w.eq(PsyHistory::getUserId, history.getUserId())
         .eq(PsyHistory::getResourceType, history.getResourceType())
         .eq(PsyHistory::getResourceId, history.getResourceId());
        PsyHistory existing = psyHistoryService.getOne(w);
        if (existing != null) {
            existing.setCreateTime(java.time.LocalDateTime.now());
            if (history.getPlayProgress() != null) existing.setPlayProgress(history.getPlayProgress());
            if (history.getDuration() != null) existing.setDuration(history.getDuration());
            if (history.getIsFinished() != null) existing.setIsFinished(history.getIsFinished());
            psyHistoryService.updateById(existing);
        } else {
            if (history.getPlayProgress() == null) history.setPlayProgress(0);
            if (history.getIsFinished() == null) history.setIsFinished(0);
            if (history.getDuration() == null) history.setDuration(0);
            history.setCreateTime(java.time.LocalDateTime.now());
            psyHistoryService.save(history);
        }
        return Result.success();
    }

    // 更新播放进度（upsert：不存在则创建）
    @PostMapping("/progress")
    public Result<Boolean> updateProgress(@RequestBody PsyHistory history, jakarta.servlet.http.HttpServletRequest request) {
        // 安全：归属强制为当前登录用户
        history.setUserId((Long) request.getAttribute("userId"));
        if (history.getUserId() == null || history.getResourceType() == null || history.getResourceId() == null) {
            return Result.error(400, "参数不完整");
        }
        LambdaQueryWrapper<PsyHistory> w = new LambdaQueryWrapper<>();
        w.eq(PsyHistory::getUserId, history.getUserId())
         .eq(PsyHistory::getResourceType, history.getResourceType())
         .eq(PsyHistory::getResourceId, history.getResourceId());
        PsyHistory existing = psyHistoryService.getOne(w);
        if (existing == null) {
            // 不存在则新建
            if (history.getPlayProgress() == null) history.setPlayProgress(0);
            if (history.getIsFinished() == null) history.setIsFinished(0);
            if (history.getDuration() == null) history.setDuration(0);
            history.setCreateTime(java.time.LocalDateTime.now());
            psyHistoryService.save(history);
        } else {
            if (history.getPlayProgress() != null) existing.setPlayProgress(history.getPlayProgress());
            if (history.getDuration() != null) existing.setDuration(history.getDuration());
            if (history.getIsFinished() != null) existing.setIsFinished(history.getIsFinished());
            existing.setCreateTime(java.time.LocalDateTime.now());
            psyHistoryService.updateById(existing);
        }
        return Result.success();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        PsyHistory old = psyHistoryService.getById(id);
        if (old == null) return Result.error(404, "记录不存在");
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !old.getUserId().equals(uid)) return Result.error(403, "无权删除他人记录");
        boolean b = psyHistoryService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }
}