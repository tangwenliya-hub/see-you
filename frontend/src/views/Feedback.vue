<template>
  <div class="feedback-page">
    <!-- 顶部横幅 -->
    <AnimeHero glyph="message" title="反馈与建议" sub="你的每一次声音，都让绪见变得更温柔 ">
      <template #right>
        <div class="hero-right">
          <div class="hero-stat">
            <span class="stat-num">{{ myList.length }}</span>
            <span class="stat-label">我的反馈</span>
          </div>
          <div class="hero-stat">
            <span class="stat-num">{{ repliedCount }}</span>
            <span class="stat-label">已回复</span>
          </div>
          <div class="hero-stat">
            <span class="stat-num pending">{{ pendingCount }}</span>
            <span class="stat-label">处理中</span>
          </div>
        </div>
      </template>
    </AnimeHero>

    <!-- 主体：只放表单，历史记录收进右下角悬浮按钮 -->
    <div class="fb-body">
    <!-- 小贴士：横幅下方细条 -->
    <div class="tips-strip">
      <span><AppIcon name="bulb" /> 描述越具体，越容易定位问题</span>
      <i>·</i>
      <span><AppIcon name="edit" /> 可附上你期望的改进方向~</span>
      <i>·</i>
      <span><AppIcon name="heart" /> 反馈完全匿名，但会关联账号方便回复</span>
    </div>
    <div class="form-col">
        <div class="form-card glass">
          <div class="card-head">
            <h3><AppIcon name="edit" /> 写点什么吧</h3>
            <span class="form-tip">提交后管理员会尽快回复 <AppIcon name="mail" /></span>
          </div>
          <div class="form-block">
            <label class="form-label">反馈类型</label>
            <div class="type-grid">
              <span v-for="t in types" :key="t.val"
                    :class="['type-chip', { on: form.type === t.val }]"
                    @click="form.type = t.val"><AppIcon :name="t.icon" /> {{ t.label }}</span>
            </div>
          </div>
          <div class="form-block">
            <label class="form-label">标题 <span class="optional">(选填)</span></label>
            <input v-model="form.title" maxlength="30" placeholder="简短描述一下，比如「首页加载有点慢」" />
            <span class="char-count">{{ form.title.length }}/30</span>
          </div>
          <div class="form-block">
            <label class="form-label">详细描述</label>
            <textarea v-model="form.content" rows="8" maxlength="500"
                      placeholder="告诉我们你的想法、遇到的问题、或任何建议..."></textarea>
            <span class="char-count" style="bottom:-20px">{{ form.content.length }}/500</span>
          </div>
          <div class="form-actions">
            <button class="submit-btn" :disabled="submitting || !form.content.trim()" @click="submit">
              {{ submitting ? '发送中...' : ' 提交反馈' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 悬浮小图标：点开「我的反馈记录」抽屉 -->
    <button class="fb-fab" :class="{ 'shake': hasNewReply }" @click="openDrawer" title="我的反馈记录">
      <span class="fab-icon"><AppIcon name="message" /></span>
      <span v-if="myList.length" class="fab-badge">{{ myList.length > 99 ? '99+' : myList.length }}</span>
    </button>

    <!-- 反馈记录抽屉（微信聊天列表样式） -->
    <transition name="drawer-slide">
      <div v-if="drawerOpen" class="drawer-mask" @click.self="drawerOpen = false">
        <aside class="drawer">
          <div class="drawer-hd">
            <div class="drawer-hd-l">
              <span class="drawer-title">我的反馈记录</span>
              <span class="drawer-sub">{{ myList.length }} 条 · {{ repliedCount }} 已回复</span>
            </div>
            <span class="drawer-close" @click="drawerOpen = false"><AppIcon name="close" /></span>
          </div>

          <div class="drawer-bd">
            <div v-if="loading" class="empty">
              <CowLoading size="sm" text="加载中" />
            </div>

            <div v-else-if="myList.length === 0" class="empty">
              <div class="empty-icon"><AppIcon name="mail" /></div>
              <p>还没有提交过反馈哦~</p>
            </div>

            <!-- 微信风格会话列表 -->
            <div v-else class="chat-list">
              <div v-for="item in pagedList" :key="item.feedbackId"
                   class="chat-item"
                   :class="{ unread: item.replyText && !item.seenAdminReply }"
                   @click="openChat(item)">
                <div class="chat-ava"><AppIcon :name="getTypeIcon(item.type)" /></div>
                <div class="chat-main">
                  <div class="chat-top">
                    <span class="chat-name">{{ item.title || (item.content||'').slice(0,16) || '反馈' }}</span>
                    <span class="chat-time">{{ fmtTime(item.createTime).slice(5, 16) }}</span>
                  </div>
                  <div class="chat-msg">
                    <span v-if="item.replyText" class="chat-from">管理员：</span>
                    <span v-else class="chat-from me">我：</span>
                    <span class="chat-text">{{ item.replyText || item.content || item.feedbackContent }}</span>
                  </div>
                </div>
                <span v-if="item.replyText && !item.seenAdminReply" class="chat-dot"></span>
              </div>
            </div>

            <!-- 分页器 -->
            <div v-if="myList.length > pageSize" class="pager-user">
              <button :disabled="pageNum<=1" @click="pageNum=1">首页</button>
              <button :disabled="pageNum<=1" @click="pageNum--">‹</button>
              <button v-for="p in totalPages" :key="p" :class="{on:p===pageNum}" @click="pageNum=p">{{ p }}</button>
              <button :disabled="pageNum>=totalPages" @click="pageNum++">›</button>
              <button :disabled="pageNum>=totalPages" @click="pageNum=totalPages">末页</button>
            </div>
          </div>
        </aside>
      </div>
    </transition>

    <!-- 对话弹窗 -->
    <div v-if="chatTarget" class="chat-mask" @click.self="chatTarget = null">
      <div class="chat-modal glass">
        <div class="chat-hd">
          <span class="chat-title"><AppIcon :name="getTypeIcon(chatTarget.type)" /> {{ chatTarget.title || '反馈详情' }}</span>
          <span class="chat-close" @click="chatTarget = null"><AppIcon name="close" /></span>
        </div>

        <div class="chat-body">
          <!-- 消息列表：v-for 渲染完整聊天记录 -->
          <div v-for="(m, idx) in chatMessages" :key="idx"
               :class="['b-row', m.senderType === 'admin' ? 'other' : 'me']">
            <div :class="['b-bubble', m.senderType === 'admin' ? 'admin' : '']">
              <div class="b-name">{{ m.senderType === 'admin' ? ' 管理员' : '我' }}</div>
              <div class="b-msg">{{ m.content }}</div>
              <div class="b-t">{{ fmtTime(m.createTime) }}</div>
            </div>
          </div>
          <div v-if="chatMessages.length === 0" class="empty-chat">暂无对话</div>
        </div>

        <div class="chat-foot">
          <div class="sf-area">
            <textarea v-model="suppContent" rows="2" autocomplete="off" placeholder="回复管理员，补充你的想法..."></textarea>
            <button class="sf-send" :disabled="suppSending || !suppContent.trim()" @click="confirmSupplement">{{ suppSending ? '发送中…' : '发送' }}</button>
          </div>
          <div class="chat-del">
            <button class="del-mini" @click="remove(chatTarget.feedbackId); chatTarget=null"><AppIcon name="trash" /> 从我的记录移除</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { saveFeedback, getMyFeedbackList, softDeleteFeedback, supplementFeedback, getFeedbackMessages, fetchAllPages } from '../api'
import { ElMessage } from 'element-plus'
import AnimeHero from '@/components/AnimeHero.vue'
import CowLoading from '@/components/CowLoading.vue'

const router = useRouter()

const types = [
  { val: 'suggest', icon: 'bulb', label: '功能建议' },
  { val: 'bug',     icon: 'bug', label: '问题反馈' },
  { val: 'ui',      icon: 'palette', label: '界面体验' },
  { val: 'content', icon: 'book', label: '内容相关' },
  { val: 'other',   icon: 'message', label: '其他' },
]
const getTypeIcon = (t) => types.find(x => x.val === t)?.icon || 'message'

const form = reactive({ type: 'suggest', title: '', content: '' })
const submitting = ref(false)
const myList = ref([])
const loading = ref(false)
const suppContent = ref('')
const chatTarget = ref(null)
const chatMessages = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
// 我的反馈记录：收进右下角悬浮按钮，点开抽屉才显示
const drawerOpen = ref(false)

const repliedCount = computed(() => myList.value.filter(x => x.replyText || x.status == 2).length)
const pendingCount = computed(() => myList.value.filter(x => !x.replyText && x.status != 2).length)
// 收起时标题上的小红点：有未读的管理员回复
const hasNewReply = computed(() => myList.value.some(x => x.replyText && !x.seenAdminReply))

const fmtTime = (t) => {
  if (!t) return ''
  const s = String(t)
  return s.replace('T', ' ').slice(0, 16)
}

const getUserId = () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  return info?.userId
}

const submit = async () => {
  const userId = getUserId()
  if (!userId) { router.push('/login'); return }
  submitting.value = true
  try {
    const res = await saveFeedback({
      userId: userId,
      title: form.title,
      type: form.type,
      feedbackContent: form.content,
    })
    if (res.code === 200) {
      ElMessage.success('提交成功！感谢你的反馈 ')
      form.title = ''
      form.content = ''
      await loadList()
    } else {
      ElMessage.error(res.msg || '提交失败')
    }
  } catch {
    ElMessage.error('网络错误，请重试')
  } finally {
    submitting.value = false
  }
}

const loadList = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  try {
    // /feedback/my 只返回当前登录用户自己的记录（后端按 token 过滤，无需传 userId）
    // 翻页拉全（后端单页封顶 100，size:200 会被静默截断）
    const all = await fetchAllPages(p => getMyFeedbackList({ page: p, size: 100 }))
    myList.value = all
      .sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
    // 页码钳位
    const maxPage = Math.max(1, Math.ceil(myList.value.length / pageSize.value))
    if (pageNum.value > maxPage) pageNum.value = maxPage
  } catch {
    myList.value = []
  } finally {
    loading.value = false
  }
}
// 用户端分页
const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return myList.value.slice(start, start + pageSize.value)
})
const totalPages = computed(() => Math.max(1, Math.ceil(myList.value.length / pageSize.value)))

