package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.UserLike;
import com.seeyou.see_you.service.UserLikeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import com.seeyou.see_you.util.PageLimit;

@RestController
@RequestMapping("/like")
public class UserLikeController {
    @Resource
    private UserLikeService userLikeService;

    @GetMapping("/{id:\\d+}")
    public Result<UserLike> getById(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request){
        UserLike like = userLikeService.getById(id);
        if (like == null) return Result.error(404, "记录不存在");
        // 安全：非管理员只能看自己的点赞
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !like.getUserId().equals(uid)) return Result.error(403, "无权查看他人记录");
        return Result.success(like);
    }

    @GetMapping("/list")
    public Result<Page<UserLike>> list(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) Integer resourceType,
                                        @RequestParam(required = false) Long resourceId,
                                        jakarta.servlet.http.HttpServletRequest request) {
        LambdaQueryWrapper<UserLike> wrapper = new LambdaQueryWrapper<>();
        // 安全：登录用户只能看自己的（管理员可代查）；游客只能按资源查，不允许按 userId 枚举他人
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (Integer.valueOf(1).equals(role) && userId != null) wrapper.eq(UserLike::getUserId, userId);
        else if (uid != null) wrapper.eq(UserLike::getUserId, uid);
        if (resourceType != null) wrapper.eq(UserLike::getResourceType, resourceType);
        if (resourceId != null) wrapper.eq(UserLike::getResourceId, resourceId);
        wrapper.orderByDesc(UserLike::getCreateTime);
        return Result.success(userLikeService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody UserLike like, jakarta.servlet.http.HttpServletRequest request){
        // 安全：归属强制为当前登录用户
        like.setUserId((Long) request.getAttribute("userId"));
        if (like.getUserId() == null) return Result.error(401, "请先登录后再点赞");
        like.setLikeId(null);
        boolean b = userLikeService.save(like);
        return b ? Result.success() : Result.error(500, "保存失败");
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request){
        UserLike old = userLikeService.getById(id);
        if (old == null) return Result.error(404, "记录不存在");
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !old.getUserId().equals(uid)) return Result.error(403, "无权取消他人点赞");
        boolean b = userLikeService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /**
     * 按 userId + resourceType + resourceId 取消点赞（一次请求搞定）
     */
    @DeleteMapping("/removeByResource")
    public Result<Boolean> removeByResource(@RequestParam(required = false) Long userId,
                                            @RequestParam Integer resourceType,
                                            @RequestParam Long resourceId,
                                            jakarta.servlet.http.HttpServletRequest request) {
        // 安全：只能取消自己的点赞，userId 以登录态为准
        Long uid = (Long) request.getAttribute("userId");
        if (uid == null) return Result.error(401, "未登录");
        boolean b = userLikeService.removeByResource(uid, resourceType, resourceId);
        return b ? Result.success() : Result.error(500, "取消失败");
    }

    /**
     * 统计某资源的点赞数
     */
    @GetMapping("/count")
    public Result<Long> count(@RequestParam Integer resourceType,
                              @RequestParam Long resourceId) {
        return Result.success(userLikeService.countByResource(resourceType, resourceId));
    }

    /**
     * 检查用户是否已点赞某资源（登录态接口：只检查自己）
     */
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam(required = false) Long userId,
                                 @RequestParam Integer resourceType,
                                 @RequestParam Long resourceId,
                                 jakarta.servlet.http.HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("userId");
        if (uid == null) return Result.error(401, "未登录");
        return Result.success(userLikeService.checkLike(uid, resourceType, resourceId));
    }
}
