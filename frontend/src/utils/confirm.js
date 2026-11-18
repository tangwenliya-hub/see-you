// 果冻风确认弹层（全站通用）：临时小操作（删除确认 / 退出登录 / 取消点赞…）统一入口
//
// 交互约定（2026-11-18 定）：
// · 移动端贴底弹层，下滑把手或点遮罩关闭，关闭后停在原页面原位置
// · 桌面端同一组件自动切居中卡片形态（见 ConfirmSheet.vue / BottomSheet.vue）
// · API 与旧版（ElMessageBox 包装）完全兼容：jellyConfirm(message, opts) → Promise<boolean>
import { createApp } from 'vue'
import ConfirmSheet from '../components/ConfirmSheet.vue'

export function jellyConfirm(message, { title = '提示', confirmText = '确定', cancelText = '再想想', danger = false } = {}) {
  return new Promise((resolve) => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ConfirmSheet, {
      message,
      title,
      confirmText,
      cancelText,
      danger,
      onResult: (ok) => {
        resolve(ok)
        // 动态创建的独立 app：卸载前需清理 DOM 宿主
        app.unmount()
        host.remove()
      },
    })
    app.mount(host)
  })
}
