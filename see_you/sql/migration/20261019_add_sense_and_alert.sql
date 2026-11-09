-- =====================================================================================
-- 三大新模块迁移脚本：轻量无感记录（IES）+ 情绪风险预警（Risk Alert）+ 移动端埋点支撑
-- 数据库：MySQL 8.4     字符集：utf8mb4
-- 执行方式：整文件执行即可（可重复执行前请先确认字段未重复添加）
-- =====================================================================================

SET NAMES utf8mb4;

-- =====================================================================================
-- 一、无感记录模块
-- =====================================================================================

-- ---------- 1.1 用户表：无感记录开关（默认全关 = opt-in 知情授权） ----------
ALTER TABLE `sys_user`
  ADD COLUMN `sense_enabled`   TINYINT  DEFAULT 0 COMMENT '无感记录总开关 0=关闭(默认) 1=开启' AFTER `profile_bg`,
  ADD COLUMN `sense_behavior`  TINYINT  DEFAULT 0 COMMENT '行为特征采集子开关 0=关 1=开' AFTER `sense_enabled`,
  ADD COLUMN `sense_text`      TINYINT  DEFAULT 0 COMMENT '随笔文本情感分析子开关 0=关 1=开' AFTER `sense_behavior`,
  ADD COLUMN `sense_grant_at`  DATETIME DEFAULT NULL COMMENT '用户授权时间（隐私审计用）' AFTER `sense_text`,
  ADD COLUMN `sense_revoke_at` DATETIME DEFAULT NULL COMMENT '最近一次关闭时间' AFTER `sense_grant_at`;

-- ---------- 1.2 日记表：区分「手动录入」与「系统推测后用户确认」 ----------
ALTER TABLE `psy_diary`
  ADD COLUMN `source`        TINYINT DEFAULT 1 COMMENT '数据来源 1=用户手动录入 2=系统推测后用户确认' AFTER `is_public`,
  ADD COLUMN `prediction_id` BIGINT  DEFAULT NULL COMMENT '来源为2时关联的推测记录ID（溯源用）' AFTER `source`,
  ADD INDEX `idx_user_date` (`user_id`, `diary_date`),
  ADD INDEX `idx_source` (`source`);

