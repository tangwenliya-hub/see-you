/**
 * useSwipeBack —— 手机端「从左边缘向右滑返回」
 *
 * 交互约定（用户拍板）：
 *   - 手指从屏幕左边缘（≤24px）向右滑，当前页面跟着手指移动；
 *   - 松手时滑过屏幕一半 → 返回上一页；
 *   - 不到一半 → 带缓动弹回原位；
 *   - 竖直滑动 / 向左划不接管，交还给页面自己滚。
 *
 * 适用范围：AppLayout 下的所有二级页面（首页 /main、欢迎页不接管）；
 *          全屏播放器、抽屉、弹窗打开时不接管，避免和它们抢手势。
 *
 * 实现要点：
 *   - 只对容器做 transform，不改页面自身 DOM；
 *   - touchmove 必须 passive:false 才能 preventDefault，压住浏览器原生横滑；
 *   - 动画结束后立刻清空 inline transform —— 常驻 transform（含 will-change）
 *     会给 position:fixed 子元素造包含块，聊天输入框/播放器这类 fixed 元素会错位。
 */
import { onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeTopDetail, goBack } from '@/utils/backNav'

const HOME_PATHS = ['/', '/home', '/main']
const EDGE = 24        // 触发区：屏幕左边缘 24px
const MIN_MOVE = 8     // 判定方向前的最小位移
const MAX_WIDTH = 768  // 只在手机端生效

export function useSwipeBack(wrapRef) {
  const router = useRouter()
  const route = useRoute()

  let startX = 0
  let startY = 0
  let lastDx = 0
  let phase = 'idle'      // idle | pending（按下未定向）| dragging（接管中）
  let timer = null

  const node = () => wrapRef.value

  /** 是否真的看得见（Element Plus 的 .el-overlay 常常是常驻 DOM + display:none，
   *  直接 querySelector 会误判成"弹窗开着"，手势就永远不接管） */
  function hasVisible(selector) {
    return [...document.querySelectorAll(selector)].some((el) => {
      const cs = getComputedStyle(el)
      if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity) === 0) return false
      return el.getClientRects().length > 0
    })
  }

  function enabled() {
    if (window.innerWidth > MAX_WIDTH) return false
    if (HOME_PATHS.includes(route.path)) return false
    // 全屏播放器 / 抽屉 / 弹窗：不抢手势
    if (document.querySelector('.fm-page .player-view')) return false
    if (document.querySelector('.m-drawer.open')) return false
    if (document.querySelector('.quiz-screen')) return false
    if (hasVisible('.el-overlay, .share-mask, .picker-layer-active')) return false
    return true
  }

  function setX(px, animated) {
    const el = node()
    if (!el) return
    el.style.transition = animated ? 'transform .24s cubic-bezier(.22,.9,.24,1)' : 'none'
    el.style.transform = `translate3d(${px}px, 0, 0)`
  }

  function clear() {
    const el = node()
    if (!el) return
    el.classList.remove('is-swiping')
    el.style.transition = ''
    el.style.transform = ''
  }

  function onStart(e) {
    if (e.touches.length !== 1) return
    if (!enabled()) return
    const t = e.touches[0]
    if (t.clientX > EDGE) return
    const target = e.target
    // 表单控件、横向滚动的标签条不接管
    if (target?.closest?.('input, textarea, select, [contenteditable="true"], .cat-tabs')) return
    startX = t.clientX
    startY = t.clientY
    lastDx = 0
    phase = 'pending'
  }

  function onMove(e) {
    if (phase === 'idle') return
    const t = e.touches[0]
    const mx = t.clientX - startX
    const my = t.clientY - startY

    if (phase === 'pending') {
      if (Math.abs(mx) < MIN_MOVE && Math.abs(my) < MIN_MOVE) return
      // 向左划或竖直滑动占比更大：放弃接管，让页面正常滚动
      if (mx <= 0 || Math.abs(my) > Math.abs(mx)) { phase = 'idle'; return }
      phase = 'dragging'
      node()?.classList.add('is-swiping')
    }
    if (phase !== 'dragging') return
    if (e.cancelable) e.preventDefault()
    lastDx = Math.max(0, Math.min(mx, window.innerWidth))
    setX(lastDx, false)
  }

  function finish() {
    if (phase !== 'dragging') { phase = 'idle'; return }
    phase = 'idle'
    const w = window.innerWidth
    const passed = lastDx > w * 0.5
    if (passed) {
      // 页面里还有展开的详情（底部弹层/量表详情等）：这一划等于「收起详情」，
      // 原地回到它所在的列表，不离开当前路由
      if (closeTopDetail()) {
        setX(0, true)
        timer = setTimeout(clear, 260)
        lastDx = 0
        return
      }
      // 滑过一半：整页顺势推出屏幕，然后返回
      // goBack 内部：有站内历史就后退；深链直接进站的就回到该内容所属的列表页（不是首页）
      setX(w, true)
      timer = setTimeout(() => {
        clear()
        goBack(router, route, { skipDetail: true })
      }, 230)
    } else {
      // 不到一半：弹回原位
      setX(0, true)
      timer = setTimeout(clear, 260)
    }
    lastDx = 0
  }

  function onCancel() {
    if (phase === 'dragging') setX(0, true), (timer = setTimeout(clear, 260))
    phase = 'idle'
  }

  onMounted(() => {
    document.addEventListener('touchstart', onStart, { passive: true })
    // passive:false —— 需要 preventDefault 压住浏览器原生的横滑行为
    document.addEventListener('touchmove', onMove, { passive: false })
    document.addEventListener('touchend', finish, { passive: true })
    document.addEventListener('touchcancel', onCancel, { passive: true })
  })

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
    document.removeEventListener('touchstart', onStart)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', finish)
    document.removeEventListener('touchcancel', onCancel)
  })
}
