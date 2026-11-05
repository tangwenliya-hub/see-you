/**
 * useChart —— ECharts 实例统一管理（本项目所有图表实例的统一入口）
 *
 * 解决四类问题：
 * 1. 生命周期：init / setOption / resize / dispose 分散在各页面手写，容易漏 dispose 造成内存泄漏
 *    （典型：个人中心 tab 用 v-if 切换时 DOM 被销毁，旧实例仍挂在已移除的节点上）
 * 2. resize 监听重复：每个页面各自 window.addEventListener('resize')，N 个页面 N 个监听器
 *    → 改为全局单例监听 + requestAnimationFrame 合并，registry 为空时自动解绑
 * 3. 大数据量渲染：统一按数据量开启 sampling / large / progressive，避免 1 万点卡顿
 * 4. 【移动端适配】响应式 option：断点变化时用 factory 重新生成完整 option 并重绘，
 *    同时用 ResizeObserver 观察容器本身（比 window.resize 更准，能捕获 flex/侧栏折叠导致的尺寸变化）
 *
 * ⚠️ 重要教训（ECharts 6）：
 *    二次 setOption 用 notMerge:true 却只传部分字段时，series.type 会丢失导致渲染崩溃。
 *    因此 **响应式 factory 必须每次返回完整 option**，本模块在此基础上才敢用 notMerge。
 */
import * as echarts from '../lib/echarts.js'
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'

/* ============ 全局注册表 ============ */
const registry = new Set()
export const chartRegistry = registry

/** 大数据量阈值：超过该点数才启用优化，小图表仍保留动画 */
const LARGE_THRESHOLD = 2000

/* ==================================================================
 * ============ 断点工具（移动端适配的基础） =========================
 * ================================================================== */

/** 断点：xs 超小屏 / sm 手机 / md 平板 / lg 桌面 */
export const BREAKPOINTS = { XS: 480, SM: 768, MD: 1024 }

export function breakpointOf(width) {
  if (width < BREAKPOINTS.XS) return 'xs'
  if (width < BREAKPOINTS.SM) return 'sm'
  if (width < BREAKPOINTS.MD) return 'md'
  return 'lg'
}

/** 当前视口断点 */
export function getBreakpoint() {
  if (typeof window === 'undefined') return 'lg'
  return breakpointOf(window.innerWidth)
}

/** 是否窄屏（手机） */
export function isNarrow() {
  const bp = getBreakpoint()
  return bp === 'xs' || bp === 'sm'
}

/**
 * 深拷贝 option —— **必须保留函数**。
 *
 * 旧实现是 `JSON.parse(JSON.stringify(option))`，看起来省事，但 JSON 序列化会
 * 把所有 `formatter` 函数变成 undefined：tooltip 格式化、label 回调、axisLabel
 * 回调全部失效。它一直没炸，只是因为 applyMobileOption 此前从未被调用；
 * 一旦按移动端适配把它接上，全站 tooltip 会同时变成空白——这也是这里必须
 * 手写递归拷贝的原因（注释里那句「option 内只允许纯数据」是错的，option 里
 * 到处都是函数）。
 */
export function deepClone(v) {
  if (v === null || typeof v !== 'object') return v
  if (typeof v === 'function') return v
  if (Array.isArray(v)) return v.map(deepClone)
  if (v instanceof Date) return new Date(v.getTime())
  const out = {}
  for (const k of Object.keys(v)) out[k] = deepClone(v[k])
  return out
}

/** series 的 data 是否「实质为空」：空数组 / 全 null / 未定义 */
function seriesEmpty(s) {
  const d = s?.data
  if (!Array.isArray(d) || d.length === 0) return true
  return d.every((it) => it == null)
}

/**
 * 判断一份 option 是否没有任何可画的数据（用于空态提示，避免"白图表"）。
 * 只在 series 全部为空时才算空——饼图/桑基等数据结构不同，但都逃不出 data。
 */
export function isEmptyOption(option) {
  const series = option?.series
  if (!series) return false
  const arr = Array.isArray(series) ? series : [series]
  if (!arr.length) return false
  return arr.every(seriesEmpty)
}

/**
 * 把一份桌面版 option 加工成适合当前断点的版本（纯函数，便于单测）。
 *
 * 手机上常见的三类「图表溢出」：
 *   ① 轴标签太长把画布顶出去     → grid.containLabel + axisLabel 缩短/旋转
 *   ② tooltip 跟随鼠标超出屏幕   → tooltip.confine
 *   ③ 图例占满两行把标题挤没了   → 移动端图例高度压缩、字号下调
 */
