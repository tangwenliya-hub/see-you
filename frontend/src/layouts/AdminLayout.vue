<template>
  <div class="admin-wrap">
    <!-- 左侧果冻菜单 -->
    <div v-if="drawerOpen" class="admin-mask" @click="closeDrawer"></div>
    <aside class="admin-side" :class="{ hidden: collapsed && !drawerOpen, drawer: drawerOpen }">
      <div class="side-logo">
        <span class="logo-emoji"><img src="/img/绪见后台管理.png" alt=""></span>
        <span class="logo-text">绪见后台管理</span>
        <!-- 抽屉模式下：点图钉恢复常驻侧栏 -->
        <button v-if="drawerOpen" class="pin-btn" title="固定侧栏" @click="pinSide"><img src="/img/固定侧栏.png" alt="固定侧栏" /></button>
      </div>
      <nav class="side-menu">
        <template v-for="g in menuGroups" :key="g.title">
          <!-- 分组标题可折叠 -->
          <button class="group-head" :class="{ open: isOpen(g) }" @click="toggleGroup(g)">
            <span class="g-title">{{ g.title }}</span>
            <span class="g-count">{{ g.items.length }}</span>
            <span class="g-arrow">▾</span>
          </button>
          <router-link
            v-for="item in (isOpen(g) ? g.items : [])"
            :key="item.path"
            :to="'/admin/' + item.path"
            class="menu-item"
            active-class="active"
            @click="onMenuClick"
          >
            <span class="m-icon">
              <img v-if="item.isImg" :src="item.icon" class="m-icon-img" style="width:48px;height:48px;object-fit:cover;display:block;" />
              <template v-else>{{ item.icon }}</template>
            </span>
            <span class="m-label">{{ item.label }}</span>
          </router-link>
        </template>
      </nav>
    </aside>

    <!-- 右侧主体 -->
    <div class="admin-body">
      <header class="admin-top">
        <div class="top-left">
          <button class="collapse-btn" :title="collapsed ? '展开菜单' : '收起菜单'" @click="toggleSide"><img src="/img/侧边下拉框.png" /></button>
          <div class="crumb">后台管理 / {{ currentTitle }}</div>
        </div>
        <div class="top-right">
          <!-- 管理员用户区：可点击弹出个人中心 -->
          <div class="admin-user clickable" @click="showProfile = true">
            <div class="avatar avatar-framed">
              <img :src="adminInfo.avatar || '/img/默认管理员头像.png'" alt="头像">
              <img v-if="frameMeta && frameMeta.img" class="avatar-frame-ring" :src="frameMeta.img" alt=""
                   :style="{ width: frameMeta.scale + '%', height: frameMeta.scale + '%' }"
                   @error="$event.target.style.display='none'" />
            </div>
            <div class="u-info">
              <div class="u-name">{{ adminInfo.nickname || adminInfo.username || '管理员' }}</div>
              <div class="u-role">{{ adminInfo.role === 1 ? '超级管理员' : '管理员' }}</div>
            </div>
            <button class="logout-btn" @click.stop="logout">退出</button>
          </div>
        </div>
      </header>

      <main class="admin-main">
        <router-view />
      </main>

      <!-- 🆕 管理员个人中心弹窗（跟随主题色，与前台个人中心同风格） -->
      <div v-if="showProfile" class="modal-mask" @click.self="closeProfile">
        <div class="profile-modal">
          <!-- 顶部用户卡 -->
          <div class="pm-hero">
            <div class="pm-hero-avatar">
              <img :src="form.avatar || '/img/默认管理员头像.png'" />
            </div>
            <div class="pm-hero-info">
              <p class="pm-hero-name">
                {{ form.nickname || form.username || '管理员' }}
                <span class="pm-hero-tag">{{ adminInfo.role === 1 ? '超级管理员' : '管理员' }}</span>
              </p>
              <p class="pm-hero-sub">{{ form.username || '—' }} · 后台管理账号</p>
            </div>
            <button class="pm-close" @click="closeProfile">✕</button>
          </div>

          <!-- 胶囊 Tab -->
          <div class="pm-tabs">
            <button :class="['pm-tab', profileTab==='basic'?'on':'']" @click="profileTab='basic'">📋 基本资料</button>
            <button :class="['pm-tab', profileTab==='theme'?'on':'']" @click="profileTab='theme'">🎨 个性化</button>
            <button :class="['pm-tab', profileTab==='pwd'?'on':'']" @click="profileTab='pwd'">🔐 修改密码</button>
          </div>

          <!-- Tab1：基本资料 -->
          <div v-show="profileTab==='basic'" class="pm-panel">
            <div class="pm-avatar-row">
              <div class="pm-avatar">
                <img :src="form.avatar || '/img/默认管理员头像.png'" />
              </div>
              <div class="pm-avatar-meta">
                <p class="pm-avatar-tip">支持 JPG / PNG，上传后记得点「保存资料」</p>
                <label class="pm-avatar-btn">
                  📷 上传头像
                  <input type="file" accept="image/*" @change="onAvatarUpload" hidden />
                </label>
              </div>
            </div>
            <div class="pm-grid">
              <div class="pm-field">
                <label>昵称（展示名）</label>
                <input v-model="form.nickname" placeholder="给改个昵称吧~" />
                <p class="pm-uname-hint">登录账号：{{ form.username }}（不可修改）</p>
              </div>
              <div class="pm-field">
                <label>手机号</label>
                <input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
              </div>
              <div class="pm-field">
                <label>邮箱</label>
                <input v-model="form.email" placeholder="your@email.com" />
              </div>
              <div class="pm-field full">
                <label>个人简介</label>
                <textarea v-model="form.bio" rows="3" placeholder="写点什么介绍自己吧..."></textarea>
              </div>
            </div>
            <div class="pm-actions">
              <button class="pm-save" :disabled="saving" @click="saveProfile">{{ saving ? '保存中...' : '💾 保存资料' }}</button>
            </div>
          </div>

          <!-- Tab3：个性化设置 -->
          <div v-show="profileTab==='theme'" class="pm-panel">
            <div class="pm-field">
              <label>🎨 主题色</label>
              <div class="pm-dots">
                <button v-for="c in themeColors" :key="c.name"
                        :class="['pm-dot', { on: form.theme === c.name }]"
                        :style="{ background: c.hex }"
                        @click="form.theme = c.name"
                        :title="c.label">
                  <span v-if="form.theme === c.name" class="pm-dot-check">✓</span>
                </button>
              </div>
            </div>
            <div class="pm-field">
              <label>🌙 深色模式</label>
              <div class="pm-dark-row">
                <el-switch v-model="form.darkMode" />
                <span class="pm-dark-tip">{{ form.darkMode ? '已开启深色模式' : '使用浅色模式' }}</span>
              </div>
            </div>
            <div class="pm-field">
              <label>🖼️ 头像装饰框</label>
              <div class="pm-frame-grid">
                <button v-for="f in avatarFrameOptions" :key="f.name"
                        :class="['pm-frame-opt', { on: form.avatarFrame === f.name }]"
                        :title="f.label"
                        @click="form.avatarFrame = f.name">
                  <span class="pm-frame-preview">
                    <img class="pm-frame-base" :src="form.avatar || '/img/默认管理员头像.png'" alt="" />
                    <img v-if="f.img" class="pm-frame-ring" :src="f.img" alt=""
                         :style="{ width: f.scale + '%', height: f.scale + '%' }"
                         @error="$event.target.style.display='none'" />
                  </span>
                  <span class="pm-frame-name">{{ f.label }}</span>
                </button>
              </div>
            </div>
            <div class="pm-actions">
              <button class="pm-save" :disabled="saving" @click="saveTheme">{{ saving ? '保存中...' : '💾 保存设置' }}</button>
            </div>
          </div>

          <!-- Tab2：修改密码 -->
          <div v-show="profileTab==='pwd'" class="pm-panel">
            <div class="pm-grid single-col">
              <div class="pm-field">
                <label>旧密码</label>
                <input v-model="pwdForm.oldPassword" type="password" placeholder="输入当前密码" />
              </div>
              <div class="pm-field">
                <label>新密码</label>
                <input v-model="pwdForm.newPassword" type="password" placeholder="至少 6 位" />
              </div>
              <div class="pm-field">
                <label>确认新密码</label>
                <input v-model="pwdForm.confirm" type="password" placeholder="再输一次新密码" />
              </div>
            </div>
            <p class="pm-pwd-tip">🔒 密码至少 6 位，修改成功后请牢记新密码</p>
            <div class="pm-actions">
              <button class="pm-save" :disabled="pwdSaving" @click="changePwd">{{ pwdSaving ? '提交中...' : '🔑 确认修改' }}</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resetAdminCheck } from '../router'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import { AVATAR_FRAMES, FRAME_SCALES } from '@/lib/avatarFrames'
