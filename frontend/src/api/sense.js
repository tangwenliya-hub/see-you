/**
 * 无感记录模块 + 情绪风险预警模块的接口封装。
 *
 * 统一说明：
 *  · 所有接口都**不接受 userId 参数** —— 后端一律从 JWT 里取身份。
 *  · 这里刻意没有「查询他人数据」的方法：包括管理员在内，任何人都看不到别人的行为特征、推测候选与预警记录。
 */
import request from './index'

// ==================== 无感记录 ====================

/** 我的开关配置 + 数据概况 */
export const getSenseConfig = () => request.get('/sense/config')

/** 修改开关（分级：总开关关 → 子开关全关） */
export const updateSenseConfig = (d) => request.post('/sense/config', d)

/** 批量上报脱敏行为特征 */
export const reportBehavior = (items) => request.post('/sense/behavior/report', { items })

/** 我的行为数据（自查） */
export const getMyBehavior = (from, to) => request.get('/sense/behavior/my', { params: { from, to } })

/** 一键清空我的行为数据 */
export const clearBehavior = () => request.delete('/sense/behavior')

/** 导出我的行为数据 CSV */
export const exportBehavior = () =>
  request.get('/sense/behavior/export', { responseType: 'blob' })

/** 我的隐私操作日志 */
export const getPrivacyLogs = () => request.get('/sense/privacy/logs')

/** 今日待确认推测 */
export const getTodayPrediction = () => request.get('/sense/predict/today')

/** 主动生成一次推测（可带随笔文本增强准确度） */
export const generatePrediction = (text) => request.post('/sense/predict/generate', { text })

/** 采用推测 → 生成正式日记 */
export const confirmPrediction = (id, d) => request.post(`/sense/predict/${id}/confirm`, d)

/** 忽略推测 */
export const rejectPrediction = (id) => request.post(`/sense/predict/${id}/reject`)

/** 立刻自动代记一次（定时任务每天 23:30 跑，这里是手动补记） */
export const autoFillDiary = () => request.post('/sense/auto-fill')

/** 推测历史 */
export const getPredictionHistory = (page = 1, size = 10) =>
  request.get('/sense/predict/history', { params: { page, size } })

/** 命中率统计（论文实验数据） */
export const getSenseAccuracy = () => request.get('/sense/accuracy')

/** 短文本即时情感分析（原文不落库） */
export const analyzeText = (text) => request.post('/sense/text/analyze', { text })

/** 特征-情绪关联数据 */
export const getSenseCorrelation = (days = 90) =>
  request.get('/sense/correlation', { params: { days } })

/** 手动录入 vs 系统推测 双序列 */
export const getSenseTimeline = (days = 30) =>
  request.get('/sense/timeline', { params: { days } })

/** 重新加载词典 */
export const reloadLexicon = () => request.post('/sense/lexicon/reload')

// ==================== 情绪风险预警（非医疗诊断） ====================

/** 最近一条未确认预警（弹窗用） */
export const getRiskLatest = () => request.get('/risk/latest')

/** 预警概览：未读数 / 累计 / 最高等级 / 当前阈值 */
export const getRiskOverview = () => request.get('/risk/overview')

/** 历史预警记录 */
export const getRiskList = (page = 1, size = 10) =>
  request.get('/risk/list', { params: { page, size } })

/** 确认知晓 */
export const ackRisk = (id) => request.post(`/risk/${id}/ack`)

/** 标记已读 */
export const readRisk = (id) => request.post(`/risk/${id}/read`)

/** 标记误报（「这不是我」）：危机词通道后端会拒绝 */
export const misreportRisk = (id) => request.post(`/risk/${id}/misreport`)

/** 手动触发一次判定（调试用） */
export const evaluateRisk = () => request.post('/risk/evaluate')
