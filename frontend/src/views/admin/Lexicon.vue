<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/情绪词典.png" class="title-icon" /> 情绪词典维护</div>
      <div style="display:flex;gap:10px">
        <button class="jelly-btn ghost" @click="importVisible = true">批量导入</button>
        <button class="jelly-btn primary" @click="openAdd">新增词条</button>
      </div>
    </div>

    <div class="ext-note info">
      词典是无感记录「随笔文本情感分析」的基础：情感词决定倾向，否定词做符号翻转（不开心 → 负向），
      程度副词做倍数缩放（非常累 ×1.6）。词表存在库里而非写死在代码里，
      <b>后台改完点保存即热更新，无需重启服务</b>。
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat"><div class="l">词条总数</div><div class="v">{{ st.total }}</div><div class="s">覆盖 9 类情绪</div></div>
      <div class="ext-stat is-blue"><div class="l">情感词</div><div class="v">{{ st.sentiment }}</div><div class="s">决定情绪倾向</div></div>
      <div class="ext-stat is-purple"><div class="l">否定词</div><div class="v">{{ st.negation }}</div><div class="s">如「不」「没有」</div></div>
      <div class="ext-stat"><div class="l">程度副词</div><div class="v">{{ st.degree }}</div><div class="s">如「非常」「有点」</div></div>
    </div>

    <div class="ext-box" style="margin-bottom:18px">
      <h3><img src="/img/情绪词典.png" /> 九类情绪词条分布</h3>
      <div ref="distRef" class="ext-chart"></div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <div class="admin-search">
          <img src="/img/搜索.png" class="search-icon" />
          <input v-model="keyword" placeholder="搜索词条..." @input="onSearchInput" />
        </div>
        <el-select v-model="emotion" placeholder="全部情绪" clearable @change="onFilter">
          <el-option v-for="(n, i) in EMOTIONS" :key="i" :label="n" :value="i + 1" />
        </el-select>
        <el-select v-model="wordType" placeholder="全部类型" clearable @change="onFilter">
          <el-option label="情感词" :value="0" />
          <el-option label="否定词" :value="1" />
          <el-option label="程度副词" :value="2" />
        </el-select>
      </div>

      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr><th>ID</th><th>词条</th><th>情绪</th><th>权重</th><th>类型</th><th>来源</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="l in list" :key="l.lexiconId">
              <td>{{ l.lexiconId }}</td>
              <td><b>{{ l.word }}</b></td>
              <td><span class="status-tag" :class="{ muted: l.emotion === 0 }">{{ l.emotionName }}</span></td>
              <td>{{ l.weight }}</td>
              <td>{{ l.wordTypeName }}</td>
              <td style="color:#8a9a8a">{{ l.sourceName || l.sourceTag || '-' }}</td>
              <td>
                <button class="jelly-btn op-main" style="margin-right:6px" @click="openEdit(l)">编辑</button>
                <button class="jelly-btn danger" @click="handleDelete(l)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">没有符合条件的词条~</div>
      </template>

      <div style="display:flex;justify-content:center;margin-top:20px">
        <el-pagination
          v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]" :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList" @current-change="loadList" background />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="editVisible" :title="form.lexiconId ? '编辑词条' : '新增词条'" width="460px">
      <div class="lex-form">
        <label>词条<input v-model="form.word" placeholder="如：开心 / 不 / 非常" /></label>
        <label>情绪类别
          <select v-model="form.emotion">
            <option v-for="(n, i) in EMOTIONS" :key="i" :value="i + 1">{{ n }}</option>
          </select>
        </label>
        <label>权重<input v-model.number="form.weight" type="number" step="0.1" /></label>
        <label>类型
          <select v-model="form.wordType">
            <option :value="0">情感词</option>
            <option :value="1">否定词</option>
            <option :value="2">程度副词</option>
          </select>
        </label>
      </div>
      <template #footer>
        <button class="jelly-btn ghost" @click="editVisible = false">取消</button>
        <button class="jelly-btn primary" @click="submitSave">保存并生效</button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入词条" width="560px">
      <div class="ext-note info" style="margin-top:0">
        每行一条，格式：<b>词条,情绪编号,权重,类型</b>（后两项可省略，默认 1.0 / 情感词）。<br />
        示例：<code>开心,1,1.2,0</code>　<code>不,4,1,1</code>　<code>非常,3,1.6,2</code>　<code># 井号开头为注释</code>
      </div>
      <textarea v-model="importText" rows="10" class="import-box" placeholder="开心,1&#10;有点累,3,0.8,0&#10;不,4,1,1"></textarea>
      <template #footer>
        <button class="jelly-btn ghost" @click="importVisible = false">取消</button>
        <button class="jelly-btn primary" @click="submitImport">开始导入</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import { adminLexiconStats, adminLexiconList, adminLexiconSave, adminLexiconDelete, adminLexiconImport } from '@/api'
