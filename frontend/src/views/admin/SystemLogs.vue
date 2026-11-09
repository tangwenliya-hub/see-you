<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/操作日志.png" class="title-icon" /> 系统操作日志</div>
      <div style="display:flex;gap:10px;align-items:center">
        <button class="jelly-btn ghost" :disabled="exporting" @click="doExport">导出 CSV</button>
        <button class="jelly-btn ghost" @click="openClear">清理旧日志</button>
        <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
      </div>
    </div>

    <div class="ext-note info">
      由 AOP 切面自动记录：管理员在后台的<b>所有写操作</b>（新增 / 修改 / 删除 / 强制下线 / 恢复默认）
      与登录行为。请求参数中的<b>密码、验证码、token 会自动打码</b>后再落库。只读请求不记录，避免日志表被列表查询刷爆。
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat">
        <div class="l"><img src="/img/操作日志.png" />日志总数</div>
        <div class="v">{{ s.total }}</div>
        <div class="s">今日 {{ s.today }}</div>
      </div>
      <div class="ext-stat is-red">
        <div class="l"><img src="/img/情绪风险预警等级分布.png" />失败操作</div>
        <div class="v">{{ s.fail }}</div>
        <div class="s" :class="{ warn: s.fail > 0 }">{{ s.fail > 0 ? '建议排查' : '暂无异常' }}</div>
      </div>
      <div class="ext-stat is-blue">
        <div class="l"><img src="/img/用户活跃趋势.png" />平均耗时</div>
        <div class="v">{{ s.avgCost }}<span style="font-size:14px">ms</span></div>
        <div class="s">近 2000 条均值</div>
      </div>
      <div class="ext-stat is-purple">
        <div class="l"><img src="/img/操作日志.png" />覆盖模块</div>
        <div class="v">{{ (s.moduleDist || []).length }}</div>
        <div class="s">个后台模块有操作留痕</div>
      </div>
    </div>

    <div class="ext-chart-grid">
      <div class="ext-box">
        <h3><img src="/img/用户活跃趋势.png" /> 近 14 天操作量趋势</h3>
        <div ref="trendRef" class="ext-chart"></div>
      </div>
      <div class="ext-box">
        <h3><img src="/img/操作日志.png" /> 模块操作分布</h3>
        <div ref="moduleRef" class="ext-chart"></div>
      </div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <el-select v-model="module" placeholder="全部模块" clearable @change="onFilter">
          <el-option v-for="m in (s.moduleDist || [])" :key="m.name" :label="m.name" :value="m.name" />
        </el-select>
        <el-select v-model="status" placeholder="全部状态" clearable @change="onFilter">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <div class="admin-search">
          <img src="/img/搜索.png" class="search-icon" />
          <input v-model="keyword" placeholder="搜索操作人 / 描述..." @input="onSearchInput" />
        </div>
        <span style="margin-left:auto;color:#9aa79a;font-size:12.5px">共 {{ total }} 条</span>
      </div>

      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th>时间</th><th>操作人</th><th>模块</th><th>类型</th><th>描述</th><th>耗时</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in list" :key="l.logId">
              <td style="color:#8a9a8a">{{ fmtTime(l.createTime) }}</td>
              <td>{{ l.nickname || l.username || '-' }}</td>
              <td><span class="status-tag">{{ l.module }}</span></td>
              <td>{{ l.operation }}</td>
              <td style="max-width:260px" :title="l.description">{{ l.description }}</td>
              <td>{{ l.costTime }}ms</td>
              <td><span class="status-tag" :class="l.status === 1 ? 'on' : 'warn'">{{ l.status === 1 ? '成功' : '失败' }}</span></td>
              <td><button class="jelly-btn op-main" @click="openDetail(l)">详情</button></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无操作日志~（写操作与登录后才会产生）</div>
      </template>

      <div style="display:flex;justify-content:center;margin-top:20px">
        <el-pagination
          v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList" @current-change="loadList" background />
      </div>
    </div>

    <!-- 日志详情 -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="600px">
      <div v-if="detail" class="detail-body">
        <p class="d-row"><span>操作人</span><b>{{ detail.nickname || detail.username || '-' }}（ID {{ detail.userId }}）</b></p>
        <p class="d-row"><span>时间</span><b>{{ fmtTime(detail.createTime) }}</b></p>
        <p class="d-row"><span>模块 / 类型</span><b>{{ detail.module }} · {{ detail.operation }}</b></p>
        <p class="d-row"><span>请求方法</span><b>{{ detail.method }}</b></p>
        <p class="d-row"><span>IP</span><b>{{ detail.ip || '-' }}</b></p>
        <p class="d-row"><span>耗时</span><b>{{ detail.costTime }} ms</b></p>
        <p class="d-row"><span>状态</span><b>{{ detail.status === 1 ? '成功' : '失败' }}</b></p>
        <div class="d-block">
          <div class="d-title">请求参数（敏感字段已打码）</div>
          <div class="d-text mono">{{ detail.params || '（无参数）' }}</div>
        </div>
        <div v-if="detail.status === 0" class="d-block">
          <div class="d-title">失败原因</div>
          <div class="d-text err">{{ detail.errorMsg || '-' }}</div>
        </div>
      </div>
      <template #footer>
        <button class="jelly-btn primary" @click="detailVisible = false">关闭</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminLogStats, adminLogList, adminLogClear } from '@/api'
