import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../stores/user'

// 请求前缀按环境区分：
// 本地开发(npm run dev) 默认 /api，由 vite.config.js 代理去掉前缀转发到后端；
// 生产构建由 Dockerfile 注入 VITE_API_BASE=""，前端以同源相对路径直连后端真实接口（同源无跨域）。
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'
const request = axios.create({ baseURL: API_BASE, timeout: 10000 })

// 自动带 token：状态源是 Pinia store（localStorage 仅作兜底，避免极早期模块加载时 store 还没激活）
request.interceptors.request.use((config) => {
  const token = useUserStore().token || localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 统一处理 401 / 403
let redirecting401 = false // 防并发 401 多次跳转

/** 业务码 401：清会话 + 去重跳登录（与 HTTP 401 同一套动作） */
function handleBusiness401() {
  const store = useUserStore()
  const hadToken = !!store.token || !!localStorage.getItem('token')
  store.clearSession()
  if (hadToken && !redirecting401 && router.currentRoute.value.path !== '/login') {
    redirecting401 = true
    router.replace('/login')
    setTimeout(() => { redirecting401 = false }, 1500)
  }
}

request.interceptors.response.use(
  (res) => {
    const body = res.data
    /*
     * 后端 GlobalExceptionHandler 把业务错误统一包成 HTTP 200 + { code, msg }，
     * 所以 HTTP 状态码在这里永远是 200 —— 只看 status 的话，所有业务级 403
     * （无感未授权、只读管理员、越权操作）都会被前端静默吞掉。
     * 历史事故：无感开关默认关闭时点「帮我猜一下」，接口返回 403，界面毫无反应，
     * 现场表现为「这个功能是坏的」。因此业务码必须在成功分支里单独处理。
     */
    const code = body && typeof body === 'object' ? body.code : undefined
    if (code === 401) {
      handleBusiness401()
    } else if (code === 403) {
      // grouping 会合并同文案的重复提示，避免连环点击刷屏；
      // 若页面自己也弹了同样的 msg，Element Plus 会合并成一条。
      ElMessage.warning({ message: body.msg || '没有权限执行此操作', grouping: true })
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      handleBusiness401()
    } else if (status === 403) {
      // 无权限：给出明确提示，而不是静默失败
      ElMessage.warning(err.response?.data?.msg || '没有权限执行此操作')
    }
    return Promise.reject(err)
  }
)

// 用户
export const login = (d) => request.post('/user/login', d)
export const register = (d) => request.post('/user/register', d)
export const getUserList = (params) => request.get('/user/list', { params })
// ===== 后台日记管理（管理员）=====
export const adminDiaryList = (params) => request.get('/diary/admin/list', { params })
export const adminDiaryDelete = (id) => request.delete(`/diary/admin/${id}`)
// ===== 账号注销（需密码确认）=====
export const deactivateAccount = (password) => request.put('/user/deactivate', { password })
export const getUserById = (id) => request.get(`/user/${id}`)
export const updateUser = (d) => request.put('/user/update', d)
export const updateUserStatus = (d) => request.put('/user/status', d)
export const deleteUser = (id) => request.delete(`/user/${id}`)
export const downloadUserTemplate = () => request.get('/user/import/template', { responseType: 'blob' })
export const importUsers = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/user/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// ===== 账号安全：验证码 / 找回密码 / 会话 =====
export const getCaptcha = () => request.get('/auth/captcha')
export const forgotSendCode = (account) => request.post('/auth/forgot/send', { account })
export const forgotReset = (d) => request.post('/auth/forgot/reset', d)
export const getSessions = () => request.get('/auth/sessions')
export const kickSession = (sessionId) => request.delete(`/auth/sessions/${sessionId}`)
export const kickOtherSessions = () => request.delete('/auth/sessions/others')
export const changePassword = (d) => request.put('/user/changePassword', d)

// 测评量表
export const getTestPaperList = () => request.get('/paper/list')
export const saveTestPaper = (d) => request.post('/paper/save', d)
export const updateTestPaper = (d) => request.put('/paper/update', d)
export const deleteTestPaper = (id) => request.delete(`/paper/${id}`)
export const updateTestPaperStatus = (testId, status) => request.put('/paper/updateStatus', null, { params: { testId, status } })
// 测评结果分级
export const getResultGradeList = (testId) => request.get('/admin/resultGrade/listByTest', { params: { testId } })
export const batchSaveResultGrade = (testId, grades) => request.post('/admin/resultGrade/batchSave', grades, { params: { testId } })
// ===== 测评记录 =====
export const getRecordList = (params) => request.get('/record/list', { params })
export const saveRecord = (data) => request.post('/record/save', data)
// 按「量表+得分」匹配后台配置的结果分级；未配置返回 null，前端回退本地兜底文案
export const matchResultGrade = (testId, score) => request.get('/record/grade', { params: { testId, score } })

// ===== 题目 & 选项 =====
export const getQuestionList = (params) => request.get('/question/list', { params })
export const saveQuestion = (d) => request.post('/question/save', d)
export const deleteQuestion = (id) => request.delete(`/question/${id}`)
export const saveQuestionWithOptions = (d) => request.post('/question/saveWithOptions', d)
export const getOptionList = (params) => request.get('/option/list', { params })
export const saveOption = (d) => request.post('/option/save', d)
export const updateOption = (d) => request.put('/option/update', d)
export const deleteOption = (id) => request.delete(`/option/${id}`)
// 视频
export const getVideoList = (params) => request.get('/video/list', { params })
export const saveVideo = (d) => request.post('/video/save', d)
export const updateVideo = (d) => request.put('/video/update', d)
export const deleteVideo = (id) => request.delete(`/video/${id}`)
export const playVideo = (id) => request.post(`/video/play/${id}`)

// 音频
export const getAudioList = (params) => request.get('/audio/list', { params })
export const saveAudio = (d) => request.post('/audio/save', d)
export const updateAudio = (d) => request.put('/audio/update', d)
export const deleteAudio = (id) => request.delete(`/audio/${id}`)
export const playAudio = (id) => request.post(`/audio/play/${id}`)

// AI 关键词
export const getAiWordsList = (params) => request.get('/admin/aiWords/list', { params })
export const getAiWordsCategories = () => request.get('/admin/aiWords/categories')
export const saveAiWords = (d) => request.post('/admin/aiWords/save', d)
export const updateAiWords = (d) => request.put('/admin/aiWords/update', d)
export const deleteAiWords = (id) => request.delete(`/admin/aiWords/${id}`)

// AI 聊天（以会话 sessionId 为单位）
export const chatWithAi = (sessionId, userMsg) => request.post('/aiChat/chat', { sessionId, userMsg })
export const getAiSessions = () => request.get('/aiChat/session/list')
export const createAiSession = () => request.post('/aiChat/session/create', {})
export const getAiSessionMessages = (sessionId) => request.get('/aiChat/session/messages', { params: { sessionId } })
export const deleteAiSession = (sessionId) => request.delete(`/aiChat/session/${encodeURIComponent(sessionId)}`)
export const regenerateAiMessage = (chatId) => request.post(`/aiChat/${chatId}/regenerate`)
export const deleteAiChat = (id) => request.delete(`/aiChat/${id}`)

// 文件上传（日记配图等）
export const uploadFile = (file, dir = 'diary') => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('dir', dir)
  return request.post('/upload/file', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/**
 * 翻页拉全一个列表：后端 PageLimit 把单页 size 封顶 100，
 * 直接传 size:200/500 会被静默截断成 100 条（列表满 100 后统计数字就"再也不动"了）。
 * 用法：const all = await fetchAllPages(p => getDiaryList({ page: p, size: 100, userId }))
 */
export async function fetchAllPages (fetcher, { maxPages = 50 } = {}) {
  const out = []
  for (let page = 1; page <= maxPages; page++) {
    const res = await fetcher(page)
    const records = res?.data?.records || res?.data || []
    out.push(...records)
    // 返回不满一页（或达到 total）说明到底了
    if (!Array.isArray(records) || records.length < 100) break
    const total = res?.data?.total
    if (total && out.length >= total) break
  }
  return out
}

// 日记
export const getDiaryList = (params = { page: 1, size: 100 }) => request.get('/diary/list', { params })
export const saveDiary = (d) => request.post('/diary/save', d)
export const updateDiary = (d) => request.put('/diary/update', d)
export const deleteDiary = (id) => request.delete(`/diary/${id}`)
// 把「系统代记」补一句话转成亲笔日记（source 3 → 1）
export const claimDiary = (id, data) => request.put(`/diary/${id}/claim`, data)
// 睡眠补录：近 7 天预填 + 按日期批量写 sleep_hours
export const getSleepWeek = () => request.get('/diary/sleep/week')
export const batchSleep = (items) => request.post('/diary/sleep/batch', items)
export const generateWhisper = (userId) => request.get('/diary/whisper', { params: { userId } })

// 评论
export const getCommentList = (params) => request.get('/comment/list', { params })
export const getCommentListByResource = (params) => request.get('/comment/listByResource', { params })
// 我的评论（跨音频/视频聚合，带审核状态）
export const getMyComments = (params) => request.get('/comment/my', { params })
export const getAllCommentsWithUser = (params) => request.get('/comment/listAllWithUser', { params })
export const saveComment = (d) => request.post('/comment/save', d)
export const updateComment = (d) => request.put('/comment/update', d)
export const updateCommentStatus = (commentId, status) => request.put('/comment/updateStatus', null, { params: { commentId, status } })
export const deleteComment = (id) => request.delete(`/comment/${id}`)
export const batchUpdateCommentStatus = (commentIds, status) => request.put('/comment/batchUpdateStatus', commentIds, { params: { status } })
export const batchDeleteComment = (commentIds) => request.delete('/comment/batchDelete', { data: commentIds })

// 点赞
export const getLikeList = (params) => request.get('/like/list', { params })
export const saveLike = (d) => request.post('/like/save', d)
export const cancelLike = (params) => request.delete('/like/removeByResource', { params })
export const getLikeCount = (params) => request.get('/like/count', { params })

// 收藏
export const getCollectList = (params) => request.get('/collect/list', { params })
export const saveCollect = (d) => request.post('/collect/save', d)
export const cancelCollect = (params) => request.delete('/collect/removeByResource', { params })
export const getCollectCount = (params) => request.get('/collect/count', { params })

// 反馈
export const saveFeedback = (d) => request.post('/feedback/save', d)
export const getFeedbackList = (params = { page: 1, size: 50 }) => request.get('/feedback/list', { params })
// 「我的反馈」走 /feedback/my（/feedback/list 是管理员接口，普通用户会 403）
export const getMyFeedbackList = (params = { page: 1, size: 50 }) => request.get('/feedback/my', { params })
export const getFeedbackMessages = (id) => request.get(`/feedback/${id}/messages`)
export const updateFeedback = (d) => request.put('/feedback/update', d)
export const replyFeedback = (d) => request.put('/feedback/reply', d)
export const supplementFeedback = (d) => request.put('/feedback/supplement', d)
export const deleteFeedback = (id) => request.delete(`/feedback/${id}`)
export const softDeleteFeedback = (id) => request.put(`/feedback/soft-delete/${id}`)

// ECharts 统计
export const getOverview = () => request.get('/statistics/overview')
export const getResourceStats = () => request.get('/statistics/resource')
export const getVideoRank = () => request.get('/statistics/videoRank')

// 个人统计（「我的数据看板」用，userId 由后端从 token 取）
export const getMyOverview = () => request.get('/statistics/my/overview')
export const getMyTrend = (days = 30) => request.get('/statistics/my/trend', { params: { days } })
export const getMyHeatmap = (year) => request.get('/statistics/my/heatmap', { params: year ? { year } : {} })
export const getMyEmotion = () => request.get('/statistics/my/emotion')
export const getMyTestTrend = () => request.get('/statistics/my/testTrend')
export const getMyMonthly = (months = 6) => request.get('/statistics/my/monthly', { params: { months } })
// ===== 进阶图表（Step 3）：散点回归 / 桑基流向 / 词云关键词 =====
export const getMyScatter = (days = 90) => request.get('/statistics/my/scatter', { params: { days } })
export const getMySankey = (days = 90) => request.get('/statistics/my/sankey', { params: { days } })
export const getMyKeywords = (days = 180) => request.get('/statistics/my/keywords', { params: { days } })

// 数据大盘统一统计接口
export const getDashboardStats = () => request.get('/admin/dashboard/stats')

// ===== 后台扩展管理（三大创新模块 + 老功能补齐）=====
// 1. 情绪风险预警
export const adminAlertStats = () => request.get('/admin/alert/stats')
export const adminAlertList = (params) => request.get('/admin/alert/list', { params })
// 导出预警报表（Excel）：responseType 必须 blob，拦截器对 Blob 原样透传
export const adminAlertExport = (params) => request.get('/admin/alert/export', { params, responseType: 'blob' })
export const adminAlertDetail = (id) => request.get(`/admin/alert/${id}`)
export const adminAlertMarkRead = (id) => request.post(`/admin/alert/${id}/read`)
// 管理员处置预警（轻量闭环）：disposition = 已电话联系 / 已通知辅导员 / 已上门面谈，note 备注可空
export const adminAlertDisposition = (id, data) => request.post(`/admin/alert/${id}/disposition`, data)
// 预警词库管理（危机词/预警词/例外短语）：改完内存立即生效 + sys_config 持久化，无需重启
export const adminRiskWords = () => request.get('/admin/alert/words')
export const adminRiskWordAdd = (type, word) => request.post('/admin/alert/words', { type, word })
export const adminRiskWordRemove = (type, word) => request.post('/admin/alert/words/delete', { type, word })
// 评论自动审核设置（可疑词库 + 总开关）：改完内存立即生效 + sys_config 持久化，无需重启
export const adminCommentWords = () => request.get('/admin/comment/words')
export const adminCommentWordAdd = (word) => request.post('/admin/comment/words', { word })
export const adminCommentWordRemove = (word) => request.post('/admin/comment/words/delete', { word })
// 2. 轻量无感记录
export const adminSenseStats = () => request.get('/admin/sense/stats')
export const adminSensePredictions = (params) => request.get('/admin/sense/predictions', { params })
// 3. 情绪词典维护
export const adminLexiconStats = () => request.get('/admin/lexicon/stats')
export const adminLexiconList = (params) => request.get('/admin/lexicon/list', { params })
export const adminLexiconSave = (d) => request.post('/admin/lexicon/save', d)
export const adminLexiconDelete = (id) => request.delete(`/admin/lexicon/${id}`)
export const adminLexiconImport = (text) => request.post('/admin/lexicon/import', { text })
// 4. 系统运行参数
export const adminSettingsGet = () => request.get('/admin/settings')
export const adminSettingsSave = (d) => request.put('/admin/settings', d)
export const adminSettingsReset = (group) => request.post('/admin/settings/reset', null, group ? { params: { group } } : undefined)
// 5. AI 对话记录
export const adminAiChatStats = () => request.get('/admin/aiChat/stats')
export const adminAiChatUsers = (params) => request.get('/admin/aiChat/users', { params })
export const adminAiChatSessions = (userId) => request.get('/admin/aiChat/sessions', { params: { userId } })
export const adminAiChatMessages = (sessionId) => request.get('/admin/aiChat/messages', { params: { sessionId } })
// 6. 测评答题记录
export const adminRecordStats = () => request.get('/admin/record/stats')
export const adminRecordList = (params) => request.get('/admin/record/list', { params })
// 7. 账号安全
export const adminSecurityOverview = () => request.get('/admin/security/overview')
export const adminSecuritySessions = (params) => request.get('/admin/security/sessions', { params })
export const adminSecurityKick = (id) => request.post(`/admin/security/kick/${id}`)
export const adminSecurityAttempts = () => request.get('/admin/security/attempts')
export const adminSecurityUnlock = (id) => request.post(`/admin/security/unlock/${id}`)
export const adminSecurityResets = () => request.get('/admin/security/resets')
export const adminSecurityResetDelete = (id) => request.delete(`/admin/security/reset/${id}`)

// 8. 隐私审计日志
export const adminPrivacyStats = () => request.get('/admin/privacy/stats')
export const adminPrivacyList = (params) => request.get('/admin/privacy/list', { params })
// 9. 用户 360 画像
export const adminUserProfile = (id) => request.get(`/admin/user/${id}/profile`)

// 头像审核：只针对用户自行上传的头像，可一键重置为系统默认头像
export const adminAvatarStats = () => request.get('/admin/avatar/stats')
export const adminAvatarList = (params) => request.get('/admin/avatar/list', { params })
export const adminAvatarReset = (userId) => request.post('/admin/avatar/reset', { userId })
// 10. 互动与内容消费
export const adminInteractionStats = () => request.get('/admin/interaction/stats')
export const adminInteractionHot = (params) => request.get('/admin/interaction/hot', { params })
export const adminInteractionHistory = (params) => request.get('/admin/interaction/history', { params })
// 11. 系统操作日志
export const adminLogStats = () => request.get('/admin/log/stats')
export const adminLogList = (params) => request.get('/admin/log/list', { params })
export const adminLogClear = (days) => request.post('/admin/log/clear', null, { params: { days } })
// 12. 首页轮播 / 公告位（后台维护）
export const adminBannerList = (params) => request.get('/admin/banner/list', { params })
export const adminBannerSave = (d) => request.post('/admin/banner/save', d)
export const adminBannerStatus = (id, status) => request.post(`/admin/banner/status/${id}`, null, { params: { status } })
export const adminBannerDelete = (id) => request.delete(`/admin/banner/${id}`)
// 首页轮播（前台公开读取，游客可见）
export const getBannerList = (position) => request.get('/banner/list', { params: { position: position || 'home' } })

export default request