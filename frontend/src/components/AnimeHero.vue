<template>
  <!--
    动漫风页面顶部横幅（与 Test.vue 英雄区同款视觉的通用版）：
    主题色渐变 + 星星/云朵 + 吉祥物 + 波浪底边。
    颜色跟随「个人中心 → 个性化设置 → 主题颜色」实时切换。

    用法：
      <AnimeHero glyph="notebook" title="绪见日记" sub="记录每一天的心情">
        <template #right> 右侧功能区（统计卡/按钮等，原样塞进来） </template>
        <template #left>  左侧内容（如返回按钮，WriteDiary 用） </template>
      </AnimeHero>
  -->
  <header class="ah-hero" :style="heroVars">
    <div class="ah-inner">
      <div v-if="$slots.left" class="ah-left"><slot name="left" /></div>
      <div class="ah-title">
        <h1>
          <span v-if="glyph" class="ah-emoji"><AppIcon :name="glyph" :size="21" /></span>
          {{ title }}
        </h1>
        <!-- 面包屑：路由 meta.crumbs 层级 > 2 时显示（如 首页 > 日记 > 写日记），每一级可点击跳回 -->
        <nav v-if="crumbs" class="ah-crumb" aria-label="页面路径">
          <template v-for="(c, i) in crumbs" :key="i">
            <span v-if="i" class="ah-crumb-sep">›</span>
            <a v-if="c.to" class="ah-crumb-item is-link" href="javascript:;" @click.prevent="go(c.to)">{{ c.label }}</a>
            <span v-else class="ah-crumb-item is-cur">{{ c.label }}</span>
          </template>
        </nav>
      </div>
      <div v-if="$slots.right" class="ah-right"><slot name="right" /></div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

/** 面包屑：只有层级超过两层（≥3 级）才显示；点击任意上级跳回对应页面 */
const crumbs = computed(() => {
  const c = route.meta?.crumbs
  return Array.isArray(c) && c.length > 2 ? c : null
})
const go = (to) => router.push(to)

const props = defineProps({
  /** 页面大标题 */
  title: { type: String, required: true },
  /** 副标题（一句话说明） */
  sub: { type: String, default: '' },
  /** 标题前的线性图标名（见 components/appIcons.js），全站统一用线性图标 */
  glyph: { type: String, default: '' },
})

/* ===== 主题色：与 Test.vue 同源（localStorage.profile_theme），切换即时生效 ===== */
const theme = ref(null)
function readTheme() {
  try { theme.value = JSON.parse(localStorage.getItem('profile_theme')) || null } catch { theme.value = null }
}
readTheme()

/** hex 向白色混合（amount=0.3  30% 白） */
function tint(hexStr, amount) {
  const h = hexStr.replace('#', '')
  const full = h.length === 3 ? h.split('').map(c => c + c).join('') : h
  const r = parseInt(full.slice(0, 2), 16), g = parseInt(full.slice(2, 4), 16), b = parseInt(full.slice(4, 6), 16)
  const mix = (v) => Math.round(v + (255 - v) * amount)
  return `#${[mix(r), mix(g), mix(b)].map(v => v.toString(16).padStart(2, '0')).join('')}`
}

const hex = computed(() => theme.value?.hex || '#e59bb3')
const main = computed(() => theme.value?.main || tint(hex.value, 0.18))
/** 渐变三段：浅  主  深（与 Test 英雄区一致） */
const heroVars = computed(() => ({
  '--ah-hex': hex.value,
  '--ah-h1': tint(main.value, 0.28),
  '--ah-h2': main.value,
  '--ah-h3': hex.value,
  '--ah-shadow': tint(hex.value, 0.45),
}))

const onThemeChange = () => readTheme()
onMounted(() => {
  window.addEventListener('profile-updated', onThemeChange)
  window.addEventListener('storage', onThemeChange)
})
onBeforeUnmount(() => {
  window.removeEventListener('profile-updated', onThemeChange)
  window.removeEventListener('storage', onThemeChange)
})
</script>

<style scoped>
/* ===== 紧凑顶条（参考 AI 陪伴页顶条）：淡主题色通栏一行 ===== */
.ah-hero {
  position: relative;
  width: 100%;
  box-sizing: border-box;
  min-height: 52px;
  padding: 9px 24px;
  margin: 0 0 14px;
  background: linear-gradient(90deg,
    color-mix(in srgb, var(--ah-h2) 14%, transparent) 0%,
    color-mix(in srgb, var(--ah-h2) 5%, transparent) 100%);
  border-bottom: 1px solid color-mix(in srgb, var(--ah-h2) 14%, transparent);
  display: flex;
  align-items: center;
  transition: background 0.3s;
}
/* 内容层 */
.ah-inner {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.ah-title h1 {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--ah-h2);
  margin: 0;
  line-height: 1.3;
  display: flex;
  align-items: center;
  gap: 8px;
}
.ah-emoji { display: inline-flex; align-items: center; color: var(--ah-h2); }

.ah-left, .ah-right { flex-shrink: 0; }

/* ===== 面包屑路径（层级 > 2 的页面才出现） ===== */
.ah-crumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 3px;
  font-size: 12.5px;
  line-height: 1.4;
  color: color-mix(in srgb, var(--ah-h2) 55%, #5c5c72);
  user-select: none;
}
.ah-crumb-sep { opacity: 0.5; font-size: 13px; }
.ah-crumb-item.is-link {
  cursor: pointer;
  font-weight: 600;
  color: color-mix(in srgb, var(--ah-h2) 80%, #4a4a5a);
  transition: color 0.2s;
}
.ah-crumb-item.is-link:hover { color: var(--ah-h2); text-decoration: underline; }
.ah-crumb-item.is-cur { font-weight: 700; color: var(--ah-h2); }

:root.dark-mode .ah-crumb { color: color-mix(in srgb, var(--ah-h2) 55%, #c9cad6); }
:root.dark-mode .ah-crumb-item.is-link { color: color-mix(in srgb, var(--ah-h2) 75%, #d5d6e2); }
:root.dark-mode .ah-crumb-item.is-cur { color: color-mix(in srgb, var(--ah-h2) 70%, #fff); }
:root.dark-mode .ah-title h1 { color: color-mix(in srgb, var(--ah-h2) 70%, #fff); }
</style>

