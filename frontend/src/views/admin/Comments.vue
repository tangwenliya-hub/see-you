<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/评论审核.png" class="title-icon" /> 评论审核</div>
      <div style="display:flex; gap:12px; align-items:center">
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="keyword" placeholder="搜索评论内容或用户名..." @input="onSearchInput" /></div>
        <div style="display:flex; gap:10px">
          <button :class="['jelly-btn', statusFilter==='all' ? 'primary' : 'ghost']" @click="switchStatus('all')">全部</button>
          <button :class="['jelly-btn', statusFilter==='pending' ? 'pink' : 'ghost']" @click="switchStatus('pending')">待审核</button>
          <button :class="['jelly-btn', statusFilter==='approved' ? 'primary' : 'ghost']" @click="switchStatus('approved')">已通过</button>
          <button :class="['jelly-btn', statusFilter==='rejected' ? 'danger' : 'ghost']" @click="switchStatus('rejected')">已拒绝</button>
        </div>
      </div>
    </div>

    <!-- 自动审核设置：系统先审，只有可疑评论才需要人工处理 -->
    <div class="ext-box" style="margin-bottom:18px">
      <h3><img src="/img/评论审核.png" /> 自动审核设置（改完立即生效，无需重启）</h3>
      <div class="wl-note">
        开启后系统先审：正常评论直接公开、发完即可见；只有命中可疑判据（可疑词 / 链接 / 超长 / 短时间重复）的才转人工复核。
        机器只判定「是否转人工」，不直接拒绝任何评论 —— 最终放行与否仍由管理员决定，避免误杀正常口语。
      </div>
      <div class="ar-row">
        <el-switch v-model="autoReview" :loading="switchSaving" @change="onToggleAutoReview" />
        <span class="ar-label">{{ autoReview ? '已开启：正常评论直接公开，仅可疑的转人工审核' : '已关闭：所有评论一律待审核（全人工模式）' }}</span>
      </div>
      <div class="wl-groups">
        <div class="wl-group">
          <div class="wl-head">
            <span class="wl-name review">可疑词库</span>
            <span class="wl-desc">命中 <AppIcon name="arrow-right" /> 转人工审核（不是直接拒绝）</span>
            <span class="wl-count">{{ reviewWords.length }} 个</span>
          </div>
          <div class="wl-tags">
            <span v-for="w in reviewWords" :key="w" class="wl-tag review">
              {{ w }}<i class="wl-del" title="删除该词条" @click="removeWord(w)">×</i>
            </span>
            <span class="wl-add">
              <input v-model="wordDraft" placeholder="新增可疑词…" maxlength="20" @keyup.enter="addWord" />
              <button :disabled="!wordDraft.trim() || wordSaving" @click="addWord">{{ wordSaving ? '…' : '＋ 添加' }}</button>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selectedIds.length > 0" class="batch-bar">
      <span class="batch-info">已选择 <b>{{ selectedIds.length }}</b> 条评论</span>
      <button class="jelly-btn primary" @click="batchApprove"><AppIcon name="check" /> 批量通过</button>
      <button class="jelly-btn danger" @click="batchReject"><AppIcon name="close" /> 批量拒绝</button>
      <button class="jelly-btn danger" style="background:#ff6b6b" @click="batchDelete"><AppIcon name="trash" /> 批量删除</button>
      <button class="jelly-btn ghost" @click="clearSelection">取消选择</button>
    </div>

    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th style="width:50px">
                <el-checkbox 
                  :model-value="isAllSelected" 
                  :indeterminate="isIndeterminate"
                  @change="toggleSelectAll"
                />
              </th>
              <th>ID</th><th>用户</th><th>内容</th><th>关联内容</th><th>时间</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(c, idx) in list" :key="c.commentId" :class="{ 'row-selected': selectedIds.includes(c.commentId) }">
              <td>
                <el-checkbox :model-value="selectedIds.includes(c.commentId)" @change="toggleSelect(c.commentId)" />
              </td>
              <td>{{ idx + 1 }}</td>
              <td>
                <div style="display:flex;align-items:center;gap:8px">
                  <img v-if="c.avatar" :src="getAvatarUrl(c.avatar)" style="width:28px;height:28px;border-radius:50%;object-fit:cover;border:1px solid color-mix(in srgb, var(--c-main) 30%, transparent)" @error="$event.target.src='/img/1 (1).png'" />
                  <img v-else src="/img/1 (1).png" style="width:28px;height:28px;border-radius:50%;object-fit:cover;border:1px solid color-mix(in srgb, var(--c-main) 30%, transparent)" />
                  <span>{{ c.nickname }}</span>
                </div>
              </td>
              <td style="max-width:300px">{{ c.commentText }}</td>
              <td style="color:#8a9a8a">{{ c.target }}</td>
              <td style="color:#8a9a8a">{{ fmtTime(c.createTime).slice(0, 16) }}</td>
              <td><span :class="'status-tag ' + (c.status==='pending'?'warn':c.status==='approved'?'on':'danger')">{{ c.status==='pending'?'待审核':c.status==='approved'?'已通过':'已拒绝' }}</span></td>
              <td>
                <div class="op-btns" v-if="c.status==='pending'">
                  <button class="jelly-btn op-main" @click="changeStatus(c, 1, '通过')">通过</button>
                  <button class="jelly-btn danger" @click="changeStatus(c, -1, '拒绝')">拒绝</button>
                  <button class="jelly-btn danger" style="background:#ff6b6b" @click="handleDelete(c)">删除</button>
                </div>
                <div class="op-btns" v-else>
                  <button class="jelly-btn op-sub" @click="changeStatus(c, 0, '改回待审')">改回待审</button>
                  <button class="jelly-btn danger" style="background:#ff6b6b" @click="handleDelete(c)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无评论数据~</div>
      </template>
      <!-- 分页 -->
      <div style="display:flex;justify-content:center;margin-top:20px">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import { getAllCommentsWithUser, updateCommentStatus, deleteComment, getVideoList, getAudioList, batchUpdateCommentStatus, batchDeleteComment, fetchAllPages, adminCommentWords, adminCommentWordAdd, adminCommentWordRemove, adminSettingsSave } from '@/api'
