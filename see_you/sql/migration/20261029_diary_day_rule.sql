-- =====================================================================
-- 2026-10-29  日记「当天情绪」裁决规则 + 索引收敛
--
-- 一、为什么要定一条裁决规则
--    psy_diary 允许同一天写多篇（这是功能，不是缺陷），但所有「按天」的统计
--    都必须回答同一个问题：这一天到底算哪种情绪？
--
--    当前数据里存在 12 组同日多篇（共 30 行）。此前三处实现各写各的：
--      · StatisticsMapper.myDiaryBetween / myDiaryAgg  → MAX(diary_id)
--      · SenseServiceImpl.loadEmotions                 → ORDER BY diary_date 后 map.put
--      · RiskAlertServiceImpl.loadEmotions             → ORDER BY diary_date 后 map.put
--    后两处只按日期排序，并列行在 MySQL 里顺序不保证，等价于「随机取一篇」。
--    于是同一个用户同一天，趋势图、推测、预警可能取到不同的日记。
--
--    统一规则（代码侧已全部对齐）：
--      **同一天多篇 → diary_id 最大（当天最后写的那篇）代表当天。**
--    选择「最后一篇」而不是「平均/最早」的理由：它是用户当天最后的状态，
--    也天然对应「打开日记页看到的最新一篇」，与用户直觉一致。
--
-- 二、为什么不加 (user_id, diary_date) 唯一键
--    加唯一键意味着「一天只能写一篇」，需要把现有 12 组数据合并（删除或迁移），
--    属于产品语义变更而非缺陷修复。若后续决定改为「一天一篇」，
--    应先做数据合并脚本，再加唯一键；本文件只解决「取值不确定」的问题。
--
-- 三、索引
--    下面 4 条统计 SQL 的形态都是「按 user_id + source 过滤，再按 diary_date
--    分组取 MAX(diary_id)」。给一个覆盖索引可以让子查询走 index-only scan，
--    不必回表。
-- =====================================================================

-- 覆盖索引：user_id, source, diary_date, diary_id
-- 对应查询：myDiaryAgg / myDiaryBetween / myDiaryDates
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'psy_diary'
                 AND INDEX_NAME = 'idx_uid_source_date_id');
SET @sql := IF(@exist = 0,
    'ALTER TABLE psy_diary ADD INDEX idx_uid_source_date_id (user_id, source, diary_date, diary_id)',
    'SELECT ''idx_uid_source_date_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 单列 source 索引：基数只有 1/2，全站统计靠它筛不出多少行，
-- 优化器通常仍会全表扫描；保留它只会给写日记增加一次索引维护。
SET @exist2 := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'psy_diary'
                  AND INDEX_NAME = 'idx_source');
SET @sql2 := IF(@exist2 > 0,
    'ALTER TABLE psy_diary DROP INDEX idx_source',
    'SELECT ''idx_source 不存在，跳过'' AS msg');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
