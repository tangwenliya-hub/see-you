<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/日记管理.png" class="title-icon" /> 日记管理</div>
      <div style="display:flex; gap:12px; align-items:center">
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="keyword" placeholder="搜索日记内容..." @input="onSearchInput" /></div>
        <div class="admin-search"><input v-model.number="userIdFilter" placeholder="按用户 ID 过滤" style="width:130px" @input="onSearchInput" /></div>
      </div>
    </div>

    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <!-- 批量操作条 -->
        <div v-if="selectedIds.length > 0" class="batch-bar">
          <span class="batch-info">已选择 <b>{{ selectedIds.length }}</b> 篇日记</span>
          <button class="jelly-btn danger" style="background:#ff6b6b" @click="batchDelete">🗑 批量删除</button>
        </div>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th style="width:50px">
                <el-checkbox :model-value="isAllSelected" :indeterminate="isIndeterminate" @change="toggleSelectAll" />
              </th>
              <th>ID</th><th>用户</th><th>情绪</th><th>内容</th><th>睡眠</th><th>日期</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(d, idx) in list" :key="d.diaryId" :class="{ 'row-selected': selectedIds.includes(d.diaryId) }">
              <td><el-checkbox :model-value="selectedIds.includes(d.diaryId)" @change="toggleSelect(d.diaryId)" /></td>
              <td>{{ (page - 1) * pageSize + idx + 1 }}</td>
              <td>
                <div style="display:flex;align-items:center;gap:8px">
                  <img :src="userMap[d.userId]?.avatar || '/img/1 (1).png'" style="width:28px;height:28px;border-radius:50%;object-fit:cover" />
                  <span>{{ userMap[d.userId]?.nickname || ('用户 #' + d.userId) }}</span>
                </div>
              </td>
              <td><span :class="'status-tag ' + (emotionTone(d.emotion))">{{ emotionName(d.emotion) }}</span></td>
              <td style="max-width:320px">{{ d.diaryContent }}</td>
              <td style="color:#8a9a8a">{{ d.sleepHours != null ? d.sleepHours + 'h' : '-' }}</td>
              <td style="color:#8a9a8a">{{ d.diaryDate }}</td>
              <td>
                <button class="jelly-btn danger" style="background:#ff6b6b" @click="handleDelete(d)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">没有符合条件的日记~</div>
      </template>
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { adminDiaryList, adminDiaryDelete, getUserList } from '@/api'
import { useBatchSelect } from '@/composables/useBatchSelect'
import { jellyConfirm } from '@/utils/confirm'

const EMOTION_NAMES = { 1: '开心', 2: '平静', 3: '疲惫', 4: '难过', 5: '生气', 6: '焦虑', 7: '兴奋', 8: '满足', 9: '其他' }
const POSITIVE = [1, 2, 7, 8], NEGATIVE = [4, 5]

const list = ref([])
const keyword = ref('')
const userIdFilter = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const userMap = ref({})
let searchTimer = null

// 批量勾选
const { selectedIds, isAllSelected, isIndeterminate, toggleSelect, toggleSelectAll, clearSelection } = useBatchSelect(list, d => d.diaryId)

// 批量删除
const batchDelete = () => {
  jellyConfirm(`确定删除选中的 ${selectedIds.value.length} 篇日记吗？删除后不可恢复。`, { title: '批量删除日记', confirmText: '删除', danger: true })
    .then(async (ok) => {
      if (!ok) return
      let done = 0
      for (const id of selectedIds.value) {
        try { await adminDiaryDelete(id); done++ } catch {}
      }
      ElMessage.success(`已删除 ${done}/${selectedIds.value.length} 篇日记`)
      clearSelection()
      loadData()
    })
}

const emotionName = (v) => EMOTION_NAMES[v] || '其他'
const emotionTone = (v) => POSITIVE.includes(v) ? 'on' : NEGATIVE.includes(v) ? 'danger' : 'warn'

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (userIdFilter.value) params.userId = userIdFilter.value
    const res = await adminDiaryList(params)
    list.value = res?.data?.records || []
    total.value = Number(res?.data?.total || 0)
    // 翻页/筛选后清掉上一页的勾选
    clearSelection()
    // 当前页出现的用户一次性查昵称（避免每行一个请求）
    const ids = [...new Set(list.value.map(d => d.userId).filter(Boolean))]
    if (ids.length) {
      const us = await getUserList({ page: 1, size: 100 })
      const m = {}
      ;(us?.data?.records || []).forEach(u => { m[u.userId] = { nickname: u.nickname || u.username, avatar: u.avatar } })
      userMap.value = m
    }
  } catch (e) {
    ElMessage.error('日记列表加载失败')
  } finally {
    loading.value = false
  }
}

const onSearchInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadData() }, 400)
}

const handleDelete = (d) => {
  jellyConfirm('确定删除这篇日记吗？删除后不可恢复。', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      await adminDiaryDelete(d.diaryId)
      ElMessage.success('已删除')
      // 删空当前页时回退一页
      if (list.value.length === 1 && page.value > 1) page.value--
      loadData()
    }).catch(() => {})
}

onMounted(loadData)
onBeforeUnmount(() => clearTimeout(searchTimer))
</script>
