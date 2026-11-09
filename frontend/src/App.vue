<template>
  <!-- 不需要用户导航栏的页面：
       1. /login / 
       2. /admin/*  （后台有自己的 AdminLayout）
       这些路由自己渲染自己的布局 -->
  <router-view v-if="noLayout" :key="route.fullPath" />
  <!-- 其他普通用户页面：套 AppLayout（顶部导航 + 左侧悬浮导航） -->
  <AppLayout v-else>
    <!-- :key 用 fullPath：同一路由只换 query（如 /test?testId=1→2）时也要重建组件 -->
    <router-view :key="route.fullPath" />
  </AppLayout>

  <!-- 全局路由切换加载页（仅非首次进站时出现） -->
  <transition name="route-fade">
    <div v-if="routeLoading" class="route-loading">
      <CowLoading size="lg" text="加载中" />
    </div>
  </transition>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from './layouts/AppLayout.vue'
import CowLoading from './components/CowLoading.vue'
import './styles/base.css'
// 移动端响应式兜底层：必须在 base.css 之后引入才能覆盖页面内的 scoped 样式
import './styles/mobile.css'

const route = useRoute()
const router = useRouter()

// ===== 路由切换加载页 =====
const routeLoading = ref(false)
let switchStart = 0
// 保存定时器 id：连续快速切路由时，上一次的 setTimeout 必须清掉，
// 否则它会在新页面加载中途把 routeLoading 置 false，加载页闪一下就消失
let loadingTimer = null
router.beforeEach((to, from, next) => {
  // from.name 为空说明是首次进站，交给首屏加载页处理
  if (from.name) {
    routeLoading.value = true
    switchStart = Date.now()
  }
  next()
})
router.afterEach(() => {
  if (!routeLoading.value) return
  // 最少显示 350ms，避免闪一下就消失
  const elapsed = Date.now() - switchStart
  if (loadingTimer) clearTimeout(loadingTimer)
  loadingTimer = setTimeout(() => {
    routeLoading.value = false
    loadingTimer = null
  }, Math.max(0, 350 - elapsed))
})
onBeforeUnmount(() => {
  if (loadingTimer) clearTimeout(loadingTimer)
})
// 不套用户导航栏的页面：登录页、欢迎页（/ 与 /home 同一页面）、后台管理
const noLayout = computed(() => {
  return route.path === '/login'
      || route.path === '/'
      || route.path === '/home'
      || route.path.startsWith('/admin')
})

// ===== 全局主题（读取 localStorage，注入 :root CSS 变量）=====
const ALL_THEMES = [
  { name: 'green',  label: '薄荷绿', hex: '#7bc47b', main: '#90db90', bg1: '#f6f9f6', bg2: '#f0f7f0' },
  { name: 'blue',   label: '天空蓝', hex: '#5b9bd5', main: '#7cb8ea', bg1: '#f2f7fc', bg2: '#eaf2fa' },
  { name: 'purple', label: '浪漫紫', hex: '#9b7bd5', main: '#b8a0e6', bg1: '#f6f3fb', bg2: '#ede8f7' },
  { name: 'pink',   label: '少女粉', hex: '#e59bb3', main: '#f2b8cd', bg1: '#fcf4f7', bg2: '#fae8ef' },
  { name: 'orange', label: '暖阳橙', hex: '#e69b5b', main: '#f2b87c', bg1: '#fdf6ef', bg2: '#fae8d9' },
  { name: 'teal',   label: '青碧',   hex: '#5bb8a6', main: '#7cd4c3', bg1: '#f2faf8', bg2: '#eaf5f2' },
  { name: 'rose',   label: '玫瑰红', hex: '#d96b7b', main: '#ec8c9a', bg1: '#fcf2f4', bg2: '#fae6e9' },
  { name: 'indigo', label: '靛青',   hex: '#6b7bd9', main: '#8c9aec', bg1: '#f2f4fc', bg2: '#e6e9fa' },
]
const DEFAULT_THEME = ALL_THEMES[0]

// 把主题色写入 :root CSS 变量（全局生效）
// 用户端与后台主题互相独立：/admin 路由读 admin_theme/admin_dark，其余读 profile_theme/profile_dark
const applyTheme = () => {
  try {
    const isAdmin = route.path.startsWith('/admin')
    const saved = localStorage.getItem(isAdmin ? 'admin_theme' : 'profile_theme')
    let t = saved ? JSON.parse(saved) : null
    // 旧数据/后台色板只存了 name+hex，按 name 从内置色板补齐 main/bg1/bg2
    if (t) { const full = ALL_THEMES.find(x => x.name === t.name); if (full) t = full }
    t = t || DEFAULT_THEME
    const r = document.documentElement.style
    r.setProperty('--c-hex', t.hex)
    r.setProperty('--c-main', t.main)
    // 深色模式
    const dark = localStorage.getItem(isAdmin ? 'admin_dark' : 'profile_dark') === '1'
    // 后台深色：主题色板里的 bg1/bg2 都是浅色（#f6f9f6 这类），深色模式下当页面底会发白。
    // 这里只能用 JS 覆盖——变量是内联写在 <html> 上的，CSS 的 :root.dark-mode 优先级比不过内联样式。
    // 前台不在这里改：Main/Profile 等页面各自有 dark-mode 的专属配色，动这个变量会把它们染脏。
    if (isAdmin && dark) {
      r.setProperty('--c-bg1', '#141c26')
      r.setProperty('--c-bg2', '#1a2330')
    } else {
      r.setProperty('--c-bg1', t.bg1)
      r.setProperty('--c-bg2', t.bg2)
    }
    document.documentElement.classList.toggle('dark-mode', dark)
    // Element Plus 的深色主题只认 html.dark，两个类一起切，组件（弹窗/下拉/分页）才会跟着变深
    document.documentElement.classList.toggle('dark', dark)
  } catch (e) { console.warn('主题读取失败', e) }
}

// 初始化 + 每次路由变化都重新读（进出后台自动切换两套主题；Profile 页改完主题跳回其他页面能实时生效）
onMounted(applyTheme)
watch(() => route.path, applyTheme)
// 监听 storage 事件（同一个浏览器里多个 tab 同步）
window.addEventListener('storage', (e) => {
  if (['profile_theme', 'profile_dark', 'admin_theme', 'admin_dark'].includes(e.key)) applyTheme()
})
// 监听同标签页内的自定义事件（Profile/AdminLayout 保存后立刻生效，不用等路由切换）
window.addEventListener('profile-updated', applyTheme)
window.addEventListener('admin-theme-updated', applyTheme)
</script>

<style>
html, body, #app { margin: 0; padding: 0; min-height: 100%; }
body { font-family: "宋体", "Microsoft YaHei", sans-serif; }

/* ===== 全局主题 CSS 变量（默认薄荷绿）===== */
:root {
  --c-hex: #7bc47b;      /* 主色（按钮、图标、强调）*/
  --c-main: #90db90;     /* 浅色（渐变、hover）*/
  --c-bg1: #f6f9f6;      /* 背景1 */
  --c-bg2: #f0f7f0;      /* 背景2 */
  --c-text: #5a6b5a;     /* 正文/坐标轴文字（ECharts 用）*/
  --c-text-weak: #8a9a8a;/* 次要文字/图例 */
  --c-border: rgba(140, 160, 140, 0.18); /* 分隔线/边框 */
  --c-border-strong: #ced8cb;            /* 较深边框/禁用态（实色）*/
  --c-panel: #ffffff;    /* 卡片/图表背景（ECharts tooltip 用）*/
  /* 面板层级（后台卡片体系）：浅色是白/半透明白，深色模式整体翻转，卡片不再"白得刺眼" */
  --c-surface: rgba(255, 255, 255, 0.82);        /* 磨砂卡片（admin-card / chart-box）*/
  --c-surface-solid: #ffffff;                    /* 实色面板（弹窗 / 输入框 / 统计卡）*/
  --c-surface-weak: rgba(255, 255, 255, 0.72);   /* 侧栏 / 顶栏 */
  --c-surface-line: rgba(255, 255, 255, 0.6);    /* 卡片高光描边 */
  --c-shadow-soft: rgba(140, 170, 130, 0.12);    /* 卡片投影 */
  --c-shadow-hover: rgba(140, 170, 130, 0.2);
  /* 图表系列色（第 1 色用 --c-hex，2~6 走这里）：换主题/深色时系列色一起变 */
  --c-s2: #378ADD;
  --c-s3: #BA7517;
  --c-s4: #D4537E;
  --c-s5: #534AB7;
  --c-s6: #1D9E75;
}

