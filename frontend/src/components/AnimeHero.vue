<template>
  <!--
    动漫风页面顶部横幅（与 Test.vue 英雄区同款视觉的通用版）：
    主题色渐变 + 星星/云朵 + 吉祥物 + 波浪底边。
    颜色跟随「个人中心 → 个性化设置 → 主题颜色」实时切换。

    用法：
      <AnimeHero icon="/img/绪见日记.png" title="绪见日记" sub="记录每一天的心情">
        <template #right> 右侧功能区（统计卡/按钮等，原样塞进来） </template>
        <template #left>  左侧内容（如返回按钮，WriteDiary 用） </template>
      </AnimeHero>
  -->
  <header class="ah-hero" :style="heroVars">
    <div class="ah-blob b1"></div>
    <div class="ah-blob b2"></div>
    <span class="ah-star s1"><AppIcon name="sparkles" /></span>
    <span class="ah-star s2"><AppIcon name="sparkles" /></span>
    <span class="ah-star s3"><AppIcon name="sparkles" /></span>
    <span class="ah-cloud c1"><AppIcon name="cloud" /></span>
    <span class="ah-cloud c2"><AppIcon name="cloud" /></span>

    <div class="ah-inner">
      <div v-if="$slots.left" class="ah-left"><slot name="left" /></div>
      <div class="ah-title">
        <h1>
          <span v-if="icon" class="ah-icon-chip"><img :src="icon" alt="" /></span>
          <span v-else-if="glyph" class="ah-emoji"><AppIcon :name="glyph" /></span>
          {{ title }}
        </h1>
        <p v-if="sub" class="ah-sub">{{ sub }}</p>
      </div>
      <!-- 吉祥物小熊：作为弹性布局中间项，自动落在标题与右侧功能区的空档，右侧内容再宽也不会被挡 -->
      <div class="ah-mascot">ʕ •ᴥ• ʔ</div>
      <div v-if="$slots.right" class="ah-right"><slot name="right" /></div>
    </div>

    <svg class="ah-wave" viewBox="0 0 1440 70" preserveAspectRatio="none">
      <path d="M0,40 C240,80 480,0 720,30 C960,60 1200,10 1440,40 L1440,70 L0,70 Z" fill="#ffffff"/>
    </svg>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  /** 页面大标题 */
  title: { type: String, required: true },
  /** 副标题（一句话说明） */
  sub: { type: String, default: '' },
  /** 标题前图标：传图片路径（如 /img/绪见日记.png），与 glyph 二选一 */
  icon: { type: String, default: '' },
  /** 标题前的线性图标名（见 components/appIcons.js），无插画时用 */
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
.ah-hero {
  position: relative;
  width: 100%;
  box-sizing: border-box;
  padding: 44px 28px 58px;   /* 与 Test 页英雄区一致 */
  margin: 0 0 16px;   /* 波浪底边与下方内容留一点呼吸 */
  background: linear-gradient(135deg, var(--ah-h1) 0%, var(--ah-h2) 45%, var(--ah-h3) 100%);
  border-radius: 0 0 36px 36px;
  overflow: hidden;
  box-shadow: 0 12px 32px var(--ah-shadow);
  transition: background 0.3s;
}
/* 柔和光斑（与 Test 页同款，让顶部质感一致） */
.ah-blob {
  position: absolute;
  border-radius: 50%;
  background: rgba(255,255,255,0.25);
  filter: blur(2px);
}
.ah-blob.b1 { width: 220px; height: 220px; top: -80px; right: -40px; }
.ah-blob.b2 { width: 140px; height: 140px; bottom: -60px; left: 12%; background: rgba(255,255,255,0.18); }

/* 一闪一闪的星星 */
.ah-star {
  position: absolute; color: rgba(255,255,255,0.95);
  animation: ahTwinkle 2.4s ease-in-out infinite;
  text-shadow: 0 0 10px rgba(255,255,255,0.9);
  pointer-events: none;
}
.ah-star.s1 { font-size: 18px; top: 26%; left: 34%; }
.ah-star.s2 { font-size: 13px; top: 62%; left: 48%; animation-delay: 0.8s; }
.ah-star.s3 { font-size: 15px; top: 18%; right: 27%; animation-delay: 1.4s; }
@keyframes ahTwinkle {
  0%, 100% { opacity: 0.35; transform: scale(0.85) rotate(0deg); }
  50% { opacity: 1; transform: scale(1.2) rotate(18deg); }
}

/* 慢慢飘的云 */
.ah-cloud {
  position: absolute; color: rgba(255,255,255,0.5);
  font-size: 40px; pointer-events: none;
  animation: ahCloudDrift 26s linear infinite;
}
.ah-cloud.c1 { top: 12%; left: -60px; }
.ah-cloud.c2 { top: 55%; left: -120px; font-size: 30px; animation-delay: -13s; animation-duration: 34s; }
@keyframes ahCloudDrift {
  from { transform: translateX(0); }
  to { transform: translateX(calc(100vw + 200px)); }
}

/* 吉祥物小熊：弹性布局中间项，自动站在标题与右侧功能区的空档（不再写死百分比）；窄屏让位 */
.ah-mascot {
  font-size: 46px; font-weight: 700;
  color: rgba(255,255,255,0.96);
  text-shadow: 0 3px 0 var(--ah-shadow), 0 8px 18px var(--ah-shadow);
  animation: ahBob 2.6s ease-in-out infinite;
  pointer-events: none;
  user-select: none;
  letter-spacing: -2px;
  line-height: 1;
  flex-shrink: 0;
}
@keyframes ahBob {
  0%, 100% { transform: translateY(0) rotate(-4deg); }
  50% { transform: translateY(-10px) rotate(4deg); }
}
@media (max-width: 1180px) {
  .ah-mascot, .ah-star.s3 { display: none; }
}

/* 波浪底边（与页面白色内容区衔接） */
.ah-wave {
  position: absolute; bottom: -2px; left: 0;
  width: 100%; height: 42px; display: block;
  pointer-events: none;
}

/* 内容层 */
.ah-inner {
  position: relative; z-index: 2;
  display: flex; justify-content: space-between; align-items: center;
  gap: 18px; flex-wrap: wrap; max-width: 1400px; margin: 0 auto;
}
.ah-title h1 {
  font-size: 34px; font-weight: 800; letter-spacing: 2px;
  color: #fff; margin: 0 0 6px;
  line-height: 1.25;
  display: flex; align-items: center; gap: 10px;
  text-shadow: 0 2px 0 var(--ah-shadow), 0 3px 12px var(--ah-shadow);
}
.ah-sub { font-size: 15px; color: rgba(255,255,255,0.92); margin: 0; letter-spacing: 2px; }
.ah-emoji { font-size: 26px; display: inline-flex; align-items: center; }

/* 图片图标：白色小圆角底板托住，保证任何主题色下都看得清 */
.ah-icon-chip {
  display: inline-flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.92);
  border-radius: 12px; padding: 5px 8px;
  box-shadow: 0 3px 8px var(--ah-shadow);
}
.ah-icon-chip img { width: 26px; height: 26px; object-fit: contain; display: block; }

.ah-left, .ah-right { flex-shrink: 0; }
</style>
