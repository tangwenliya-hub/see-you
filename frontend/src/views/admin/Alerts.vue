<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/情绪预警后台图标.png" class="title-icon" /> 情绪预警管理</div>
      <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
    </div>

    <!-- 非医疗边界声明：后台侧同样必须展示，防止管理员把它当诊断结果使用 -->
    <div class="ext-note">
      ⚠️ {{ disclaimer || '本提示仅基于用户自行记录的情绪数据做统计参考，不属于医疗诊断，不能替代专业评估。' }}
      后台同样遵循隐私红线：<b>不展示任何用户的日记正文与行为明细</b>，仅呈现聚合统计指标。
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat">
        <div class="l"><img src="/img/情绪预警总数.png" />预警总数</div>
        <div class="v">{{ s.total }}</div>
        <div class="s">今日新增 {{ s.today }}</div>
      </div>
      <div class="ext-stat is-red">
        <div class="l"><img src="/img/情绪风险预警等级分布.png" />三级 高度关注</div>
        <div class="v">{{ s.l3 }}</div>
        <div class="s" :class="{ warn: s.l3 > 0 }">{{ s.l3 > 0 ? '需要人工关注' : '暂无' }}</div>
      </div>
      <div class="ext-stat is-blue">
        <div class="l"><img src="/img/情绪预警后台图标.png" />二级 中度提醒</div>
        <div class="v">{{ s.l2 }}</div>
        <div class="s">一级 {{ s.l1 }}</div>
      </div>
      <div class="ext-stat is-purple">
        <div class="l"><img src="/img/我的评论.png" />未读 / 未确认</div>
        <div class="v">{{ s.unread }}</div>
        <div class="s">未确认 {{ s.unacked }} 条</div>
      </div>
      <!-- 准确率 = (触发总数 - 误报数) / 触发总数：来自用户端「这不是我」反馈，论文量化指标 -->
      <div class="ext-stat is-green">
        <div class="l"><img src="/img/我的评论.png" />预警准确率</div>
        <div class="v">{{ s.accuracy == null ? '--' : s.accuracy + '%' }}</div>
        <div class="s">误报 {{ s.misreport }} 条（用户标记）</div>
      </div>
    </div>

    <div class="ext-chart-grid">
      <div class="ext-box">
        <h3><img src="/img/用户活跃趋势.png" /> 近 30 天预警趋势（按等级）</h3>
        <div ref="trendRef" class="ext-chart"></div>
      </div>
      <div class="ext-box">
        <h3><img src="/img/情绪风险预警等级分布.png" /> 预警等级分布</h3>
        <div ref="levelRef" class="ext-chart"></div>
      </div>
    </div>

    <!-- 分通道准确率：讲清楚「哪条触发通道最准、误报集中在哪」 -->
    <div class="ext-box" style="margin-bottom:18px">
      <h3><img src="/img/我的评论.png" /> 各触发通道准确率（用户「这不是我」反馈）</h3>
      <div v-if="!s.channels?.length" class="ch-empty">暂无预警数据，产生预警后这里会按通道统计准确率</div>
      <div v-else class="ch-list">
        <div v-for="c in s.channels" :key="c.rule" class="ch-row">
          <div class="ch-name">
            {{ c.ruleName }}<span v-if="c.crisis" class="ch-flag">安全通道</span>
          </div>
          <div class="ch-bar">
            <div class="ch-bar-in" :style="{ width: (c.accuracy == null ? 0 : c.accuracy) + '%' }"></div>
          </div>
          <div class="ch-acc">{{ c.accuracy == null ? '—' : c.accuracy + '%' }}</div>
          <div class="ch-num">
            触发 {{ c.total }} 条 · 误报 {{ c.misreport }} 条
            <span v-if="c.crisis" class="ch-note">（危机通道不采集误报，安全优先）</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 词库管理：危机词/预警词/例外短语，改完内存立即生效 + 落库持久化，无需重启 -->
    <div class="ext-box" style="margin-bottom:18px">
      <h3><img src="/img/情绪风险预警等级分布.png" /> 预警词库管理（改完立即生效，无需重启）</h3>
      <div class="wl-note">
        危机词命中 → 三级红色关怀；预警词命中 → 二级中度提醒；例外短语 → 命中词附近出现时判为亲昵/夸张用法，不触发（如「想死你了」）。
        词表改动会记录操作人，并落库持久化，重启后依然有效。
      </div>
      <div class="wl-groups">
        <div v-for="g in wordGroups" :key="g.type" class="wl-group">
          <div class="wl-head">
            <span class="wl-name" :class="g.type">{{ g.label }}</span>
            <span class="wl-desc">{{ g.desc }}</span>
            <span class="wl-count">{{ g.words.length }} 个</span>
          </div>
          <div class="wl-tags">
            <span v-for="w in g.words" :key="w" class="wl-tag" :class="g.type">
              {{ w }}<i class="wl-del" title="删除该词条" @click="removeWord(g, w)">×</i>
            </span>
            <span class="wl-add">
              <input v-model="g.draft" :placeholder="'新增' + g.label + '…'" maxlength="20" @keyup.enter="addWord(g)" />
              <button :disabled="!g.draft.trim() || g.saving" @click="addWord(g)">{{ g.saving ? '…' : '＋ 添加' }}</button>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <el-select v-model="level" placeholder="全部等级" clearable @change="onFilter">
          <el-option label="一级 轻度关注" :value="1" />
          <el-option label="二级 中度提醒" :value="2" />
          <el-option label="三级 高度关注" :value="3" />
        </el-select>
        <el-select v-model="isRead" placeholder="全部状态" clearable @change="onFilter">
          <el-option label="未读" :value="0" />
          <el-option label="已读" :value="1" />
        </el-select>
        <div class="admin-search">
          <img src="/img/搜索.png" class="search-icon" />
          <input v-model="keyword" placeholder="搜索用户名..." @input="onSearchInput" />
        </div>
        <button class="export-btn" :disabled="exporting" @click="onExport">
          {{ exporting ? '导出中…' : '⬇ 导出 Excel' }}
        </button>
      </div>

      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th>用户</th><th>预警日期</th><th>等级</th><th>低落天数</th>
              <th>负面占比</th><th>平均指数</th><th>触发规则</th><th title="已读 = 被查看（用户端弹窗弹出或管理员点开查看依据，任一方都算）；已知晓 = 用户在用户端点了「我知道了」">状态</th><th title="管理员处置：已电话联系 / 已通知辅导员 / 已上门面谈（详情弹窗里标记）">处置</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in list" :key="a.alertId">
              <td>{{ a.userName }}</td>
              <td>{{ a.alertDate }}</td>
              <td>
                <span class="status-tag" :style="levelStyle(a.level)">{{ a.levelName }}</span>
                <span v-if="a.crisis" class="status-tag warn" style="margin-left:4px">危机词</span>
              </td>
              <td>{{ a.maxDowncastDays == null ? '—' : (a.maxDowncastDays + ' 天') }}</td>
              <td>{{ pct(a.negativeRatio) }}</td>
              <td>{{ a.avgIndex ?? '-' }}</td>
              <td style="max-width:200px">{{ a.triggerRuleName || a.triggerRule || '-' }}</td>
              <td>
                <span class="status-tag" :class="a.isRead ? 'on' : 'warn'">{{ a.isRead ? '已读' : '未读' }}</span>
                <span class="status-tag" style="margin-left:4px">{{ a.ackStatus ? '已知晓' : '未确认' }}</span>
                <span v-if="a.misreport" class="status-tag misreport" style="margin-left:4px" title="用户标记「这不是我」">误报</span>
              </td>
              <td>
                <span class="status-tag" :class="a.disposition ? 'on' : 'muted'">{{ a.disposition || '未处置' }}</span>
              </td>
              <td><button class="jelly-btn ghost" @click="openDetail(a)">查看依据</button></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无预警记录~</div>
      </template>

      <div style="display:flex;justify-content:center;margin-top:20px">
        <el-pagination
          v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList" @current-change="loadList" background />
      </div>
    </div>

    <!-- 预警依据弹窗：只给统计指标，不给日记正文 -->
    <el-dialog v-model="detailVisible" title="预警判定依据" width="560px">
      <div v-if="detail" class="detail-body">
        <p class="d-row"><span>用户</span><b>{{ detail.userName }}</b></p>
        <p class="d-row"><span>预警日期</span><b>{{ detail.alertDate }}</b></p>
        <p class="d-row"><span>等级</span><b>{{ levelName(detail.level) }}</b></p>
        <p class="d-row"><span>判定窗口</span><b>{{ detail.windowDays }} 天（有记录 {{ detail.recordDays }} 天）</b></p>
        <p class="d-row"><span>负面天数</span><b>{{ detail.negativeDays }} 天 · 低落 {{ detail.maxDowncastDays }} 天</b></p>
        <p class="d-row"><span>负面占比</span><b>{{ pct(detail.negativeRatio) }}</b></p>
        <p class="d-row"><span>平均情绪指数</span><b>{{ detail.avgIndex ?? '-' }}</b></p>
        <p class="d-row"><span>指数趋势斜率</span><b>{{ detail.trendSlope ?? '-' }}</b></p>
        <p class="d-row"><span>命中规则</span><b>{{ detail.triggerRuleName || detail.triggerRule || '-' }}</b></p>
        <div class="d-block">
          <div class="d-title">判定说明</div>
          <div class="d-text">{{ detail.triggerDetail || '-' }}</div>
        </div>
        <div class="d-block">
          <div class="d-title">系统给用户的建议</div>
          <div class="d-text">{{ detail.advice || '-' }}</div>
        </div>

        <!-- 处置（轻量闭环）：发现 → 查看 → 处置，记录管理员做了什么 -->
        <div class="d-block">
          <div class="d-title">管理员处置</div>
          <div v-if="detail.misreport" class="d-text misreport-note">
            ⚠️ 用户已将本条标记为<b>误报（「这不是我」）</b><template v-if="fmtTime(detail.misreportTime)"> · {{ fmtTime(detail.misreportTime) }}</template>。处置前建议先与用户确认真实状态。
          </div>
          <div class="d-text" v-if="detail.disposition">
            {{ detail.disposition }}
            <template v-if="fmtTime(detail.dispositionTime)"> · {{ fmtTime(detail.dispositionTime) }}</template>
            <template v-if="detail.dispositionNote"> · 备注：{{ detail.dispositionNote }}</template>
          </div>
          <div class="d-text" v-else style="color:#b08a4a">还未处置 —— 处置完成后请标记，便于回溯处理过程与响应时效</div>
          <div class="disp-form">
            <el-select v-model="dispWay" placeholder="选择处置方式" size="small" style="width:170px" clearable>
              <el-option v-for="w in DISPOSITION_OPTIONS" :key="w" :label="w" :value="w" />
            </el-select>
            <el-input v-model="dispNote" placeholder="备注：联系到了谁 / 用户状态如何（可空）" size="small" maxlength="200" style="flex:1" />
            <el-button type="primary" size="small" :loading="dispSaving" :disabled="!dispWay" @click="saveDisposition">保存处置</el-button>
          </div>
        </div>

        <div class="ext-note" style="margin:12px 0 0">{{ detail.privacyNote }}</div>
        <div class="ext-note" style="margin:8px 0 0">{{ detail.disclaimer }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { adminAlertStats, adminAlertList, adminAlertDetail, adminAlertMarkRead, adminAlertDisposition, adminAlertExport, adminRiskWords, adminRiskWordAdd, adminRiskWordRemove } from '@/api'
import { useChart } from '@/composables/useChart'
import { accent, textColor, weakColor, borderColor, hexA, LEVEL_COLORS, categoryAxis, valueAxis } from '@/utils/adminTheme'

// 初始值必须包含模板里会访问的所有字段（channels 缺失曾导致首次渲染时
// !s.channels.length 报 TypeError，整个页面白屏）
const s = ref({ total: 0, today: 0, l1: 0, l2: 0, l3: 0, unread: 0, unacked: 0, misreport: 0, accuracy: null, channels: [] })
const disclaimer = ref('')
const list = ref([])
const total = ref(0)
const page = ref(1)
const exporting = ref(false)

/** 导出 Excel：带当前筛选条件（等级/已读/关键字），后端导出全部匹配记录 */
const onExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await adminAlertExport({
      level: level.value ?? undefined,
      isRead: isRead.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
    })
    // 拦截器对 blob 原样透传；业务错误时后端仍可能回 JSON（类型是 application/json）
    if (blob instanceof Blob && blob.type.includes('json')) {
      const j = JSON.parse(await blob.text())
      ElMessage.error(j?.msg || '导出失败')
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `情绪预警报表_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('报表已导出')
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}
const pageSize = ref(10)
const level = ref(null)
const isRead = ref(null)
const keyword = ref('')
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
let searchTimer = null

const trendRef = ref(null)
const levelRef = ref(null)
const trendChart = useChart(trendRef, 'admin-alert-trend')
const levelChart = useChart(levelRef, 'admin-alert-level')

const levelName = (v) => ({ 1: '轻度关注', 2: '中度提醒', 3: '高度关注' }[v] || '-')
const levelStyle = (v) => v ? { background: LEVEL_COLORS[v - 1] + '22', color: LEVEL_COLORS[v - 1], border: '1px solid ' + LEVEL_COLORS[v - 1] } : {}
const pct = (v) => v == null ? '-' : (Number(v) * 100).toFixed(0) + '%'

const loadStats = async () => {
  const res = await adminAlertStats()
  if (res?.code !== 200) { ElMessage.error(res?.msg || '预警统计加载失败'); return }
  const d = res.data || {}
  s.value = {
    total: Number(d.total || 0), today: Number(d.today || 0),
    l1: Number(d.l1 || 0), l2: Number(d.l2 || 0), l3: Number(d.l3 || 0),
    unread: Number(d.unread || 0), unacked: Number(d.unacked || 0),
    misreport: Number(d.misreport || 0),
    accuracy: d.accuracy == null ? null : Number(d.accuracy),
    channels: Array.isArray(d.channels) ? d.channels : [],
  }
  disclaimer.value = d.disclaimer || ''
  const trend = Array.isArray(d.trend) ? d.trend : []
  trendChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', confine: true },
    legend: { data: ['一级', '二级', '三级'], textStyle: { color: textColor() } },
    grid: { left: 8, right: 14, top: 36, bottom: 24, containLabel: true },
    xAxis: categoryAxis(trend.map(i => String(i.date || '').slice(5))),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [
      { name: '一级', type: 'bar', stack: 'lv', barWidth: 14, data: trend.map(i => i.l1), itemStyle: { color: LEVEL_COLORS[0] } },
      { name: '二级', type: 'bar', stack: 'lv', barWidth: 14, data: trend.map(i => i.l2), itemStyle: { color: LEVEL_COLORS[1] } },
      { name: '三级', type: 'bar', stack: 'lv', barWidth: 14, data: trend.map(i => i.l3), itemStyle: { color: LEVEL_COLORS[2] } },
    ],
  }))
  levelChart.setResponsive(() => ({
    tooltip: { trigger: 'item', confine: true, formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: textColor() } },
    series: [{
      name: '预警等级', type: 'pie', radius: ['42%', '68%'], center: ['50%', '45%'],
      label: { show: false },
      data: [
        { name: '一级 轻度关注', value: s.value.l1, itemStyle: { color: LEVEL_COLORS[0] } },
        { name: '二级 中度提醒', value: s.value.l2, itemStyle: { color: LEVEL_COLORS[1] } },
        { name: '三级 高度关注', value: s.value.l3, itemStyle: { color: LEVEL_COLORS[2] } },
      ],
    }],
  }))
}

const loadList = async () => {
  const res = await adminAlertList({
    page: page.value, size: pageSize.value,
    level: level.value ?? undefined,
    isRead: isRead.value ?? undefined,
    keyword: keyword.value.trim() || undefined,
  })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '预警列表加载失败'); return }
  list.value = res.data?.list || []
  total.value = Number(res.data?.total || 0)
}

const loadAll = async () => {
  loading.value = true
  try { await Promise.all([loadStats(), loadList()]) } finally { loading.value = false }
}

const onFilter = () => { page.value = 1; loadList() }
const onSearchInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadList() }, 400)
}

const openDetail = async (a) => {
  const res = await adminAlertDetail(a.alertId)
  if (res?.code !== 200) { ElMessage.error(res?.msg || '详情加载失败'); return }
  detail.value = res.data
  // 处置表单回显当前值
  dispWay.value = res.data.disposition || ''
  dispNote.value = res.data.dispositionNote || ''
  detailVisible.value = true
  // 管理员查看即已读：「已读 = 被查看」（用户端弹窗或管理员查看，任一方都算）
  if (!a.isRead) {
    const r = await adminAlertMarkRead(a.alertId)
    if (r?.code === 200) a.isRead = 1
  }
}

// ==================== 管理员处置（轻量闭环） ====================
const DISPOSITION_OPTIONS = ['已电话联系', '已通知辅导员', '已上门面谈']
const dispWay = ref('')
const dispNote = ref('')
const dispSaving = ref(false)

// LocalDateTime 后端可能序列化成 "2026-11-04T15:40:01" 或数组，两种都兜住
const fmtTime = (t) => {
  if (!t) return ''
  if (Array.isArray(t)) {
    const [y, mo, d, h = 0, mi = 0] = t
    return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`
  }
  return String(t).replace('T', ' ').slice(0, 16)
}