import { fmtTime } from '@/utils/exportCsv'

const list = ref([])
const keyword = ref('')
const statusFilter = ref('all')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const videoMap = ref({})
const audioMap = ref({})
const selectedIds = ref([])

const isAllSelected = computed(() => {
  return list.value.length > 0 && selectedIds.value.length === list.value.length
})

const isIndeterminate = computed(() => {
  return selectedIds.value.length > 0 && selectedIds.value.length < list.value.length
})

const getAvatarUrl = (avatar) => {
  if (!avatar) return ''
  if (avatar.startsWith('http') || avatar.startsWith('/')) return avatar
  return '/avatar/' + avatar
}

const loadResourceMaps = async () => {
  try {
    // 翻页拉全（后端单页封顶 100，size:500 会被静默截断导致资源名映射缺项）
    const [videos, audios] = await Promise.all([
      fetchAllPages(p => getVideoList({ page: p, size: 100 })),
      fetchAllPages(p => getAudioList({ page: p, size: 100 })),
    ])
    {
      const map = {}
      videos.forEach(v => { map[v.videoId || v.id] = v.videoName || v.video_name || '-' })
      videoMap.value = map
    }
    {
      const map = {}
      audios.forEach(a => { map[a.audioId || a.id] = a.audioName || a.audio_name || '-' })
      audioMap.value = map
    }
  } catch (e) { console.warn('加载资源映射失败:', e) }
}

const getTargetName = (resourceType, resourceId) => {
  if (resourceType === 2) {
    const name = videoMap.value[resourceId]
    return name ? ` ${name}` : `视频#${resourceId}`
  }
  if (resourceType === 1) {
    const name = audioMap.value[resourceId]
    return name ? ` ${name}` : `音频#${resourceId}`
  }
  return `#${resourceId}`
}

const statusToVal = (s) => {
  if (s === 'approved') return 1
  if (s === 'rejected') return -1
  if (s === 'pending') return 0
  return null
}

const switchStatus = (s) => {
  statusFilter.value = s
  page.value = 1
  selectedIds.value = []
  loadData()
}

let searchTimer = null

// 组件卸载时清理搜索防抖定时器
onBeforeUnmount(() => clearTimeout(searchTimer))
const onSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; selectedIds.value = []; loadData() }, 400)
}

const toggleSelect = (commentId) => {
  const idx = selectedIds.value.indexOf(commentId)
  if (idx > -1) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(commentId)
  }
}

