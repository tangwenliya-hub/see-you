/**
 * 演示数据种子脚本：造出两个"趋势通道"用户，把一级 / 二级预警点亮。
 *
 *   demo_user_a  演示·小晴  →  一级 L1_TREND   （负面占比 0.75 + 斜率下滑 + 低落 2 天）
 *   demo_user_b  演示·阿沉  →  二级 L2_DOWNCAST（低落 3 天，不到三级）
 *
 * 两个账号密码都是 123456。可重复执行（先清自己旧的再插）。
 * 用法：node scripts/seed_demo.mjs [端口，默认 8081]
 */
import { execFileSync } from 'node:child_process'

const PORT = process.argv[2] || '8081'
const BASE = `http://localhost:${PORT}`
const MYSQL = 'E:/mysql-8.4.8-winx64/bin/mysql'

let passCount = 0, failCount = 0
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? '✅' : '❌'} ${name}${extra ? '  ' + extra : ''}`)
  cond ? passCount++ : failCount++
}

const sql = (q) => execFileSync(MYSQL, ['-uroot', ...(process.env.MYSQL_PWD ? ['-p' + process.env.MYSQL_PWD] : []), '-D', 'see_you', '-N', '-e', q]  // 密码走环境变量，避免明文入库, { encoding: 'utf8' })
  .replace(/\n$/, '')

const localToday = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const dayOffset = (n) => {
  const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
  d.setUTCDate(d.getUTCDate() - n)
  return d.toISOString().slice(0, 10)
}

async function A(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return res.json()
}

/** 登录：验证码 → dev-code → login */
async function login(username, password) {
  const cap = await A('/auth/captcha')
  const key = cap.data?.captchaKey ?? cap.data?.data?.captchaKey
  const code = (await A(`/auth/captcha/dev-code?key=${key}`)).data?.code
  const r = await A('/user/login', { method: 'POST', body: { username, password, captchaKey: key, captchaCode: code } })
  const d = r.data?.data || r.data
  if (!d?.token) throw new Error('login failed: ' + JSON.stringify(r).slice(0, 200))
  return d.token
}

// 情绪码：1开心 2平静 3疲惫 4难过 5生气 6焦虑 7兴奋 8满足 9其他
// 指数：   100    70   50   20   25   40    90   85   55
const USERS = [
  {
    username: 'demo_user_a', nickname: '演示·小晴',
    // [70, 25, 40, 20]：负面 3/4=0.75≥0.5，低落 2 天（焦虑+难过，<3 不二级），斜率明显下滑 → 一级
    diaries: [
      { off: 3, emotion: 2, text: '还行，就是有点闲不下来，日子过得挺平静。' },
      { off: 2, emotion: 5, text: '今天火气有点大，和人闹了点不愉快，冷静了一晚上。' },
      { off: 1, emotion: 6, text: '心里七上八下的，事情越堆越多，有点慌。' },
      { off: 0, emotion: 4, text: '说不出的低落，什么都不想做，就想躺着。' },
    ],
    expectLevel: 1,
  },
  {
    username: 'demo_user_b', nickname: '演示·阿沉',
    // [70, 50, 20, 40]：低落 3 天（疲惫+难过+焦虑 ≥3 → 二级；<5 且 avg=45>35 不三级）→ 二级
    diaries: [
      { off: 3, emotion: 2, text: '一切照旧，平平淡淡的一天。' },
      { off: 2, emotion: 3, text: '连续几天没睡好，整个人都垮了，好累。' },
      { off: 1, emotion: 4, text: '有点扛不住了，偷偷难过了一会儿。' },
      { off: 0, emotion: 6, text: '越想越慌，担心的事情太多了，睡不着。' },
    ],
    expectLevel: 2,
  },
]

async function main() {
  const today = localToday()
  const from = dayOffset(3)
  console.log(`种子窗口：${from} ~ ${today}\n`)

  // 0. 建演示账号（密码哈希直接复制 test 的 = 123456；已存在则跳过）
  const hash = sql(`SELECT password FROM sys_user WHERE username='test' LIMIT 1`)
  for (const u of USERS) {
    sql(`INSERT INTO sys_user (username, password, nickname, role, status, create_time)
         SELECT '${u.username}', '${hash.trim()}', '${u.nickname}', 0, 1, NOW()
         FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='${u.username}')`)
    ok(`账号 ${u.username}（${u.nickname}）就绪`, true)
  }

  // 1. 写日记：先清自己的旧演示日记，再插（source=1 手动，参与 KNN/统计/趋势窗口）
  for (const u of USERS) {
    sql(`DELETE FROM psy_diary WHERE user_id=(SELECT user_id FROM sys_user WHERE username='${u.username}')
         AND diary_date BETWEEN '${from}' AND '${today}' AND diary_content LIKE '%[demo]%'`)
    for (const d of u.diaries) {
      const date = dayOffset(d.off)
      sql(`INSERT INTO psy_diary (user_id, diary_content, emotion, diary_date, is_public, source, create_time, update_time)
           VALUES ((SELECT user_id FROM sys_user WHERE username='${u.username}'),
                   '[demo] ${d.text}', ${d.emotion}, '${date}', 0, 1, NOW(), NOW())`)
    }
    ok(`${u.username} 写入 ${u.diaries.length} 篇趋势日记`, true)
  }

  // 2. 登录并手动触发趋势评估（日常由定时任务扫描，这个接口答辩现场也能用）
  for (const u of USERS) {
    try {
      const token = await login(u.username, '123456')
      const r = await A('/risk/evaluate', { method: 'POST', token })
      const d = r.data || {}
      const hit = d.triggered && d.level === u.expectLevel
      ok(`${u.nickname} 趋势评估 → ${d.levelName || '未触发'}`, hit,
        hit ? '' : `got=${JSON.stringify(d).slice(0, 120)}`)
    } catch (e) {
      ok(`${u.nickname} 评估失败`, false, String(e).slice(0, 150))
    }
  }

  // 3. 核对库里落了正确等级、保持未读
  for (const u of USERS) {
    const row = sql(`SELECT CONCAT(ra.alert_level, '/', ra.is_read) FROM psy_risk_alert ra
      JOIN sys_user su ON su.user_id=ra.user_id
      WHERE su.username='${u.username}' AND ra.alert_date='${today}' AND ra.trigger_rule NOT IN ('CRISIS_KEYWORD','AI_JUDGE') LIMIT 1`).trim().replace(/\r/g, '')
    ok(`${u.nickname} 预警等级/已读 = ${row}（期望 ${u.expectLevel}/0）`, row === `${u.expectLevel}/0`)
  }

  console.log(`\n完成：${passCount} 通过，${failCount} 失败`)
  process.exit(failCount ? 1 : 0)
}

main().catch(e => { console.error(e); process.exit(1) })
