/**
 * 后台图表主题工具
 *
 * 后台新增的 7 个管理页都要画 ECharts，而颜色必须跟随管理员设置的主题色 /
 * 深色模式（CSS 变量 --c-hex / --c-text / --c-border）。每个页面各写一份取色逻辑
 * 既重复又容易漏改，这里统一收口。
 *
 * 移动端：图表高度一律用 clamp()，窄屏下自动压扁，配合 useChart 的响应式 factory
 * 就能做到「一套代码兼容 PC + 手机」，不需要在每个页面手写 media query。
 */

export const cssVar = (name, fallback) => {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name)
    return (v || '').trim() || fallback
  } catch {
    return fallback
  }
}

export const accent = () => cssVar('--c-hex', '#7bc47b')
export const accentSoft = () => cssVar('--c-main', '#90db90')
export const textColor = () => cssVar('--c-text', '#556a55')
export const weakColor = () => cssVar('--c-text-weak', '#8a9a8a')
export const borderColor = () => cssVar('--c-border', 'rgba(184,212,170,0.15)')

/** #rrggbb + 透明度 → rgba()，用于 areaStyle 这类需要半透明填充的地方 */
export const hexA = (hex, a) => {
  const h = (hex || '').replace('#', '')
  if (h.length !== 6) return `rgba(123,196,123,${a})`
  const n = parseInt(h, 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
}

/** 预警三级配色：黄 / 橙 / 红（与前台预警卡片保持一致） */
export const LEVEL_COLORS = ['#fbbf24', '#fb923c', '#ef4444']
export const LEVEL_NAMES = ['', '轻度关注', '中度提醒', '高度关注']

/** 九类情绪的统一配色（后台词典/推测分布复用，避免每页各写一套） */
export const EMOTION_COLORS = [
  '#f9ca24', '#a0c4ff', '#bdb2ff', '#748ffc', '#ff8787', '#ffa8a8', '#ffd43b', '#8ce99a', '#adb5bd',
]

/** 通用坐标轴样式（配色跟随主题） */
export const axis = () => ({
  axisLine: { lineStyle: { color: borderColor() } },
  axisLabel: { color: textColor() },
})
export const valueAxis = (extra = {}) => ({
  type: 'value',
  splitLine: { lineStyle: { color: borderColor() } },
  axisLabel: { color: weakColor() },
  ...extra,
})
export const categoryAxis = (data) => ({
  type: 'category',
  data: data || [],
  ...axis(),
})