const toggleSelectAll = (val) => {
  if (val) {
    selectedIds.value = list.value.map(c => c.commentId)
  } else {
    selectedIds.value = []
  }
}

const clearSelection = () => {
  selectedIds.value = []
}

const changeStatus = async (c, statusVal, label) => {
  const oldStatus = c.status
  const newStatus = statusVal === 1 ? 'approved' : statusVal === -1 ? 'rejected' : 'pending'
  try {
    const res = await updateCommentStatus(c.commentId, statusVal)
    if (res.code === 200) {
      c.status = newStatus
      ElMessage.success(`已${label}`)
    } else {
      ElMessage.error(res.msg || `${label}失败`)
    }
  } catch (e) {
    c.status = oldStatus
    ElMessage.error(`${label}失败：` + (e.message || '未知错误'))
  }
}

const batchApprove = () => {
  jellyConfirm(`确定通过选中的 ${selectedIds.value.length} 条评论吗？`, { title: '批量通过' })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await batchUpdateCommentStatus(selectedIds.value, 1)
        if (res.code === 200) {
          ElMessage.success('批量通过成功')
          selectedIds.value = []
          loadData()
        } else {
          ElMessage.error(res.msg || '批量通过失败')
        }
      } catch (e) {
        ElMessage.error('批量通过失败：' + (e.message || '未知错误'))
      }
    }).catch(() => {})
}

const batchReject = () => {
  jellyConfirm(`确定拒绝选中的 ${selectedIds.value.length} 条评论吗？`, { title: '批量拒绝' })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await batchUpdateCommentStatus(selectedIds.value, -1)
        if (res.code === 200) {
          ElMessage.success('批量拒绝成功')
          selectedIds.value = []
          loadData()
        } else {
          ElMessage.error(res.msg || '批量拒绝失败')
        }
      } catch (e) {
        ElMessage.error('批量拒绝失败：' + (e.message || '未知错误'))
      }
    }).catch(() => {})
}

const batchDelete = () => {
  jellyConfirm(`确定删除选中的 ${selectedIds.value.length} 条评论吗？此操作不可恢复！`, { title: '批量删除', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await batchDeleteComment(selectedIds.value)
        if (res.code === 200) {
          ElMessage.success('批量删除成功')
          selectedIds.value = []
          loadData()
        } else {
          ElMessage.error(res.msg || '批量删除失败')
        }
      } catch (e) {
        ElMessage.error('批量删除失败：' + (e.message || '未知错误'))
      }
    }).catch(() => {})
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    const statusVal = statusToVal(statusFilter.value)
    if (statusVal !== null) params.status = statusVal
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await getAllCommentsWithUser(params)
    if (res.code === 200) {
      const data = res.data
      const records = data?.records || []
      total.value = data?.total || 0
      // 删掉当前页最后一条后自动回退一页，避免停在空白页
      if (records.length === 0 && page.value > 1) {
        page.value -= 1
        loading.value = false
        loadData()
        return
      }
      list.value = records.map(c => ({
        commentId: c.commentId || c.id,
        userId: c.userId,
        nickname: c.nickname || '用户' + (c.userId || ''),
        avatar: c.avatar || '',
        commentText: c.commentText || c.content || '-',
        resourceType: c.resourceType,
        resourceId: c.resourceId,
        target: getTargetName(c.resourceType, c.resourceId),
        createTime: c.createTime || c.time || '-',
        status: c.status === 1 ? 'approved' : c.status === -1 ? 'rejected' : 'pending',
      }))
    }
  } catch (e) {
    console.warn('加载评论列表失败:', e)
  } finally {
    loading.value = false
  }
}

const handleDelete = (c) => {
  jellyConfirm('确定删除这条评论吗？', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteComment(c.commentId)
        if (res.code === 200) { ElMessage.success('删除成功'); loadData() }
        else ElMessage.error(res.msg || '删除失败')
      } catch (e) { ElMessage.error('删除失败：' + (e.message || '未知错误')) }
    }).catch(() => {})
}

// ==================== 自动审核设置（系统先审，可疑才转人工） ====================
// 词库与开关都是「内存立即生效 + sys_config 持久化」—— 改完直接发条评论就能验证，不用重启后端
const autoReview = ref(true)
const switchSaving = ref(false)
const reviewWords = ref([])
const wordDraft = ref('')
const wordSaving = ref(false)

