package com.seeyou.see_you.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论自动审核配置（application.yml 的 app.comment 下）。
 *
 * <p>背景：原方案所有评论一律「待审核」，管理员逐条放行 —— 用户量上来后完全审不过来。
 * 现在改为两级漏斗：系统先做机器判定，只有「可疑」的评论才转人工；
 * 干净评论直接 status=1 公开，发完立即可见。</p>
 *
 * <p>机器判据（可解释、可配置）：</p>
 * <ol>
 *   <li>命中可疑词库（后台「评论管理」页可增删，改完立即生效）；</li>
 *   <li>包含 URL / 链接（广告导流的典型特征）；</li>
 *   <li>正文超长（正常聊天不会写小作文刷屏）；</li>
 *   <li>短时间窗口内重复发送相同内容（刷屏）。</li>
 * </ol>
 * 命中任意一条 → status=0 转人工；一条不中 → status=1 直接公开。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.comment")
public class CommentProperties {

    /**
     * 自动审核总开关。
     * true  = 系统先审，干净评论直接公开（默认，适合用户多起来之后）；
     * false = 回到全人工模式，所有评论一律待审核（演示/风控收紧时用）。
     */
    private boolean autoReview = true;

    /**
     * 可疑词库：命中任意一条即转人工审核（不是直接拒绝 —— 交给管理员判断，
     * 避免「我想死你们了」这类口语误杀）。词库可在后台「评论管理」页维护。
     */
    private List<String> reviewWords = new ArrayList<>(List.of(
            "加微信", "加vx", "加V", "加企鹅", "私聊我", "私信我领取",
            "刷单", "兼职", "赚钱", "日结", "佣金", "返利", "引流", "接单",
            "赌博", "博彩", "彩票计划", "色情", "裸聊", "约炮",
            "代开发票", "代办证件", "低价出售", "免费领取", "点击链接"
    ));

    /** 正文最大长度：超过即视为可疑（正常评论不会写小作文） */
    private int maxTextLen = 500;

    /** 刷屏判定窗口：N 秒内重复发送相同内容 → 可疑 */
    private int dupWindowSec = 60;
}
