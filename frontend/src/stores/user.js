/**
 * useUserStore —— 登录态的唯一状态源（Pinia）
 *
 * 为什么从「直接读写 localStorage」迁到 Pinia：
 * 1. 无响应式：localStorage 是字符串存取，改了之后各组件不会自动更新，
 *    原来只能靠 window 的 storage 事件 + 路由切换时手动重新 JSON.parse（AppLayout 里就有这段补丁）
 * 2. 状态分散：token / userInfo 散落在 Login、Profile、AdminLayout、AppLayout 等多处各自读写，
 *    任一处漏写就出现「导航显示已登录、页面却 401」的错位
 * 3. 无法统一收口：登出要记得同时清 token + userInfo + 会话，散着写迟早漏
 *
 * 分工（面试常问）：
 *   Pinia        = 运行时的唯一状态源，负责响应式与统一收口
 *   localStorage = 只做持久化（刷新不丢登录态），不参与组件间通信
 *   后端会话表    = 服务端控制（踢设备 / 注销后强制失效），这是 JWT 做不到的部分
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const LS_TOKEN = 'token'
const LS_USER = 'userInfo'

/** localStorage 里存的是 JSON 字符串，解析失败一律按 null 处理（避免脏数据把页面搞崩） */
const parseUser = () => {
  try {
    const raw = localStorage.getItem(LS_USER)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  /* ================= state ================= */
  // 初始值从 localStorage 恢复（刷新页面不丢登录态）
  const token = ref(localStorage.getItem(LS_TOKEN) || '')
  const userInfo = ref(parseUser())

  /* ================= getters ================= */
  // 判定登录：token 与 userInfo 必须同时存在（只有 token 说明是脏状态）
  const isLoggedIn = computed(() => !!token.value && !!userInfo.value?.userId)
  const userId = computed(() => userInfo.value?.userId ?? null)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const isAdmin = computed(() => Number(userInfo.value?.role) === 1)

  /* ================= actions ================= */

  /** 登录成功：统一写入 store + 持久化 */
  function setSession(newToken, newUserInfo) {
    token.value = newToken || ''
    userInfo.value = newUserInfo && typeof newUserInfo === 'object' ? newUserInfo : null
    persist()
  }

  /** 局部更新用户资料（改昵称/头像后调用，避免整对象覆盖丢字段） */
  function patchUserInfo(patch = {}) {
    if (!userInfo.value) userInfo.value = {}
    userInfo.value = { ...userInfo.value, ...patch }
    persist()
  }

  /** 用服务端返回的最新用户信息整体替换（个人中心保存后调用） */
  function setUserInfo(info) {
    userInfo.value = info && typeof info === 'object' ? info : null
    persist()
  }

  /** 登出 / 被踢 / 401：清空状态与持久化 */
  function clearSession() {
    token.value = ''
    userInfo.value = null
    persist()
  }

  /** 持久化到 localStorage（唯一写 localStorage 的出口） */
  function persist() {
    try {
      if (token.value) localStorage.setItem(LS_TOKEN, token.value)
      else localStorage.removeItem(LS_TOKEN)

      if (userInfo.value) localStorage.setItem(LS_USER, JSON.stringify(userInfo.value))
      else localStorage.removeItem(LS_USER)
    } catch {
      /* 隐私模式下 localStorage 可能不可写，忽略即可（内存中的 store 仍然可用） */
    }
  }

  /**
   * 外部（别的标签页 / 老代码）直接改了 localStorage 时，把 store 拉回一致
   * 兼容期用：老页面仍可能直接写 localStorage
   */
  function syncFromStorage() {
    const t = localStorage.getItem(LS_TOKEN) || ''
    const u = parseUser()
    if (t !== token.value || JSON.stringify(u) !== JSON.stringify(userInfo.value)) {
      token.value = t
      userInfo.value = u
    }
  }

  return {
    // state
    token, userInfo,
    // getters
    isLoggedIn, userId, username, nickname, avatar, isAdmin,
    // actions
    setSession, patchUserInfo, setUserInfo, clearSession, syncFromStorage,
  }
})

/**
 * 跨标签页同步（在 main.js 里调用一次）
 *
 * 为什么需要：Pinia 的 store 是内存态，A 标签登出后 B 标签的 store 仍是旧的登录态，
 * 会出现「这边已退出、那边还能操作」的错位。浏览器的 storage 事件只在**其它标签页**触发，
 * 正好用来把 B 标签的 store 拉回一致。
 */
export function initUserStorageSync() {
  if (typeof window === 'undefined') return
  window.addEventListener('storage', (e) => {
    // e.key 为 null 表示 localStorage 被整体清空
    if (e.key === null || e.key === LS_TOKEN || e.key === LS_USER) {
      useUserStore().syncFromStorage()
    }
  })
}