import { feedbackWS, notifyFeedback } from '../utils/ws'
import { riskSSE, notifyRiskAlert, notifyRiskMisreport } from '../utils/sse'
import { getUserById, updateUser, changePassword, uploadFile } from '../api'
import { useUserStore } from '../stores/user'
import '../styles/admin.css'

let wsTimer = null

// ===== 后台全局 WebSocket 通知 =====
const initWS = async () => {
  const info = useUserStore().userInfo
  if (!info) return

  // 关掉旧连接，重新连（避免脏状态）
  try { feedbackWS.disconnect() } catch {}
  await new Promise(r => setTimeout(r, 200))

  feedbackWS.connect()

  notifyFeedback('new_feedback', () => {
    window.__feedbacksLoadData?.()
  })
  notifyFeedback('user_supplement', () => {
    window.__feedbacksLoadData?.()
  })

  // ===== 情绪预警 SSE 实时通道（管理端）：有用户触发预警立刻弹通知 =====
  riskSSE.disconnect()   // 防止换号后复用旧 token 的连接
  riskSSE.connect()
  notifyRiskAlert(() => {
    window.__alertsLoadData?.()   // 预警管理页开着就同步刷新
  })
  // 用户标「这不是我」→ 低打扰通知 + 刷新列表（管理员据此判断要不要回访）
  notifyRiskMisreport(() => {
    window.__alertsLoadData?.()
  })
}