/* ===== 全局深色模式 ===== */
:root.dark-mode {
  color-scheme: dark;
  --c-text: #cbd5e1;
  --c-text-weak: #94a3b8;
  --c-border: rgba(148, 163, 184, 0.22);
  --c-border-strong: #4b5563;
  --c-panel: #1f2937;
  /* 面板层级翻转到深色（卡片、弹窗、输入框、侧栏）*/
  --c-surface: rgba(31, 41, 55, 0.86);
  --c-surface-solid: #1f2937;
  --c-surface-weak: rgba(24, 33, 45, 0.82);
  --c-surface-line: rgba(255, 255, 255, 0.07);
  --c-shadow-soft: rgba(0, 0, 0, 0.4);
  --c-shadow-hover: rgba(0, 0, 0, 0.55);
  /* 深色模式下把系列色整体提亮，保证在 #1f2937 底上的对比度 */
  --c-s2: #60a5fa;
  --c-s3: #fbbf24;
  --c-s4: #f472b6;
  --c-s5: #a78bfa;
  --c-s6: #34d399;
}
:root.dark-mode body {
  background: linear-gradient(135deg, #1f2937, #111827, #1f2937);
  color: #e5e7eb;
}

/* ===== 路由切换加载页 ===== */
.route-loading {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 50% 40%, #ffffff 0%, #f2fbf4 55%, #e6f7ea 100%);
}
:root.dark-mode .route-loading {
  background: radial-gradient(circle at 50% 40%, #1f2937 0%, #18222f 60%, #111827 100%);
}
.route-fade-enter-active,
.route-fade-leave-active { transition: opacity 0.25s ease; }
.route-fade-enter-from,
.route-fade-leave-to { opacity: 0; }
</style>