export function applyMobileOption(option, bp = getBreakpoint()) {
  if (!option || bp === 'lg') return option
  const out = deepClone(option)

  if (bp === 'md') {
    if (out.tooltip) out.tooltip.confine = true
    applyGrid(out, { left: 8, right: 14, top: 34, bottom: 28, containLabel: true }, 12)
    return out
  }

  // ===== xs / sm：手机 =====
  const tiny = bp === 'xs'
  const fs = tiny ? 10 : 11

  if (out.tooltip) {
    out.tooltip.confine = true
    out.tooltip.textStyle = { ...(out.tooltip.textStyle || {}), fontSize: fs }
    // 手机上 hover 不可靠，触发轴_pointer 容易挡手，保持默认即可，不额外配置
  }
  if (out.legend) {
    const arr = Array.isArray(out.legend) ? out.legend : [out.legend]
    arr.forEach((lg) => {
      lg.itemWidth = tiny ? 10 : 12
      lg.itemHeight = tiny ? 8 : 10
      lg.textStyle = { ...(lg.textStyle || {}), fontSize: fs }
      if (lg.orient !== 'vertical') { lg.top = 0; lg.left = 'center' }
    })
  }
  if (out.title) {
    const arr = Array.isArray(out.title) ? out.title : [out.title]
    arr.forEach((t) => { t.textStyle = { ...(t.textStyle || {}), fontSize: tiny ? 12 : 13 } })
  }

  applyGrid(out, {
    left: tiny ? 4 : 8,
    right: tiny ? 10 : 14,
    top: out.legend ? 26 : 18,
    bottom: tiny ? 24 : 30,
    containLabel: true,
  }, fs)

  // X 轴标签：手机上要么旋转要么隔一个显示
  const axes = [...(Array.isArray(out.xAxis) ? out.xAxis : out.xAxis ? [out.xAxis] : []),
                ...(Array.isArray(out.yAxis) ? out.yAxis : out.yAxis ? [out.yAxis] : [])]
  axes.forEach((ax) => {
    if (ax.show === false) return
    ax.axisLabel = { ...(ax.axisLabel || {}), fontSize: fs, hideOverlap: true }
    if (ax.type === 'category' && ax.axisLabel && !ax.axisLabel.rotate && !ax.axisLabel.interval) {
      ax.axisLabel.rotate = 45
    }
    if (ax.name) {
      ax.nameTextStyle = { ...(ax.nameTextStyle || {}), fontSize: fs }
      ax.nameGap = tiny ? 6 : 8
    }
  })

  /*
   * 日历热力图：一年 53 列固定 cellSize['auto',18] 在窄屏会横向溢出。
   * 这里按可视宽度反推能放下的列宽，再把格子压扁。
   */
  const cals = Array.isArray(out.calendar) ? out.calendar : out.calendar ? [out.calendar] : []
  cals.forEach((cal) => {
    cal.left = tiny ? 24 : 30
    cal.right = tiny ? 6 : 10
    cal.top = cal.top ?? 60
    cal.cellSize = ['auto', tiny ? 11 : 13]
    cal.splitLine = { show: false, ...(cal.splitLine || {}) }
    cal.itemStyle = { ...(cal.itemStyle || {}), borderWidth: 1 }
    cal.dayLabel = { ...(cal.dayLabel || {}), fontSize: tiny ? 8 : 9 }
    cal.monthLabel = { ...(cal.monthLabel || {}), fontSize: tiny ? 9 : 10 }
    cal.yearLabel = { ...(cal.yearLabel || {}), fontSize: tiny ? 10 : 11 }
  })

  // visualMap 连续条在手机上会占掉半屏，改成横排短条
  const vms = Array.isArray(out.visualMap) ? out.visualMap : out.visualMap ? [out.visualMap] : []
  vms.forEach((vm) => {
    if (vm.type !== 'continuous') return
    Object.assign(vm, {
      orient: 'horizontal', left: 'center', bottom: 0,
      itemWidth: tiny ? 8 : 10, itemHeight: tiny ? 90 : 110,
      textStyle: { ...(vm.textStyle || {}), fontSize: fs },
    })
  })

  return out
}