import { useChart } from '@/composables/useChart'
import { accent, textColor, weakColor, hexA, categoryAxis, valueAxis } from '@/utils/adminTheme'
import { exportCsv, fmtTime } from '@/utils/exportCsv'

const s = ref({ total: 0, today: 0, fail: 0, avgCost: 0, moduleDist: [] })
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const module = ref(null)
const status = ref(null)
const keyword = ref('')
const loading = ref(false)
const exporting = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
let searchTimer = null

const trendRef = ref(null)
const moduleRef = ref(null)
const trendChart = useChart(trendRef, 'admin-log-trend')
const moduleChart = useChart(moduleRef, 'admin-log-module')

const loadStats = async () => {
  let res
  try {
    res = await adminLogStats()
  } catch {
    // 网络层失败（后端未启动/正在重启）：不弹假 0，给一句能看懂的提示
    ElMessage.error('连不上后端服务（可能正在重启），稍后点「刷新数据」重试')
    return
  }
  if (res?.code !== 200) { ElMessage.error(res?.msg || '日志统计加载失败'); return }
  const d = res.data || {}
  s.value = {
    total: Number(d.total || 0), today: Number(d.today || 0),
    fail: Number(d.fail || 0), avgCost: Number(d.avgCost || 0),
    moduleDist: Array.isArray(d.moduleDist) ? d.moduleDist : [],
  }
  const trend = Array.isArray(d.trend) ? d.trend : []
  trendChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', confine: true },
    grid: { left: 8, right: 14, top: 20, bottom: 24, containLabel: true },
    xAxis: categoryAxis(trend.map(i => String(i.date || '').slice(5))),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [{
      name: '操作次数', type: 'bar', barWidth: 14, data: trend.map(i => i.count),
      itemStyle: { color: accent(), borderRadius: [4, 4, 0, 0] },
    }],
  }))
  const dist = s.value.moduleDist.slice(0, 8)
  moduleChart.setResponsive(() => ({
    tooltip: { trigger: 'item', confine: true, formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', bottom: 0, textStyle: { color: textColor() } },
    series: [{
      name: '模块分布', type: 'pie', radius: ['40%', '66%'], center: ['50%', '44%'],
      label: { show: false },
      data: (dist.length ? dist : [{ name: '暂无数据', value: 0 }]).map((i, idx) => ({
        name: i.name, value: i.value,
        itemStyle: { color: ['#7bc47b', '#5b9bd5', '#9b7bd5', '#e59bb3', '#e69b5b', '#5bb8a6', '#d96b7b', '#6b7bd9'][idx % 8] },
      })),
    }],
  }))
}

