/**
 * 头像框几何分析器：量化一张框图的「圆孔位置 + 内外径」，并给出建议 scale。
 *
 * 背景：头像框图的构图千差万别（圆环偏心、装饰超出画布、竖构图），
 * 目测猜 scale 经常翻车。本脚本解析 PNG alpha 通道，量出圆环几何，
 * 按已校准的基准（框外径 ≈ 头像直径的 1.16 倍）反推建议 scale。
 *
 * 用法：
 *   node scripts/analyze_frames.js                       # 扫描 frontend/public/img/头像框*.png
 *   node scripts/analyze_frames.js path/to/框图.png      # 指定单张
 *
 * 依赖：仅 Node 内置模块（fs / zlib），无第三方依赖。
 * 校准来源：头像框3(建议205)、头像框6(建议145) 两张经人工确认的框图反推。
 */
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

const BASELINE_RATIO = 1.16 // 框圆环外径 / 头像直径 的基准值（可 ±10 目测微调）

function parsePNG(buf) {
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('不是 PNG 文件')
  let off = 8, ihdr = null, idat = [], trns = null
  while (off + 8 < buf.length) {
    const len = buf.readUInt32BE(off)
    const type = buf.toString('ascii', off + 4, off + 8)
    const data = buf.subarray(off + 8, off + 8 + len)
    if (type === 'IHDR') ihdr = { w: data.readUInt32BE(0), h: data.readUInt32BE(4), depth: data[8], ct: data[9] }
    else if (type === 'IDAT') idat.push(data)
    else if (type === 'tRNS') trns = data
    off += 12 + len
    if (type === 'IEND') break
  }
  const { w, h, depth, ct } = ihdr
  if (depth !== 8) throw new Error(`仅支持 8bit 深度，此图是 ${depth}bit`)
  const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ct]
  if (!ch) throw new Error(`不支持的颜色类型 ${ct}`)

  // inflate + 去 filter，还原每行原始像素
  const raw = zlib.inflateSync(Buffer.concat(idat))
  const stride = w * ch
  const out = Buffer.alloc(h * stride)
  let p = 0
  for (let y = 0; y < h; y++) {
    const f = raw[p++]
    for (let x = 0; x < stride; x++) {
      const a = x >= ch ? out[y * stride + x - ch] : 0
      const b = y > 0 ? out[(y - 1) * stride + x] : 0
      const c = x >= ch && y > 0 ? out[(y - 1) * stride + x - ch] : 0
      let v = raw[p++]
      if (f === 1) v = (v + a) & 255
      else if (f === 2) v = (v + b) & 255
      else if (f === 3) v = (v + ((a + b) >> 1)) & 255
      else if (f === 4) {
        const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c)
        v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 255
      }
      out[y * stride + x] = v
    }
  }
  // alpha 图
  const alpha = new Uint8Array(w * h)
  for (let i = 0; i < w * h; i++) {
    if (ct === 6) alpha[i] = out[i * 4 + 3]
    else if (ct === 4) alpha[i] = out[i * 2 + 1]
    else if (ct === 3) alpha[i] = trns ? (trns[out[i]] ?? 255) : 255
    else alpha[i] = 255
  }
  return { w, h, alpha }
}

function analyze(file) {
  const { w, h, alpha } = parsePNG(fs.readFileSync(file))
  const TH = 128
  // 不透明像素质心 ≈ 圆环中心
  let sx = 0, sy = 0, n = 0
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++)
      if (alpha[y * w + x] > TH) { sx += x; sy += y; n++ }
  if (!n) return console.log(`${path.basename(file)}: 整张全透明，跳过`)
  const cx = sx / n, cy = sy / n

  // 从质心向四方向走，第一个不透明像素 = 内径（圆孔边缘）
  const alphaAt = (x, y) => (x < 0 || y < 0 || x >= w || y >= h) ? 0 : alpha[y * w + x]
  const GAP = Math.round(Math.max(w, h) * 0.02) // 连续 GAP 个透明像素视为进入背景（2% 容差防噪声）
  // 沿射线：先找到孔边（第一段不透明），再向外走到不透明段结束 → 圆环外缘
  const rayRing = (deg) => {
    const dx = Math.cos(deg * Math.PI / 180), dy = Math.sin(deg * Math.PI / 180)
    let seen = false, first = 0, lastOpaque = 0, gapRun = 0
    for (let d = 1; d < Math.max(w, h); d++) {
      const on = alphaAt(Math.round(cx + dx * d), Math.round(cy + dy * d)) > TH
      if (on) { if (!seen) first = d; seen = true; lastOpaque = d; gapRun = 0 }
      else if (seen && ++gapRun >= GAP) return [first, lastOpaque]
    }
    return seen ? [first, lastOpaque] : null
  }
  const rays = []
  for (let a = 0; a < 360; a += 5) { const r = rayRing(a); if (r) rays.push(r) }
  if (!rays.length) return console.log(`${path.basename(file)}: 未测出圆环，跳过`)
  const median = arr => { const s = [...arr].sort((a, b) => a - b); return s[Math.floor(s.length / 2)] }
  const rIn = median(rays.map(r => r[0]))
  const rOut = median(rays.map(r => r[1])) // 中位数：少数与装饰相连的射线不干扰圆环本身

  const fDiam = (2 * rOut) / w                       // 外径占图宽比例
  const suggest = Math.round((BASELINE_RATIO * 100) / fDiam)
  const ox = ((cx - w / 2) / w * 100).toFixed(1)     // 孔心水平偏移 %
  const oy = ((cy - h / 2) / h * 100).toFixed(1)

  // 读取 AppLayout.vue 里已配置的 scale，把「建议 vs 现用」放在一起看
  const rel = `/img/${path.basename(file)}`
  let now = ''
  try {
    const vue = fs.readFileSync(path.join(__dirname, '../frontend/src/layouts/AppLayout.vue'), 'utf8')
    const m = vue.match(new RegExp(`img: ['"]${rel.replace(/[.]/g, '\\.')}['"]\\s*,\\s*scale:\\s*(\\d+)`))
    if (m) now = ` | 现用 scale ${m[1]} → 实际外径 ${(fDiam * m[1] / 100).toFixed(2)} 倍`
  } catch (_) { /* 对照信息拿不到不影响主输出 */ }

  console.log(`${path.basename(file)}  (${w}x${h})`)
  console.log(`  圆孔: 内径 ${Math.round(2 * rIn)}px | 外径 ${Math.round(2 * rOut)}px（占图宽 ${(fDiam * 100).toFixed(0)}%）| 孔心偏移 (${ox}%, ${oy}%)`)
  console.log(`  建议 scale: ${suggest}${now}   （基准外径=头像直径 ${BASELINE_RATIO} 倍，装上后目测微调 ±20 属正常）\n`)
}

const args = process.argv.slice(2)
const files = args.length ? args : fs.readdirSync(path.join(__dirname, '../frontend/public/img'))
  .filter(f => /^头像框.*\.png$/.test(f))
  .map(f => path.join(__dirname, '../frontend/public/img', f))
if (!files.length) { console.log('没找到框图，用法：node scripts/analyze_frames.js <图片路径>'); process.exit(1) }
for (const f of files) { try { analyze(f) } catch (e) { console.log(`${path.basename(f)}: 解析失败 - ${e.message}`) } }
