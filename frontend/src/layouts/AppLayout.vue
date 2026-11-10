<template>
  <div class="app-layout-wrap">
    <!-- 顶部 hover 感应区（鼠标靠近顶部 0-60px 时显示导航栏） -->
    <div class="app-bar-hover-zone" @mouseenter="onHoverZoneEnter" @mouseleave="onHoverZoneLeave"></div>

    <!-- 顶部导航栏（默认隐藏，hover 顶部显示） -->
    <header class="app-bar" :class="{ visible: showBar }"
            @mouseenter="onBarEnter" @mouseleave="onBarLeave">
      <!-- 返回按钮（除了主页 /main 都显示） -->
      <router-link v-if="!isHome" to="/main" class="back-btn" title="返回主页">
        ← 返回
      </router-link>

      <router-link to="/main" class="logo">
        <img class="logo-cow" src="/img/绪见后台管理.png" alt="绪见" />
        <span>绪见</span>
      </router-link>
      <nav class="nav-links">
        <router-link v-for="item in navItems" :key="item.id" :to="item.to" class="bar-item">{{ item.label }}</router-link>
      </nav>
      <!-- 未登录：显示登入/注册 -->
      <div v-if="!isLoggedIn" class="app-actions">
        <router-link to="/login" class="auth-btn login">登入</router-link>
        <router-link :to="{ path: '/login', query: { mode: 'register' } }" class="auth-btn register">注册</router-link>
      </div>
      <!-- 已登录：显示头像 + 退出 -->
      <div v-else class="app-actions user-actions">
        <span v-if="userInfo" class="nav-greeting">你好！{{ userInfo.nickname || userInfo.username || '用户' }} 👋</span>
        <router-link to="/profile" class="user-info" title="个人中心">
          <div class="top-avatar-wrap" :class="['frame-' + userFrame]">
            <img :src="topAvatarUrl" class="user-avatar" @error="onAvatarError" />
            <img v-if="currentFrameImg" :src="currentFrameImg" class="frame-ring-img" :style="{ width: currentFrameScale + '%', height: currentFrameScale + '%' }" @error="$event.target.style.display='none'" />
          </div>
          <span v-if="isAdmin" class="role-badge" title="管理员">👑</span>
        </router-link>
        <button class="auth-btn logout" @click="doLogout">退出</button>
      </div>
    </header>

    <!-- 页面内容直接插入 -->
    <slot />

    <!--
      情绪风险预警弹窗（全局单例，非医疗诊断）
      挂在布局层而不是某个页面：无论用户从哪个页面进来，只要有未确认的预警都能被看到。
    -->
    <RiskAlertDialog v-if="isLoggedIn" :key="'rad-' + (userInfo?.userId || userInfo?.id || 'anon')" />

    <!-- 移动端底部导航（PC 端由 mobile.css 隐藏）
         为什么必须有：顶部导航是「鼠标 hover 顶部才显示」的，
         手机没有 hover 事件 → 导航栏永远不可见 → 用户点不回主页。 -->
    <nav class="mobile-tabbar">
      <router-link v-for="t in mobileTabs" :key="t.to" :to="t.to"
                   class="mt-item" :class="{ active: isTabActive(t) }">
        <span class="mt-emoji">{{ t.icon }}</span>
        <span>{{ t.label }}</span>
        <span v-if="t.badge && unreadAlerts > 0" class="mt-dot" :title="`${unreadAlerts} 条未读关怀提醒`"></span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resetAdminCheck } from '../router'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import { AVATAR_FRAMES, FRAME_SCALES } from '@/lib/avatarFrames'
import { feedbackWS, notifyFeedback } from '../utils/ws'
import { clearUserPrefs } from '../utils/userPrefs'
import { useUserStore } from '../stores/user'
import { senseTracker } from '../utils/senseTracker'
import { getSenseConfig, getRiskOverview } from '../api/sense'
import RiskAlertDialog from '../components/RiskAlertDialog.vue'

const route = useRoute()
const router = useRouter()
const showBar = ref(false)
let hideTimer = null

// 顶部导航栏 hover 控制（带延迟，防止闪烁）
const onHoverZoneEnter = () => {
  if (hideTimer) { clearTimeout(hideTimer); hideTimer = null }
  showBar.value = true
}
const onHoverZoneLeave = () => {
  // 延迟 200ms 隐藏，给用户时间移到导航栏上
  hideTimer = setTimeout(() => {
    showBar.value = false
    hideTimer = null
  }, 200)
}
const onBarEnter = () => {
  if (hideTimer) { clearTimeout(hideTimer); hideTimer = null }
  showBar.value = true
}
const onBarLeave = () => {
  hideTimer = setTimeout(() => {
    showBar.value = false
    hideTimer = null
  }, 200)
}

