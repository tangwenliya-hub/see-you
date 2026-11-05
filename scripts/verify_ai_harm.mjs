/**
 * 伤害他人语义的预警验证：词表未命中的行凶表达 → AI 精判应标记
 * 用法：node scripts/verify_ai_harm.mjs [端口=8082]
 */
const PORT = process.argv[2] || '8082'
const BASE = `http://localhost:${PORT}`
const MYSQL = 'E:/mysql-8.4.8-winx64/bin/mysql'
const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const log = (...a) => console.log('[verify]', ...a)
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function sql(q) {
  const { execFileSync } = await import('node:child_process')
  return execFileSync(MYSQL, ['-uroot', ...(process.env.MYSQL_PWD ? ['-p' + process.env.MYSQL_PWD] : []), '-D', 'see_you', '-N', '-e', q]  // 密码走环境变量，避免明文入库, { encoding: 'utf8' })
    .replace(/mysql: \[Warning\][^\n]*\n/g, '').trim()
}
async function A(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  let data = null
  try { data = await res.json() } catch { }
  return { status: res.status, data }
}
async function login(username, password) {
  const cap = await A('/auth/captcha')
  const key = cap.data?.data?.captchaKey
  const code = (await A(`/auth/captcha/dev-code?key=${encodeURIComponent(key)}`)).data?.data?.code
  const r = await A('/user/login', { method: 'POST', body: { username, password, captchaKey: key, captchaCode: code } })
  const d = r.data?.data || {}
  if (!d.token) throw new Error('login failed')
  return d.token
}
const MARK = '[HARM-PROBE]'
const saveDiary = (token, content, emotion) =>
  A('/diary/save', { method: 'POST', token, body: { diaryContent: content, emotion, diaryDate: today, isPublic: 0 } })
    .then(r => r.data?.code === 200)

async function main() {
  let passed = 0, failed = 0
  const ok = (name, cond, extra = '') => {
    if (cond) { passed++; log(`✅ ${name}${extra ? ' · ' + extra : ''}`) }
    else { failed++; log(`❌ ${name}${extra ? ' · ' + extra : ''}`) }
  }
  await sql(`DELETE FROM psy_risk_alert WHERE alert_date='${today}' AND user_id=(SELECT user_id FROM sys_user WHERE username='test') AND (trigger_rule IN ('AI_JUDGE','CRISIS_KEYWORD') AND create_time > NOW() - INTERVAL 10 MINUTE)`)
  await sql(`DELETE FROM psy_diary WHERE diary_date='${today}' AND diary_content LIKE '%${MARK}%'`)

  const token = await login('test', '123456')

  // 1. 词表未命中、指向他人的行凶表达 → AI 精判应标记
  const t1 = `同事${MARK} 天天针对我，抢我的项目还背后告状，我真的想捅了他`
  ok('保存行凶表达日记（词表未命中）', await saveDiary(token, t1, 5))
  let hit = null
  for (let i = 0; i < 12; i++) {
    await sleep(2500)
    const n = await sql(`SELECT COUNT(*) FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
    if (Number(n) > 0) { hit = true; break }
  }
  ok('AI 研判生成预警', !!hit)

  // 2. 词表命中「拿刀砍人」→ 危机通道立即预警
  ok('保存含「拿刀砍人」日记', await saveDiary(token, `气死我了 ${MARK} 我准备拿刀砍人了`, 5))
  await sleep(2000)
  const kw = await sql(`SELECT COUNT(*) FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='CRISIS_KEYWORD' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
  ok('危机词通道立即预警', Number(kw) > 0)

  // 3. 清理
  await sql(`DELETE FROM psy_risk_alert WHERE alert_date='${today}' AND user_id=(SELECT user_id FROM sys_user WHERE username='test') AND create_time > NOW() - INTERVAL 10 MINUTE`)
  await sql(`DELETE FROM psy_diary WHERE diary_date='${today}' AND diary_content LIKE '%${MARK}%'`)
  const left = await sql(`SELECT (SELECT COUNT(*) FROM psy_diary WHERE diary_content LIKE '%${MARK}%') + (SELECT COUNT(*) FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule IN ('AI_JUDGE','CRISIS_KEYWORD') AND user_id=(SELECT user_id FROM sys_user WHERE username='test'))`)
  ok('测试数据零残留', Number(left) === 0, 'left=' + left)

  log(`\n结果：${passed} 通过 / ${failed} 失败`)
  process.exit(failed ? 1 : 0)
}
main().catch(e => { console.error('[verify] 异常:', e.message); process.exit(1) })
