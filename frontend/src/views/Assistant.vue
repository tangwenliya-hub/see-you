<template>
  <div class="ai-chat-page">
    <!-- 侧边栏会话列表 -->
    <aside class="session-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <button class="new-chat-btn jelly-btn" @click="createNewSession">
          <span class="plus-icon">+</span>
          <span v-if="!sidebarCollapsed">新对话</span>
        </button>
        <button class="toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <span v-if="sidebarCollapsed">&rarr;</span>
          <span v-else>&larr;</span>
        </button>
      </div>
      <div class="session-list" v-if="!sidebarCollapsed">
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: session.sessionId === currentSessionId }"
          @click="switchSession(session.sessionId)"
          @contextmenu.prevent="handleContextMenu(session.sessionId, $event)"
        >
          <div class="session-title">{{ session.title || '新对话' }}</div>
          <div class="session-preview">{{ getLastMessagePreview(session) }}</div>
          <div class="session-time">{{ formatSessionTime(session.lastTime) }}</div>
        </div>
        <div v-if="sessions.length === 0" class="session-empty">暂无会话</div>
      </div>
      <!-- 移动端/窄屏长按删除提示 -->
      <div
        v-if="contextMenuVisible"
        class="context-menu"
        :style="{ top: contextMenuY + 'px', left: contextMenuX + 'px' }"
      >
        <button class="context-menu-item" @click="confirmDeleteSession(contextMenuSessionId)">
          删除会话
        </button>
      </div>
    </aside>

    <!-- 主聊天区域 -->
    <div class="chat-main">
      <!-- 顶部标题栏 -->
      <header class="chat-header">
        <div class="header-left">
          <img src="/img/AI对话历史.png" class="header-icon" alt="" />
          <div class="header-info">
            <h2 class="header-title">绪见AI</h2>
            <p class="header-sub">💫 你的专属情绪陪伴助手</p>
          </div>
        </div>
        <div class="header-right">
          <!-- 导出按钮 -->
          <button class="export-btn" @click="exportChat" title="导出聊天记录">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
          </button>
          <!-- 用户信息 -->
          <div class="user-info-bar">
            <div class="user-avatar-wrap" :class="['frame-' + userFrame]">
              <img :src="userAvatar" class="user-avatar-mini" alt="我的头像" @error="onAvatarError" />
              <img v-if="currentFrameImg" :src="currentFrameImg" class="frame-ring-img" :style="{ width: currentFrameScale + '%', height: currentFrameScale + '%' }" @error="$event.target.style.display='none'" />
            </div>
            <span class="user-name">{{ userName }}</span>
          </div>
          <div class="status-badge">
            <span class="online-dot"></span>
            <span class="online-text">AI 在线</span>
          </div>
        </div>
      </header>

      <!-- 聊天消息区域 -->
      <div class="chat-messages" ref="messagesRef">
        <!-- 欢迎消息 -->
        <div v-if="messages.length === 0" class="welcome-box">
          <div class="welcome-avatar">
            <img src="/img/AI对话历史.png" alt="绪见AI" />
          </div>
          <h3>你好呀~ 我是绪见AI 🌸</h3>
          <p>有什么心事都可以和我说哦，我会一直陪着你的~</p>
          
          <!-- 功能入口 -->
          <div class="welcome-actions">
            <button class="action-btn" @click="openDiaryPicker">
              <img src="/img/绪见日记.png" class="action-img-icon" alt="" />
              <span class="action-text">选日记给AI</span>
            </button>
            <button class="action-btn" @click="openTestPicker">
              <img src="/img/测评报告.png" class="action-img-icon" alt="" />
              <span class="action-text">选测评给AI</span>
            </button>
          </div>

          <div class="quick-questions">
            <button v-for="q in quickQuestions" :key="q" class="quick-btn" @click="sendQuickMsg(q)">
              {{ q }}
            </button>
          </div>
        </div>

        <!-- 写日记面板 -->
        <div v-if="showDiaryPanel" class="diary-panel-wrap">
          <div class="diary-panel">
            <div class="diary-panel-header">
              <h4>✍️ 写日记给AI</h4>
              <button class="close-btn" @click="closeDiaryPanel">✕</button>
            </div>
            
            <div class="diary-form">
              <div class="form-label">今天的心情 <span class="required">*</span></div>
              <div class="emotion-picker">
                <div
                  v-for="emo in emotions"
                  :key="emo.val"
                  class="emo-item"
                  :class="{ active: diaryForm.emotion === emo.val }"
                  :style="{ '--emo-bg': emo.bg }"
                  @click="diaryForm.emotion = emo.val"
                >
                  <img :src="emo.icon" :alt="emo.label" />
                  <span>{{ emo.label }}</span>
                </div>
              </div>

              <div class="form-label">想说的话 <span class="required">*</span></div>
              <textarea
                v-model="diaryForm.content"
                class="diary-textarea"
                placeholder="把今天的心情写下来，我会认真听的~"
                rows="4"
              ></textarea>
            </div>

            <div class="diary-panel-footer">
              <button class="cancel-btn" @click="closeDiaryPanel">取消</button>
              <button
                class="submit-diary-btn"
                :disabled="!diaryForm.content.trim() || isSendingDiary"
                @click="sendDiaryToAi"
              >
                <span v-if="isSendingDiary">发送中...</span>
                <span v-else>💌 保存并发送给AI</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="(msg, idx) in decoratedMessages"
          :key="(msg.chatId || 'tmp') + '_' + msg.role + '_' + idx"
          :class="['msg-item', msg.role]"
          @mouseenter="hoveredMsgIndex = idx"
          @mouseleave="hoveredMsgIndex = -1"
        >
          <div v-if="msg.role === 'ai'" class="ai-avatar-wrap">
            <img src="/img/AI对话历史.png" alt="AI" class="ai-avatar-big" />
          </div>
          <div v-else class="msg-avatar user-avatar-frame">
            <img :src="userAvatar" alt="我" class="msg-user-avatar" />
            <img v-if="currentFrameImg" :src="currentFrameImg" class="frame-ring-img" :style="{ width: currentFrameScale + '%', height: currentFrameScale + '%' }" @error="$event.target.style.display='none'" />
          </div>
          <div class="msg-bubble">
            <div class="msg-content">
              <template v-if="isImageMsg(msg)">
                <img :src="imageUrlOf(msg)" class="chat-image" alt="图片消息" />
              </template>
              <template v-else-if="msg.role === 'ai'">
                <span v-html="highlightKeywords(msg.content)"></span>
              </template>
              <template v-else>
                {{ msg.content }}
              </template>
            </div>
            <div class="msg-time">{{ formatTime(msg.time) }}</div>
            <!-- 消息操作按钮 -->
            <div v-if="hoveredMsgIndex === idx" class="msg-actions">
              <button class="msg-action-btn" @click="quoteMessage(msg)" title="引用">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/>
                  <path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/>
                </svg>
                <span>引用</span>
              </button>
              <button class="msg-action-btn" @click="copyMessage(msg.content)" title="复制">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
                <span>复制</span>
              </button>
              <button class="msg-action-btn" @click="deleteMessage(msg.chatId)" title="删除这一轮对话">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
                <span>删除</span>
              </button>
              <button v-if="msg.role === 'ai'" class="msg-action-btn" @click="regenerateMessage(msg.chatId)" title="重新生成">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="23 4 23 10 17 10"/>
                  <polyline points="1 20 1 14 7 14"/>
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                </svg>
                <span>重新生成</span>
              </button>
            </div>
            <!-- AI主动推荐（依据你这一轮说的话，而不是 AI 的回复） -->
            <div v-if="msg.recs && msg.recs.length > 0" class="ai-recommendations">
              <span class="rec-label">相关测评推荐：</span>
              <span
                v-for="rec in msg.recs"
                :key="rec"
                class="rec-tag"
                @click="goToTest(rec)"
              >
                {{ rec }}
              </span>
            </div>
          </div>
        </div>

        <!-- AI正在输入 -->
        <div v-if="isTyping" class="msg-item ai">
          <div class="ai-avatar-wrap">
            <img src="/img/AI对话历史.png" alt="AI" class="ai-avatar-big" />
          </div>
          <div class="msg-bubble typing-bubble">
            <div class="typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入区域 -->
      <div class="chat-input-area">
        <!-- 选择日记面板 -->
        <div v-if="showDiaryPicker" class="bottom-picker-panel">
          <div class="picker-panel-header">
            <h4><img src="/img/绪见日记.png" class="picker-title-icon" alt="" /> 选择一篇日记发给AI</h4>
            <button class="close-btn" @click="closeDiaryPicker">✕</button>
          </div>
          <div class="picker-list">
            <div v-if="loadingDiary" class="loading-tip"><CowLoading size="sm" text="加载中" /></div>
            <div v-else-if="diaryList.length === 0" class="empty-tip">
              <p>📭 还没有日记哦~</p>
              <router-link to="/diary" class="link-btn" @click="closeDiaryPicker">去写一篇</router-link>
            </div>
            <div
              v-for="diary in diaryList"
              :key="diary.diaryId"
              class="picker-item diary-item"
              @click="selectDiary(diary)"
            >
              <div class="item-emotion" :style="{ background: getEmotionBg(diary.emotion) }">
                <img :src="getEmotionIcon(diary.emotion)" alt="" />
              </div>
              <div class="item-content">
                <div class="item-text">{{ diary.diaryContent }}</div>
                <div class="item-date">{{ diary.diaryDate }}</div>
              </div>
              <span class="item-arrow">→</span>
            </div>
          </div>
        </div>

        <!-- 选择测评面板 -->
        <div v-if="showTestPicker" class="bottom-picker-panel">
          <div class="picker-panel-header">
            <h4><img src="/img/测评报告.png" class="picker-title-icon" alt="" /> 选择一份测评发给AI</h4>
            <button class="close-btn" @click="closeTestPicker">✕</button>
          </div>
          <div class="picker-list">
            <div v-if="loadingTest" class="loading-tip"><CowLoading size="sm" text="加载中" /></div>
            <div v-else-if="testList.length === 0" class="empty-tip">
              <p>📭 还没有测评记录哦~</p>
              <router-link to="/test" class="link-btn" @click="closeTestPicker">去做测评</router-link>
            </div>
            <div
              v-for="record in testList"
              :key="record.recordId"
              class="picker-item test-item"
              @click="selectTest(record)"
            >
              <div class="item-icon test-icon">📊</div>
              <div class="item-content">
                <div class="item-title">{{ record.testName || '心理测评' }}</div>
                <div class="item-desc">得分：{{ record.score }}分 · {{ formatDate(record.createTime) }}</div>
              </div>
              <span class="item-arrow">→</span>
            </div>
          </div>
        </div>

        <!-- 快捷功能按钮 -->
        <div class="quick-actions-bar">
          <button class="quick-action-btn" @click="showDiaryPanel = true">
            <span class="qa-icon">✍️</span>
            <span class="qa-text">写日记</span>
          </button>
          <button class="quick-action-btn" @click="openDiaryPicker">
            <img src="/img/绪见日记.png" class="qa-img-icon" alt="" />
            <span class="qa-text">选日记</span>
          </button>
          <button class="quick-action-btn" @click="openTestPicker">
            <img src="/img/测评报告.png" class="qa-img-icon" alt="" />
            <span class="qa-text">选测评</span>
          </button>
        </div>
        <!-- 录音中指示器（替代突兀的 ElMessage 白色胶囊） -->
        <transition name="rec-fade">
          <div v-if="isRecording" class="recording-pill" @click="toggleVoiceInput">
            <span class="rec-mic">🎙️</span>
            <span class="rec-bars"><i></i><i></i><i></i><i></i><i></i></span>
            <span class="rec-text">正在聆听，说出你的心情…</span>
            <span class="rec-stop">点击结束</span>
          </div>
        </transition>
        <div class="input-wrapper">
          <!-- 图片上传按钮 -->
          <button class="image-btn" @click="triggerImageUpload" title="发送图片">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21 15 16 10 5 21"/>
            </svg>
          </button>
          <input
            type="file"
            accept="image/*"
            hidden
            ref="imageInput"
            @change="handleImageUpload"
          />
          <!-- 语音输入按钮 -->
          <button
            class="voice-btn"
            :class="{ recording: isRecording }"
            @click="toggleVoiceInput"
            title="语音输入"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              <line x1="12" y1="19" x2="12" y2="23"/>
              <line x1="8" y1="23" x2="16" y2="23"/>
            </svg>
            <span v-if="isRecording" class="recording-dot"></span>
          </button>
          <textarea
            v-model="inputText"
            class="chat-input"
            placeholder="说说你的心情吧..."
            rows="1"
            @keydown.enter.exact.prevent="sendMessage"
            @input="autoResize"
            ref="inputRef"
          ></textarea>
          <button class="send-btn" :disabled="!inputText.trim() || isTyping" @click="sendMessage">
            发送
          </button>
        </div>
        <p class="input-tip">💡 试着说"我今天心情不好"、"我好焦虑"...</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { EMOTIONS as emotions } from '@/utils/constants'  // 情绪字典唯一真源（utils/constants.js）
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import {
  chatWithAi, getAiSessions, createAiSession, getAiSessionMessages, deleteAiSession,
  regenerateAiMessage, deleteAiChat, uploadFile,
  saveDiary, getDiaryList, getRecordList, getUserById, getTestPaperList
} from '@/api'
import CowLoading from '@/components/CowLoading.vue'

