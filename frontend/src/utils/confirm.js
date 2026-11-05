// 果冻风确认弹窗（全站通用）：ElMessageBox 的项目配色定制版
// 样式在 src/styles/jelly-confirm.css（main.js 全局引入）
import { ElMessageBox } from 'element-plus'

export const jellyConfirm = (message, { title = '提示', confirmText = '确定', cancelText = '再想想', danger = false } = {}) =>
  ElMessageBox.confirm(message, title, {
    confirmButtonText: confirmText,
    cancelButtonText: cancelText,
    customClass: 'jelly-confirm' + (danger ? ' danger' : ''),
  }).then(() => true).catch(() => false)
