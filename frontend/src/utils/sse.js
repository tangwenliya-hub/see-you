import { ElNotification } from 'element-plus'
import { useUserStore } from '../stores/user'
import router from '../router'

// 与 src/api/index.js 同一套前缀约定：dev=/api（Vite 代理），prod=空（同源直连）
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'

/**
 * 情绪预警 SSE 实时推送工具（管理端）
 *
 * 为什么用 SSE 而不是复用反馈的 WebSocket：
 * 预警是纯服务端单向广播（管理员不需要往回发消息），EventSource 单向模型刚好匹配，
 * 且浏览器原生自带断线重连；反馈需要双向收发（即时回复），留在 WS。两个通道各司其职。
 *
 * 鉴权：EventSource 无法携带 Authorization 头，token 走查询参数，
 * 后端 /risk/admin-stream 在控制器里手动做「token + 会话 + 管理员角色」三重校验。
 */
class RiskSse {
  constructor() {
    this.es = null
    this.listeners = {}       // event -> [cb]
    this.manualClose = false  // 登出时置 true，关闭后不再因后端断开弹提示
  }

  connect() {
    if (typeof window === 'undefined' || !window.EventSource) return
    // 状态源统一走 Pinia store（未挂载 pinia 的极早期场景退回 localStorage）
    let token = ''
    let role = null
    try {
      const st = useUserStore()
      token = st.token || ''
      role = st.userInfo?.role
    } catch {
      token = localStorage.getItem('token') || ''
      role = JSON.parse(localStorage.getItem('userInfo') || 'null')?.role
    }
    // 只有管理员才有必要连；普通用户连了后端也会拒绝，但没必要发起请求
    if (!token || !(role >= 1)) return
    if (this.es) return  // 已有连接（EventSource 原生自动重连，无需重建）

    this.manualClose = false
    try {
      this.es = new EventSource(`${API_BASE}/risk/admin-stream?token=${encodeURIComponent(token)}`)
    } catch {
      this.es = null
      return
    }

    this.es.addEventListener('connected', () => {
      console.log('[RiskSSE] ✅ 预警实时通道已连接')
    })

    // 预警事件由后端按 name=risk-alert 推送
    this.es.addEventListener('risk-alert', (e) => {
      let data = null
      try { data = JSON.parse(e.data) } catch { return }
      const cbs = this.listeners['risk-alert'] || []
      cbs.forEach(cb => { try { cb(data) } catch (err) { console.warn('[RiskSSE] 回调异常', err) } })
    })

    // 用户标记「这不是我（误报）」事件，由后端按 name=risk-misreport 推送
    this.es.addEventListener('risk-misreport', (e) => {
      let data = null
      try { data = JSON.parse(e.data) } catch { return }
      const cbs = this.listeners['risk-misreport'] || []
      cbs.forEach(cb => { try { cb(data) } catch (err) { console.warn('[RiskSSE] 回调异常', err) } })
    })

    this.es.onerror = () => {
      // EventSource 会自动重连（包括后端重启场景）；这里只打日志
      console.log('[RiskSSE] 连接中断，浏览器将自动重连…')
    }
  }

  on(event, cb) {
    if (!this.listeners[event]) this.listeners[event] = []
    this.listeners[event].push(cb)
  }

  off(event, cb) {
    const arr = this.listeners[event]
    if (!arr) return
    const i = arr.indexOf(cb)
    if (i > -1) arr.splice(i, 1)
  }

  disconnect() {
    this.manualClose = true
    try { this.es?.close() } catch {}
    this.es = null
  }
}

export const riskSSE = new RiskSse()

// 同一事件只保留一个通知监听：组件反复挂载时先移除旧的，避免通知重复弹 N 次
let notifyCb = null

/**
 * 管理端便捷方法：收到新预警弹果冻风通知 + 执行回调（如刷新列表）。
 * 用法与 notifyFeedback 对齐。
 */
export function notifyRiskAlert(callback) {
  if (notifyCb) riskSSE.off('risk-alert', notifyCb)
  notifyCb = (data) => {
    const lv = Number(data?.level || 0)
    const type = lv >= 3 ? 'error' : lv === 2 ? 'warning' : 'info'
    const icon = data?.crisis ? '🆘' : lv >= 3 ? '🚨' : '⚠️'
    // 点击通知直接跳预警页处理（duration=0 的常驻通知尤其需要一条退出路径）
    const n = ElNotification({
      title: `${icon} 情绪预警 · ${data?.levelName || '关注'}`,
      message: `${data?.userName || '用户'} · ${data?.triggerRuleName || data?.triggerRule || '触发预警'}（点击查看）`,
      type,
      duration: data?.crisis || lv >= 3 ? 0 : lv === 2 ? 20000 : 12000,
      key: 'risk-' + (data?.alertId || ''),   // 同一条预警只弹一次
      grouping: true,
      customClass: 'risk-notify-clickable',
      onClick: () => {
        n.close()
        // 已在预警页就只刷新数据，不在则跳过去（跳转后 Alerts.vue 挂载会自动加载）
        if (router.currentRoute.value.path === '/admin/alerts') {
          window.dispatchEvent(new CustomEvent('risk-alert-new', { detail: data }))
        } else {
          router.push('/admin/alerts')
        }
      },
    })
    // 通知预警相关页面（Alerts.vue / Dashboard.vue）刷新数据
    window.dispatchEvent(new CustomEvent('risk-alert-new', { detail: data }))
    callback?.(data)
  }
  riskSSE.on('risk-alert', notifyCb)
}

let misreportCb = null

/**
 * 管理端便捷方法：用户把某条预警标记为「这不是我」时弹一条低打扰通知。
 * 与预警通知的区别：不抢焦点、15 秒自动消失（不是需要立刻冲过去处理的事，
 * 但处置前必须知道「用户否认了这条」，否则会按高危流程去打电话反而冒犯用户）。
 */
export function notifyRiskMisreport(callback) {
  if (misreportCb) riskSSE.off('risk-misreport', misreportCb)
  misreportCb = (data) => {
    const n = ElNotification({
      title: '📮 用户反馈：这条预警与实际不符',
      message: `${data?.userName || '用户'} 把「${data?.triggerRuleName || data?.triggerRule || '预警'}」标记为误报（点击核实）`,
      type: 'info',
      duration: 15000,
      key: 'misreport-' + (data?.alertId || ''),
      grouping: true,
      customClass: 'risk-notify-clickable',
      onClick: () => {
        n.close()
        if (router.currentRoute.value.path === '/admin/alerts') {
          window.dispatchEvent(new CustomEvent('risk-alert-new', { detail: { ...data, misreport: 1 } }))
        } else {
          router.push('/admin/alerts')
        }
      },
    })
    // 预警页开着就同步刷新（列表会出现「误报」标签、准确率卡会掉）
    window.dispatchEvent(new CustomEvent('risk-alert-new', { detail: { ...data, misreport: 1 } }))
    callback?.(data)
  }
  riskSSE.on('risk-misreport', misreportCb)
}
