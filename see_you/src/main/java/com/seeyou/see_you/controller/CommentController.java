package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.Comment;
import com.seeyou.see_you.service.CommentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.util.Result;
import com.seeyou.see_you.vo.CommentVO;
import com.seeyou.see_you.vo.MyCommentVO;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import com.seeyou.see_you.util.PageLimit;

@RestController
@RequestMapping("/comment")
public class CommentController {
    @Resource
    private CommentService commentService;
    @Resource
    private com.seeyou.see_you.util.JwtUtil jwtUtil;

    @GetMapping("/{id:\\d+}")
    public Result<Comment> getById(@PathVariable Long id){
        return Result.success(commentService.getById(id));
    }
    @GetMapping("/list")
    public Result<Page<Comment>> list(@RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(commentService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 20))));
    }

    @GetMapping("/listByResource")
    public Result<Page<CommentVO>> listByResource(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer size,
                                                  @RequestParam Integer resourceType,
                                                  @RequestParam Long resourceId,
                                                  HttpServletRequest request) {
        // 该接口在拦截器白名单里（游客可读），所以 request 里没有 userId，这里手动解析一次
        Long viewerId = null;
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            if (token != null && !token.isEmpty() && jwtUtil.validateToken(token)) {
                viewerId = jwtUtil.getUserIdByToken(token);
            }
        } catch (Exception ignore) {}
        return Result.success(commentService.getCommentList(page, size, resourceType, resourceId, viewerId));
    }

    @GetMapping("/listAllWithUser")
    public Result<Page<CommentVO>> listAllWithUser(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer size,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) String keyword) {
        return Result.success(commentService.getCommentPage(page, size, status, keyword));
    }

    /**
     * 我的评论：跨音频 / 视频聚合，带资源名、封面与审核状态。
     * userId 只从登录态取，杜绝 ?userId= 越权查看他人评论。
     */
    @GetMapping("/my")
    public Result<Page<MyCommentVO>> my(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "20") Integer size,
                                        @RequestParam(required = false) Integer status,
                                        HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute("userId");
        if (viewerId == null) return Result.error(401, "请先登录");
        return Result.success(commentService.getMyComments(page, size, viewerId, status));
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody Comment comment, HttpServletRequest request){
        // 安全：userId 一律以登录态为准，防止冒名发评论（该接口不在白名单，拦截器已注入 userId）
        Long viewerId = (Long) request.getAttribute("userId");
        if (viewerId == null) return Result.error(401, "请先登录后再评论");
        comment.setUserId(viewerId);
        // 新评论强制为待审核状态，防止前端绕过审核
        comment.setStatus(0);
        comment.setCommentId(null);
        boolean b = commentService.save(comment);
        return b ? Result.success() : Result.error(500, "保存失败");
    }
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody Comment comment){
        boolean b = commentService.updateById(comment);
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    /**
     * 只更新评论状态（安全，不会覆盖其他字段）
     */
    @PutMapping("/updateStatus")
    public Result<Boolean> updateStatus(@RequestParam Long commentId,
                                        @RequestParam Integer status) {
        boolean b = commentService.updateStatus(commentId, status);
        return b ? Result.success() : Result.error(500, "更新失败");
    }
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id){
        boolean b = commentService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /**
     * 批量更新评论状态
     */
    @PutMapping("/batchUpdateStatus")
    public Result<Boolean> batchUpdateStatus(@RequestParam Integer status,
                                             @RequestBody List<Long> commentIds) {
        boolean b = commentService.batchUpdateStatus(commentIds, status);
        return b ? Result.success() : Result.error(500, "批量更新失败");
    }

    /**
     * 批量删除评论
     */
    @DeleteMapping("/batchDelete")
    public Result<Boolean> batchDelete(@RequestBody List<Long> commentIds) {
        boolean b = commentService.batchDelete(commentIds);
        return b ? Result.success() : Result.error(500, "批量删除失败");
    }
}