const router = useRouter()

// ========== 会话管理（会话与消息统一由后端按 sessionId 存储）==========
const DRAFT_PREFIX = 'AI_DRAFT_'
const sessions = ref([])
const currentSessionId = ref('')
const sidebarCollapsed = ref(false)

const draftKey = (sid) => DRAFT_PREFIX + sid

const saveDraft = () => {
  if (!currentSessionId.value) return
  try { localStorage.setItem(draftKey(currentSessionId.value), inputText.value || '') } catch (e) { /* 忽略配额异常 */ }
}

// 加载会话列表（后端）
const loadSessions = async () => {
  try {
    const res = await getAiSessions()
    sessions.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
  } catch (e) {
    sessions.value = []
  }
}

// 创建新会话（后端分配 sessionId）
const createNewSession = async () => {
  try {
    const res = await createAiSession()
    const sid = res?.data?.sessionId
    if (!sid) {
      ElMessage.error('新建会话失败，请稍后重试')
      return
    }
    currentSessionId.value = sid
    sessions.value.unshift({ sessionId: sid, title: '新对话', preview: '', lastTime: null, rounds: 0 })
    messages.value = []
    inputText.value = ''
    nextTick(() => {
      autoResize()
      scrollToBottom()
    })
  } catch (e) {
    ElMessage.error('新建会话失败，请检查网络')
  }
}

