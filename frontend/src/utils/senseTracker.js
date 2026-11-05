/**
 * senseTracker —— 端侧脱敏行为采集器（单例）
 *
 * ============================================================================
 * 隐私红线（评审时请对照这五条逐条核对）
 * ============================================================================
 * 1. 不监听 keydown / keyup / input / paste —— 键盘输入内容一律不碰；
 * 2. 不读取 DOM 的 textContent / innerText、不读 input.value、不读 URL query、
 *    不读 localStorage / sessionStorage 的内容；
 * 3. 不调用摄像头、麦克风、录屏、陀螺仪、GPS；不使用第三方 Cookie，不做浏览器指纹；
 * 4. 只上报四类**聚合数值**：停留毫秒数、点击次数、进入次数、24 位活跃小时位图；
 *    场景用 8 个枚举之一表示，绝不上报完整 URL；
 * 5. 默认不启动。必须用户显式开启开关（opt-in）后由 AppLayout 调 enable()，
 *    关闭开关时立刻 destroy()：移除所有监听、丢弃未上报的数据。
 *
 * ============================================================================
 * 数据流
 * ============================================================================
 *   内存聚合（按场景累加） → 每 30 秒 flush → 服务端 psy_behavior_feature（幂等累加）
 *   服务端永远拿不到「用户几点几分点了哪个按钮」这种事件序列，
 *   只知道「今天在日记页待了 6 分钟、点了 34 次」。
 */
import { reportBehavior } from '../api/sense'

/** 上报间隔（毫秒） */
const FLUSH_INTERVAL = 30 * 1000

/** 路由 -> 场景枚举（白名单之外一律归 home，避免 URL 泄露隐私） */
const ROUTE_SCENE = [
  ['/main', 'home'],
  ['/diary/write', 'write'],
  ['/diary', 'diary'],
  ['/test', 'test'],
  ['/assistant', 'assistant'],
  ['/fm', 'fm'],
  ['/classroom', 'classroom'],
  ['/profile', 'profile'],
  ['/databoard', 'profile'],
  ['/feedback', 'profile'],
]

/** 场景名 -> 枚举（防御非法值） */
const SCENES = new Set(['home', 'diary', 'write', 'fm', 'test', 'classroom', 'profile', 'assistant'])

function sceneOfPath(path = '') {
  for (const [prefix, scene] of ROUTE_SCENE) {
    if (path === prefix || path.startsWith(prefix + '/') || path.startsWith(prefix + '?')) return scene
  }
  return 'home'
}

/** 今天的日期字符串 YYYY-MM-DD（服务端会校验，最多允许补昨天） */
function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

class SenseTracker {
  constructor() {
    this.enabled = false
    /** 累计数据（本次会话）：scene -> {stayMs, visitCnt, clickCnt, hourMask} */
    this.buffer = new Map()
    /** 已成功上报的量：buffer 减去它就是待上报增量 */
    this.sent = new Map()
    this.curScene = 'home'
    this.enterTs = 0
    this.timer = 0
    this.date = todayStr()
    // 绑定后保存引用，destroy 时才能精确移除
    this.onClick = this.onClick.bind(this)
    this.onVisibility = this.onVisibility.bind(this)
    this.onPageHide = this.onPageHide.bind(this)
  }

  // ==================== 生命周期 ====================

  enable(path) {
    if (this.enabled) return
    this.enabled = true
    this.date = todayStr()
    this.curScene = sceneOfPath(path)
    this.enterTs = Date.now()
    this.markVisit(this.curScene)

    // 只监听「点击」这个动作本身，不读取点击目标 —— 这是第 2 条红线的落地点
    document.addEventListener('click', this.onClick, true)
    document.addEventListener('visibilitychange', this.onVisibility)
    window.addEventListener('pagehide', this.onPageHide)

    this.timer = setInterval(() => { this.flush() }, FLUSH_INTERVAL)
  }

  /** 彻底关闭：移除监听 + 丢弃尚未上报的数据（用户关闭开关后不应再有残留） */
  disable() {
    if (!this.enabled) return
    this.enabled = false
    this.stopStay()
    document.removeEventListener('click', this.onClick, true)
    document.removeEventListener('visibilitychange', this.onVisibility)
    window.removeEventListener('pagehide', this.onPageHide)
    if (this.timer) { clearInterval(this.timer); this.timer = 0 }
    this.buffer.clear()
    this.sent.clear()
  }

