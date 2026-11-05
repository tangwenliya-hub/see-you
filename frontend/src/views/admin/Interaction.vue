<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/互动统计.png" class="title-icon" /> 互动与内容消费</div>
      <div style="display:flex;gap:10px;align-items:center">
        <button class="jelly-btn ghost" :disabled="exporting" @click="doExport">导出 CSV</button>
        <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
      </div>
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat">
        <div class="l"><img src="/img/互动统计.png" />点赞总数</div>
        <div class="v">{{ s.likeTotal }}</div>
        <div class="s">收藏 {{ s.collectTotal }}</div>
      </div>
      <div class="ext-stat is-blue">
        <div class="l"><img src="/img/心理视频.png" />播放记录</div>
        <div class="v">{{ s.historyTotal }}</div>
        <div class="s">音频 {{ s.audioPlay }} · 视频 {{ s.videoPlay }}</div>
      </div>
      <div class="ext-stat is-purple">
        <div class="l"><img src="/img/治愈音频.png" />完播率</div>
        <div class="v">{{ s.finishRate }}%</div>
        <div class="s">已看完 {{ s.finished }} 次</div>
      </div>
      <div class="ext-stat">
        <div class="l"><img src="/img/用户活跃趋势.png" />参与用户</div>
        <div class="v">{{ s.activeUsers }}</div>
        <div class="s">有点赞/收藏/播放行为</div>
      </div>
    </div>

    <div class="ext-chart-grid">
      <div class="ext-box">
        <h3><img src="/img/用户活跃趋势.png" /> 近 14 天点赞与收藏趋势</h3>
        <div ref="trendRef" class="ext-chart"></div>
      </div>
      <div class="ext-box">
        <h3><img src="/img/最近活跃用户.png" /> 热门内容 Top 10</h3>
        <div ref="hotRef" class="ext-chart"></div>
      </div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <el-select v-model="resourceType" placeholder="全部类型" clearable @change="onFilter">
          <el-option label="音频" :value="1" />
          <el-option label="视频" :value="2" />
        </el-select>
        <el-select v-model="finished" placeholder="全部播放状态" clearable @change="onFilter">
          <el-option label="已看完" :value="1" />
          <el-option label="未看完" :value="0" />
        </el-select>
        <span style="margin-left:auto;color:#9aa79a;font-size:12.5px">共 {{ total }} 条播放记录</span>
      </div>

      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th>时间</th><th>用户</th><th>类型</th><th>内容</th><th>播放进度</th><th>已看完</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="h in list" :key="h.historyId">
              <td style="color:#8a9a8a">{{ fmtTime(h.createTime) }}</td>
              <td>{{ h.userName }}</td>
              <td><span class="status-tag">{{ h.typeName }}</span></td>
              <td style="max-width:240px">{{ h.title }}</td>
              <td>
                <div class="prog-wrap">
                  <div class="prog-bar"><div class="prog-in" :style="{ width: (h.percent || 0) + '%' }"></div></div>
                  <span class="prog-txt">{{ h.percent || 0 }}%</span>
                </div>
              </td>
              <td><span class="status-tag" :class="h.finished === '是' ? 'on' : ''">{{ h.finished }}</span></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无播放记录~</div>
      </template>

      <div style="display:flex;justify-content:center;margin-top:20px">
        <el-pagination
          v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList" @current-change="loadList" background />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { adminInteractionStats, adminInteractionHot, adminInteractionHistory } from '@/api'
import { useChart } from '@/composables/useChart'
import { accent, textColor, weakColor, hexA, categoryAxis, valueAxis } from '@/utils/adminTheme'
import { exportCsv, fmtTime } from '@/utils/exportCsv'

const s = ref({ likeTotal: 0, collectTotal: 0, historyTotal: 0, finished: 0, finishRate: 0, activeUsers: 0, audioPlay: 0, videoPlay: 0 })
const hot = ref([])
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const resourceType = ref(null)
const finished = ref(null)
const loading = ref(false)
const exporting = ref(false)

const trendRef = ref(null)
const hotRef = ref(null)
const trendChart = useChart(trendRef, 'admin-inter-trend')
const hotChart = useChart(hotRef, 'admin-inter-hot')

