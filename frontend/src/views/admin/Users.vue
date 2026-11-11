<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/用户管理.png" class="title-icon" /> 用户管理</div>
      <div style="display:flex; gap:12px; align-items:center">
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="keyword" placeholder="搜索用户名/邮箱..." @input="onSearchInput" /></div>
        <button class="jelly-btn ghost" @click="openImportDialog"><AppIcon name="inbox" /> 批量导入</button>
        <button class="jelly-btn primary" @click="openDialog(null)">＋ 新增用户</button>
      </div>
    </div>

    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <!-- 批量操作条 -->
        <div v-if="selectedIds.length > 0" class="batch-bar">
          <span class="batch-info">已选择 <b>{{ selectedIds.length }}</b> 个用户</span>
          <button class="jelly-btn ghost" @click="batchToggle(0)"><AppIcon name="ban" /> 批量禁用</button>
          <button class="jelly-btn ghost" @click="batchToggle(1)"><AppIcon name="check" /> 批量启用</button>
          <button class="jelly-btn danger" style="background:#ff6b6b" @click="batchDelete"><AppIcon name="trash" /> 批量删除</button>
        </div>
        <table v-if="list.length > 0" class="admin-table">
        <thead>
          <tr>
            <th style="width:50px">
              <el-checkbox :model-value="isAllSelected" :indeterminate="isIndeterminate" @change="toggleSelectAll" />
            </th>
            <th>ID</th><th>头像</th><th>用户</th><th>邮箱</th><th>角色</th>
            <th>注册时间</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(u, idx) in list" :key="u.userId" :class="{ 'row-selected': selectedIds.includes(u.userId) }">
            <td><el-checkbox :model-value="selectedIds.includes(u.userId)" @change="toggleSelect(u.userId)" /></td>
            <td>{{ u.userId }}</td>
            <td>
              <div class="avatar" style="width:32px;height:32px;font-size:16px;display:flex;align-items:center;justify-content:center">
                <img v-if="u.avatar && (u.avatar.startsWith('/') || u.avatar.startsWith('http'))" :src="u.avatar" @error="onAvatarError($event, u)" style="width:32px;height:32px;border-radius:50%;object-fit:cover" />
                <span v-else>{{ u.avatar || '' }}</span>
              </div>
            </td>
            <td>
              <div class="user-cell">
                <span class="uc-name">{{ u.nickname || u.username }}</span>
                <span v-if="u.nickname" class="uc-uname">@{{ u.username }}</span>
              </div>
            </td>
            <td style="color:#8a9a8a">{{ u.email }}</td>
            <td><span :class="'status-tag ' + roleTag(u.role)">{{ roleName(u.role) }}</span></td>
            <td style="color:#8a9a8a">{{ fmtTime(u.createdAt).slice(0, 16) }}</td>
            <td><span :class="'status-tag ' + (u.active?'on':'off')">{{ u.active?'正常':'禁用' }}</span></td>
            <td>
              <div class="op-btns">
                <button v-if="isCustomAvatar(u.avatar)" class="jelly-btn op-main" @click="goAvatarAudit(u)">头像</button>
                <button class="jelly-btn op-main" @click="openProfile(u)">画像</button>
                <button class="jelly-btn op-sub" @click="openDialog(u)">编辑</button>
                <button class="jelly-btn danger" @click="toggleActive(u)">{{ u.active?'禁用':'启用' }}</button>
                <button class="jelly-btn danger" style="background:#ff6b6b" @click="handleDelete(u)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="table-empty">暂无用户数据~</div>
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
    </div>

    <el-dialog v-model="dialogVisible" :title="form.userId?'编辑用户':'新增用户'" width="480px">
      <el-form :model="form" label-width="80px" label-position="right">
        <!-- ===== 账号信息：管理员真正该管的东西 ===== -->
        <div class="dlg-sec">账号信息</div>
        <el-form-item v-if="!form.userId" label="用户名"><el-input v-model="form.username" /></el-form-item>
        <div v-else class="uname-static">登录账号：<b>{{ form.username }}</b>（不可修改）</div>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="昵称"><el-input v-model="form.nickname" placeholder="可选，留空则显示用户名" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item></el-col>
        </el-row>
        <el-form-item v-if="!form.userId" label="密码"><el-input v-model="form.password" type="password" placeholder="新增必填（至少6位）" show-password /></el-form-item>
        <el-form-item v-else label="新密码"><el-input v-model="form.password" type="password" placeholder="留空则不改；填写即重置该用户密码（至少6位）" show-password /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="14">
            <el-form-item label="角色">
              <el-select v-model="form.role" style="width:100%">
                <el-option label="普通用户" value="user" />
                <el-option label="管理员（可读写）" value="admin" />
                <el-option label="只读管理员（只能看）" value="viewer" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="状态">
              <el-switch v-model="form.active" />
              <span style="margin-left:8px;color:#8a9a8a;font-size:13px">{{ form.active ? '正常' : '禁用' }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 头像不在本弹窗修改：如需重置违规头像，请到「头像审核」页面 -->
        <div class="dlg-tip">头像如需重置，请前往「头像审核」页面处理</div>
      </el-form>
      <template #footer>
        <button class="jelly-btn ghost" @click="dialogVisible=false">取消</button>
        <button class="jelly-btn primary" @click="save">保存</button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="批量导入用户" width="520px" :close-on-click-modal="false">
      <div class="import-area">
        <div class="import-tip">
          <p><AppIcon name="pin" /> 导入说明：</p>
          <ul>
            <li>支持 .xlsx 格式的 Excel 文件</li>
            <li>用户名和密码为必填项</li>
            <li>角色填写"管理员"或"普通用户"</li>
            <li>用户名不能重复</li>
          </ul>
          <button class="jelly-btn ghost tiny" @click="downloadTemplate"><AppIcon name="file-text" /> 下载导入模板</button>
        </div>

        <!-- 拖拽上传区 -->
        <div class="upload-zone" 
             :class="{ 'drag-over': importDragOver, 'has-file': importFile }"
             @dragover.prevent="importDragOver = true"
             @dragleave="importDragOver = false"
             @drop.prevent="onFileDrop">
          <input type="file" ref="importFileInput" accept=".xlsx,.xls" @change="onFileSelect" hidden />
          <div v-if="!importFile" class="upload-placeholder" @click="$refs.importFileInput.click()">
            <div class="upload-icon"><AppIcon name="folder" /></div>
            <div class="upload-text">点击或拖拽 Excel 文件到此处</div>
            <div class="upload-hint">支持 .xlsx 格式</div>
          </div>
          <div v-else class="file-info">
            <span class="file-name"><AppIcon name="file-text" /> {{ importFile.name }}</span>
            <span class="file-size">{{ formatFileSize(importFile.size) }}</span>
            <button class="jelly-btn tiny danger" @click.stop="clearImportFile">移除</button>
          </div>
        </div>

        <!-- 导入结果 -->
        <div v-if="importResult" class="import-result">
          <div class="result-summary">
            <div class="result-item success">
              <span class="result-num">{{ importResult.success }}</span>
              <span class="result-label">成功</span>
            </div>
            <div class="result-item fail">
              <span class="result-num">{{ importResult.fail }}</span>
              <span class="result-label">失败</span>
            </div>
            <div class="result-item total">
              <span class="result-num">{{ importResult.total }}</span>
              <span class="result-label">总计</span>
            </div>
          </div>
          <div v-if="importResult.errors && importResult.errors.length > 0" class="error-list">
            <div class="error-title">失败详情：</div>
            <ul>
              <li v-for="(err, idx) in importResult.errors" :key="idx">{{ err }}</li>
            </ul>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="jelly-btn ghost" @click="closeImportDialog">关闭</button>
        <button class="jelly-btn primary" :disabled="!importFile || importing" @click="doImport">
          {{ importing ? '导入中...' : '开始导入' }}
        </button>
      </template>
    </el-dialog>

    <!-- 用户 360 画像抽屉：六维汇总，不含日记正文 -->
    <el-drawer v-model="profileVisible" :title="profile?.user ? '用户画像 · ' + (profile.user.nickname || profile.user.username) : '用户画像'" size="46%" :destroy-on-close="true">
      <div v-if="profileLoading" class="table-loading">加载中...</div>
      <div v-else-if="profile" class="pf">
        <div class="pf-head">
          <img :src="profile.user.avatar || '/img/1 (1).png'" class="pf-avatar" @error="e => e.target.src='/img/1 (1).png'" />
          <div class="pf-meta">
            <div class="pf-name">{{ profile.user.nickname || profile.user.username }}</div>
            <div class="pf-sub">@{{ profile.user.username }} · {{ profile.user.roleName }} · ID {{ profile.user.userId }}</div>
            <div class="pf-sub">注册于 {{ fmtDate(profile.user.createTime) }}{{ profile.user.bio ? ' · ' + profile.user.bio : '' }}</div>
          </div>
        </div>

        <div class="pf-grid">
          <div class="pf-card">
            <div class="pf-t">情绪日记</div>
            <div class="pf-big">{{ profile.diary.total }} <span>篇</span></div>
            <div class="pf-line">手动 {{ profile.diary.manual }} · 推测入库 {{ profile.diary.inferred }}</div>
            <div class="pf-line">最近记录 {{ profile.diary.lastDate }} · 平均指数 {{ profile.diary.avgIndex }}</div>
            <div class="pf-bars">
              <div v-for="e in profile.diary.emotionDist" :key="e.emotion" class="pf-bar-row">
                <span class="pf-bar-l">{{ e.name }}</span>
                <div class="pf-bar"><i :style="{ width: barWidth(profile.diary.emotionDist, e.value) }"></i></div>
                <span class="pf-bar-n">{{ e.value }}</span>
              </div>
              <div v-if="!profile.diary.emotionDist?.length" class="pf-empty">暂无情绪数据</div>
            </div>
          </div>

          <div class="pf-card">
            <div class="pf-t">心理测评</div>
            <div class="pf-big">{{ profile.test.total }} <span>次</span></div>
            <template v-if="profile.test.last">
              <div class="pf-line">最近：{{ profile.test.last.paperName }}</div>
              <div class="pf-line">得分 {{ profile.test.last.score }} · {{ profile.test.last.level || '-' }}</div>
              <div class="pf-line">日期 {{ profile.test.last.date }}</div>
            </template>
            <div v-else class="pf-line">暂无测评记录</div>
          </div>

          <div class="pf-card">
            <div class="pf-t">情绪预警</div>
            <div class="pf-big" :class="{ 'is-red': profile.alert.maxLevel >= 2 }">{{ profile.alert.total }} <span>条</span></div>
            <div class="pf-line">最高等级 {{ levelName(profile.alert.maxLevel) }} · 未读 {{ profile.alert.unread }}</div>
            <div v-if="profile.alert.last" class="pf-line">
              最近 {{ profile.alert.last.date }} · 低落 {{ profile.alert.last.maxDowncastDays }} 天 · 指数 {{ profile.alert.last.avgIndex }}
            </div>
          </div>

          <div class="pf-card">
            <div class="pf-t">无感记录</div>
            <div class="pf-big">{{ profile.sense.enabled === 1 ? '已开启' : '未开启' }}</div>
            <div class="pf-line">行为采集 {{ yn(profile.sense.behavior) }} · 文本分析 {{ yn(profile.sense.text) }}</div>
            <div class="pf-line">推测量 {{ profile.sense.predictions }} · 已采纳 {{ profile.sense.adopted }}</div>
            <div class="pf-line" v-if="profile.sense.grantAt">授权于 {{ fmtDate(profile.sense.grantAt) }}</div>
          </div>

          <div class="pf-card">
            <div class="pf-t">账号安全</div>
            <div class="pf-big">{{ profile.security.activeSession }} <span>个在线</span></div>
            <div class="pf-line">累计会话 {{ profile.security.sessionTotal }} · 登录失败 {{ profile.security.loginFail }} 次</div>
            <div class="pf-line">最近登录 {{ fmtTime(profile.security.lastLogin) }}</div>
          </div>

          <div class="pf-card">
            <div class="pf-t">互动行为</div>
            <div class="pf-big">{{ profile.interaction.likes }} <span>赞</span></div>
            <div class="pf-line">收藏 {{ profile.interaction.collects }} · 播放记录 {{ profile.interaction.history }}</div>
          </div>
        </div>

        <div class="pf-note">{{ profile.privacyNote }}</div>
      </div>
      <div v-else class="table-empty">暂无数据~</div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request, { getUserList, updateUserStatus, deleteUser, downloadUserTemplate, importUsers, adminUserProfile } from '@/api'
import { fmtTime, fmtDate } from '@/utils/exportCsv'
import { useBatchSelect } from '@/composables/useBatchSelect'
import { jellyConfirm } from '@/utils/confirm'

// 角色展示
const roleName = (r) => ({ admin: '管理员', viewer: '只读管理员', user: '普通用户' }[r] || '普通用户')
const roleTag = (r) => ({ admin: 'warn', viewer: 'on', user: '' }[r] || '')

// ===== 用户 360 画像 =====
const profileVisible = ref(false)
const profileLoading = ref(false)
const profile = ref(null)
const levelName = (v) => ({ 1: '轻度关注', 2: '中度提醒', 3: '高度关注' }[v] || (v ? '未知' : '无'))
const yn = (v) => (v === 1 ? '开' : '关')
const barWidth = (dist, v) => {
  const max = Math.max(...(dist || []).map(i => i.value), 1)
  return Math.round((v / max) * 100) + '%'
}
const openProfile = async (u) => {
  profile.value = null
  profileVisible.value = true
  profileLoading.value = true
  try {
    const res = await adminUserProfile(u.userId)
    if (res?.code !== 200) { ElMessage.error(res?.msg || '画像加载失败'); return }
    profile.value = res.data
  } catch (e) {
    ElMessage.error('画像加载失败')
  } finally {
    profileLoading.value = false
  }
}

// 头像加载失败处理
const onAvatarError = (e, u) => {
  e.target.src = '/img/1 (1).png'
  u.avatar = '/img/1 (1).png'
}

const keyword = ref('')
const dialogVisible = ref(false)
const form = ref({ userId: null, username: '', nickname: '', password: '', email: '', role: 'user', active: true, avatar: '', createdAt: '', theme: 'green', darkMode: false })

const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 批量勾选
const { selectedIds, isAllSelected, isIndeterminate, toggleSelect, toggleSelectAll, clearSelection } = useBatchSelect(list, u => u.userId)

const loadData = async (p) => {
  if (typeof p === 'number') page.value = p
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await getUserList(params)
    if (res.code === 200) {
      const data = res.data
      const records = Array.isArray(data) ? data : (data?.records || [])
      total.value = data?.total || records.length
      list.value = records.map(u => ({
        userId: u.userId || u.id,
        username: u.username || u.name || '-',
        nickname: u.nickname || '',
        email: u.email || '-',
        role: u.role === 1 ? 'admin' : (u.role === 2 ? 'viewer' : 'user'),
        active: u.status !== 0 && u.status !== 'disabled',
        avatar: u.avatar || '/img/1 (1).png',
        createdAt: u.createTime || u.createdAt || '-',
        theme: u.theme || 'green',
        darkMode: u.darkMode === 1 || u.darkMode === true,
      }))
    }
  } catch (e) {
    console.warn('加载用户列表失败:', e)
  } finally {
    loading.value = false
  }
}

