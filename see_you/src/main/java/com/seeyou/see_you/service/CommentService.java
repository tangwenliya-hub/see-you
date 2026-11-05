package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.Comment;
import com.seeyou.see_you.vo.CommentVO;
import com.seeyou.see_you.vo.MyCommentVO;

import java.util.List;

public interface CommentService extends IService<Comment> {
    Page<CommentVO> getCommentList(Integer page, Integer size, Integer resourceType, Long resourceId, Long viewerId);
    Page<CommentVO> getCommentPage(Integer page, Integer size, Integer status, String keyword);

    /** 我的评论（跨音频/视频），userId 由 Controller 从 token 注入 */
    Page<MyCommentVO> getMyComments(Integer page, Integer size, Long userId, Integer status);

    /**
     * 仅更新评论状态
     */
    boolean updateStatus(Long commentId, Integer status);

    /**
     * 批量更新评论状态
     */
    boolean batchUpdateStatus(List<Long> commentIds, Integer status);

    /**
     * 批量删除评论
     */
    boolean batchDelete(List<Long> commentIds);
}