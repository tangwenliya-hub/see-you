<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/AI 对话.png" class="title-icon" /> AI 对话记录</div>
      <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
    </div>

    <div class="ext-note info">
      仅用于<b>内容安全审计与服务质量评估</b>：查看 AI 回复是否存在不当内容、用户是否在对话中流露极端情绪。
      会话按用户隔离存放，后台不做跨用户的关键词检索。
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat"><div class="l"><img src="/img/AI对话次数.png" />消息总数</div><div class="v">{{ s.messages }}</div><div class="s">用户 + AI 各计一条</div></div>
      <div class="ext-stat is-blue"><div class="l"><img src="/img/AI 对话.png" />会话数</div><div class="v">{{ s.sessions }}</div><div class="s">平均 {{ s.avgPerSession }} 条/会话</div></div>
      <div class="ext-stat is-purple"><div class="l"><img src="/img/用户管理.png" />使用用户</div><div class="v">{{ s.users }}</div><div class="s">使用过 AI 陪伴的人数</div></div>
      <div class="ext-stat"><div class="l"><img src="/img/用户活跃趋势.png" />近 7 天消息</div><div class="v">{{ weekTotal }}</div><div class="s">趋势见下方图表</div></div>
    </div>

    <div class="ext-box" style="margin-bottom:18px">
      <h3><img src="/img/用户活跃趋势.png" /> 近 7 天 AI 对话量</h3>
      <div ref="trendRef" class="ext-chart"></div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <div class="admin-search">
          <img src="/img/搜索.png" class="search-icon" />
          <input v-model="keyword" placeholder="搜索用户名..." @input="onSearchInput" />
        </div>
      </div>
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="users.length > 0" class="admin-table">
          <thead><tr><th>用户</th><th>消息数</th><th>会话数</th><th>最近对话</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="u in users" :key="u.userId">
              <td>{{ u.userName }}</td>
              <td>{{ u.messages }}</td>
              <td>{{ u.sessions }}</td>
              <td style="color:#8a9a8a">{{ fmt(u.lastTime) }}</td>
              <td><button class="jelly-btn ghost" @click="openSessions(u)">查看会话</button></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无 AI 对话记录~</div>
      </template>
    </div>

    <!-- 会话列表 -->
    <el-dialog v-model="sessionVisible" :title="`${curUser.userName} 的会话`" width="640px">
      <div v-if="sessions.length === 0" class="table-empty">该用户暂无会话</div>
      <table v-else class="admin-table">
        <thead><tr><th>会话 ID</th><th>消息数</th><th>开始时间</th><th>首句</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="s in sessions" :key="s.sessionId">
            <td style="font-family:monospace;font-size:12px">{{ short(s.sessionId) }}</td>
            <td>{{ s.count }}</td>
            <td style="color:#8a9a8a">{{ fmt(s.startTime) }}</td>
            <td style="max-width:220px" class="ellipsis">{{ s.preview }}</td>
            <td><button class="jelly-btn ghost" @click="openMessages(s)">查看内容</button></td>
          </tr>
        </tbody>
      </table>
    </el-dialog>

    <!-- 消息明细 -->
    <el-dialog v-model="msgVisible" title="对话内容" width="640px">
      <div v-if="messages.length === 0" class="table-empty">无消息</div>
      <div v-else class="chat-scroll">
        <div v-for="m in messages" :key="m.chatId" class="chat-line">
          <img class="cl-avatar" :src="curUser.avatar || '/img/1 (1).png'" />
          <div class="cl-body">
            <div class="cl-name">{{ curUser.userName }} · {{ fmt(m.createTime) }}</div>
            <div class="cl-text">{{ m.userMsg }}</div>
          </div>
        </div>
        <div v-for="m in messages" :key="'ai-' + m.chatId" class="chat-line is-ai">
          <img class="cl-avatar" src="/img/AI对话奶牛.png" />
          <div class="cl-body">
            <div class="cl-name">绪见 AI · {{ fmt(m.createTime) }}</div>
            <div class="cl-text">{{ m.aiReply }}</div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { adminAiChatStats, adminAiChatUsers, adminAiChatSessions, adminAiChatMessages } from '@/api'
import { useChart } from '@/composables/useChart'
import { accent, textColor, weakColor, borderColor, hexA, categoryAxis, valueAxis } from '@/utils/adminTheme'

const s = ref({ messages: 0, sessions: 0, users: 0, avgPerSession: 0 })
const trendData = ref([])
const users = ref([])
const keyword = ref('')
const loading = ref(false)

const sessionVisible = ref(false)
const msgVisible = ref(false)
const sessions = ref([])
const messages = ref([])
const curUser = ref({})
let searchTimer = null

const trendRef = ref(null)
const trendChart = useChart(trendRef, 'admin-aichat-trend')

const weekTotal = computed(() => trendData.value.reduce((a, b) => a + Number(b.count || 0), 0))
const fmt = (t) => t ? String(t).replace('T', ' ').slice(0, 16) : '-'
const short = (v) => String(v || '').length > 18 ? String(v).slice(0, 18) + '…' : v

const loadStats = async () => {
  const res = await adminAiChatStats()
  if (res?.code !== 200) { ElMessage.error(res?.msg || 'AI 对话统计加载失败'); return }
  const r = res.data || {}
  s.value = {
    messages: Number(r.messages || 0), sessions: Number(r.sessions || 0),
    users: Number(r.users || 0), avgPerSession: Number(r.avgPerSession || 0),
  }
  trendData.value = Array.isArray(r.trend) ? r.trend : []
  trendChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', confine: true },
    grid: { left: 8, right: 14, top: 20, bottom: 24, containLabel: true },
    xAxis: categoryAxis(trendData.value.map(i => String(i.date || '').slice(5))),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [{
      name: '消息数', type: 'line', smooth: true, data: trendData.value.map(i => i.count),
      areaStyle: { color: hexA(accent(), 0.15) }, itemStyle: { color: accent() }, lineStyle: { width: 3 },
    }],
  }))
}

const loadUsers = async () => {
  const res = await adminAiChatUsers({ keyword: keyword.value.trim() || undefined })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '用户列表加载失败'); return }
  users.value = Array.isArray(res.data) ? res.data : []
}

const loadAll = async () => {
  loading.value = true
  try { await Promise.all([loadStats(), loadUsers()]) } finally { loading.value = false }
}

const onSearchInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadUsers, 400)
}

const openSessions = async (u) => {
  curUser.value = u
  const res = await adminAiChatSessions(u.userId)
  if (res?.code !== 200) { ElMessage.error(res?.msg || '会话加载失败'); return }
  sessions.value = Array.isArray(res.data) ? res.data : []
  sessionVisible.value = true
}

const openMessages = async (s) => {
  const res = await adminAiChatMessages(s.sessionId)
  if (res?.code !== 200) { ElMessage.error(res?.msg || '消息加载失败'); return }
  messages.value = Array.isArray(res.data) ? res.data : []
  msgVisible.value = true
}

onMounted(async () => { await nextTick(); loadAll() })
onBeforeUnmount(() => clearTimeout(searchTimer))
</script>

<style scoped>
.chat-scroll { max-height: 60vh; overflow-y: auto; padding-right: 4px; }
.ellipsis { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