-- ---------- 1.3 脱敏行为特征表（只有数值与枚举，不存在任何文本列） ----------
CREATE TABLE IF NOT EXISTS `psy_behavior_feature` (
  `feature_id`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '数据归属用户（查询强制以 JWT 为准）',
  `stat_date`   DATE         NOT NULL                COMMENT '统计归属日期（按服务端日期归档）',
  `scene_code`  VARCHAR(32)  NOT NULL                COMMENT '场景枚举: home/diary/write/fm/test/classroom/profile/assistant',
  `stay_ms`     INT UNSIGNED DEFAULT 0               COMMENT '该场景当日累计停留毫秒数',
  `visit_cnt`   INT UNSIGNED DEFAULT 0               COMMENT '当日进入该场景次数',
  `click_cnt`   INT UNSIGNED DEFAULT 0               COMMENT '当日该场景点击次数（仅计数，不记录点击对象）',
  `hour_mask`   INT UNSIGNED DEFAULT 0               COMMENT '24小时活跃位图，bit i=1 表示第 i 小时活跃过',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`feature_id`),
  UNIQUE KEY `uniq_user_date_scene` (`user_id`, `stat_date`, `scene_code`),
  KEY `idx_user_date` (`user_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无感记录-脱敏行为特征表(仅存聚合数值，无文本)';

-- ---------- 1.4 情绪推测候选表（草稿性质，未确认前不进正式日记） ----------
CREATE TABLE IF NOT EXISTS `psy_emotion_prediction` (
  `prediction_id`     BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT       NOT NULL                COMMENT '归属用户（严格一人一份）',
  `target_date`       DATE         NOT NULL                COMMENT '推测目标日期',
  `trigger_type`      TINYINT      DEFAULT 1               COMMENT '1=系统定时 2=用户主动 3=文本即时',
  `suggest_emotion`   TINYINT      NOT NULL                COMMENT 'Top1 推测情绪 1~9',
  `suggest_intensity` TINYINT      DEFAULT 2               COMMENT '推测情绪强度 1~3',
  `confidence`        DECIMAL(4,3) DEFAULT 0.000           COMMENT '置信度 0~1，低于阈值不推荐',
  `candidates`        VARCHAR(255) DEFAULT NULL            COMMENT 'Top3候选 JSON: [{"e":2,"p":0.62},...]',
  `reason`            VARCHAR(500) DEFAULT NULL            COMMENT '可解释的中文依据（给用户看）',
  `detail`            VARCHAR(255) DEFAULT NULL            COMMENT '三路贡献明细 JSON: {"behavior":0.30,"text":0.45,"series":0.25}',
  `feature_snapshot`  VARCHAR(255) DEFAULT NULL            COMMENT '本次使用的7维行为特征快照，便于复盘',
  `confirm_status`    TINYINT      DEFAULT 0               COMMENT '0=待确认 1=已采用 2=已忽略 3=已过期',
  `confirm_emotion`   TINYINT      DEFAULT NULL            COMMENT '用户最终确认的情绪（可能与推测不同，用于计算命中率）',
  `diary_id`          BIGINT       DEFAULT NULL            COMMENT '采用后生成的正式日记ID',
  `expire_time`       DATETIME     DEFAULT NULL            COMMENT '过期时间（创建后 7 天）',
  `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`prediction_id`),
  UNIQUE KEY `uniq_user_target_trigger` (`user_id`, `target_date`, `trigger_type`),
  KEY `idx_user_status` (`user_id`, `confirm_status`),
  KEY `idx_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无感记录-情绪推测候报表(待用户确认，未入库日记)';

-- ---------- 1.5 九类情绪中文词典（情感词 / 否定词 / 程度副词） ----------
CREATE TABLE IF NOT EXISTS `psy_emotion_lexicon` (
  `lexicon_id`  BIGINT       NOT NULL AUTO_INCREMENT,
  `word`        VARCHAR(32)  NOT NULL COMMENT '词条',
  `emotion`     TINYINT      NOT NULL COMMENT '映射到9类情绪 1开心2平静3疲惫4难过5生气6焦虑7兴奋8满足9其他',
  `weight`      DECIMAL(3,2) DEFAULT 1.00 COMMENT '情感权重 0.5~2.0',
  `word_type`   TINYINT      DEFAULT 0  COMMENT '0=情感词 1=否定词 2=程度副词',
  `source_tag`  VARCHAR(32)  DEFAULT 'manual' COMMENT '来源标记 manual/custom',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`lexicon_id`),
  UNIQUE KEY `uniq_word_emotion` (`word`, `emotion`),
  KEY `idx_type` (`word_type`),
  KEY `idx_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无感记录-九类情绪中文词典';

