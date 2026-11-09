-- =============================================================
-- 后台扩展：系统运行参数表 sys_config
-- 用途：把原先只能改 application.yml 的阈值（预警窗口、连续天数、
--       最低置信度等）搬到数据库，支持后台「系统参数」页可视化调整
--       并立即生效，答辩现场可以直接边讲边调。
--
-- 优先级：sys_config 存在该键 → 覆盖 yml 默认值；不存在 → 用 yml。
-- 恢复默认：后台点「恢复默认」删掉记录即可回到 yml 的值。
-- =============================================================
CREATE TABLE IF NOT EXISTS sys_config (
    config_key   VARCHAR(64)  NOT NULL COMMENT '配置键，如 alert.windowDays',
    config_value VARCHAR(255) DEFAULT NULL COMMENT '配置值（统一字符串存储，读取时按类型转换）',
    config_group VARCHAR(32)  DEFAULT NULL COMMENT '分组：alert=情绪预警 sense=无感记录',
    config_remark VARCHAR(255) DEFAULT NULL COMMENT '配置说明',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    update_by   VARCHAR(64)  DEFAULT NULL COMMENT '最近修改人',
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统运行参数（后台可视化配置）';
