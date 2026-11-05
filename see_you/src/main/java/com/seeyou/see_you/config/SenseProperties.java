package com.seeyou.see_you.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 无感记录模块的运行参数（写在 application.yml 的 app.sense 下，改完重启即生效）。
 *
 * 阈值集中在这里的目的：毕设调试时不必改代码就能验证不同策略（演示时也能当场改参数）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sense")
public class SenseProperties {

    /** 功能总开关（关掉后所有 /sense 接口返回「功能未开启」） */
    private boolean enabled = true;

    /** 行为数据保留天数，到期由定时任务物理删除 */
    private int retentionDays = 180;

    /** 单次上报允许的最大条目数（防御恶意大包） */
    private int maxBatch = 50;

    /** 单场景单日停留上限（毫秒），超出即截断，防止用户挂机导致特征失真 */
    private long maxStayMs = 8 * 60 * 60 * 1000L;

    /** 单场景单日点击上限，同上 */
    private int maxClickCnt = 100000;

    /** KNN 邻居数 */
    private int knnK = 5;

    /** 历史窗口（天）：KNN 从这个范围内找相似日 */
    private int historyDays = 90;

    /** AR(1) 使用的序列长度（天） */
    private int seriesDays = 30;

    /** 置信度阈值：低于该值不产生推荐（宁可不猜） */
    private double minConfidence = 0.45;

    /**
     * 自动代记的置信度阈值（比主动推荐更宽松）。
     *
     * 为什么要分开：{@link #minConfidence} 是「向用户下结论」的门槛，必须谨慎；
     * 而夜里自动代记只是「保底打卡」，用途不同 —— 若沿用 0.45，
     * 数据稍少的日子就会被跳过，用户又断签，无感记录的免打卡就形同虚设。
     * 因此这里放低到 0.30，并在正文里明确写出把握程度，不夸大成确定判断。
     */
    private double autoFillMinConfidence = 0.30;

    /**
     * 自动代记是否要求「当天有使用痕迹」。
     *
     * true（默认，保守）：当天没打开过 App、没有任何埋点 → 不代记。无感不等于凭空捏造。
     * false（激进）：只要历史数据够，就用 AR(1) 时序外推 + 历史 KNN 兜底代记，
     *        真正做到「一天都不打开也有记录」。代价是纯推测占比高，需说明口径。
     */
    private boolean autoFillRequireBehavior = true;

    /** 候选过期天数：超过未处理的待确认候选置为「已过期」 */
    private int expireDays = 7;

    /** 系统定时生成推测的小时（cron 见 SenseScheduler，此值仅作提示用） */
    private int generateHour = 20;

    /** 是否允许「文本即时分析」在关闭总开关时单独使用（默认不允许，遵循整体 opt-in） */
    private boolean allowStandaloneText = false;
}
