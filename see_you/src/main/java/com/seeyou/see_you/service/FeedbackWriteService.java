package com.seeyou.see_you.service;

import com.seeyou.see_you.entity.Feedback;
import com.seeyou.see_you.entity.FeedbackMessage;
import com.seeyou.see_you.mapper.FeedbackMessageMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 反馈相关的多步写入：反馈主表 + 往来消息表必须"同生共死"。
 *
 * <p>为什么单独抽一个 Service，而不是在 Controller 里加 @Transactional：
 * Spring 的声明式事务靠代理实现，同类内部 this.xxx() 自调用不经过代理、事务不会生效
 * （项目里操作日志切面就踩过这个坑）。把多步写入挪到独立 Bean，由 Controller 跨 Bean 调用，
 * 事务才会真正开启。</p>
 *
 * <p>WebSocket 推送不放在事务里：推送是"尽力而为"的副作用，
 * 不能让推送失败回滚已落库的反馈，所以统一在 Controller 事务方法返回之后再推。</p>
 */
@Slf4j
@Service
public class FeedbackWriteService {

    @Resource
    private FeedbackService feedbackService;

    @Resource
    private FeedbackMessageMapper msgMapper;

    /** 用户提交反馈：反馈主表 + 首条消息 */
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(Feedback feedback) {
        boolean ok = feedbackService.save(feedback);
        if (!ok) {
            return false;
        }
        FeedbackMessage m = new FeedbackMessage();
        m.setFeedbackId(feedback.getFeedbackId());
        m.setSenderType("user");
        m.setContent(feedback.getFeedbackContent());
        m.setCreateTime(feedback.getCreateTime());
        msgMapper.insert(m);
        return true;
    }

    /** 管理员回复：更新反馈为已完成 + 写入管理员消息 */
    @Transactional(rollbackFor = Exception.class)
    public boolean reply(Long feedbackId, String replyText) {
        Feedback f = new Feedback();
        f.setFeedbackId(feedbackId);
        f.setReplyText(replyText);
        f.setStatus(2);
        boolean ok = feedbackService.updateById(f);
        if (!ok) {
            return false;
        }
        FeedbackMessage m = new FeedbackMessage();
        m.setFeedbackId(feedbackId);
        m.setSenderType("admin");
        m.setContent(replyText);
        m.setCreateTime(LocalDateTime.now());
        msgMapper.insert(m);
        return true;
    }

    /** 用户补充：状态回到"处理中" + 写入用户消息 */
    @Transactional(rollbackFor = Exception.class)
    public boolean supplement(Long feedbackId, String text) {
        Feedback f = new Feedback();
        f.setFeedbackId(feedbackId);
        f.setUserSupplement(text);
        f.setSupplementTime(LocalDateTime.now());
        f.setStatus(1);
        boolean ok = feedbackService.updateById(f);
        if (!ok) {
            return false;
        }
        FeedbackMessage m = new FeedbackMessage();
        m.setFeedbackId(feedbackId);
        m.setSenderType("user");
        m.setContent(text);
        m.setCreateTime(LocalDateTime.now());
        msgMapper.insert(m);
        return true;
    }
}