// 确保当前有会话可用（发消息 / 选日记 / 选测评前调用）
const ensureSession = async () => {
  if (!currentSessionId.value) await createNewSession()
  return currentSessionId.value
}

// 拉取某个会话的全部消息（一轮记录展开成 user + ai 两条气泡）
const loadMessages = async (sid) => {
  try {
    const res = await getAiSessionMessages(sid)
    const list = res.code === 200 && Array.isArray(res.data) ? res.data : []
    const flat = []
    list.forEach(c => {
      if (c.userMsg) flat.push({ chatId: c.chatId, role: 'user', content: c.userMsg, time: c.createTime })
      if (c.aiReply) flat.push({ chatId: c.chatId, role: 'ai', content: c.aiReply, time: c.createTime })
    })
    messages.value = flat
  } catch (e) {
    messages.value = []
  }
}

// 切换会话
const switchSession = async (sid) => {
  if (!sid || sid === currentSessionId.value) return
  if (isTyping.value) {
    ElMessage.warning('AI 正在回复中，请稍候再切换')
    return
  }
  saveDraft()
  currentSessionId.value = sid
  await loadMessages(sid)
  inputText.value = localStorage.getItem(draftKey(sid)) || ''
  nextTick(() => {
    autoResize()
    scrollToBottom()
  })
}

// 删除会话（连后端一起删，刷新不会复活）
const confirmDeleteSession = async (sid) => {
  contextMenuVisible.value = false
  const ok = await jellyConfirm('确定要删除这个会话吗？', { title: '删除会话', confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await deleteAiSession(sid)
    try { localStorage.removeItem(draftKey(sid)) } catch (e) { /* 忽略 */ }
    sessions.value = sessions.value.filter(s => s.sessionId !== sid)
    if (currentSessionId.value === sid) {
      if (sessions.value.length > 0) {
        currentSessionId.value = ''
        await switchSession(sessions.value[0].sessionId)
      } else {
        await createNewSession()
      }
    }
    ElMessage.success('会话已删除')
  } catch (e) {
    ElMessage.error('删除失败，请重试')
  }
}

// 获取会话最后消息预览（直接取后端返回的摘要）
const getLastMessagePreview = (session) => session.preview || ''

// 格式化会话时间
const formatSessionTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) {
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  }
  return `${d.getMonth() + 1}-${d.getDate()}`
}

// 右键菜单
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuSessionId = ref('')

const handleContextMenu = (id, e) => {
  contextMenuSessionId.value = id
  contextMenuX.value = e.clientX
  contextMenuY.value = e.clientY
  contextMenuVisible.value = true
}

// 点击页面其他地方关闭右键菜单
const closeContextMenu = () => {
  contextMenuVisible.value = false
}

// ========== 消息操作 ==========
const hoveredMsgIndex = ref(-1)

// 复制消息
const copyMessage = async (content) => {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = content
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制到剪贴板')
  }
}

// 引用消息：把内容以引用格式填进输入框
const quoteMessage = (msg) => {
  const text = msg?.content || ''
  if (!text) return
  const quoted = text.split('\n').map(line => '> ' + line).join('\n')
  inputText.value = (inputText.value ? inputText.value.replace(/\s*$/, '') + '\n' : '') + quoted + '\n'
  nextTick(() => {
    autoResize()
    inputRef.value?.focus()
  })
  ElMessage.success('已引用到输入框')
}

// 删除消息：一条记录 = 一轮对话（用户 + AI 一起删），后端同步删除
const deleteMessage = async (chatId) => {
  if (!chatId) {
    messages.value = messages.value.filter(m => !m.pending)
    return
  }
  try {
    await deleteAiChat(chatId)
    messages.value = messages.value.filter(m => m.chatId !== chatId)
    await loadSessions()
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败，请重试')
  }
}

// 重新生成AI消息：沿用原问题 + 该轮之前的历史，后端重写该轮回复
const regenerateMessage = async (chatId) => {
  if (!chatId) {
    ElMessage.warning('这条消息还没存下来，暂时无法重新生成')
    return
  }
  if (isTyping.value) return
  isTyping.value = true
  scrollToBottom()
  try {
    const res = await regenerateAiMessage(chatId)
    if (res.code === 200 && res.data) {
      const idx = messages.value.findIndex(m => m.role === 'ai' && m.chatId === chatId)
      if (idx >= 0) {
        messages.value[idx] = { ...messages.value[idx], content: res.data.aiReply, time: res.data.createTime }
      }
      await loadSessions()
    } else {
      ElMessage.error(res?.msg || '重新生成失败')
    }
  } catch (e) {
    ElMessage.error('重新生成失败，请稍后重试')
  } finally {
    isTyping.value = false
    scrollToBottom()
  }
}

// ========== AI主动推荐 ==========
// 量表名称必须与数据库 psy_test.test_name 完全一致（命名规则：英文缩写 + 中文名）
const TEST_KEYWORDS = [
  { keywords: ['焦虑', '紧张', '不安', '担心', '恐慌', '心慌'], tests: ['GAD-7 广泛性焦虑量表', 'SAS 焦虑自评量表'] },
  { keywords: ['抑郁', '低落', '沮丧', '绝望', '不开心', '想哭', '没意思'], tests: ['PHQ-9 抑郁筛查量表', 'SDS 抑郁自评量表'] },
  { keywords: ['压力', '压得喘', '好累', '疲惫', '倦怠', '撑不住', ' burnout'], tests: ['PSS-10 压力感知量表', 'MBI-GS 职业倦怠量表'] },
  { keywords: ['失眠', '睡不着', '睡眠', '早醒', '睡不好', '半夜醒'], tests: ['PSQI 匹兹堡睡眠质量指数', 'ISI 失眠严重程度量表'] },
]

