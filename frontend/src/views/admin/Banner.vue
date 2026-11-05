<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/心理视频.png" class="title-icon" /> 首页轮播 / 公告</div>
      <button class="jelly-btn primary" @click="openDialog(null)">＋ 新增轮播</button>
    </div>

    <div class="ext-note info">
      运营位管理：配置首页横幅、暖心文案、活动入口。前台欢迎页<b>游客可见</b>（无需登录）自动读取启用中且在生效时间窗内的条目，按排序号升序展示。
    </div>

    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th>排序</th><th>标题</th><th>副标题</th><th>位置</th><th>跳转</th><th>生效时间</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in list" :key="b.bannerId">
              <td>{{ b.sort }}</td>
              <td><b>{{ b.title }}</b></td>
              <td style="max-width:240px;color:#8a9a8a">{{ b.subtitle || '-' }}</td>
              <td><span class="status-tag">{{ positionName(b.position) }}</span></td>
              <td>{{ linkName(b) }}</td>
              <td style="color:#9aa79a;font-size:12.5px">{{ rangeText(b) }}</td>
              <td><span class="status-tag" :class="b.status === 1 ? 'on' : 'off'">{{ b.status === 1 ? '启用' : '停用' }}</span></td>
              <td>
                <div class="op-btns">
                  <button class="jelly-btn ghost" @click="openDialog(b)">编辑</button>
                  <button class="jelly-btn ghost" @click="toggle(b)">{{ b.status === 1 ? '停用' : '启用' }}</button>
                  <button class="jelly-btn danger" @click="handleDelete(b)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无轮播配置~</div>
      </template>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.bannerId ? '编辑轮播' : '新增轮播'" width="560px">
      <el-form :model="form" label-width="90px" label-position="right">
        <el-form-item label="主标题"><el-input v-model="form.title" placeholder="例如：今天，你感觉怎么样？" /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="form.subtitle" type="textarea" :rows="2" placeholder="一句话文案，可选" /></el-form-item>
        <el-form-item label="背景图">
          <div style="display:flex;gap:10px;align-items:center;width:100%">
            <img v-if="form.coverImg" :src="form.coverImg" style="width:64px;height:40px;object-fit:cover;border-radius:8px" />
            <input type="file" accept="image/*" @change="onUpload" style="flex:1;font-size:12px" />
          </div>
        </el-form-item>
        <el-form-item label="展示位置">
          <el-select v-model="form.position" style="width:100%">
            <el-option label="欢迎页" value="home" />
            <el-option label="首页顶部" value="main" />
          </el-select>
        </el-form-item>
        <el-form-item label="跳转类型">
          <el-select v-model="form.linkType" style="width:100%">
            <el-option label="不跳转" value="none" />
            <el-option label="去写日记" value="diary" />
            <el-option label="去做测评" value="test" />
            <el-option label="心理视频" value="video" />
            <el-option label="治愈音频" value="audio" />
            <el-option label="外部链接" value="url" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.linkType === 'url'" label="链接地址">
          <el-input v-model="form.linkUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
          <span style="margin-left:10px;color:#9aa79a;font-size:12px">越小越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusOn" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="jelly-btn ghost" @click="dialogVisible = false">取消</button>
        <button class="jelly-btn primary" @click="save">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import request, { adminBannerList, adminBannerSave, adminBannerStatus, adminBannerDelete } from '@/api'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return { bannerId: null, title: '', subtitle: '', coverImg: '', position: 'home', linkType: 'none', linkUrl: '', sort: 0, statusOn: true }
}

const positionName = (p) => (p === 'main' ? '首页顶部' : '欢迎页')
const linkName = (b) => ({
  none: '不跳转', diary: '去写日记', test: '去做测评', video: '心理视频', audio: '治愈音频', url: b.linkUrl || '外链',
}[b.linkType] || '不跳转')
const rangeText = (b) => {
  const f = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '')
  if (!b.startTime && !b.endTime) return '长期有效'
  return `${f(b.startTime) || '起'} ~ ${f(b.endTime) || '止'}`
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await adminBannerList()
    if (res?.code !== 200) { ElMessage.error(res?.msg || '轮播列表加载失败'); return }
    list.value = res.data || []
  } finally { loading.value = false }
}

const onUpload = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  fd.append('dir', 'banner')
  try {
    const res = await request.post('/upload/file', fd)
    if (res.code === 200) { form.value.coverImg = res.data.url; ElMessage.success('图片上传成功') }
    else ElMessage.error(res.msg || '上传失败')
  } catch { ElMessage.error('上传失败') }
  e.target.value = ''
}

const openDialog = (row) => {
  form.value = row
    ? { ...row, statusOn: row.status === 1 }
    : emptyForm()
  dialogVisible.value = true
}

const save = async () => {
  if (!form.value.title?.trim()) { ElMessage.warning('请填写主标题'); return }
  const payload = { ...form.value, status: form.value.statusOn ? 1 : 0 }
  delete payload.statusOn
  const res = await adminBannerSave(payload)
  if (res?.code !== 200) { ElMessage.error(res?.msg || '保存失败（只读管理员无此权限）'); return }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadList()
}

const toggle = async (b) => {
  const res = await adminBannerStatus(b.bannerId, b.status === 1 ? 0 : 1)
  if (res?.code !== 200) { ElMessage.error(res?.msg || '操作失败（只读管理员无此权限）'); return }
  ElMessage.success(b.status === 1 ? '已停用' : '已启用')
  loadList()
}

const handleDelete = (b) => {
  jellyConfirm(`确定删除轮播「${b.title}」吗？`, { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      const res = await adminBannerDelete(b.bannerId)
      if (res?.code !== 200) { ElMessage.error(res?.msg || '删除失败（只读管理员无此权限）'); return }
      ElMessage.success('删除成功')
      loadList()
    }).catch(() => {})
}

onMounted(loadList)
</script>
