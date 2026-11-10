<template>
  <view class="page">
    <!-- 顶部：品牌 + 会话入口 -->
    <view class="top-bar">
      <view class="brand">
        <image class="brand-icon" src="/static/emo/q-ai.png" mode="aspectFit" />
        <view class="brand-info">
          <text class="brand-name">绪见AI</text>
          <text class="brand-sub">💫 你的专属情绪陪伴助手</text>
        </view>
      </view>
      <view class="sess-btn" @click="openSessions">会话</view>
    </view>

    <!-- 消息流 -->
    <scroll-view class="chat" scroll-y :scroll-top="scrollTop" scroll-with-animation>
      <!-- 欢迎条（与 H5 一致） -->
      <view class="welcome">
        <text class="welcome-title">👋 你好，我是绪见AI</text>
        <text class="welcome-sub">开心或难过都可以和我说说，我会认真听</text>
      </view>

      <view v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
        <view class="bubble" :class="m.role">
          <text class="bubble-text">{{ m.content }}</text>
        </view>
      </view>

      <view v-if="sending" class="msg-row ai">
        <view class="bubble ai typing"><text class="bubble-text">正在输入…</text></view>
      </view>
      <view class="chat-bottom-pad" />
    </scroll-view>

    <!-- 输入区 -->
    <view class="input-bar">
      <input
        class="input"
        v-model="text"
        placeholder="输入你想说的话…"
        placeholder-class="ph"
        confirm-type="send"
        :disabled="sending"
        @confirm="send"
      />
      <view class="send" :class="{ sending }" @click="send">{{ sending ? '…' : '发送' }}</view>
    </view>

    <!-- 会话半屏弹层：新建 / 切换 / 删除 -->
    <view v-if="sessOpen" class="mask" @click="sessOpen = false" />
    <view class="sheet" :class="{ open: sessOpen }">
      <view class="sheet-head">
        <text class="sheet-title">聊天会话</text>
        <view class="new-btn" @click="onCreateSession">＋ 新对话</view>
      </view>
      <scroll-view scroll-y class="sheet-list">
        <view
          v-for="s in sessions"
          :key="s.sessionId"
          class="sess-item"
          :class="{ active: s.sessionId === sessionId }"
          @click="onSwitchSession(s)"
        >
          <view class="sess-info">
            <text class="sess-title">{{ s.title || '新对话' }}</text>
            <text class="sess-time">{{ fmtTime(s.lastTime) }}</text>
          </view>
          <view class="sess-del" @click.stop="onDeleteSession(s)">删除</view>
        </view>
        <view v-if="sessions.length === 0" class="sess-empty">暂无会话，点右上「＋ 新对话」开始</view>
        <view class="sheet-pad" />
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import {
  getAiSessions, createAiSession, getAiSessionMessages, deleteAiSession, chatWithAi,
} from '../../api'
import { ensureLogin } from '../../utils/storage'

const messages = ref([])
const text = ref('')
const sending = ref(false)
const sessionId = ref('')
const scrollTop = ref(0)

// 会话管理
const sessions = ref([])
const sessOpen = ref(false)

function push(role, content) {
  messages.value.push({ role, content })
  nextTick(() => { scrollTop.value = messages.value.length * 1000 + 100 })
}

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 拉会话列表（标题/最后时间由后端给出） */
async function loadSessions() {
  const res = await getAiSessions()
  if (res?.code === 200) sessions.value = res.data || []
}

function openSessions() {
  sessOpen.value = true
  loadSessions()
}

/** 切换会话：清空当前消息，载入目标会话历史 */
async function onSwitchSession(s) {
  if (s.sessionId === sessionId.value) { sessOpen.value = false; return }
  sessOpen.value = false
  sessionId.value = s.sessionId
  messages.value = []
  await loadHistory()
}

/** 新建会话并切换过去 */
async function onCreateSession() {
  const res = await createAiSession()
  const sid = res?.data?.sessionId
  if (!sid) { uni.showToast({ title: res?.msg || '创建失败', icon: 'none' }); return }
  sessOpen.value = false
  sessionId.value = sid
  messages.value = []
  sessions.value = [{ sessionId: sid, title: '新对话', lastTime: null }, ...sessions.value]
}

