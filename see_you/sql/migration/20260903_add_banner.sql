-- ============================================================
-- 首页轮播 / 系统公告位
-- 用途：运营位管理。后台可配置首页横幅、暖心文案、活动入口，
--       前台 Home 页公开读取，不需要登录。
-- ============================================================
CREATE TABLE IF NOT EXISTS psy_banner (
    banner_id   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '轮播ID',
    title       VARCHAR(120)  NOT NULL DEFAULT '' COMMENT '主标题',
    subtitle    VARCHAR(200)  DEFAULT '' COMMENT '副标题 / 文案',
    cover_img   VARCHAR(300)  DEFAULT '' COMMENT '背景图地址',
    link_type   VARCHAR(20)   DEFAULT 'none' COMMENT '跳转类型：none-不跳转 diary-写日记 test-测评 video-视频 audio-音频 url-外链',
    link_url    VARCHAR(300)  DEFAULT '' COMMENT '跳转参数或外链地址',
    position    VARCHAR(20)   DEFAULT 'home' COMMENT '展示位置：home-欢迎页 main-首页顶部',
    sort        INT           DEFAULT 0 COMMENT '排序，越小越靠前',
    status      TINYINT       DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    start_time  DATETIME      NULL COMMENT '生效开始时间，为空表示不限制',
    end_time    DATETIME      NULL COMMENT '生效结束时间，为空表示不限制',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_position_status (position, status),
    INDEX idx_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播/公告位';

-- 两条默认数据：一条暖心欢迎语，一条引导记录情绪
INSERT INTO psy_banner (title, subtitle, cover_img, link_type, link_url, position, sort, status)
SELECT '绪见 · 记录每一天的情绪', '写下一句话，让情绪被看见。', '', 'diary', '', 'home', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM psy_banner WHERE title = '绪见 · 记录每一天的情绪');

INSERT INTO psy_banner (title, subtitle, cover_img, link_type, link_url, position, sort, status)
SELECT '今天，你感觉怎么样？', '做一次心理测评，更了解当下的自己。', '', 'test', '', 'home', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM psy_banner WHERE title = '今天，你感觉怎么样？');
