<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/账号安全.png" class="title-icon" /> 账号安全</div>
      <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
    </div>

    <div class="ext-note info">
      多设备会话、登录失败锁定、邮箱找回申请的统一管理入口。
      <b>令牌在后台只展示脱敏片段</b>，找回申请的验证码不下发到前端，避免二次泄露。
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat"><div class="l"><img src="/img/用户管理.png" />在线会话</div><div class="v">{{ o.onlineSessions }}</div><div class="s">历史累计 {{ o.totalSessions }}</div></div>
      <div class="ext-stat is-red"><div class="l"><img src="/img/账号安全.png" />锁定账号</div><div class="v">{{ o.locked }}</div><div class="s" :class="{ warn: o.locked > 0 }">连续失败 5 次锁定 10 分钟</div></div>
      <div class="ext-stat is-purple"><div class="l"><img src="/img/我的反馈.png" />待处理找回</div><div class="v">{{ o.pendingResets }}</div><div class="s">已完成 {{ o.usedResets }}</div></div>
      <div class="ext-stat is-blue"><div class="l"><img src="/img/系统参数.png" />安全策略</div><div class="v" style="font-size:16px">已启用</div><div class="s">验证码 + 限流 + 会话校验</div></div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <button :class="['jelly-btn', tab === 'sessions' ? 'primary' : 'ghost']" @click="tab = 'sessions'">在线会话</button>
        <button :class="['jelly-btn', tab === 'attempts' ? 'primary' : 'ghost']" @click="tab = 'attempts'">登录锁定</button>
        <button :class="['jelly-btn', tab === 'resets' ? 'primary' : 'ghost']" @click="tab = 'resets'">密码找回</button>
        <el-select v-if="tab === 'sessions'" v-model="status" placeholder="全部" clearable @change="loadAll">
          <el-option label="在线" :value="1" />
          <el-option label="已下线" :value="0" />
        </el-select>
      </div>

      <div v-if="loading" class="table-loading">加载中...</div>

      <!-- 在线会话 -->
      <template v-else-if="tab === 'sessions'">
        <table v-if="sessions.length > 0" class="admin-table">
          <thead><tr><th>用户</th><th>设备</th><th>IP</th><th>状态</th><th>登录时间</th><th>最后活跃</th><th>令牌</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="s in sessions" :key="s.sessionId">
              <td>{{ s.userName }}</td>
              <td>{{ s.device || '-' }}</td>
              <td>{{ s.ip || '-' }}</td>
              <td><span class="status-tag" :class="s.status === 1 ? 'on' : ''">{{ s.status === 1 ? '在线' : '已下线' }}</span></td>
              <td style="color:#8a9a8a">{{ fmt(s.loginTime) }}</td>
              <td style="color:#8a9a8a">{{ fmt(s.lastActiveTime) }}</td>
              <td style="font-family:monospace;font-size:11px;color:#b0bdb0">{{ s.tokenMasked }}</td>
              <td>
                <button v-if="s.status === 1" class="jelly-btn danger" @click="handleKick(s)">强制下线</button>
                <span v-else style="color:#b0bdb0">-</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无会话记录~</div>
      </template>

      <!-- 登录锁定 -->
      <template v-else-if="tab === 'attempts'">
        <table v-if="attempts.length > 0" class="admin-table">
          <thead><tr><th>账号</th><th>失败次数</th><th>锁定至</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="a in attempts" :key="a.id">
              <td><b>{{ a.username }}</b></td>
              <td>{{ a.failCount }}</td>
              <td style="color:#8a9a8a">{{ fmt(a.lockUntil) }}</td>
              <td><span class="status-tag" :class="a.locked ? 'danger' : 'on'">{{ a.locked ? '锁定中' : '已解除' }}</span></td>
              <td><button class="jelly-btn op-main" @click="handleUnlock(a)">解除锁定</button></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无登录失败记录，大家密码都记得很清楚~</div>
      </template>

      <!-- 密码找回 -->
      <template v-else>
        <table v-if="resets.length > 0" class="admin-table">
          <thead><tr><th>用户</th><th>申请时间</th><th>有效期至</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="r in resets" :key="r.id">
              <td>{{ r.userName }}</td>
              <td style="color:#8a9a8a">{{ fmt(r.createTime) }}</td>
              <td style="color:#8a9a8a">{{ fmt(r.expireTime) }}</td>
              <td>
                <span class="status-tag" :class="r.used ? 'on' : (r.expired ? '' : 'warn')">
                  {{ r.used ? '已完成' : (r.expired ? '已过期' : '待使用') }}
                </span>
              </td>
              <td><button class="jelly-btn danger" @click="handleDeleteReset(r)">删除</button></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无密码找回申请~</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import {
  adminSecurityOverview, adminSecuritySessions, adminSecurityKick,
  adminSecurityAttempts, adminSecurityUnlock, adminSecurityResets, adminSecurityResetDelete,
} from '@/api'

const o = ref({ onlineSessions: 0, totalSessions: 0, locked: 0, pendingResets: 0, usedResets: 0 })
const tab = ref('sessions')
const status = ref(null)
const sessions = ref([])
const attempts = ref([])
const resets = ref([])
const loading = ref(false)

const fmt = (t) => t ? String(t).replace('T', ' ').slice(0, 16) : '-'

const loadAll = async () => {
  loading.value = true
  try {
    const ov = await adminSecurityOverview()
    if (ov?.code === 200) {
      const r = ov.data || {}
      o.value = {
        onlineSessions: Number(r.onlineSessions || 0), totalSessions: Number(r.totalSessions || 0),
        locked: Number(r.locked || 0), pendingResets: Number(r.pendingResets || 0), usedResets: Number(r.usedResets || 0),
      }
    }
    const se = await adminSecuritySessions({ status: status.value ?? undefined })
    if (se?.code === 200) sessions.value = Array.isArray(se.data) ? se.data : []
    const at = await adminSecurityAttempts()
    if (at?.code === 200) attempts.value = Array.isArray(at.data) ? at.data : []
    const rs = await adminSecurityResets()
    if (rs?.code === 200) resets.value = Array.isArray(rs.data) ? rs.data : []
  } finally { loading.value = false }
}

const handleKick = (s) => {
  jellyConfirm(`确定将「${s.userName}」的该设备强制下线吗？`, { title: '强制下线' })
    .then(async (ok) => {
      if (!ok) return
      const res = await adminSecurityKick(s.sessionId)
      if (res?.code !== 200) { ElMessage.error(res?.msg || '操作失败'); return }
      ElMessage.success({ message: '已强制下线', grouping: true })
      loadAll()
    }).catch(() => {})
}

const handleUnlock = (a) => {
  jellyConfirm(`确定解除「${a.username}」的登录锁定吗？`, { title: '解除锁定' })
    .then(async (ok) => {
      if (!ok) return
      const res = await adminSecurityUnlock(a.id)
      if (res?.code !== 200) { ElMessage.error(res?.msg || '操作失败'); return }
      ElMessage.success({ message: '已解除锁定', grouping: true })
      loadAll()
    }).catch(() => {})
}

const handleDeleteReset = (r) => {
  jellyConfirm('确定删除这条密码找回申请吗？', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      const res = await adminSecurityResetDelete(r.id)
      if (res?.code !== 200) { ElMessage.error(res?.msg || '删除失败'); return }
      ElMessage.success({ message: '已删除', grouping: true })
      loadAll()
    }).catch(() => {})
}

onMounted(loadAll)
</script>