const saveDisposition = async () => {
  if (!detail.value?.alertId || !dispWay.value) return
  dispSaving.value = true
  try {
    const res = await adminAlertDisposition(detail.value.alertId, {
      disposition: dispWay.value,
      note: dispNote.value.trim() || '',
    })
    if (res?.code !== 200) { ElMessage.error(res?.msg || '处置保存失败'); return }
    ElMessage.success('✅ 处置已记录')
    detail.value.disposition = dispWay.value
    detail.value.dispositionNote = dispNote.value.trim() || null
    detail.value.dispositionTime = new Date().toISOString().slice(0, 19)
    // 同步列表行，免得用户关掉弹窗看到旧状态
    const row = list.value.find(x => x.alertId === detail.value.alertId)
    if (row) { row.disposition = dispWay.value; row.dispositionTime = detail.value.dispositionTime }
  } finally {
    dispSaving.value = false
  }
}

// ==================== 预警词库管理（危机词/预警词/例外短语） ====================
// 三组词表都是「内存立即生效 + sys_config 持久化」——演示时改完直接发日记验证，不用重启后端
const wordGroups = reactive([
  { type: 'crisis',   label: '危机词',   desc: '命中 → 三级红色关怀', words: [], draft: '', saving: false },
  { type: 'warning',  label: '预警词',   desc: '命中 → 二级中度提醒', words: [], draft: '', saving: false },
  { type: 'excludes', label: '例外短语', desc: '亲昵/夸张用法豁免',   words: [], draft: '', saving: false },
])

