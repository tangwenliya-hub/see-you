<template>
  <!--
    全站统一线性图标（唯一渲染入口）
    - 尺寸：默认 1em，跟着字号走；需要固定尺寸时传数字（像素）或 CSS 长度
    - 颜色：stroke=currentColor，直接继承父级文字颜色，深浅色/主题色都自动跟随
    - 粗细：默认 1.6，可用 CSS 变量 --icon-stroke 全局覆盖，或单传 stroke-width
  -->
  <svg
    class="app-icon"
    :width="cssSize"
    :height="cssSize"
    viewBox="0 0 24 24"
    :fill="isFilled ? 'currentColor' : 'none'"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import { ICONS, FILLED, hasIcon } from './appIcons'

const props = defineProps({
  /** 图标名，见 appIcons.js 的 ICONS */
  name: { type: String, required: true },
  /** 尺寸：数字=像素；字符串原样（1em / 18px / 1.2rem） */
  size: { type: [Number, String], default: '1em' },
  /** 描边粗细，默认跟随全局 --icon-stroke（1.6） */
  strokeWidth: { type: [Number, String], default: null },
})

const paths = computed(() => {
  if (!hasIcon(props.name) && import.meta.env.DEV) {
    console.warn(`[AppIcon] 未定义的图标名：${props.name}`)
  }
  return ICONS[props.name] || []
})
const isFilled = computed(() => FILLED.has(props.name))
const cssSize = computed(() => {
  const s = props.size
  if (typeof s === 'number') return `${s}px`
  return /^\d+(\.\d+)?$/.test(String(s)) ? `${s}px` : s
})
const strokeWidth = computed(() => {
  if (props.strokeWidth !== null && props.strokeWidth !== '') return props.strokeWidth
  return 'var(--icon-stroke, 1.6)'
})
</script>

<style>
/* 全局而非 scoped：图标到处都在用，且要能被父级的 font-size / color 影响 */
.app-icon {
  display: inline-block;
  flex: none;
  vertical-align: -0.125em;
  overflow: visible;
  pointer-events: none;
}
</style>
