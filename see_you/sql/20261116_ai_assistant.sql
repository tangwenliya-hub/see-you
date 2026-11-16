-- =====================================================================
-- 2026-11-16  AI 生活助手三件套：情绪周报 / 日记润色 / 语音写日记
-- 只有周报需要落库缓存（每人每周一条），润色与语音转写均不落库、不留痕。
-- 幂等：可重复执行。
-- =====================================================================

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 情绪周报缓存';