/** 删除会话（二次确认）；删的是当前会话时回到最近一个会话 */
function onDeleteSession(s) {
  uni.showModal({
    title: '删除会话',
    content: '删除后这轮对话记录将一并清除，确定删除吗？',
    confirmColor: '#e5484d',
    success: async (r) => {
      if (!r.confirm) return
      const res = await deleteAiSession(s.sessionId)
      if (res?.code !== 200) { uni.showToast({ title: res?.msg || '删除失败', icon: 'none' }); return }
      uni.showToast({ title: '已删除', icon: 'none' })
      await loadSessions()
      if (s.sessionId === sessionId.value) {
        sessionId.value = ''
        messages.value = []
        const first = sessions.value[0]
        if (first) await onSwitchSession(first)
      }
    },
  })
}

/** 载入当前会话历史（后端按 sessionId 重建，重启不失忆） */
async function loadHistory() {
  if (!sessionId.value) return
  const his = await getAiSessionMessages(sessionId.value)
  const arr = his?.data || []
  for (const m of arr) {
    push('user', m.userMsg || m.userMessage || '')
    if (m.aiReply || m.aiMsg) push('ai', m.aiReply || m.aiMsg)
  }
}

/** 首次进入：优先复用最近的会话，没有则新建 */
async function ensureSession() {
  if (sessionId.value) return
  await loadSessions()
  const first = sessions.value[0]
  if (first?.sessionId) {
    sessionId.value = first.sessionId
  } else {
    const c = await createAiSession()
    sessionId.value = c?.data?.sessionId || ''
    if (sessionId.value) sessions.value = [{ sessionId: sessionId.value, title: '新对话', lastTime: null }]
  }
  await loadHistory()
}

async function send() {
  const content = text.value.trim()
  if (!content || sending.value) return
  text.value = ''
  push('user', content)

  sending.value = true
  try {
    await ensureSession()
    const res = await chatWithAi(sessionId.value, content)
    if (res?.code === 200) {
      const reply = res.data?.aiReply || res.data?.reply || res.data?.content || '（没有收到回复）'
      push('ai', reply)
    } else if (res?.code === 401) {
      push('ai', '登录已过期，请回「我的」页重新登录后再聊～')
    } else {
      // 失败时明确提示，而不是静默
      push('ai', `抱歉，我暂时没能回应（${res?.msg || '服务不可用'}）。你可以稍后再试。`)
    }
  } catch (e) {
    push('ai', '网络似乎开小差了，请检查后端是否启动，再试一次～')
  } finally {
    sending.value = false
  }
}

onLoad(async () => {
  try { await ensureSession() } catch { /* 会话加载失败不阻塞输入 */ }
})
onShow(() => {
  if (!ensureLogin()) return
  if (!sessionId.value) ensureSession().catch(() => {})
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  /* 关键：不让子元素把容器撑开，否则 scroll-view 拿不到确定高度就没法滚动 */
  overflow: hidden;
  background: transparent;
}

/* ===== 顶部品牌条 ===== */
.top-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx 12rpx;
}
.brand {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex: 1;
}
.brand-icon {
  width: 72rpx;
  height: 72rpx;
}
.brand-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #3d5145;
}
.brand-sub {
  display: block;
  margin-top: 2rpx;
  font-size: 22rpx;
  color: #8a9a8a;
}
.sess-btn {
  padding: 12rpx 28rpx;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 32rpx;
  font-size: 24rpx;
  color: #44604a;
  box-shadow: 0 8rpx 18rpx -12rpx rgba(80, 110, 80, 0.4);
}
.sess-btn:active { transform: scale(0.96); }

