<template>
  <div :class="['board-page', { embedded }]">
    <!-- ===== 顶部（内嵌时不显示）===== -->
    <AnimeHero v-if="!embedded" glyph="chart-bar" title="我的数据看板" sub="把每天的情绪攒起来，慢慢看清自己的曲线 ">
      <template #right>
        <div class="hero-right">
          <div class="range-switch">
            <button v-for="r in trendRanges" :key="r.days"
                    :class="['range-btn', { on: trendDays === r.days }]"
                    @click="trendDays = r.days">{{ r.label }}</button>
          </div>
          <select v-model="heatYear" class="year-select" @change="loadHeatmap">
            <option v-for="y in yearOptions" :key="y" :value="y">{{ y }} 年</option>
          </select>
          <button class="export-btn" :disabled="exporting" @click="exportBoardCsv" title="导出近一年每日情绪指数为 CSV">
            <template v-if="exporting">导出中…</template><template v-else><AppIcon name="download" /> 导出数据</template>
          </button>
        </div>
      </template>
    </AnimeHero>

    <!-- 内容区：与测试/日记页同宽（横幅通栏，内容 1400 居中） -->
    <div class="board-body">

    <!-- 接口异常时给出可见提示，避免"图表空白但用户不知道为什么" -->
    <div v-if="loadErr" class="board-alert">
      <AppIcon name="alert-triangle" /> 「{{ loadErr }}」加载失败，其余图表不受影响。
      <button class="alert-retry" @click="loadErr = ''; loadAll()">重试</button>
    </div>

    <!-- ===== 概览指标 ===== -->
    <section class="metric-grid">
      <div class="metric-card glass">
        <span class="metric-num">{{ overview.totalDays ?? 0 }}</span>
        <span class="metric-label">记录天数</span>
      </div>
      <div class="metric-card glass">
        <span class="metric-num">{{ overview.currentStreak ?? 0 }}<i class="unit">天</i></span>
        <span class="metric-label">连续打卡</span>
      </div>
      <div class="metric-card glass">
        <span class="metric-num">{{ overview.longestStreak ?? 0 }}<i class="unit">天</i></span>
        <span class="metric-label">最长连续</span>
      </div>
      <div class="metric-card glass">
        <span class="metric-num">{{ overview.avgIndex ?? 0 }}<i class="unit">分</i></span>
        <span class="metric-label">平均情绪指数</span>
      </div>
      <div class="metric-card glass">
        <span class="metric-num">{{ overview.positiveRatio ?? 0 }}<i class="unit">%</i></span>
        <span class="metric-label">积极情绪占比</span>
      </div>
      <div class="metric-card glass">
        <span class="metric-num">{{ overview.testCount ?? 0 }}<i class="unit">次</i></span>
        <span class="metric-label">完成测评</span>
      </div>
    </section>

    <!-- ===== 年度打卡热力图 ===== -->
    <section class="panel glass">
      <div class="panel-head">
        <h2>{{ heatYear }} 年打卡热力图</h2>
        <span class="panel-tip">颜色越深，那天的心情指数越高 · 已记录 {{ heatFilled }} 天</span>
      </div>
      <div ref="heatRef" class="chart heat-chart"></div>
    </section>

    <div class="panel-row">
      <!-- ===== 情绪指数趋势 ===== -->
      <section class="panel glass flex-2">
        <div class="panel-head">
          <h2>情绪指数趋势</h2>
          <span class="panel-tip">近 {{ trendDays }} 天 · 满分 100（开心 100 / 平静 70 / 难过 20）</span>
        </div>
        <div ref="trendRef" class="chart"></div>
      </section>

      <!-- ===== 情绪分布 ===== -->
      <section class="panel glass flex-1">
        <div class="panel-head">
          <h2>情绪分布</h2>
          <span class="panel-tip">共 {{ emotionTotal }} 篇日记</span>
        </div>
        <div ref="pieRef" class="chart"></div>
      </section>
    </div>

    <div class="panel-row">
      <!-- ===== 月度平均 ===== -->
      <section class="panel glass flex-1">
        <div class="panel-head">
          <h2>月度情绪走势</h2>
          <span class="panel-tip">柱=记录天数，线=当月平均指数</span>
        </div>
        <div ref="monthRef" class="chart"></div>
      </section>

      <!-- ===== 测评分数对比 ===== -->
      <section class="panel glass flex-1">
        <div class="panel-head">
          <h2>测评分数变化</h2>
          <span class="panel-tip">{{ testSeries.length ? '按量表分线对比' : '暂无测评记录' }}</span>
        </div>
        <div ref="testRef" class="chart"></div>
      </section>
    </div>

    <div class="panel-row">
      <!-- ===== 睡眠 x 情绪 散点 + 回归线 ===== -->
      <section class="panel glass flex-1">
        <div class="panel-head">
          <h2>睡眠与情绪的关系</h2>
          <span class="panel-tip">{{ scatterTip }}</span>
        </div>
        <div ref="scatterRef" class="chart"></div>
      </section>

      <!-- ===== 未来 7 天预测带 ===== -->
      <section class="panel glass flex-1">
        <div class="panel-head">
          <h2>情绪走势预测</h2>
          <span class="panel-tip">{{ forecastTip }}</span>
        </div>
        <div ref="forecastRef" class="chart"></div>
      </section>
    </div>

    <div class="panel-row">
      <!-- ===== 星期 x 情绪 桑基图 ===== -->
      <section class="panel glass flex-1">
        <div class="panel-head">
          <h2>一周情绪流向</h2>
          <span class="panel-tip">星期 <AppIcon name="arrow-right" /> 情绪 · 线越宽表示这天出现得越多</span>
        </div>
        <div ref="sankeyRef" class="chart sankey-chart"></div>
      </section>

      <!-- ===== 日记关键词词云 ===== -->
      <section class="panel glass flex-1">
        <div class="panel-head">
          <h2>我的心情关键词</h2>
          <span class="panel-tip">近 180 天日记里的高频词</span>
        </div>
        <div ref="wordRef" class="chart"></div>
      </section>
    </div>

    <!-- ===== 无感记录扩展：真实情绪 vs 系统推测 ===== -->
    <div class="panel-row">
      <section class="panel glass flex-2 sense-highlight">
        <div class="panel-head">
          <h2><AppIcon name="edit" /> 手记 vs <AppIcon name="bot" /> 推测</h2>
          <span class="panel-tip">
            实线=你亲手记录的真实情绪，虚线=系统推测后经你确认的记录 · 近 {{ senseDays }} 天
          </span>
        </div>
        <div class="sense-range">
          <button v-for="d in [7, 30, 90]" :key="d"
                  :class="['range-btn', { on: senseDays === d }]"
                  @click="senseDays = d">{{ d }}天</button>
        </div>
        <div ref="dualRef" class="chart"></div>
        <p v-if="!senseTimeline.manualCount && !senseTimeline.inferredCount" class="chart-empty">
          还没有推测记录。开启「无感记录」后，系统会先把推荐填进表单，由你确认后才会计入这条虚线。
        </p>
      </section>

      <section class="panel glass flex-1 sense-highlight">
        <div class="panel-head">
          <h2>推测采用情况</h2>
          <span class="panel-tip">完全一致率 {{ senseAccuracy.hitRate }}%</span>
        </div>
        <div ref="accRef" class="chart"></div>
        <p class="chart-note">「完全一致」= 你最终保存的情绪与系统推荐完全相同</p>
      </section>
    </div>

    <!-- ===== 行为 × 情绪 关联 ===== -->
    <div class="panel-row">
      <section class="panel glass flex-1 sense-highlight">
        <div class="panel-head">
          <h2>停留时长 × 情绪指数</h2>
          <span class="panel-tip">{{ corrDays }} 天内 {{ corrScatter.length }} 个样本点</span>
        </div>
        <div ref="behScatterRef" class="chart"></div>
        <p v-if="!corrScatter.length" class="chart-empty">采集到若干天行为数据后，这里会画出你的「使用时长 <AppIcon name="arrows-horizontal" /> 心情」散点。</p>
      </section>

      <section class="panel glass flex-1 sense-highlight">
        <div class="panel-head">
          <h2>低情绪日 vs 高情绪日</h2>
          <span class="panel-tip">低={{ corrLowDays }} 天 / 高={{ corrHighDays }} 天</span>
        </div>
        <div ref="behRadarRef" class="chart"></div>
        <p v-if="!corrLowDays && !corrHighDays" class="chart-empty">需要有指数 ≤49 或 ≥70 的记录日才能对比。</p>
      </section>
    </div>

    <section class="panel glass sense-highlight">
      <div class="panel-head">
        <h2>活跃时段 × 情绪（出现次数）</h2>
        <span class="panel-tip">横轴=一天 24 小时 · 纵轴=情绪类别 · 颜色越深表示组合出现越多</span>
      </div>
      <div ref="behHeatRef" class="chart beh-heat-chart"></div>
      <p v-if="!corrHeat.length" class="chart-empty">开启行为采集并连续记录几天后即可看到这张图。</p>
    </section>

    <!-- ===== 管理员：全站统计 ===== -->
    <section v-if="isAdmin" class="admin-area">
      <div class="panel-head">
        <h2>全站数据概览（管理员）</h2>
        <span class="panel-tip">来自 /statistics/* 全站统计接口</span>
      </div>
      <div class="metric-grid">
        <div class="metric-card glass"><span class="metric-num">{{ siteOverview.userTotal ?? 0 }}</span><span class="metric-label">注册用户</span></div>
        <div class="metric-card glass"><span class="metric-num">{{ siteOverview.diaryTotal ?? 0 }}</span><span class="metric-label">日记总数</span></div>
        <div class="metric-card glass"><span class="metric-num">{{ siteOverview.testRecordTotal ?? 0 }}</span><span class="metric-label">测评记录</span></div>
        <div class="metric-card glass"><span class="metric-num">{{ siteOverview.chatTotal ?? 0 }}</span><span class="metric-label">AI 对话</span></div>
        <div class="metric-card glass"><span class="metric-num">{{ siteOverview.audioTotal ?? 0 }}</span><span class="metric-label">音频资源</span></div>
        <div class="metric-card glass"><span class="metric-num">{{ siteOverview.videoTotal ?? 0 }}</span><span class="metric-label">视频资源</span></div>
      </div>
      <div class="panel-row">
        <section class="panel glass flex-1">
          <div class="panel-head"><h2>资源分类占比</h2></div>
          <div ref="resRef" class="chart"></div>
        </section>
        <section class="panel glass flex-1">
          <div class="panel-head"><h2>视频播放排行</h2></div>
          <div ref="rankRef" class="chart"></div>
        </section>
      </div>
    </section>

    <!-- ===== 加载中 ===== -->
    <div v-if="loading" class="loading-mask"><div class="loading-text">数据加载中...</div></div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from '../lib/echarts.js'
import 'echarts-wordcloud'   // 词云系列（echarts-wordcloud 2.x，兼容 echarts 5）
import { EMOTIONS } from '@/utils/constants'  // 情绪字典唯一真源
import AnimeHero from '@/components/AnimeHero.vue'
import { useChart } from '@/composables/useChart'
import {
  getMyOverview, getMyTrend, getMyHeatmap, getMyEmotion, getMyTestTrend, getMyMonthly,
  getMyScatter, getMySankey, getMyKeywords,
  getOverview, getResourceStats, getVideoRank,
} from '../api'
import { getSenseTimeline, getSenseAccuracy, getSenseCorrelation } from '../api/sense'
import { useUserStore } from '../stores/user'
import { isAdminFromServer } from '../router'

// embedded = 内嵌在个人中心里（隐藏顶部标题栏、去掉页面留白）
const props = defineProps({ embedded: { type: Boolean, default: false } })

// ===== 主题色：跟随全站 --c-hex / --c-main（与个人中心、FM、日记同一套）=====
const cssVar = (name, fallback) => {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name)
    return (v || '').trim() || fallback
  } catch { return fallback }
}
const accent = () => cssVar('--c-hex', '#7bc47b')
/*
 * 系列配色：第 1 色用主题主色，其余取 --c-s2..--c-s6。
 * 旧实现把 #378ADD/#BA7517/#D4537E 写死在这里，切换主题或进深色模式时
 * 这些系列完全不跟随，且深色底上对比度不足。变量在 App.vue 里按主题定义。
 */
