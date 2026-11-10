-- =====================================================================================
-- 2026-11-10 清理恶意注册的垃圾账号
--
-- 背景：注册接口曾被脚本批量刷号（无验证码 + XFF 伪造绕过限流），刷出上千个垃圾账号。
-- 特征（2026-11-10 03:22~03:23 注册的一批）：
--   * 用户名：b 开头、bb/bbb 结尾、中间随机字母数字，长度 9~11（如 btYEWUxcbbb、bDKtglBHbb）
--   * 邮箱全部是 @163.com
--   * role = 0（普通用户）
--
-- 修复已上线（注册验证码 + trust-proxy 限流），本脚本只负责清库。
-- 用法（Navicat）：
--   第 1 步：只执行【第一步的 SELECT】，肉眼核对结果里没有真实用户；
--   第 2 步：确认无误后，取消注释 DELETE 并执行（一次只开一段）。
-- =====================================================================================

-- ================= 第一步：预览待删名单（只读，安全） =================
SELECT user_id, username, email, role, status, create_time
FROM sys_user
WHERE role = 0
  AND email LIKE '%@163.com'
  AND username REGEXP '^b[A-Za-z0-9]{7,9}b{1,2}$'
ORDER BY create_time;

-- ================= 第二步：确认后执行删除 =================
-- DELETE FROM sys_user
-- WHERE role = 0
--   AND email LIKE '%@163.com'
--   AND username REGEXP '^b[A-Za-z0-9]{7,9}b{1,2}$';

-- 删除后核对剩余数量（应为 0）：
-- SELECT COUNT(*) FROM sys_user WHERE role = 0
--   AND email LIKE '%@163.com'
--   AND username REGEXP '^b[A-Za-z0-9]{7,9}b{1,2}$';

-- =====================================================================================
-- 兜底说明：
-- 1. 若预览结果里混进了真实用户（例如某人真用 bxxxxxxxxbb 风格的用户名 + 163 邮箱），
--    可在 DELETE 末尾追加排除，例如：AND user_id NOT IN (162, 165);
-- 2. 垃圾号刚注册几秒就被发现，不会产生日记/评论/点赞等业务数据，直接删 sys_user 即可；
--    如不放心，可先跑一次关联检查（结果应全为 0）：
-- SELECT (SELECT COUNT(*) FROM psy_diary d JOIN sys_user u ON d.user_id=u.user_id WHERE u.username REGEXP '^b[A-Za-z0-9]{7,9}b{1,2}$') AS diary,
--        (SELECT COUNT(*) FROM sys_user_session s JOIN sys_user u ON s.user_id=u.user_id WHERE u.username REGEXP '^b[A-Za-z0-9]{7,9}b{1,2}$') AS session,
--        (SELECT COUNT(*) FROM sys_login_attempt l JOIN sys_user u ON l.username=u.username WHERE u.username REGEXP '^b[A-Za-z0-9]{7,9}b{1,2}$') AS login_attempt;
-- 3. sys_user_session / sys_login_attempt 里若有这些号的残留记录，可一并清理（可选，不影响功能）。
-- =====================================================================================
