<template>
  <div>
    <!-- 工具栏 -->
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/用户反馈.png" class="title-icon" /> 用户反馈管理</div>
      <div style="display:flex; gap:12px; align-items:center">
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="keyword" placeholder="搜索标题/内容/用户" /></div>
        <div style="display:flex; gap:10px">
          <button :class="['jelly-btn', filter === 'all' ? 'primary' : 'ghost']" @click="filter='all'">全部</button>
          <button :class="['jelly-btn', filter === '0' ? 'pink' : 'ghost']" @click="filter='0'">未处理 ({{ unreadCount }})</button>
          <button :class="['jelly-btn', filter === '1' ? 'primary' : 'ghost']" @click="filter='1'">处理中 ({{ processingCount }})</button>
          <button :class="['jelly-btn', filter === '2' ? 'primary' : 'ghost']" @click="filter='2'">已完成 ({{ doneCount }})</button>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <!-- 批量操作条 -->
        <div v-if="selectedIds.length > 0" class="batch-bar">
          <span class="batch-info">已选择 <b>{{ selectedIds.length }}</b> 条反馈</span>
          <button class="jelly-btn danger" @click="batchDelete">🗑 批量删除</button>
        </div>
        <table v-if="pagedList.length > 0" class="admin-table">
        <thead>
          <tr>
            <th style="width:50px">
              <el-checkbox :model-value="isAllSelected" :indeterminate="isIndeterminate" @change="toggleSelectAll" />
            </th>
            <th>ID</th>
            <th>用户</th>
            <th>类型</th>
            <th>标题</th>
            <th>内容</th>
            <th>时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in pagedList" :key="f.feedbackId" :class="{ 'row-selected': selectedIds.includes(f.feedbackId) }" @click="openDetail(f)" style="cursor:pointer">
            <td @click.stop><el-checkbox :model-value="selectedIds.includes(f.feedbackId)" @change="toggleSelect(f.feedbackId)" /></td>
            <td>
              <span v-if="hasNewSupplement(f) || f.status == 0" class="red-dot"></span>
              {{ f.feedbackId }}
            </td>
            <td>
              <div style="display:flex;align-items:center;gap:8px">
                <img :src="f.userAvatar || '/img/1 (1).png'" @error="$event.target.src='/img/1 (1).png'" style="width:28px;height:28px;border-radius:50%;object-fit:cover" />
                <span>{{ f.username }}</span>
              </div>
            </td>
            <td><span class="status-tag warn">{{ typeIcon(f.type) }} {{ typeLabel(f.type) }}</span></td>
            <td>{{ f.title || '-' }}</td>
            <td style="max-width:260px">
              <span v-if="f.userSupplement" class="tag-supp">补</span>
              <span v-else class="tag-fb">原</span>
              <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;display:inline-block;max-width:200px;vertical-align:bottom">{{ f.userSupplement || f.content }}</span>
            </td>
            <td style="color:#8a9a8a">{{ fmtTime(f.supplementTime || f.createTime) }}</td>
            <td>
              <span :class="'status-tag ' + (f.status == 0 ? 'warn' : f.status == 1 ? '' : 'on')">
                {{ f.status == 0 ? '待回复' : f.status == 1 ? '处理中' : '已完成' }}
              </span>
              <span v-if="f.replyText" class="status-tag on" style="margin-left:4px">已回复</span>
              <span v-if="hasNewSupplement(f)" class="status-tag danger pulse" style="margin-left:4px">用户补充</span>
            </td>
            <td @click.stop>
              <div class="op-btns">
                <button class="jelly-btn ghost" @click="openDetail(f)">查看对话</button>
                <button class="jelly-btn ghost" @click="openEdit(f)">编辑</button>
                <button class="jelly-btn danger" @click="handleDelete(f)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="table-empty">暂无反馈数据~</div>

      <!-- 分页 -->
      <div style="display:flex;justify-content:center;margin-top:20px">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredList.length"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="pageNum=1"
          background
        />
      </div>
      </template>
    </div>

    <!-- 对话弹窗 -->
    <el-dialog v-model="detailVisible" title="反馈对话" width="580px" :close-on-click-modal="false">
      <div class="chat-timeline">
        <div v-for="(m, idx) in detailMessages" :key="idx"
             :class="['chat-msg', m.senderType === 'admin' ? 'admin-msg' : 'user-msg']">
          <div class="msg-head">
            <span :class="['badge', m.senderType === 'admin' ? 'admin' : 'user']">
              {{ m.senderType === 'admin' ? '管理员' : (idx === 0 && detailTarget?.title ? detailTarget.username : '用户') }}
            </span>
            <span class="msg-time">{{ fmtTime(m.createTime) }}</span>
          </div>
          <div v-if="idx === 0 && detailTarget?.title" class="msg-title">{{ detailTarget.title }}</div>
          <div :class="['msg-body', m.senderType === 'admin' ? 'admin-body' : '']">{{ m.content }}</div>
        </div>
        <div v-if="detailMessages.length === 0" class="empty-chat">暂无对话记录</div>
      </div>

      <div class="reply-input-area">
        <el-input v-model="replyText" type="textarea" :rows="3" placeholder="写下你的回复..." />
        <div class="reply-actions">
          <button class="jelly-btn ghost" @click="detailVisible = false">关闭</button>
          <button class="jelly-btn primary" :disabled="!replyText.trim()" @click="sendReply">发送回复</button>
        </div>
      </div>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑反馈" width="480px">
      <el-form v-if="editTarget" :model="editTarget" label-width="80px" label-position="right">
        <el-form-item label="标题">
          <el-input v-model="editTarget.title" placeholder="反馈标题" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editTarget.type" style="width:100%">
            <el-option label="功能建议" value="suggest" />
            <el-option label="问题反馈" value="bug" />
            <el-option label="界面体验" value="ui" />
            <el-option label="内容相关" value="content" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model.number="editTarget.status" style="width:100%">
            <el-option label="待回复" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已完成" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="原始内容">
          <el-input v-model="editTarget.content" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="jelly-btn ghost" @click="editVisible = false">取消</button>
        <button class="jelly-btn primary" @click="saveEdit">保存修改</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getFeedbackList, updateFeedback, replyFeedback, deleteFeedback, getFeedbackMessages } from '@/api'
