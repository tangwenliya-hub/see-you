package com.seeyou.see_you.config;

import com.seeyou.see_you.service.RiskAlertService;
import com.seeyou.see_you.service.impl.SenseServiceImpl;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 无感记录与情绪预警的定时任务。
 *
 * 时间点的选择理由：
 *  · 20:00 生成推测 —— 大多数用户此时已结束一天的主要活动，行为特征相对完整；
 *  · 21:00 扫描预警 —— 晚间是情绪低谷的高发时段，此时提示更有价值；
 *  · 03:00 清理过期 —— 凌晨对线上影响最小。
 *
 * ⚠️ 集群部署时：多个实例会重复执行这里的所有任务。
 *    毕设单机演示没问题；生产环境需要引入分布式锁或 XXL-Job 这类调度中心。
 */
@Component
@EnableScheduling
public class SenseScheduler {

    private static final Logger log = LoggerFactory.getLogger(SenseScheduler.class);

    @Resource
    private SenseServiceImpl senseService;
    @Resource
    private RiskAlertService riskAlertService;

    /** 每天 20:00：为开启无感记录且当天还没写日记的用户生成推测 */
    @Scheduled(cron = "0 0 20 * * ?")
    public void generatePredictions() {
        try {
            senseService.generateForAll();
        } catch (Exception e) {
            log.warn("[无感记录] 定时推测生成失败", e);
        }
    }

    /**
     * 每天 23:30：自动代记。
     * 20:00 只生成「候选」，用户不打开 App 就永远不落库，打卡照样断 ——
     * 这一步把当天有痕迹、又确实没写日记的用户的候选直接落成 source=3 的日记，
     * 这才是无感记录「不用每天打卡」的闭环。刻意排在 03:00 过期清理之前。
     */
    @Scheduled(cron = "0 30 23 * * ?")
    public void autoFillDiaries() {
        try {
            senseService.autoFillForAll();
        } catch (Exception e) {
            log.warn("[无感记录] 自动代记失败", e);
        }
    }

    /** 每天 21:00：扫描近期有记录的用户，判定情绪风险（非医疗诊断） */
    @Scheduled(cron = "0 0 21 * * ?")
    public void scanRisk() {
        try {
            int n = riskAlertService.scanAll();
            log.info("[情绪预警] 定时扫描完成，命中 {} 人", n);
        } catch (Exception e) {
            log.warn("[情绪预警] 定时扫描失败", e);
        }
    }

    /** 每天 03:00：把过期未确认的推测候选置为「已过期」 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void expirePredictions() {
        try {
            int n = senseService.expireOld();
            if (n > 0) log.info("[无感记录] 过期候选 {} 条", n);
        } catch (Exception e) {
            log.warn("[无感记录] 过期清理失败", e);
        }
    }

    /** 每周日 04:00：清理超出保留期的行为数据（隐私合规要求） */
    @Scheduled(cron = "0 0 4 ? * SUN")
    public void cleanRetention() {
        try {
            int n = senseService.cleanRetention();
            if (n > 0) log.info("[无感记录] 保留期清理 {} 行", n);
        } catch (Exception e) {
            log.warn("[无感记录] 保留期清理失败", e);
        }
    }
}
