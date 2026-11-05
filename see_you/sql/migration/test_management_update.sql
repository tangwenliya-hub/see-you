-- =====================================================
-- 测评量表管理功能修复 SQL 脚本
-- 包含：
-- 1. 量表状态字段（启用/禁用）
-- 2. 测评结果分级表
-- =====================================================

-- 1. 量表表添加状态字段（1=启用/已发布，0=禁用/下架）
ALTER TABLE `psy_test` ADD COLUMN `status` INT DEFAULT 1 COMMENT '状态 1=启用 0=禁用' AFTER `max_score`;

-- 2. 测评结果分级表
CREATE TABLE IF NOT EXISTS `psy_test_result_grade` (
  `grade_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分级ID',
  `test_id` bigint DEFAULT NULL COMMENT '量表ID',
  `min_score` int DEFAULT 0 COMMENT '最低分（包含）',
  `max_score` int DEFAULT 0 COMMENT '最高分（包含）',
  `grade_name` varchar(100) DEFAULT NULL COMMENT '等级名称',
  `description` text COMMENT '结果描述',
  `suggestion` text COMMENT '建议',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`grade_id`),
  KEY `idx_test` (`test_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测评结果分级表';
