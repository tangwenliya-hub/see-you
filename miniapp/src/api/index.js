import request, { unwrap } from './adapter'

/**
 * 小程序端接口集合 —— 只保留核心记录链路需要的接口
 *
 * 这些函数与主项目 frontend/src/api/index.js 的签名保持一致，
 * 底层由 adapter.js 决定用 axios（H5）还是 uni.request（小程序）。
 * 后端零改动：所有接口都是现成的。
 */

// ============ 账号 ============
export const getCaptcha = () => request.get('/auth/captcha')
export const login = (d) => request.post('/user/login', d)
export const register = (d) => request.post('/user/register', d)
export const getUserInfoApi = () => request.get('/user/info')
export const logout = () => request.post('/user/logout')

// ============ 日记 ============
export const getDiaryList = (params = { page: 1, size: 20 }) => request.get('/diary/list', params)
export const saveDiary = (d) => request.post('/diary/save', d)
export const getDiaryDetail = (id) => request.get(`/diary/${id}`)
export const deleteDiary = (id) => request.delete(`/diary/${id}`)

// ============ 个人统计 ============
export const getMyOverview = () => request.get('/statistics/my/overview')
export const getMyEmotion = () => request.get('/statistics/my/emotion')
export const getMyTrend = (days = 30) => request.get('/statistics/my/trend', { days })

// ============ AI 情绪陪伴 ============
export const getAiSessions = () => request.get('/aiChat/session/list')
export const createAiSession = () => request.post('/aiChat/session/create', {})
export const getAiSessionMessages = (sessionId) =>
  request.get('/aiChat/session/messages', { sessionId })
export const deleteAiSession = (sessionId) =>
  request.delete(`/aiChat/session/${encodeURIComponent(sessionId)}`)
// AI 回复可能比普通接口慢（大模型生成），单独放宽到 60s，避免误报「请求超时」
export const chatWithAi = (sessionId, userMsg) =>
  request.request({ url: '/aiChat/chat', method: 'POST', data: { sessionId, userMsg }, timeout: 60000 })

// ============ 情绪风险预警（非医疗诊断） ============
export const getRiskOverview = () => request.get('/risk/overview')
export const getRiskLatest = () => request.get('/risk/latest')
export const getRiskList = () => request.get('/risk/list')

export { unwrap }
