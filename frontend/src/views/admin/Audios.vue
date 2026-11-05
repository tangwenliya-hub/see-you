<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/治愈音频.png" class="title-icon" /> 治愈音频管理（绪见 FM）</div>
      <div style="display:flex; gap:12px; align-items:center">
        <el-select v-model="filterCat" placeholder="全部分类" style="width:160px" clearable @change="page = 1; loadData()">
          <el-option v-for="c in audioCategories" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="keyword" placeholder="搜索音频内容..." @input="onSearch" /></div>
        <button class="jelly-btn primary" @click="handleAdd">＋ 新增音频</button>
      </div>
    </div>
    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
      <!-- 批量操作条 -->
      <div v-if="selectedIds.length > 0" class="batch-bar">
        <span class="batch-info">已选择 <b>{{ selectedIds.length }}</b> 条音频</span>
        <button class="jelly-btn danger" @click="batchDelete">🗑 批量删除</button>
      </div>
      <table v-if="list.length > 0" class="admin-table">
        <thead><tr>
          <th style="width:50px"><el-checkbox :model-value="isAllSelected" :indeterminate="isIndeterminate" @change="toggleSelectAll" /></th>
          <th>ID</th><th>封面</th><th>音频内容</th><th>音频</th><th>分类</th><th>播放量</th><th>创建时间</th><th>操作</th>
        </tr></thead>
        <tbody>
          <tr v-for="a in list" :key="a.audioId" :class="{ 'row-selected': selectedIds.includes(a.audioId) }">
            <td><el-checkbox :model-value="selectedIds.includes(a.audioId)" @change="toggleSelect(a.audioId)" /></td>
            <td>{{ a.audioId }}</td>
            <td>
              <img v-if="getCoverUrl(a.coverImg)" :src="getCoverUrl(a.coverImg)"
                   style="width:60px;height:60px;border-radius:10px;object-fit:cover;background:#f0f0f0" />
              <span v-else style="color:#ccc;font-size:12px">暂无</span>
            </td>
            <td>{{ a.audioName || '-' }}</td>
            <td>
              <button v-if="a.audioUrl" class="jelly-btn ghost" style="padding:4px 14px;font-size:13px" @click="playAudio(a)">🎵 试听</button>
              <span v-else style="color:#ccc;font-size:12px">-</span>
            </td>
            <td>{{ a.category || '-' }}</td>
            <td>{{ a.playCount || 0 }}</td>
            <td>{{ a.createTime ? fmtTime(a.createTime).slice(0, 16) : '-' }}</td>
            <td>
              <div class="op-btns">
                <button class="jelly-btn ghost" @click="handleEdit(a)">编辑</button>
                <button class="jelly-btn danger" @click="handleDelete(a)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="table-empty">暂无音频数据，点击"新增音频"添加~</div>
      <!-- 分页 -->
      <div style="display:flex;justify-content:center;margin-top:20px">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          background
        />
      </div>
      </template>

      <!-- 试听弹窗 -->
      <el-dialog v-model="playVisible" title="音频试听" width="420px" append-to-body>
        <div style="text-align:center">
          <img v-if="currentAudio" :src="getCoverUrl(currentAudio.coverImg)" style="width:160px;height:160px;border-radius:20px;object-fit:cover;margin:0 auto 16px;box-shadow:0 4px 20px rgba(0,0,0,.1)" />
          <div style="font-weight:600;font-size:16px;margin-bottom:4px">{{ currentAudio?.audioName }}</div>
          <div style="color:#999;font-size:12px;margin-bottom:16px">{{ currentAudio?.category }}</div>
          <audio v-if="currentAudio?.audioUrl" :src="currentAudio.audioUrl" controls style="width:100%"></audio>
        </div>
        <template #footer><button class="jelly-btn ghost" @click="playVisible=false">关闭</button></template>
      </el-dialog>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.audioId?'编辑音频':'新增音频'" width="640px">
      <el-form :model="form" label-width="100px" label-position="right">
        <el-form-item label="音频内容"><el-input v-model="form.audioName" placeholder="如 白噪音-雨声" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择分类" style="width:100%">
            <el-option v-for="c in audioCategories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>

        <!-- 🎵 音频：拖拽/粘贴/选文件 三选一 -->
        <el-form-item label="音频">
          <div
            class="drop-zone"
            :class="{dragging: audioDragging, filled: !!form.audioUrl}"
            @dragover.prevent="audioDragging=true"
            @dragleave="audioDragging=false"
            @drop.prevent="onAudioDrop"
            @paste.prevent="onAudioPaste"
            tabindex="0"
          >
            <div v-if="uploadingAudio" class="dz-loading">⏳ 上传中... {{ audioProgress }}</div>
            <div v-else-if="form.audioUrl" class="dz-filled">
              🎵 {{ currentAudioFileName }}
              <button type="button" class="dz-clear" @click.stop="clearAudio">✕ 移除</button>
            </div>
            <div v-else class="dz-hint">
              📦 拖文件到这里 · Ctrl+V 粘贴 · 或
              <button type="button" class="dz-link" @click="$refs.audioInput.click()">点击选择</button>
              <div class="dz-sub">支持 mp3 / wav / m4a / ogg · 可批量拖入</div>
            </div>
            <input type="file" accept="audio/*" multiple @change="handleAudioPick" ref="audioInput" style="display:none" />
          </div>
          
          <!-- 已有音频网格（可点选） -->
          <div v-if="audioFileList.length > 0" class="file-section">
            <div class="file-section-title">📂 已有音频（点选复用）</div>
            <div class="file-grid">
              <div v-for="f in audioFileList" :key="f.name" @click="selectAudio(f)"
                   class="file-chip" :class="{active: form.audioUrl === f.url}">
                🎵 <span class="fn">{{ f.name }}</span>
              </div>
            </div>
          </div>
        </el-form-item>

        <!-- 🖼️ 封面：拖拽/粘贴/选文件 三选一 -->
        <el-form-item label="封面图片">
          <div
            class="drop-zone cover-dz"
            :class="{dragging: coverDragging, filled: !!form.coverImg}"
            @dragover.prevent="coverDragging=true"
            @dragleave="coverDragging=false"
            @drop.prevent="onCoverDrop"
            @paste.prevent="onCoverPaste"
            tabindex="0"
          >
            <img v-if="coverPreviewUrl" :src="coverPreviewUrl" class="dz-cover-img" />
            <div v-else-if="uploadingCover" class="dz-loading">⏳ 上传中...</div>
            <div v-else class="dz-hint">
              🖼️ 拖图片到这里 · Ctrl+V 粘贴 · 或
              <button type="button" class="dz-link" @click="$refs.coverInput.click()">点击选择</button>
              <div class="dz-sub">支持 jpg / png / webp · 自动适配尺寸</div>
            </div>
            <input type="file" accept="image/*" multiple @change="handleCoverPick" ref="coverInput" style="display:none" />
          </div>
          
          <!-- 已有封面网格（可点选） -->
          <div v-if="coverFileList.length > 0" class="file-section">
            <div class="file-section-title">📂 已有封面（点选复用）</div>
            <div class="cover-grid">
              <div v-for="img in coverFileList" :key="img.name" @click="selectCover(img)"
                   class="cover-chip" :class="{active: form.coverImg === img.url}">
                <img :src="img.url" />
              </div>
            </div>
          </div>
        </el-form-item>

        <!-- 📝 歌词 -->
        <el-form-item label="歌词">
          <el-input v-model="form.lyric" type="textarea" :rows="6" placeholder="请输入歌词（支持LRC格式带时间戳，或纯文本）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="jelly-btn ghost" @click="dialogVisible=false">取消</button>
        <button class="jelly-btn primary" @click="save">💾 保存</button>
      </template>
    </el-dialog>

    <!-- 封面大图预览 -->
    <el-dialog v-model="coverZoom" width="500px" append-to-body show-close>
      <img v-if="coverPreviewUrl" :src="coverPreviewUrl" style="width:100%;border-radius:10px" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import request, { getAudioList, saveAudio, updateAudio, deleteAudio } from '@/api'