import { useBatchSelect } from '@/composables/useBatchSelect'
import { jellyConfirm } from '@/utils/confirm'
// WS 已在 AdminLayout 全局建立，这里不再自己连接

const list = ref([])
const loading = ref(false)
const filter = ref('all')
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const detailVisible = ref(false)
const detailTarget = ref(null)
const editVisible = ref(false)
const editTarget = ref(null)
const replyText = ref('')
const detailMessages = ref([])
// 标记哪些 feedbackId 有用户新补充（打开过就清掉）
const seenSupplement = ref(new Set())

const hasNewSupplement = (f) => {
  if (!f.userSupplement) return false
  return !seenSupplement.value.has(f.feedbackId)
}

const typeMap = { suggest:'功能建议', bug:'问题反馈', ui:'界面体验', content:'内容相关', other:'其他' }
const typeIcons = { suggest:'', bug:'', ui:'', content:'', other:'' }
const typeLabel = (t) => typeMap[t] || t || '其他'
const typeIcon = (t) => typeIcons[t] || ''
const fmtTime = (t) => String(t || '').replace('T',' ').slice(0,16)

const filteredList = computed(() => {
  let arr = list.value
  if (filter.value !== 'all') arr = arr.filter(f => String(f.status) === filter.value)
  if (keyword.value.trim()) {
    const k = keyword.value.trim().toLowerCase()
    arr = arr.filter(f =>
      (f.username||'').toLowerCase().includes(k) ||
      (f.title||'').toLowerCase().includes(k) ||
      (f.content||'').toLowerCase().includes(k)
    )
  }
  return arr
})
const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

// 批量勾选（按当前页）
const { selectedIds, isAllSelected, isIndeterminate, toggleSelect, toggleSelectAll, clearSelection } = useBatchSelect(pagedList, f => f.feedbackId)

// 批量删除
const batchDelete = () => {
  jellyConfirm(`确定删除选中的 ${selectedIds.value.length} 条反馈吗？删除后不可恢复！`, { title: '批量删除反馈', confirmText: '删除', danger: true })
    .then(async (ok) => {
      if (!ok) return
      let done = 0
      for (const id of selectedIds.value) {
        try {
          const res = await deleteFeedback(id)
          if (res.code === 200) done++
        } catch {}
      }
      ElMessage.success(`已删除 ${done}/${selectedIds.value.length} 条反馈`)
      clearSelection()
      loadData()
    })
}
// 删除/筛选导致数据变少时，把页码收敛回有效范围，避免停在空白页
watch(() => filteredList.value.length, (len) => {
  const maxPage = Math.max(1, Math.ceil(len / pageSize.value))
  if (pageNum.value > maxPage) pageNum.value = maxPage
})

const unreadCount = computed(() => list.value.filter(f => f.status == 0).length)
const processingCount = computed(() => list.value.filter(f => f.status == 1).length)
const doneCount = computed(() => list.value.filter(f => f.status == 2).length)

const loadData = async () => {
  loading.value = true
  try {
    const res = await getFeedbackList({ page:1, size:500, showDeleted: true })
    if (res.code === 200) {
      let arr = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      arr.sort((a,b) => new Date(b.createTime) - new Date(a.createTime))
      list.value = arr
    }
  } catch { ElMessage.error('加载反馈列表失败') }
  finally { loading.value = false }
}

