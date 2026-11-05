/**
 * 列表导出 CSV 工具。
 *
 * 后台所有列表页共用：把当前页（或全量）数据导出成 CSV 文件下载。
 * 带 UTF-8 BOM，Excel 直接双击打开不会乱码。
 *
 * @param {string} filename 文件名（不含扩展名）
 * @param {Array<Object>} rows   数据行
 * @param {Array<{label:string, field:string, fmt?:Function}>} columns 列定义
 */
export function exportCsv(filename, rows, columns) {
  if (!rows || rows.length === 0) {
    return { ok: false, msg: '没有可导出的数据' }
  }
  const esc = (v) => {
    if (v === null || v === undefined) return ''
    let s = String(v)
    // 防止 CSV 公式注入：以 = + - @ 开头的内容前置单引号
    if (/^[=+\-@]/.test(s)) s = "'" + s
    s = s.replace(/"/g, '""')
    return /[",\n\r]/.test(s) ? '"' + s + '"' : s
  }

  const head = columns.map((c) => esc(c.label)).join(',')
  const body = rows.map((r) => columns.map((c) => {
    const raw = typeof c.field === 'function' ? c.field(r) : r[c.field]
    const v = c.fmt ? c.fmt(raw, r) : raw
    return esc(v)
  }).join(','))

  const csv = head + '\r\n' + body.join('\r\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filename}_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  return { ok: true, count: rows.length }
}

/** 时间格式化：后端返回的 LocalDateTime 数组或字符串都能处理 */
export function fmtTime(v) {
  if (!v) return '-'
  if (Array.isArray(v)) {
    const [y, m, d, hh = 0, mm = 0, ss = 0] = v
    const p = (n) => String(n).padStart(2, '0')
    return `${y}-${p(m)}-${p(d)} ${p(hh)}:${p(mm)}:${p(ss)}`
  }
  return String(v).replace('T', ' ').slice(0, 19)
}

/** 日期格式化：只要年月日 */
export function fmtDate(v) {
  const t = fmtTime(v)
  return t === '-' ? '-' : t.slice(0, 10)
}
