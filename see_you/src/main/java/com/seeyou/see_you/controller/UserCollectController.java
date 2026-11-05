package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.UserCollect;
import com.seeyou.see_you.service.UserCollectService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import com.seeyou.see_you.util.PageLimit;
@RestController
@RequestMapping("/collect")
public class UserCollectController {
    @Resource
    private UserCollectService userCollectService;

    @GetMapping("/{id:\\d+}")
    public Result<UserCollect> getById(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request){
        UserCollect c = userCollectService.getById(id);
        if (c == null) return Result.error(404, "记录不存在");
        // 安全：非管理员只能看自己的收藏
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !c.getUserId().equals(uid)) return Result.error(403, "无权查看他人记录");
        return Result.success(c);
    }
    @GetMapping("/list")
    public Result<Page<UserCollect>> list(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) Integer resourceType,
                                           @RequestParam(required = false) Long resourceId,
                                           jakarta.servlet.http.HttpServletRequest request) {
        LambdaQueryWrapper<UserCollect> wrapper = new LambdaQueryWrapper<>();
        // 安全：登录用户只能看自己的（管理员可代查）；游客只能按资源查，不允许按 userId 枚举他人
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (Integer.valueOf(1).equals(role) && userId != null) wrapper.eq(UserCollect::getUserId, userId);
        else if (uid != null) wrapper.eq(UserCollect::getUserId, uid);
        if (resourceType != null) wrapper.eq(UserCollect::getResourceType, resourceType);
        if (resourceId != null) wrapper.eq(UserCollect::getResourceId, resourceId);
        wrapper.orderByDesc(UserCollect::getCreateTime);
        return Result.success(userCollectService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody UserCollect collect, jakarta.servlet.http.HttpServletRequest request){
        // 安全：归属强制为当前登录用户
        collect.setUserId((Long) request.getAttribute("userId"));
        if (collect.getUserId() == null) return Result.error(401, "请先登录后再收藏");
        collect.setCollectId(null);
        boolean b = userCollectService.save(collect);
        return b ? Result.success() : Result.error(500, "保存失败");
    }
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request){
        UserCollect old = userCollectService.getById(id);
        if (old == null) return Result.error(404, "记录不存在");
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !old.getUserId().equals(uid)) return Result.error(403, "无权取消他人收藏");
        boolean b = userCollectService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /**
     * 按 userId + resourceType + resourceId 取消收藏（一次请求搞定）
     */
    @DeleteMapping("/removeByResource")
    public Result<Boolean> removeByResource(@RequestParam(required = false) Long userId,
                                            @RequestParam Integer resourceType,
                                            @RequestParam Long resourceId,
                                            jakarta.servlet.http.HttpServletRequest request) {
        // 安全：只能取消自己的收藏，userId 以登录态为准
        Long uid = (Long) request.getAttribute("userId");
        if (uid == null) return Result.error(401, "未登录");
        boolean b = userCollectService.removeByResource(uid, resourceType, resourceId);
        return b ? Result.success() : Result.error(500, "取消失败");
    }

    /**
     * 统计某资源的收藏数
     */
    @GetMapping("/count")
    public Result<Long> count(@RequestParam Integer resourceType,
                              @RequestParam Long resourceId) {
        return Result.success(userCollectService.countByResource(resourceType, resourceId));
    }

    /**
     * 检查用户是否已收藏某资源（登录态接口：只检查自己）
     */
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam(required = false) Long userId,
                                 @RequestParam Integer resourceType,
                                 @RequestParam Long resourceId,
                                 jakarta.servlet.http.HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("userId");
        if (uid == null) return Result.error(401, "未登录");
        return Result.success(userCollectService.checkCollect(uid, resourceType, resourceId));
    }
}
