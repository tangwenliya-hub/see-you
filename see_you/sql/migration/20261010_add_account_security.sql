-- ======================================================
-- 2026-10-09 账号安全 / 会话 / 找回密码 / 第三方登录 建表
-- 用法：mysql -uroot -p see_you < add_account_security.sql
-- ======================================================

-- 1. 登录会话（多设备管理）
CREATE TABLE IF NOT EXISTS sys_user_session (
  session_id        BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
  user_id           BIGINT       NOT NULL COMMENT '用户ID',
  token             VARCHAR(512) NOT NULL COMMENT 'JWT（或后8位指纹）',
  device            VARCHAR(64)  DEFAULT '' COMMENT '设备名（浏览器/系统）',
  ip                VARCHAR(64)  DEFAULT '' COMMENT '登录IP',
  user_agent        VARCHAR(512) DEFAULT '' COMMENT '浏览器UA',
  login_time        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  last_active_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  status            TINYINT      DEFAULT 1 COMMENT '1=在线 0=已下线（被踢/退出）',
  INDEX idx_user (user_id),
  INDEX idx_token (token(190))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录会话';

-- 2. 登录失败计数与锁定
CREATE TABLE IF NOT EXISTS sys_login_attempt (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(64) NOT NULL COMMENT '用户名',
  fail_count  INT      DEFAULT 0 COMMENT '连续失败次数',
  lock_until  DATETIME NULL COMMENT '锁定到什么时候',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录失败次数/锁定';

-- 3. 找回密码令牌
CREATE TABLE IF NOT EXISTS sys_pwd_reset (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  token       VARCHAR(64) NOT NULL COMMENT '重置令牌',
  code        VARCHAR(10) NOT NULL COMMENT '6位验证码',
  expire_time DATETIME    NOT NULL COMMENT '过期时间',
  used        TINYINT     DEFAULT 0 COMMENT '0可用 1已用',
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码重置令牌';