import { useChart } from '@/composables/useChart'
import { textColor, weakColor, borderColor, EMOTION_COLORS, categoryAxis, valueAxis } from '@/utils/adminTheme'

const EMOTIONS = ['开心', '平静', '疲惫', '难过', '生气', '焦虑', '兴奋', '满足', '其他']
const st = ref({ total: 0, sentiment: 0, negation: 0, degree: 0 })
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const emotion = ref(null)
const wordType = ref(null)
const loading = ref(false)

const editVisible = ref(false)
const importVisible = ref(false)
const importText = ref('')
const form = ref({ lexiconId: null, word: '', emotion: 1, weight: 1.0, wordType: 0 })

const distRef = ref(null)
const distChart = useChart(distRef, 'admin-lexicon-dist')

let searchTimer = null

const loadStats = async () => {
  const res = await adminLexiconStats()
  if (res?.code !== 200) return
  const r = res.data || {}
  st.value = {
    total: Number(r.total || 0), sentiment: Number(r.sentiment || 0),
    negation: Number(r.negation || 0), degree: Number(r.degree || 0),
  }
  const by = r.byEmotion || {}
  distChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, confine: true },
    grid: { left: 8, right: 14, top: 20, bottom: 24, containLabel: true },
    xAxis: categoryAxis(EMOTIONS),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [{
      type: 'bar', barWidth: 20,
      data: EMOTIONS.map((n, i) => ({
        value: Number(by[n] || 0),
        itemStyle: { borderRadius: [6, 6, 0, 0], color: EMOTION_COLORS[i % EMOTION_COLORS.length] },
      })),
      label: { show: true, position: 'top', color: textColor(), fontSize: 11 },
    }],
  }))
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await adminLexiconList({
      page: page.value, size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      emotion: emotion.value ?? undefined,
      wordType: wordType.value ?? undefined,
    })
    if (res?.code !== 200) { ElMessage.error(res?.msg || '词典加载失败'); return }
    list.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
  } finally { loading.value = false }
}

const onFilter = () => { page.value = 1; loadList() }
const onSearchInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadList() }, 400)
}

const openAdd = () => {
  form.value = { lexiconId: null, word: '', emotion: 1, weight: 1.0, wordType: 0 }
  editVisible.value = true
}
const openEdit = (l) => {
  form.value = {
    lexiconId: l.lexiconId, word: l.word, emotion: l.emotion,
    weight: Number(l.weight ?? 1), wordType: l.wordType ?? 0,
  }
  editVisible.value = true
}

const submitSave = async () => {
  if (!form.value.word?.trim()) { ElMessage.warning('词条不能为空'); return }
  const res = await adminLexiconSave({ ...form.value, word: form.value.word.trim() })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '保存失败'); return }
  ElMessage.success({ message: '已保存并热更新词典', grouping: true })
  editVisible.value = false
  loadStats(); loadList()
}

const handleDelete = (l) => {
  jellyConfirm(`确定删除词条「${l.word}」吗？`, { title: '删除确认', danger: true })
    .then(async (ok) => {
      if (!ok) return
      const res = await adminLexiconDelete(l.lexiconId)
      if (res?.code !== 200) { ElMessage.error(res?.msg || '删除失败'); return }
      ElMessage.success({ message: '已删除', grouping: true })
      loadStats(); loadList()
    }).catch(() => {})
}

const submitImport = async () => {
  if (!importText.value.trim()) { ElMessage.warning('导入内容为空'); return }
  const res = await adminLexiconImport(importText.value)
  if (res?.code !== 200) { ElMessage.error(res?.msg || '导入失败'); return }
  const r = res.data || {}
  ElMessage.success({ message: `导入完成：新增 ${r.added} 条，更新 ${r.updated} 条，跳过 ${r.skipped} 条`, grouping: true })
  importVisible.value = false
  importText.value = ''
  loadStats(); loadList()
}

onMounted(async () => { await nextTick(); loadStats(); loadList() })
onBeforeUnmount(() => clearTimeout(searchTimer))
</script>

<style scoped>
/* 功能词（否定词/程度副词）的情绪列显示「—」时弱化为灰色，不与真实情绪标签抢注意力 */
.status-tag.muted { background: #eef1f4; color: #98a2b3; border: 1px solid #e2e6ea; }
.lex-form { display: flex; flex-direction: column; gap: 12px; }
.lex-form label { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #556a55; }
.lex-form input, .lex-form select {
  border: 1px solid color-mix(in srgb, var(--c-main) 60%, transparent); border-radius: 10px;
  padding: 9px 12px; font-size: 14px; outline: none; background: var(--c-surface-solid); color: var(--c-text);
}
.lex-form input:focus, .lex-form select:focus { border-color: var(--c-hex, #7bc47b); }
.import-box {
  width: 100%; border: 1px solid color-mix(in srgb, var(--c-main) 60%, transparent); border-radius: 12px;
  padding: 12px; font-size: 13px; line-height: 1.6; outline: none; resize: vertical;
  font-family: monospace; color: #3d4a3d;
}
</style>