const loadModeration = async () => {
  try {
    const res = await adminCommentWords()
    if (res?.code !== 200) { ElMessage.error(res?.msg || '审核设置加载失败'); return }
    const d = res.data || {}
    autoReview.value = !!d.autoReview
    reviewWords.value = Array.isArray(d.review) ? d.review : []
  } catch (e) { console.warn('审核设置加载失败:', e) }
}

const onToggleAutoReview = async (val) => {
  switchSaving.value = true
  try {
    const res = await adminSettingsSave({ 'comment.autoReview': val })
    if (res?.code !== 200) {
      autoReview.value = !val   // 失败回滚
      ElMessage.error(res?.msg || '保存失败')
      return
    }
    ElMessage.success(val ? '已开启自动审核：正常评论直接公开，仅可疑的转人工' : '已关闭自动审核：所有评论一律待人工审核')
  } catch (e) {
    autoReview.value = !val
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  } finally { switchSaving.value = false }
}

const addWord = async () => {
  const word = wordDraft.value.trim()
  if (!word || wordSaving.value) return
  if (word.includes(',') || word.includes('，')) { ElMessage.warning('词条不能包含逗号'); return }
  wordSaving.value = true
  try {
    const res = await adminCommentWordAdd(word)
    if (res?.code !== 200) { ElMessage.error(res?.msg || '添加失败'); return }
    reviewWords.value.push(word)
    wordDraft.value = ''
    ElMessage.success(`已添加「${word}」，立即生效`)
  } finally { wordSaving.value = false }
}

const removeWord = async (w) => {
  try {
    const res = await adminCommentWordRemove(w)
    if (res?.code !== 200) { ElMessage.error(res?.msg || '删除失败'); return }
    reviewWords.value = reviewWords.value.filter(x => x !== w)
    ElMessage.success(`已删除「${w}」`)
  } catch { ElMessage.error('删除失败，请重试') }
}

onMounted(async () => {
  loadModeration()
  await loadResourceMaps()
  loadData()
})
</script>

<style scoped>
/* batch-bar / batch-info / row-selected 样式已抽至 styles/admin.css（全站后台列表通用） */
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
/* ===== 自动审核设置（样式与预警页词库管理保持一致） ===== */
.wl-note {
  font-size: 12.5px; line-height: 1.8; color: #6b7f93;
  background: #f4f8fb; border-left: 3px solid #8fb8d8;
  border-radius: 8px; padding: 8px 12px; margin-bottom: 14px;
}
.ar-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.ar-label { font-size: 13px; color: #4a5a4a; }
.wl-groups { display: flex; flex-direction: column; gap: 14px; }
.wl-group {
  background: var(--c-bg1, #f7faf5); border-radius: 12px;
  padding: 12px 14px;
}
.wl-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 10px; }
.wl-name { font-size: 14px; font-weight: 800; }
.wl-name.review { color: #cf8a3a; }
.wl-desc { font-size: 12px; color: #98a2b3; }
.wl-count { margin-left: auto; font-size: 12px; color: #98a2b3; }
.wl-tags { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.wl-tag {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12.5px; padding: 4px 8px 4px 12px; border-radius: 999px;
  transition: transform 0.15s ease;
}
.wl-tag:hover { transform: translateY(-1px); }
.wl-tag.review { background: #fdf3e3; color: #a06a28; border: 1px solid #efd9b8; }
.wl-del {
  font-style: normal; width: 16px; height: 16px; line-height: 14px; text-align: center;
  border-radius: 50%; cursor: pointer; font-size: 13px;
  background: rgba(0,0,0,0.08); color: inherit;
  transition: all 0.15s ease;
}
.wl-del:hover { background: #d65757; color: #fff; }
.wl-add { display: inline-flex; align-items: center; gap: 6px; }
.wl-add input {
  width: 170px; padding: 5px 10px; font-size: 12.5px;
  border: 1px dashed #c4cfda; border-radius: 999px; outline: none;
  background: #fff; color: #3d4a3d; transition: border-color 0.2s;
}
.wl-add input:focus { border-color: #5aa96c; border-style: solid; }
.wl-add button {
  padding: 5px 12px; font-size: 12.5px; border: none; border-radius: 999px;
  background: linear-gradient(135deg, #9ed3a8, #5aa96c); color: #fff;
  cursor: pointer; transition: opacity 0.2s;
}
.wl-add button:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