const loadList = async () => {
  let res
  try {
    res = await adminLogList({
      page: page.value, size: pageSize.value,
      module: module.value ?? undefined,
      status: status.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
    })
  } catch {
    ElMessage.error('连不上后端服务（可能正在重启），稍后点「刷新数据」重试')
    return
  }
  if (res?.code !== 200) { ElMessage.error(res?.msg || '日志列表加载失败'); return }
  list.value = res.data?.rows || []
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

const openDetail = (l) => { detail.value = l; detailVisible.value = true }

const openClear = () => {
  ElMessageBox.prompt('清理多少天之前的操作日志？', '清理旧日志', {
    inputType: 'number', inputValue: '90', inputPattern: /^[1-9]\d*$/,
    inputErrorMessage: '请输入正整数天数', type: 'warning',
  }).then(async ({ value }) => {
    const res = await adminLogClear(Number(value))
    if (res?.code !== 200) { ElMessage.error(res?.msg || '清理失败（只读管理员无此权限）'); return }
    ElMessage.success(`已清理 ${res.data?.deleted ?? 0} 条 ${value} 天前的日志`)
    loadAll()
  }).catch(() => {})
}

const doExport = async () => {
  exporting.value = true
  try {
    // 后端分页单页封顶 100（PageLimit.MAX_SIZE），传 size:1000 会被静默截断成 100。
    // 这里按 100/页循环拉全，最多 200 页（2 万条）兜底，避免把整表拖垮。
    const rows = []
    const size = 100
    let reachedEnd = false
    for (let p = 1; p <= 200 && !reachedEnd; p++) {
      const res = await adminLogList({
        page: p, size,
        module: module.value ?? undefined,
        status: status.value ?? undefined,
        keyword: keyword.value.trim() || undefined,
      })
      if (res?.code !== 200) { ElMessage.error(res?.msg || '导出失败'); return }
      const chunk = res.data?.rows || []
      rows.push(...chunk)
      const t = Number(res.data?.total || 0)
      if (chunk.length < size || rows.length >= t) reachedEnd = true
    }
    const r = exportCsv('系统操作日志', rows, [
      { label: '时间', field: 'createTime', fmt: fmtTime },
      { label: '操作人ID', field: 'userId' },
      { label: '账号', field: 'username' },
      { label: '昵称', field: 'nickname' },
      { label: '模块', field: 'module' },
      { label: '操作类型', field: 'operation' },
      { label: 'IP', field: 'ip' },
      { label: '耗时(ms)', field: 'costTime' },
      { label: '状态', field: (r) => (r.status === 1 ? '成功' : '失败') },
      { label: '描述', field: 'description' },
    ])
    if (r.ok) ElMessage.success(`已导出 ${r.count} 条`)
    else ElMessage.warning(r.msg)
  } finally { exporting.value = false }
}

onMounted(async () => {
  await nextTick()
  loadAll()
})
onBeforeUnmount(() => clearTimeout(searchTimer))
</script>

<style scoped>
.detail-body { font-size: 13.5px; color: #3d4a3d; }
.d-row { display: flex; gap: 12px; margin: 0 0 8px; }
.d-row span { width: 96px; color: #8a9a8a; flex-shrink: 0; }
.d-block { margin-top: 12px; }
.d-title { font-weight: 600; margin-bottom: 4px; color: var(--c-text); }
.d-text { background: var(--c-bg1, #f4f8f2); border-radius: 10px; padding: 10px 12px; line-height: 1.7; word-break: break-all; }
.d-text.mono { font-family: monospace; font-size: 12.5px; }
.d-text.err { background: #ffebee; color: #c62828; }
</style>
