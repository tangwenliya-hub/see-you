<template>
  <div class="app-layout-wrap">
    <!-- 顶部导航栏：常驻固定在顶部（PC 与手机一致，不再 hover 滑出） -->
    <header class="app-bar">
      <!-- 手机端菜单按钮（PC 端由 mobile.css 隐藏）：打开左侧抽屉 -->
      <button class="m-drawer-btn" type="button" aria-label="打开菜单" @click.stop="drawerOpen = true">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 7h16" /><path d="M4 12h16" /><path d="M4 17h16" />
        </svg>
      </button>

      <!-- 返回按钮（除了主页 /main 都显示） -->
      <router-link v-if="!isHome" to="/main" class="back-btn" title="返回主页">
        <AppIcon name="arrow-left" /> 返回
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
        <span v-if="userInfo" class="nav-greeting">你好！{{ userInfo.nickname || userInfo.username || '用户' }} </span>
        <router-link to="/profile" class="user-info" title="个人中心">
          <div class="top-avatar-wrap" :class="['frame-' + userFrame]">
            <img :src="topAvatarUrl" class="user-avatar" @error="onAvatarError" />
            <img v-if="currentFrameImg" :src="currentFrameImg" class="frame-ring-img" :style="{ width: currentFrameScale + '%', height: currentFrameScale + '%' }" @error="$event.target.style.display='none'" />
          </div>
          <span v-if="isAdmin" class="role-badge" title="管理员"><AppIcon name="crown" /></span>
        </router-link>
        <button class="auth-btn logout" @click="doLogout">退出</button>
      </div>
    </header>

    <!-- 页面内容容器：手机端从左边缘右滑返回时整页跟着手指平移
         （PC 端 display:contents 不产生盒子，布局与改动前完全一致） -->
    <div ref="pageWrapRef" class="page-wrap">
      <slot />
    </div>

    <!-- ===== 手机端左侧抽屉菜单（PC 端由 mobile.css 整体 display:none）=====
         底部 Tab 只放得下 5 个主入口，测试/学堂/数据看板/反馈这些次要功能
         收进抽屉里：左上角按钮打开，点旁边遮罩关闭。 -->
    <transition name="m-mask">
      <div v-if="drawerOpen" class="m-drawer-mask" @click="drawerOpen = false"></div>
    </transition>
    <aside class="m-drawer" :class="{ open: drawerOpen }" aria-label="更多功能">
      <div class="m-drawer-head">
        <div class="m-dh-avatar">
          <img :src="topAvatarUrl" class="m-dh-face" alt="" @error="onAvatarError" />
          <!-- 头像装饰框：与顶栏/个人中心同一份数据源，改框这里实时同步 -->
          <img v-if="currentFrameImg" :src="currentFrameImg" class="m-dh-frame"
               :style="{ width: currentFrameScale + '%', height: currentFrameScale + '%' }"
               @error="$event.target.style.display='none'" />
        </div>
        <div class="m-dh-info">
          <div class="m-dh-name">{{ userInfo ? (userInfo.nickname || userInfo.username || '用户') : '未登录' }}</div>
          <div class="m-dh-sub">{{ isAdmin ? '管理员' : (isLoggedIn ? '世界和我爱着你' : '登录后同步你的记录') }}</div>
        </div>
        <button class="m-dh-close" type="button" aria-label="关闭菜单" @click="drawerOpen = false">
          <AppIcon name="close" />
        </button>
      </div>

      <div class="m-drawer-body">
        <div class="m-dh-sec">探索更多</div>
        <router-link v-for="item in drawerLinks" :key="item.to" :to="item.to"
                     class="m-drawer-item" :class="{ active: route.path === item.to }">
          <img :src="item.icon" alt="" />
          <span class="m-di-label">{{ item.label }}</span>
          <span class="m-di-desc">{{ item.desc }}</span>
        </router-link>

        <div class="m-dh-sec">账号</div>
        <router-link to="/profile" class="m-drawer-item" :class="{ active: route.path === '/profile' }">
          <img src="/img/开心牛牛.png" alt="" />
          <span class="m-di-label">个人中心</span>
          <span v-if="unreadAlerts > 0" class="m-di-dot" :title="`${unreadAlerts} 条未读关怀提醒`"></span>
          <span class="m-di-desc">资料 · 主题 · 提醒</span>
        </router-link>
        <template v-if="!isLoggedIn">
          <router-link to="/login" class="m-drawer-item">
            <img src="/img/登入.jpg" alt="" />
            <span class="m-di-label">登入 / 注册</span>
          </router-link>
        </template>
        <button v-else type="button" class="m-drawer-item m-di-danger" @click="onDrawerLogout">
          <img src="/img/账号安全.png" alt="" />
          <span class="m-di-label">退出登录</span>
        </button>
      </div>

      <div class="m-drawer-foot">绪见 · 世界和我爱着你</div>
    </aside>

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
        <span class="mt-emoji"><img :src="t.icon" alt="" /></span>
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
import { useSwipeBack } from '../composables/useSwipeBack'
import RiskAlertDialog from '../components/RiskAlertDialog.vue'

const route = useRoute()
const router = useRouter()

// 手机端「左边缘右滑返回」：页面容器跟着手指走，过半返回、不过半弹回
const pageWrapRef = ref(null)
useSwipeBack(pageWrapRef)

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
/** 用户在弹窗/卡片里点过「我知道了」 立刻把底部红点消掉 */
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
// 个人中心不放在底部 Tab：抽屉里有「个人中心」入口，避免重复
const mobileTabs = [
  { to: '/main',      label: '主页', icon: '/img/绪见后台管理.png', match: (p) => p === '/main' || p === '/' || p === '/home' },
  { to: '/diary',     label: '日记', icon: '/img/我的日记本.png', match: (p) => p.startsWith('/diary') },
  { to: '/assistant', label: 'AI',   icon: '/img/AI对话历史.png', match: (p) => p.startsWith('/assistant') },
  { to: '/fm',        label: 'FM',   icon: '/img/治愈音频.png', match: (p) => p.startsWith('/fm') },
]
const isTabActive = (t) => t.match(route.path)

// ===== 手机端左侧抽屉菜单 =====
// 底部 Tab 放不下的次要功能收进抽屉，避免顶栏堆一排入口（手机顶栏只有 52px）
const drawerOpen = ref(false)
const drawerLinks = [
  { to: '/test',      label: '绪见测试', icon: '/img/测评量表.png', desc: '量表 · 报告' },
  { to: '/classroom', label: '绪见学堂', icon: '/img/心理视频.png', desc: '视频 · 课程' },
  { to: '/databoard', label: '数据看板', icon: '/img/数据大盘.png', desc: '情绪 · 趋势' },
  { to: '/feedback',  label: '反馈建议', icon: '/img/我的反馈.png', desc: '问题 · 想法' },
]

/** 抽屉里的退出：复用顶栏的确认弹窗逻辑，点完自动收起抽屉 */
const onDrawerLogout = async () => {
  drawerOpen.value = false
  await doLogout()
}

// 抽屉打开时锁住页面滚动，关闭/切页时立刻收起（点条目跳转后不该还挂在屏幕上）
watch(drawerOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})
watch(() => route.path, () => {
  if (drawerOpen.value) drawerOpen.value = false
})

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
  // 抽屉占用的滚动锁要还回去，否则离开布局后整站都滚不动
  document.body.style.overflow = ''
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