function applyGrid(option, g, fontSize) {
  const grids = Array.isArray(option.grid) ? option.grid : option.grid ? [option.grid] : null
  if (grids) {
    grids.forEach((it) => {
      it.left = it.left ?? g.left
      it.right = it.right ?? g.right
      it.top = it.top ?? g.top
      it.bottom = it.bottom ?? g.bottom
      if (it.containLabel === undefined) it.containLabel = true
    })
    return
  }
  // 没有显式 grid 时补一个默认的（饼图等无坐标轴的图表不受影响）
  if (option.series && option.series.some?.((s) => ['line', 'bar', 'scatter'].includes(s.type))) {
    option.grid = { ...g }
  }
  void fontSize
}

/* ============ 全局 resize（单例 + rAF 合并） ============ */
let bound = false
let rafId = 0
/** 上一次的断点，变化时触发所有 factory 重算 */
let lastBp = null

/** 实例是否仍然有效：未 dispose 且容器还在文档里（v-if 移除 DOM 后即为失效） */
const isAlive = (c) => {
  try { return !c.isDisposed() && !!c.getDom() && c.getDom().isConnected } catch { return false }
}

/**
 * 清扫僵尸实例：容器被 v-if 移除后，实例仍挂在注册表里就是内存泄漏。
 * 在每次 init 前 + 每次 resize 后各跑一次，保证注册表大小≈真实存活图表数。
 */
const prune = () => {
  let n = 0
  for (const c of [...registry]) {
    if (isAlive(c)) continue
    registry.delete(c)
    try { c.dispose() } catch { /* DOM 已移除 */ }
    n++
  }
  return n
}

const flushResize = () => {
  rafId = 0
  prune()
  const bp = getBreakpoint()
  const changed = bp !== lastBp
  lastBp = bp
  for (const c of [...registry]) {
    try {
      // 断点跨越（PC ↔ 手机）时，用 factory 重新生成完整 option，再 resize
      if (changed && typeof c.__seeFactory === 'function') {
        c.setOption(normalizeLegend(withPerf(c.__seeFactory(bp))), { notMerge: true })
      } else if (changed && c.__seeRaw) {
        /*
         * 没有 factory 的图表（项目里占大多数）：用记住的原始 option 重新套一遍
         * 断点加工。这里用 merge（notMerge:false）而不是整份覆盖，
         * 因为 __seeRaw 可能只是最近一次局部更新（如只改了 series），
         * 整份覆盖会把更早写入的坐标轴/图例擦掉。
         */
        c.setOption(applyMobileOption(c.__seeRaw, bp))
      }
      c.resize()
    } catch { /* 单个实例异常不影响其它 */ }
  }
  maybeUnbindResize()
}
const scheduleResize = () => {
  if (rafId) return
  rafId = requestAnimationFrame(flushResize)
}
const onWindowResize = () => scheduleResize()

const bindResize = () => {
  if (bound || typeof window === 'undefined') return
  bound = true
  lastBp = getBreakpoint()
  window.addEventListener('resize', onWindowResize, { passive: true })
  // 手机横竖屏切换：iOS Safari 上 resize 有时不触发，必须单独监听
  window.addEventListener('orientationchange', () => setTimeout(scheduleResize, 260))
}
const maybeUnbindResize = () => {
  if (!bound || registry.size > 0 || typeof window === 'undefined') return
  bound = false
  window.removeEventListener('resize', onWindowResize)
  if (rafId) { cancelAnimationFrame(rafId); rafId = 0 }
}

/* ============ 组合式函数 ============ */
/**
 * @param {import('vue').Ref<HTMLElement|null>} elRef 模板 ref（容器 div）
 * @param {string|{name?:string, lazy?:boolean}} name 实例名，或 { name, lazy }
 * @param {{lazy?:boolean}} options lazy=true 时容器进入视口才真正 init（首屏图表多时省掉大量渲染）
 *
 * 三个容易踩的点，都在这里收口：
 * 1. **断点适配**：set() 会自动套 applyMobileOption（窄屏才生效），并记住原始 option，
 *    断点跨越时重算。页面只管写桌面版 option，不用关心手机。
 * 2. **空态**：series 全空时给容器打 data-empty 属性，由 CSS 显示「暂无记录」，
 *    避免"白图表"——用户无法区分「没有数据」「加载中」「加载失败」。
 * 3. **懒初始化**：lazy 图表在进入视口前不 init，set() 的调用排队，进入视口后回放。
 */