  destroy() { this.disable() }

  // ==================== 事件处理 ====================

  onRouteChange(path) {
    if (!this.enabled) return
    const next = sceneOfPath(path)
    this.stopStay()
    this.curScene = next
    this.enterTs = Date.now()
    this.markVisit(next)
  }

  /** 只做 cnt++，绝不读取 event.target 的内容 */
  onClick() {
    if (!this.enabled || document.hidden) return
    const b = this.bucket(this.curScene)
    b.clickCnt++
  }

  onVisibility() {
    if (!this.enabled) return
    if (document.hidden) {
      this.stopStay()   // 切后台的时间不算「使用时长」
      this.flush()
    } else {
      this.enterTs = Date.now()
      this.markVisit(this.curScene)
    }
  }

  onPageHide() {
    if (!this.enabled) return
    this.stopStay()
    this.flush()
  }

  // ==================== 内部 ====================

  stopStay() {
    if (!this.enterTs) return
    const b = this.bucket(this.curScene)
    b.stayMs += Math.max(0, Date.now() - this.enterTs)
    b.hourMask |= 1 << new Date().getHours()
    this.enterTs = 0
  }

  markVisit(scene) {
    // 进入即把当前小时置位，同时累加一次「进入」
    const b = this.bucket(scene)
    b.visitCnt++
    b.hourMask |= 1 << new Date().getHours()
  }

  bucket(scene) {
    const s = SCENES.has(scene) ? scene : 'home'
    if (!this.buffer.has(s)) {
      this.buffer.set(s, { stayMs: 0, visitCnt: 0, clickCnt: 0, hourMask: 0 })
      this.sent.set(s, { stayMs: 0, visitCnt: 0, clickCnt: 0, hourMask: 0 })
    }
    return this.buffer.get(s)
  }

  /** 取出「增量」（累计 - 已上报） */
  collect() {
    this.stopStay()
    this.enterTs = Date.now()   // 立刻续计，flush 不打断计时
    const items = []
    for (const [scene, b] of this.buffer) {
      const s = this.sent.get(scene) || { stayMs: 0, visitCnt: 0, clickCnt: 0, hourMask: 0 }
      const stayMs = Math.round(b.stayMs - s.stayMs)
      const visitCnt = b.visitCnt - s.visitCnt
      const clickCnt = b.clickCnt - s.clickCnt
      const hourMask = b.hourMask & ~s.hourMask
      if (stayMs <= 0 && visitCnt <= 0 && clickCnt <= 0 && hourMask === 0) continue
      items.push({ sceneCode: scene, date: this.date, stayMs, visitCnt, clickCnt, hourMask })
    }
    return items
  }

  /** 上报成功后再推进 sent 基线，失败则保留增量等待下次重试 */
  markSent(items) {
    for (const it of items) {
      const b = this.buffer.get(it.sceneCode)
      const s = this.sent.get(it.sceneCode)
      if (!b || !s) continue
      if (it.stayMs > 0) s.stayMs += it.stayMs
      if (it.visitCnt > 0) s.visitCnt += it.visitCnt
      if (it.clickCnt > 0) s.clickCnt += it.clickCnt
      s.hourMask |= it.hourMask
    }
  }

  async flush() {
    if (!this.enabled) return 0
    const items = this.collect()
    if (!items.length) return 0
    try {
      const res = await reportBehavior(items)
      // 后端业务错误是 HTTP 200 + { code }，不会进 catch，必须在这里自己判。
      // 否则用户关掉开关后，采集器仍会每 30 秒打一次注定 403 的接口，控制台一直红，
      // 而且 collect() 清空过缓冲、markSent 没执行，基线会错位。
      if (res && res.code !== 200) {
        if (res.code === 403 || res.code === 401) this.disable()
        return 0
      }
      this.markSent(items)
      return items.length
    } catch (e) {
      // 网络层 401/403（网关/Nginx/非业务错误）同样直接停用，其它错误留给下次重试
      if (e?.response?.status === 403 || e?.response?.status === 401) this.disable()
      return 0
    }
  }

  /** 开发调试用：看看当前累计了什么（注意：这里只有数字） */
  snapshot() {
    return [...this.buffer.entries()].map(([scene, b]) => ({ scene, ...b }))
  }
}

/** 单例：整个应用只有一个采集器 */
export const senseTracker = new SenseTracker()
export { ROUTE_SCENE, sceneOfPath }