// 搜索防抖
let searchTimer = null

// 组件卸载时清理搜索防抖定时器
onBeforeUnmount(() => clearTimeout(searchTimer))
const onSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; clearSelection(); loadData(1) }, 300)
}

const handleSizeChange = () => {
  page.value = 1
  clearSelection()
  loadData(1)
}
const handleCurrentChange = (val) => {
  clearSelection()
  loadData(val)
}

// 批量禁用 / 启用（逐个调单条接口，毕设数据量下可接受）
const batchToggle = async (statusVal) => {
  const label = statusVal === 1 ? '启用' : '禁用'
  if (!(await jellyConfirm(`确定${label}选中的 ${selectedIds.value.length} 个用户吗？`, { title: `批量${label}`, confirmText: label, cancelText: '取消' }))) return
  let ok = 0
  for (const id of selectedIds.value) {
    try {
      const res = await updateUserStatus({ userId: id, status: statusVal })
      if (res.code === 200) ok++
    } catch {}
  }
  ElMessage.success(`已${label} ${ok}/${selectedIds.value.length} 个用户`)
  clearSelection()
  loadData()
}

// 批量删除
const batchDelete = () => {
  jellyConfirm(`确定删除选中的 ${selectedIds.value.length} 个用户吗？此操作不可恢复！`, { title: '批量删除', confirmText: '删除', danger: true })
    .then(async (ok) => {
      if (!ok) return
      let done = 0
      for (const id of selectedIds.value) {
        try {
          const res = await deleteUser(id)
          if (res.code === 200) done++
        } catch {}
      }
      ElMessage.success(`已删除 ${done}/${selectedIds.value.length} 个用户`)
      clearSelection()
      loadData()
    })
}