// 推荐看「用户说了什么」，而不是 AI 回复里出现了哪些词
const getTestRecommendations = (content) => {
  if (!content) return []
  const lower = content.toLowerCase()
  const recs = []
  TEST_KEYWORDS.forEach(group => {
    if (group.keywords.some(k => lower.includes(k.trim()))) {
      recs.push(...group.tests)
    }
  })
  return [...new Set(recs)].slice(0, 3)
}

// 量表名称 → testId 映射：推荐胶囊点击时直达测评页对应量表详情
const testNameIdMap = ref({})
onMounted(async () => {
  try {
    const res = await getTestPaperList()
    if (res.code === 200) {
      const records = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      records.forEach(r => { testNameIdMap.value[r.testName] = r.testId })
    }
  } catch (e) { /* 拉不到就用名称深链兜底 */ }
})

const goToTest = (name) => {
  if (!name) { router.push('/test'); return }
  const tid = testNameIdMap.value[name]
  // 优先按 testId 直达量表详情；映射里没有再用名称深链
  if (tid) router.push('/test?testId=' + tid)
  else router.push({ path: '/test', query: { paper: name } })
}

// ========== 语音输入 ==========
const isRecording = ref(false)
let recognition = null

const initSpeechRecognition = () => {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) {
    ElMessage.warning('您的浏览器不支持语音输入')
    return null
  }
  const r = new SpeechRecognition()
  r.lang = 'zh-CN'
  r.continuous = true
  r.interimResults = true
  r.onresult = (e) => {
    let final = ''
    let interim = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      if (e.results[i].isFinal) {
        final += e.results[i][0].transcript
      } else {
        interim += e.results[i][0].transcript
      }
    }
    if (final) {
      inputText.value += final
      autoResize()
    }
    if (interim && !final) {
      // 可以显示临时结果，这里直接追加到输入框
      // 为避免闪烁，只在有final时更新
    }
  }
  r.onerror = (e) => {
    console.warn('语音识别错误:', e.error)
    if (e.error !== 'aborted') {
      ElMessage.error('语音识别出错，请重试')
    }
    isRecording.value = false
  }
  r.onend = () => {
    isRecording.value = false
  }
  return r
}

const toggleVoiceInput = () => {
  if (!recognition) {
    recognition = initSpeechRecognition()
    if (!recognition) return
  }
  if (isRecording.value) {
    recognition.stop()
    isRecording.value = false
  } else {
    try {
      recognition.start()
      isRecording.value = true
    } catch (e) {
      console.warn('启动语音识别失败:', e)
      ElMessage.error('启动语音识别失败')
    }
  }
}

// ========== AI关键词高亮 ==========
const KEYWORDS = [
  '焦虑', '抑郁', '压力', '失眠', '孤独', '伤心', '难过', '愤怒', '害怕', '恐惧',
  '疲惫', '迷茫', '空虚', '紧张', '担心', '想哭', '暴食', '厌食', '噩梦', '强迫',
  '创伤', '幻听', '幻觉', '惊恐', 'panic', '自卑', '内疚', '自责', '拖延', '社交恐惧'
]

const highlightKeywords = (text) => {
  if (!text) return ''
  // 先对HTML特殊字符进行转义，防止XSS
  let safeText = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

  // 按关键词长度降序排序，避免短词先匹配导致长词无法匹配
  const sortedKeywords = [...KEYWORDS].sort((a, b) => b.length - a.length)

  sortedKeywords.forEach(keyword => {
    // 构建正则，不区分大小写，全局匹配
    const reg = new RegExp(keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi')
    safeText = safeText.replace(reg, match => `<mark class="keyword-highlight">${match}</mark>`)
  })

  return safeText
}

// ========== 图片上传 ==========
// 图片上传到服务器，以「[图片] url」的形式存进 userMsg，刷新后仍在；
// 发给大模型的会被后端转成文字描述（图片内容分析未实现，避免把裸 URL 丢给模型）
const IMAGE_PREFIX = '[图片] '

const isImageMsg = (msg) => typeof msg.content === 'string' && msg.content.startsWith(IMAGE_PREFIX)
const imageUrlOf = (msg) => isImageMsg(msg) ? msg.content.slice(IMAGE_PREFIX.length).trim() : (msg.imageUrl || '')

const triggerImageUpload = () => {
  imageInput.value?.click()
}

const handleImageUpload = async (e) => {
  const file = e.target.files?.[0]
  // 清空input，允许重复选择同一张图片
  e.target.value = ''
  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }

  // 验证文件大小（最大 5MB）
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }

  await ensureSession()
  isTyping.value = true
  try {
    const up = await uploadFile(file, 'chat')
    const url = up?.data?.url
    if (!url) throw new Error('上传失败')
    isTyping.value = false
    await askAi(IMAGE_PREFIX + url)
  } catch (err) {
    isTyping.value = false
    ElMessage.error('图片上传失败，请重试')
  }
}

// ========== 聊天记录导出 ==========
const exportChat = () => {
  if (messages.value.length === 0) {
    ElMessage.warning('当前会话没有聊天记录')
    return
  }
  const lines = messages.value.map(msg => {
    const role = msg.role === 'user' ? '用户' : 'AI'
    return `[${role}] ${msg.content}`
  })
  const content = lines.join('\n')
  const dateStr = formatDate(new Date())
  const filename = `绪见AI_对话记录_${dateStr}.txt`

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success('聊天记录已导出')
}

// ========== 原有代码 ==========
const messagesRef = ref(null)
const inputRef = ref(null)
const imageInput = ref(null)
const messages = ref([])

// 为每条 AI 消息预计算测评推荐（基于同一轮「用户说的话」），避免在模板里重复计算
const decoratedMessages = computed(() => {
  const userOf = new Map()
  messages.value.forEach(m => { if (m.role === 'user') userOf.set(m.chatId, m.content) })
  return messages.value.map(m => ({
    ...m,
    recs: m.role === 'ai' ? getTestRecommendations(userOf.get(m.chatId) || '') : []
  }))
})

const inputText = ref('')
const isTyping = ref(false)
const showDiaryPanel = ref(false)
const isSendingDiary = ref(false)
const showDiaryPicker = ref(false)
const showTestPicker = ref(false)
const loadingDiary = ref(false)
const loadingTest = ref(false)
const diaryList = ref([])
const testList = ref([])

// 情绪配置（和写日记页一致）

const diaryForm = ref({
  emotion: 1,
  content: ''
})

