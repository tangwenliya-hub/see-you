/**
 * 核对「管理员接口注解」与「JwtInterceptor 旧 uri 规则」是否一致。
 *
 * 用法：node scripts/check_admin_annotation.mjs
 *
 * 背景：2026-11-09 把管理员权限从 17 处 uri.startsWith 硬编码改成 @RequireAdmin 注解，
 * 旧规则暂时保留做兜底（两者是「或」关系）。本脚本确保两边的判定完全一致：
 *   - 旧规则拦、注解没标 → 以后删旧规则会漏权限（必须为 0）
 *   - 注解标了、旧规则不拦 → 权限变严，需人工确认是否合理（应为 0，除非有意收紧）
 * 新增管理端接口时跑一次，能立刻发现忘记标注的方法。
 */
import fs from 'node:fs'
import path from 'node:path'

const dir = path.resolve(
  process.cwd(),
  'see_you/src/main/java/com/seeyou/see_you/controller'
)

// 与 JwtInterceptor.preHandle 里的兜底规则保持同步
function legacyNeedAdmin(uri, method) {
  if (uri.includes('/admin/')) return true
  if (uri.startsWith('/user/list') || uri.startsWith('/user/status')) return true
  if (uri.startsWith('/diary/admin/')) return true
  if (uri.startsWith('/user/import')) return true
  if (method === 'DELETE' && uri.startsWith('/user/')) return true
  if (
    uri.startsWith('/comment/listAllWithUser') ||
    uri.startsWith('/comment/updateStatus') ||
    uri.startsWith('/comment/batchUpdateStatus') ||
    uri.startsWith('/comment/batchDelete')
  )
    return true
  if (method === 'DELETE' && uri.startsWith('/comment/')) return true
  if (method === 'PUT' && uri.startsWith('/comment/update')) return true
  if (
    uri.startsWith('/feedback/list') ||
    uri.startsWith('/feedback/update') ||
    uri.startsWith('/feedback/reply')
  )
    return true
  if (method === 'DELETE' && uri.startsWith('/feedback/')) return true
  if (['POST', 'PUT', 'DELETE'].includes(method)) {
    if (uri.startsWith('/paper/') || uri.startsWith('/question/') || uri.startsWith('/option/'))
      return true
    // 播放计数是普通用户操作，不算管理员写操作
    if (uri.startsWith('/video/') || uri.startsWith('/audio/')) return !uri.includes('/play/')
  }
  return false
}

const rows = []
for (const file of fs.readdirSync(dir).filter((f) => f.endsWith('.java'))) {
  const lines = fs.readFileSync(path.join(dir, file), 'utf8').split('\n')
  let classPath = ''
  let classAnnotated = false
  let inClass = false
  let pending = false

  for (const raw of lines) {
    const line = raw.trim()

    if (!classPath) {
      // 类级 @RequireAdmin 写在 @RequestMapping 之前，这里必须一并记录
      if (/@RequireAdmin/.test(line)) classAnnotated = true
      const m = line.match(/@RequestMapping\("([^"]*)"\)/)
      if (m) classPath = m[1]
      continue
    }
    if (!inClass) {
      if (/@RequireAdmin/.test(line)) classAnnotated = true
      if (/public class/.test(line)) inClass = true
      continue
    }

    if (/@RequireAdmin/.test(line)) {
      pending = true
      continue
    }
    if (/^@(Get|Post|Put|Delete)Mapping/.test(line)) {
      const mm = line.match(/^@(Get|Post|Put|Delete)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"/)
      if (mm) {
        const method = mm[1].toUpperCase()
        const full = (classPath + mm[2]).replace(/\{id:[^}]*\}/g, '{id}')
        rows.push({
          file: file.replace('.java', ''),
          uri: full,
          method,
          annotated: pending || classAnnotated,
          legacy: legacyNeedAdmin(full, method),
        })
      }
      pending = false
    }
  }
}

const gap = rows.filter((r) => r.legacy && !r.annotated)
const over = rows.filter((r) => !r.legacy && r.annotated)

console.log('接口总数:', rows.length)
console.log('注解覆盖数:', rows.filter((r) => r.annotated).length)
console.log('\n【风险】旧规则判 admin 但注解未标（删旧规则会漏）:', gap.length)
gap.forEach((r) => console.log(`  ${r.method} ${r.uri}   (${r.file})`))
console.log('\n【变严】注解标了但旧规则原本不拦（需人工确认）:', over.length)
over.forEach((r) => console.log(`  ${r.method} ${r.uri}   (${r.file})`))

process.exit(gap.length > 0 ? 1 : 0)
