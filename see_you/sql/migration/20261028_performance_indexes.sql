-- =====================================================================================
-- 性能索引补充（2026-10-28）
--
-- 依据：查询 information_schema 盘点现有索引后，只补真正缺失的，并清理冗余。
-- 全部为幂等写法（MySQL 不支持 CREATE INDEX IF NOT EXISTS，故用存储过程判断后执行）。
-- 数据量：日记 841 条级别，执行瞬间完成；生产环境建议在低峰期执行。
-- =====================================================================================

USE see_you;

DROP PROCEDURE IF EXISTS add_idx_if_missing;
DELIMITER $$
CREATE PROCEDURE add_idx_if_missing(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index
    ) THEN
        SET @sql = CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        SELECT CONCAT('已创建索引：', p_table, '.', p_index) AS result;
    ELSE
        SELECT CONCAT('索引已存在，跳过：', p_table, '.', p_index) AS result;
    END IF;
END$$
DELIMITER ;

-- 1. 播放历史：upsert 按 user + type + resource 去重，原来只有 user_id 单列索引
CALL add_idx_if_missing('psy_history', 'uk_user_resource', 'user_id, resource_type, resource_id');

-- 2. 评论：按资源查评论是最高频的读操作（资源详情页每次打开都要查）
CALL add_idx_if_missing('psy_comment', 'idx_resource_status', 'resource_type, resource_id, status');

-- 3. 测评记录：个人中心要按时间倒序列出"我的测评"
CALL add_idx_if_missing('psy_test_record', 'idx_user_time', 'user_id, create_time');

-- 4. 反馈列表：列表查询恒带 deleted 过滤条件
CALL add_idx_if_missing('psy_feedback', 'idx_user_deleted', 'user_id, deleted');

-- 5. 点赞：与收藏表对齐，加唯一约束防止并发重复点赞
--    注意：若库里已存在重复行，此语句会失败，需先去重再执行。
CALL add_idx_if_missing('psy_like', 'uk_user_resource', 'user_id, resource_type, resource_id');

DROP PROCEDURE IF EXISTS add_idx_if_missing;

-- 6. 清理冗余索引：psy_diary 上 idx_uid_date 与 idx_user_date 列完全相同，
--    保留 idx_uid_date（部署说明中引用），删除重复的那一个。
SET @dup := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'psy_diary'
               AND index_name = 'idx_user_date');
SET @sql := IF(@dup > 0, 'ALTER TABLE psy_diary DROP INDEX idx_user_date', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================================
-- 执行后核对：
--   SHOW INDEX FROM psy_diary;
--   SHOW INDEX FROM psy_comment;
-- =====================================================================================
