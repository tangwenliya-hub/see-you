/**
 * backNav —— 全站统一「返回」行为（2026-11-18）
 *
 * 背景：内容页（测评详情、FM 播放器、学堂播放器、某天的日记、反馈对话…）
 * 既可能从列表点进来，也可能是从通知 / 分享链接直接打开的。两种进入方式，
 * 「返回」的含义必须一致：回到这条内容所属的列表，而不是首页，更不能直接退出 APP。
 *
 * 规则（按优先级）：
 *   1. 页面内有展开的详情/弹层 → 先收起它，原地回到列表（路由不动、滚动位置不动）；
 *   2. 有站内历史 → router.back()；
 *   3. 没有站内历史（深链直接进站）→ 去 meta.listPath 指定的列表页。
 *
 * 浏览器/系统返回键由 router/index.js 的「历史垫底」处理（进站时先把列表页塞到历史里）。
 */
import { onBeforeUnmount, watch } from 'vue'

/** 详情级关闭器栈：后注册的先关（弹层套弹层时按打开顺序倒着收） */
const closers = []

export function registerBackCloser(fn) {
  if (typeof fn !== 'function') return () => {}
  closers.push(fn)
  return () => {
    const i = closers.indexOf(fn)
    if (i > -1) closers.splice(i, 1)
  }
}

export const hasOpenDetail = () => closers.length > 0

/** 收起最上层详情；返回是否真的收了东西 */
export function closeTopDetail() {
  const fn = closers.pop()
  if (!fn) return false
  try { fn() } catch (e) { /* 关闭失败就当这层不存在，继续往下走 */ }
  return true
}

/**
 * 这条内容属于哪个列表页：
 *   路由 meta.listPath > 面包屑的上一级 > 首页
 */
export function listFallback(route) {
  if (route?.meta?.listPath) return route.meta.listPath
  const crumbs = route?.meta?.crumbs
  if (Array.isArray(crumbs) && crumbs.length > 1) {
    const up = crumbs[crumbs.length - 2]?.to
    if (up) return up
  }
  return '/main'
}

/**
 * 统一的返回动作。
 * @param {import('vue-router').Router} router
 * @param {import('vue-router').RouteLocationNormalized} route
 * @param {{ skipDetail?: boolean }} opts skipDetail=true 时不再尝试收详情（调用方已经处理过）
 * @returns {'detail'|'history'|'list'}
 */
export function goBack(router, route, { skipDetail = false } = {}) {
  if (!skipDetail && closeTopDetail()) return 'detail'

  // 站内历史可用：正常后退（保持列表的滚动位置与筛选状态）
  if (window.history.state?.back) {
    router.back()
    return 'history'
  }

  // 深链直接进站：没有可回退的站内历史 → 回它所在的列表
  const target = listFallback(route)
  if (target && target !== route.path) router.push(target)
  else router.push('/main')
  return 'list'
}

/**
 * 页面用：详情/播放器打开时登记关闭器，返回时优先收起它。
 * @param {import('vue').Ref<boolean>|(() => boolean)} isOpen
 * @param {() => void} close
 */
export function useBackCloser(isOpen, close) {
  let off = null
  const read = () => (typeof isOpen === 'function' ? isOpen() : isOpen.value)
  watch(read, (open) => {
    if (open) { if (!off) off = registerBackCloser(close) }
    else if (off) { off(); off = null }
  }, { immediate: true })
  onBeforeUnmount(() => { if (off) { off(); off = null } })
}
