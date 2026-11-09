-- =====================================================================
-- 2026-11-02  psy_risk_alert 补充「处置 + 误报」字段
--
-- 背景：预警的「确认处理」（disposition 三列）与「误报标记」（misreport 两列）
--   是在 add_sense_and_alert.sql 建表之后直接在生产库上执行的，当时漏了迁移脚本。
--   本文件补齐该缺口，否则全新环境建出的表缺列，后端 SQL 直接报错。
--
-- 写法：查 information_schema 后动态执行，幂等——已加过列的库重复执行无副作用。
-- =====================================================================

USE see_you;

DROP PROCEDURE IF EXISTS add_col_if_missing;
DELIMITER $$
CREATE PROCEDURE add_col_if_missing(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
    ) THEN
        SET @sql := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_ddl);
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        SELECT CONCAT('已加列：', p_table, '.', p_column) AS result;
    ELSE
        SELECT CONCAT('列已存在，跳过：', p_table, '.', p_column) AS result;
    END IF;
END$$
DELIMITER ;

CALL add_col_if_missing('psy_risk_alert', 'disposition',     "VARCHAR(32)  DEFAULT NULL COMMENT '处置方式' AFTER ack_time");
CALL add_col_if_missing('psy_risk_alert', 'disposition_note', "VARCHAR(255) DEFAULT NULL COMMENT '处置备注' AFTER disposition");
CALL add_col_if_missing('psy_risk_alert', 'disposition_time', "DATETIME     DEFAULT NULL COMMENT '处置时间' AFTER disposition_note");
CALL add_col_if_missing('psy_risk_alert', 'misreport',       "TINYINT NOT NULL DEFAULT 0 COMMENT '误报标记 0=有效 1=用户标记误报' AFTER disposition_time");
CALL add_col_if_missing('psy_risk_alert', 'misreport_time',  "DATETIME     DEFAULT NULL COMMENT '误报标记时间' AFTER misreport");

DROP PROCEDURE IF EXISTS add_col_if_missing;