/* ===== 消息流 ===== */
.chat {
  flex: 1;
  /* scroll-view 作为 flex 子项时必须给 min-height:0，否则内容会把它撑高而不是内部滚动 */
  min-height: 0;
  padding: 8rpx 24rpx 0;
  box-sizing: border-box;
  width: 100%;
}
.welcome {
  padding: 24rpx 28rpx;
  margin-bottom: 24rpx;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 24rpx;
}
.welcome-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #3d5145;
}
.welcome-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a9a8a;
  line-height: 1.6;
}
.msg-row {
  display: flex;
  margin-bottom: 24rpx;
  width: 100%;
}
.msg-row.user {
  justify-content: flex-end;
}
.bubble {
  /* 盒模型必须含内边距，否则 max-width 加上 padding 会把气泡顶出屏幕 */
  box-sizing: border-box;
  max-width: 72%;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  line-height: 1.7;
}
.bubble.ai {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-top-left-radius: 4rpx;
}
.bubble.user {
  background: #5aa96c;
  color: #ffffff;
  border-top-right-radius: 4rpx;
  box-shadow: 0 10rpx 20rpx -12rpx rgba(90, 169, 108, 0.6);
}
.bubble.typing {
  opacity: 0.7;
}
.bubble-text {
  display: block;
  word-break: break-all;
}
.chat-bottom-pad {
  height: 24rpx;
}

/* ===== 输入区 ===== */
.input-bar {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.85);
  border-top: 1rpx solid rgba(140, 160, 140, 0.18);
}
.input {
  flex: 1;
  height: 80rpx;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(140, 160, 140, 0.18);
  border-radius: 40rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}
.ph {
  color: #b5c4b5;
}
.send {
  width: 130rpx;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  background: #5aa96c;
  color: #ffffff;
  border-radius: 40rpx;
  font-size: 28rpx;
  box-shadow: 0 10rpx 20rpx -12rpx rgba(90, 169, 108, 0.7);
}
.send.sending { opacity: 0.6; }

/* ===== 会话半屏弹层 ===== */
.mask {
  position: fixed;
  /* 不用 inset 简写：低版本小程序基础库不认，遮罩会退化成 0 尺寸（点空白处关不掉弹层） */
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(45, 65, 45, 0.3);
  z-index: 100;
}
.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 62vh;
  background: #fdfefc;
  border-radius: 36rpx 36rpx 0 0;
  z-index: 101;
  transform: translateY(100%);
  transition: transform 0.28s ease;
  display: flex;
  flex-direction: column;
}
.sheet.open {
  transform: translateY(0);
}
.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 28rpx 20rpx;
  border-bottom: 1rpx solid rgba(140, 160, 140, 0.18);
}
.sheet-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #3d5145;
}
.new-btn {
  padding: 12rpx 26rpx;
  background: #5aa96c;
  color: #fff;
  border-radius: 32rpx;
  font-size: 24rpx;
}
.sheet-list {
  flex: 1;
  padding: 12rpx 24rpx;
  box-sizing: border-box;
}
.sess-item {
  display: flex;
  align-items: center;
  padding: 22rpx 20rpx;
  border-radius: 20rpx;
  margin-bottom: 10rpx;
  background: rgba(246, 249, 246, 0.9);
  border: 1px solid rgba(140, 160, 140, 0.14);
}
.sess-item.active {
  background: #eaf6ec;
  border-color: rgba(90, 169, 108, 0.4);
}
.sess-info {
  flex: 1;
  min-width: 0;
}
.sess-title {
  display: block;
  font-size: 27rpx;
  color: #3f4f3f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sess-item.active .sess-title {
  font-weight: 600;
  color: #2f7a3e;
}
.sess-time {
  display: block;
  margin-top: 4rpx;
  font-size: 21rpx;
  color: #a3ada6;
}
.sess-del {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 8rpx 20rpx;
  font-size: 22rpx;
  color: #e5484d;
  background: rgba(229, 72, 77, 0.08);
  border-radius: 20rpx;
}
.sess-empty {
  padding: 80rpx 0;
  text-align: center;
  color: #a3ada6;
  font-size: 26rpx;
}
.sheet-pad {
  height: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
