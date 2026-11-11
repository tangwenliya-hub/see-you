<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/心理视频.png" class="title-icon" /> 心理视频管理（绪见学堂）</div>
      <div style="display:flex; gap:12px; align-items:center">
        <el-select v-model="filterCat" placeholder="全部分类" style="width:160px" clearable @change="loadData(1)">
          <el-option v-for="c in videoCategories" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="keyword" placeholder="搜索视频名称..." @input="onSearch" /></div>
        <button class="jelly-btn primary" @click="handleAdd">＋ 新增视频</button>
      </div>
    </div>
    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
      <!-- 批量操作条 -->
      <div v-if="selectedIds.length > 0" class="batch-bar">
        <span class="batch-info">已选择 <b>{{ selectedIds.length }}</b> 条视频</span>
        <button class="jelly-btn danger" @click="batchDelete"><AppIcon name="trash" /> 批量删除</button>
      </div>
      <table v-if="list.length > 0" class="admin-table">
        <thead><tr>
          <th style="width:50px"><el-checkbox :model-value="isAllSelected" :indeterminate="isIndeterminate" @change="toggleSelectAll" /></th>
          <th>ID</th><th>封面</th><th>视频名称</th><th>分类</th><th>时长</th><th>播放量</th><th>视频</th><th>创建时间</th><th>操作</th>
        </tr></thead>
        <tbody>
          <tr v-for="(v, idx) in list" :key="v.videoId" :class="{ 'row-selected': selectedIds.includes(v.videoId) }">
            <td><el-checkbox :model-value="selectedIds.includes(v.videoId)" @change="toggleSelect(v.videoId)" /></td>
            <td>{{ v.videoId }}</td>
            <td>
              <img :src="getCoverUrl(v.coverImg)"
                   style="width:100px;height:56px;border-radius:10px;object-fit:cover;background:#eee" />
            </td>
            <td>{{ v.videoName || '-' }}</td>
            <td>{{ v.category || '-' }}</td>
            <td>{{ secondsToDuration(v.duration) || '-' }}</td>
            <td>{{ v.playCount || 0 }}</td>
            <td>
                <button v-if="v.videoUrl" class="jelly-btn ghost" style="padding:4px 14px;font-size:13px" @click="playVideo(v)"><AppIcon name="play" /> 播放</button>
                <span v-else style="color:#ccc;font-size:12px">-</span>
              </td>
              <td>{{ v.createTime ? fmtTime(v.createTime).slice(0, 16) : '-' }}</td>
            <td>
              <div class="op-btns">
                <button class="jelly-btn op-main" @click="handleEdit(v)">编辑</button>
                <button class="jelly-btn danger" @click="handleDelete(v)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="table-empty">暂无视频数据，点击"新增视频"添加~</div>
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

      <!-- 播放弹窗 -->
      <el-dialog v-model="playVisible" title="视频播放" width="720px" append-to-body>
        <video v-if="currentVideoUrl" :src="currentVideoUrl" controls style="width:100%;border-radius:10px"></video>
        <template #footer><button class="jelly-btn ghost" @click="playVisible=false">关闭</button></template>
      </el-dialog>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.videoId?'编辑视频':'新增视频'" width="640px">
      <el-form :model="form" label-width="100px" label-position="right">
        <el-form-item label="视频名称"><el-input v-model="form.videoName" placeholder="如 5分钟呼吸冥想" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择分类" style="width:100%">
            <el-option v-for="c in videoCategories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>

        <!-- 视频：网格选 + 上传 + 预览 -->
        <el-form-item label="视频">
          <div style="font-size:12px;color:#8a9a8a;margin-bottom:4px"><AppIcon name="folder-open" /> 已有视频（点击选中）：</div>
          <div v-if="videoList.length > 0" style="max-height:100px;overflow-y:auto;padding:6px;border:1px solid #eee;border-radius:8px;margin-bottom:6px">
            <div v-for="v in videoList" :key="v.name" @click="selectVideo(v)"
                 :style="{padding:'4px 8px',cursor:'pointer',borderRadius:'4px',marginBottom:'3px',fontSize:'12px',background: form.videoUrl === v.url ? '#e6f3e6' : 'transparent',border: form.videoUrl === v.url ? '1px solid #6a9a6a' : '1px solid transparent'}">
              <AppIcon name="video" /> {{ v.name }}
            </div>
          </div>
          <div v-else style="font-size:12px;color:#ccc;margin-bottom:6px">暂无视频文件，请先上传</div>
          <div style="display:flex;gap:8px;align-items:center">
            <input type="file" accept="video/*" @change="handleVideoPick" ref="videoInput" style="display:none" />
            <button type="button" class="jelly-btn ghost" @click="$refs.videoInput.click()" :disabled="uploadingVideo">
              {{ uploadingVideo ? '上传中...' : ' 上传新视频' }}
            </button>
          </div>
        </el-form-item>

        <!-- 视频预览（紧跟在视频选择下面） -->
        <el-form-item v-if="form.videoUrl" label="视频预览">
          <div style="border-radius:12px;overflow:hidden;border:2px solid #6a9a6a;background:#000">
            <video :src="form.videoUrl" controls style="width:100%;max-height:260px;display:block"></video>
          </div>
        </el-form-item>

        <!-- 视频时长 -->
        <el-form-item label="视频时长">
          <el-input v-model="form.duration" placeholder="如 05:30" />
        </el-form-item>

        <!-- 视频简介 -->
        <el-form-item label="视频简介">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入视频简介..." />
        </el-form-item>

        <!-- 封面图片：网格选 + 上传 -->
        <el-form-item label="封面图片">
          <div v-if="coverPreviewUrl" @click="coverZoom = true" style="width:100%;height:100px;border-radius:10px;background:#f5f5f5;display:flex;align-items:center;justify-content:center;margin-bottom:8px;border:2px dashed #6a9a6a;cursor:zoom-in;transition:.2s" onmouseover="this.style.borderStyle='solid'" onmouseout="this.style.borderStyle='dashed'">
            <img :src="coverPreviewUrl" style="max-width:100%;max-height:100%;border-radius:8px" />
          </div>
          <div v-else style="width:100%;height:80px;border-radius:10px;background:#f8f8f8;border:2px dashed #ddd;display:flex;align-items:center;justify-content:center;color:#aaa;font-size:13px;margin-bottom:8px">未选择封面</div>
          <div style="font-size:12px;color:#8a9a8a;margin-bottom:4px"><AppIcon name="folder-open" /> 已有封面（点击选中）：</div>
          <div v-if="coverList.length > 0" style="display:grid;grid-template-columns:repeat(4,1fr);gap:6px;max-height:150px;overflow-y:auto;padding:4px;border:1px solid #eee;border-radius:8px;margin-bottom:6px">
            <div v-for="img in coverList" :key="img.name" @click="selectCover(img)"
                 :style="{border: form.coverImg === img.url ? '2px solid #6a9a6a' : '2px solid transparent', borderRadius:'6px', cursor:'pointer', padding:'2px'}">
              <img :src="img.url" style="width:100%;aspect-ratio:1;object-fit:cover;border-radius:4px" />
            </div>
          </div>
          <div style="display:flex;gap:8px;align-items:center">
            <input type="file" accept="image/*" @change="handleCoverPick" ref="coverInput" style="display:none" />
            <button type="button" class="jelly-btn ghost" @click="$refs.coverInput.click()" :disabled="uploadingCover">
              {{ uploadingCover ? '上传中...' : ' 上传新封面' }}
            </button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="jelly-btn ghost" @click="dialogVisible=false">取消</button>
        <button class="jelly-btn primary" @click="save"><AppIcon name="save" /> 保存</button>
      </template>
    </el-dialog>

    <!-- 封面大图预览 -->
    <el-dialog v-model="coverZoom" width="600px" append-to-body show-close>
      <img v-if="coverPreviewUrl" :src="coverPreviewUrl" style="width:100%;border-radius:10px" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import request, { getVideoList, saveVideo, updateVideo, deleteVideo } from '@/api'
