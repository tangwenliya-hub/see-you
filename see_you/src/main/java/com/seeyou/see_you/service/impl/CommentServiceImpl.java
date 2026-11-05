package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.Comment;
import com.seeyou.see_you.mapper.CommentMapper;
import com.seeyou.see_you.service.CommentService;
import com.seeyou.see_you.vo.CommentVO;
import com.seeyou.see_you.vo.MyCommentVO;
import org.springframework.stereotype.Service;

import java.util.List;
import com.seeyou.see_you.util.PageLimit;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Override
    public Page<CommentVO> getCommentList(Integer page, Integer size, Integer resourceType, Long resourceId, Long viewerId) {
        Page<CommentVO> p = new Page<>(PageLimit.page(page), PageLimit.of(size, 10));
        return baseMapper.selectCommentWithUserPage(p, resourceType, resourceId, viewerId);
    }

    @Override
    public Page<CommentVO> getCommentPage(Integer page, Integer size, Integer status, String keyword) {
        Page<CommentVO> p = new Page<>(PageLimit.page(page), PageLimit.of(size, 10));
        return baseMapper.selectAllCommentsWithUserPage(p, status, keyword);
    }

    @Override
    public Page<MyCommentVO> getMyComments(Integer page, Integer size, Long userId, Integer status) {
        Page<MyCommentVO> p = new Page<>(PageLimit.page(page), PageLimit.of(size, 20));
        return baseMapper.selectMyComments(p, userId, status);
    }

    @Override
    public boolean updateStatus(Long commentId, Integer status) {
        LambdaUpdateWrapper<Comment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Comment::getCommentId, commentId).set(Comment::getStatus, status);
        return update(wrapper);
    }

    @Override
    public boolean batchUpdateStatus(List<Long> commentIds, Integer status) {
        if (commentIds == null || commentIds.isEmpty()) return false;
        LambdaUpdateWrapper<Comment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(Comment::getCommentId, commentIds).set(Comment::getStatus, status);
        return update(wrapper);
    }

    @Override
    public boolean batchDelete(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) return false;
        return removeByIds(commentIds);
    }
}