const loadStats = async () => {
  const res = await adminInteractionStats()
  if (res?.code !== 200) { ElMessage.error(res?.msg || '互动统计加载失败'); return }
  const d = res.data || {}
  s.value = {
    likeTotal: Number(d.likeTotal || 0), collectTotal: Number(d.collectTotal || 0),
    historyTotal: Number(d.historyTotal || 0), finished: Number(d.finished || 0),
    finishRate: Number(d.finishRate || 0), activeUsers: Number(d.activeUsers || 0),
    audioPlay: Number(d.audioPlay || 0), videoPlay: Number(d.videoPlay || 0),
  }
  const trend = Array.isArray(d.trend) ? d.trend : []
  trendChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', confine: true },
    legend: { data: ['点赞', '收藏'], textStyle: { color: textColor() } },
    grid: { left: 8, right: 14, top: 36, bottom: 24, containLabel: true },
    xAxis: categoryAxis(trend.map(i => String(i.date || '').slice(5))),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [
      { name: '点赞', type: 'line', smooth: true, symbolSize: 4, data: trend.map(i => i.like),
        lineStyle: { width: 2, color: accent() }, itemStyle: { color: accent() }, areaStyle: { color: hexA(accent(), 0.14) } },
      { name: '收藏', type: 'line', smooth: true, symbolSize: 4, data: trend.map(i => i.collect),
        lineStyle: { width: 2, color: '#5b9bd5' }, itemStyle: { color: '#5b9bd5' }, areaStyle: { color: hexA('#5b9bd5', 0.14) } },
    ],
  }))
}

const loadHot = async () => {
  const res = await adminInteractionHot({ limit: 10 })
  if (res?.code !== 200) return
  const rows = res.data || []
  hot.value = rows
  hotChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    legend: { data: ['点赞', '收藏'], textStyle: { color: textColor() } },
    grid: { left: 8, right: 16, top: 36, bottom: 24, containLabel: true },
    xAxis: valueAxis({ minInterval: 1 }),
    yAxis: categoryAxis(rows.slice().reverse().map(i => short(i.title))),
    series: [
      { name: '点赞', type: 'bar', stack: 't', barWidth: 12,
        data: rows.slice().reverse().map(i => i.likes), itemStyle: { color: accent() } },
      { name: '收藏', type: 'bar', stack: 't', barWidth: 12,
        data: rows.slice().reverse().map(i => i.collects), itemStyle: { color: '#9b7bd5' } },
    ],
  }))
}

const short = (t) => {
  const s = String(t || '未知')
  return s.length > 9 ? s.slice(0, 9) + '…' : s
}

const loadList = async () => {
  const res = await adminInteractionHistory({
    page: page.value, size: pageSize.value,
    resourceType: resourceType.value ?? undefined,
    finished: finished.value ?? undefined,
  })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '播放记录加载失败'); return }
  list.value = res.data?.rows || []
  total.value = Number(res.data?.total || 0)
}

const loadAll = async () => {
  loading.value = true
  try { await Promise.all([loadStats(), loadHot(), loadList()]) } finally { loading.value = false }
}

const onFilter = () => { page.value = 1; loadList() }

const doExport = async () => {
  exporting.value = true
  try {
    const res = await adminInteractionHistory({
      page: 1, size: 1000,
      resourceType: resourceType.value ?? undefined,
      finished: finished.value ?? undefined,
    })
    if (res?.code !== 200) { ElMessage.error(res?.msg || '导出失败'); return }
    const r = exportCsv('播放记录', res.data?.rows || [], [
      { label: '时间', field: 'createTime', fmt: fmtTime },
      { label: '用户ID', field: 'userId' },
      { label: '用户名', field: 'userName' },
      { label: '类型', field: 'typeName' },
      { label: '内容标题', field: 'title' },
      { label: '播放进度(%)', field: 'percent' },
      { label: '是否已看完', field: 'finished' },
    ])
    if (r.ok) ElMessage.success(`已导出 ${r.count} 条`)
    else ElMessage.warning(r.msg)
  } finally { exporting.value = false }
}

onMounted(async () => {
  await nextTick()
  loadAll()
})
</script>

<style scoped>
.prog-wrap { display: flex; align-items: center; gap: 8px; }
.prog-bar { flex: 1; height: 6px; background: color-mix(in srgb, var(--c-main) 25%, transparent); border-radius: 3px; overflow: hidden; min-width: 60px; }
.prog-in { height: 100%; background: var(--c-hex, #7bc47b); border-radius: 3px; }
.prog-txt { font-size: 12px; color: #8a9a8a; width: 34px; text-align: right; }
</style>
