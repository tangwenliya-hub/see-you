import { createRouter, createWebHistory } from 'vue-router'
import axios from 'axios'
import AdminLayout from '../layouts/AdminLayout.vue'
import { useUserStore } from '../stores/user'

// 与 src/api/index.js 同一套前缀约定：dev=/api（Vite 代理），prod=空（同源直连）
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'

/**
 * 面包屑元数据：数组长度 = 页面层级（1=首页，2=二级页，3=三级页…）。
 * AnimeHero 顶部横幅读取该 meta，层级 > 2 时自动显示可点击路径（首页 > 日记 > 写日记）。
 * 每一级：label=显示文字，to=点击跳转目标；最后一级是当前页，不挂 to。
 *
 * listPath：这条内容「所属的列表页」。从通知/分享链接直接打开某个详情
 * （/fm?audioId=3、/test?testId=2、/diary?date=…）时，返回要回到列表而不是首页，
 * 见 utils/backNav.js 与本文件底部的「深链历史垫底」。
 */
const crumb = (items, listPath) => ({ meta: { crumbs: items, ...(listPath ? { listPath } : {}) } })

const routes = [
  { path: '/', component: () => import('../views/Home.vue'), alias: '/home' },   /* 打开即欢迎页（奶牛落地页）；已登录访问会自动跳 /main */
  { path: '/main', component: () => import('../views/Main.vue'), ...crumb([{ label: '首页' }]) },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/diary', component: () => import('../views/Diary.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '日记' }], '/diary') },
  { path: '/diary/write', component: () => import('../views/WriteDiary.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '日记', to: '/diary' }, { label: '写日记' }], '/diary') },
  { path: '/test', component: () => import('../views/Test.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '心理测评' }], '/test') },
  { path: '/assistant', component: () => import('../views/Assistant.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: 'AI 陪伴' }], '/assistant') },
  { path: '/fm', component: () => import('../views/FM.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '心理FM' }], '/fm') },
  { path: '/classroom', component: () => import('../views/Classroom.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '绪见学堂' }], '/classroom') },
  { path: '/profile', component: () => import('../views/Profile.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '个人中心' }], '/profile') },
  { path: '/databoard', component: () => import('../views/DataBoard.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '数据看板' }]) },
  { path: '/feedback', component: () => import('../views/Feedback.vue'), ...crumb([{ label: '首页', to: '/main' }, { label: '反馈与建议' }]) },

  // ===== 404 兜底：所有不存在的路径统一跳主页 =====
  { path: '/:pathMatch(.*)*', redirect: '/main' },

  // ===== 后台管理（果冻软萌风）=====
  {
    path: '/admin',
    component: AdminLayout,
    redirect: '/admin/dashboard',
    meta: { requiresAdmin: true },   // 进后台一律用服务端返回的 role 校验（见下方守卫）
    children: [
      { path: 'dashboard',  component: () => import('../views/admin/Dashboard.vue') },
      { path: 'users',      component: () => import('../views/admin/Users.vue') },
      { path: 'videos',     component: () => import('../views/admin/Videos.vue') },
      { path: 'audios',     component: () => import('../views/admin/Audios.vue') },
      { path: 'tests',      component: () => import('../views/admin/Tests.vue') },
      { path: 'keywords',   component: () => import('../views/admin/Keywords.vue') },
      { path: 'comments',   component: () => import('../views/admin/Comments.vue') },
      { path: 'feedbacks',  component: () => import('../views/admin/Feedbacks.vue') },
      { path: 'diaries',    component: () => import('../views/admin/Diaries.vue') },
      // 👇 三大创新模块的后台管理端（预警 / 无感 / 词典）+ 老功能补齐
      { path: 'alerts',     component: () => import('../views/admin/Alerts.vue') },
      { path: 'sense',      component: () => import('../views/admin/Sense.vue') },
      { path: 'lexicon',    component: () => import('../views/admin/Lexicon.vue') },
      { path: 'settings',   component: () => import('../views/admin/Settings.vue') },
      { path: 'aichat',     component: () => import('../views/admin/AiChatAdmin.vue') },
      { path: 'records',    component: () => import('../views/admin/Records.vue') },
      { path: 'security',   component: () => import('../views/admin/Security.vue') },
      { path: 'privacy',    component: () => import('../views/admin/Privacy.vue') },
      { path: 'interaction', component: () => import('../views/admin/Interaction.vue') },
      { path: 'logs',       component: () => import('../views/admin/SystemLogs.vue') },
      { path: 'banner',     component: () => import('../views/admin/Banner.vue') },
      { path: 'avatars',    component: () => import('../views/admin/Avatars.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 需要登录才能访问的页面（白名单之外的）
const needLogin = ['/diary', '/profile', '/feedback', '/assistant', '/databoard']
// 这些路径的子路由也需要登录
const needLoginPrefixes = ['/diary/write']

/** 本地缓存的 role 是不是管理员（仅用于「落地页跳转」这类无安全影响的判断） */
function isAdminLocal(userInfo) {
  const r = userInfo?.role
  return r === 1 || r === '1' || r === 'admin'
}

/**
 * 服务端权威的管理员校验
 *
 * ⚠️ 为什么不能只看 localStorage：那个值用户按 F12 改一下就能把后台菜单/页面渲染出来，
 * 等于把后台的页面结构和接口地址全部暴露。这里改成问服务端要真实 role，
 * 并顺手把本地 userInfo.role 纠正回来（比如管理员被降级后能立刻生效）。
 *
 * 结果缓存 30 秒，避免后台里点每个菜单都发一次请求。
 *
 * @returns {Promise<boolean|'expired'>} true=管理员；false=不是；'expired'=登录态失效
 */
let serverRoleCache = { role: null, ts: 0 }
const SERVER_ROLE_TTL = 30 * 1000

export async function isAdminFromServer(token) {
  const now = Date.now()
  if (serverRoleCache.role !== null && now - serverRoleCache.ts < SERVER_ROLE_TTL) {
    return isAdminRole(serverRoleCache.role)
  }
  try {
    const res = await axios.get(`${API_BASE}/user/info`, {
      headers: { Authorization: `Bearer ${token}` },
      timeout: 5000,
    })
    const data = res.data?.data || {}
    serverRoleCache = { role: data.role, ts: now }
    syncLocalRole(data)
    return isAdminRole(data.role)
  } catch (e) {
    const status = e.response?.status
    // 401：token 过期 / 被踢下线 —— 清掉本地登录态去登录页
    if (status === 401) {
      useUserStore().clearSession()   // 统一收口：清内存状态 + 持久化
      serverRoleCache = { role: null, ts: 0 }
      return 'expired'
    }
    // 403（账号被禁用）等其它明确拒绝 —— 按非管理员处理
    if (status === 403) return false
    // 网络抖动 / 后端没起来：此时后台本来也取不到数据，退回本地判断，不让管理员被误挡
    return isAdminLocal(useUserStore().userInfo)
  }
}

function isAdminRole(role) {
  return role === 1 || role === '1' || role === 'admin'
}

/** 用服务端返回的资料校正本地 userInfo（role / 主题等以服务端为准） */
function syncLocalRole(data) {
  try {
    // 只提交变化的字段给 store，store 负责合并与持久化
    const patch = {}
    if (data.role !== undefined) patch.role = data.role
    if (data.nickname !== undefined) patch.nickname = data.nickname
    if (data.avatar !== undefined) patch.avatar = data.avatar
    if (Object.keys(patch).length) useUserStore().patchUserInfo(patch)
  } catch (_) { /* 本地存储不可用时忽略 */ }
}

/** 退出登录 / 切换账号时调用，清掉服务端 role 缓存，防止串号 */
export function resetAdminCheck() {
  serverRoleCache = { role: null, ts: 0 }
}

// 路由守卫：统一权限控制
router.beforeEach(async (to, from, next) => {
  // 登录态统一从 Pinia store 读（store 已负责与 localStorage 保持同步）
  const store = useUserStore()
  const userInfo = store.userInfo
  const isLoggedIn = store.isLoggedIn

  // 1. 已登录用户访问 /login —— 直接跳主页
  //    （这里只是决定「落地页」，不涉及权限，用本地缓存的 role 即可）
  if (to.path === '/login' && isLoggedIn) {
    // 但如果是带 redirect 进来的（分享链接 → 登录 → 回原页面），优先回原目标
    const back = typeof to.query.redirect === 'string' ? to.query.redirect : ''
    if (back.startsWith('/') && !back.startsWith('/login')) { next(back); return }
    next(isAdminLocal(userInfo) ? '/admin/dashboard' : '/main')
    return
  }

  // 2. 后台 /admin —— 必须是管理员，且 role 以服务端返回为准
  if (to.path.startsWith('/admin')) {
    if (!isLoggedIn) { next(toLogin(to)); return }
    const ok = await isAdminFromServer(store.token)
    if (ok === false) { next('/main'); return }
    if (ok === 'expired') { next(toLogin(to)); return }
    next()
    return
  }

  // 3. 需要登录的普通页面
  const needAuth = needLogin.some(p => to.path === p) || needLoginPrefixes.some(p => to.path.startsWith(p))
  if (needAuth && !isLoggedIn) {
    // 记住原本要去的页面：从分享链接进来的人登录完能直接落到那条内容上
    next(toLogin(to))
    return
  }

  next()
})

/** 去登录页，并把「登录后回哪里」带上（首页/欢迎页不需要，避免地址栏变脏） */
function toLogin(to) {
  if (!to.fullPath || to.fullPath === '/' || to.fullPath === '/main' || to.path === '/login') return '/login'
  return { path: '/login', query: { redirect: to.fullPath } }
}

/**
 * ===== 深链历史垫底 =====
 *
 * 从通知/分享链接（/fm?audioId=3、/test?testId=2、/diary?date=2026-11-01 …）
 * 直接打开时，浏览器历史里只有这一条记录 —— 按系统返回键/微信左上角返回会直接退出应用。
 *
 * 这里在进站时把当前这条历史改写成「列表页」，再压入「内容页」：
 *   [ 列表页 ] ← [ 内容页 ]   ← 用户在这里
 * 于是系统返回键先回到列表（而不是退出），和站内滑动返回的落点完全一致。
 *
 * 只做一次：已经有站内历史（history.state.back 存在）时不动，不干扰正常后退与滚动位置。
 */
router.afterEach((to) => {
  const listPath = to.meta?.listPath
  if (!listPath) return
  const st = window.history.state || {}
  if (st.back) return                                   // 站内跳进来的，历史链天然存在
  const contentUrl = window.location.pathname + window.location.search + window.location.hash
  const listUrl = router.resolve(listPath).href
  if (contentUrl === listUrl) return                    // 本身就是列表页，不用垫
  if (!contentUrl.startsWith(listUrl.split('?')[0])) return  // 兜底：不是这条路由的内容页就跳过

  const base = Number.isFinite(st.position) ? st.position : 0
  window.history.replaceState({ ...st, back: null, current: listUrl, position: base }, '', listUrl)
  window.history.pushState({ ...st, back: listUrl, current: contentUrl, position: base + 1, replaced: false }, '', contentUrl)
})


export default router