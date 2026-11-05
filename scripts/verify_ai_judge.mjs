/**
 * AI 精判（两级漏斗第二级）端到端验证
 *
 * 链路：保存「词表未命中但语义高危」的日记 → 异步 LLM 研判 → 落 AI_JUDGE 预警
 *      → /risk/list 可见（中文规则名）；再保存正常日记 → 不产生新预警。
 * 测试数据全部自动清理（注意 await，否则 process.exit 会杀掉 mysql 子进程）。
 * 用法：node scripts/verify_ai_judge.mjs [端口=8082]
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

/** 登录：验证码 → dev-code → login（Result 包一层 data：res.data = {code, data}） */
async function login(username, password) {
  const cap = await A('/auth/captcha')
  const key = cap.data?.data?.captchaKey
  const code = (await A(`/auth/captcha/dev-code?key=${encodeURIComponent(key)}`)).data?.data?.code
  const r = await A('/user/login', { method: 'POST', body: { username, password, captchaKey: key, captchaCode: code } })
  const d = r.data?.data || {}
  if (!d.token) throw new Error('login failed: ' + JSON.stringify(r.data).slice(0, 200))
  return d.token
}

const MARK = '[AIJUDGE-PROBE]'
async function saveDiary(token, content, emotion) {
  const r = await A('/diary/save', {
    method: 'POST', token,
    body: { diaryContent: content, emotion, diaryDate: today, isPublic: 0 },
  })
  return r.data?.code === 200
}

async function main() {
  let passed = 0, failed = 0
  const ok = (name, cond, extra = '') => {
    if (cond) { passed++; log(`✅ ${name}${extra ? ' · ' + extra : ''}`) }
    else { failed++; log(`❌ ${name}${extra ? ' · ' + extra : ''}`) }
  }

  // 0. 清理今天的探针残留
  await sql(`DELETE FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
  await sql(`DELETE FROM psy_diary WHERE diary_date='${today}' AND diary_content LIKE '%${MARK}%'`)

  const token = await login('test', '123456')
  ok('登录 test 用户', !!token)

  // 1. 词表未命中 + 语义高危 → 应触发 AI 精判（异步，等 LLM）
  const risky = `说实话最近真的${MARK} 撑不住了，感觉整个人被掏空，有时候觉得自己消失了会更轻松一点，没有人会发现吧`
  ok('保存语义高危日记（词表未命中）', await saveDiary(token, risky, 4))

  let aiAlert = null
  for (let i = 0; i < 12; i++) {
    await sleep(2500)
    const rows = await sql(`SELECT COUNT(*) FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
    if (Number(rows) > 0) { aiAlert = true; break }
  }
  ok('AI 研判生成预警（≤30s）', !!aiAlert)

  // 2. 预警字段正确
  const detail = await sql(`SELECT alert_level, trigger_detail FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE' AND user_id=(SELECT user_id FROM sys_user WHERE username='test') LIMIT 1`)
  ok('等级为三级', detail.startsWith('3'), detail.split('\t')[0])
  ok('判定理由落库且不含原文', detail.split('\t')[1] && !detail.includes(MARK))

  // 3. 列表接口返回中文规则名
  const list = await A('/risk/list?page=1&size=5', { token })
  const row = list.data?.data?.records?.find(x => x.triggerRule === 'AI_JUDGE')
  ok('列表返回中文规则名', row?.triggerRuleName === 'AI 情绪研判（辅助判断）', 'got=' + row?.triggerRuleName)
  ok('crisis 标记为 true（关怀弹窗样式）', row?.crisis === true)

  // 4. 正常日记 → 不应产生新 AI 预警
  const cntBefore = await sql(`SELECT COUNT(*) FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE'`)
  ok('保存正常日记', await saveDiary(token, `今天天气不错 ${MARK} 吃到了好吃的蛋糕`, 1))
  await sleep(6000)
  const cntAfter = await sql(`SELECT COUNT(*) FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE'`)
  ok('正常日记不触发预警', cntBefore === cntAfter, `${cntBefore} -> ${cntAfter}`)

  // 5. 清理测试数据（必须 await，否则 exit 杀掉 mysql 子进程导致静默失败）
  await sql(`DELETE FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
  await sql(`DELETE FROM psy_diary WHERE diary_date='${today}' AND diary_content LIKE '%${MARK}%'`)
  const left = await sql(`SELECT (SELECT COUNT(*) FROM psy_diary WHERE diary_content LIKE '%${MARK}%') + (SELECT COUNT(*) FROM psy_risk_alert WHERE alert_date='${today}' AND trigger_rule='AI_JUDGE')`)
  ok('测试数据零残留', Number(left) === 0, 'left=' + left)

  log(`\n结果：${passed} 通过 / ${failed} 失败`)
  process.exit(failed ? 1 : 0)
}

main().catch(e => { console.error('[verify] 异常:', e.message); process.exit(1) })