const loadWords = async () => {
  const res = await adminRiskWords()
  if (res?.code !== 200) { ElMessage.error(res?.msg || '词库加载失败'); return }
  const d = res.data || {}
  wordGroups.forEach(g => { g.words = Array.isArray(d[g.type]) ? d[g.type] : [] })
}

const addWord = async (g) => {
  const word = g.draft.trim()
  if (!word || g.saving) return
  g.saving = true
  try {
    const res = await adminRiskWordAdd(g.type, word)
    if (res?.code !== 200) { ElMessage.error(res?.msg || '添加失败'); return }
    g.words.push(word)
    g.draft = ''
    ElMessage.success(`已添加「${word}」，立即生效`)
  } finally { g.saving = false }
}

const removeWord = async (g, w) => {
  try {
    const res = await adminRiskWordRemove(g.type, w)
    if (res?.code !== 200) { ElMessage.error(res?.msg || '删除失败'); return }
    g.words = g.words.filter(x => x !== w)
    ElMessage.success(`已删除「${w}」`)
  } catch { ElMessage.error('删除失败，请重试') }
}

onMounted(async () => {
  await nextTick()
  loadAll()
  loadWords()
  // SSE 收到新预警时自动刷新（AdminLayout 负责弹通知，这里只管数据）
  window.addEventListener('risk-alert-new', onRiskPush)
  window.__alertsLoadData = loadAll
})
onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  window.removeEventListener('risk-alert-new', onRiskPush)
  if (window.__alertsLoadData === loadAll) delete window.__alertsLoadData
})
const onRiskPush = () => loadAll()
</script>