// ===== 登录态（状态源是 Pinia store，天然响应式，无需再监听 storage 事件）=====
const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn)
const isAdmin = computed(() => {
  const r = userInfo.value?.role
  return r === 1 || r === '1' || r === 'admin'
})
const DEFAULT_AVATAR = '/img/默认管理员头像.png'
const topAvatarUrl = computed(() => {
  const a = userInfo.value?.avatar
  if (!a) return DEFAULT_AVATAR
  return (a.startsWith('http') || a.startsWith('/')) ? a : DEFAULT_AVATAR
})
const onAvatarError = (e) => { e.target.src = DEFAULT_AVATAR }

// 头像装饰框（和个人中心同步）
const frameNameMap = { frame1: 'flower', frame2: 'leaf', frame3: 'crown', frame4: 'star', frame5: 'heart', frame6: 'halo', frame7: 'cloud' }
// 优先从 userInfo（后端数据）读取，其次从 localStorage 读取
const _getInitFrame = () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (info?.avatarFrame) {
    const f = frameNameMap[info.avatarFrame] || info.avatarFrame
    localStorage.setItem('profile_frame', f)
    return f
  }
  const raw = localStorage.getItem('profile_frame') || 'none'
  const migrated = frameNameMap[raw] || raw
  if (frameNameMap[raw]) localStorage.setItem('profile_frame', migrated)
  return migrated
}
const userFrame = ref(_getInitFrame())
// 头像框名单统一从 lib/avatarFrames.js 取（与个人中心/后台同一份，改名字只改一处）
const avatarFrames = AVATAR_FRAMES.map(f => ({ ...f, scale: FRAME_SCALES[f.name] }))
const currentFrameImg = computed(() => {
  const f = avatarFrames.find(f => f.name === userFrame.value)
  return f?.img || ''
})
const currentFrameScale = computed(() => {
  const f = avatarFrames.find(f => f.name === userFrame.value)
  return f?.scale || 120
})
// 监听 localStorage 变化（个人中心改了头像框后，这里实时同步）
// 注意：watch 无法监听 localStorage，改用 storage 事件 + 路由切换同步
const syncFromStorage = () => {
  // 兼容期：老代码可能直接写 localStorage，这里把 store 拉回一致
  userStore.syncFromStorage()
  const info = userStore.userInfo
  // 优先从 userInfo 读取头像框（后端数据为准）
  if (info?.avatarFrame) {
    const f = frameNameMap[info.avatarFrame] || info.avatarFrame
    userFrame.value = f
    localStorage.setItem('profile_frame', f)
    return
  }
  const raw = localStorage.getItem('profile_frame')
  if (raw) {
    const migrated = frameNameMap[raw] || raw
    userFrame.value = migrated
    if (frameNameMap[raw]) localStorage.setItem('profile_frame', migrated)
  }
}

const handleStorageChange = (e) => {
  if (e.key === 'profile_frame' || e.key === 'userInfo') {
    syncFromStorage()
  }
}

// 同标签页同步：Profile 页面保存后立即触发（storage 事件只在跨标签页触发）
const handleProfileUpdated = () => { syncFromStorage() }
const handleSenseChange = () => { syncSenseState() }
/** 用户在弹窗/卡片里点过「我知道了」→ 立刻把底部红点消掉 */
const handleRiskRead = () => { loadRiskBadge() }

// 每次路由切换都刷新登录态和用户信息（防止其他页修改后这里不更新）
watch(() => route.path, (p) => {
  syncFromStorage()
  // 行为采集：切换场景时结算上一个场景的停留时长
  senseTracker.onRouteChange(p)
})

// 退出登录
const doLogout = async () => {
  try {
    await jellyConfirm('确定要退出登录吗？', { title: '退出登录', confirmText: '退出', cancelText: '留下' })
    // 退出前先停掉行为采集，避免下一个账号继承上一个账号的采集状态
    senseTracker.disable()
    unreadAlerts.value = 0
    userStore.clearSession()
    resetAdminCheck()   // 清掉服务端 role 缓存，防止换号串号
    // 清掉个性化设置 + 断开推送，避免下一个账号继承上一个账号的状态
    clearUserPrefs()
    try { feedbackWS.disconnect() } catch {}
    userInfo.value = null
    ElMessage.success('已退出登录')
    router.replace('/login')
  } catch { /* 取消则不做任何事 */ }
}

// ===== 移动端底部导航 =====
const mobileTabs = [
  { to: '/main',      label: '主页', icon: '🏠', match: (p) => p === '/main' || p === '/' || p === '/home' },
  { to: '/diary',     label: '日记', icon: '📔', match: (p) => p.startsWith('/diary') },
  { to: '/assistant', label: 'AI',   icon: '💬', match: (p) => p.startsWith('/assistant') },
  { to: '/fm',        label: 'FM',   icon: '🎵', match: (p) => p.startsWith('/fm') },
  { to: '/profile',   label: '我的', icon: '👤', badge: true, match: (p) => p.startsWith('/profile') },
]
const isTabActive = (t) => t.match(route.path)

