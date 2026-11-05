-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT COMMENT '操作人ID',
    username VARCHAR(100) COMMENT '操作人用户名',
    nickname VARCHAR(100) COMMENT '操作人昵称',
    module VARCHAR(50) COMMENT '操作模块',
    operation VARCHAR(50) COMMENT '操作类型',
    description VARCHAR(500) COMMENT '操作描述',
    ip VARCHAR(50) COMMENT '操作IP',
    method VARCHAR(10) COMMENT '请求方法',
    params TEXT COMMENT '请求参数(JSON)',
    status TINYINT DEFAULT 1 COMMENT '状态：1=成功，0=失败',
    error_msg VARCHAR(500) COMMENT '失败原因',
    cost_time BIGINT COMMENT '耗时（毫秒）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_id (user_id),
    INDEX idx_module (module),
    INDEX idx_create_time (create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';