import { useBatchSelect } from '@/composables/useBatchSelect'
import { jellyConfirm } from '@/utils/confirm'
import { fmtTime } from '@/utils/exportCsv'

// 视频分类（V1-V5）
const videoCategories = [
  { value: '情绪记录', label: 'V1 · 情绪记录' },
  { value: '调节教程', label: 'V2 · 调节教程' },
  { value: '心理科普', label: 'V3 · 心理科普' },
  { value: '治愈影像', label: 'V4 · 治愈影像' },
  { value: '援助指引', label: 'V5 · 援助指引' },
]

const list = ref([])
const keyword = ref('')
const filterCat = ref('')
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)

// 批量勾选
const { selectedIds, isAllSelected, isIndeterminate, toggleSelect, toggleSelectAll, clearSelection } = useBatchSelect(list, v => v.videoId)

// 批量删除
const batchDelete = () => {
  jellyConfirm(`确定删除选中的 ${selectedIds.value.length} 条视频吗？删除后不可恢复！`, { title: '批量删除视频', confirmText: '删除', danger: true })
    .then(async (ok) => {
      if (!ok) return
      let done = 0
      for (const id of selectedIds.value) {
        try {
          const res = await deleteVideo(id)
          if (res.code === 200) done++
        } catch {}
      }
      ElMessage.success(`已删除 ${done}/${selectedIds.value.length} 条视频`)
      clearSelection()
      loadData()
    })
}
const form = ref({ videoId: null, videoName: '', videoUrl: '', category: '', coverImg: '', description: '', duration: '' })
const coverList = ref([])
const videoList = ref([])
const uploadingCover = ref(false)
const uploadingVideo = ref(false)
const playVisible = ref(false)
const currentVideoUrl = ref('')
const coverZoom = ref(false)  // 封面大图预览

