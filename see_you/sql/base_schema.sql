-- =====================================================================
-- 绪见 · 基础表结构（base schema，15 张基础业务表）
--
-- 覆盖：sys_user / psy_diary / psy_test 及其题目选项记录 / 音视频与历史 /
--       评论点赞收藏 / 反馈 / AI 会话与话术。其余表由 migration/ 增量脚本创建。
--
-- ⚠️ 本文件是「迁移前」的原始结构：迁移脚本后续 ADD COLUMN 的字段
--   （sys_user 主题/无感开关、psy_diary.source、psy_video.duration 等）已刻意剥除，
--   所以必须【先执行本文件，再按文件名日期从旧到新执行 migration/】，顺序反了
--   会报 Duplicate column / Unknown column。
--
-- 生成方式：真实库 `mysqldump --no-data` 导出后清洗（剥除迁移新增列）；
--   基础表结构有变更时重新导出并同步剥除。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS see_you DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE see_you;

-- 表按字母序排列，先关外键检查避免建表顺序依赖
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `psy_ai_chat`;
CREATE TABLE `psy_ai_chat` (
  `chat_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `user_msg` text COMMENT '用户发送消息',
  `ai_reply` text COMMENT 'AI返回内容',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`chat_id`),
  KEY `idx_ai_session` (`session_id`),
  KEY `idx_ai_user_session` (`user_id`,`session_id`),
  CONSTRAINT `psy_ai_chat_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话记录';
DROP TABLE IF EXISTS `psy_ai_words`;
CREATE TABLE `psy_ai_words` (
  `word_id` bigint NOT NULL AUTO_INCREMENT,
  `keyword` varchar(100) NOT NULL COMMENT '触发关键词',
  `category` varchar(50) DEFAULT NULL,
  `match_example` varchar(500) DEFAULT NULL,
  `reply_text` text NOT NULL COMMENT '治愈回复文案',
  `sort` int DEFAULT '0',
  `status` int DEFAULT '1',
  PRIMARY KEY (`word_id`)
) ENGINE=InnoDB AUTO_INCREMENT=98 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI关键词话术库';
DROP TABLE IF EXISTS `psy_audio`;
CREATE TABLE `psy_audio` (
  `audio_id` bigint NOT NULL AUTO_INCREMENT,
  `audio_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `audio_url` varchar(255) NOT NULL COMMENT '音频文件地址',
  `category` varchar(50) DEFAULT NULL COMMENT '分类：白噪音/轻音乐/睡前故事',
  `cover_img` varchar(255) DEFAULT NULL COMMENT '封面图',
  `play_count` int DEFAULT '0',
  `lyric` text COMMENT '歌词（支持LRC格式或纯文本）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audio_id`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='治愈音频';
DROP TABLE IF EXISTS `psy_collect`;
CREATE TABLE `psy_collect` (
  `collect_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `resource_type` tinyint NOT NULL COMMENT '1音频 2视频',
  `resource_id` bigint NOT NULL COMMENT '音频/视频id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`collect_id`),
  UNIQUE KEY `uk_user_resource` (`user_id`,`resource_type`,`resource_id`),
  CONSTRAINT `psy_collect_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表';
DROP TABLE IF EXISTS `psy_comment`;
CREATE TABLE `psy_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `resource_type` tinyint NOT NULL,
  `resource_id` bigint NOT NULL,
  `comment_text` text NOT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0待审核 1通过 2驳回',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`comment_id`),
  KEY `user_id` (`user_id`),
  KEY `idx_resource_status` (`resource_type`,`resource_id`,`status`),
  CONSTRAINT `psy_comment_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论表';
DROP TABLE IF EXISTS `psy_diary`;
CREATE TABLE `psy_diary` (
  `diary_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `diary_content` text COMMENT '日记内容',
  `emotion` tinyint NOT NULL COMMENT '情绪标签：1开心 2平静 3焦虑 4低落 5疲惫',
  `intensity` tinyint DEFAULT NULL COMMENT '情绪强度 1轻度 2中度 3重度',
  `sleep_hours` decimal(3,1) DEFAULT NULL COMMENT '睡眠时长(h)',
  `stress_level` tinyint DEFAULT NULL COMMENT '压力自评1-5',
  `exercise_min` int DEFAULT NULL COMMENT '运动时长(分钟)',
  `diary_date` date NOT NULL COMMENT '日记日期（用于统计）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_public` tinyint(1) DEFAULT '0' COMMENT '0=私密 1=公开',
  `image_url` varchar(500) DEFAULT NULL COMMENT '日记配图URL',
  PRIMARY KEY (`diary_id`),
  KEY `idx_uid_date` (`user_id`,`diary_date`),
  CONSTRAINT `psy_diary_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=35789 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='情绪日记表';
DROP TABLE IF EXISTS `psy_feedback`;
CREATE TABLE `psy_feedback` (
  `feedback_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(30) DEFAULT 'other' COMMENT '反馈类型',
  `title` varchar(100) DEFAULT '' COMMENT '反馈标题',
  `feedback_content` text NOT NULL,
  `reply_text` text COMMENT '管理员回复',
  `user_supplement` text COMMENT '用户补充回复',
  `supplement_time` datetime DEFAULT NULL COMMENT '用户补充时间',
  `status` tinyint DEFAULT '0' COMMENT '0未读 1处理中 2已完成',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`feedback_id`),
  KEY `idx_user_deleted` (`user_id`,`deleted`),
  CONSTRAINT `psy_feedback_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';
DROP TABLE IF EXISTS `psy_feedback_message`;
CREATE TABLE `psy_feedback_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT,
  `feedback_id` bigint NOT NULL,
  `sender_type` varchar(20) NOT NULL,
  `content` text NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`message_id`),
  KEY `idx_fb` (`feedback_id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='反馈完整聊天记录';
DROP TABLE IF EXISTS `psy_history`;
CREATE TABLE `psy_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `resource_type` tinyint NOT NULL COMMENT '1音频 2视频',
  `resource_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `play_progress` int DEFAULT '0' COMMENT '上次播放到第几秒',
  `is_finished` tinyint DEFAULT '0' COMMENT '是否播放完 0否 1是',
  `duration` int DEFAULT '0' COMMENT '总时长(秒)',
  PRIMARY KEY (`history_id`),
  KEY `uk_user_resource` (`user_id`,`resource_type`,`resource_id`),
  CONSTRAINT `psy_history_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音视频浏览历史';
DROP TABLE IF EXISTS `psy_test`;
CREATE TABLE `psy_test` (
  `test_id` bigint NOT NULL AUTO_INCREMENT,
  `test_name` varchar(100) NOT NULL COMMENT '测评名称',
  `test_desc` varchar(255) DEFAULT NULL COMMENT '测评简介',
  `min_score` int DEFAULT '0',
  `max_score` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`test_id`)
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测评量表';
DROP TABLE IF EXISTS `psy_test_option`;
CREATE TABLE `psy_test_option` (
  `option_id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `option_text` varchar(200) NOT NULL COMMENT '选项文字',
  `score` int NOT NULL COMMENT '该选项分值，用来计算情绪结果',
  PRIMARY KEY (`option_id`),
  KEY `question_id` (`question_id`),
  CONSTRAINT `psy_test_option_ibfk_1` FOREIGN KEY (`question_id`) REFERENCES `psy_test_question` (`question_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2823 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目选项';
DROP TABLE IF EXISTS `psy_test_question`;
CREATE TABLE `psy_test_question` (
  `question_id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL COMMENT '所属测评',
  `question_content` text NOT NULL COMMENT '题目内容',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`question_id`),
  KEY `test_id` (`test_id`),
  CONSTRAINT `psy_test_question_ibfk_1` FOREIGN KEY (`test_id`) REFERENCES `psy_test` (`test_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=707 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测评题目';
DROP TABLE IF EXISTS `psy_test_record`;
CREATE TABLE `psy_test_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `test_id` bigint NOT NULL,
  `total_score` int DEFAULT NULL COMMENT '总分',
  `score` int DEFAULT NULL COMMENT '测评得分',
  `level` varchar(32) DEFAULT NULL COMMENT '结果等级',
  `tip` varchar(500) DEFAULT NULL COMMENT '结果建议',
  `result_emotion` tinyint DEFAULT NULL COMMENT '测评得出的情绪标签 1~5',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`record_id`),
  KEY `test_id` (`test_id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  CONSTRAINT `psy_test_record_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `psy_test_record_ibfk_2` FOREIGN KEY (`test_id`) REFERENCES `psy_test` (`test_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=150 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户测评记录';
DROP TABLE IF EXISTS `psy_video`;
CREATE TABLE `psy_video` (
  `video_id` bigint NOT NULL AUTO_INCREMENT,
  `video_name` varchar(100) NOT NULL,
  `video_url` varchar(255) NOT NULL,
  `category` varchar(50) DEFAULT NULL COMMENT '视频分类',
  `cover_img` varchar(255) DEFAULT NULL,
  `play_count` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`video_id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='疗愈视频资源';
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户id',
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(100) NOT NULL COMMENT '密码（加密存储）',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `role` tinyint DEFAULT '0' COMMENT '0普通用户 1管理员',
  `status` int DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `bio` varchar(500) DEFAULT NULL COMMENT '个人简介',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

SET FOREIGN_KEY_CHECKS = 1;