const SERIES_FALLBACK = ['#378ADD', '#BA7517', '#D4537E', '#534AB7', '#1D9E75']
const seriesColor = (i) => (i === 0 ? accent() : cssVar(`--c-s${i + 1}`, SERIES_FALLBACK[i - 1] || SERIES_FALLBACK[0]))
const seriesPalette = () => SERIES_FALLBACK.map((_, i) => seriesColor(i + 1))
/** 第二系列色（散点拟合线、条形高亮等单点使用） */
const S2 = () => cssVar('--c-s2', '#378ADD')
const accentSoft = () => cssVar('--c-main', '#90db90')
const hexA = (hex, a) => {
  const h = (hex || '').replace('#', '')
  if (h.length !== 6) return `rgba(123,196,123,${a})`
  const n = parseInt(h, 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
}

// 情绪字典唯一真源（utils/constants.js），color 字段用于图表配色

const loading = ref(true)

// ===== 加载失败提示（原来 catch 静默吞掉，接口挂了用户完全无感知）=====
const loadErr = ref('')
const failPart = (name) => { if (!loadErr.value) loadErr.value = name }
// 管理员判定以服务端为准：localStorage 里的 role 本地随手就能改，不能拿它决定是否显示管理区（F8）
// 真正的数据接口本来就有服务端鉴权，这里只是避免"篡改本地 role 就看到后台区块"的误判
const isAdmin = ref(false)

const currentYear = new Date().getFullYear()
const yearOptions = [currentYear, currentYear - 1]
const heatYear = ref(currentYear)
const heatFilled = ref(0)

const trendRanges = [{ days: 7, label: '7天' }, { days: 30, label: '30天' }, { days: 90, label: '90天' }]
const trendDays = ref(30)

// 导出近一年每日情绪指数为 CSV（前端生成，带 BOM 避免 Excel 乱码）
const exporting = ref(false)
const exportBoardCsv = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await getMyTrend(365)
    const labels = res?.data?.labels || [], values = res?.data?.values || []
    let csv = '\uFEFF日期,情绪指数\n'
    labels.forEach((l, i) => { csv += `${l},${values[i] ?? ''}\n` })
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `绪见情绪指数_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    loadErr.value = '数据导出'
  } finally { exporting.value = false }
}

// ===== 数据 =====
const overview = ref({})
const emotionTotal = ref(0)
const testSeries = ref([])
const siteOverview = ref({})

// ===== 图表实例（统一交给 useChart 托管：init / resize / dispose 全自动） =====
const heatRef = ref(null), trendRef = ref(null), pieRef = ref(null)
const monthRef = ref(null), testRef = ref(null), resRef = ref(null), rankRef = ref(null)
const scatterRef = ref(null), forecastRef = ref(null), sankeyRef = ref(null), wordRef = ref(null)
const dualRef = ref(null), accRef = ref(null)
const behScatterRef = ref(null), behRadarRef = ref(null), behHeatRef = ref(null)
const heatChart = useChart(heatRef, 'heat', { lazy: true })
const trendChart = useChart(trendRef, 'trend', { lazy: true })
const pieChart = useChart(pieRef, 'pie', { lazy: true })
const monthChart = useChart(monthRef, 'month', { lazy: true })
const testChart = useChart(testRef, 'test', { lazy: true })
const resChart = useChart(resRef, 'res', { lazy: true })
const rankChart = useChart(rankRef, 'rank', { lazy: true })
const scatterChart = useChart(scatterRef, 'scatter', { lazy: true })
const forecastChart = useChart(forecastRef, 'forecast', { lazy: true })
const sankeyChart = useChart(sankeyRef, 'sankey', { lazy: true })
const wordChart = useChart(wordRef, 'word', { lazy: true })
// ===== 无感模块扩展图表（同样交给 useChart 托管） =====
const dualChart = useChart(dualRef, 'sense-dual', { lazy: true })
const accChart = useChart(accRef, 'sense-acc', { lazy: true })
const behScatterChart = useChart(behScatterRef, 'sense-scatter', { lazy: true })
const behRadarChart = useChart(behRadarRef, 'sense-radar', { lazy: true })
const behHeatChart = useChart(behHeatRef, 'sense-heat', { lazy: true })

// 坐标轴文字 / 分隔线 / 浮层配色全部取自主题变量，切主题与深色模式下自动跟随
const AXIS_COLOR = () => cssVar('--c-text-weak', '#8a9a8a')
const SPLIT_COLOR = () => cssVar('--c-border', 'rgba(140,160,140,0.18)')
const TOOLTIP_BG = () => cssVar('--c-panel', '#ffffff')
const TOOLTIP_TEXT = () => cssVar('--c-text', '#3d4d3d')

// ===== 1. 打卡热力图 =====
const loadHeatmap = async () => {
  try {
    const res = await getMyHeatmap(heatYear.value)
    const data = res?.data || {}
    heatFilled.value = data.filledDays || 0
    heatChart.set({
      tooltip: {
        formatter: (p) => `${p.value[0]}<br/>心情指数 <b>${p.value[1]}</b>`,
        backgroundColor: TOOLTIP_BG(),
        borderColor: hexA(accent(), 0.45),
        textStyle: { color: TOOLTIP_TEXT() },
      },
      visualMap: {
        min: 0, max: 100, orient: 'horizontal', left: 'center', top: 4,
        itemWidth: 12, itemHeight: 70,
        textStyle: { color: AXIS_COLOR(), fontSize: 11 },
        inRange: { color: ['#eef3e6', '#c9e3a8', '#97c459', '#639922', '#3b6d11'] },
      },
      calendar: {
        top: 70, left: 40, right: 20, cellSize: ['auto', 18],
        range: String(heatYear.value),
        itemStyle: { color: cssVar('--c-bg1', '#f6f8f3'), borderWidth: 2, borderColor: cssVar('--c-panel', '#fff') },
        splitLine: { show: false },
        yearLabel: { show: false },
        monthLabel: { color: AXIS_COLOR(), fontSize: 11, nameMap: 'ZH' },
        dayLabel: { color: AXIS_COLOR(), fontSize: 10, firstDay: 1, nameMap: ['日', '一', '二', '三', '四', '五', '六'] },
      },
      series: [{
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data: data.data || [],
        itemStyle: { borderRadius: 3 },
      }],
    }, { notMerge: true })
  } catch (e) { failPart('打卡热力图') }
}

// ===== 2. 情绪指数趋势 =====
const loadTrend = async () => {
  try {
    const res = await getMyTrend(trendDays.value)
    const d = res?.data || {}
    trendChart.set({
      tooltip: {
        trigger: 'axis',
        backgroundColor: TOOLTIP_BG(),
        borderColor: hexA(accent(), 0.45),
        textStyle: { color: TOOLTIP_TEXT() },
        formatter: (ps) => {
          // 用 ps[0] 取轴名是安全的，但取值必须遍历：一旦这条折线以后加了
          // 对比系列（如"同期均值"），只读 ps[0] 会静默丢掉其它系列。
          const head = ps[0]?.axisValue ?? ''
          const rows = ps.filter((p) => p.value != null)
            .map((p) => `${p.marker}${p.seriesName || '心情指数'} <b>${Array.isArray(p.value) ? p.value[1] : p.value}</b>`)
          return rows.length ? `${head}<br/>${rows.join('<br/>')}` : `${head}<br/>这天没有记录`
        },
      },
      grid: { top: 24, bottom: 32, left: 16, right: 20, containLabel: true },
      xAxis: {
        type: 'category', data: d.labels || [], boundaryGap: false,
        axisLine: { lineStyle: { color: '#b0c4b0' } },
        axisLabel: { color: AXIS_COLOR(), fontSize: 11, hideOverlap: true },
      },
      yAxis: {
        type: 'value', min: 0, max: 100,
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: AXIS_COLOR(), fontSize: 11 },
        splitLine: { lineStyle: { color: SPLIT_COLOR() } },
      },
      series: [{
        name: '情绪指数', type: 'line', smooth: true, connectNulls: false,
        data: d.values || [], symbol: 'circle', symbolSize: 6,
        itemStyle: { color: accent() },
        lineStyle: { color: accent(), width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: hexA(accent(), 0.35) },
            { offset: 1, color: hexA(accent(), 0.03) },
          ]),
        },
      }],
    }, { notMerge: true })
  } catch (e) { failPart('情绪趋势') }
}

// ===== 3. 情绪分布 =====
const loadEmotion = async () => {
  try {
    const res = await getMyEmotion()
    const d = res?.data || {}
    emotionTotal.value = d.total || 0
    pieChart.set({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
      legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: AXIS_COLOR(), fontSize: 11 } },
      series: [{
        type: 'pie', radius: ['38%', '62%'], center: ['50%', '46%'],
        data: (d.pie || []).map((x, i) => ({
          name: x.name,
          value: x.value,
          itemStyle: { color: EMOTIONS.find(e => e.label === x.name)?.color || '#B2BEC3', borderColor: '#fff', borderWidth: 2 },
        })),
        label: { show: false },
        labelLine: { show: false },
      }],
    }, { notMerge: true })
  } catch (e) { failPart('情绪分布') }
}

// ===== 4. 月度走势 =====
const loadMonthly = async () => {
  try {
    const res = await getMyMonthly(6)
    const d = res?.data || {}
    monthChart.set({
      tooltip: { trigger: 'axis', backgroundColor: TOOLTIP_BG(), textStyle: { color: TOOLTIP_TEXT() } },
      legend: { data: ['平均指数', '记录天数'], bottom: 0, textStyle: { color: AXIS_COLOR(), fontSize: 11 } },
      grid: { top: 20, bottom: 40, left: 16, right: 20, containLabel: true },
      xAxis: { type: 'category', data: d.labels || [], axisLabel: { color: AXIS_COLOR(), fontSize: 11 } },
      yAxis: [
        { type: 'value', min: 0, max: 100, axisLabel: { color: AXIS_COLOR(), fontSize: 11 }, splitLine: { lineStyle: { color: SPLIT_COLOR() } } },
        { type: 'value', min: 0, axisLabel: { color: AXIS_COLOR(), fontSize: 11 }, splitLine: { show: false } },
      ],
      series: [
        { name: '记录天数', type: 'bar', yAxisIndex: 1, data: d.counts || [], itemStyle: { color: accentSoft(), borderRadius: [4, 4, 0, 0] }, barWidth: '40%' },
        { name: '平均指数', type: 'line', smooth: true, data: d.values || [], itemStyle: { color: accent() }, lineStyle: { width: 3 }, symbol: 'circle', symbolSize: 7 },
      ],
    }, { notMerge: true })
  } catch (e) { failPart('月度走势') }
}

// ===== 5. 测评分数变化 =====
const loadTestTrend = async () => {
  try {
    const res = await getMyTestTrend()
    const list = res?.data?.series || []
    testSeries.value = list
    // 汇总所有出现过的日期作为 x 轴
    const dateSet = new Set()
    list.forEach(s => (s.points || []).forEach(p => { if (p.date) dateSet.add(p.date) }))
    const dates = [...dateSet].sort()
    const palette = seriesPalette()
    testChart.set({
      tooltip: { trigger: 'axis', backgroundColor: TOOLTIP_BG(), textStyle: { color: TOOLTIP_TEXT() } },
      legend: { bottom: 0, textStyle: { color: AXIS_COLOR(), fontSize: 11 }, type: 'scroll' },
      grid: { top: 20, bottom: 44, left: 16, right: 20, containLabel: true },
      xAxis: { type: 'category', data: dates, boundaryGap: false, axisLabel: { color: AXIS_COLOR(), fontSize: 11 } },
      yAxis: { type: 'value', min: 0, axisLabel: { color: AXIS_COLOR(), fontSize: 11 }, splitLine: { lineStyle: { color: SPLIT_COLOR() } } },
      series: list.map((s, i) => ({
        name: s.testName,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        itemStyle: { color: palette[i % palette.length] },
        lineStyle: { width: 2.5, color: palette[i % palette.length] },
        data: dates.map(dt => {
          const hit = (s.points || []).find(p => p.date === dt)
          return hit ? hit.score : null
        }),
      })),
    }, { notMerge: true })
  } catch (e) { failPart('测评变化') }
}

// ===== 6. 睡眠 x 情绪 散点 + 最小二乘回归线 =====
const scatterTip = ref('每个点 = 一天的睡眠时长与情绪指数')
const loadScatter = async () => {
  try {
    const res = await getMyScatter(90)
    const pts = res?.data || []
    if (!pts.length) { scatterTip.value = '暂无带睡眠时长的日记数据 · 去「绪见日记」页点「 补睡眠」可批量补录'; return }
    // 最小二乘回归：y = a + b·x
    const n = pts.length
    const sx = pts.reduce((s, p) => s + p.sleep, 0)
    const sy = pts.reduce((s, p) => s + p.score, 0)
    const sxx = pts.reduce((s, p) => s + p.sleep * p.sleep, 0)
    const sxy = pts.reduce((s, p) => s + p.sleep * p.score, 0)
    const b = (n * sxy - sx * sy) / Math.max(1e-9, n * sxx - sx * sx)
    const a = (sy - b * sx) / n
    const xs = pts.map(p => p.sleep)
    const xMin = Math.min(...xs), xMax = Math.max(...xs)
    // 相关系数 r：给面板提示语一个"相关性强度"
    const meanX = sx / n, meanY = sy / n
    const cov = pts.reduce((s, p) => s + (p.sleep - meanX) * (p.score - meanY), 0)
    const vx = pts.reduce((s, p) => s + (p.sleep - meanX) ** 2, 0)
    const vy = pts.reduce((s, p) => s + (p.score - meanY) ** 2, 0)
    const r = vx && vy ? cov / Math.sqrt(vx * vy) : 0
    scatterTip.value = `近 90 天 ${n} 条样本 · 相关系数 r = ${r.toFixed(2)}（${Math.abs(r) < 0.2 ? '几乎无关' : Math.abs(r) < 0.5 ? '弱相关' : '明显相关'}）`
    scatterChart.set({
      tooltip: {
        trigger: 'item',
        formatter: (p) => p.seriesType === 'line'
          ? `回归趋势<br/>睡 ${p.value[0]} 小时 ≈ 指数 ${p.value[1].toFixed(0)}`
          : `${EMOTIONS.find(e => e.val === p.data[2])?.label || '其他'}<br/>睡眠 ${p.value[0]} 小时 · 指数 ${p.value[1]}`,
        backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45), textStyle: { color: TOOLTIP_TEXT() },
      },
      grid: { top: 24, bottom: 34, left: 16, right: 20, containLabel: true },
      xAxis: {
        name: '睡眠(小时)', nameTextStyle: { color: AXIS_COLOR(), fontSize: 11 },
        min: 0, max: 14, type: 'value',
        axisLabel: { color: AXIS_COLOR(), fontSize: 11 },
        splitLine: { lineStyle: { color: SPLIT_COLOR() } },
      },
      yAxis: {
        name: '情绪指数', nameTextStyle: { color: AXIS_COLOR(), fontSize: 11 },
        min: 0, max: 100, type: 'value',
        axisLabel: { color: AXIS_COLOR(), fontSize: 11 },
        splitLine: { lineStyle: { color: SPLIT_COLOR() } },
      },
      series: [
        {
          type: 'scatter', symbolSize: 11,
          data: pts.map(p => [p.sleep, p.score, p.emotion]),
          itemStyle: {
            color: (p) => hexA(EMOTIONS.find(e => e.val === p.value[2])?.color || '#B2BEC3', 0.75),
            borderColor: '#fff', borderWidth: 1,
          },
        },
        {
          name: '回归线', type: 'line',
          data: [[xMin, a + b * xMin], [xMax, a + b * xMax]],
          symbol: 'none', silent: true,
          lineStyle: { color: hexA(accent(), 0.9), width: 2.5, type: 'dashed' },
        },
      ],
    }, { notMerge: true })
  } catch (e) { failPart('睡眠散点图') }
}

// ===== 7. 未来 7 天预测（近 30 天线性回归 + 80% 置信带） =====
const forecastTip = ref('基于近 30 天趋势外推 · 阴影为 80% 置信区间')
const loadForecast = async () => {
  try {
    const res = await getMyTrend(90)
    const d = res?.data || {}
    const labels = d.labels || [], values = d.values || []
    // 取最后 30 天有值的样本做回归
    const pairs = labels.map((l, i) => [l, values[i]]).filter(([, v]) => v != null).slice(-30)
    if (pairs.length < 8) { forecastTip.value = '打卡天数还不够（至少 8 天），攒一攒再来看预测'; return }
    const m = pairs.length
    const xs = pairs.map((_, i) => i)
    const meanX = (m - 1) / 2, meanY = pairs.reduce((s, [, v]) => s + v, 0) / m
    let sxx = 0, sxy = 0
    pairs.forEach(([, v], i) => { sxx += (i - meanX) ** 2; sxy += (i - meanX) * (v - meanY) })
    const bHat = sxx ? sxy / sxx : 0
    const aHat = meanY - bHat * meanX
    let sse = 0
    pairs.forEach(([, v], i) => { sse += (v - (aHat + bHat * i)) ** 2 })
    const sigma = Math.sqrt(sse / Math.max(1, m - 2))
    // 未来 7 天日期标签（与趋势图同为 MM/dd 格式）
    const future = []
    const base = new Date()
    for (let k = 1; k <= 7; k++) {
      const dt = new Date(base.getTime() + k * 86400000)
      future.push(`${String(dt.getMonth() + 1).padStart(2, '0')}/${String(dt.getDate()).padStart(2, '0')}`)
    }
    const allLabels = pairs.map(([l]) => l).concat(future)
    const Z = 1.28   // 80% 置信
    const clamp = (v) => Math.max(0, Math.min(100, v))
    const fitAt = (i) => clamp(aHat + bHat * i)
    const actualData = allLabels.map((_, i) => (i < m ? pairs[i][1] : null))
    const fitData = allLabels.map((_, i) => fitAt(i))
    const lower = allLabels.map((_, i) => (i < m ? null : clamp(fitAt(i) - Z * sigma)))
    const upper = allLabels.map((_, i) => (i < m ? null : clamp(fitAt(i) + Z * sigma)))
    const bandBase = allLabels.map((_, i) => (i < m ? null : clamp(fitAt(i) - Z * sigma)))
    const bandWidth = allLabels.map((_, i) => (i < m ? null : 2 * Z * sigma))
    forecastChart.set({
      tooltip: {
        trigger: 'axis',
        backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45), textStyle: { color: TOOLTIP_TEXT() },
        formatter: (ps) => {
          const rows = ps.filter(p => p.value != null).map(p => `${p.marker}${p.seriesName} <b>${Array.isArray(p.value) ? p.value[1] : p.value}</b>`)
          return `${ps[0]?.axisValue ?? ''}<br/>` + rows.join('<br/>')
        },
      },
      legend: { bottom: 0, textStyle: { color: AXIS_COLOR(), fontSize: 11 } },
      grid: { top: 24, bottom: 44, left: 16, right: 20, containLabel: true },
      xAxis: { type: 'category', data: allLabels, boundaryGap: false, axisLabel: { color: AXIS_COLOR(), fontSize: 10, hideOverlap: true } },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: AXIS_COLOR(), fontSize: 11 }, splitLine: { lineStyle: { color: SPLIT_COLOR() } } },
      series: [
        // 置信带：stack 底 + 宽度，透明填充
        { name: '_bandBase', type: 'line', stack: 'band', data: bandBase, symbol: 'none', silent: true, lineStyle: { width: 0 }, areaStyle: { color: 'transparent' } },
        { name: '置信区间', type: 'line', stack: 'band', data: bandWidth, symbol: 'none', silent: true, lineStyle: { width: 0 }, areaStyle: { color: hexA(accent(), 0.15) } },
        { name: '实际指数', type: 'line', data: actualData, smooth: true, symbol: 'circle', symbolSize: 5, itemStyle: { color: accent() }, lineStyle: { color: accent(), width: 2.5 }, connectNulls: false },
        { name: '趋势拟合', type: 'line', data: fitData, symbol: 'none', silent: true, lineStyle: { color: hexA(S2(), 0.8), width: 2, type: 'dashed' } },
        { name: '下界', type: 'line', data: lower, symbol: 'none', lineStyle: { color: hexA(accent(), 0.35), width: 1, type: 'dotted' } },
        { name: '上界', type: 'line', data: upper, symbol: 'none', lineStyle: { color: hexA(accent(), 0.35), width: 1, type: 'dotted' }, areaStyle: { color: 'transparent' } },
      ],
    }, { notMerge: true })
  } catch (e) { failPart('情绪预测') }
}

// ===== 8. 星期 x 情绪 桑基图 =====
const loadSankey = async () => {
  try {
    const res = await getMySankey(90)
    const d = res?.data || {}
    if (!(d.links || []).length) { sankeyChart.set({ graphic: [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: AXIS_COLOR(), fontSize: 13 } }] }, { notMerge: true }); return }
    sankeyChart.set({
      tooltip: {
        trigger: 'item',
        formatter: (p) => p.dataType === 'edge'
          ? `${p.data.source}  ${p.data.target}<br/><b>${p.data.value}</b> 篇`
          : `${p.name}<br/>共 <b>${p.value}</b> 篇`,
        backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45), textStyle: { color: TOOLTIP_TEXT() },
      },
      series: [{
        type: 'sankey', left: 12, right: 90, top: 12, bottom: 16,
        emphasis: { focus: 'adjacency' },
        nodeWidth: 14, nodeGap: 10, nodeAlign: 'justify',
        data: (d.nodes || []).map(n => ({
          name: n.name,
          itemStyle: { color: EMOTIONS.find(e => e.label === n.name)?.color || accent() },
        })),
        links: d.links || [],
        label: { color: AXIS_COLOR(), fontSize: 11 },
        lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.45 },
      }],
    }, { notMerge: true })
  } catch (e) { failPart('情绪流向图') }
}

// ===== 9. 日记关键词词云 =====
/*
 * echarts-wordcloud@2.1.0 的 peerDependency 是 echarts ^5，本项目用的是 6.1.0。
 * 它靠 `import 'echarts/lib/echarts'` 打到内部注册表，目前能用，但这属于
 * 「官方没承诺」的路径：换依赖版本就可能变成「图表区域一片空白 + 控制台只有
 * 一行看不懂的 warning」。所以这里加一个运行时探针 + 降级方案：
 *   setOption 后若 zrender 里连一个图元都没有  判定词云不可用，
 *   改用词频条形图（同样的数据、同样的信息量），并在标题里说明已降级。
 * 降级只会发生一次（wordCloudBroken 缓存），不影响其它图表。
 */
const WORD_COLORS = ['#FF6B6B', '#4ECDC4', '#95E1D3', '#74B9FF', '#A29BFE', '#FD79A8', '#639922', '#BA7517']
let wordCloudBroken = false

/** 降级方案：词频 Top12 横向条形图（信息量与词云等价，且不依赖第三方布局） */
const renderWordBars = (words) => {
  const top = [...words].slice(0, 12).reverse()
  wordChart.set({
    title: {
      text: '词云组件不可用，已降级为词频条形',
      left: 'center', top: 2,
      textStyle: { fontSize: 11, fontWeight: 'normal', color: AXIS_COLOR() },
    },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45), textStyle: { color: TOOLTIP_TEXT() },
      formatter: (ps) => `${ps[0]?.name ?? ''}<br/>出现 <b>${ps[0]?.value ?? 0}</b> 次`,
    },
    grid: { top: 30, bottom: 10, left: 10, right: 24, containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: AXIS_COLOR(), fontSize: 10 }, splitLine: { lineStyle: { color: SPLIT_COLOR() } } },
    yAxis: {
      type: 'category', data: top.map((w) => w.name),
      axisLabel: { color: AXIS_COLOR(), fontSize: 10 },
      axisLine: { lineStyle: { color: AXIS_COLOR() } },
    },
    series: [{
      type: 'bar', data: top.map((w) => w.value), barMaxWidth: 14,
      itemStyle: { color: S2(), borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 10, color: AXIS_COLOR() },
    }],
  }, { notMerge: true })
}

const loadKeywords = async () => {
  try {
    const res = await getMyKeywords(180)
    const words = res?.data || []
    if (!words.length) {
      wordChart.set({
        series: [{ type: 'bar', data: [] }],
        graphic: [{ type: 'text', left: 'center', top: 'middle', style: { text: '日记再多写几篇，关键词就会长出来 ', fill: AXIS_COLOR(), fontSize: 13 } }],
      }, { notMerge: true })
      return
    }
    if (wordCloudBroken) { renderWordBars(words); return }

    const c = wordChart.set({
      tooltip: {
        formatter: (p) => `${p.name}<br/>出现 <b>${p.value}</b> 次`,
        backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45), textStyle: { color: TOOLTIP_TEXT() },
      },
      series: [{
        type: 'wordCloud',
        shape: 'circle', width: '96%', height: '92%',
        sizeRange: [13, 46], rotationRange: [-45, 45], rotationStep: 45,
        gridSize: 6, drawOutOfBound: false, layoutAnimation: true,
        textStyle: {
          fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif',
          color: () => WORD_COLORS[Math.floor(Math.random() * WORD_COLORS.length)],
        },
        data: words,
      }],
    }, { notMerge: true })

    /* 探针：懒加载时 c 为 null（容器还没进视口），此时无法判断，跳过即可 */
    if (!c) return
    setTimeout(() => {
      let painted = true
      try {
        const list = c.getZr?.().storage?.getDisplayList?.()
        painted = !Array.isArray(list) || list.length > 0
      } catch { painted = true }   // 探测失败就当正常，避免误降级
      if (!painted) {
        wordCloudBroken = true
        console.warn('[词云] wordCloud 系列未注册成功（echarts-wordcloud 与 ECharts 6 不兼容？），已降级为词频条形图')
        renderWordBars(words)
      }
    }, 400)
  } catch (e) { failPart('心情关键词') }
}

// ===== 10. 管理员：全站统计 =====
const loadSite = async () => {
  try {
    const r1 = await getOverview()
    siteOverview.value = r1?.data || {}
  } catch (e) { failPart('全站统计') }
  try {
    const r2 = await getResourceStats()
    const d = r2?.data || {}
    const audio = d.audio || [], video = d.video || []
    resChart.set({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { color: AXIS_COLOR(), fontSize: 11 } },
      series: [
        { name: '音频', type: 'pie', radius: ['20%', '38%'], center: ['50%', '46%'], data: audio, label: { fontSize: 10, color: AXIS_COLOR() } },
        { name: '视频', type: 'pie', radius: ['48%', '64%'], center: ['50%', '46%'], data: video, label: { fontSize: 10, color: AXIS_COLOR() } },
      ],
    }, { notMerge: true })
  } catch (e) { failPart('全站统计') }
  try {
    const r3 = await getVideoRank()
    const list = (r3?.data || []).slice().reverse()
    rankChart.set({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { top: 16, bottom: 24, left: 16, right: 40, containLabel: true },
      xAxis: { type: 'value', axisLabel: { color: AXIS_COLOR(), fontSize: 11 }, splitLine: { lineStyle: { color: SPLIT_COLOR() } } },
      yAxis: { type: 'category', data: list.map(x => x.name), axisLabel: { color: AXIS_COLOR(), fontSize: 11 } },
      series: [{
        type: 'bar', data: list.map(x => x.value), barWidth: '55%',
        itemStyle: { color: S2(), borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', color: AXIS_COLOR(), fontSize: 11 },
      }],
    }, { notMerge: true })
  } catch (e) { failPart('全站统计') }
}

// ==================================================================
// 无感记录扩展可视化
//   红绿约定遵循 A 股习惯，这里不涉及涨跌，统一用：
//   实心=用户亲手确认的真实情绪；虚线=系统推测的来源，颜色同主题色但透明度更低。
// ==================================================================
const senseDays = ref(30)
const senseTimeline = ref({ manualCount: 0, inferredCount: 0 })
const senseAccuracy = ref({ total: 0, hitRate: 0, adopted: 0, rejected: 0, exactHit: 0, byEmotion: [] })

const corrDays = ref(90)
const corrScatter = ref([])
const corrHeat = ref([])
const corrIndicators = ref([])
const corrRadarLow = ref([])
const corrRadarHigh = ref([])
const corrLowDays = ref(0)
const corrHighDays = ref(0)

const EMOTION_LABELS = { 1: '开心', 2: '平静', 3: '疲惫', 4: '难过', 5: '生气', 6: '焦虑', 7: '兴奋', 8: '满足', 9: '其他' }

/** 手动录入 vs 系统推测：两条线，一眼看出系统参与了哪些天 */
const loadSenseTimeline = async () => {
  try {
    const res = await getSenseTimeline(senseDays.value)
    const d = res?.data || {}
    senseTimeline.value = d

    const base = {
      tooltip: {
        trigger: 'axis',
        backgroundColor: TOOLTIP_BG(),
        borderColor: hexA(accent(), 0.45),
        textStyle: { color: TOOLTIP_TEXT() },
      },
      legend: {
        top: 4, itemWidth: 14, itemHeight: 8,
        textStyle: { color: AXIS_COLOR(), fontSize: 11 },
      },
      grid: { left: 44, right: 18, top: 44, bottom: 32, containLabel: true },
      xAxis: {
        type: 'category', data: d.labels || [], boundaryGap: false,
        axisLine: { lineStyle: { color: SPLIT_COLOR() } },
        axisLabel: { color: AXIS_COLOR(), fontSize: 10 },
      },
      yAxis: {
        type: 'value', min: 0, max: 100,
        axisLine: { show: false }, axisTick: { show: false },
        splitLine: { lineStyle: { color: SPLIT_COLOR() } },
        axisLabel: { color: AXIS_COLOR(), fontSize: 10 },
      },
    }

    dualChart.setResponsive((bp) => {
      const narrow = bp === 'XS' || bp === 'SM'
      return {
        ...base,
        legend: { ...base.legend, left: narrow ? 'center' : 'right', top: narrow ? 0 : 4 },
        grid: { ...base.grid, left: narrow ? 8 : 44, top: narrow ? 38 : 44 },
        xAxis: { ...base.xAxis, axisLabel: { ...base.xAxis.axisLabel, rotate: narrow ? 45 : 0, interval: narrow ? 4 : 'auto' } },
        series: [
          {
            name: '我的手记', type: 'line', data: d.manual || [],
            smooth: true, connectNulls: true, symbolSize: 6, z: 3,
            lineStyle: { width: 2.5, color: accent() },
            itemStyle: { color: accent() },
            areaStyle: { color: hexA(accent(), 0.14) },
          },
          {
            name: '系统推测（已采纳）', type: 'line', data: d.inferred || [],
            smooth: true, connectNulls: true, symbolSize: 6, symbol: 'emptyCircle', z: 2,
            lineStyle: { width: 2, type: 'dashed', color: '#8a7fd0' },
            itemStyle: { color: '#8a7fd0' },
          },
        ],
      }
    })
  } catch (e) { failPart('手记/推测对比') }
}

/** 推测采用情况：环形图 */
const loadSenseAccuracy = async () => {
  try {
    const res = await getSenseAccuracy()
    const a = res?.data || {}
    senseAccuracy.value = {
      total: Number(a.total || 0),
      exactHit: Number(a.exactHit || 0),
      adopted: Number(a.adopted || 0),
      rejected: Number(a.rejected || 0),
      hitRate: Math.round(Number(a.hitRate || 0) * 100),
      byEmotion: a.byEmotion || [],
    }
    const s = senseAccuracy.value
    const data = [
      { name: '完全一致', value: s.exactHit, itemStyle: { color: accent() } },
      { name: '采纳但有修改', value: Math.max(0, s.adopted - s.exactHit), itemStyle: { color: hexA(accent(), 0.45) } },
      { name: '忽略', value: s.rejected, itemStyle: { color: '#cbbfd8' } },
    ]
    accChart.setResponsive((bp) => {
      const narrow = bp === 'XS'
      return {
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} 次 ({d}%)',
          backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45),
          textStyle: { color: TOOLTIP_TEXT() },
        },
        legend: { bottom: 0, itemWidth: 12, itemHeight: 8, textStyle: { color: AXIS_COLOR(), fontSize: 11 } },
        series: [{
          type: 'pie', radius: narrow ? ['42%', '66%'] : ['48%', '70%'],
          center: ['50%', narrow ? '44%' : '46%'],
          avoidLabelOverlap: true, padAngle: 2,
          itemStyle: { borderRadius: 6, borderColor: TOOLTIP_BG(), borderWidth: 2 },
          label: { show: !narrow, color: AXIS_COLOR(), fontSize: 11, formatter: '{b}\n{c}' },
          labelLine: { show: !narrow, length: 8, length2: 10 },
          data: data.some(x => x.value > 0) ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: '#e8eee8' } }],
        }],
      }
    })
  } catch (e) { failPart('推测命中率') }
}

/** 行为 × 情绪 关联三件套：散点 / 雷达 / 热力 */
const loadSenseCorrelation = async () => {
  try {
    const res = await getSenseCorrelation(corrDays.value)
    const d = res?.data || {}
    corrScatter.value = d.scatter || []
    corrHeat.value = d.heat || []
    corrIndicators.value = d.indicators || []
    corrRadarLow.value = d.radarLow || []
    corrRadarHigh.value = d.radarHigh || []
    corrLowDays.value = d.lowDays || 0
    corrHighDays.value = d.highDays || 0

    // ---- 散点：停留分钟 vs 情绪指数，颜色按情绪类别 ----
    const pts = (d.scatter || []).map((it) => {
      const [date, minutes, index, emotion] = it
      return {
        value: [Number(minutes) || 0, Number(index) || 0],
        name: `${date} · ${EMOTION_LABELS[emotion] || '未知'}`,
        itemStyle: { color: EMOTIONS.find(e => e.val === Number(emotion))?.bg || '#c9d6e2' },
      }
    })
    behScatterChart.setResponsive((bp) => {
      const narrow = bp === 'XS' || bp === 'SM'
      return {
        tooltip: {
          trigger: 'item',
          formatter: (p) => `${p.name}<br/>停留 ${p.value[0]} 分钟 · 情绪指数 <b>${p.value[1]}</b>`,
          backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45),
          textStyle: { color: TOOLTIP_TEXT() }, confine: true,
        },
        grid: { left: narrow ? 8 : 16, right: 16, top: 20, bottom: narrow ? 8 : 16, containLabel: true },
        xAxis: {
          type: 'value', name: narrow ? '' : '停留(分钟)',
          nameTextStyle: { color: AXIS_COLOR(), fontSize: 10 },
          splitLine: { lineStyle: { color: SPLIT_COLOR() } },
          axisLabel: { color: AXIS_COLOR(), fontSize: 10 },
        },
        yAxis: {
          type: 'value', min: 0, max: 100,
          splitLine: { lineStyle: { color: SPLIT_COLOR() } },
          axisLabel: { color: AXIS_COLOR(), fontSize: 10 },
        },
        series: [{
          type: 'scatter', data: pts, symbolSize: (v) => 8 + Math.min(10, (v[0] || 0) / 6),
          itemStyle: { opacity: 0.82, borderColor: 'rgba(255,255,255,.7)', borderWidth: 1 },
        }],
      }
    })

    // ---- 雷达：低情绪日 vs 高情绪日 的七维行为均值 ----
    const low = d.radarLow || [], high = d.radarHigh || []
    const names = d.indicators || []
    const indicators = names.map((n, i) => {
      const peak = Math.max(Number(low[i]) || 0, Number(high[i]) || 0, 1)
      return { name: n, max: Math.ceil(peak * 1.25 * 10) / 10 }
    })
    behRadarChart.setResponsive(() => ({
      tooltip: { backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45), textStyle: { color: TOOLTIP_TEXT() }, confine: true },
      legend: { bottom: 0, itemWidth: 12, itemHeight: 8, textStyle: { color: AXIS_COLOR(), fontSize: 11 } },
      radar: {
        indicator: indicators,
        radius: '62%', center: ['50%', '48%'],
        splitLine: { lineStyle: { color: SPLIT_COLOR() } },
        axisName: { color: AXIS_COLOR(), fontSize: 10 },
        splitArea: { areaStyle: { color: ['rgba(255,255,255,0)', 'rgba(140,160,140,0.04)'] } },
      },
      series: [{
        type: 'radar', symbolSize: 4,
        data: [
          { name: '低情绪日', value: low, itemStyle: { color: '#7b9ecf' }, areaStyle: { color: 'rgba(123,158,207,0.22)' } },
          { name: '高情绪日', value: high, itemStyle: { color: accent() }, areaStyle: { color: hexA(accent(), 0.20) } },
        ],
      }],
    }))

    // ---- 热力：活跃小时 × 情绪类别 ----
    const hours = Array.from({ length: 24 }, (_, i) => `${i}时`)
    const cats = [1, 2, 3, 4, 5, 6, 7, 8, 9].map(v => EMOTION_LABELS[v])
    const maxCnt = (d.heat || []).reduce((m, it) => Math.max(m, Number(it[2]) || 0), 0)
    behHeatChart.setResponsive((bp) => {
      const narrow = bp === 'XS' || bp === 'SM'
      return {
        tooltip: {
          position: 'top',
          formatter: (p) => `${p.value[0]}时 · ${p.value[1]}<br/>出现 <b>${p.value[2]}</b> 天`,
          backgroundColor: TOOLTIP_BG(), borderColor: hexA(accent(), 0.45),
          textStyle: { color: TOOLTIP_TEXT() }, confine: true,
        },
        grid: { left: narrow ? 8 : 16, right: 16, top: 10, bottom: narrow ? 52 : 60, containLabel: true },
        xAxis: {
          type: 'category', data: hours, splitArea: { show: true },
          axisLabel: { color: AXIS_COLOR(), fontSize: 9, rotate: 0, interval: narrow ? 2 : 1 },
        },
        yAxis: {
          type: 'category', data: cats, splitArea: { show: true },
          axisLabel: { color: AXIS_COLOR(), fontSize: 10 },
        },
        visualMap: {
          min: 0, max: Math.max(1, maxCnt), calculable: true, orient: 'horizontal',
          left: 'center', bottom: 0, itemWidth: 12, itemHeight: 80,
          textStyle: { color: AXIS_COLOR(), fontSize: 10 },
          inRange: { color: ['#f2f6f2', '#cfe3c0', '#9ccb7d', '#62a63c', '#33701a'] },
        },
        series: [{
          type: 'heatmap', data: d.heat || [],
          label: { show: false },
          itemStyle: { borderColor: 'rgba(255,255,255,.6)', borderWidth: 1 },
          emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.2)' } },
        }],
      }
    })
  } catch (e) { failPart('行为关联分析') }
}

// ===== 汇总加载 =====
/**
 * 受控并发执行：同时最多 limit 个任务，单个失败不影响其余。
 * （不引第三方 p-limit，二十行够用，也少一个依赖）
 */
const runLimit = async (tasks, limit = 3) => {
  const queue = [...tasks]
  const workers = Array.from({ length: Math.min(limit, queue.length) }, async () => {
    while (queue.length) {
      const task = queue.shift()
      try { await task() } catch { /* 各任务内部已 catch 并标记 failPart */ }
    }
  })
  await Promise.all(workers)
}

const loadAll = async () => {
  loading.value = true
  loadErr.value = ''
  try {
    const r = await getMyOverview()
    overview.value = r?.data || {}
  } catch (e) { failPart('统计数据') }
  /*
   * 分两批、每批限 3 个并发：原来这里一次 Promise.all 9 个 + 3 个无感请求，
   * 后端冷启动（连接池未热）时首屏会被 20+ 并发请求拖成几秒白屏。
   * 限流后总耗时几乎不变（每个请求都很小），但首屏出图明显更早。
   */
  await runLimit([loadHeatmap, loadTrend, loadEmotion, loadMonthly, loadTestTrend,
                  loadScatter, loadForecast, loadSankey, loadKeywords], 3)
  // 无感扩展：失败不影响原有图表（内部已各自 catch）
  await runLimit([loadSenseTimeline, loadSenseAccuracy, loadSenseCorrelation], 2)
  if (isAdmin.value) {
    await nextTick()
    await loadSite()
  }
  loading.value = false
}

// 窗口尺寸变化由 useChart 的全局单例监听器统一处理（rAF 合并），这里无需再注册

watch(trendDays, () => { loadTrend() })
watch(heatYear, () => { loadHeatmap() })
watch(senseDays, () => { loadSenseTimeline() })

// 主题色变更后图表配色也要跟着变（个人中心改主题会广播该事件）
const onThemeChanged = () => { loadAll() }

onMounted(async () => {
  loadAll()
  window.addEventListener('profile-updated', onThemeChanged)
  // 向服务端确认一次管理员身份（结果 30 秒内缓存，不会每次都请求）
  isAdmin.value = (await isAdminFromServer(useUserStore().token)) === true
})

onBeforeUnmount(() => {
  window.removeEventListener('profile-updated', onThemeChanged)
})

// 卸载时 release 由 useChart 内部 onBeforeUnmount 完成（防止内存泄漏）
</script>

<style scoped>
/* 全站主题变量（App.vue 注入，与个人中心 / FM / 日记同一套） */
.board-page {
  --p-hex: var(--c-hex, #7bc47b);
  --p-main: var(--c-main, #90db90);
  --p-card: #fff;
  --p-border: color-mix(in srgb, var(--p-hex) 22%, #e8efe8);
  --p-text: var(--c-text);
  --p-sub: var(--c-text-weak);
  max-width: 100%;
  margin: 0 auto;
  padding: 0 0 60px;   /* 横幅通栏贴顶，内容宽度由 .board-body 控制 */
  min-height: calc(100vh - 56px);
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  color: var(--p-text);
  background: linear-gradient(135deg, var(--c-bg1, #f6f9f6) 0%, var(--c-bg2, #f0f7f0) 50%, var(--c-bg1, #f6f9f6) 100%);
}
/* 内容区：与其他页面一致的全宽版心（仅留 28px 内边距） */
.board-body {
  width: 100%;
  padding: 0 28px;
  box-sizing: border-box;
}
/* 内嵌在个人中心里：去掉页面留白与自己的背景 */
.board-page.embedded {
  max-width: 100%;
  padding: 0;
  min-height: auto;
  background: transparent;
}
/* 内嵌时内容不再额外居中留白（外层个人中心已有自己的版心） */
.board-page.embedded .board-body { max-width: 100%; padding: 0; }

/* 玻璃卡片（原先 .glass 未定义，导致顶部整块透明无背景） */
.glass {
  background: var(--p-card);
  border: 1px solid var(--p-border);
  border-radius: 18px;
  box-shadow: 0 4px 16px rgba(90, 110, 90, 0.06);
  backdrop-filter: blur(6px);
}

/* 顶部 */
/* 加载失败提示条 */
.board-alert {
  margin: 12px 0 0;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #92400e;
  background: #fff7ed;
  border: 1px solid #fed7aa;
}
.alert-retry {
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid #fdba74;
  background: #fff;
  color: #b45309;
  font-size: 12px;
  cursor: pointer;
}
.alert-retry:hover { background: #ffedd5; }

.board-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px;
  border-radius: 20px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.board-hero h1 { font-size: 24px; font-weight: 700; margin: 0 0 6px; color: var(--p-text); }
.hero-sub { margin: 0; font-size: 14px; color: var(--p-sub); }
.hero-right { display: flex; align-items: center; gap: 12px; }
.range-switch { display: flex; gap: 6px; background: var(--c-bg2, #f0f7f0); padding: 4px; border-radius: 12px; }
.range-btn {
  border: none; background: transparent; padding: 6px 14px; border-radius: 9px;
  font-size: 13px; color: var(--p-sub); cursor: pointer; transition: all .2s;
}
.range-btn:hover { color: var(--p-hex); }
.range-btn.on { background: var(--p-hex); color: #fff; font-weight: 600; box-shadow: 0 2px 8px color-mix(in srgb, var(--p-hex) 35%, transparent); }
.year-select {
  border: 1px solid var(--p-border); background: #fff; border-radius: 10px;
  padding: 7px 10px; font-size: 13px; color: var(--p-text); cursor: pointer;
}
.export-btn {
  border: 1px solid var(--p-border); background: #fff; border-radius: 10px;
  padding: 7px 14px; font-size: 13px; color: var(--p-text); cursor: pointer; transition: all .2s;
}
.export-btn:hover { color: var(--p-hex); border-color: var(--p-hex); }
.export-btn:disabled { opacity: 0.6; cursor: default; }

/* 指标卡 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}
.metric-card {
  background: var(--p-card);
  border-radius: 16px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid var(--p-border);
  box-shadow: 0 4px 16px rgba(90, 110, 90, 0.06);
}
.metric-num { font-size: 28px; font-weight: 700; color: var(--p-hex); line-height: 1.1; }
.metric-num .unit { font-size: 13px; font-weight: 400; color: var(--p-sub); margin-left: 2px; font-style: normal; }
.metric-label { font-size: 13px; color: var(--p-sub); }

/* 面板 */
.panel {
  background: var(--p-card);
  border: 1px solid var(--p-border);
  border-radius: 18px;
  padding: 18px 20px 14px;
  box-shadow: 0 4px 16px rgba(90, 110, 90, 0.06);
}
.panel-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 6px; flex-wrap: wrap; }
.panel-head h2 { font-size: 16px; font-weight: 600; margin: 0; color: var(--p-text); display: flex; align-items: center; gap: 8px; }
.panel-head h2::before { content: ''; width: 4px; height: 15px; border-radius: 3px; background: var(--p-hex); }
.panel-tip { font-size: 12px; color: var(--p-sub); }
.panel-row { display: flex; gap: 16px; margin-top: 16px; flex-wrap: wrap; }
.flex-1 { flex: 1 1 340px; min-width: 300px; }
.flex-2 { flex: 2 1 480px; min-width: 300px; }
.chart { width: 100%; height: 280px; }
.heat-chart { height: 240px; }
.sankey-chart { height: 300px; }

/* ===== 无感记录扩展面板：用淡紫左边框与其它面板区分 ===== */
.sense-highlight { border-left: 3px solid #b7a9e6; }
.sense-highlight .panel-head h2::before { background: #8a7fd0; }
.sense-range { display: flex; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
.sense-range .range-btn {
  border: 1px solid var(--p-border); background: transparent; color: var(--p-sub);
  font-size: 12px; padding: 3px 11px; border-radius: 9px; cursor: pointer;
  font-family: inherit;
}
.sense-range .range-btn.on { background: #8a7fd0; border-color: #8a7fd0; color: #fff; }
.beh-heat-chart { height: 320px; }
.chart-empty {
  margin: 4px 0 0; font-size: 12px; line-height: 1.75; color: var(--p-sub);
  background: rgba(140, 160, 140, 0.07); border-radius: 10px; padding: 9px 11px;
}
.chart-note { margin: 4px 0 0; font-size: 11.5px; color: var(--p-sub); text-align: right; }

.admin-area { margin-top: 28px; padding-top: 18px; border-top: 1px dashed var(--p-border); }

.loading-mask {
  position: fixed; inset: 0; display: flex; align-items: center; justify-content: center;
  background: color-mix(in srgb, var(--c-bg1, #f6f9f6) 70%, transparent); z-index: 20;
}
.loading-text { font-size: 15px; color: var(--p-sub); }
/* 内嵌时遮罩只盖住看板本身，不挡全屏 */
.board-page.embedded { position: relative; }
.board-page.embedded .loading-mask { position: absolute; inset: 0; border-radius: 16px; }

@media (max-width: 768px) {
  .board-page:not(.embedded) { padding: 0 0 40px; }
  .board-body { padding: 0 14px; }
  .chart { height: 240px; }
  /* 无感扩展：窄屏下高度用 clamp() 收一收，避免一屏塞太多图 */
  .beh-heat-chart { height: clamp(240px, 58vw, 300px); }
  .chart-empty, .chart-note { font-size: 11.5px; }
}
</style>