onMounted(() => {
  loadAdminInfo()  // 🆕 页面加载时拉管理员信息
  initWS()
  // 窄屏（手机）下默认收起侧栏，点按钮临时滑出
  if (window.innerWidth <= 768) collapsed.value = true
  window.addEventListener('resize', onResize)
  window.addEventListener('keydown', onKey)
})

const onResize = () => {
  if (window.innerWidth <= 768) collapsed.value = true
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('keydown', onKey)
  if (wsTimer) clearInterval(wsTimer)
  // 注意：不要调 feedbackWS.disconnect()！
  // WS 是全局单例，AppLayout 也在用，路由切换时不能关。
  // 只有用户手动退出登录时才应该断开（见 logout 方法）。
})

const route = useRoute()
const router = useRouter()

// collapsed：侧栏已收起（完全消失，内容铺满全屏）
// drawerOpen：收起状态下临时滑出整条侧栏（选完菜单自动收回）
const collapsed = ref(false)
const drawerOpen = ref(false)

const toggleSide = () => {
  if (!collapsed.value) {
    // 常驻 → 收起：整条侧栏消失
    collapsed.value = true
    drawerOpen.value = false
  } else {
    // 收起 → 临时滑出；再点一次收起
    drawerOpen.value = !drawerOpen.value
  }
  try { localStorage.setItem('adminSideCollapsed', collapsed.value ? '1' : '0') } catch {}
}
const closeDrawer = () => { drawerOpen.value = false }
// 抽屉里点图钉：恢复常驻侧栏
const pinSide = () => { collapsed.value = false; drawerOpen.value = false; try { localStorage.setItem('adminSideCollapsed', '0') } catch {} }
// 抽屉里点菜单：跳转后自动收回
const onMenuClick = () => { if (drawerOpen.value) drawerOpen.value = false }
const onKey = (e) => { if (e.key === 'Escape') drawerOpen.value = false }
// 记住上次收起状态
try { if (localStorage.getItem('adminSideCollapsed') === '1') collapsed.value = true } catch {}

