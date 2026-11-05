/**
 * 预警词库管理端到端验证：GET/新增/去重/非法值/删除 + 内存立即生效 + sys_config 持久化
 * 测试数据全部自动清理。用法：node scripts/verify_words.mjs [端口=8082]
 */
const PORT = process.argv[2] || '8082'
const HOST = process.env.VERIFY_HOST || '127.0.0.1'
const BASE = `http://${HOST}:${PORT}`
const MYSQL = 'E:/mysql-8.4.8-winx64/bin/mysql'
const log = (...a) => console.log('[words]', ...a)

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
  try { data = await res.json() } catch {}
  return { status: res.status, data }
}

async function login(username, password) {
  const cap = await A('/auth/captcha')
  const key = cap.data?.data?.captchaKey
  const code = (await A(`/auth/captcha/dev-code?key=${encodeURIComponent(key)}`)).data?.data?.code
  const r = await A('/user/login', { method: 'POST', body: { username, password, captchaKey: key, captchaCode: code } })
  const d = r.data?.data || {}
  if (!d.token) throw new Error('login failed: ' + JSON.stringify(r.data).slice(0, 200))
  return d.token
}

async function main() {
  let passed = 0, failed = 0
  const ok = (name, cond, extra = '') => {
    if (cond) { passed++; log(`✅ ${name}`, extra) }
    else { failed++; log(`❌ ${name}`, extra) }
  }

  await sql(`DELETE FROM sys_config WHERE config_key IN ('alert.crisisWords','alert.warningWords','alert.crisisExcludes')`)
  await sql(`DELETE FROM sys_user WHERE username='sse_probe_admin'`)
  sql(`INSERT INTO sys_user (username, password, nickname, role, status, create_time)
       SELECT 'sse_probe_admin', password, '词库探针管理员', 1, status, NOW() FROM sys_user WHERE username='test'`)
  const token = await login('sse_probe_admin', '123456')
  ok('探针管理员登录', !!token)

  // 1. 词库列表：内存默认（yml）词库可读
  const g1 = await A('/admin/alert/words', { token })
  const crisis = g1.data?.data?.crisis || []
  ok('GET 词库列表', g1.data?.code === 200 && crisis.includes('不想活了'), `危机词 ${crisis.length} 个`)

  // 2. 新增预警词 → 内存立即生效（GET 能读到）
  const a1 = await A('/admin/alert/words', { method: 'POST', token, body: { type: 'warning', word: '词库探针测试词' } })
  const g2 = await A('/admin/alert/words', { token })
  ok('新增预警词成功', a1.data?.code === 200)
  ok('新增后内存立即可读', (g2.data?.data?.warning || []).includes('词库探针测试词'))

  // 3. 持久化：sys_config 落库
  const row = await sql(`SELECT config_value FROM sys_config WHERE config_key='alert.warningWords'`)
  ok('sys_config 已持久化', row.includes('词库探针测试词'), row.slice(0, 60))

  // 4. 重复添加 → 400
  const a2 = await A('/admin/alert/words', { method: 'POST', token, body: { type: 'warning', word: '词库探针测试词' } })
  ok('重复添加被拒绝', a2.data?.code === 400, a2.data?.msg)

  // 5. 非法词条：带逗号 / 超长 / 未知类型 → 400
  const a3 = await A('/admin/alert/words', { method: 'POST', token, body: { type: 'warning', word: '不行,逗号' } })
  const a4 = await A('/admin/alert/words', { method: 'POST', token, body: { type: 'warning', word: 'x'.repeat(21) } })
  const a5 = await A('/admin/alert/words', { method: 'POST', token, body: { type: 'hacker', word: 'hack' } })
  ok('逗号词条被拒绝', a3.data?.code === 400, a3.data?.msg)
  ok('超长词条被拒绝', a4.data?.code === 400, a4.data?.msg)
  ok('未知词库类型被拒绝', a5.data?.code === 400, a5.data?.msg)

  // 6. 删除 → 内存与库都回到原样
  const d2 = await A('/admin/alert/words/delete', { method: 'POST', token, body: { type: 'warning', word: '词库探针测试词' } })
  const g3 = await A('/admin/alert/words', { token })
  const row2 = await sql(`SELECT config_value FROM sys_config WHERE config_key='alert.warningWords'`)
  ok('删除成功', d2.data?.code === 200)
  ok('删除后内存已移除', !(g3.data?.data?.warning || []).includes('词库探针测试词'))
  ok('删除后库已同步', !row2.includes('词库探针测试词'))

  // 7. 删除不存在的词 → 400
  const d3 = await A('/admin/alert/words/delete', { method: 'POST', token, body: { type: 'warning', word: '词库探针测试词' } })
  ok('删除不存在词条被拒绝', d3.data?.code === 400, d3.data?.msg)

  // 8. 权限：普通用户 token → 403
  const ut = await login('test', '123456')
  const u1 = await A('/admin/alert/words', { token: ut })
  ok('普通用户被拒绝', (u1.status === 403 || u1.data?.code === 403), JSON.stringify(u1.data).slice(0, 60))

  // 清理
  await sql(`DELETE FROM sys_config WHERE config_key IN ('alert.crisisWords','alert.warningWords','alert.crisisExcludes')`)
  await sql(`DELETE FROM sys_user WHERE username='sse_probe_admin'`)
  log(`\n结果：${passed} 通过 / ${failed} 失败`)
  process.exit(failed ? 1 : 0)
}

main().catch(e => { console.error('[words] 脚本异常：', e.message); process.exit(1) })
