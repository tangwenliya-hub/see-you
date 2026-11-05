package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.Feedback;
import com.seeyou.see_you.entity.FeedbackMessage;
import com.seeyou.see_you.mapper.FeedbackMapper;
import com.seeyou.see_you.mapper.FeedbackMessageMapper;
import com.seeyou.see_you.service.FeedbackService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {

    @Resource
    private FeedbackMessageMapper feedbackMessageMapper;

    /** 先删往来消息再删反馈主表，整体事务保证不留孤儿消息 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCascade(Long feedbackId) {
        feedbackMessageMapper.delete(
                new LambdaQueryWrapper<FeedbackMessage>().eq(FeedbackMessage::getFeedbackId, feedbackId)
        );
        return removeById(feedbackId);
    }
}