// 暴露到全局，让 AppLayout 收到 WS 推送时自动刷新
if (typeof window !== 'undefined') {
  window.__feedbackPageLoad = loadList
}

// 打开抽屉时顺手刷新一次，保证看到最新回复
const openDrawer = () => {
  drawerOpen.value = true
  loadList()
}

const remove = async (id) => {
  if (!confirm('从你的记录中移除？（后台仍会保留）')) return
  try {
    const res = await softDeleteFeedback(id)
    if (res.code === 200) {
      await loadList()
      ElMessage.success('已移除')
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch {
    ElMessage.error('网络错误，请重试')
  }
}

const openChat = async (item) => {
  chatTarget.value = { ...item }
  suppContent.value = ''
  item.seenAdminReply = true
  // 加载完整聊天记录
  try {
    const res = await getFeedbackMessages(item.feedbackId)
    chatMessages.value = res.data || []
  } catch {
    chatMessages.value = []
  }
}

const suppSending = ref(false)
const confirmSupplement = async () => {
  if (suppSending.value) return // 防连点/重复提交
  if (!suppContent.value.trim() || !chatTarget.value) return
  suppSending.value = true
  try {
    const res = await supplementFeedback({
      feedbackId: chatTarget.value.feedbackId,
      userSupplement: suppContent.value,
    })
    if (res.code === 200) {
      ElMessage.success('已发送，管理员会看到 ')
      suppContent.value = ''
      await loadList()
      // 刷新弹窗数据和消息列表
      const fresh = myList.value.find(x => x.feedbackId === chatTarget.value.feedbackId)
      if (fresh) chatTarget.value = { ...fresh }
      try {
        const msgRes = await getFeedbackMessages(chatTarget.value.feedbackId)
        chatMessages.value = msgRes.data || []
      } catch {}
    } else {
      ElMessage.error(res.msg || '发送失败')
    }
  } catch {
    ElMessage.error('网络错误，请重试')
  } finally {
    suppSending.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.feedback-page {
  padding: 0 0 80px;   /* 横幅通栏贴顶，内容宽度由 .fb-body 控制 */
  min-height: 100vh;
  position: relative;
  font-family: "宋体", "Microsoft YaHei", sans-serif;
  color: #55606b;
}
/* 内容区：与其他页面一致的全宽版心（仅留 28px 内边距） */
.fb-body {
  width: 100%;
  padding: 0 28px;
  box-sizing: border-box;
}
.feedback-page::before {
  content: '';
  position: fixed; inset: 0;
  background:
    radial-gradient(circle at 15% 10%, color-mix(in srgb, var(--c-main) 40%, transparent) 0%, transparent 40%),
    radial-gradient(circle at 85% 30%, rgba(200, 220, 240, 0.4) 0%, transparent 40%),
    radial-gradient(circle at 50% 90%, rgba(230, 210, 240, 0.35) 0%, transparent 40%),
    linear-gradient(135deg, var(--c-bg1) 0%, var(--c-bg2) 50%, var(--c-bg1) 100%);
  background-size: cover;
  background-attachment: fixed;
  z-index: -2;
}

/* ===== 玻璃态卡片 ===== */
.glass {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 22px;
  box-shadow: 0 8px 28px color-mix(in srgb, var(--c-hex) 12%, transparent);
}

/* ===== 顶部横幅 ===== */
/* 小贴士：横幅下方的细长提示条 */
.tips-strip {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin: 0 0 18px;
  padding: 9px 18px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  font-size: 12.5px;
  letter-spacing: 1px;
  color: var(--c-text-weak);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--c-hex) 8%, transparent);
}
.tips-strip i {
  font-style: normal;
  color: color-mix(in srgb, var(--c-main) 55%, transparent);
  font-weight: 600;
}

.hero {
  padding: 26px 36px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
  border-radius: 22px;
  background:
    radial-gradient(circle at 20% 20%, rgba(255,255,255,0.4) 0%, transparent 45%),
    linear-gradient(135deg, var(--c-main) 0%, var(--c-hex) 55%, var(--c-main) 100%);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.6),
    0 10px 30px color-mix(in srgb, var(--c-hex) 20%, transparent);
  position: relative;
  overflow: hidden;
}
.hero::before {
  content: '';
  position: absolute;
  top: 10px; left: 24px; right: 24px;
  border-top: 1.5px dashed rgba(255,255,255,0.6);
  pointer-events: none;
}
.hero::after {
  content: '';
  position: absolute;
  bottom: 10px; left: 24px; right: 24px;
  border-top: 1.5px dashed rgba(255,255,255,0.6);
  pointer-events: none;
}
.hero h1 {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 3px;
  color: var(--c-text);
  text-shadow: 0 2px 10px rgba(255,255,255,0.3);
}
.hero-sub {
  font-size: 14px;
  color: var(--c-text);
  margin-top: 6px;
  letter-spacing: 2px;
}
.hero-right {
  display: flex;
  gap: 14px;
}
.hero-stat {
  padding: 10px 22px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid color-mix(in srgb, var(--c-main) 30%, transparent);
  text-align: center;
  min-width: 78px;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-hex) 12%, transparent);
  transition: transform 0.25s;
}
.hero-stat:hover { transform: translateY(-2px); }
.stat-num {
  display: block;
  font-size: 22px;
  font-weight: 600;
  color: var(--c-hex);
}
.stat-num.pending { color: #d4a85a; }
.stat-label {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: 2px;
  letter-spacing: 1px;
}

/* ===== 主体：表单占满整行，历史记录在右下角悬浮按钮里 ===== */
.form-col {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== 表单卡 ===== */
.form-card {
  padding: 28px 30px;
}
.card-head h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--c-text);
  letter-spacing: 2px;
}
.form-tip {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: 4px;
  letter-spacing: 1px;
}
.form-block { margin: 20px 0 0; position: relative; }
.form-label {
  display: block;
  font-size: 14px;
  color: var(--c-text);
  margin-bottom: 10px;
  letter-spacing: 1px;
  font-weight: 500;
}
.type-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.type-chip {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--c-main) 40%, transparent);
  background: rgba(255, 255, 255, 0.7);
  font-size: 13px;
  color: var(--c-text);
  cursor: pointer;
  transition: all 0.25s;
}
.type-chip:hover {
  border-color: var(--c-main);
  background: rgba(255, 255, 255, 0.9);
  transform: translateY(-1px);
}
.type-chip.on {
  border-color: transparent;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color: #fff;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-main) 40%, transparent);
}
.form-block textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 16px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--c-main) 35%, transparent);
  background: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  font-family: inherit;
  line-height: 1.7;
  resize: vertical;
  color: #55606b;
  transition: all 0.25s;
}
.form-block textarea:focus {
  outline: none;
  border-color: var(--c-hex);
  background: #fff;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 20%, transparent);
}
.form-block textarea::placeholder { color: var(--c-text-weak); }
.form-block input {
  width: 100%; box-sizing: border-box;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--c-main) 35%, transparent);
  background: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  color: #55606b;
  transition: all 0.25s;
}
.form-block input:focus {
  outline:none;
  border-color: var(--c-hex);
  background: #fff;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 20%, transparent);
}
.form-block input::placeholder { color:var(--c-text-weak); }
.form-label .optional { font-size:11px; color:var(--c-text-weak); font-weight:normal; margin-left:4px; }
.char-count {
  position: absolute;
  bottom: -20px;
  right: 4px;
  font-size: 11px;
  color: var(--c-text-weak);
}
.form-actions { margin-top: 28px; text-align: right; }
.submit-btn {
  padding: 10px 30px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 2px;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 14px color-mix(in srgb, var(--c-main) 40%, transparent);
}
.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px color-mix(in srgb, var(--c-main) 50%, transparent);
}
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 悬浮小图标（打开我的反馈记录） ===== */
.fb-fab {
  position: fixed;
  right: 30px;
  bottom: 96px;
  z-index: 600;
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  box-shadow: 0 8px 22px color-mix(in srgb, var(--c-hex) 45%, transparent);
  transition: transform 0.25s, box-shadow 0.25s;
}
.fb-fab:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 0 12px 28px color-mix(in srgb, var(--c-hex) 55%, transparent);
}
.fab-icon { font-size: 24px; line-height: 1; }
.fab-badge {
  position: absolute;
  top: -3px; right: -3px;
  min-width: 20px; height: 20px;
  padding: 0 5px;
  box-sizing: border-box;
  border-radius: 999px;
  border: 2px solid #fff;
  background: #e0607a;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}
/* 有新回复时轻微摇晃提示 */
.fb-fab.shake { animation: fabShake 3s ease-in-out infinite; }
@keyframes fabShake {
  0%, 86%, 100% { transform: rotate(0deg); }
  89% { transform: rotate(-12deg); }
  92% { transform: rotate(10deg); }
  95% { transform: rotate(-6deg); }
}

/* ===== 抽屉（微信会话列表样式） ===== */
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  justify-content: flex-end;
  background: rgba(60, 62, 72, 0.25);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
}
.drawer {
  width: 420px;
  max-width: 92vw;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 22px 0 0 22px;
  box-shadow: -12px 0 40px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}
