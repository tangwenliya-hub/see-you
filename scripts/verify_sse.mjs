/**
 * 情绪预警 SSE 实时推送端到端验证
 *
 * 链路：管理端 SSE 通道鉴权 → 用探针管理员连接 → 普通用户保存含危机词的日记
 *      → 后端落库预警并 SSE 广播 → 探针收到 risk-alert 事件（含中文名触发规则）
 *
 * 测试数据全部自动清理。用法：node scripts/verify_sse.mjs [端口=8082]
 */
const PORT = process.argv[2] || '8082'
// 用 127.0.0.1 而不是 localhost：Windows 上 localhost 可能先解析到 ::1，
// 而内嵌 Tomcat 只监听了 IPv4，表现为 node fetch 直接 ECONNREFUSED（curl 却正常）。
const HOST = process.env.VERIFY_HOST || '127.0.0.1'
const BASE = `http://${HOST}:${PORT}`
const MYSQL = 'E:/mysql-8.4.8-winx64/bin/mysql'
const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const log = (...a) => console.log('[verify]', ...a)

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
  try { data = await res.json() } catch { /* SSE 流无 JSON */ }
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

async function main() {
  let passed = 0, failed = 0
  const ok = (name, cond, extra = '') => {
    if (cond) { passed++; log(`✅ ${name}`, extra) }
    else { failed++; log(`❌ ${name}`, extra) }
  }

  // 0. 清理残留：今天的预警（让危机通道走全新插入）+ 旧探针
  await sql(`DELETE FROM psy_risk_alert WHERE alert_date='${today}' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
  await sql(`DELETE FROM psy_diary WHERE diary_date='${today}' AND user_id=(SELECT user_id FROM sys_user WHERE username='test') AND diary_content LIKE '%SSE-PROBE%'`)
  await sql(`DELETE FROM sys_user WHERE username='sse_probe_admin'`)

  // 1. 探针管理员：复制 test 用户的密码哈希，角色改成 1
  sql(`INSERT INTO sys_user (username, password, nickname, role, status, create_time)
       SELECT 'sse_probe_admin', password, 'SSE探针管理员', 1, status, NOW() FROM sys_user WHERE username='test'`)
  const probeToken = await login('sse_probe_admin', '123456')
  ok('探针管理员登录', !!probeToken)

  // 2. 鉴权边界：无 token → 拒绝；普通用户 token → 拒绝
  const r1 = await A('/risk/admin-stream')
  ok('无 token 被拒绝', (r1.status === 401 || r1.data?.code === 401), JSON.stringify(r1.data).slice(0, 80))
  const userToken = await login('test', '123456')
  const r2 = await A('/risk/admin-stream?token=' + encodeURIComponent(userToken))
  ok('普通用户被拒绝', (r2.status === 403 || r2.data?.code === 403), JSON.stringify(r2.data).slice(0, 80))

  // 3. 管理员连接 SSE 流（fetch 流式读取，等 connected 事件）
  const ctrl = new AbortController()
  const sseRes = await fetch(`${BASE}/risk/admin-stream?token=${encodeURIComponent(probeToken)}`, {
    headers: { Accept: 'text/event-stream' }, signal: ctrl.signal,
  })
  ok('SSE 通道建立', sseRes.status === 200 && (sseRes.headers.get('content-type') || '').includes('text/event-stream'),
    'content-type=' + sseRes.headers.get('content-type'))

  const reader = sseRes.body.getReader()
  const decoder = new TextDecoder()
  let buf = '', events = []
  const pump = (async () => {
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buf.indexOf('\n\n')) > -1) {
          const chunk = buf.slice(0, idx); buf = buf.slice(idx + 2)
          const ev = { event: 'message', data: '' }
          for (const line of chunk.split('\n')) {
            if (line.startsWith('event:')) ev.event = line.slice(6).trim()
            else if (line.startsWith('data:')) ev.data += line.slice(5).trim()
          }
          events.push(ev)
        }
      }
    } catch { /* abort 正常 */ }
  })()

  const waitEvent = async (name, timeoutMs = 12000) => {
    const start = Date.now()
    while (Date.now() - start < timeoutMs) {
      const hit = events.find(e => e.event === name)
      if (hit) return hit
      await new Promise(r => setTimeout(r, 200))
    }
    return null
  }

  ok('收到 connected 事件', !!(await waitEvent('connected')))

  // 4. 普通用户保存含危机词的日记 → 危机通道 → 落库 + SSE 广播
  // 注意：这里必须用「三级危机词」。「撑不下去了」已按设计降为二级预警词
  // （见 app.alert.warning-words），拿它测三级会失败——那是规则变了，不是 bug。
  const save = await A('/diary/save', {
    method: 'POST', token: userToken,
    body: { diaryContent: '[SSE-PROBE] 测试危机词推送：我真的不想活了', emotion: 4, diaryDate: today },
  })
  ok('危机日记保存', save.data?.code === 200 || save.status === 200, JSON.stringify(save.data).slice(0, 120))

  const alertEv = await waitEvent('risk-alert')
  ok('管理员收到 risk-alert 实时事件', !!alertEv)
  if (alertEv) {
    const d = JSON.parse(alertEv.data)
    ok('事件含用户名', !!d.userName, 'userName=' + d.userName)
    ok('事件含中文规则名', d.triggerRuleName === '危机词命中（即时关怀）', 'triggerRuleName=' + d.triggerRuleName)
    ok('事件等级=3', Number(d.level) === 3, 'level=' + d.level)
  }

  // 5. 列表接口返回中文规则名
  const list = await A('/risk/list?page=1&size=5', { token: userToken })
  const row = list.data?.data?.records?.find(x => x.alertDate === today)
  ok('列表返回 triggerRuleName', row?.triggerRuleName === '危机词命中（即时关怀）', 'got=' + row?.triggerRuleName)

  // 5.1 求助渠道由后端下发（前端不再写死），且主热线与清单首条一致
  const latest = await A('/risk/latest', { token: userToken })
  const ld = latest.data?.data || {}
  ok('latest 返回 crisisHotlines 清单', Array.isArray(ld.crisisHotlines) && ld.crisisHotlines.length >= 3,
    'len=' + (ld.crisisHotlines?.length ?? 'null'))
  ok('crisisHotline 与清单首条一致', ld.crisisHotline && ld.crisisHotline === ld.crisisHotlines?.[0],
    `hotline=${ld.crisisHotline}`)

  // 6. 幂等：同一用户当天重复触发不应再广播（无第二条 risk-alert）
  const before = events.filter(e => e.event === 'risk-alert').length
  await A('/diary/save', {
    method: 'POST', token: userToken,
    body: { diaryContent: '[SSE-PROBE] 第二次危机词记录：还是难受，不想活', emotion: 4, diaryDate: today },
  })
  await new Promise(r => setTimeout(r, 1500))
  const after = events.filter(e => e.event === 'risk-alert').length
  ok('当天重复触发不再重复广播', after === before, `事件数 ${before} -> ${after}`)

  // 6.1 预警词通道（二级）：清掉今天的记录后写「撑不下去了」→ 应为 WARNING_WORD / 二级 / 非危机
  await sql(`DELETE FROM psy_risk_alert WHERE alert_date='${today}' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
  const wBefore = events.filter(e => e.event === 'risk-alert').length
  const wSave = await A('/diary/save', {
    method: 'POST', token: userToken,
    body: { diaryContent: '[SSE-PROBE] 预警词：这活我实在撑不下去了', emotion: 4, diaryDate: today },
  })
  ok('预警词日记保存', wSave.data?.code === 200 || wSave.status === 200)
  let wHit = null
  const wt0 = Date.now()
  while (Date.now() - wt0 < 8000) {
    const all = events.filter(e => e.event === 'risk-alert')
    if (all.length > wBefore) { wHit = JSON.parse(all[all.length - 1].data); break }
    await new Promise(r => setTimeout(r, 200))
  }
  ok('预警词通道触发推送', !!wHit, wHit ? '' : '8 秒内未收到新的 risk-alert')
  if (wHit) {
    ok('预警词通道规则名', wHit.triggerRule === 'WARNING_WORD', 'rule=' + wHit.triggerRule)
    ok('预警词通道等级=2', Number(wHit.level) === 2, 'level=' + wHit.level)
    ok('预警词不算危机关怀', wHit.crisis === false, 'crisis=' + wHit.crisis)
  }

  // 6.2 误报标记 → 后端应实时推送 risk-misreport 给在线管理员
  const ml = await A('/risk/list?page=1&size=5', { token: userToken })
  const mRow = ml.data?.data?.records?.find(x => x.alertDate === today)
  ok('取得待标记的预警', !!mRow, 'alertId=' + mRow?.alertId)
  if (mRow) {
    const mb = events.filter(e => e.event === 'risk-misreport').length
    const mr = await A(`/risk/${mRow.alertId}/misreport`, { method: 'POST', token: userToken })
    ok('误报标记接口成功', mr.data?.code === 200, JSON.stringify(mr.data).slice(0, 120))
    let mHit = null
    const mt0 = Date.now()
    while (Date.now() - mt0 < 8000) {
      const all = events.filter(e => e.event === 'risk-misreport')
      if (all.length > mb) { mHit = JSON.parse(all[all.length - 1].data); break }
      await new Promise(r => setTimeout(r, 200))
    }
    ok('管理员收到 risk-misreport 实时事件', !!mHit, mHit ? '' : '8 秒内未收到误报事件')
    if (mHit) {
      ok('误报事件带用户名', !!mHit.userName, 'userName=' + mHit.userName)
      ok('误报事件带中文规则名', !!mHit.triggerRuleName, 'rule=' + mHit.triggerRuleName)
    }

    // 6.3 后台统计：分通道准确率 + 误报计数
    const st = await A('/admin/alert/stats', { token: probeToken })
    const sd = st.data?.data || {}
    ok('统计返回分通道准确率', Array.isArray(sd.channels) && sd.channels.length > 0,
      'channels=' + (sd.channels ? sd.channels.length : 'null'))
    const wch = (sd.channels || []).find(c => c.rule === 'WARNING_WORD')
    ok('WARNING_WORD 通道准确率=0（唯一一条被标误报）',
      !!wch && wch.misreport >= 1 && wch.accuracy === 0, JSON.stringify(wch))
    ok('统计整体误报数≥1', Number(sd.misreport) >= 1, 'misreport=' + sd.misreport)
  }
  ctrl.abort()
  await pump.catch(() => {})

  // 7. 清理测试数据（必须 await：process.exit 会杀死尚未完成的 mysql 子进程）
  await sql(`DELETE FROM psy_risk_alert WHERE alert_date='${today}' AND user_id=(SELECT user_id FROM sys_user WHERE username='test')`)
  await sql(`DELETE FROM psy_diary WHERE diary_date='${today}' AND user_id=(SELECT user_id FROM sys_user WHERE username='test') AND diary_content LIKE '%SSE-PROBE%'`)
  await sql(`DELETE FROM sys_user WHERE username='sse_probe_admin'`)
  log('清理完成')

  log(`\n结果：${passed} 通过 / ${failed} 失败`)
  process.exit(failed ? 1 : 0)
}

main().catch(e => { console.error('[verify] 异常:', e); process.exit(1) })