-- 初始语料：情感词（每类 20~26 条，共 210 条）
INSERT IGNORE INTO `psy_emotion_lexicon` (`word`, `emotion`, `weight`, `word_type`) VALUES
-- 1 开心
('开心',1,1.5,0),('快乐',1,1.5,0),('高兴',1,1.4,0),('愉快',1,1.2,0),('欣喜',1,1.3,0),
('欢喜',1,1.2,0),('美滋滋',1,1.3,0),('乐呵',1,1.0,0),('灿烂',1,1.0,0),('晴朗',1,0.8,0),
('元气',1,1.0,0),('美好',1,1.2,0),('充实',1,1.1,0),('幸运',1,1.1,0),('治愈',1,1.3,0),
('如释重负',1,1.4,0),('太棒了',1,1.5,0),('顺利',1,1.1,0),('圆满',1,1.2,0),('顺心',1,1.1,0),
('哈哈',1,1.2,0),('笑',1,1.0,0),
-- 2 平静
('平静',2,1.5,0),('平和',2,1.3,0),('安宁',2,1.2,0),('安稳',2,1.1,0),('淡然',2,1.2,0),
('从容',2,1.2,0),('释然',2,1.3,0),('放松',2,1.1,0),('踏实',2,1.1,0),('自在',2,1.1,0),
('宁静',2,1.2,0),('温和',2,1.0,0),('规律',2,0.9,0),('按部就班',2,0.9,0),('岁月静好',2,1.2,0),
('还行',2,0.7,0),('一般',2,0.6,0),('没什么',2,0.6,0),('稳定',2,1.0,0),('日常',2,0.7,0),
-- 3 疲惫
('累',3,1.5,0),('疲惫',3,1.6,0),('疲倦',3,1.4,0),('疲劳',3,1.4,0),('好困',3,1.2,0),
('累瘫',3,1.6,0),('倦怠',3,1.3,0),('精疲力尽',3,1.6,0),('乏力',3,1.2,0),('没劲',3,1.1,0),
('通宵',3,1.4,0),('加班',3,1.3,0),('熬夜',3,1.3,0),('奔波',3,1.2,0),('连轴转',3,1.4,0),
('透支',3,1.4,0),('撑不住',3,1.5,0),('昏沉',3,1.2,0),('眼皮打架',3,1.3,0),('充电',3,0.9,0),
('休息',3,0.9,0),
-- 4 难过
('难过',4,1.6,0),('伤心',4,1.6,0),('难受',4,1.4,0),('低落',4,1.5,0),('失落',4,1.4,0),
('沮丧',4,1.5,0),('委屈',4,1.4,0),('心疼',4,1.3,0),('想哭',4,1.5,0),('哽咽',4,1.4,0),
('五味杂陈',4,1.2,0),('酸楚',4,1.3,0),('遗憾',4,1.2,0),('孤独',4,1.4,0),('想念',4,1.1,0),
('怀念',4,1.1,0),('告别',4,1.0,0),('苦',4,1.1,0),('撑不住了',4,1.3,0),('空落落',4,1.3,0),
('哭',4,1.3,0),
-- 5 生气
('生气',5,1.6,0),('愤怒',5,1.7,0),('恼火',5,1.5,0),('烦躁',5,1.4,0),('气死',5,1.6,0),
('抓狂',5,1.5,0),('暴躁',5,1.5,0),('火大',5,1.5,0),('窝火',5,1.4,0),('不爽',5,1.3,0),
('讨厌',5,1.3,0),('烦人',5,1.3,0),('不公平',5,1.4,0),('过分',5,1.3,0),('离谱',5,1.3,0),
('荒唐',5,1.3,0),('气愤',5,1.5,0),('炸毛',5,1.3,0),('难以置信',5,1.1,0),('不计后果',5,1.0,0),
-- 6 焦虑
('焦虑',6,1.7,0),('紧张',6,1.5,0),('担心',6,1.4,0),('慌',6,1.4,0),('害怕',6,1.4,0),
('不安',6,1.4,0),('忐忑',6,1.4,0),('恐慌',6,1.7,0),('压力',6,1.4,0),('着急',6,1.2,0),
('患得患失',6,1.4,0),('心慌',6,1.4,0),('睡不着',6,1.4,0),('失眠',6,1.4,0),('纠结',6,1.2,0),
(' deadline',6,1.3,0),('考试',6,1.1,0),('面试',6,1.1,0),('不确定',6,1.2,0),('等待',6,0.9,0),
('头皮发麻',6,1.3,0),('放不下',6,1.2,0),
-- 7 兴奋
('兴奋',7,1.6,0),('激动',7,1.5,0),('期待',7,1.3,0),('燃',7,1.3,0),('热血',7,1.4,0),
('欣喜若狂',7,1.7,0),('惊喜',7,1.5,0),('跃跃欲试',7,1.3,0),('上头',7,1.2,0),('新鲜',7,1.0,0),
('停不下来',7,1.2,0),('燃起来了',7,1.4,0),('太好了',7,1.4,0),('按捺不住',7,1.3,0),('盼',7,1.1,0),
('终于',7,1.1,0),('肾上腺素',7,1.2,0),('好玩',7,1.1,0),('爱了',7,1.2,0),('念念不忘',7,1.0,0),
-- 8 满足
('满足',8,1.6,0),('满意',8,1.5,0),('感恩',8,1.5,0),('感谢',8,1.4,0),('有成就感',8,1.5,0),
('被认可',8,1.4,0),('暖心',8,1.4,0),('幸福感',8,1.6,0),('温柔',8,1.3,0),('陪伴',8,1.3,0),
('有收获',8,1.4,0),('进步',8,1.3,0),('突破',8,1.4,0),('被理解',8,1.5,0),('值得',8,1.3,0),
('幸福感满满',8,1.6,0),('欣慰',8,1.4,0),('守得云开',8,1.3,0),('放下',8,1.1,0),('和解',8,1.3,0),
-- 9 其他/迷茫
('迷茫',9,1.5,0),('麻木',9,1.4,0),('说不清',9,1.2,0),('复杂',9,1.1,0),('无感',9,1.2,0),
('空白',9,1.2,0),('虚空',9,1.3,0),('无所谓',9,1.2,0),('还好',9,0.8,0),('勉强',9,1.0,0),
('困惑',9,1.3,0),('怀疑',9,1.2,0),('恍惚',9,1.2,0),('割裂',9,1.2,0),('悬浮',9,1.0,0),
('不知道',9,1.0,0),(' emo了',9,1.2,0);