/** 未读关怀提醒数（情绪风险预警，非医疗诊断） */
const unreadAlerts = ref(0)

async function loadRiskBadge() {
  if (!isLoggedIn.value) { unreadAlerts.value = 0; return }
  try {
    const res = await getRiskOverview()
    unreadAlerts.value = Number(res?.data?.unread || 0)
  } catch { unreadAlerts.value = 0 }
}

// ===== 无感记录：跟着用户的开关决定要不要采集 =====
// 这是 privacy-by-design 的落地点：开关关着时，连采集器都不会挂载。
async function syncSenseState(path = route.path) {
  if (!isLoggedIn.value) { senseTracker.disable(); return }
  try {
    const cfg = await getSenseConfig()
    const d = cfg?.data || {}
    const allow = d.featureEnabled && d.enabled === 1 && d.behavior === 1
    if (allow) senseTracker.enable(path)
    else senseTracker.disable()
  } catch {
    // 取不到配置 / 未登录：一律按关闭处理（fail-safe）
    senseTracker.disable()
  }
}

let wsTimer = null

// ===== 全局 WebSocket 通知 =====
const initWS = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return

  // 干净重连
  try { feedbackWS.disconnect() } catch {}
  await new Promise(r => setTimeout(r, 200))

  feedbackWS.connect()

  notifyFeedback('reply', () => {
    // 刷新 Feedback.vue 里用户自己的列表
    window.__feedbackPageLoad?.()
  })
}

// 切换标签页回来时同步一次本地状态
// 必须具名：匿名函数无法在 onBeforeUnmount 里 removeEventListener，会一直挂在 document 上
function handleVisibilityChange() {
  if (!document.hidden) syncFromStorage()
}

onMounted(() => {
  initWS()
  // 给底部 Tab 栏留出空间（只在手机上真的占位，见 mobile.css）
  document.body.classList.add('has-mobile-tabbar')
  loadRiskBadge()
  syncSenseState()
  // 同标签页监听：用户在个人中心改了开关后立即生效，不用刷新
  window.addEventListener('sense-config-changed', handleSenseChange)
  window.addEventListener('risk-alert-read', handleRiskRead)
  // 监听 localStorage 变化（跨页面同步头像、头像框、用户信息）
  window.addEventListener('storage', handleStorageChange)
  // 监听同标签页内的自定义事件（Profile 保存后立即生效）
  window.addEventListener('profile-updated', handleProfileUpdated)
  // 页面可见时刷新（切换标签页回来时同步）
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  if (wsTimer) clearInterval(wsTimer)
  if (hideTimer) clearTimeout(hideTimer)
  // 卸载即停止采集：保证「开关关闭」是真正的关闭，而不是仅仅不再上报
  senseTracker.disable()
  document.body.classList.remove('has-mobile-tabbar')
  window.removeEventListener('sense-config-changed', handleSenseChange)
  window.removeEventListener('risk-alert-read', handleRiskRead)
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('profile-updated', handleProfileUpdated)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  // 不要调 feedbackWS.disconnect()！WS 是全局单例，AdminLayout 也在用。
})

// 原来的 navItems 保留在下面

// 是否主页（主页不显示返回按钮）
const isHome = computed(() => route.path === '/main' || route.path === '/')

// 统一的导航栏菜单项（顶部导航栏）
const navItems = [
  { id: 'home',    to: '/main',      label: '主页' },
  { id: 'diary',   to: '/diary',     label: '绪见日记' },
  { id: 'ai',      to: '/assistant', label: '绪见AI' },
  { id: 'test',    to: '/test',      label: '绪见测试' },
  { id: 'fm',      to: '/fm',        label: '绪见FM' },
  { id: 'class',   to: '/classroom', label: '绪见学堂' },
  { id: 'feedback',to: '/feedback',  label: '反馈建议' },
  { id: 'board',   to: '/databoard', label: '数据看板' },
  { id: 'profile', to: '/profile',   label: '个人中心' },
]
</script>

<style scoped>
/* 这里不放任何包裹样式，让页面自己决定布局 */
/* 导航栏都是 fixed 定位，不占用文档流 */

/* 顶部导航栏头像装饰框 */
.top-avatar-wrap {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: visible;
  position: relative;
  flex-shrink: 0;
}

.top-avatar-wrap .user-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  background: var(--c-bg2);
  position: relative;
  z-index: 1;
}

.top-avatar-wrap .frame-ring-img {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 3;
  object-fit: contain;
  display: block;
}
</style>