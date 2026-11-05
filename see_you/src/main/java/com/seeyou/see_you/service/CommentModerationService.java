package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.config.CommentProperties;
import com.seeyou.see_you.entity.Comment;
import com.seeyou.see_you.mapper.CommentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 评论机器审核（两级漏斗的第一级）。
 *
 * <p>判定结果只影响「是否转人工」，不直接拒绝任何评论 ——
 * 命中可疑判据的评论 status=0 等管理员复核，干净的 status=1 直接公开。
 * 每条判据都返回人话 reason，方便排查误判。</p>
 */
@Slf4j
@Service
public class CommentModerationService {

    @Resource
    private CommentProperties props;

    @Resource
    private CommentMapper commentMapper;

    /** URL / 链接特征（广告导流最典型的信号）。容忍中英文混排的常见写法 */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(https?://|www\\.)\\S+|\\S+\\.(com|cn|net|org|top|xyz|cc|vip)(/|\\s|$)");

    /** 审核结论 */
    public static class Verdict {
        public final boolean suspicious;   // true = 转人工
        public final String reason;        // 命中的判据（人话，用于日志排查误判）

        Verdict(boolean suspicious, String reason) {
            this.suspicious = suspicious;
            this.reason = reason;
        }
    }

    /**
     * 对一条待发评论做机器判定。
     *
     * @param text   评论正文
     * @param userId 发送者（刷屏判定用）
     */
    public Verdict moderate(String text, Long userId) {
        // 总开关关闭 → 一律转人工（保持全人工模式）
        if (!props.isAutoReview()) return new Verdict(true, "自动审核已关闭，全部转人工");

        if (text == null || text.isBlank()) return new Verdict(true, "内容为空");

        // 1) 可疑词库（后台可配置，命中即转人工而非直接拒绝，避免误杀口语）
        String t = text.toLowerCase();
        for (String w : props.getReviewWords()) {
            if (w != null && !w.isBlank() && t.contains(w.toLowerCase())) {
                return new Verdict(true, "命中可疑词「" + w + "」");
            }
        }

        // 2) 链接特征
        if (URL_PATTERN.matcher(text).find()) return new Verdict(true, "包含链接/网址");

        // 3) 超长（正常聊天不会写小作文）
        if (text.length() > props.getMaxTextLen())
            return new Verdict(true, "正文超长（" + text.length() + " 字 > " + props.getMaxTextLen() + "）");

        // 4) 刷屏：时间窗口内同一用户发过相同内容
        if (userId != null && props.getDupWindowSec() > 0) {
            try {
                LambdaQueryWrapper<Comment> q = new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .eq(Comment::getCommentText, text)
                        .ge(Comment::getCreateTime, LocalDateTime.now().minusSeconds(props.getDupWindowSec()))
                        .last("LIMIT 1");
                if (commentMapper.selectCount(q) > 0) return new Verdict(true, "短时间内重复发送相同内容");
            } catch (Exception e) {
                log.warn("[评论审核] 刷屏判定查询失败，按不命中处理", e);
            }
        }

        return new Verdict(false, null);
    }
}