// 封面URL处理：有值就用，没值显示默认占位图
const getCoverUrl = (coverImg) => {
  if (!coverImg) return 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjU2IiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjxyZWN0IHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiIGZpbGw9IiNlZWUiLz48dGV4dCB4PSI1MCUiIHk9IjUwJSIgZm9udC1zaXplPSIxMiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZmlsbD0iI2FiYiI+暂无封面PC90ZXh0Pjwvc3ZnPg=='
  const v = coverImg.trim()
  if (v.startsWith('http') || v.startsWith('/')) return v
  return '/cover/' + v
}

// 播放视频
const playVideo = (v) => {
  currentVideoUrl.value = v.videoUrl
  playVisible.value = true
}

const coverPreviewUrl = computed(() => {
  if (!form.value.coverImg) return ''
  const v = form.value.coverImg.trim()
  if (v.startsWith('http') || v.startsWith('/')) return v
  return '/cover/' + v
})

// 扫描已有文件
const loadFileList = async (dir) => {
  try {
    const res = await request.get('/upload/list?dir=' + dir)
    if (res.code === 200) {
      if (dir === 'cover') coverList.value = res.data || []
      else if (dir === 'video') videoList.value = res.data || []
    }
  } catch (e) { console.warn('加载文件列表失败', e) }
}

const selectVideo = (v) => {
  form.value.videoUrl = v.url
  // 如果时长为空，自动从视频URL读取
  if (!form.value.duration) {
    extractDurationFromUrl(v.url).then(d => {
      if (d) form.value.duration = d
    })
  }
}
const selectCover = (img) => { form.value.coverImg = img.url }

// ===== 时长换算：数据库 duration 是 int 秒，界面用 mm:ss 展示/输入 =====
// "mm:ss" / "hh:mm:ss" / 纯数字(秒)  秒；解析失败返回 0
const durationToSeconds = (val) => {
  if (val === null || val === undefined || val === '') return 0
  const s = String(val).trim()
  if (/^\d+$/.test(s)) return Math.max(0, parseInt(s, 10))
  const parts = s.split(':').map(p => parseInt(p, 10))
  if (parts.some(p => !isFinite(p))) return 0
  return parts.length === 3
    ? parts[0] * 3600 + parts[1] * 60 + parts[2]
    : parts[0] * 60 + parts[1]
}
// 秒  "mm:ss"（展示用）
const secondsToDuration = (sec) => {
  const n = parseInt(sec, 10)
  if (!isFinite(n) || n <= 0) return ''
  const m = Math.floor(n / 60), s = n % 60
  return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s
}

// 从视频文件自动提取时长（mm:ss 格式）
const extractDurationFromFile = (file) => {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      const dur = video.duration
      URL.revokeObjectURL(url)
      if (isFinite(dur) && dur > 0) {
        const m = Math.floor(dur / 60)
        const s = Math.floor(dur % 60)
        resolve((m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s)
      } else {
        resolve('')
      }
    }
    video.onerror = () => { URL.revokeObjectURL(url); resolve('') }
    video.src = url
  })
}

// 从视频URL自动提取时长
const extractDurationFromUrl = (url) => {
  return new Promise((resolve) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.crossOrigin = 'anonymous'
    video.onloadedmetadata = () => {
      const dur = video.duration
      if (isFinite(dur) && dur > 0) {
        const m = Math.floor(dur / 60)
        const s = Math.floor(dur % 60)
        resolve((m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s)
      } else {
        resolve('')
      }
    }
    video.onerror = () => { resolve('') }
    video.src = url
  })
}

