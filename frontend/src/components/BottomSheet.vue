<template>
  <!--
    通用底部弹层（移动端贴底 + 下滑关闭；桌面端自动变居中卡片）。

    交互约定（全站临时小操作统一走这里）：
    · 点遮罩关闭；手机上按住把手/标题往下滑，超过阈值或快速甩动即关闭，否则弹回原位
    · 关闭只是收起覆盖层，页面路由与滚动位置都不动 —— 「停回原页面原位置」
    · 打开期间锁定 body 滚动（保留滚动位置），关闭后自动恢复

    用法：
      <BottomSheet :open="showPicker" title="选日期" @close="showPicker = false">
        ...任意内容...
      </BottomSheet>
  -->
  <Teleport to="body">
    <div v-if="open" class="bs-root" @touchmove="onMaskTouchMove">
      <div class="bs-mask" :style="{ opacity: maskOpacity }" @click="requestClose('mask')"></div>

      <div
        ref="panelRef"
        :class="['bs-panel', { dragging }]"
        :style="panelStyle"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <!-- 把手 + 标题：整个头部区域都可按住下滑 -->
        <div class="bs-grab" @touchstart.passive="onDragStart" @touchmove.passive="onDragMove" @touchend="onDragEnd" @mousedown="onMouseDragStart">
          <span class="bs-handle"></span>
          <div class="bs-head">
            <h3 class="bs-title">{{ title }}</h3>
            <button class="bs-close" aria-label="关闭" @click="requestClose('btn')"><AppIcon name="close" /></button>
          </div>
        </div>

        <div ref="bodyRef" class="bs-body" @touchstart.passive="onBodyTouchStart" @touchmove.passive="onBodyTouchMove" @touchend="onBodyTouchEnd">
          <slot />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
})
const emit = defineEmits(['close'])

const panelRef = ref(null)
const bodyRef = ref(null)
const dy = ref(0)           // 当前面板下移像素
const dragging = ref(false) // 是否正被手指拖着走

const CLOSE_THRESHOLD = 96   // 松手关闭的位移阈值
const VELOCITY_THRESHOLD = 0.6 // 快速甩动关闭（px/ms）

/* ===== 打开/关闭：锁 body 滚动（只锁 overflow，不动 top，滚动位置天然保留） ===== */
let lockCount = 0
function lockScroll() {
  if (lockCount++ === 0) document.body.style.overflow = 'hidden'
}
function unlockScroll() {
  if (lockCount > 0 && --lockCount === 0) document.body.style.overflow = ''
}
watch(() => props.open, (v) => {
  dy.value = 0
  dragging.value = false
  if (v) {
    lockScroll()
    document.addEventListener('keydown', onKeydown)
    nextTick(() => panelRef.value?.scrollIntoView?.({ block: 'end' }))
  } else {
    unlockScroll()
    document.removeEventListener('keydown', onKeydown)
  }
})
onBeforeUnmount(() => {
  unlockScroll()
  document.removeEventListener('keydown', onKeydown)
})
const onKeydown = (e) => { if (e.key === 'Escape') requestClose('esc') }

const requestClose = (from) => emit('close', from)

/* ===== 滑动状态机 ===== */
let startY = 0
let startT = 0
let lastY = 0
let lastT = 0
let tracking = false   // 本次触摸是否已判定为「下拉关闭」手势
let dragEnabled = false // 是否允许拖拽（把手处恒允许；内容区仅当已滚到顶）

function beginTrack(y) {
  startY = lastY = y
  startT = lastT = performance.now()
  tracking = false
}
function moveTrack(y, allowStart) {
  const total = y - startY
  // 还没判定手势：向下拉超过 8px 且允许触发时，锁定为下拉手势（避免和点击/上滑内容冲突）
  if (!tracking && allowStart && total > 8) tracking = true
  if (!tracking) return
  // 只跟手向下，向上拖给一点阻尼回弹感
  dy.value = total > 0 ? total : Math.max(total * 0.18, -24)
  dragging.value = dy.value > 0
  lastY = y
  lastT = performance.now()
}
function endTrack() {
  if (!tracking) { dragging.value = false; return }
  const dt = Math.max(lastT - startT, 1)
  const v = (lastY - startY) / dt
  const shouldClose = dy.value >= CLOSE_THRESHOLD || (v > VELOCITY_THRESHOLD && dy.value > 24)
  tracking = false
  dragging.value = false
  if (shouldClose) { dy.value = 0; requestClose('swipe') }
  else { dy.value = 0 } // 弹回原位（dy 归零，CSS transition 自动回弹）
}

/* 把手/标题区：恒可拖拽 */
const onDragStart = (e) => { dragEnabled = true; beginTrack(e.touches?.[0]?.clientY ?? e.clientY) }
const onDragMove = (e) => { if (!dragEnabled) return; moveTrack(e.touches?.[0]?.clientY ?? e.clientY, true) }
const onDragEnd = () => { dragEnabled = false; endTrack() }