const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const userId = computed(() => userInfo.value.userId || userInfo.value.id)
const userName = computed(() => userInfo.value.nickname || userInfo.value.username || '用户')
const DEFAULT_AVATAR = '/img/默认管理员头像.png'
const userAvatar = computed(() => {
  const a = userInfo.value.avatar
  if (!a) return DEFAULT_AVATAR
  if (a.startsWith('http') || a.startsWith('/') || a.startsWith('data:')) return a
  return '/' + a
})

// 加载最新用户信息（确保和个人中心同步）
const loadUserInfo = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  userInfo.value = info
  try {
    const res = await getUserById(info.userId || info.id)
    if (res.code === 200 && res.data) {
      userInfo.value = { ...userInfo.value, ...res.data }
      // 同步更新 localStorage
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    }
  } catch (e) {
    console.warn('获取用户信息失败:', e)
  }
}

// 头像装饰框（和个人中心同步）
const frameNameMap = { frame1: 'flower', frame2: 'leaf', frame3: 'crown', frame4: 'star', frame5: 'heart', frame6: 'halo', frame7: 'cloud' }
// 优先从 userInfo（后端数据）读取，其次从 localStorage 读取
const _getInitFrame = () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (info?.avatarFrame) {
    const f = frameNameMap[info.avatarFrame] || info.avatarFrame
    localStorage.setItem('profile_frame', f)
    return f
  }
  const raw = localStorage.getItem('profile_frame') || 'none'
  const migrated = frameNameMap[raw] || raw
  if (frameNameMap[raw]) localStorage.setItem('profile_frame', migrated)
  return migrated
}
const userFrame = ref(_getInitFrame())

// 同步用户信息（头像、头像框等）
const syncUserInfo = () => {
  // 从 localStorage 读取最新的用户信息
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (info) userInfo.value = info
  // 优先从 userInfo 读取头像框（后端数据为准）
  if (info?.avatarFrame) {
    const f = frameNameMap[info.avatarFrame] || info.avatarFrame
    userFrame.value = f
    localStorage.setItem('profile_frame', f)
    return
  }
  // 从 localStorage 读取最新的头像框
  const raw = localStorage.getItem('profile_frame')
  if (raw) {
    const migrated = frameNameMap[raw] || raw
    userFrame.value = migrated
    if (frameNameMap[raw]) localStorage.setItem('profile_frame', migrated)
  }
}

// 监听 storage 变化（其他页面修改时实时同步）
const handleStorageChange = (e) => {
  if (e.key === 'profile_frame' || e.key === 'userInfo') {
    syncUserInfo()
  }
}
// 同标签页：Profile 保存后立刻同步（storage 事件只在跨标签页触发）
const handleProfileUpdated = () => {
  syncUserInfo()
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (info?.avatarFrame) userFrame.value = frameNameMap[info.avatarFrame] || info.avatarFrame
}

// 页面可见时刷新用户信息
const handleVisibilityChange = () => {
  if (!document.hidden) {
    syncUserInfo()
    loadUserInfo()
  }
}
const avatarFrames = [
  { name: 'none',   label: '无',     img: '',                       scale: 0 },
  { name: 'flower', label: '小猫咪', img: '/img/头像框.png',        scale: 110 },
  { name: 'leaf',   label: '生气',   img: '/img/头像框2.png',       scale: 165 },
  { name: 'crown',  label: '开心',   img: '/img/头像框3.png',       scale: 205 },
  { name: 'star',   label: '粉色猫', img: '/img/头像框4.png',       scale: 150 },
  { name: 'heart',  label: '雪球猫', img: '/img/头像框5.png',       scale: 150 },
  { name: 'halo',   label: '大耳狗', img: '/img/头像框6.png',       scale: 145 },
  { name: 'cloud',  label: '花环',   img: '/img/头像框7.png',       scale: 165 },
]
const currentFrameImg = computed(() => {
  const f = avatarFrames.find(f => f.name === userFrame.value)
  return f?.img || ''
})
const currentFrameScale = computed(() => {
  const f = avatarFrames.find(f => f.name === userFrame.value)
  return f?.scale || 120
})

// 头像加载失败时用默认头像
const onAvatarError = (e) => {
  e.target.src = DEFAULT_AVATAR
}

const quickQuestions = [
  '我今天心情不好',
  '我好焦虑怎么办',
  '睡不着失眠了',
  '感觉压力好大',
]

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  return `${h}:${m}`
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 自动调整输入框高度
const autoResize = () => {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 120) + 'px'
  }
}

// 快捷问题
const sendQuickMsg = (text) => {
  inputText.value = text
  sendMessage()
}

// 关闭写日记面板
const closeDiaryPanel = () => {
  showDiaryPanel.value = false
  diaryForm.value = { emotion: 1, content: '' }
}

// 获取情绪图标
const getEmotionIcon = (emotionVal) => {
  const emo = emotions.find(e => e.val === emotionVal)
  return emo?.icon || '/img/其他.png'
}
const getEmotionBg = (emotionVal) => {
  const emo = emotions.find(e => e.val === emotionVal)
  return emo?.bg || '#e0e0e0'
}
const getEmotionLabel = (emotionVal) => {
  const emo = emotions.find(e => e.val === emotionVal)
  return emo?.label || '其他'
}

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 打开日记选择器
const openDiaryPicker = async () => {
  showDiaryPicker.value = true
  loadingDiary.value = true
  try {
    const res = await getDiaryList({ userId: userId.value, page: 1, size: 20 })
    if (res.code === 200) {
      // 兼容不同返回格式
      const data = res.data?.records || res.data?.list || res.data || []
      diaryList.value = Array.isArray(data) ? data : []
    }
  } catch (e) {
    console.warn('加载日记列表失败:', e)
    diaryList.value = []
  } finally {
    loadingDiary.value = false
  }
}
const closeDiaryPicker = () => {
  showDiaryPicker.value = false
  diaryList.value = []
}

// 选择日记发给AI
const selectDiary = async (diary) => {
  if (!diary || !userId.value) return
  closeDiaryPicker()

  const emoLabel = getEmotionLabel(diary.emotion)
  const msg = `这是我${diary.diaryDate}写的日记，那天心情是${emoLabel}：\n${diary.diaryContent}\n\n你能帮我分析一下吗？`
  await askAi(msg)
}

// 打开测评选择器
const openTestPicker = async () => {
  showTestPicker.value = true
  loadingTest.value = true
  try {
    const res = await getRecordList({ userId: userId.value, page: 1, size: 20 })
    if (res.code === 200) {
      const data = res.data?.records || res.data?.list || res.data || []
      testList.value = Array.isArray(data) ? data : []
    }
  } catch (e) {
    console.warn('加载测评记录失败:', e)
    testList.value = []
  } finally {
    loadingTest.value = false
  }
}
const closeTestPicker = () => {
  showTestPicker.value = false
  testList.value = []
}

