<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/测评量表.png" class="title-icon" /> 测评量表管理</div>
      <div style="display:flex; gap:12px; align-items:center">
        <div class="admin-search"><img src="/img/搜索.png" class="search-icon" /><input v-model="keyword" placeholder="搜索量表名称..." /></div>
        <button class="jelly-btn primary" @click="openPaperDialog(null)">＋ 新增量表</button>
      </div>
    </div>
    <div class="admin-card">
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="filteredList.length > 0" class="admin-table">
          <thead><tr><th>ID</th><th>量表名称</th><th>题数</th><th>总分区间</th><th>完成次数</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="(t, idx) in pagedList" :key="t.testId">
              <td>{{ (page - 1) * pageSize + idx + 1 }}</td>
              <td>{{ t.name }}</td>
              <td>{{ t.count }} 题</td>
              <td>{{ t.scoreRange }}</td>
              <td>{{ t.taken }} 次</td>
              <td><el-switch v-model="t.on" @change="(val) => handleStatusChange(t, val)" :active-value="1" :inactive-value="0" /></td>
              <td>
                <div class="op-btns">
                  <button class="jelly-btn op-main" @click="openQuestionDialog(t)">编辑题目</button>
                  <button class="jelly-btn op-sub" @click="openPaperDialog(t)">编辑</button>
                  <button class="jelly-btn danger" @click="handleDeletePaper(t)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无测评量表，点击"新增量表"添加~</div>
        <!-- 分页 -->
        <div v-if="filteredList.length > 0" style="display:flex;justify-content:center;margin-top:20px">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredList.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </template>
    </div>

    <!-- 量表编辑弹窗 -->
    <el-dialog v-model="paperDialogVisible" :title="paperForm.testId?'编辑量表':'新增量表'" width="620px" top="6vh"
               :close-on-click-modal="false" append-to-body>
      <el-tabs v-model="paperActiveTab" type="border-card">
        <el-tab-pane label="基本信息" name="basic">
          <el-form :model="paperForm" label-width="100px" label-position="right" style="padding-top:8px">
            <el-form-item label="量表名称"><el-input v-model="paperForm.name" placeholder="如 PHQ-9 抑郁自评" /></el-form-item>
            <el-form-item label="量表简介"><el-input v-model="paperForm.desc" type="textarea" :rows="2" placeholder="量表简介说明" /></el-form-item>
            <el-form-item label="最低分"><el-input-number v-model="paperForm.minScore" :min="0" /></el-form-item>
            <el-form-item label="最高分"><el-input-number v-model="paperForm.maxScore" :min="0" /></el-form-item>
            <el-form-item label="状态"><el-switch v-model="paperForm.on" /></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="结果分级" name="grade">
          <div class="grade-toolbar">
            <span style="color:#888;font-size:13px">不同分数段对应不同的结果解读和建议</span>
            <button class="jelly-btn primary tiny" @click="addGrade">+ 添加分级</button>
          </div>
          <div class="grade-list">
            <div v-for="(g, idx) in gradeList" :key="idx" class="grade-item">
              <div class="grade-header">
                <span class="grade-index">分级 {{ idx + 1 }}</span>
                <button class="jelly-btn tiny danger" @click="removeGrade(idx)">删除</button>
              </div>
              <el-form :model="g" label-width="80px" label-position="right" size="small">
                <el-form-item label="等级名称">
                  <el-input v-model="g.gradeName" placeholder="如：轻度抑郁" />
                </el-form-item>
                <el-form-item label="分数区间">
                  <div style="display:flex; gap:8px; align-items:center">
                    <el-input-number v-model="g.minScore" :min="0" size="small" />
                    <span>~</span>
                    <el-input-number v-model="g.maxScore" :min="0" size="small" />
                    <span style="color:#999;font-size:12px">分</span>
                  </div>
                </el-form-item>
                <el-form-item label="结果描述">
                  <el-input v-model="g.description" type="textarea" :rows="2" placeholder="对该等级的结果描述" />
                </el-form-item>
                <el-form-item label="建议">
                  <el-input v-model="g.suggestion" type="textarea" :rows="2" placeholder="给用户的建议" />
                </el-form-item>
              </el-form>
            </div>
            <div v-if="gradeList.length === 0" class="empty-grades">
              还没有结果分级，点击上方"添加分级"添加
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <button class="jelly-btn ghost" @click="paperDialogVisible=false">取消</button>
        <button class="jelly-btn primary" @click="savePaper">保存</button>
      </template>
    </el-dialog>

    <!-- 题目管理弹窗 -->
    <el-dialog v-model="questionDialogVisible" :title="currentPaper.name + ' - 题目管理'" width="720px" top="5vh"
               :close-on-click-modal="false" append-to-body>
      <div class="question-toolbar">
        <span style="color:#888">共 {{ questionList.length }} 道题</span>
        <button class="jelly-btn primary" @click="openOptionDialog(null)">＋ 新增题目</button>
      </div>
      <div class="question-list">
        <div v-for="(q, qIdx) in questionList" :key="q.questionId" class="question-item">
          <div class="question-header">
            <span class="q-index">第 {{ qIdx + 1 }} 题</span>
            <span class="q-content">{{ q.questionContent }}</span>
            <div class="q-ops">
              <button class="jelly-btn tiny ghost" @click="openOptionDialog(q)">编辑</button>
              <button class="jelly-btn tiny danger" @click="handleDeleteQuestion(q)">删除</button>
            </div>
          </div>
          <div class="option-list">
            <div v-for="(opt, oIdx) in q.options" :key="opt.optionId" class="option-item">
              <span class="opt-label">{{ String.fromCharCode(65 + oIdx) }}.</span>
              <span class="opt-text">{{ opt.optionText }}</span>
              <span class="opt-score">（{{ opt.score }} 分）</span>
            </div>
            <div v-if="!q.options || q.options.length === 0" class="empty-options">暂无选项</div>
          </div>
        </div>
        <div v-if="questionList.length === 0" class="empty-questions">
          还没有题目，点击右上角"新增题目"添加
        </div>
      </div>
    </el-dialog>

    <!-- 题目编辑弹窗（含选项） -->
    <el-dialog v-model="optionDialogVisible" :title="questionForm.questionId?'编辑题目':'新增题目'" width="560px"
               :close-on-click-modal="false" append-to-body>
      <el-form :model="questionForm" label-width="80px" label-position="right">
        <el-form-item label="题目内容">
          <el-input v-model="questionForm.questionContent" type="textarea" :rows="2" placeholder="请输入题目内容" />
        </el-form-item>
        <el-form-item label="选项">
          <div class="option-edit-list">
            <div v-for="(opt, idx) in questionForm.options" :key="idx" class="option-edit-row">
              <span class="opt-letter">{{ String.fromCharCode(65 + idx) }}</span>
              <el-input v-model="opt.optionText" placeholder="选项内容" style="flex:1" />
              <el-input-number v-model="opt.score" :min="0" :max="10" size="small" />
              <button class="jelly-btn tiny danger" @click="removeOption(idx)">删除</button>
            </div>
          </div>
          <button class="jelly-btn ghost" style="margin-top:8px" @click="addOption">+ 添加选项</button>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="jelly-btn ghost" @click="optionDialogVisible=false">取消</button>
        <button class="jelly-btn primary" @click="handleSaveQuestion">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import {
  getTestPaperList, saveTestPaper, updateTestPaper, deleteTestPaper, updateTestPaperStatus,
  getResultGradeList, batchSaveResultGrade,
  getQuestionList, saveQuestionWithOptions, deleteQuestion,
  getOptionList, saveOption, updateOption, deleteOption
} from '@/api'

