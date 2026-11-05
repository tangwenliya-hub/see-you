-- 点赞表
CREATE TABLE IF NOT EXISTS `psy_like` (
  `like_id` bigint NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `resource_type` int DEFAULT NULL COMMENT '资源类型 1=音频 2=视频',
  `resource_id` bigint DEFAULT NULL COMMENT '资源ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`like_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞表';