// 暴露给 AdminLayout 收到 WS 推送时调（卸载时清理，避免挂在 window 上变成全局泄漏）
if (typeof window !== 'undefined') window.__feedbacksLoadData = loadData
onBeforeUnmount(() => {
  if (typeof window !== 'undefined' && window.__feedbacksLoadData === loadData) {
    delete window.__feedbacksLoadData
  }
})

// 打开对话弹窗
const openDetail = async (f) => {
  seenSupplement.value.add(f.feedbackId)
  detailTarget.value = { ...f }
  replyText.value = ''
  detailVisible.value = true
  // 加载完整聊天记录
  try {
    const res = await getFeedbackMessages(f.feedbackId)
    if (res.code === 200) {
      detailMessages.value = res.data || []
    } else {
      detailMessages.value = []
    }
  } catch {
    detailMessages.value = []
  }
}

const openEdit = (f) => {
  editTarget.value = { ...f }
  editVisible.value = true
}

const saveEdit = async () => {
  try {
    const res = await updateFeedback(editTarget.value)
    if (res.code === 200) {
      ElMessage.success('保存成功')
      editVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  }
}

const sendReply = async () => {
  if (!replyText.value.trim() || !detailTarget.value) return
  try {
    const res = await replyFeedback({ feedbackId: detailTarget.value.feedbackId, replyText: replyText.value })
    if (res.code === 200) {
      ElMessage.success('回复成功，用户会实时收到')
      detailVisible.value = false
      replyText.value = ''
      await loadData()
    } else {
      ElMessage.error(res.msg || '回复失败')
    }
  } catch (e) {
    ElMessage.error('网络错误：' + (e.message || '未知错误'))
  }
}

const handleDelete = (f) => {
  jellyConfirm(`确定删除 ${f.username} 的这条反馈吗？`, { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteFeedback(f.feedbackId)
        if (res.code === 200) { ElMessage.success('删除成功'); loadData() }
        else ElMessage.error(res.msg || '删除失败')
      } catch (e) {
        ElMessage.error('删除失败：' + (e.message || '未知错误'))
      }
    }).catch(() => {})
}

onMounted(loadData)

// 筛选变化时回到第1页
watch([filter, keyword], () => { pageNum.value = 1 })
</script>

<style scoped>
.table-loading {
  text-align: center;
  padding: 60px 0;
  color: #999;
  font-size: 14px;
}
.table-empty {
  text-align: center;
  padding: 60px 0;
  color: #bbb;
  font-size: 14px;
}
.red-dot {
  display:inline-block; width:8px; height:8px; border-radius:50%;
  background:#ff6b6b; margin-right:6px;
  box-shadow:0 0 0 2px rgba(255,107,107,.2);
}
.tag-fb { display:inline-block; padding:2px 8px; margin-right:4px; font-size:11px; color:#fff; background:linear-gradient(135deg, var(--c-hex), var(--c-main)); border-radius:6px; }
.tag-supp { display:inline-block; padding:2px 8px; margin-right:4px; font-size:11px; color:#c76b6b; background:linear-gradient(135deg,#f8c9d8,#fde7ee); border-radius:6px; }
.pulse { animation:pulse 1.4s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }

/* ===== 对话弹窗内的聊天区域 ===== */
.chat-timeline {
  max-height: 50vh; overflow-y: auto; padding: 16px;
  display:flex; flex-direction:column; gap:12px;
  background:linear-gradient(180deg, #f8fbf6, #f0f8ef);
  border-radius: 12px;
}
.chat-msg { padding:12px 14px; border-radius:14px; background:var(--c-surface); border:1px solid color-mix(in srgb, var(--c-main) 20%, transparent); }
.admin-msg { background:linear-gradient(135deg, color-mix(in srgb, var(--c-main) 20%, transparent), color-mix(in srgb, var(--c-hex) 12%, transparent)); border-color:color-mix(in srgb, var(--c-hex) 30%, transparent); }
.msg-head { display:flex; gap:10px; align-items:center; margin-bottom:6px; }
.badge { font-size:12px; padding:3px 10px; border-radius:999px; font-weight:500; }
.badge.user { background:color-mix(in srgb, var(--c-main) 40%, transparent); color:#556a55; }
.badge.admin { background:linear-gradient(135deg, var(--c-hex), var(--c-main)); color:#fff; }
.msg-time { font-size:11px; color:#8a9a8a; }
.msg-title { font-weight:600; color:#2f4a22; margin-bottom:4px; font-size:13px; }
.msg-body { font-size:14px; line-height:1.7; color:#445a44; }
.admin-body { color:#2f4a22; }
.empty-chat { text-align:center; padding:40px; color:#b8c5b0; font-size:14px; }

.reply-input-area {
  margin-top: 16px;
}
.reply-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:12px; }
</style>