// ===== 量表列表 =====
const list = ref([])
const keyword = ref('')
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filteredList = computed(() =>
  list.value.filter(t => !keyword.value || t.name?.includes(keyword.value))
)
const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

watch(keyword, () => {
  page.value = 1
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getTestPaperList()
    if (res.code === 200) {
      const data = res.data
      const records = Array.isArray(data) ? data : (data?.records || [])
      list.value = records.map(t => ({
        testId: t.testId || t.id,
        name: t.testName || t.title || t.name || '-',
        desc: t.testDesc || t.desc || '',
        count: t.questionCount || t.count || 0,
        scoreRange: t.scoreRange || (t.minScore != null && t.maxScore != null ? `${t.minScore}-${t.maxScore}` : '-'),
        taken: t.takenCount || t.taken || 0,
        on: t.status != null ? (t.status === 1 || t.status === true) : true,
      }))
    }
  } catch (e) {
    console.warn('加载测评列表失败:', e)
  } finally {
    loading.value = false
  }
}

// ===== 量表编辑 =====
const paperDialogVisible = ref(false)
const paperActiveTab = ref('basic')
const paperForm = ref({ testId: null, name: '', desc: '', minScore: 0, maxScore: 0, on: true })
const gradeList = ref([])

const openPaperDialog = (row) => {
  paperActiveTab.value = 'basic'
  if (row) {
    const parts = (row.scoreRange || '0-0').split('-')
    paperForm.value = {
      testId: row.testId,
      name: row.name,
      desc: row.desc || '',
      minScore: parseInt(parts[0]) || 0,
      maxScore: parseInt(parts[1]) || 0,
      on: row.on,
    }
    // 加载结果分级
    loadGrades(row.testId)
  } else {
    paperForm.value = { testId: null, name: '', desc: '', minScore: 0, maxScore: 0, on: true }
    gradeList.value = []
  }
  paperDialogVisible.value = true
}