// 选择测评发给AI
const selectTest = async (record) => {
  if (!record || !userId.value) return
  closeTestPicker()

  // 字段取自 /record/list 联表结果：testName / score / level / tip
  const testName = record.testName || '心理测评'
  const score = record.score ?? record.totalScore ?? '—'
  const levelText = record.level ? `，结果等级：${record.level}` : ''
  const tipText = record.tip ? `\n结果说明：${record.tip}` : ''
  const msg = `我做了《${testName}》测评，得分 ${score} 分${levelText}。${tipText}\n\n你能帮我解读一下吗？`
  await askAi(msg)
}

// 发送日记给AI（先落库日记，再把内容交给 AI）
const sendDiaryToAi = async () => {
  const content = diaryForm.value.content.trim()
  if (!content || !userId.value) return
  isSendingDiary.value = true

  try {
    const d = new Date()
    const diaryDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

    // 1. 先保存日记
    const saveRes = await saveDiary({
      userId: userId.value,
      diaryContent: content,
      emotion: diaryForm.value.emotion,
      diaryDate,
      isPublic: 0,
    })
    if (saveRes?.code !== 200) {
      ElMessage.warning('日记保存失败，请重试')
      return
    }

    // 2. 组装消息交给 AI
    const emoLabel = emotions.find(e => e.val === diaryForm.value.emotion)?.label || ''
    const msg = `我今天的心情是${emoLabel}，想和你说说：\n${content}`

    // 关闭面板
    closeDiaryPanel()

    await askAi(msg)
  } catch (e) {
    ElMessage.error('发送失败，请重试')
  } finally {
    // 成功路径也要复位，否则按钮会一直停在「发送中...」
    isSendingDiary.value = false
  }
}

// 一轮记录（后端一条）展开成 user + ai 两条气泡
const pushRound = (rec) => {
  if (!rec) return
  if (rec.userMsg) messages.value.push({ chatId: rec.chatId, role: 'user', content: rec.userMsg, time: rec.createTime })
  if (rec.aiReply) messages.value.push({ chatId: rec.chatId, role: 'ai', content: rec.aiReply, time: rec.createTime })
}

// 统一的「问 AI」入口：先乐观上屏用户消息，后端落库后替换成真实记录
// 注意：ref 深层响应式会把 push 进去的对象代理化，闭包里的 temp（原始对象）
// 和数组里读出来的（代理）永远不相等，filter(m => m !== temp) 删不掉它——
// 那条「乐观消息」会残留，同一条用户消息就显示两个气泡。改用唯一 ID 过滤。
let askLocalSeq = 0
const askAi = async (userMsg) => {
  const sid = await ensureSession()
  if (!sid) return

  const temp = { _localId: ++askLocalSeq, chatId: null, role: 'user', content: userMsg, time: new Date(), pending: true }
  messages.value.push(temp)
  scrollToBottom()

  isTyping.value = true
  try {
    const res = await chatWithAi(sid, userMsg)
    messages.value = messages.value.filter(m => m._localId !== temp._localId)
    if (res.code === 200 && res.data) {
      pushRound(res.data)
      // 会话标题/预览由后端生成，重新拉一次列表
      await loadSessions()
    } else {
      ElMessage.error(res?.msg || 'AI 暂时无法回复，请稍后再试')
    }
  } catch (e) {
    messages.value = messages.value.filter(m => m._localId !== temp._localId)
    ElMessage.error('网络异常，消息未能发送')
  } finally {
    isTyping.value = false
    scrollToBottom()
  }
}

// 发送消息
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isTyping.value) return
  inputText.value = ''
  autoResize()
  saveDraft()
  await askAi(text)
}

// 输入草稿按会话持久化
watch(inputText, saveDraft)

onMounted(async () => {
  loadUserInfo()
  // 会话与消息统一从后端加载：不再读本地快照，也不会被后端历史覆盖
  await loadSessions()
  if (sessions.value.length > 0) {
    await switchSession(sessions.value[0].sessionId)
  } else {
    await createNewSession()
  }
  // 监听 localStorage 变化（跨页面同步头像、头像框）
  window.addEventListener('storage', handleStorageChange)
  // 同标签页内 Profile 保存后立刻同步
  window.addEventListener('profile-updated', handleProfileUpdated)
  // 页面可见时刷新（切换标签页回来时同步）
  document.addEventListener('visibilitychange', handleVisibilityChange)
  // 窗口获得焦点时刷新
  window.addEventListener('focus', handleVisibilityChange)
  // 点击关闭右键菜单
  document.addEventListener('click', closeContextMenu)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('profile-updated', handleProfileUpdated)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('focus', handleVisibilityChange)
  document.removeEventListener('click', closeContextMenu)
  if (recognition) {
    recognition.stop()
  }
})
</script>

<style scoped>
.ai-chat-page {
  width: 100%;
  height: 100vh;
  margin: 0;
  display: flex;
  background: linear-gradient(180deg, #fef5f8 0%, #f0f7ff 100%);
  overflow: hidden;
  box-sizing: border-box;
}

/* ========== 侧边栏 ========== */
.session-sidebar {
  width: 260px;
  min-width: 260px;
  background: var(--c-bg1);
  border-right: 1px solid rgba(111, 163, 106, 0.15);
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  overflow: hidden;
}

.session-sidebar.collapsed {
  width: 60px;
  min-width: 60px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 50px 12px 16px;
  border-bottom: 1px solid rgba(111, 163, 106, 0.1);
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  font-family: 'Kaiti', '楷体', 'STKaiti', serif;
  flex: 1;
  justify-content: center;
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(111, 163, 106, 0.4);
}

.plus-icon {
  font-size: 16px;
  font-weight: 700;
}

.toggle-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(111, 163, 106, 0.1);
  border-radius: 8px;
  cursor: pointer;
  color: var(--c-hex);
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 8px;
  transition: all 0.2s;
}

.toggle-btn:hover {
  background: rgba(111, 163, 106, 0.2);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-list::-webkit-scrollbar { width: 4px; }
.session-list::-webkit-scrollbar-thumb {
  background: rgba(111, 163, 106, 0.3);
  border-radius: 2px;
}

.session-item {
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 6px;
  position: relative;
}

.session-item:hover {
  background: rgba(111, 163, 106, 0.12);
}

.session-item.active {
  background: rgba(111, 163, 106, 0.2);
  border-left: 3px solid var(--c-hex);
}

.session-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
  font-family: 'Kaiti', '楷体', 'STKaiti', serif;
}

.session-preview {
  font-size: 12px;
  color: var(--c-text-weak);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}

.session-time {
  font-size: 11px;
  color: var(--c-text-weak);
  text-align: right;
}

.session-empty {
  text-align: center;
  padding: 40px 20px;
  color: var(--c-text-weak);
  font-size: 13px;
  font-family: 'Kaiti', '楷体', 'STKaiti', serif;
}