.drawer-hd {
  padding: 22px 22px 16px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 1px solid var(--c-border);
  background: linear-gradient(135deg,
    color-mix(in srgb, var(--c-main) 20%, #fff) 0%,
    rgba(255, 255, 255, 0.9) 100%);
}
.drawer-hd-l { display: flex; flex-direction: column; gap: 4px; }
.drawer-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--c-text);
  letter-spacing: 2px;
}
.drawer-sub {
  font-size: 12px;
  color: var(--c-text-weak);
  letter-spacing: 1px;
}
.drawer-close {
  cursor: pointer;
  font-size: 17px;
  color: var(--c-text-weak);
  padding: 2px 8px;
  border-radius: 8px;
  transition: all 0.2s;
}
.drawer-close:hover { color: var(--c-text); background: rgba(0, 0, 0, 0.05); }
.drawer-bd { flex: 1; overflow-y: auto; padding: 6px 0 24px; }

/* 会话条目：头像 + 标题/时间 + 最后一条消息 */
.chat-list { display: flex; flex-direction: column; }
.chat-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  transition: background 0.2s;
}
.chat-item + .chat-item { border-top: 1px solid rgba(0, 0, 0, 0.045); }
.chat-item:hover { background: rgba(0, 0, 0, 0.03); }
.chat-item.unread { background: rgba(255, 248, 220, 0.55); }
.chat-ava {
  width: 44px; height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--c-main) 16%, #fff);
}
.chat-main { flex: 1; min-width: 0; }
.chat-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}
.chat-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chat-time { font-size: 11px; color: var(--c-text-weak); flex-shrink: 0; }
.chat-msg {
  font-size: 13px;
  color: var(--c-text-weak);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chat-from { color: #8a8fa3; }
.chat-from.me { color: var(--c-text-weak); }
.chat-item.unread .chat-text { color: var(--c-text); }
/* 未读小红点（贴在头像右上角） */
.chat-dot {
  position: absolute;
  left: 50px;
  top: 12px;
  width: 10px; height: 10px;
  border-radius: 50%;
  background: #e0607a;
  border: 2px solid #fff;
  box-sizing: border-box;
}

/* 抽屉滑入/滑出 */
.drawer-slide-enter-active, .drawer-slide-leave-active { transition: opacity 0.25s; }
.drawer-slide-enter-active .drawer, .drawer-slide-leave-active .drawer { transition: transform 0.28s ease; }
.drawer-slide-enter-from, .drawer-slide-leave-to { opacity: 0; }
.drawer-slide-enter-from .drawer, .drawer-slide-leave-to .drawer { transform: translateX(100%); }
.empty {
  padding: 50px 20px;
  text-align: center;
  color: var(--c-text-weak);
}
.empty-icon { font-size: 52px; margin-bottom: 12px; }
.empty p { font-size: 14px; letter-spacing: 2px; }

/* 对话弹窗（气泡风格） */

/* 对话弹窗（气泡风格） */
.chat-mask {
  position:fixed; inset:0;
  background: rgba(70, 80, 70, 0.4);
  display:flex; align-items:center; justify-content:center;
  z-index:9999;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}
.chat-modal {
  width:500px; max-height:85vh;
  display:flex; flex-direction:column; overflow:hidden;
}
.chat-hd {
  padding:16px 24px;
  border-bottom: 1px solid var(--c-border);
  display:flex; justify-content:space-between; align-items:center;
  background: color-mix(in srgb, var(--c-bg2) 60%, transparent);
}
.chat-title { font-size:16px; font-weight:600; color:var(--c-text); }
.chat-close { cursor:pointer; color:var(--c-text-weak); font-size:18px; transition: color 0.2s; }
.chat-close:hover { color:var(--c-text); }

.chat-body {
  flex:1; overflow-y:auto; padding:20px;
  background: color-mix(in srgb, var(--c-bg1) 50%, transparent);
  display:flex; flex-direction:column; gap:14px;
}
.b-row { display:flex; max-width:75%; }
.b-row.me { align-self:flex-start; }
.b-row.other { align-self:flex-end; }
.b-bubble {
  padding:10px 14px;
  border-radius: 14px;
  background:#fff;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--c-hex) 8%, transparent);
  border: 1px solid var(--c-border);
}
.b-bubble.admin {
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color: #fff;
  border: none;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-hex) 35%, transparent);
}
.b-name { font-size:11px; font-weight:600; margin-bottom:4px; color:var(--c-text-weak); }
.b-bubble.admin .b-name { color: rgba(255,255,255,0.85); }
.b-msg { font-size:14px; line-height:1.6; word-break:break-all; }
.b-t { font-size:11px; color:var(--c-text-weak); margin-top:6px; }
.b-bubble.admin .b-t { color: rgba(255,255,255,0.75); }

