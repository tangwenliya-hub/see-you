-- 用户表添加个性化设置字段
ALTER TABLE `sys_user` ADD COLUMN `theme` VARCHAR(32) DEFAULT 'green' COMMENT '主题色名称' AFTER `bio`;
ALTER TABLE `sys_user` ADD COLUMN `dark_mode` TINYINT DEFAULT 0 COMMENT '深色模式 0=关闭 1=开启' AFTER `theme`;
ALTER TABLE `sys_user` ADD COLUMN `avatar_frame` VARCHAR(32) DEFAULT 'none' COMMENT '头像装饰框' AFTER `dark_mode`;
ALTER TABLE `sys_user` ADD COLUMN `profile_bg` VARCHAR(500) DEFAULT NULL COMMENT '个人中心背景图' AFTER `avatar_frame`;
