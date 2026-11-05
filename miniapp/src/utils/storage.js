/**
 * 存储封装 —— 对应主项目的 localStorage 用法（168 处）
 *
 * 小程序里没有 localStorage，用 uni.getStorageSync / setStorageSync。
 * 之所以要包一层：主项目还有「跨标签页同步」的 storage 事件监听，
 * 小程序没有这个概念，这里只保留同步读写即可。
 */

const K_TOKEN = 'token'
const K_USER = 'userInfo'

export const getToken = () => {
  try { return uni.getStorageSync(K_TOKEN) || '' } catch { return '' }
}
export const setToken = (t) => uni.setStorageSync(K_TOKEN, t)

export const getUserInfo = () => {
  try {
    const v = uni.getStorageSync(K_USER)
    return v && typeof v === 'object' ? v : (v ? JSON.parse(v) : null)
  } catch { return null }
}
export const setUserInfo = (u) => uni.setStorageSync(K_USER, u)

export const isLoggedIn = () => !!getToken()

export function clearSession() {
  try {
    uni.removeStorageSync(K_TOKEN)
    uni.removeStorageSync(K_USER)
  } catch { /* 忽略 */ }
}

/**
 * 登录守卫 —— 各 tab 页 onShow 里调用
 *
 * 【为什么需要】App.vue 的 onLaunch 只在冷启动跑一次，小程序没有全局路由钩子，
 * tabBar 页面切换也不会触发 onUnload，所以「token 过期被清」之后
 * 页面不会自动回登录页，只会静默显示空数据（用户看到的就是「页面没内容」）。
 * 这里统一在每次页面露出时兜一次底。
 *
 * @returns {boolean} true = 已登录可以继续加载数据；false = 已跳转登录页，调用方应 return
 */
export function ensureLogin() {
  if (getToken()) return true
  uni.reLaunch({ url: '/pages/login/login' })
  return false
}

/** 记住的用户名（登录页用，只存用户名，不存密码） */
export const getRemembered = () => {
  try { return uni.getStorageSync('rememberedUser') || '' } catch { return '' }
}
export const setRemembered = (u) => uni.setStorageSync('rememberedUser', u)
