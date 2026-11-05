package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.Feedback;

public interface FeedbackService extends IService<Feedback> {

    /**
     * 硬删除反馈，并级联删除其下的往来消息，避免留下孤儿消息记录。
     */
    boolean removeCascade(Long feedbackId);
}
