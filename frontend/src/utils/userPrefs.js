// 用户个性化偏好的本地存储（主题 / 头像框 / 个人背景 / 深色模式）
// 这些 key 原来是无用户维度的全局 key，切换账号会串号，这里统一按账号写入/清理。

export const THEMES = [
  { name: 'green',  label: '薄荷绿', hex: '#7bc47b', main: '#90db90', bg1: '#f6f9f6', bg2: '#f0f7f0' },
  { name: 'blue',   label: '天空蓝', hex: '#5b9bd5', main: '#7cb8ea', bg1: '#f2f7fc', bg2: '#eaf2fa' },
  { name: 'purple', label: '浪漫紫', hex: '#9b7bd5', main: '#b8a0e6', bg1: '#f6f3fb', bg2: '#ede8f7' },
  { name: 'pink',   label: '少女粉', hex: '#e59bb3', main: '#f2b8cd', bg1: '#fcf4f7', bg2: '#fae8ef' },
  { name: 'orange', label: '暖阳橙', hex: '#e69b5b', main: '#f2b87c', bg1: '#fdf6ef', bg2: '#fae8d9' },
  { name: 'teal',   label: '青碧',   hex: '#5bb8a6', main: '#7cd4c3', bg1: '#f2faf8', bg2: '#eaf5f2' },
  { name: 'rose',   label: '玫瑰红', hex: '#d96b7b', main: '#ec8c9a', bg1: '#fcf2f4', bg2: '#fae6e9' },
  { name: 'indigo', label: '靛青',   hex: '#6b7bd9', main: '#8c9aec', bg1: '#f2f4fc', bg2: '#e6e9fa' },
]

// 后端头像框字段(frame1~frame7) → 前端资源名
export const FRAME_MAP = {
  frame1: 'flower', frame2: 'leaf', frame3: 'crown', frame4: 'star',
  frame5: 'heart',  frame6: 'halo', frame7: 'cloud',
}

const KEYS = ['profile_theme', 'profile_frame', 'profile_bg', 'profile_bg_pos', 'profile_dark']

// 清除上一个账号留下的个性化设置（退出登录 / 切换账号时调用）
export function clearUserPrefs() {
  KEYS.forEach((k) => localStorage.removeItem(k))
}

// 登录成功后，用后端返回的账号设置覆盖本地（没有设置就用默认值）
export function applyUserPrefs(user = {}) {
  try {
    // 主题
    const themeName = typeof user.theme === 'string' ? user.theme : (user.theme?.name || '')
    const theme = THEMES.find((t) => t.name === themeName) || THEMES[0]
    localStorage.setItem('profile_theme', JSON.stringify(theme))

    // 深色模式
    const dark = user.darkMode === true || user.darkMode === 1 || user.darkMode === '1'
    localStorage.setItem('profile_dark', dark ? '1' : '0')

    // 头像框
    const frame = FRAME_MAP[user.avatarFrame] || (typeof user.avatarFrame === 'string' ? user.avatarFrame : '')
    if (frame && frame !== 'none') localStorage.setItem('profile_frame', frame)
    else localStorage.removeItem('profile_frame')

    // 个人主页背景
    if (user.profileBg) localStorage.setItem('profile_bg', user.profileBg)
    else localStorage.removeItem('profile_bg')
  } catch (e) {
    console.warn('应用用户偏好失败', e)
  }
}