import { useBatchSelect } from '@/composables/useBatchSelect'
import { jellyConfirm } from '@/utils/confirm'
import { fmtTime } from '@/utils/exportCsv'

// 音频分类（A1-A5）
const audioCategories = [
  { value: '情绪口述', label: 'A1 · 情绪口述' },
  { value: '正念放松', label: 'A2 · 正念放松' },
  { value: '心理科普', label: 'A3 · 心理科普' },
  { value: '自我复盘', label: 'A4 · 自我复盘' },
  { value: '危机疏导', label: 'A5 · 危机疏导' },
]

const list = ref([])
const keyword = ref('')
const filterCat = ref('')
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 批量勾选
const { selectedIds, isAllSelected, isIndeterminate, toggleSelect, toggleSelectAll, clearSelection } = useBatchSelect(list, a => a.audioId)

// 批量删除
const batchDelete = () => {
  jellyConfirm(`确定删除选中的 ${selectedIds.value.length} 条音频吗？删除后不可恢复！`, { title: '批量删除音频', confirmText: '删除', danger: true })
    .then(async (ok) => {
      if (!ok) return
      let done = 0
      for (const id of selectedIds.value) {
        try {
          const res = await deleteAudio(id)
          if (res.code === 200) done++
        } catch {}
      }
      ElMessage.success(`已删除 ${done}/${selectedIds.value.length} 条音频`)
      clearSelection()
      loadData()
    })
}