const loadGrades = async (testId) => {
  try {
    const res = await getResultGradeList(testId)
    if (res.code === 200) {
      gradeList.value = res.data || []
    } else {
      gradeList.value = []
    }
  } catch (e) {
    gradeList.value = []
    console.warn('加载结果分级失败:', e)
  }
}

const addGrade = () => {
  gradeList.value.push({
    gradeId: null,
    gradeName: '',
    minScore: 0,
    maxScore: 0,
    description: '',
    suggestion: ''
  })
}

const removeGrade = (idx) => {
  gradeList.value.splice(idx, 1)
}

const savePaper = async () => {
  if (!paperForm.value.name) { ElMessage.warning('请填写量表名称'); return }
  try {
    const payload = {
      testName: paperForm.value.name,
      testDesc: paperForm.value.desc || '',
      minScore: paperForm.value.minScore ?? 0,
      maxScore: paperForm.value.maxScore ?? 0,
      status: paperForm.value.on ? 1 : 0,
    }
    let res
    let savedTestId
    if (paperForm.value.testId) {
      payload.testId = paperForm.value.testId
      res = await updateTestPaper(payload)
      savedTestId = paperForm.value.testId
    } else {
      res = await saveTestPaper(payload)
      savedTestId = res.data
    }
    if (res.code !== 200) { ElMessage.error(res.msg || '保存失败'); return }

    // 保存结果分级
    if (savedTestId) {
      const gradeRes = await batchSaveResultGrade(savedTestId, gradeList.value)
      if (gradeRes.code !== 200) {
        ElMessage.warning('量表保存成功，但结果分级保存失败')
        paperDialogVisible.value = false
        loadData()
        return
      }
    }

    ElMessage.success('保存成功')
    paperDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
    console.error(e)
  }
}

const handleDeletePaper = (t) => {
  jellyConfirm('确定删除该量表？题目和选项也会受到影响。', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteTestPaper(t.testId)
        if (res.code === 200) { ElMessage.success('删除成功'); loadData() }
        else ElMessage.error(res.msg || '删除失败')
      } catch (e) { ElMessage.error('删除失败') }
    }).catch(() => {})
}

const handleStatusChange = async (row, newVal) => {
  try {
    const res = await updateTestPaperStatus(row.testId, newVal ? 1 : 0)
    if (res.code !== 200) {
      // 失败时回滚
      row.on = !newVal
      ElMessage.error(res.msg || '状态更新失败')
    }
  } catch (e) {
    // 失败时回滚
    row.on = !newVal
    ElMessage.error('状态更新失败')
  }
}

// ===== 题目管理 =====
const questionDialogVisible = ref(false)
const currentPaper = ref({ testId: null, name: '' })
const questionList = ref([])

const openQuestionDialog = async (paper) => {
  currentPaper.value = paper
  questionDialogVisible.value = true
  questionList.value = []
  await loadQuestions()
}