<style scoped>
.detail-body { font-size: 13.5px; color: #3d4a3d; }
/* 误报标签：灰紫调，与已读/未读、危机词标签都区分开 */
.status-tag.misreport { background: #efe7f5; color: #7a5a9a; border: 1px solid #d9c8ea; }
/* 详情弹窗里的误报提示条 */
.d-text.misreport-note { background: #f6f1fb; border-left: 3px solid #b593d6; border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; color: #6e5290; }
.d-text.misreport-note b { color: #5a3f78; }
.d-row { display: flex; gap: 12px; margin: 0 0 8px; }
.d-row span { width: 96px; color: #8a9a8a; flex-shrink: 0; }
.d-block { margin-top: 12px; }
.d-title { font-weight: 600; margin-bottom: 4px; color: var(--c-text); }
.d-text { background: var(--c-bg1, #f4f8f2); border-radius: 10px; padding: 10px 12px; line-height: 1.7; }
/* 处置表单 */
.disp-form { display: flex; gap: 8px; align-items: center; margin-top: 10px; }
/* 列表里「未处置」用灰字，不与「未读」的警示黄抢注意力 */
.status-tag.muted { background: #eef1f4; color: #98a2b3; border: 1px solid #e2e6ea; }

/* 分通道准确率 */
.ch-empty { color: #9aa8b5; font-size: 13px; padding: 8px 0 2px; }
.ch-list { display: flex; flex-direction: column; gap: 12px; padding-top: 4px; }
.ch-row {
  display: grid; grid-template-columns: 210px 1fr 64px; align-items: center;
  gap: 12px; font-size: 13px; color: #3d4a3d;
}
.ch-name { font-weight: 700; display: flex; align-items: center; gap: 6px; }
.ch-flag {
  font-size: 11px; font-weight: 500; color: #b45f2b;
  background: #fdf0d6; border-radius: 7px; padding: 1px 6px;
}
.ch-bar { height: 8px; border-radius: 6px; background: #eef2ee; overflow: hidden; }
.ch-bar-in {
  height: 100%; border-radius: 6px;
  background: linear-gradient(90deg, #9ed3a8, #5aa96c);
  transition: width 0.4s ease;
}
.ch-acc { font-weight: 800; color: #2e7d32; text-align: right; }
.ch-num { grid-column: 2 / 4; font-size: 12px; color: #98a2b3; margin-top: -8px; }
.ch-note { color: #c08a3e; }
/* ===== 词库管理 ===== */
.wl-note {
  font-size: 12.5px; line-height: 1.8; color: #6b7f93;
  background: #f4f8fb; border-left: 3px solid #8fb8d8;
  border-radius: 8px; padding: 8px 12px; margin-bottom: 14px;
}
.wl-groups { display: flex; flex-direction: column; gap: 14px; }
.wl-group {
  background: var(--c-bg1, #f7faf5); border-radius: 12px;
  padding: 12px 14px;
}
.wl-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 10px; }
.wl-name { font-size: 14px; font-weight: 800; }
.wl-name.crisis { color: #c94f4f; }
.wl-name.warning { color: #cf8a3a; }
.wl-name.excludes { color: #7a8a9a; }
.wl-desc { font-size: 12px; color: #98a2b3; }
.wl-count { margin-left: auto; font-size: 12px; color: #98a2b3; }
.wl-tags { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.wl-tag {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12.5px; padding: 4px 8px 4px 12px; border-radius: 999px;
  transition: transform 0.15s ease;
}
.wl-tag:hover { transform: translateY(-1px); }
.wl-tag.crisis { background: #fdeaea; color: #a04343; border: 1px solid #f0c9c9; }
.wl-tag.warning { background: #fdf3e3; color: #a06a28; border: 1px solid #efd9b8; }
.wl-tag.excludes { background: #eef1f4; color: #5d7186; border: 1px solid #dbe2e9; }
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
  cursor: pointer; white-space: nowrap; transition: opacity 0.2s;
}
.wl-add button:disabled { opacity: 0.5; cursor: not-allowed; }
@media (max-width: 768px) {
  .ch-row { grid-template-columns: 1fr 52px; }
  .ch-bar { grid-column: 1 / 3; }
  .ch-num { grid-column: 1 / 3; margin-top: 0; }
  .wl-head { flex-wrap: wrap; }
  .wl-count { margin-left: 0; }
}

</style>