const dialogVisible = ref(false)
const form = ref({ audioId: null, audioName: '', audioUrl: '', category: '', coverImg: '', lyric: '' })
const coverFileList = ref([])
const audioFileList = ref([])
const uploadingCover = ref(false)
const uploadingAudio = ref(false)
const coverZoom = ref(false)
const playVisible = ref(false)
const currentAudio = ref(null)

// 🆕 拖拽状态
const audioDragging = ref(false)
const coverDragging = ref(false)
const audioProgress = ref('')

// 🆕 从 URL 里取文件名（给自动填名称用）
const currentAudioFileName = computed(() => {
  if (!form.value.audioUrl) return ''
  const url = form.value.audioUrl
  return url.substring(url.lastIndexOf('/') + 1)
})

// 封面URL处理
const getCoverUrl = (coverImg) => {
  if (!coverImg) return ''
  const v = coverImg.trim()
  if (v.startsWith('http') || v.startsWith('/')) return v
  return '/cover/' + v
}

// 试听音频
const playAudio = (a) => { currentAudio.value = a; playVisible.value = true }

const coverPreviewUrl = computed(() => {
  if (!form.value.coverImg) return ''
  return getCoverUrl(form.value.coverImg)
})

const loadFileList = async (dir) => {
  try {
    const res = await request.get('/upload/list?dir=' + dir)
    if (res.code === 200) {
      if (dir === 'cover') coverFileList.value = res.data || []
      else if (dir === 'audio') audioFileList.value = res.data || []
    }
  } catch (e) { console.warn('加载文件列表失败', e) }
}

const selectAudio = (f) => { form.value.audioUrl = f.url }
const selectCover = (img) => { form.value.coverImg = img.url }

// 🆕 移除已选音频
const clearAudio = () => { form.value.audioUrl = '' }

// ===== 🆕 拖拽 / 粘贴 / 批量上传 =====