// 侧栏按职能分组：21 项平铺太长，收起后只剩 7 行分组标题 + 当前组内容
const menuGroups = [
  { title: '总览', icon: '/img/数据大盘.png', items: [
    { path: 'dashboard', icon: '/img/数据大盘.png', label: '数据大盘', isImg: true },
  ]},
  { title: '内容管理', icon: '/img/心理视频.png', items: [
    { path: 'videos',   icon: '/img/心理视频.png', label: '心理视频', isImg: true },
    { path: 'audios',   icon: '/img/治愈音频.png', label: '治愈音频', isImg: true },
    { path: 'tests',    icon: '/img/测评量表.png', label: '测评量表', isImg: true },
    { path: 'keywords', icon: '/img/AI关键词库.png', label: 'AI 关键词库', isImg: true },
    { path: 'banner',   icon: '/img/心理视频.png', label: '轮播公告', isImg: true },
  ]},
  { title: '社区审核', icon: '/img/评论审核.png', items: [
    { path: 'comments',  icon: '/img/评论审核.png', label: '评论审核', isImg: true },
    { path: 'diaries',   icon: '/img/日记管理.png', label: '日记管理', isImg: true },
    { path: 'feedbacks', icon: '/img/用户反馈.png', label: '用户反馈', isImg: true },
  ]},
  { title: '用户管理', icon: '/img/用户管理.png', items: [
    { path: 'users',   icon: '/img/用户管理.png', label: '用户管理', isImg: true },
    { path: 'avatars', icon: '/img/头像审核.png', label: '头像审核', isImg: true },
  ]},
  { title: '情绪洞察', icon: '/img/情绪预警后台图标.png', items: [
    // 👇 三大创新模块的后台管理端
    { path: 'alerts',  icon: '/img/情绪预警后台图标.png', label: '情绪预警', isImg: true },
    { path: 'sense',   icon: '/img/无感记录后台图标.png', label: '无感记录', isImg: true },
    { path: 'lexicon', icon: '/img/情绪词典.png', label: '情绪词典', isImg: true },
  ]},
  { title: '使用记录', icon: '/img/AI 对话.png', items: [
    { path: 'aichat',       icon: '/img/AI 对话.png', label: 'AI 对话', isImg: true },
    { path: 'records',      icon: '/img/测评记录.png', label: '测评记录', isImg: true },
    { path: 'interaction',  icon: '/img/互动统计.png', label: '互动统计', isImg: true },
  ]},
  { title: '系统与安全', icon: '/img/账号安全.png', items: [
    { path: 'security', icon: '/img/账号安全.png', label: '账号安全', isImg: true },
    { path: 'settings', icon: '/img/系统参数.png', label: '系统参数', isImg: true },
    { path: 'logs',     icon: '/img/操作日志.png', label: '操作日志', isImg: true },
    { path: 'privacy',  icon: '/img/隐私审计.png', label: '隐私审计', isImg: true },
  ]},
]

// 扁平列表：仅用于面包屑标题查找
const menus = menuGroups.flatMap(g => g.items)

// ===== 分组展开/收起 =====
const openGroups = ref(new Set(['总览']))
const isOpen = (g) => openGroups.value.has(g.title)
const toggleGroup = (g) => {
  const s = new Set(openGroups.value)
  if (s.has(g.title)) s.delete(g.title)
  else s.add(g.title)
  openGroups.value = s
  try { localStorage.setItem('adminMenuOpen', JSON.stringify([...s])) } catch {}
}