/* 右键菜单 */
.context-menu {
  position: fixed;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px;
  z-index: 1000;
  min-width: 120px;
}

.context-menu-item {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  color: #e74c3c;
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  text-align: left;
  transition: all 0.2s;
}

.context-menu-item:hover {
  background: #fdf2f2;
}

/* ========== 主聊天区域 ========== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* 顶部标题栏 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 28px;
  background: linear-gradient(135deg, rgba(253, 231, 238, 0.9) 0%, rgba(205, 232, 248, 0.9) 100%);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.5);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-icon {
  width: 64px !important;
  height: 64px !important;
  object-fit: contain;
}

.header-info { display: flex; flex-direction: column; gap: 2px; }

.header-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #e879a0, #7ba8d8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-sub {
  margin: 0;
  font-size: 13px;
  color: #8a8a9a;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 导出按钮 */
.export-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-hex);
  transition: all 0.25s;
}

.export-btn:hover {
  background: rgba(111, 163, 106, 0.15);
  transform: scale(1.1);
}

/* 用户信息栏 */
.user-info-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 16px 6px 8px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 28px;
}

.user-avatar-wrap {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: visible;
  position: relative;
  flex-shrink: 0;
  margin: 4px;
}

.user-avatar-mini {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 1;
  display: block;
  background: var(--c-bg2);
}

.user-avatar-wrap .frame-ring-img {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 3;
  object-fit: contain;
  display: block;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 20px;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-hex);
  box-shadow: 0 0 8px var(--c-hex);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.online-text {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

/* 聊天消息区域 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.chat-messages::-webkit-scrollbar { width: 6px; }
.chat-messages::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, #f8c9d8, #cde8f8);
  border-radius: 3px;
}

/* 欢迎消息 */
.welcome-box {
  text-align: center;
  padding: 40px 20px;
}

.welcome-avatar {
  width: 100px;
  height: 100px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.welcome-avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.welcome-box h3 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #374151;
}

.welcome-box > p {
  margin: 0 0 24px;
  font-size: 14px;
  color: #6b7280;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.quick-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(253, 231, 238, 0.8), rgba(205, 232, 248, 0.8));
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.25s;
}

.quick-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(248, 201, 216, 0.3);
  color: #e879a0;
}

/* 欢迎区域功能入口 */
.welcome-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: 24px;
  background: linear-gradient(135deg, #fde7ee, #cde8f8);
  color: #4b5563;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(248, 201, 216, 0.2);
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(248, 201, 216, 0.35);
}

.action-icon {
  font-size: 18px;
}

.action-img-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

/* 写日记面板 */
.diary-panel-wrap {
  display: flex;
  justify-content: center;
  padding: 10px 0;
}

.diary-panel {
  width: 100%;
  max-width: 520px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.diary-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #fde7ee, #cde8f8);
}

.diary-panel-header h4 {
  margin: 0;
  font-size: 16px;
  color: #4b5563;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 50%;
  cursor: pointer;
  font-size: 14px;
  color: #6b7280;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #fff;
  color: #e879a0;
}

.diary-form {
  padding: 20px;
}

.form-label {
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
  margin-bottom: 10px;
}

.form-label .required {
  color: #e879a0;
}

.emotion-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.emo-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border-radius: 14px;
  background: var(--emo-bg, var(--c-bg2));
  cursor: pointer;
  transition: all 0.25s;
  border: 2px solid transparent;
  opacity: 0.7;
}

.emo-item:hover {
  opacity: 1;
  transform: translateY(-2px);
}

.emo-item.active {
  opacity: 1;
  border-color: #e879a0;
  box-shadow: 0 4px 12px rgba(232, 121, 160, 0.3);
}

.emo-item img {
  width: 28px;
  height: 28px;
}

.emo-item span {
  font-size: 12px;
  color: #4b5563;
  font-weight: 500;
}

.diary-textarea {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
  resize: vertical;
  min-height: 100px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
  font-family: inherit;
}

.diary-textarea:focus {
  border-color: #f8c9d8;
}

.diary-textarea::placeholder {
  color: #c0c0cc;
}

.diary-panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 20px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}

.cancel-btn {
  padding: 8px 20px;
  border: 1px solid var(--c-border-strong);
  background: #fff;
  color: #6b7280;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.cancel-btn:hover {
  background: var(--c-bg2);
}

.submit-diary-btn {
  padding: 8px 20px;
  border: none;
  background: linear-gradient(135deg, #f8c9d8, #e8a0c0);
  color: #fff;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.25s;
}

.submit-diary-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(248, 201, 216, 0.4);
}

.submit-diary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 底部选择面板 */
.bottom-picker-panel {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.08);
  margin-bottom: 12px;
  overflow: hidden;
  max-height: 300px;
  display: flex;
  flex-direction: column;
}

.picker-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #fde7ee, #cde8f8);
}

.picker-panel-header h4 {
  margin: 0;
  font-size: 16px;
  color: #4b5563;
  display: flex;
  align-items: center;
  gap: 8px;
}

.picker-title-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

.picker-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
}

.picker-list::-webkit-scrollbar { width: 6px; }
.picker-list::-webkit-scrollbar-thumb {
  background: var(--c-border-strong);
  border-radius: 3px;
}

.loading-tip, .empty-tip {
  text-align: center;
  padding: 24px 20px;
  color: #9ca3af;
  font-size: 14px;
}

.empty-tip p {
  margin: 0 0 12px;
}

.link-btn {
  background: none;
  border: none;
  color: #e879a0;
  font-size: 14px;
  cursor: pointer;
  text-decoration: underline;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 8px;
}

.picker-item:hover {
  background: var(--c-bg1);
  transform: translateX(4px);
}

