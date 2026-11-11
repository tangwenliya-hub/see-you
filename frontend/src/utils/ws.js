import { h } from 'vue'
import { ElNotification } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { useUserStore } from '../stores/user'

/**
 * 反馈实时推送 WebSocket 工具
 * 用户端：监听 type=reply
 * 管理端：监听 type=new_feedback
 */
class FeedbackWS {
  constructor() {
    this.ws = null
    this.listeners = {}
    this.reconnectTimer = null
    this.reconnectDelay = 3000
    this.manualClose = false   // 主动断开标志：登出等场景下不再自动重连
  }

  /** 建立连接 */
  connect() {
    const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
    if (!info) {
      console.log('[WS] 没有登录信息，跳过连接')
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const userId = info.userId
    const role = info.role
    // 状态源统一走 Pinia store（未挂载 pinia 的极早期场景退回 localStorage）
    let token = ''
    try { token = useUserStore().token || '' } catch { token = localStorage.getItem('token') || '' }
    // 安全：userId/role 由服务端从 token 解析，URL 上的只是冗余信息
    const url = `${protocol}//${host}/ws/feedback?token=${encodeURIComponent(token || '')}`

    console.log('[WS] 正在连接:', url, 'readyState=', this.ws?.readyState)

    this.manualClose = false
    // 如果已经有旧连接，先关掉
    if (this.ws && this.ws.readyState < 2) {
      try { this.ws.close() } catch {}
      this.ws = null
    }

    try {
      this.ws = new WebSocket(url)
    } catch (e) {
      console.log('[WS] 创建 WebSocket 异常:', e.message)
      this.scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      console.log('[WS] 连接成功 userId=', userId, 'role=', role)
    }

    this.ws.onmessage = (e) => {
      console.log('[WS] 收到消息:', e.data)
      try {
        const msg = JSON.parse(e.data)
        const listeners = this.listeners[msg.type] || []
        console.log('[WS] 触发消息 type=', msg.type, 'listeners 数量=', listeners.length)
        listeners.forEach((cb, i) => {
          console.log('[WS] → 调用 listener[' + i + ']')
          cb(msg.data)
        })
      } catch (err) {
        console.warn('[WS] 消息解析失败', err)
      }
    }

    this.ws.onclose = (e) => {
      if (this.manualClose) {
        console.log('[WS] 主动断开，不再重连')
        return
      }
      console.warn('[WS] 连接已关闭 code=', e.code, '尝试重连...')
      this.scheduleReconnect()
    }

    this.ws.onerror = (e) => {
      console.log('[WS] 连接出错')
      try { this.ws?.close() } catch {}
    }
  }

  /** 注册事件监听 */
  on(type, callback) {
    if (!this.listeners[type]) this.listeners[type] = []
    this.listeners[type].push(callback)
  }

  /** 移除事件监听 */
  off(type, callback) {
    const arr = this.listeners[type]
    if (!arr) return
    const i = arr.indexOf(callback)
    if (i > -1) arr.splice(i, 1)
  }

  /** 断开连接 */
  disconnect() {
    this.manualClose = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }

  scheduleReconnect() {
    if (this.reconnectTimer) return
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, this.reconnectDelay)
  }
}

export const feedbackWS = new FeedbackWS()

// 调试：暴露到全局 window，方便 Console 检查
if (typeof window !== 'undefined') {
  window.__feedbackWS = feedbackWS
}

/** 便捷方法：显示通知并执行回调 */
// 同一 type 只保留一个通知监听：组件反复挂载（来回跳页面）时先移除旧的，避免监听累积 → 通知重复弹 N 次
const notifyCbs = {}
export function notifyFeedback(type, callback) {
  if (notifyCbs[type]) feedbackWS.off(type, notifyCbs[type])
  const cb = (data) => {
    console.log('[WS] 触发 notifyFeedback type=', type, 'data=', data)
    if (type === 'reply') {
      ElNotification({
        title: '管理员回复了你',
        icon: h(AppIcon, { name: 'message', size: 18 }),
        message: data.replyText || '有新的回复',
        type: 'success',
        duration: 5000,
        key: 'reply-' + (data.feedbackId || ''),  // 同反馈只弹一次
      })
    } else if (type === 'new_feedback') {
      ElNotification({
        title: '收到新反馈',
        icon: h(AppIcon, { name: 'inbox', size: 18 }),
        message: `${data.username || '用户'} 提交了反馈：${data.title || data.content || ''}`,
        type: 'warning',
        duration: 5000,
        key: 'newfb-' + (data.feedbackId || ''),
      })
    } else if (type === 'user_supplement') {
      ElNotification({
        title: '用户补充了反馈',
        icon: h(AppIcon, { name: 'message', size: 18 }),
        message: `${data.username || '用户'} 补充了：${(data.content || '').slice(0, 30)}`,
        type: 'info',
        duration: 5000,
        key: 'supp-' + (data.feedbackId || ''),
      })
    }
    callback?.(data)
  }
  notifyCbs[type] = cb
  feedbackWS.on(type, cb)
}