const loadQuestions = async () => {
  try {
    const res = await getQuestionList({ testId: currentPaper.value.testId, page: 1, size: 100 })
    if (res.code === 200) {
      const records = res.data?.records || res.data || []
      // 加载每道题的选项
      const qWithOpts = []
      for (const q of records) {
        try {
          const oRes = await getOptionList({ questionId: q.questionId, page: 1, size: 20 })
          const opts = oRes.data?.records || oRes.data || []
          qWithOpts.push({ ...q, options: opts })
        } catch (e) {
          qWithOpts.push({ ...q, options: [] })
        }
      }
      questionList.value = qWithOpts
    }
  } catch (e) {
    console.warn('加载题目失败:', e)
  }
}

// ===== 题目编辑（含选项）=====
const optionDialogVisible = ref(false)
const questionForm = ref({ questionId: null, questionContent: '', options: [] })

const openOptionDialog = (q) => {
  if (q) {
    questionForm.value = {
      questionId: q.questionId,
      questionContent: q.questionContent,
      options: (q.options || []).map(o => ({ ...o }))
    }
  } else {
    questionForm.value = {
      questionId: null,
      questionContent: '',
      options: [
        { optionId: null, optionText: '', score: 0 },
        { optionId: null, optionText: '', score: 1 }
      ]
    }
  }
  optionDialogVisible.value = true
}

const addOption = () => {
  questionForm.value.options.push({ optionId: null, optionText: '', score: 0 })
}

const removeOption = (idx) => {
  questionForm.value.options.splice(idx, 1)
}

const handleSaveQuestion = async () => {
  if (!questionForm.value.questionContent) { ElMessage.warning('请填写题目内容'); return }
  if (questionForm.value.options.length < 2) { ElMessage.warning('至少需要2个选项'); return }

  try {
    const payload = {
      questionId: questionForm.value.questionId,
      testId: currentPaper.value.testId,
      questionContent: questionForm.value.questionContent,
      options: questionForm.value.options
    }

    const res = await saveQuestionWithOptions(payload)
    if (res.code === 200) {
      ElMessage.success('保存成功')
      optionDialogVisible.value = false
      loadQuestions()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
    console.error(e)
  }
}

const handleDeleteQuestion = (q) => {
  jellyConfirm('确定删除该题目？', { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      try {
        const res = await deleteQuestion(q.questionId)
        if (res.code === 200) { ElMessage.success('删除成功'); loadQuestions() }
        else ElMessage.error(res.msg || '删除失败')
      } catch (e) { ElMessage.error('删除失败') }
    }).catch(() => {})
}

onMounted(loadData)
</script>

<style scoped>
.question-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

.question-list {
  max-height: 55vh;
  overflow-y: auto;
  padding-right: 8px;
}

.question-item {
  background: #f9faf9;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid #eaeaea;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.q-index {
  background: var(--c-hex);
  color: white;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.q-content {
  flex: 1;
  font-weight: 500;
  color: var(--c-text);
}

.q-ops {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.option-list {
  padding-left: 8px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  color: var(--c-text);
  font-size: 14px;
}

.opt-label {
  color: var(--c-hex);
  font-weight: 600;
  width: 20px;
}

.opt-text {
  flex: 1;
}

.opt-score {
  color: #aaa;
  font-size: 13px;
}

.empty-options, .empty-questions {
  text-align: center;
  color: #bbb;
  padding: 20px;
  font-size: 14px;
}

.option-edit-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-edit-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.opt-letter {
  width: 24px;
  height: 24px;
  background: var(--c-hex);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.jelly-btn.tiny {
  padding: 4px 10px;
  font-size: 12px;
}

.grade-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.grade-list {
  max-height: 50vh;
  overflow-y: auto;
  padding-right: 4px;
}

.grade-item {
  background: #f9faf9;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  border: 1px solid #eaeaea;
}

.grade-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.grade-index {
  font-weight: 600;
  color: var(--c-hex);
  font-size: 14px;
}

.empty-grades {
  text-align: center;
  color: #bbb;
  padding: 30px;
  font-size: 14px;
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