// ===== 抽屉模式：按 Esc 关闭 =====
watch(() => route.path, () => { drawerOpen.value = false })
// 恢复上次展开的分组
try {
  const saved = JSON.parse(localStorage.getItem('adminMenuOpen') || 'null')
  if (Array.isArray(saved) && saved.length) openGroups.value = new Set(saved)
} catch {}
// 进入某页面时自动展开它所属的分组
const expandGroupOf = (path) => {
  const g = menuGroups.find(g => g.items.some(i => path.includes('/' + i.path)))
  if (g && !openGroups.value.has(g.title)) {
    const s = new Set(openGroups.value); s.add(g.title); openGroups.value = s
  }
}
expandGroupOf(route.path)
watch(() => route.path, (p) => expandGroupOf(p))

const currentTitle = computed(() => {
  const match = menus.find(m => route.path.includes('/' + m.path))
  return match ? match.label : '数据大盘'
})

// ===== 🆕 管理员个人中心 =====
const adminInfo = ref({})       // 当前管理员（完整信息）
const showProfile = ref(false)
const profileTab = ref('basic')
const saving = ref(false)
// 顶栏头像装饰框（弹窗保存后即时更新）
const adminFrame = ref('none')
const frameMeta = computed(() => avatarFrameOptions.find(f => f.name === adminFrame.value) || null)
const pwdSaving = ref(false)

const form = ref({ username:'', nickname:'', avatar:'', phone:'', email:'', bio:'', theme:'green', darkMode:false, avatarFrame:'none' })
const pwdForm = ref({ oldPassword:'', newPassword:'', confirm:'' })

// 8种主题色
// 后台专用主题色板（与用户端 App.vue 的 ALL_THEMES 同名同色，但独立存储，互不影响）
// 必须带 main/bg1/bg2：CSS 里大量 color-mix(var(--c-main)) 和背景变量依赖它们
const themeColors = [
  { name: 'green',  label: '薄荷绿', hex: '#7bc47b', main: '#90db90', bg1: '#f6f9f6', bg2: '#f0f7f0' },
  { name: 'blue',   label: '天空蓝', hex: '#5b9bd5', main: '#7cb8ea', bg1: '#f2f7fc', bg2: '#eaf2fa' },
  { name: 'purple', label: '浪漫紫', hex: '#9b7bd5', main: '#b8a0e6', bg1: '#f6f3fb', bg2: '#ede8f7' },
  { name: 'pink',   label: '少女粉', hex: '#e59bb3', main: '#f2b8cd', bg1: '#fcf4f7', bg2: '#fae8ef' },
  { name: 'orange', label: '暖阳橙', hex: '#e69b5b', main: '#f2b87c', bg1: '#fdf6ef', bg2: '#fae8d9' },
  { name: 'teal',   label: '青碧',   hex: '#5bb8a6', main: '#7cd4c3', bg1: '#f2faf8', bg2: '#eaf5f2' },
  { name: 'rose',   label: '玫瑰红', hex: '#d96b7b', main: '#ec8c9a', bg1: '#fcf2f4', bg2: '#fae6e9' },
  { name: 'indigo', label: '靛青',   hex: '#6b7bd9', main: '#8c9aec', bg1: '#f2f4fc', bg2: '#e6e9fa' },
]

// 头像框选项：名单/名字统一从 lib/avatarFrames.js 取（与前台个人中心同一份，改名字只改一处）
const avatarFrameOptions = AVATAR_FRAMES.map(f => ({ ...f, scale: FRAME_SCALES[f.name] }))

// 登录时 localStorage 存的 userInfo 只有基础字段，要拉完整信息
const loadAdminInfo = async () => {
  const brief = useUserStore().userInfo || {}
  if (!brief.userId) return
  try {
    const res = await getUserById(brief.userId)
    if (res.code === 200 && res.data) {
      adminInfo.value = res.data
      // 初始化 form
      form.value.username   = res.data.username || ''
      form.value.nickname   = res.data.nickname || ''
      form.value.avatar     = res.data.avatar   || ''
      form.value.phone      = res.data.phone    || ''
      form.value.email      = res.data.email    || ''
      form.value.bio        = res.data.bio      || ''
      form.value.theme      = res.data.theme    || 'green'
      form.value.darkMode   = res.data.darkMode === 1 || res.data.darkMode === true
      form.value.avatarFrame = res.data.avatarFrame || 'none'
      adminFrame.value = form.value.avatarFrame
    }
  } catch { console.warn('[AdminLayout] 拉取管理员信息失败') }
}