// 上传单个文件（通用）
const uploadOne = async (file, dir) => {
  const fd = new FormData(); fd.append('file', file)
  return request.post('/upload/file?dir=' + dir, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// 批量上传文件数组（可用于批量导入，但保存只选最后一个）
const uploadFiles = async (files, dir, label) => {
  if (!files || files.length === 0) return
  const uploadingRef = dir === 'audio' ? uploadingAudio : uploadingCover
  uploadingRef.value = true
  let lastUrl = ''
  for (let i = 0; i < files.length; i++) {
    const f = files[i]
    if (dir === 'audio' && !f.type.startsWith('audio/')) continue
    if (dir === 'cover' && !f.type.startsWith('image/')) continue
    try {
      const res = await uploadOne(f, dir)
      if (res.code === 200) {
        lastUrl = res.data.url
        // 拖入/粘贴时自动填名称（只在名称为空或默认时）
        if (dir === 'audio') {
          const baseName = f.name.replace(/\.[^.]+$/, '') // 去扩展名
          if (!form.value.audioName || form.value.audioName.trim() === '') {
            form.value.audioName = baseName
          }
        }
      }
    } catch (e) { console.warn('[上传] ' + f.name + ' 失败') }
  }
  if (lastUrl) {
    if (dir === 'audio') form.value.audioUrl = lastUrl
    else form.value.coverImg = lastUrl
    ElMessage.success(label + ' 上传成功' + (files.length > 1 ? '（共 ' + files.length + ' 个，最后一个已选中）' : ''))
    loadFileList(dir)
  }
  uploadingRef.value = false
}

// 音频：拖拽
const onAudioDrop = (e) => {
  audioDragging.value = false
  const files = [...e.dataTransfer.files].filter(f => f.type.startsWith('audio/'))
  if (files.length === 0) return ElMessage.warning('请拖音频文件')
  uploadFiles(files, 'audio', '音频')
}
// 音频：粘贴
const onAudioPaste = (e) => {
  const items = e.clipboardData?.items
  const files = []
  if (items) {
    for (const it of items) {
      if (it.kind === 'file' && it.type.startsWith('audio/')) files.push(it.getAsFile())
    }
  }
  if (files.length) uploadFiles(files, 'audio', '音频')
}

// 封面：拖拽
const onCoverDrop = (e) => {
  coverDragging.value = false
  const files = [...e.dataTransfer.files].filter(f => f.type.startsWith('image/'))
  if (files.length === 0) return ElMessage.warning('请拖图片文件')
  uploadFiles(files, 'cover', '封面')
}
// 封面：粘贴（Ctrl+V 粘贴图片）
const onCoverPaste = (e) => {
  const items = e.clipboardData?.items
  const files = []
  if (items) {
    for (const it of items) {
      if (it.kind === 'file' && it.type.startsWith('image/')) files.push(it.getAsFile())
    }
  }
  if (files.length) uploadFiles(files, 'cover', '封面')
}

// 文件选择器（保留兼容）
const handleAudioPick = async (e) => {
  const files = [...e.target.files].filter(f => f.type.startsWith('audio/'))
  if (files.length) uploadFiles(files, 'audio', '音频')
  e.target.value = ''
}
const handleCoverPick = async (e) => {
  const files = [...e.target.files].filter(f => f.type.startsWith('image/'))
  if (files.length) uploadFiles(files, 'cover', '封面')
  e.target.value = ''
}

const openDialog = (row) => {
  if (row) {
    form.value = { audioId: row.audioId, audioName: row.audioName || '', audioUrl: row.audioUrl || '', category: row.category || '', coverImg: row.coverImg || '', lyric: row.lyric || '' }
  } else {
    form.value = { audioId: null, audioName: '', audioUrl: '', category: '', coverImg: '', lyric: '' }
  }
  dialogVisible.value = true
  loadFileList('cover')
  loadFileList('audio')
}

const save = async () => {
  // 表单校验
  if (!form.value.audioName) { ElMessage.warning('请填写音频内容'); return }
  if (!form.value.category) { ElMessage.warning('请选择分类'); return }
  if (!form.value.audioUrl) { ElMessage.warning('请选择或上传音频'); return }
  if (!form.value.coverImg) { ElMessage.warning('请选择或上传封面'); return }
  try {
    const payload = { audioId: form.value.audioId, audioName: form.value.audioName, audioUrl: form.value.audioUrl, category: form.value.category, coverImg: form.value.coverImg, lyric: form.value.lyric }
    const isEdit = !!form.value.audioId
    const res = isEdit ? await updateAudio(payload) : await saveAudio(payload)
    if (res.code === 200) {
      dialogVisible.value = false
      ElMessage.success('保存成功 ✅')
      // 新增后回到第 1 页；编辑则停留在当前页
      if (!isEdit) page.value = 1
      loadData()
    }
    else ElMessage.error(res.msg || '保存失败')
  } catch (e) {
    console.warn('保存失败:', e)
    ElMessage.error('保存失败，请稍后重试')
  }
}

const handleEdit = (a) => openDialog(a)
const handleDelete = (a) => {
  jellyConfirm('确定删除？', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteAudio(a.audioId)
        if (res.code === 200) {
          ElMessage.success('删除成功')
          // 如果当前页只剩一条，删除后跳上一页
          if (list.value.length === 1 && page.value > 1) page.value--
          loadData()
        }
        else ElMessage.error(res.msg || '删除失败')
      } catch (e) {
        console.warn('删除失败:', e)
        ElMessage.error('删除失败，请稍后重试')
      }
    }).catch(() => {})
}
const handleAdd = () => openDialog(null)

const handleSizeChange = () => {
  page.value = 1
  loadData()
}

const handleCurrentChange = () => {
  loadData()
}

