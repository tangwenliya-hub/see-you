package com.seeyou.see_you.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等补建「新增功能依赖的表」。
 *
 * <p>背景：本项目迁移脚本是手工 SQL（sql/ 目录），生产库（Railway MySQL）没有公网入口，
 * 新功能上线时容易漏执行脚本，运行到相关接口就报 Table doesn't exist。
 * 这里只对「纯粹新增、与既有表无耦合」的表做 CREATE TABLE IF NOT EXISTS 自愈，
 * 已存在则什么也不做，可安全重复启动。</p>
 *
 * <p>注意：不要在这里写 ALTER / 数据修正类语句，结构变更仍走 sql/ 手工脚本，
 * 避免线上库结构被启动逻辑隐式改动。</p>
 */
@Slf4j
@Component
public class SchemaBootstrap implements ApplicationRunner {

    /** 与 sql/20261116_ai_assistant.sql 保持一致（AI 情绪周报缓存，每人每周一条） */
    private static final String CREATE_PSY_AI_WEEKLY_REPORT = """
            CREATE TABLE IF NOT EXISTS `psy_ai_weekly_report` (
              `report_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
              `user_id`     BIGINT       NOT NULL                COMMENT '用户ID（数据归属，查询一律带此条件）',
              `week_start`  DATE         NOT NULL                COMMENT '报告覆盖区间起点（近7天含当天）',
              `week_end`    DATE         NOT NULL                COMMENT '报告覆盖区间终点',
              `content`     MEDIUMTEXT           NULL            COMMENT 'AI 生成的周报正文',
              `diary_count` INT          NOT NULL DEFAULT 0      COMMENT '生成时纳入的日记篇数',
              `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
              PRIMARY KEY (`report_id`),
              UNIQUE KEY `uk_user_week` (`user_id`, `week_start`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 情绪周报缓存'
            """;

    private final JdbcTemplate jdbcTemplate;

    public SchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable("psy_ai_weekly_report", CREATE_PSY_AI_WEEKLY_REPORT);
    }

    /**
     * 建表失败不阻断启动：其他功能照常可用，对应接口会返回错误，
     * 日志里能直接看到根因（通常是数据库权限不足）。
     */
    private void ensureTable(String tableName, String ddl) {
        try {
            jdbcTemplate.execute(ddl);
            log.info("[SchemaBootstrap] 表 {} 就绪", tableName);
        } catch (Exception e) {
            log.error("[SchemaBootstrap] 表 {} 自动创建失败，请手工执行 sql/ 对应脚本：{}", tableName, e.getMessage());
        }
    }
}