.chat-foot {
  border-top: 1px solid var(--c-border);
  padding:14px 20px;
  background: color-mix(in srgb, var(--c-bg2) 60%, transparent);
}
.sf-area { display:flex; gap:10px; align-items:flex-end; }
.sf-area textarea {
  flex:1; padding:10px 14px;
  border: 1px solid var(--c-border);
  border-radius: 14px;
  outline:none; resize:none;
  font-size:13px; font-family:inherit;
  background: rgba(255, 255, 255, 0.8);
  transition: all 0.25s;
}
.sf-area textarea:focus {
  border-color: var(--c-hex);
  background: #fff;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 15%, transparent);
}
.sf-send {
  padding:10px 22px; border:none; border-radius: 999px;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color:#fff; font-weight:500; cursor:pointer; font-size:13px;
  box-shadow: 0 3px 10px color-mix(in srgb, var(--c-main) 35%, transparent);
  transition: all 0.25s;
}
.sf-send:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 5px 14px color-mix(in srgb, var(--c-main) 50%, transparent);
}
.sf-send:disabled { opacity:.5; cursor:not-allowed; }
.chat-del { margin-top:10px; display:flex; justify-content:flex-end; }
.del-mini {
  font-size:12px; background:transparent; color:var(--c-text-weak);
  border:none; cursor:pointer; padding:4px 8px;
  border-radius: 999px;
  transition: all 0.2s;
}
.del-mini:hover { color:#e06060; background: rgba(240, 180, 180, 0.15); }

/* 用户端分页器 */
.pager-user {
  display:flex; gap:6px; justify-content:center;
  padding:16px 0 4px; flex-wrap:wrap;
}
.pager-user button {
  padding:6px 14px; font-size:12px;
  border: 1px solid color-mix(in srgb, var(--c-main) 40%, transparent);
  background: rgba(255, 255, 255, 0.7);
  color:var(--c-text); border-radius: 999px;
  cursor:pointer; transition:all 0.25s;
}
.pager-user button:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color:#fff; border-color: transparent;
  transform: translateY(-1px);
}
.pager-user button.on {
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color:#fff; border-color: transparent;
  box-shadow: 0 3px 10px color-mix(in srgb, var(--c-main) 40%, transparent);
}
.pager-user button:disabled {
  opacity:.35; cursor:not-allowed;
}

/* ===== 响应式 ===== */
@media (max-width: 1000px) {
  .feedback-page { padding: 0 0 60px; }
  .fb-body { padding: 0 14px; }
  .hero { padding: 20px; }
  .hero h1 { font-size: 24px; }
  .fb-fab { right: 18px; bottom: 84px; width: 50px; height: 50px; }
  .fab-icon { font-size: 21px; }
  .drawer { width: 100%; border-radius: 0; }
}
</style>