/* 内容区：仅当内容已滚到最顶且继续下拉时才触发关闭 */
const onBodyTouchStart = (e) => {
  const el = bodyRef.value
  dragEnabled = !!el && el.scrollTop <= 0
  beginTrack(e.touches?.[0]?.clientY ?? 0)
}
const onBodyTouchMove = (e) => { if (!dragEnabled) return; moveTrack(e.touches?.[0]?.clientY ?? 0, true) }
const onBodyTouchEnd = () => { dragEnabled = false; endTrack() }

/* 桌面鼠标拖把手（可选体验，逻辑同触摸） */
let mouseDown = false
const onMouseDragStart = (e) => {
  if (e.button !== 0) return
  mouseDown = true; dragEnabled = true; beginTrack(e.clientY)
  const mv = (ev) => moveTrack(ev.clientY, true)
  const up = () => {
    mouseDown = false; dragEnabled = false; endTrack()
    window.removeEventListener('mousemove', mv); window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', mv); window.addEventListener('mouseup', up)
}

/* 遮罩滚动穿透兜底 */
const onMaskTouchMove = (e) => e.preventDefault()

/* ===== 视觉状态 ===== */
const panelStyle = computed(() => {
  if (dy.value > 0) return { transform: `translateY(${dy.value}px)`, transition: 'none' }
  if (dy.value < 0) return { transform: `translateY(${dy.value}px)` }
  return {}
})
const maskOpacity = computed(() => (dragging.value ? Math.max(0.28, 0.5 - dy.value / 400) : null))
</script>

<style scoped>
.bs-root { position: fixed; inset: 0; z-index: 3000; }
.bs-mask {
  position: absolute; inset: 0;
  background: rgba(30, 22, 34, 0.5);
  backdrop-filter: blur(2px);
  transition: opacity 0.25s;
}

/* ===== 移动端：贴底弹层 ===== */
.bs-panel {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  max-height: 82vh;
  display: flex;
  flex-direction: column;
  background: #fffdfb;
  border-radius: 20px 20px 0 0;
  box-shadow: 0 -8px 40px rgba(60, 30, 50, 0.18);
  animation: bs-up 0.28s cubic-bezier(0.22, 1, 0.36, 1);
  transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1);
  padding-bottom: env(safe-area-inset-bottom, 0px);
}
@keyframes bs-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
.bs-grab { flex-shrink: 0; cursor: grab; touch-action: none; padding-top: 8px; user-select: none; }
.bs-grab:active { cursor: grabbing; }
.bs-handle {
  display: block; width: 44px; height: 4.5px; border-radius: 3px;
  margin: 0 auto 6px;
  background: color-mix(in srgb, #a08a96 35%, transparent);
}
.bs-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 2px 16px 8px;
}
.bs-title { margin: 0; font-size: 15.5px; font-weight: 800; color: #6b5560; letter-spacing: 0.5px; }
.bs-close {
  border: 0; background: none; cursor: pointer;
  color: #a08a96; font-size: 16px; padding: 4px 6px; border-radius: 8px;
}
.bs-close:hover { background: rgba(160, 138, 150, 0.12); color: #6b5560; }
.bs-body {
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding: 0 16px calc(16px + env(safe-area-inset-bottom, 0px));
}

/* ===== 桌面端：居中卡片（同一组件两种形态） ===== */
@media (min-width: 769px) {
  .bs-panel {
    left: 50%; right: auto; bottom: auto; top: 50%;
    width: 460px; max-width: calc(100vw - 48px);
    max-height: 78vh;
    transform: translate(-50%, -50%);
    border-radius: 18px;
    box-shadow: 0 18px 60px rgba(60, 30, 50, 0.22);
    animation: bs-pop 0.22s cubic-bezier(0.22, 1, 0.36, 1);
    transition: none;
  }
  @keyframes bs-pop {
    from { opacity: 0; transform: translate(-50%, calc(-50% + 14px)); }
    to { opacity: 1; transform: translate(-50%, -50%); }
  }
  .bs-grab { cursor: default; }
  .bs-handle { display: none; }
  .bs-head { padding: 14px 18px 8px; }
  .bs-title { font-size: 16px; }
  .bs-body { padding: 0 18px 18px; }
}

/* 深色模式 */
:root.dark-mode .bs-panel { background: #2a2230; box-shadow: 0 -8px 40px rgba(0, 0, 0, 0.5); }
:root.dark-mode .bs-title { color: #e8dde4; }
:root.dark-mode .bs-handle { background: rgba(232, 221, 228, 0.28); }
:root.dark-mode .bs-close { color: #b9a6b3; }
:root.dark-mode .bs-close:hover { background: rgba(232, 221, 228, 0.12); color: #e8dde4; }
</style>