const closeProfile = () => {
  showProfile.value = false
  // 改密码成功后清空
  pwdForm.value = { oldPassword:'', newPassword:'', confirm:'' }
  loadAdminInfo() // 关闭时刷新一下（万一保存了）
}

// 保存基本资料
const saveProfile = async () => {
  if (!form.value.nickname?.trim()) { ElMessage.warning('昵称不能为空'); return }
  saving.value = true
  try {
    await updateUser({ userId: adminInfo.value.userId, ...form.value })
    ElMessage.success('资料已保存 ✅')
    // 同步登录态（store 负责同时更新内存状态与持久化）
    useUserStore().patchUserInfo({ nickname: form.value.nickname })
    adminInfo.value.nickname = form.value.nickname
    adminInfo.value.avatar   = form.value.avatar
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 保存个性化设置
const saveTheme = async () => {
  saving.value = true
  try {
    await updateUser({
      userId: adminInfo.value.userId,
      theme: form.value.theme,
      darkMode: form.value.darkMode ? 1 : 0,
      avatarFrame: form.value.avatarFrame,
    })
    ElMessage.success('设置已保存 ✅')
    useUserStore().patchUserInfo({
      theme: form.value.theme,
      darkMode: form.value.darkMode ? 1 : 0,
      avatarFrame: form.value.avatarFrame,
    })
    // 后台主题独立存储（admin_theme/admin_dark），不覆盖用户端的 profile_theme
    // App.vue 监听 admin-theme-updated / admin_theme，保存后后台即时生效
    const t = themeColors.find(c => c.name === form.value.theme) || themeColors[0]
    localStorage.setItem('admin_theme', JSON.stringify(t))
    localStorage.setItem('admin_dark', form.value.darkMode ? '1' : '0')
    adminFrame.value = form.value.avatarFrame   // 顶栏头像框即时生效
    localStorage.setItem('profile_frame', form.value.avatarFrame)
    window.dispatchEvent(new Event('admin-theme-updated'))
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 修改密码
const changePwd = async () => {
  const f = pwdForm.value
  if (!f.oldPassword || !f.newPassword || !f.confirm) return ElMessage.warning('请填写完整')
  if (f.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (f.newPassword !== f.confirm) return ElMessage.warning('两次新密码不一致')
  pwdSaving.value = true
  try {
    await changePassword({ userId: adminInfo.value.userId, oldPassword:f.oldPassword, newPassword:f.newPassword })
    ElMessage.success('密码已修改 ✅ 请牢记新密码')
    pwdForm.value = { oldPassword:'', newPassword:'', confirm:'' }
  } catch {
    ElMessage.error('修改失败（旧密码可能不对）')
  } finally {
    pwdSaving.value = false
  }
}

// 头像上传
const onAvatarUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    ElMessage.info('正在上传头像...')
    const res = await uploadFile(file, 'avatar')
    if (res.code === 200) {
      form.value.avatar = res.data.url || res.data
      adminInfo.value.avatar = form.value.avatar
      ElMessage.success('头像已上传（别忘了点保存）')
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch {
    ElMessage.error('上传失败，请重试')
  }
  e.target.value = '' // 允许选同一个文件
}

const logout = async () => {
  if (!(await jellyConfirm('确定要退出登录吗？', { title: '退出登录', confirmText: '退出', cancelText: '留下' }))) return
  feedbackWS.disconnect()  // 退出登录时才真正断开
  riskSSE.disconnect()     // 预警 SSE 同样只在登出时断开（路由切换由单例保持）
  useUserStore().clearSession()
  resetAdminCheck()   // 清掉服务端 role 缓存，防止换号串号
  router.push('/login')
}
</script>