export function useChart(elRef, name = 'chart', options = {}) {
  const opt = typeof name === 'object' && name !== null ? name : options
  const chartName = typeof name === 'string' ? name : (opt.name || 'chart')
  const lazy = !!opt.lazy

  let inst = null
  let ro = null
  let io = null
  /** 懒加载下尚未执行的 set/setResponsive（只保留最后一次，等同最新状态） */
  let pending = null
  /** 容器是否已进入过视口（非 lazy 视为一直可见） */
  let seen = !lazy

  const dispose = () => {
    if (ro) { try { ro.disconnect() } catch {} ro = null }
    if (io) { try { io.disconnect() } catch {} io = null }
    pending = null
    if (!inst) return
    try { if (!inst.isDisposed()) inst.dispose() } catch { /* 忽略：DOM 已被移除 */ }
    registry.delete(inst)
    inst = null
    maybeUnbindResize()
  }

  /** 容器首次进入视口时解锁，并回放排队的 set */
  const watchVisibility = () => {
    if (seen || io) return
    const el = elRef && elRef.value
    if (!el) return
    if (typeof IntersectionObserver === 'undefined') { seen = true; return }
    try {
      io = new IntersectionObserver((entries) => {
        if (!entries.some((e) => e.isIntersecting)) return
        seen = true
        try { io.disconnect() } catch {}
        io = null
        const fn = pending
        pending = null
        if (fn) fn()
      }, { rootMargin: '240px' })   // 提前 240px 就开始渲染，滚动时不会看到空白
      io.observe(el)
    } catch { seen = true }
  }

  /*
   * 懒加载的兜底：set() 可能在容器 ref 还没就绪（v-if 未渲染）时被调用，
   * 这时 watchVisibility 观察不到任何元素，图表就会永远不渲染。
   * 因此挂载后再试一次，并监听 ref 从 null 变有值（tab 切换 / v-if 打开）。
   */
  if (lazy) {
    onMounted(() => nextTick(() => { if (!seen) watchVisibility() }))
    watch(elRef, (el) => { if (el && !seen) watchVisibility() })
  }

  /** 空态标记：交给 CSS 显示提示，不改 option（改 option 会与 merge 语义打架） */
  const markEmpty = (isEmpty) => {
    const el = elRef && elRef.value
    if (!el) return
    if (isEmpty) el.setAttribute('data-empty', '暂无记录')
    else el.removeAttribute('data-empty')
  }

  /** 拿到可用实例：容器就绪才 init，DOM 被 v-if 重建/移除过则自动重建实例 */
  const ensure = (theme, initOpts) => {
    const el = elRef && elRef.value
    if (!el) return null
    if (!seen) { watchVisibility(); return null }
    if (inst) {
      if (isAlive(inst) && inst.getDom() === el) return inst
      dispose() // 容器节点已换新或已被移除（tab 切换），旧实例作废
    }
    if (inst) { registry.delete(inst); inst = null }
    prune() // 顺手清掉其它 tab 留下的僵尸实例
    inst = echarts.init(el, theme, { renderer: 'canvas', ...initOpts })
    inst.__seeName = chartName
    registry.add(inst)
    bindResize()

    // ResizeObserver：容器自身尺寸变化（侧栏折叠、flex 重排、移动端 vw 高度变化）
    // 比 window.resize 更准，且不需要跨 250ms 的 CSS 过渡
    try {
      if (typeof ResizeObserver !== 'undefined') {
        ro = new ResizeObserver(() => scheduleResize())
        ro.observe(el)
      }
    } catch { /* 老浏览器忽略 */ }
    return inst
  }

  /**
   * 设置 option（默认 merge，行为与 echarts.setOption 一致，兼容已有的局部更新写法）
   * @param {object} option
   * @param {{notMerge?:boolean, lazyUpdate?:boolean, perf?:boolean, theme?:string, raw?:boolean}} opts
   *        raw=true 表示 option 已经是按断点算好的，不再二次加工
   */
  const set = (option, opts = {}) => {
    if (!seen) {
      // 未进入视口：排队，等可见后一次性执行（只保留最新，避免回放一长串历史状态）
      pending = () => set(option, opts)
      watchVisibility()
      return null
    }
    const c = ensure(opts.theme)
    if (!c) return null
    c.__seeRaw = option
    markEmpty(isEmptyOption(option))
    const finalOpt = opts.raw ? option : applyMobileOption(option)
    c.setOption(opts.perf === false ? normalizeLegend(finalOpt) : normalizeLegend(withPerf(finalOpt)), {
      notMerge: !!opts.notMerge,
      lazyUpdate: !!opts.lazyUpdate,
    })
    return c
  }

  /**
   * 【响应式】传入「按断点生成 option」的工厂函数。
   *
   * 用法：
   *   chart.setResponsive((bp) => buildOption(bp))
   *
   * 与 set() 的区别：
   *  · set() 只写一次，窗口大小变化时 ECharts 自己按画布缩放（字号会跟着变小，但布局不变）；
   *  · setResponsive() 在断点跨越时**重新生成完整 option**（例如手机上把图例挪到底部、
   *    隐藏次要坐标轴），这是移动端「图表不溢出」的关键。
   *
   * ⚠️ factory 必须返回完整 option（包含 series.type），否则 ECharts 6 下会崩溃。
   */
  const setResponsive = (factory, opts = {}) => {
    if (!seen) {
      pending = () => setResponsive(factory, opts)
      watchVisibility()
      return null
    }
    const bp = getBreakpoint()
    lastBp = bp
    const c = ensure(opts.theme)
    if (!c) return null
    c.__seeFactory = factory
    const full = factory(bp)
    markEmpty(isEmptyOption(full))
    c.setOption(opts.perf === false ? normalizeLegend(full) : normalizeLegend(withPerf(full)), { notMerge: true, lazyUpdate: !!opts.lazyUpdate })
    // 首帧立即贴合容器尺寸（容器可能刚由 v-if 渲染出来）
    try { c.resize() } catch {}
    return c
  }

  const resize = () => { try { if (inst && !inst.isDisposed()) inst.resize() } catch {} }
  const getInstance = () => inst
  /** 有些地方需要原生实例（如 dispatchAction / on 事件） */
  const on = (...args) => { const c = ensure(); if (c) c.on(...args); return c }
  const off = (...args) => { if (inst && !inst.isDisposed()) inst.off(...args) }

  // 组件卸载自动释放（页面无需再手写 dispose）
  onBeforeUnmount(dispose)

  return {
    /** 当前实例（可能为 null，容器未渲染时） */
    get instance() { return inst },
    getInstance,
    ensure,
    set,
    setOption: set, // 兼容旧写法
    setResponsive,
    resize,
    dispose,
    on,
    off,
  }
}

