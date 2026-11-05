-- =====================================================================================
-- 无感记录模块 · 演示数据种子（仅用于答辩演示 / 链路验证，不是真实采集数据）
-- 执行：mysql -u root -p see_you < 20261028_seed_sense_demo.sql
-- 清理：见文件末尾
--
-- ⚠️ 重要说明（写论文 / 答辩时必须如实标注）：
--   无感记录的行为采集依赖 H5 端 senseTracker 的真实停留、点击埋点，演示环境无法在
--   短时间内自然产生一个月的行为数据。本脚本按「每类情绪对应一组行为指纹」的假设
--   合成特征，用于打通「行为采集 → 情绪推测 → 风险预警」的完整演示链路。
--
--   合成特征与真实日记情绪之间存在**构造性关联**，因此：
--     ✅ 可以演示：系统能给出推测情绪、置信度、相似日解释、触发预警
--     ❌ 不能用于：证明 KNN 的真实准确率（那属于循环论证）
--   真实准确率需待真实埋点采集后，重跑 ModelEvaluationTest 得到。
-- =====================================================================================

SET @uid = 1;   -- 演示账号：test（换账号改这里）

-- 0. 打开该用户的无感开关（opt-in：默认全关，必须显式授权）
--    sense_enabled  总开关
--    sense_behavior 行为采集（停留/点击/进入次数/活跃位图，不含任何文本）
--    sense_text     文本情感分析（仅在写日记点「帮我猜一下」时对当次输入生效）
UPDATE sys_user
   SET sense_enabled   = 1,
       sense_behavior  = 1,
       sense_text      = 1,
       sense_grant_at  = NOW()
 WHERE user_id = @uid;

-- 1. 清理窗口内已有特征，保证脚本可重复执行（幂等）
DELETE FROM psy_behavior_feature
 WHERE user_id = @uid
   AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 60 DAY);

-- 2. 生成 60 天 × 8 场景的行为特征
INSERT INTO psy_behavior_feature
    (user_id, stat_date, scene_code, stay_ms, visit_cnt, click_cnt, hour_mask, create_time, update_time)
WITH RECURSIVE seq AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 59
),
-- 每类情绪的「行为指纹」：主页停留 / FM 停留 / 点击活跃度 / 作息时段
-- hour_mode: 1=白天活跃  2=深夜活跃  3=全天活跃
mood_profile AS (
    --                 home停留   FM停留   点击数  进入次数  作息
    SELECT 1 AS emotion, 1800000 AS home_ms,  300000 AS fm_ms, 150 AS clicks, 6 AS visits, 1 AS hour_mode  -- 开心
    UNION ALL SELECT 2,  900000,  600000,   70, 4, 1   -- 平静
    UNION ALL SELECT 3,  500000, 3000000,   20, 3, 2   -- 疲惫
    UNION ALL SELECT 4,  300000, 4200000,   15, 5, 2   -- 难过
    UNION ALL SELECT 5, 2100000,  900000,  130, 2, 2   -- 生气
    UNION ALL SELECT 6, 1500000, 3600000,  190, 8, 3   -- 焦虑
    UNION ALL SELECT 7, 2400000,  150000,  200, 7, 1   -- 兴奋
    UNION ALL SELECT 8, 1200000, 1500000,   90, 5, 1   -- 满足
    UNION ALL SELECT 9,  800000, 1200000,   60, 4, 1   -- 其他
),
day_emotion AS (
    -- 每天取一条手动日记的情绪作为"当天真实情绪"（KNN 的对照标签）
    SELECT DATE_SUB(CURDATE(), INTERVAL s.n DAY) AS stat_date,
           COALESCE((SELECT MAX(pd.emotion)
                       FROM psy_diary pd
                      WHERE pd.user_id = @uid
                        AND pd.diary_date = DATE_SUB(CURDATE(), INTERVAL s.n DAY)
                        AND pd.source = 1), 2)     AS emotion
      FROM seq s
),
scenes AS (
    SELECT 'home' AS scene_code
    UNION ALL SELECT 'diary'
    UNION ALL SELECT 'write'
    UNION ALL SELECT 'fm'
    UNION ALL SELECT 'test'
    UNION ALL SELECT 'classroom'
    UNION ALL SELECT 'profile'
    UNION ALL SELECT 'assistant'
),
base AS (
    SELECT de.stat_date, sc.scene_code, mp.emotion, mp.home_ms, mp.fm_ms, mp.clicks, mp.visits, mp.hour_mode
      FROM day_emotion de
      JOIN mood_profile mp ON mp.emotion = de.emotion
     CROSS JOIN scenes sc
)
SELECT @uid,
       b.stat_date,
       b.scene_code,
       -- 停留时长（毫秒）：按情绪指纹取值，叠加 ±15% 噪声避免完全同质
       ROUND((CASE b.scene_code
             WHEN 'home'      THEN b.home_ms
             WHEN 'fm'        THEN b.fm_ms
             WHEN 'diary'     THEN b.home_ms * 0.5
             WHEN 'write'     THEN b.home_ms * 0.7
             WHEN 'test'      THEN b.home_ms * 0.4
             WHEN 'classroom' THEN b.home_ms * 0.3
             WHEN 'profile'   THEN 180000
             ELSE                   b.home_ms * 0.35
       END) * (0.95 + RAND() * 0.10))                                   AS stay_ms,
       -- 进入次数
       ROUND(b.visits * (0.9 + RAND() * 0.2))                           AS visit_cnt,
       -- 点击次数：情绪越积极交互越频繁
       ROUND(b.clicks * (0.95 + RAND() * 0.10))                         AS click_cnt,
       -- 24 小时活跃位图：bit i = 第 i 小时活跃过
       CASE b.hour_mode
            WHEN 2 THEN (1 << 22) | (1 << 23) | (1 << 0) | (1 << 1)
            WHEN 3 THEN ((1 << 22) | (1 << 23) | (1 << 0) | (1 << 1)) | (FLOOR(RAND() * 1024) << 9)
            ELSE        FLOOR(RAND() * 1024) << 9
       END                                                              AS hour_mask,
       NOW(),
       NOW()
  FROM base b;

-- 3. 核对：应生成 60 天 × 8 场景 = 480 行
SELECT COUNT(*) AS 生成行数,
       MIN(stat_date) AS 起始日期,
       MAX(stat_date) AS 结束日期,
       COUNT(DISTINCT scene_code) AS 场景数,
       COUNT(DISTINCT stat_date)  AS 天数
  FROM psy_behavior_feature
 WHERE user_id = @uid
   AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 60 DAY);

-- =====================================================================================
-- 后续动作（在前端完成，不需要 SQL）：
--   1. 用 test 账号登录 → 我的 → 「无感」标签页 → 打开开关
--   2. 点击「生成今日推测」→ 得到推测情绪、置信度与「你最像哪一天」的解释
--   3. 触发预警：我的 → 情绪预警 → 手动重新评估
--
-- 清理演示数据（答辩后如需还原）：
--   DELETE FROM psy_behavior_feature WHERE user_id = 1 AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 60 DAY);
--   DELETE FROM psy_emotion_prediction WHERE user_id = 1;
--   UPDATE sys_user SET sense_enabled = 0, sense_behavior = 0 WHERE user_id = 1;
-- =====================================================================================
