-- ============================================================
-- 绪见 / See You —— 反馈模块演示数据（用户侧 + 管理员侧）
-- 说明：user_id 从库里现有普通用户中动态取，避免外键报错；
--      如库里用户不足 3 个，会复用同一个用户（不影响展示）。
--      主表与聊天记录成对写入，与 FeedbackWriteService 的落库逻辑一致。
-- 执行：mysql -u root -p 库名 < 20261119_seed_feedback_demo.sql
-- ============================================================

-- 取 3 个普通用户（不足则回退复用）
SET @u1 := (SELECT user_id FROM sys_user WHERE role = 0 ORDER BY user_id LIMIT 1);
SET @u2 := COALESCE((SELECT user_id FROM sys_user WHERE role = 0 ORDER BY user_id LIMIT 1, 1), @u1);
SET @u3 := COALESCE((SELECT user_id FROM sys_user WHERE role = 0 ORDER BY user_id LIMIT 1, 2), @u2);

-- ------------------------------------------------------------
-- 1. 已完成：情绪记录日期错位（有管理员回复 + 用户补充）
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u1, 'bug', '情绪记录保存后日期错位一天',
        '我在晚上 11 点多记录当天心情，保存后在情绪日历里显示的是第二天，曲线也算到后一天去了。连续记了三天都是这样，麻烦看一下。',
        '已经定位到了：晚上 23:00 之后提交的记录按服务器时间取日期，跨天时被算到下一天。我们改成按用户本地时区取日期，12 小时内会更新上线，你刷新后重新记一条应该就正常了，原来错位的两条我们也一并修正。',
        '刷新后试了，11 点半记的这条日期对了，之前错位的那两天也回到正确位置了，辛苦了！',
        '2026-11-06 09:12:40', 2, 0, '2026-11-04 23:18:22');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '我在晚上 11 点多记录当天心情，保存后在情绪日历里显示的是第二天，曲线也算到后一天去了。连续记了三天都是这样，麻烦看一下。', '2026-11-04 23:18:22'),
(@f, 'admin', '已经定位到了：晚上 23:00 之后提交的记录按服务器时间取日期，跨天时被算到下一天。我们改成按用户本地时区取日期，12 小时内会更新上线，你刷新后重新记一条应该就正常了，原来错位的两条我们也一并修正。', '2026-11-05 10:36:05'),
(@f, 'user', '刷新后试了，11 点半记的这条日期对了，之前错位的那两天也回到正确位置了，辛苦了！', '2026-11-06 09:12:40');

-- ------------------------------------------------------------
-- 2. 处理中：情绪日历按月导出
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u2, 'suggest', '希望情绪日历可以按月导出图片',
        '每月复盘的时候想把情绪日历整页保存下来发给自己，现在只能一张张截图，颜色也对不齐。如果能有「导出本月情绪日历」按钮，生成一张长图就好了，最好带当月平均分。',
        NULL, NULL, NULL, 1, 0, '2026-11-08 21:07:15');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '每月复盘的时候想把情绪日历整页保存下来发给自己，现在只能一张张截图，颜色也对不齐。如果能有「导出本月情绪日历」按钮，生成一张长图就好了，最好带当月平均分。', '2026-11-08 21:07:15'),
(@f, 'admin', '这个需求很合理，已经加进 12 月的迭代计划里：在情绪日历页右上角加导出按钮，输出带月份和平均情绪的竖版长图，做好后会在更新公告里提一下，先给你留个进度。', '2026-11-09 14:22:31');

-- ------------------------------------------------------------
-- 3. 已完成：深色模式对比度
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u3, 'ui', '深色模式下「今日心情」卡片字看不清',
        '切到深色模式后，首页「今日心情」卡片上的浅灰字压在深灰底上，尤其那句鼓励语几乎看不见，晚上用眼睛很累。建议把文字提亮一点或者给卡片加个描边。',
        '确实是配色的漏项，深色主题里这张卡片的正文色没跟着变量走。已调成 #E6E6E6 并把卡片底色提了一档，另外全站扫了一遍低对比度文字，一起修了。',
        NULL, NULL, 2, 0, '2026-11-10 00:41:58');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '切到深色模式后，首页「今日心情」卡片上的浅灰字压在深灰底上，尤其那句鼓励语几乎看不见，晚上用眼睛很累。建议把文字提亮一点或者给卡片加个描边。', '2026-11-10 00:41:58'),
