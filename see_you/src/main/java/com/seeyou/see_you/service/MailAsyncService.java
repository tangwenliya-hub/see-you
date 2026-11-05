package com.seeyou.see_you.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 异步邮件发送。
 *
 * <p>必须独立成 bean：Spring 的 @Async 依赖 AOP 代理，同类内部自调用不生效。
 * 找回密码流程把发送动作委托到这里后，请求线程不等 SMTP 握手即可返回。
 */
@Service
public class MailAsyncService {

    private static final Logger log = LoggerFactory.getLogger(MailAsyncService.class);

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    /**
     * 异步发送，方法立即返回；成败只记日志。
     * 调用方用「SMTP 是否已配置」决定走邮件流程还是开发降级，不依赖本次发送结果。
     */
    @Async("mailExecutor")
    public void sendResetCode(String to, String code) {
        if (mailFrom == null || mailFrom.isBlank()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(to);
            msg.setSubject("绪见 · 密码重置验证码");
            msg.setText("您的重置验证码是：" + code + "，10 分钟内有效。若非本人操作请忽略本邮件。");
            mailSender.send(msg);
            log.info("[邮件] 重置验证码已异步发送 to={}", to);
        } catch (Exception e) {
            // 发送失败不阻塞流程：未配置 SMTP 时走开发降级
            log.warn("[邮件] 重置验证码发送失败 to={}: {}", to, e.getMessage());
        }
    }
}