.item-emotion {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.item-emotion img {
  width: 28px;
  height: 28px;
}

.item-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #fde7ee, #cde8f8);
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-text {
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-date {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 4px;
}

.item-desc {
  font-size: 12px;
  color: #9ca3af;
}

.item-arrow {
  color: #d1d5db;
  font-size: 18px;
  flex-shrink: 0;
}

.picker-item:hover .item-arrow {
  color: #e879a0;
}

/* 消息气泡 */
.msg-item {
  display: flex;
  gap: 10px;
  max-width: 75%;
  position: relative;
}

.msg-item.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #fde7ee, #cde8f8);
  padding: 2px;
}

/* 用户消息头像框 */
.msg-avatar.user-avatar-frame {
  overflow: visible;
  background: transparent;
  padding: 0;
  position: relative;
}

/* AI消息头像框 */
.msg-avatar.ai-avatar-frame {
  overflow: visible;
  background: transparent;
  padding: 0;
  position: relative;
}

/* AI消息头像图标 */
.ai-avatar-wrap {
  width: 72px !important;
  height: 72px !important;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-avatar-big {
  width: 72px !important;
  height: 72px !important;
  object-fit: contain;
  display: block;
}

.msg-avatar .msg-user-avatar,
.msg-avatar .msg-ai-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  position: relative;
  z-index: 1;
  display: block;
}

.msg-avatar .frame-ring-img {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 3;
  object-fit: contain;
  display: block;
}

.msg-bubble {
  position: relative;
  padding: 12px 16px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
}

.msg-item.ai .msg-bubble {
  background: #fff;
  border-top-left-radius: 6px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  color: #374151;
}

.msg-item.user .msg-bubble {
  background: linear-gradient(135deg, #f8c9d8, #e8a0c0);
  border-top-right-radius: 6px;
  color: #fff;
  box-shadow: 0 2px 10px rgba(248, 201, 216, 0.3);
}

.msg-content { white-space: pre-wrap; }

/* 关键词高亮样式 */
:deep(.keyword-highlight) {
  background: #fef3c7;
  color: #92400e;
  border-radius: 4px;
  padding: 1px 4px;
  font-weight: 500;
}

/* 聊天图片消息 */
.chat-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 12px;
  object-fit: cover;
  display: block;
}

.msg-time {
  margin-top: 6px;
  font-size: 11px;
  opacity: 0.6;
}

.msg-item.user .msg-time { text-align: right; }

/* 消息操作按钮 */
.msg-actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.msg-action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  background: rgba(111, 163, 106, 0.1);
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  color: var(--c-hex);
  transition: all 0.2s;
}

.msg-action-btn:hover {
  background: rgba(111, 163, 106, 0.2);
  transform: translateY(-1px);
}

/* AI推荐测评 */
.ai-recommendations {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(111, 163, 106, 0.2);
}

.rec-label {
  font-size: 12px;
  color: var(--c-text-weak);
}

.rec-tag {
  display: inline-block;
  padding: 4px 10px;
  background: linear-gradient(135deg, rgba(111, 163, 106, 0.15), rgba(61, 92, 50, 0.1));
  color: var(--c-text);
  font-size: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.rec-tag:hover {
  background: linear-gradient(135deg, rgba(111, 163, 106, 0.3), rgba(61, 92, 50, 0.2));
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(111, 163, 106, 0.2);
}

/* 正在输入 */
.typing-bubble {
  padding: 16px 20px !important;
}

.typing-dots {
  display: flex;
  gap: 5px;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-border-strong);
  animation: typingBounce 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(1) { animation-delay: 0s; }
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* 底部输入区域 */
.chat-input-area {
  padding: 12px 28px 16px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border-top: 1px solid rgba(248, 201, 216, 0.3);
}

/* 快捷功能栏 */
.quick-actions-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.quick-action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid rgba(248, 201, 216, 0.4);
  background: rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  cursor: pointer;
  font-size: 13px;
  color: #6b7280;
  transition: all 0.25s;
}

.quick-action-btn:hover {
  background: linear-gradient(135deg, #fde7ee, #cde8f8);
  color: #e879a0;
  border-color: transparent;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(248, 201, 216, 0.3);
}

.qa-icon {
  font-size: 15px;
}

.qa-img-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  background: #fff;
  border-radius: 24px;
  padding: 10px 10px 10px 14px;
  box-shadow: 0 2px 12px rgba(248, 201, 216, 0.15);
  border: 1px solid rgba(248, 201, 216, 0.3);
}

/* 语音按钮 */
.image-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(111, 163, 106, 0.1);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-hex);
  flex-shrink: 0;
  transition: all 0.25s;
}

.image-btn:hover {
  background: rgba(111, 163, 106, 0.2);
  transform: scale(1.1);
}

.voice-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(111, 163, 106, 0.1);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-hex);
  flex-shrink: 0;
  transition: all 0.25s;
  position: relative;
}

.voice-btn:hover {
  background: rgba(111, 163, 106, 0.2);
  transform: scale(1.1);
}

/* ===== 录音中指示胶囊（主题化，替代 ElMessage 默认白条） ===== */
.recording-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  margin: 0 auto 10px;
  padding: 8px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(255, 241, 235, 0.95), rgba(255, 235, 235, 0.95));
  border: 1px solid rgba(240, 170, 160, 0.55);
  box-shadow: 0 6px 20px rgba(220, 140, 130, 0.18);
  cursor: pointer;
  user-select: none;
  backdrop-filter: blur(6px);
}
.rec-mic { font-size: 16px; animation: recBob 1.2s ease-in-out infinite; }
@keyframes recBob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-2px); } }
.rec-bars { display: flex; align-items: flex-end; gap: 3px; height: 16px; }
.rec-bars i {
  width: 3px;
  border-radius: 2px;
  background: var(--c-hex, #8cc269);
  animation: recBar 0.9s ease-in-out infinite;
}
.rec-bars i:nth-child(1) { height: 6px; animation-delay: 0s; }
.rec-bars i:nth-child(2) { height: 12px; animation-delay: 0.15s; }
.rec-bars i:nth-child(3) { height: 16px; animation-delay: 0.3s; }
.rec-bars i:nth-child(4) { height: 10px; animation-delay: 0.45s; }
.rec-bars i:nth-child(5) { height: 7px; animation-delay: 0.6s; }
@keyframes recBar { 0%, 100% { transform: scaleY(0.5); } 50% { transform: scaleY(1.15); } }
.rec-text { font-size: 13px; color: #a05a4e; font-weight: 600; }
.rec-stop {
  font-size: 11px;
  color: #fff;
  background: rgba(226, 92, 80, 0.75);
  padding: 2px 10px;
  border-radius: 999px;
}
.rec-fade-enter-active, .rec-fade-leave-active { transition: all 0.25s ease; }
.rec-fade-enter-from, .rec-fade-leave-to { opacity: 0; transform: translateY(8px); }


.voice-btn.recording {
  background: rgba(231, 76, 60, 0.15);
  color: #e74c3c;
  animation: voicePulse 1.5s infinite;
}

.recording-dot {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 8px;
  height: 8px;
  background: #e74c3c;
  border-radius: 50%;
  animation: voicePulse 1.5s infinite;
}

@keyframes voicePulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.2); opacity: 0.6; }
}

.chat-input {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  color: #374151;
  background: transparent;
  line-height: 1.5;
  max-height: 120px;
  font-family: inherit;
  min-height: 24px;
}

.chat-input::placeholder { color: #c0c0cc; }

.send-btn {
  width: 60px;
  height: 40px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, #f8c9d8, #e8a0c0);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.08);
  box-shadow: 0 4px 12px rgba(248, 201, 216, 0.4);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.input-tip {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

/* 响应式 */
@media (max-width: 768px) {
  .session-sidebar {
    position: absolute;
    z-index: 100;
    height: 100%;
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.1);
  }
  .session-sidebar.collapsed {
    transform: translateX(-100%);
  }
}
</style>