(@f, 'admin', '确实是配色的漏项，深色主题里这张卡片的正文色没跟着变量走。已调成 #E6E6E6 并把卡片底色提了一档，另外全站扫了一遍低对比度文字，一起修了。', '2026-11-10 11:05:12');

-- ------------------------------------------------------------
-- 4. 处理中：冥想音频音质
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u1, 'content', '「十分钟呼吸放松」后半段有电流杂音',
        '冥想音频《十分钟呼吸放松》从 6 分半开始能听到持续的电流嗡嗡声，戴耳机特别明显。前几期没有这个问题，可能是这一期导出的时候出的。',
        NULL, NULL, NULL, 1, 0, '2026-11-11 22:33:46');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '冥想音频《十分钟呼吸放松》从 6 分半开始能听到持续的电流嗡嗡声，戴耳机特别明显。前几期没有这个问题，可能是这一期导出的时候出的。', '2026-11-11 22:33:46'),
(@f, 'admin', '收到，已转给内容组做音频复核，确认后会重新录制替换这一期文件。替换完成我会在这里回你，期间可以先听《睡前身体扫描》那一期。', '2026-11-12 10:18:03');

-- ------------------------------------------------------------
-- 5. 待回复：测评结果页空白
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u2, 'bug', '心理测评提交后结果页一片空白',
        '做完 SAS 量表点提交，页面跳转后是纯白的，只有一个转圈图标一直在转，等了两分钟也没出来。按 F5 刷新之后结果才显示，分数是正常的。安卓手机浏览器和电脑上都遇到过。',
        NULL, NULL, NULL, 0, 0, '2026-11-15 20:05:37');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '做完 SAS 量表点提交，页面跳转后是纯白的，只有一个转圈图标一直在转，等了两分钟也没出来。按 F5 刷新之后结果才显示，分数是正常的。安卓手机浏览器和电脑上都遇到过。', '2026-11-15 20:05:37');

-- ------------------------------------------------------------
-- 6. 待回复：连续记录徽章 + 提醒时间
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u3, 'suggest', '想要连续记录天数的徽章，提醒时间也希望能自定义',
        '已经连续记了 27 天，但界面里没什么反馈，容易断。能不能加个连续记录徽章或者小火苗，断了有点可惜就会想接上。另外每天 20:30 的提醒太早，希望可以自己选时间，最好能按工作日分别设。',
        NULL, NULL, NULL, 0, 0, '2026-11-17 12:44:09');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '已经连续记了 27 天，但界面里没什么反馈，容易断。能不能加个连续记录徽章或者小火苗，断了有点可惜就会想接上。另外每天 20:30 的提醒太早，希望可以自己选时间，最好能按工作日分别设。', '2026-11-17 12:44:09');

-- ------------------------------------------------------------
-- 7. 已完成：使用两周的正向反馈
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u1, 'other', '用了两周，情绪曲线是真的有用',
        '本来只是随便记记，用到第二周发现「周三情绪最低」这个规律特别准，正好是我固定开会的日子，知道原因之后没那么慌了。界面也不像别的 App 那么有压迫感，谢谢你们做这个。',
        '谢谢你把这段体验写下来，这对我们很重要。周三低点那个发现其实正是情绪追踪想给人的东西：先看见规律，再想办法。如果后面想再加一层，可以在周报里写两句当周发生了什么，曲线会更有解释力。',
        NULL, NULL, 2, 0, '2026-11-13 19:26:41');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '本来只是随便记记，用到第二周发现「周三情绪最低」这个规律特别准，正好是我固定开会的日子，知道原因之后没那么慌了。界面也不像别的 App 那么有压迫感，谢谢你们做这个。', '2026-11-13 19:26:41'),
