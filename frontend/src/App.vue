<template>
  <!-- 不需要用户导航栏的页面：
       1. /login / 
       2. /admin/*  （后台有自己的 AdminLayout）
       这些路由自己渲染自己的布局 -->
  <router-view v-if="noLayout" v-slot="{ Component }">
    <transition :name="pageTrans">
      <component :is="Component" :key="route.fullPath" />
    </transition>
  </router-view>
  <!-- 其他普通用户页面：套 AppLayout（顶部导航 + 左侧悬浮导航） -->
  <AppLayout v-else>
    <!-- :key 用 fullPath：同一路由只换 query（如 /test?testId=1→2）时也要重建组件。
         页面切换动画见下方 pageTrans：进下一级从右滑入、返回反向滑出、底部 Tab 淡入淡出 -->
    <router-view v-slot="{ Component }">
      <transition :name="pageTrans">
        <component :is="Component" :key="route.fullPath" />
      </transition>
    </router-view>
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

// ===== 页面切换动画方向（2026-11-18 约定）=====
// 进下一级（层级变深）→ 新页从右滑入；返回 → 旧页反方向滑出；
// 底部 Tab 互切 / 同级页 → 淡入淡出；同页只换 query（?tab= / ?testId=）→ 不动画（保持硬切重建）。
// CSS 动画本体在 mobile.css §2.57（只在手机端生效，PC 保持现状）。
const MOBILE_TABS = ['/main', '/diary', '/assistant', '/fm']   // 与 AppLayout.mobileTabs 一致
const pageDepth = (r) => (Array.isArray(r.meta?.crumbs) ? r.meta.crumbs.length : 1)
const pageTrans = ref('')
router.beforeEach((to, from, next) => {
  if (!from.name || to.path === from.path) { pageTrans.value = ''; next(); return }
  const tabToTab = MOBILE_TABS.includes(to.path) && MOBILE_TABS.includes(from.path)
  if (tabToTab) pageTrans.value = 'page-fade'
  else if (pageDepth(to) > pageDepth(from)) pageTrans.value = 'page-forward'
  else if (pageDepth(to) < pageDepth(from)) pageTrans.value = 'page-back'
  else pageTrans.value = 'page-fade'
  next()
})

// ===== 路由切换加载页 =====
// 2026-11-18 改为「慢了才出现」：导航超过 180ms 还没完成才亮加载页，
// 否则不透明的加载屏会把页面滑入动画整个盖住（原来每次切换固定亮 350ms）。
const routeLoading = ref(false)
// 保存定时器 id：连续快速切路由时，上一次的 setTimeout 必须清掉，
// 否则它会在新页面加载中途把 routeLoading 置 false，加载页闪一下就消失
let loadingTimer = null
let loadingHideTimer = null
router.beforeEach((to, from, next) => {
  // from.name 为空说明是首次进站，交给首屏加载页处理
  if (from.name) {
    clearTimeout(loadingTimer)
    loadingTimer = setTimeout(() => { routeLoading.value = true }, 180)
  }
  next()
})
router.afterEach(() => {
  if (loadingTimer) { clearTimeout(loadingTimer); loadingTimer = null }
  if (!routeLoading.value) return
  // 已经亮出来了：最少显示 250ms，避免闪一下就消失
  if (loadingHideTimer) clearTimeout(loadingHideTimer)
  loadingHideTimer = setTimeout(() => {
    routeLoading.value = false
    loadingHideTimer = null
  }, 250)
})
router.onError(() => {
  clearTimeout(loadingTimer)
  routeLoading.value = false
})
onBeforeUnmount(() => {
  clearTimeout(loadingTimer)
  clearTimeout(loadingHideTimer)
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

  /* ===== 主题派生色（页面里原来写死的薄荷绿/墨绿，换成这组就跟着主题走）=====
     applyTheme 只注入 --c-hex/--c-main/--c-bg1/--c-bg2，所以其余颜色一律从 --c-hex
     用 color-mix 混出来：换任何主题，卡片淡底、描边、投影、标题墨色自动同步 */
  --c-accent: var(--c-hex);                                        /* 强调实色 */
  --c-accent-deep: color-mix(in srgb, var(--c-hex) 72%, #233a28);  /* 渐变深端/深色块 */
  --c-accent-soft: color-mix(in srgb, var(--c-hex) 14%, #ffffff);  /* 淡底（选中/标签/提示条） */
  --c-accent-tint: color-mix(in srgb, var(--c-hex) 7%, #ffffff);   /* 极淡底（hover/斑马纹） */
  --c-accent-line: color-mix(in srgb, var(--c-hex) 30%, transparent); /* 描边 */
  --c-accent-glow: color-mix(in srgb, var(--c-hex) 32%, transparent); /* 光晕/彩色投影 */
  --c-ink: color-mix(in srgb, var(--c-hex) 28%, #2a3630);          /* 标题墨色（原写死 #3d5145） */
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

  /* 派生色在深色底上要混深色，否则淡底会白得发亮、墨色标题会糊成一团 */
  --c-accent-deep: color-mix(in srgb, var(--c-hex) 82%, #0f1a13);
  --c-accent-soft: color-mix(in srgb, var(--c-hex) 22%, #1f2937);
  --c-accent-tint: color-mix(in srgb, var(--c-hex) 12%, #1f2937);
  --c-accent-line: color-mix(in srgb, var(--c-hex) 42%, transparent);
  --c-ink: color-mix(in srgb, var(--c-hex) 38%, #e8eee9);
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