const handleVideoPick = async (e) => {
  const file = e.target.files[0]; if (!file) return
  uploadingVideo.value = true
  // 上传前先提取时长（本地读取更快）
  let autoDuration = ''
  try {
    autoDuration = await extractDurationFromFile(file)
  } catch (e) { /* 忽略，继续上传 */ }
  try {
    const fd = new FormData(); fd.append('file', file)
    const res = await request.post('/upload/file?dir=video', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.code === 200) {
      form.value.videoUrl = res.data.url
      // 如果用户没填时长，自动填入
      if (!form.value.duration && autoDuration) {
        form.value.duration = autoDuration
        ElMessage.success('视频上传成功，已自动提取时长')
      } else {
        ElMessage.success('视频上传成功')
      }
      loadFileList('video')
    }
    else ElMessage.error(res.msg || '上传失败')
  } catch (err) { ElMessage.error('上传出错：' + (err.message || '未知错误')) }
  finally { uploadingVideo.value = false; e.target.value = '' }
}

const handleCoverPick = async (e) => {
  const file = e.target.files[0]; if (!file) return
  uploadingCover.value = true
  try {
    const fd = new FormData(); fd.append('file', file)
    const res = await request.post('/upload/file?dir=cover', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.code === 200) { form.value.coverImg = res.data.url; ElMessage.success('封面上传成功'); loadFileList('cover') }
    else ElMessage.error(res.msg || '上传失败')
  } catch (err) { ElMessage.error('上传出错：' + (err.message || '未知错误')) }
  finally { uploadingCover.value = false; e.target.value = '' }
}

const openDialog = (row) => {
  if (row) {
    form.value = { videoId: row.videoId, videoName: row.videoName || '', videoUrl: row.videoUrl || '', category: row.category || '', coverImg: row.coverImg || '', description: row.description || '', duration: secondsToDuration(row.duration) }
  } else {
    form.value = { videoId: null, videoName: '', videoUrl: '', category: '', coverImg: '', description: '', duration: '' }
  }
  dialogVisible.value = true
  loadFileList('cover')
  loadFileList('video')
}

const save = async () => {
  if (!form.value.videoName) { ElMessage.warning('请填写视频名称'); return }
  if (!form.value.category) { ElMessage.warning('请选择分类'); return }
  if (!form.value.videoUrl) { ElMessage.warning('请选择或上传视频'); return }
  if (!form.value.coverImg) { ElMessage.warning('请选择或上传封面'); return }
  try {
    const payload = {
      videoId: form.value.videoId,
      videoName: form.value.videoName,
      videoUrl: form.value.videoUrl,
      category: form.value.category,
      coverImg: form.value.coverImg,
      description: form.value.description,
      // 数据库存秒数（int），界面输入的 "mm:ss" 在这里统一换算，避免 Data truncated 报错
      duration: durationToSeconds(form.value.duration)
    }
    const res = form.value.videoId ? await updateVideo(payload) : await saveVideo(payload)
    if (res.code === 200) {
      dialogVisible.value = false
      ElMessage.success('保存成功')
      loadData(form.value.videoId ? page.value : 1) // 编辑留在当前页，新增跳第1页
    }
    else ElMessage.error(res.msg || '保存失败')
  } catch (e) {
    console.warn('保存失败:', e)
    ElMessage.error('保存失败，请稍后重试')
  }
}

const handleEdit = (v) => openDialog(v)
const handleDelete = (v) => {
  jellyConfirm('确定删除？', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteVideo(v.videoId)
        if (res.code === 200) {
          ElMessage.success('删除成功')
          // 如果当前页只剩一条，删除后跳上一页
          const nextPage = (list.value.length === 1 && page.value > 1) ? page.value - 1 : page.value
          loadData(nextPage)
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
  loadData(1)
}
const handleCurrentChange = (val) => {
  loadData(val)
}

const loadData = async (p) => {
  if (typeof p === 'number') page.value = p
  loading.value = true
  clearSelection() // 翻页/筛选后清掉上一页勾选
  try {
    const params = { page: page.value, size: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (filterCat.value) params.category = filterCat.value
    const res = await getVideoList(params)
    if (res.code === 200) {
      const data = res.data
      const records = Array.isArray(data) ? data : (data?.records || [])
      list.value = records.map(v => ({
        videoId: v.videoId || v.id,
        videoName: v.videoName || v.video_name || '-',
        videoUrl: v.videoUrl || v.video_url || '',
        category: v.category || '-',
        coverImg: v.coverImg || v.cover_img || v.cover || '',
        description: v.description || '',
        duration: v.duration || '',
        playCount: v.playCount || v.play_count || 0,
        createTime: v.createTime || v.create_time || '-',
      }))
      total.value = data?.total || records.length
    }
  } catch (e) {
    console.warn('加载视频列表失败:', e)
  } finally {
    loading.value = false
  }
}

// 搜索防抖
let searchTimer = null

// 组件卸载时清理搜索防抖定时器
onBeforeUnmount(() => clearTimeout(searchTimer))
const onSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { loadData(1) }, 300)
}

onMounted(() => loadData(1))
</script>