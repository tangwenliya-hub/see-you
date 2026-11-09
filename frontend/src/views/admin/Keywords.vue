<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/AI关键词库.png" class="title-icon" /> AI 关键词话术库</div>
      <div style="display:flex; gap:12px; align-items:center">
        <el-select v-model="filterCategory" placeholder="全部分类" style="width:160px" clearable @change="loadData(1)">
          <el-option v-for="c in categoryList" :key="c" :label="c" :value="c" />
        </el-select>
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="searchKeyword" placeholder="搜索关键词/分类..." @input="onSearchInput" /></div>
        <select v-model="searchStatus" class="admin-select" @change="loadData(1)">
          <option value="">全部状态</option>
          <option value="1">启用</option>
          <option value="0">停用</option>
        </select>
        <button class="jelly-btn primary" @click="handleAdd">＋ 新增关键词</button>
      </div>
    </div>
    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead><tr><th style="width:60px">ID</th><th style="width:100px">关键词</th><th style="width:120px">分类</th><th>匹配示例</th><th>AI 回复内容</th><th style="width:70px">排序</th><th style="width:80px">状态</th><th style="width:160px">操作</th></tr></thead>
          <tbody>
            <tr v-for="(k, idx) in list" :key="k.wordId">
              <td>{{ (page - 1) * pageSize + idx + 1 }}</td>
              <td><strong>{{ k.keyword }}</strong></td>
              <td>
                <span v-if="k.category" class="cat-tag">{{ k.category }}</span>
                <span v-else style="color:#ccc;font-size:12px">-</span>
              </td>
              <td style="color:#8a9a8a; max-width:180px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap" :title="k.matchExample">{{ k.matchExample || '-' }}</td>
              <td style="max-width:280px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap" :title="k.replyText">{{ k.replyText }}</td>
              <td>{{ k.sort || 0 }}</td>
              <td><span :class="'status-tag ' + (k.status === 1 ? 'on' : 'off')">{{ k.status === 1 ? '启用' : '停用' }}</span></td>
              <td>
                <div class="op-btns">
                  <button class="jelly-btn op-main" @click="handleEdit(k)">编辑</button>
                  <button class="jelly-btn danger" @click="handleDelete(k)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无关键词数据，点击"新增关键词"添加~</div>
      </template>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        style="margin-top: 20px; display: flex; justify-content: center;"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.wordId ? '编辑关键词' : '新增关键词'" width="560px" append-to-body :close-on-click-modal="false">
      <el-form :model="form" label-width="100px" label-position="right">
        <el-form-item label="关键词"><el-input v-model="form.keyword" placeholder="触发关键词，如：不开心、焦虑" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择或输入分类" style="width:100%" filterable allow-create default-first-option>
            <el-option v-for="c in categoryList" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配示例"><el-input v-model="form.matchExample" type="textarea" :rows="2" placeholder="用户可能说的话，用于参考" /></el-form-item>
        <el-form-item label="AI回复内容"><el-input v-model="form.replyText" type="textarea" :rows="4" placeholder="AI应该回复的治愈文案" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="999" placeholder="数字越小越靠前" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <button class="jelly-btn ghost" @click="dialogVisible=false">取消</button>
        <button class="jelly-btn primary" @click="save">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import { getAiWordsList, getAiWordsCategories, saveAiWords, updateAiWords, deleteAiWords } from '@/api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const searchStatus = ref('')
const filterCategory = ref('')
const categoryList = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const defaultForm = { wordId: null, keyword: '', category: '', matchExample: '', replyText: '', sort: 0, status: 1 }
const form = ref({ ...defaultForm })

const loadCategories = async () => {
  try {
    const res = await getAiWordsCategories()
    if (res.code === 200) {
      categoryList.value = res.data || []
    }
  } catch (e) { console.warn('加载分类列表失败:', e) }
}

const openDialog = (row) => {
  form.value = row ? { ...row } : { ...defaultForm }
  dialogVisible.value = true
}

const save = async () => {
  if (!form.value.keyword) { ElMessage.warning('请填写关键词'); return }
  if (!form.value.replyText) { ElMessage.warning('请填写AI回复内容'); return }
  try {
    const res = form.value.wordId ? await updateAiWords(form.value) : await saveAiWords(form.value)
    if (res.code === 200) {
      dialogVisible.value = false
      ElMessage.success('保存成功')
      loadCategories() // 刷新分类列表
      loadData(page.value)
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('网络错误，请重试')
  }
}

const handleEdit = (k) => openDialog(k)
const handleDelete = (k) => {
  jellyConfirm('确定删除该关键词？', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteAiWords(k.wordId)
        if (res.code === 200) {
          ElMessage.success('删除成功')
          const nextPage = (list.value.length === 1 && page.value > 1) ? page.value - 1 : page.value
          loadData(nextPage)
        } else {
          ElMessage.error(res.msg || '删除失败')
        }
      } catch (e) { ElMessage.error('网络错误，请重试') }
    }).catch(() => {})
}
const handleAdd = () => openDialog(null)

const loadData = async (p = 1) => {
  page.value = p
  loading.value = true
  try {
    const params = {
      page: p,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined,
      status: !searchStatus.value ? undefined : searchStatus.value,
      category: filterCategory.value || undefined,
    }
    const res = await getAiWordsList(params)
    if (res.code === 200) {
      const data = res.data
      list.value = data?.records || []
      total.value = data?.total || (data?.records?.length || 0)
    }
  } catch (e) {
    console.warn('加载关键词列表失败:', e)
  } finally {
    loading.value = false
  }
}

const handleSizeChange = () => {
  page.value = 1
  loadData(1)
}

const handleCurrentChange = (val) => {
  loadData(val)
}

// 搜索防抖
let searchTimer = null

// 组件卸载时清理搜索防抖定时器
onBeforeUnmount(() => clearTimeout(searchTimer))
const onSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { loadData(1) }, 300)
}

onMounted(async () => {
  await loadCategories()
  loadData(1)
})
</script>

<style scoped>
.cat-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  background: #e8f5e9;
  color: #388e3c;
}
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
</style>
