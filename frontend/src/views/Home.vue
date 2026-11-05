<template>
  <!-- 欢迎页：未登录显示；已登录直接去主页 -->
  <div class="welcome-page">
    <!-- 背景：插画 + 柔光，保证文字可读 -->
    <div class="bg-layer"></div>
    <div class="bg-veil"></div>

    <!-- 顶部导航：品牌 + 登录入口 -->
    <header class="top-bar">
      <span class="tb-brand"><img class="brand-cow" src="/img/绪见后台管理.png" alt="绪见" />绪见</span>
      <div class="tb-actions">
        <!-- 未登录显示「登录」；已登录显示「进入主页」（不再自动跳转，欢迎页始终可见） -->
        <router-link v-if="!isLogin" to="/login" class="tb-login">登 录</router-link>
        <router-link v-else to="/main" class="tb-login">进入主页</router-link>
        <router-link to="/main" class="tb-start">免费开始</router-link>
      </div>
    </header>

    <!-- 泡泡（露珠感，克制） -->
    <span v-for="b in bubbles" :key="b.id" class="bubble" :style="b.style"></span>

    <!-- 主视觉：大字标题直接落在插画上 -->
    <main class="hero">
      <p class="eyebrow">个人情绪记录 · 分析与陪伴平台</p>
      <h1 class="headline">把情绪，<br />轻轻放下。</h1>
      <p class="sub">在这里记录心情、了解自己，<br />愿每一个坏情绪，都被温柔接住。</p>
      <div class="cta-row">
        <router-link to="/main" class="cta-main">开始记录</router-link>
        <router-link to="/fm" class="cta-ghost">先听听 FM</router-link>
      </div>
    </main>

    <!-- 后台配置的轮播 / 公告（游客可见） -->
    <div v-if="banners.length > 0" class="banner-row">
      <div class="bn-card" :style="cur.coverImg ? { backgroundImage: 'url(' + cur.coverImg + ')' } : {}">
        <div class="bn-text">
          <div class="bn-title">{{ cur.title }}</div>
          <div v-if="cur.subtitle" class="bn-sub">{{ cur.subtitle }}</div>
        </div>
        <a v-if="cur.linkType === 'url' && cur.linkUrl" :href="cur.linkUrl" target="_blank" rel="noopener" class="bn-go">去看看</a>
        <router-link v-else-if="cur.linkType !== 'none'" :to="linkTo(cur)" class="bn-go">去看看</router-link>
      </div>
      <div v-if="banners.length > 1" class="bn-dots">
        <span v-for="(b, i) in banners" :key="b.bannerId" class="bn-dot" :class="{ on: i === bnIdx }" @click="bnIdx = i"></span>
      </div>
    </div>

    <!-- 底部：左功能索引 / 右寄语 -->
    <footer class="bottom-row">
      <div class="feat-row">
        <router-link to="/main" class="feat">心情记录</router-link>
        <router-link to="/test" class="feat">心理测评</router-link>
        <router-link to="/fm" class="feat">治愈FM</router-link>
        <router-link to="/classroom" class="feat">绪见学堂</router-link>
      </div>
      <p class="foot-note">愿你被这个世界温柔以待</p>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, computed } from 'vue'
import { getBannerList } from '@/api'

// 是否已登录（只影响右上角入口文案，不再自动跳转）
const isLogin = ref(!!localStorage.getItem('token'))

// ===== 后台配置的轮播 / 公告 =====
const banners = ref([])
const bnIdx = ref(0)
let bnTimer = null
const cur = computed(() => banners.value[bnIdx.value] || { linkType: 'none' })
const linkTo = (b) => ({ diary: '/diary/write', test: '/test', video: '/main', audio: '/fm' }[b.linkType] || '/main')

onMounted(() => {
  bubbleTimer = setInterval(createBubble, 500)
  // 轮播加载失败不影响欢迎页本身
  getBannerList('home').then((res) => {
    if (res?.code === 200 && Array.isArray(res.data) && res.data.length > 0) {
      banners.value = res.data
      if (res.data.length > 1) {
        bnTimer = setInterval(() => { bnIdx.value = (bnIdx.value + 1) % banners.value.length }, 5000)
      }
    }
  }).catch(() => {})
})

