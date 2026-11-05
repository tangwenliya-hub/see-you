<template>
  <div class="page">
    <div class="page-toolbar">
      <div>
        <div class="page-title"><img src="/img/头像审核.png" class="title-icon" /> 头像审核</div>
        <p class="sub">仅列出用户自行上传的头像；系统内置头像（/img/…）不做干预</p>
      </div>
      <div class="ext-filter" style="margin-bottom:0">
        <el-input v-model="keyword" placeholder="搜索用户名 / 昵称" clearable style="width:200px"
                  @keyup.enter="loadList(1)" @clear="loadList(1)" />
        <button class="jelly-btn primary" @click="loadList(1)">搜索</button>
        <button class="jelly-btn ghost" @click="loadAll">刷新</button>
      </div>
    </div>

    <!-- 概览卡片 -->
    <div class="ext-stat-grid">
      <div class="ext-stat">
        <div class="l"><img src="/img/用户管理.png" alt="" />用户总数</div>
        <div class="v">{{ stats.userTotal ?? '-' }}</div>
        <div class="s">全站注册账号</div>
      </div>
      <div class="ext-stat">
        <div class="l"><img src="/img/头像审核.png" alt="" />自定义头像</div>
        <div class="v is-blue">{{ stats.customCount ?? '-' }}</div>
        <div class="s">需人工复核的图片</div>
      </div>
      <div class="ext-stat">
        <div class="l"><img src="/img/头像框.png" alt="" />内置头像</div>
        <div class="v">{{ stats.builtinCount ?? '-' }}</div>
        <div class="s">系统默认图，无需处理</div>
      </div>
      <div class="ext-stat">
        <div class="l"><img src="/img/操作日志.png" alt="" />累计重置</div>
        <div class="v is-red">{{ stats.resetCount ?? '-' }}</div>
        <div class="s">管理员重置次数（有日志可查）</div>
      </div>
    </div>

    <div class="ext-note">
      这里只处理「用户自己上传」的头像。发现违规图片可一键重置为系统默认头像，
      不必禁用整个账号；每次重置都会写入操作日志，可在「操作日志」页回溯。
    </div>

    <!-- 头像卡片列表 -->
    <div class="ext-box">
      <h3><img src="/img/头像审核.png" alt="" />自定义头像用户（{{ total }}）</h3>
      <div v-if="loading" class="table-loading">加载中…</div>
      <div v-else-if="!list.length" class="table-empty">当前没有用户上传的头像~</div>
      <div v-else class="av-grid">
        <div v-for="item in list" :key="item.userId" class="av-card">
          <div class="ac-img">
            <img :src="item.avatar" :alt="item.username" @error="onImgError" />
          </div>
          <div class="ac-info">
            <div class="ac-name">{{ item.nickname || item.username }}</div>
            <div class="ac-sub">@{{ item.username }}</div>
            <div class="ac-tags">
              <span :class="'status-tag ' + (item.role === 1 ? 'warn' : (item.role === 2 ? '' : 'on'))">
                {{ item.role === 1 ? '管理员' : (item.role === 2 ? '只读管理员' : '普通用户') }}
              </span>
              <span :class="'status-tag ' + (item.status === 1 ? 'on' : 'off')">
                {{ item.status === 1 ? '正常' : '禁用' }}
              </span>
            </div>
            <div class="ac-time">{{ fmtDate(item.createTime) }} 注册</div>
          </div>
          <div class="ac-op">
            <button class="jelly-btn danger" @click="handleReset(item)">重置为默认</button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > size" class="av-page">
        <el-pagination :current-page="page" :page-size="size" :total="total"
                       layout="prev, pager, next" @current-change="(p) => loadList(p)" background />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import { adminAvatarStats, adminAvatarList, adminAvatarReset } from '@/api'
import { fmtDate } from '@/utils/exportCsv'

const route = useRoute()

const stats = ref({})
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)
const keyword = ref('')

const onImgError = (e) => { e.target.src = '/img/1 (1).png' }

const loadStats = async () => {
  const res = await adminAvatarStats()
  if (res?.code === 200) stats.value = res.data || {}
}

const loadList = async (p = 1) => {
  page.value = p
  loading.value = true
  try {
    const res = await adminAvatarList({ keyword: keyword.value, page: p, size: size.value })
    if (res?.code === 200) {
      list.value = res.data?.list || []
      total.value = res.data?.total || 0
    } else {
      ElMessage.error(res?.msg || '加载失败')
    }
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const loadAll = async () => { await loadStats(); await loadList(page.value) }

const handleReset = async (item) => {
  const ok = await jellyConfirm(
    `确定将「${item.nickname || item.username}」的头像重置为系统默认头像吗？该操作会写入操作日志。`,
    { title: '重置头像', confirmText: '确定重置' }
  )
  if (!ok) return
  const res = await adminAvatarReset(item.userId)
  if (res?.code === 200) {
    ElMessage.success('已重置为系统默认头像', { grouping: true })
    await loadAll()
  } else {
    ElMessage.error(res?.msg || '重置失败', { grouping: true })
  }
}

onMounted(() => {
  if (route.query.keyword) keyword.value = String(route.query.keyword)
  loadAll()
})
</script>

<style scoped>
.sub { font-size: 12.5px; color: #8a9a8a; margin: 4px 0 0; }

.av-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px;
}
.av-card {
  display: flex; gap: 12px; align-items: center;
  background: var(--c-bg1, #f4f8f2); border-radius: 14px; padding: 12px;
  border: 1px solid color-mix(in srgb, var(--c-main) 25%, transparent);
}
.ac-img { flex-shrink: 0; }
.ac-img img { width: 62px; height: 62px; border-radius: 50%; object-fit: cover; border: 2px solid #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.ac-info { flex: 1; min-width: 0; }
.ac-name { font-size: 14px; font-weight: 600; color: var(--c-text); }
.ac-sub { font-size: 12px; color: #8a9a8a; margin-top: 2px; }
.ac-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px; }
.ac-time { font-size: 11.5px; color: #b0bdb0; margin-top: 4px; }
.ac-op { flex-shrink: 0; }
.av-page { display: flex; justify-content: center; margin-top: 18px; }

@media (max-width: 768px) {
  .av-grid { grid-template-columns: 1fr; }
  .av-card { padding: 10px; }
  .ac-img img { width: 52px; height: 52px; }
}
</style>