/**
 * ECharts 6 行为变更兜底：图例默认位置从顶部改成了底部
 * （LegendModel.defaultOption 里是 bottom: tokens.size.m），
 * 裸配图例会沉底压住 x 轴标签。这里统一恢复 ECharts 5 语义：
 * 没显式写 top/bottom 的图例归位到顶部（左中、水平排布不变）。
 * 已写 bottom: 0 的（DataBoard 等，grid 预留过空间）不受影响。
 */
function normalizeLegend(option) {
  if (!option || option.legend === undefined) return option
  const fix = (lg) =>
    lg && lg.top === undefined && lg.bottom === undefined ? { ...lg, top: 0 } : lg
  if (Array.isArray(option.legend)) {
    return { ...option, legend: option.legend.map(fix) }
  }
  const lg = fix(option.legend)
  return lg === option.legend ? option : { ...option, legend: lg }
}

/** 按数据量给 series 套上性能优化配置（纯函数，便于单测） */
export function withPerf(option) {
  if (!option || !Array.isArray(option.series)) return option
  let changed = false
  const series = option.series.map((s) => {
    const n = Array.isArray(s?.data) ? s.data.length : 0
    if (!s || n < LARGE_THRESHOLD) return s
    changed = true
    const perf = {
      animation: false,          // 大数据量下动画是主要开销
      progressive: 400,          // 渐进渲染：每帧渲染 400 个
      progressiveThreshold: 1200,
    }
    if (s.type === 'line') perf.sampling = 'lttb'  // 降采样，视觉几乎无损
    if (s.type === 'scatter') perf.large = true
    if (s.type === 'bar') perf.large = true
    return { ...s, ...perf }
  })
  return changed ? { ...option, series } : option
}

/* ============ 开发期排查助手 ============ */
if (typeof window !== 'undefined') {
  // 控制台执行 __seeCharts() 可查看存活实例，验证切换 tab 后无泄漏
  window.__seeCharts = () => {
    const alive = [...registry].filter(isAlive)
    return {
      存活实例数: alive.length,
      // 注册表里未被回收的条目数（应≈存活数，差值即僵尸实例）
      注册表条目数: registry.size,
      断点: getBreakpoint(),
      响应式实例数: alive.filter((c) => typeof c.__seeFactory === 'function').length,
      名称: alive.map((c) => c.__seeName),
    }
  }
}