// ===== 泡泡动效 =====
let bubbleTimer = null
let bubbleId = 0
const bubbles = ref([])
const createBubble = () => {
  const r = Math.random() * 4 + 16
  const id = ++bubbleId
  bubbles.value.push({
    id,
    style: {
      width: r + 'px',
      height: r + 'px',
      left: Math.random() * 100 + 'vw',
    },
  })
  setTimeout(() => {
    bubbles.value = bubbles.value.filter((b) => b.id !== id)
  }, 4800)
}
onBeforeUnmount(() => {
  if (bubbleTimer) clearInterval(bubbleTimer)
  if (bnTimer) clearInterval(bnTimer)
})
</script>

<style scoped>
.welcome-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

/* ===== 背景 ===== */
.bg-layer {
  position: absolute;
  inset: 0;
  background-image: url(/img/首页背景图.png),
    linear-gradient(180deg, #f4f8ee 0%, #e2efd8 55%, #cfe6c4 100%);
  background-size: cover;
  background-position: center;
}
/* 柔光：标题区域垫一层可读性，右侧留给奶牛 */
.bg-veil {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(920px 700px at 14% 46%, rgba(255,255,255,.96), rgba(255,255,255,.72) 52%, rgba(255,255,255,0) 76%),
    linear-gradient(180deg, rgba(255,255,255,.4) 0%, rgba(255,255,255,0) 28%);
}

/* ===== 顶部导航 ===== */
.top-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px 4vw;
}
.tb-brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #33513c;
  text-shadow: 0 1px 3px rgba(255,255,255,1), 0 0 10px rgba(255,255,255,.9);
}
/* 品牌 logo：与登录页 / 前台顶栏 / 后台侧栏同款牛牛 */
.tb-brand .brand-cow {
  width: 34px;
  height: 34px;
  object-fit: contain;
  filter: drop-shadow(0 1px 4px rgba(0,0,0,.12));
}
.tb-actions { display: flex; gap: 12px; }
.tb-login {
  padding: 9px 22px;
  font-size: 14px;
  letter-spacing: 1px;
  text-decoration: none;
  color: #4c7355;
  background: rgba(255, 255, 255, .66);
  border: 1px solid rgba(255, 255, 255, .9);
  border-radius: 999px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: transform .22s, background .22s;
}
.tb-login:hover { transform: translateY(-1px); background: #fff; }
.tb-start {
  padding: 9px 22px;
  font-size: 14px;
  letter-spacing: 1px;
  text-decoration: none;
  color: #fff;
  background: linear-gradient(135deg, #9fd48a, #7cbf6d);
  border-radius: 999px;
  box-shadow: 0 6px 16px rgba(124, 168, 130, .38);
  transition: transform .22s, box-shadow .22s;
}
.tb-start:hover { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(124, 168, 130, .48); }

/* ===== 泡泡 ===== */
.bubble {
  position: absolute;
  bottom: -36px;
  z-index: 1;
  border-radius: 50%;
  background: radial-gradient(circle at 72% 28%, #fff 3px, #cde9d2 10%, #cfe6f2 100%);
  box-shadow: inset 0 0 5px #fff, 0 0 14px rgba(255,255,255,.9);
  opacity: .45;
  animation: rise 4.8s linear infinite;
  pointer-events: none;
}
@keyframes rise {
  0%   { transform: translateY(0); opacity: .45; }
  60%  { opacity: .3; }
  100% { transform: translate(-12px, -110vh) scale(1.35); opacity: 0; }
}

/* ===== 主视觉 ===== */
.hero {
  position: absolute;
  z-index: 2;
  left: 7vw;
  top: 50%;
  transform: translateY(-54%);
  max-width: 560px;
  animation: heroIn .8s cubic-bezier(.22, .9, .3, 1.1) both;
}
@keyframes heroIn {
  from { opacity: 0; transform: translateY(calc(-54% + 24px)); }
  to   { opacity: 1; transform: translateY(-54%); }
}
.eyebrow {
  margin: 0 0 16px;
  font-size: 14px;
  letter-spacing: 4px;
  font-weight: 600;
  color: #33513c;
  text-shadow: 0 1px 2px rgba(255,255,255,1), 0 0 14px rgba(255,255,255,.95);
}
.headline {
  margin: 0 0 16px;
  font-size: 52px;
  line-height: 1.3;
  letter-spacing: 5px;
  font-weight: 700;
  color: #26402f;
  text-shadow: 0 2px 4px rgba(255,255,255,1), 0 0 20px rgba(255,255,255,.9);
}
.sub {
  margin: 0 0 26px;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.9;
  color: #2f4a38;
  text-shadow: 0 1px 2px rgba(255,255,255,1), 0 0 12px rgba(255,255,255,.95);
}
.cta-row { display: flex; gap: 14px; }
.cta-main {
  padding: 13px 38px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 3px;
  text-decoration: none;
  color: #fff;
  background: linear-gradient(135deg, #9fd48a, #7cbf6d);
  border-radius: 999px;
  box-shadow: 0 10px 24px rgba(124, 168, 130, .42);
  transition: transform .25s, box-shadow .25s;
}
.cta-main:hover { transform: translateY(-2px); box-shadow: 0 14px 30px rgba(124, 168, 130, .52); }
.cta-ghost {
  padding: 13px 30px;
  font-size: 15px;
  letter-spacing: 2px;
  text-decoration: none;
  color: #4c7355;
  background: rgba(255, 255, 255, .6);
  border: 1px solid rgba(255, 255, 255, .95);
  border-radius: 999px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: transform .25s, background .25s;
}
.cta-ghost:hover { transform: translateY(-2px); background: rgba(255, 255, 255, .85); }

/* ===== 底部行 ===== */
.bottom-row {
  position: absolute;
  z-index: 2;
  left: 4vw;
  right: 4vw;
  bottom: 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  animation: heroIn .8s .2s cubic-bezier(.22, .9, .3, 1.1) both;
}
.feat-row { display: flex; gap: 10px; flex-wrap: wrap; }
.feat {
  padding: 7px 18px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  text-decoration: none;
  color: #2f4a38;
  background: rgba(255, 255, 255, .78);
  border: 1px solid rgba(255, 255, 255, .95);
  border-radius: 999px;
  box-shadow: 0 2px 10px rgba(90, 130, 95, .22);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  transition: transform .22s, background .22s;
}
.feat:hover { transform: translateY(-2px); background: #fff; }
.foot-note {
  margin: 0;
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 3px;
  color: #3c5544;
  text-shadow: 0 1px 2px rgba(255,255,255,1), 0 0 10px rgba(255,255,255,.95);
  white-space: nowrap;
}

/* 窄屏适配 */
@media (max-width: 768px) {
  .hero { left: 6vw; right: 6vw; max-width: none; }
  .headline { font-size: 42px; letter-spacing: 4px; }
  .foot-note { display: none; }
}

/* ===== 轮播 / 公告条 ===== */
/* 欢迎页的 top-bar / hero / bottom-row 都是 absolute 布局，
   轮播必须同样 absolute 挂在底部栏上方，否则会落到文档流顶部与顶栏重叠 */
.banner-row {
  position: absolute; z-index: 2;
  left: 50%; transform: translateX(-50%);
  bottom: 88px;
  width: min(620px, calc(100% - 40px));
  text-align: center;
}
.bn-card {
  display: flex; align-items: center; justify-content: space-between; gap: 14px;
  background: rgba(255, 255, 255, 0.82); backdrop-filter: blur(8px);
  border-radius: 18px; padding: 14px 18px; min-height: 62px;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.10); background-size: cover; background-position: center;
  transition: background-image .4s ease;
}
.bn-card::before { content: ''; position: absolute; }
.bn-text { text-align: left; min-width: 0; }
.bn-title { font-size: 15px; font-weight: 600; color: #3d4a3d; line-height: 1.4; }
.bn-sub { font-size: 12.5px; color: var(--c-text-weak); margin-top: 3px; line-height: 1.5; }
.bn-go {
  flex-shrink: 0; font-size: 12.5px; color: #fff; background: var(--c-hex);
  padding: 6px 14px; border-radius: 16px; text-decoration: none; white-space: nowrap;
}
.bn-go:hover { background: #6ab36a; }
.bn-dots { display: flex; justify-content: center; gap: 6px; margin-top: 10px; }
.bn-dot { width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,0.55); cursor: pointer; transition: all .2s; }
.bn-dot.on { width: 16px; border-radius: 3px; background: var(--c-hex); }
@media (max-width: 768px) {
  /* 手机端底部功能索引会换行变高（bottom:26px 起约 72px 高），轮播要再往上抬 */
  .banner-row { bottom: 150px; }
  .bn-card { padding: 12px 14px; }
  .bn-title { font-size: 14px; }
}

</style>