const loadData = async () => {
  loading.value = true
  clearSelection() // 翻页/筛选后清掉上一页勾选
  const minLoad = new Promise(r => setTimeout(r, 250))
  try {
    const params = { page: page.value, size: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (filterCat.value) params.category = filterCat.value
    const res = await getAudioList(params)
    if (res.code === 200) {
      const data = res.data
      const records = Array.isArray(data) ? data : (data?.records || [])
      list.value = records.map(a => ({
        audioId: a.audioId || a.id,
        audioName: a.audioName || a.audio_name || '-',
        audioUrl: a.audioUrl || a.audio_url || '',
        category: a.category || '-',
        coverImg: a.coverImg || a.cover_img || a.cover || '',
        lyric: a.lyric || '',
        playCount: a.playCount || a.play_count || 0,
        createTime: a.createTime || a.create_time || '-',
      }))
      total.value = data?.total || records.length
    }
  } catch (e) {
    console.warn('加载音频列表失败:', e)
  } finally {
    await minLoad
    loading.value = false
  }
}

// 搜索防抖
let searchTimer = null

// 组件卸载时清理搜索防抖定时器
onBeforeUnmount(() => clearTimeout(searchTimer))
const onSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadData() }, 300)
}

onMounted(() => { page.value = 1; loadData() })
</script>

<style scoped>
/* ===== 🆕 拖拽上传区 ===== */
.drop-zone {
  width: 100%; min-height: 90px;
  border: 2px dashed #b8d8b0; border-radius: 14px;
  background: #f6fbf4; padding: 16px;
  display: flex; align-items: center; justify-content: center; text-align: center;
  cursor: pointer; transition: all .2s ease; outline: none;
}
.drop-zone:hover { border-color: var(--c-hex); background: color-mix(in srgb, var(--c-main) 12%, var(--c-panel)); }
.drop-zone.dragging { border-color: #5aa050; background: color-mix(in srgb, var(--c-main) 25%, var(--c-panel)); transform: scale(1.01); }
.drop-zone.filled { border-style: solid; border-color: var(--c-hex); background: color-mix(in srgb, var(--c-main) 15%, var(--c-panel)); }

.cover-dz { min-height: 140px; position: relative; overflow: hidden; }

.dz-hint { font-size: 14px; color: #5a8a4a; }
.dz-sub { font-size: 12px; color: #aaa; margin-top: 4px; }
.dz-link { background: none; border: none; color: #3d8a34; text-decoration: underline; cursor: pointer; font-size: 14px; padding: 0; }
.dz-link:hover { color: #2a6a22; }

.dz-loading { color: var(--c-text-weak); font-size: 14px; animation: pulse 1s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity: 1 } 50% { opacity: .5 } }

.dz-filled { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #3d8a34; }
.dz-clear { background: #fff0f0; border: 1px solid #ffb0b0; color: #c44; border-radius: 999px; padding: 2px 10px; font-size: 12px; cursor: pointer; }
.dz-clear:hover { background: #ffe0e0; }

.dz-cover-img { max-width: 100%; max-height: 140px; border-radius: 10px; object-fit: contain; }

/* 🆕 已有文件网格 */
.file-section { margin-top: 12px; }
.file-section-title { font-size: 12px; color: #8a9a8a; margin-bottom: 6px; }

.file-grid {
  max-height: 120px; overflow-y: auto;
  padding: 6px; border: 1px solid #eee; border-radius: 10px; background: #fafafa;
  display: flex; flex-direction: column; gap: 4px;
}
.file-chip {
  padding: 5px 10px; border-radius: 6px; cursor: pointer; font-size: 12px;
  background: var(--c-surface-solid); border: 1px solid transparent; transition: all .15s;
  display: flex; align-items: center; gap: 6px;
}
.file-chip:hover { background: color-mix(in srgb, var(--c-main) 12%, var(--c-panel)); }
.file-chip.active { background: color-mix(in srgb, var(--c-main) 25%, var(--c-panel)); border-color: var(--c-hex); }
.file-chip .fn { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--c-text); }

.cover-grid {
  display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px;
  max-height: 160px; overflow-y: auto;
  padding: 6px; border: 1px solid #eee; border-radius: 10px; background: #fafafa;
}
.cover-chip {
  border: 2px solid transparent; border-radius: 8px; cursor: pointer; padding: 2px; background: var(--c-surface-solid); transition: all .15s;
}
.cover-chip img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 6px; display: block; }
.cover-chip:hover { border-color: #b8d8b0; }
.cover-chip.active { border-color: var(--c-hex); box-shadow: 0 0 0 2px color-mix(in srgb, var(--c-hex) 30%, transparent); }
</style>