(@f, 'admin', '谢谢你把这段体验写下来，这对我们很重要。周三低点那个发现其实正是情绪追踪想给人的东西：先看见规律，再想办法。如果后面想再加一层，可以在周报里写两句当周发生了什么，曲线会更有解释力。', '2026-11-14 09:47:30');

-- ------------------------------------------------------------
-- 8. 处理中：日记图片上传
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u2, 'bug', '日记一次传 9 张图，第 6 张开始失败',
        '写日记想附几张当天的照片，选了 9 张，进度到第 6 张就卡住，后面全变成红色感叹号重试也没用。改成一次传 3 张就没问题，是不是单次上传有总量限制？',
        NULL, NULL, NULL, 1, 0, '2026-11-16 18:52:20');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '写日记想附几张当天的照片，选了 9 张，进度到第 6 张就卡住，后面全变成红色感叹号重试也没用。改成一次传 3 张就没问题，是不是单次上传有总量限制？', '2026-11-16 18:52:20'),
(@f, 'admin', '确认是单次请求总大小超了限制（原图 9 张会超过 20MB），不是你的问题。我们正在改成前端自动压缩 + 分批上传，顺带把失败提示写清楚一点。这周内能上。', '2026-11-17 10:09:44');

-- ------------------------------------------------------------
-- 9. 已完成：量表题目表述
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u3, 'content', '量表第 12 题的问法有点诱导',
        '做压力知觉量表的时候，第 12 题「你是否经常感到无法控制生活中的重要事情」这种问法读起来像在暗示我答「是」，做到后面会不自觉地往重了选。建议把选项顺序打乱或者把题干改得中性一些。',
        '这个反馈很专业，也是我们在意的问题：原量表有条目方向性，直译过来会让倾向性变明显。已经改成中性表述并把四选项顺序随机化，同时在线量表都复查了一遍，感谢你指出这一点。',
        NULL, NULL, 2, 0, '2026-11-12 16:03:27');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '做压力知觉量表的时候，第 12 题「你是否经常感到无法控制生活中的重要事情」这种问法读起来像在暗示我答「是」，做到后面会不自觉地往重了选。建议把选项顺序打乱或者把题干改得中性一些。', '2026-11-12 16:03:27'),
(@f, 'admin', '这个反馈很专业，也是我们在意的问题：原量表有条目方向性，直译过来会让倾向性变明显。已经改成中性表述并把四选项顺序随机化，同时在线量表都复查了一遍，感谢你指出这一点。', '2026-11-13 10:31:56');

-- ------------------------------------------------------------
-- 10. 待回复：风险提醒体验
-- ------------------------------------------------------------
INSERT INTO psy_feedback (user_id, type, title, feedback_content, reply_text, user_supplement, supplement_time, status, deleted, create_time)
VALUES (@u2, 'other', '情绪低的时候弹的关怀提示有点吓人',
        '前天心情分打了 2 分，提交后立刻弹出一个很大的「我们注意到你最近状态不太好」全屏提示，还给了热线电话。知道是好意，但那一刻本来只是想记一下，被这么一弹反而更紧张了，还担心是不是被人工盯着。希望能做得轻一点，或者让我自己选要不要展开。',
        NULL, NULL, NULL, 0, 0, '2026-11-18 23:57:03');
SET @f := LAST_INSERT_ID();
INSERT INTO psy_feedback_message (feedback_id, sender_type, content, create_time) VALUES
(@f, 'user', '前天心情分打了 2 分，提交后立刻弹出一个很大的「我们注意到你最近状态不太好」全屏提示，还给了热线电话。知道是好意，但那一刻本来只是想记一下，被这么一弹反而更紧张了，还担心是不是被人工盯着。希望能做得轻一点，或者让我自己选要不要展开。', '2026-11-18 23:57:03');

-- ------------------------------------------------------------
-- 校验：10 条主表 + 18 条消息
-- 状态分布：待回复 3 / 处理中 3 / 已完成 4
-- ------------------------------------------------------------
SELECT status, COUNT(*) AS cnt FROM psy_feedback WHERE deleted = 0 GROUP BY status;
SELECT COUNT(*) AS msg_cnt FROM psy_feedback_message WHERE feedback_id >= @f - 9;
