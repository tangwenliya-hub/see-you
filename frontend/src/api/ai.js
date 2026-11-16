/**
 * AI 生活助手三件套的接口封装：情绪周报 / 日记润色 / 语音写日记。
 *
 * 统一说明：
 *  · 所有接口都**不接受 userId 参数** —— 后端一律从 JWT 里取身份（隐私红线）。
 *  · 三个功能都可能要等大模型/ASR 返回，超时统一放宽到 60 秒（axios 全局默认 10 秒不够）。
 *  · 后端把「AI 未配置 key / 调用失败」包成 HTTP 200 + code=400 + 友好 msg，
 *    axios 拦截器对业务 code!==200 不 reject，调用方必须显式判断 res.code 再透出 res.msg。
 */
import request from './index'

/** 获取（或生成）本周情绪周报；refresh=true 忽略缓存强制重新生成 */
export const getWeeklyReport = (refresh = false) =>
  request.get('/ai/report/weekly', { params: refresh ? { refresh: true } : {}, timeout: 60000 })

/** 日记润色：流水账 → 通顺日记草稿（只返回文本，由用户确认后自行保存） */
export const polishDiary = (content) =>
  request.post('/ai/diary/polish', { content }, { timeout: 60000 })

/** 语音转文字：上传 16kHz 单声道 WAV，返回识别出的文字 */
export const voiceToText = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/ai/diary/voice', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}