-- 否定词（word_type=1）：命中后对窗口内的情感词做符号翻转
INSERT IGNORE INTO `psy_emotion_lexicon` (`word`, `emotion`, `weight`, `word_type`) VALUES
('不',0,-1.00,1),('没',0,-1.00,1),('没有',0,-1.00,1),('别',0,-1.00,1),('无',0,-1.00,1),
('非',0,-1.00,1),('未',0,-1.00,1),('否',0,-1.00,1),('不太',0,-1.00,1),('并不',0,-1.00,1),
('绝不',0,-1.00,1),('很少',0,-1.00,1);

-- 程度副词（word_type=2）：命中后对紧随的情感词做倍数放大/缩小
INSERT IGNORE INTO `psy_emotion_lexicon` (`word`, `emotion`, `weight`, `word_type`) VALUES
('非常',0,1.60,2),('特别',0,1.50,2),('极其',0,1.80,2),('极度',0,1.80,2),('超级',0,1.60,2),
('很',0,1.30,2),('太',0,1.40,2),('挺',0,1.10,2),('相当',0,1.30,2),('格外',0,1.40,2),
('稍微',0,0.70,2),('有点',0,0.70,2),('有些',0,0.70,2),('略微',0,0.60,2),('还算',0,0.80,2),
('比较',0,0.90,2),('略微有点',0,0.60,2),('一点点',0,0.60,2);

-- ---------- 1.6 隐私操作审计日志（证明用户对本人数据全程可控） ----------
CREATE TABLE IF NOT EXISTS `psy_sense_privacy_log` (
  `log_id`      BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      NOT NULL,
  `action`      VARCHAR(32) NOT NULL COMMENT 'GRANT/REVOKE/EXPORT/DELETE_ALL/SWITCH/RETENTION_CLEAN/GENERATE/CONFIRM',
  `detail`      VARCHAR(255)         COMMENT '动作补充说明',
  `ip_masked`   VARCHAR(64)          COMMENT '脱敏IP（IPv4 末段置 0）',
  `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无感记录-隐私操作审计日志';

-- =====================================================================================
-- 二、情绪风险预警模块（非医疗诊断，仅情绪状态提示）
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `psy_risk_alert` (
  `alert_id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT       NOT NULL COMMENT '归属用户（严格一人一份）',
  `alert_date`       DATE         NOT NULL COMMENT '预警触发日期',
  `alert_level`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=轻度关注 2=中度提醒 3=高度关注',
  `window_days`      INT          DEFAULT 7     COMMENT '判定窗口天数',
  `record_days`      INT          DEFAULT 0     COMMENT '窗口内有记录的天数',
  `negative_days`    INT          DEFAULT 0     COMMENT '窗口内负面情绪天数',
  `max_continuous`   INT          DEFAULT 0     COMMENT '窗口内最长连续负面天数',
  `negative_ratio`   DECIMAL(4,3) DEFAULT 0.000 COMMENT '窗口内负面占比 0~1',
  `avg_index`        DECIMAL(5,2) DEFAULT 0.00  COMMENT '窗口内平均情绪指数 0~100',
  `trend_slope`      DECIMAL(6,3) DEFAULT 0.000 COMMENT '窗口内情绪指数最小二乘斜率（负=持续下滑）',
  `trigger_rule`     VARCHAR(64)  DEFAULT NULL  COMMENT '命中的规则名，便于追溯阈值配置',
  `trigger_detail`   VARCHAR(500) DEFAULT NULL  COMMENT '判定依据明细（给用户看的中文）',
  `advice`           VARCHAR(1000) DEFAULT NULL COMMENT '自我调节建议（含求助引导）',
  `is_read`          TINYINT      DEFAULT 0     COMMENT '0=未读 1=已读',
  `ack_status`       TINYINT      DEFAULT 0     COMMENT '0=未确认 1=用户已确认知晓',
  `ack_time`         DATETIME     DEFAULT NULL  COMMENT '用户确认时间',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`alert_id`),
  UNIQUE KEY `uniq_user_date` (`user_id`, `alert_date`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_level` (`alert_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪风险预警记录表(仅情绪状态提示，非医疗诊断)';

-- =====================================================================================
-- 三、完成标记
-- =====================================================================================
-- 校验：应能看到 psy_behavior_feature / psy_emotion_prediction / psy_emotion_lexicon
--       / psy_sense_privacy_log / psy_risk_alert 五张新表，以及 lexicon 中的 240+ 条词典