onMounted(() => loadData(1))

const router = useRouter()

// 是否为用户自行上传的头像（系统内置头像统一在 /img/ 下）
const isCustomAvatar = (a) => !!a && !a.startsWith('/img/')
// 跳到头像审核页并带上该用户名作为搜索词
const goAvatarAudit = (u) => router.push({ path: '/admin/avatars', query: { keyword: u.username } })

const openDialog = (row) => {
  if (row) {
    form.value = { ...row }
  } else {
    form.value = { userId: null, username: '', nickname: '', password: '', email: '', role: 'user', active: true, avatar: '/img/1 (1).png', createdAt: new Date().toISOString().slice(0,10), theme: 'green', darkMode: false }
  }
  dialogVisible.value = true
}

const toggleActive = async (u) => {
  const oldActive = u.active
  const newStatus = u.active ? 0 : 1
  try {
    const res = await updateUserStatus({ userId: u.userId, status: newStatus })
    if (res.code === 200) {
      u.active = !u.active
      ElMessage.success(u.active ? '已启用' : '已禁用')
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (e) {
    u.active = oldActive
    ElMessage.error('操作失败：' + (e.message || '未知错误'))
  }
}

const handleDelete = (u) => {
  jellyConfirm(`确定删除用户 "${u.username}" 吗？此操作不可恢复！`, { title: '删除用户', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteUser(u.userId)
        if (res.code === 200) {
          ElMessage.success('删除成功')
          // 如果当前页只剩一条，删除后跳上一页
          const nextPage = (list.value.length === 1 && page.value > 1) ? page.value - 1 : page.value
          loadData(nextPage)
        } else {
          ElMessage.error(res.msg || '删除失败')
        }
      } catch (e) {
        ElMessage.error('删除失败：' + (e.message || '未知错误'))
      }
    }).catch(() => {})
}

const save = async () => {
  if (!form.value.username || !form.value.email) { ElMessage.warning('请填写完整信息'); return }
  if (form.value.password && form.value.password.length < 6) { ElMessage.warning('新密码至少 6 位'); return }
  // 新增时密码必填（管理端专用接口 /user/add 要求），编辑时留空表示不修改密码
  if (!form.value.userId && !form.value.password) { ElMessage.warning('请设置初始密码（至少 6 位）'); return }
  try {
    const payload = {
      userId: form.value.userId,
      username: form.value.username,
      nickname: form.value.nickname,
      email: form.value.email,
      role: form.value.role === 'admin' ? 1 : (form.value.role === 'viewer' ? 2 : 0),
      status: form.value.active ? 1 : 0,
    }
    if (form.value.password) payload.password = form.value.password
    // 新增走管理端专用接口（无需验证码）；注册接口自防刷修复起强制校验验证码，后台弹窗不能再用
    const res = form.value.userId ? await request.put('/user/update', payload) : await request.post('/user/add', payload)
    if (res.code === 200) {
      dialogVisible.value = false
      ElMessage.success('保存成功')
      loadData(form.value.userId ? page.value : 1) // 编辑留在当前页，新增跳第1页
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  }
}

// ===== 批量导入 =====
const importDialogVisible = ref(false)
const importFile = ref(null)
const importDragOver = ref(false)
const importing = ref(false)
const importResult = ref(null)

const openImportDialog = () => {
  importFile.value = null
  importResult.value = null
  importDialogVisible.value = true
}
const closeImportDialog = () => {
  if (importing.value) return
  importDialogVisible.value = false
}
const onFileSelect = (e) => {
  const file = e.target.files[0]
  if (!file) return
  if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
    ElMessage.warning('只支持 .xlsx 格式的 Excel 文件')
    return
  }
  importFile.value = file
  importResult.value = null
}
const onFileDrop = (e) => {
  importDragOver.value = false
  const file = e.dataTransfer.files[0]
  if (!file) return
  if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
    ElMessage.warning('只支持 .xlsx 格式的 Excel 文件')
    return
  }
  importFile.value = file
  importResult.value = null
}
const clearImportFile = () => {
  importFile.value = null
  importResult.value = null
}
const formatFileSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}
const downloadTemplate = () => {
  downloadUserTemplate().then(res => {
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '用户导入模板.xlsx')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  }).catch(() => {
    ElMessage.error('模板下载失败')
  })
}
const doImport = async () => {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importing.value = true
  importResult.value = null
  try {
    const res = await importUsers(importFile.value)
    if (res.code === 200) {
      importResult.value = res.data
      if (res.data.success > 0) {
        ElMessage.success(`导入成功 ${res.data.success} 条`)
      }
      if (res.data.fail > 0) {
        ElMessage.warning(`有 ${res.data.fail} 条导入失败`)
      }
      loadData(1)
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败：' + (e.message || '未知错误'))
  } finally {
    importing.value = false
  }
}
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
/* 批量导入样式 */
.import-area {
  padding: 10px 0;
}
.import-tip {
  background: #f5f9f5;
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #556a55;
}
.import-tip p {
  margin: 0 0 8px 0;
  font-weight: 600;
}
.import-tip ul {
  margin: 0 0 10px 0;
  padding-left: 20px;
}
.import-tip li {
  margin-bottom: 4px;
}
.upload-zone {
  border: 2px dashed #c8d8c8;
  border-radius: 12px;
  padding: 30px;
  text-align: center;
  transition: all 0.2s;
  cursor: pointer;
}
.upload-zone:hover {
  border-color: var(--c-hex);
  background: #f9fcf9;
}
.upload-zone.drag-over {
  border-color: var(--c-hex);
  background: #f0f8f0;
}
.upload-zone.has-file {
  cursor: default;
  background: #f9fcf9;
}
.upload-icon {
  font-size: 40px;
  margin-bottom: 10px;
}
.upload-text {
  font-size: 14px;
  color: #556a55;
  margin-bottom: 6px;
}
.upload-hint {
  font-size: 12px;
  color: #aaa;
}
.file-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.file-name {
  font-weight: 500;
  color: var(--c-text);
}
.file-size {
  font-size: 12px;
  color: #8a9a8a;
}
.import-result {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #eef2ee;
}
.result-summary {
  display: flex;
  justify-content: space-around;
  margin-bottom: 12px;
}
.result-item {
  text-align: center;
}
.result-num {
  display: block;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 4px;
}
.result-item.success .result-num { color: #4caf50; }
.result-item.fail .result-num { color: #f44336; }
.result-item.total .result-num { color: #2196f3; }
.result-label {
  font-size: 12px;
  color: #8a9a8a;
}
.error-list {
  background: #fff5f5;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  max-height: 120px;
  overflow-y: auto;
}
.error-title {
  color: #f44336;
  font-weight: 600;
  margin-bottom: 6px;
}
.error-list ul {
  margin: 0;
  padding-left: 18px;
  color: #c62828;
}
.error-list li {
  margin-bottom: 3px;
}
/* ===== 用户 360 画像抽屉 ===== */
.pf-head { display: flex; gap: 14px; align-items: center; padding-bottom: 14px; border-bottom: 1px dashed color-mix(in srgb, var(--c-main) 40%, transparent); }
.pf-avatar { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.pf-meta { min-width: 0; }
.pf-name { font-size: 16px; font-weight: 600; color: var(--c-text); }
.pf-sub { font-size: 12.5px; color: #8a9a8a; margin-top: 2px; }
.pf-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 16px 0; }
.pf-card { background: var(--c-bg1, #f4f8f2); border-radius: 14px; padding: 14px; }
.pf-t { font-size: 12.5px; color: #8a9a8a; margin-bottom: 6px; }
.pf-big { font-size: 22px; font-weight: 700; color: var(--c-text); line-height: 1.2; }
.pf-big.is-red { color: #c62828; }
.pf-big span { font-size: 12px; font-weight: 400; color: #9aa79a; }
.pf-line { font-size: 12.5px; color: #6b7a6b; margin-top: 4px; }
.pf-bars { margin-top: 10px; }
.pf-bar-row { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.pf-bar-l { width: 36px; font-size: 12px; color: #8a9a8a; flex-shrink: 0; }
.pf-bar { flex: 1; height: 6px; background: color-mix(in srgb, var(--c-main) 30%, transparent); border-radius: 3px; overflow: hidden; }
.pf-bar i { display: block; height: 100%; background: var(--c-hex, #7bc47b); border-radius: 3px; }
.pf-bar-n { width: 24px; text-align: right; font-size: 12px; color: #9aa79a; }
.pf-empty { font-size: 12px; color: #b0bdb0; }
.pf-note { font-size: 12px; color: #8a6d3b; background: #fff8e1; border: 1px solid #ffe0b2; border-radius: 10px; padding: 8px 12px; line-height: 1.6; }

@media (max-width: 768px) {
  .pf-grid { grid-template-columns: 1fr; }
}

/* ===== 编辑弹窗分组与头像区 ===== */
.dlg-sec {
  font-size: 13px; font-weight: 700; color: var(--c-text);
  margin: 4px 0 14px; padding-left: 10px;
  border-left: 3px solid var(--c-hex, #7bc47b); line-height: 1.2;
}
.dlg-sec:not(:first-child) { margin-top: 20px; }
.av-row { display: flex; gap: 12px; align-items: flex-start; }
.av-cur { flex-shrink: 0; }
.av-cur img { width: 52px; height: 52px; border-radius: 12px; object-fit: cover; border: 2px solid #eee; }
.av-list { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.av-item { border: 3px solid transparent; border-radius: 10px; cursor: pointer; padding: 1px; transition: border-color .15s; }
.av-item.on { border-color: #6a9a6a; }
.av-item img { width: 38px; height: 38px; border-radius: 8px; object-fit: cover; display: block; }
.av-add { width: 38px; height: 38px; border: 2px dashed #ccc; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #aaa; font-size: 18px; }
.av-add:hover { border-color: #6a9a6a; color: #6a9a6a; }


.dlg-tip { font-size: 12px; color: #9aa79a; background: var(--c-bg1, #f4f8f2); border-radius: 10px; padding: 8px 12px; line-height: 1.6; margin-top: 4px; }
/* 编辑态用户名只读提示（用户名=登录凭据不可改，只展示昵称输入框） */
.uname-static { margin: 0 0 14px 80px; font-size: 12.5px; color: #9aa79a; }
.uname-static b { color: #556a55; font-weight: 600; }

/* 用户列：昵称为主、@用户名为副 */
.user-cell { display: flex; flex-direction: column; line-height: 1.35; }
.user-cell .uc-name { font-weight: 600; color: #3d4a3d; }
.user-cell .uc-uname { font-size: 11.5px; color: #9aa79a; }

</style>
