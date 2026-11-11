<template>
  <div class="dashboard-wrap">
    <!-- 加载遮罩（绝对定位覆盖，不影响图表 DOM 渲染） -->
    <div v-if="loading" class="loading-mask">
      <CowLoading size="md" text="数据加载中" />
    </div>

    <!-- 错误提示遮罩 -->
    <div v-if="errorMsg" class="loading-mask error-mask">
      <div class="error-icon">!</div>
      <div class="error-text">{{ errorMsg }}</div>
      <button class="retry-btn" @click="loadData">重新加载</button>
    </div>

    <!-- 顶部工具栏 -->
    <div class="dash-toolbar">
      <div class="dash-title">
        <img src="/img/数据大盘.png" class="dash-title-icon" />
        <span>数据大盘</span>
      </div>
      <div class="dash-tools">
        <span class="refresh-time" v-if="lastRefreshTime">上次更新：{{ lastRefreshTime }}</span>
        <button class="jelly-btn primary refresh-btn" @click="loadData" :disabled="loading">
          <span :class="{ 'spin': loading }"><AppIcon name="refresh" /></span>
          {{ loading ? '刷新中...' : '刷新数据' }}
        </button>
      </div>
    </div>

    <!-- 待办区：把「纯展示卡片」变成可点击的行动入口 -->
    <div class="todo-bar">
      <div class="todo-title">待办</div>
      <button class="todo-item" :class="{ alert: risk.unread > 0 }" @click="go('/admin/alerts')">
        <img src="/img/情绪预警后台图标.png" />
        <span class="ti-label">未读预警</span>
        <span class="ti-num">{{ risk.unread }}</span>
      </button>
      <button class="todo-item" :class="{ alert: risk.l3 > 0 }" @click="go('/admin/alerts')">
        <img src="/img/情绪风险预警等级分布.png" />
        <span class="ti-label">三级预警</span>
        <span class="ti-num">{{ risk.l3 }}</span>
      </button>
      <button class="todo-item" :class="{ alert: stats.pendingFeedbacks > 0 }" @click="go('/admin/feedbacks')">
        <img src="/img/用户反馈.png" />
        <span class="ti-label">待回复反馈</span>
        <span class="ti-num">{{ stats.pendingFeedbacks }}</span>
      </button>
      <button class="todo-item" :class="{ alert: stats.pendingComments > 0 }" @click="go('/admin/comments')">
        <img src="/img/评论审核.png" />
        <span class="ti-label">待审核评论</span>
        <span class="ti-num">{{ stats.pendingComments }}</span>
      </button>
      <button class="todo-item" @click="go('/admin/privacy')">
        <img src="/img/无感记录后台图标.png" />
        <span class="ti-label">隐私审计</span>
        <span class="ti-num">查看</span>
      </button>
    </div>

    <!-- 统计卡片：第一行 = 核心功能量 -->
    <div class="stat-grid">
      <div class="stat-card bg-core">
        <div class="s-icon"><img src="/img/用户管理.png" /></div>
        <div class="s-label">用户总数</div>
        <div class="s-value">{{ stats.totalUsers }}</div>
        <div class="s-trend up"><AppIcon name="arrow-up" /> 今日 +{{ stats.newUsersToday }}</div>
      </div>
      <div class="stat-card bg-core">
        <div class="s-icon"><img src="/img/情绪日记.png" /></div>
        <div class="s-label">情绪日记</div>
        <div class="s-value">{{ stats.diaryTotal }}</div>
        <div class="s-trend up"><AppIcon name="edit" /> 手记 {{ stats.diaryManual }} · 推测确认 {{ stats.diaryInferred }}</div>
      </div>
      <div class="stat-card bg-core">
        <div class="s-icon"><img src="/img/测评量表.png" /></div>
        <div class="s-label">测评参与总数</div>
        <div class="s-value">{{ stats.totalTestRecords }}</div>
        <div class="s-trend up"><AppIcon name="arrow-up" /> 在线量表 {{ stats.totalTests }} 套</div>
      </div>
      <div class="stat-card bg-core">
        <div class="s-icon"><img src="/img/AI对话次数.png" /></div>
        <div class="s-label">AI 对话次数</div>
        <div class="s-value">{{ stats.aiChatTotal }}</div>
        <div class="s-trend up"><AppIcon name="bot" /> 绪见 AI 陪伴</div>
      </div>
    </div>

    <!-- 统计卡片：第二行 = 三大扩展模块 + 活跃 -->
    <div class="stat-grid" style="margin-bottom:20px">
      <div class="stat-card bg-ext">
        <div class="s-icon"><img src="/img/无感记录开启人数.png" /></div>
        <div class="s-label">无感记录开启人数</div>
        <div class="s-value">{{ sense.enabledUsers }}</div>
        <div class="s-trend up"><AppIcon name="cloud" /> opt-in 自愿开启</div>
      </div>
      <div class="stat-card bg-ext">
        <div class="s-icon"><img src="/img/无感推测总数.png" /></div>
        <div class="s-label">无感推测总数</div>
        <div class="s-value">{{ sense.predictions }}</div>
        <div class="s-trend up"><AppIcon name="check" /> 用户采用 {{ sense.adopted }} 条 · 命中率 {{ sense.hitRate }}%</div>
      </div>
      <div class="stat-card bg-ext">
        <div class="s-icon"><img src="/img/情绪预警总数.png" /></div>
        <div class="s-label">情绪预警总数</div>
        <div class="s-value">{{ risk.total }}</div>
        <div class="s-trend" :class="risk.unread > 0 ? 'warn' : 'up'">
          <AppIcon :name="risk.unread > 0 ? 'alert-circle' : 'check-circle'" /> 未读 {{ risk.unread }} 条
        </div>
      </div>
      <div class="stat-card bg-ext">
        <div class="s-icon"><img src="/img/用户反馈.png" /></div>
        <div class="s-label">今日活跃用户</div>
        <div class="s-value">{{ stats.activeUsersToday }}</div>
        <div class="s-trend" :class="stats.pendingFeedbacks + stats.pendingComments > 0 ? 'up' : 'down'">
          <AppIcon :name="stats.pendingFeedbacks + stats.pendingComments > 0 ? 'alert-circle' : 'check-circle'" />
          待处理 {{ stats.pendingFeedbacks + stats.pendingComments }} 条
        </div>
      </div>
    </div>

    <!-- 图表区域：用户增长 + 情绪指数双线 -->
    <div class="chart-grid">
      <div class="chart-box">
        <h3><img src="/img/用户活跃趋势.png" class="title-icon" /> 新增用户趋势（近 7 天）</h3>
        <div ref="trendRef" class="chart-elem"></div>
      </div>
      <div class="chart-box">
        <h3><img src="/img/情绪指数趋势（手记 vs 推测确认）.png" class="title-icon" /> 情绪指数趋势（手记 vs 推测确认）</h3>
        <div ref="indexTrendRef" class="chart-elem"></div>
      </div>
    </div>

    <!-- 情绪分布 + 功能占比 -->
    <div class="chart-grid" style="margin-top:20px">
      <div class="chart-box">
        <h3><img src="/img/情绪类型统计.png" class="title-icon" /> 情绪类型统计</h3>
        <div ref="barRef" class="chart-elem"></div>
      </div>
      <div class="chart-box">
        <h3><img src="/img/内容类型分布.png" class="title-icon" /> 功能使用占比</h3>
        <div ref="pieRef" class="chart-elem"></div>
      </div>
    </div>

    <!-- 三大扩展模块图表 -->
    <div class="chart-grid" style="margin-top:20px">
      <div class="chart-box">
        <h3><img src="/img/无感推测·生成与采用（近 7 天）.png" class="title-icon" /> 无感推测·生成与采用（近 7 天）</h3>
        <div ref="senseRef" class="chart-elem"></div>
      </div>
      <div class="chart-box">
        <h3><img src="/img/情绪风险预警等级分布.png" class="title-icon" /> 情绪风险预警等级分布</h3>
        <div ref="riskRef" class="chart-elem"></div>
      </div>
    </div>

    <!-- 内容排行 + 活跃用户 -->
    <div class="chart-grid" style="margin-top:20px">
      <div class="chart-box">
        <h3><img src="/img/心理视频.png" class="title-icon" /> 视频播放排行 TOP5</h3>
        <div ref="videoRankRef" class="chart-elem"></div>
      </div>
      <div class="chart-box">
        <h3><img src="/img/最近活跃用户.png" class="title-icon" /> 最近活跃用户</h3>
        <table class="admin-table active-table">
          <thead>
            <tr><th>用户名</th><th>活动</th><th>次数</th><th>注册时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="(u, i) in recentUsers" :key="i">
              <td><AppIcon name="flower" /> {{ u.username }}</td>
              <td>{{ u.activity }}</td>
              <td>{{ u.count }} 次</td>
              <td style="color:#8a9a8a">{{ fmtTime(u.time).slice(0, 16) }}</td>
            </tr>
            <tr v-if="!recentUsers.length">
              <td colspan="4" style="text-align:center;color:#aaa;padding:20px">暂无活跃用户</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const go = (path) => router.push(path)
import * as echarts from '../../lib/echarts.js'
import { useChart } from '@/composables/useChart'
import { getDashboardStats } from '@/api'
import CowLoading from '@/components/CowLoading.vue'
import { fmtTime } from '@/utils/exportCsv'

const trendRef = ref(null)
const pieRef = ref(null)
const barRef = ref(null)
const videoRankRef = ref(null)
const indexTrendRef = ref(null)
const senseRef = ref(null)
const riskRef = ref(null)
// 统一托管：init / resize / dispose 交给 useChart
const trendChart = useChart(trendRef, 'admin-trend')
const pieChart = useChart(pieRef, 'admin-pie')
const barChart = useChart(barRef, 'admin-bar')
const videoRankChart = useChart(videoRankRef, 'admin-rank')
const indexTrendChart = useChart(indexTrendRef, 'admin-index-trend')
const senseChart = useChart(senseRef, 'admin-sense')
const riskChart = useChart(riskRef, 'admin-risk')

// ===== 主题色：取自全局 CSS 变量，管理员切换主题/深色模式时图表一起变 =====
const cssVar = (name, fallback) => {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name)
    return (v || '').trim() || fallback
  } catch { return fallback }
}
const accent = () => cssVar('--c-hex', '#7bc47b')
const accentSoft = () => cssVar('--c-main', '#90db90')
const textColor = () => cssVar('--c-text', '#556a55')
const weakColor = () => cssVar('--c-text-weak', '#8a9a8a')
const borderColor = () => cssVar('--c-border', 'rgba(184,212,170,0.15)')
const hexA = (hex, a) => {
  const h = (hex || '').replace('#', '')
  if (h.length !== 6) return `rgba(123,196,123,${a})`
  const n = parseInt(h, 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
}

const loading = ref(true)
const errorMsg = ref('')
const lastRefreshTime = ref('')
const stats = ref({
  totalUsers: 0,
  newUsersToday: 0,
  activeUsersToday: 0,
  totalFeedbacks: 0,
  pendingFeedbacks: 0,
  totalComments: 0,
  pendingComments: 0,
  totalTests: 0,
  totalTestRecords: 0,
  videoPlayCount: 0,
  audioPlayCount: 0,
  diaryTotal: 0,
  diaryManual: 0,
  diaryInferred: 0,
  aiChatTotal: 0,
})
// 三大扩展模块：无感记录 / 情绪预警
const sense = ref({ enabledUsers: 0, predictions: 0, adopted: 0, hitRate: 0 })
const risk = ref({ total: 0, unread: 0, l1: 0, l2: 0, l3: 0 })
const recentUsers = ref([])

// resize 由 useChart 全局单例监听统一处理

// 初始化图表（必须等 DOM 渲染好后调用）
const initCharts = () => {
  if (!trendRef.value || !pieRef.value || !barRef.value || !videoRankRef.value) {
    console.warn('[Dashboard] 图表容器未就绪，跳过初始化')
    return
  }

  // 新增用户是整数计数，用柱状图（折线图在 0/1 量级下会被压成一条爬坡线，观感差）
  trendChart.set({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: [], axisLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: textColor() } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: weakColor() } },
    series: [
      { name: '新增用户', type: 'bar', barWidth: 24, data: [],
        label: { show: true, position: 'top', color: textColor() },
        itemStyle: { borderRadius: [8,8,0,0], color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:accent()},{offset:1,color:accentSoft()}]) } },
    ],
  }, { notMerge: true })

  // 情绪指数双线：绿色=用户手记，紫色=系统推测（用户确认后落库）
  indexTrendChart.set({
    tooltip: { trigger: 'axis' },
    legend: { data: ['手记情绪指数', '推测确认指数'], textStyle: { color: textColor() } },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: [], axisLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: textColor() } },
    yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: weakColor() } },
    series: [
      { name: '手记情绪指数', type: 'line', smooth: true, data: [], connectNulls: true,
        itemStyle: { color: accent() }, lineStyle: { width: 3 }, areaStyle: { color: hexA(accent(), 0.12) } },
      { name: '推测确认指数', type: 'line', smooth: true, data: [], connectNulls: true,
        itemStyle: { color: cssVar('--c-s5', '#534AB7') }, lineStyle: { width: 3, type: 'dashed' } },
    ],
  }, { notMerge: true })

  pieChart.set({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center', textStyle: { color: textColor() } },
    series: [{
      name: '功能使用', type: 'pie', radius: ['45%','70%'], center: ['35%','50%'], label: { show: false },
      data: [],
    }],
  }, { notMerge: true })

  barChart.set({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: [], axisLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: textColor() } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: weakColor() } },
    series: [{
      type: 'bar', barWidth: 40,
      itemStyle: { borderRadius: [10,10,0,0], color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:accent()},{offset:1,color:accentSoft()}]) },
      data: [],
    }],
  }, { notMerge: true })

  // 无感推测：柱=生成，折线=采用
  senseChart.set({
    tooltip: { trigger: 'axis' },
    legend: { data: ['生成推测', '用户采用'], textStyle: { color: textColor() } },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: [], axisLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: textColor() } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: weakColor() } },
    series: [
      { name: '生成推测', type: 'bar', barWidth: 18, data: [],
        itemStyle: { borderRadius: [6,6,0,0], color: cssVar('--c-s2', '#378ADD') } },
      { name: '用户采用', type: 'line', smooth: true, data: [],
        itemStyle: { color: accent() }, lineStyle: { width: 3 } },
    ],
  }, { notMerge: true })

  // 预警等级分布：三级配色（黄/橙/红）
  riskChart.set({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: ['一级 提示', '二级 关注', '三级 高度关注'], axisLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: textColor() } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: weakColor() } },
    series: [{
      type: 'bar', barWidth: 44,
      label: { show: true, position: 'top', color: textColor() },
      itemStyle: { borderRadius: [10,10,0,0] },
      data: [
        { value: 0, itemStyle: { color: '#fbbf24' } },
        { value: 0, itemStyle: { color: '#fb923c' } },
        { value: 0, itemStyle: { color: '#ef4444' } },
      ],
    }],
  }, { notMerge: true })

  videoRankChart.set({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 100, right: 30, top: 10, bottom: 20 },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: weakColor() } },
    yAxis: { type: 'category', data: [], inverse: true, axisLine: { lineStyle: { color: borderColor() } }, axisLabel: { color: textColor() } },
    series: [{
      type: 'bar', barWidth: 24,
      itemStyle: { borderRadius: [0,12,12,0], color: new echarts.graphic.LinearGradient(0,0,1,0,[{offset:0,color:accentSoft()},{offset:1,color:accent()}]) },
      label: { show: true, position: 'right', formatter: '{c} 次', color: textColor() },
      data: [],
    }],
  }, { notMerge: true })
}

// ===== 图表数据渲染 ===== 
// 抽成独立函数：数据加载后填充一次，切换主题/深色模式时再重放一次，
// 否则图表颜色是初始化时的快照，换肤后仍是旧色（要刷新页面才变）。
let lastChartData = null

const renderCharts = (d) => {
  if (!d) return
  // 2. 新增用户趋势折线图（注意：这里只更新数据，必须走 merge 模式；
  //    notMerge 会丢弃首次的完整配置，series 丢失 type，在 ECharts 6 下坐标轴渲染直接报 axisBuilder 错误）
  if (d.userTrend && trendChart) {
    trendChart.set({
      xAxis: { data: d.userTrend.map(item => item.date) },
      series: [
        { name: '新增用户', data: d.userTrend.map(item => item.newUsers) },
      ],
    })
  }

  // 3. 功能使用占比饼图（merge 更新 data）
  if (d.usageRatio && pieChart) {
    const ur = d.usageRatio
    pieChart.set({
      series: [{
        data: [
          { value: ur.diaryCount || 0, name: '心情日记', itemStyle: { color: accent() } },
          { value: ur.testRecordCount || 0, name: '测评参与', itemStyle: { color: cssVar('--c-s2', '#378ADD') } },
          { value: ur.aiChatCount || 0, name: 'AI 对话', itemStyle: { color: cssVar('--c-s3', '#BA7517') } },
          { value: ur.videoPlayCount || 0, name: '视频播放', itemStyle: { color: cssVar('--c-s4', '#D4537E') } },
          { value: ur.audioPlayCount || 0, name: '音频播放', itemStyle: { color: cssVar('--c-s5', '#534AB7') } },
          { value: ur.senseCount || 0, name: '无感推测', itemStyle: { color: cssVar('--c-s6', '#1D9E75') } },
        ],
      }],
    })
  }

  // 3.5 情绪指数双线趋势（手记 vs 推测确认）
  if (d.emotionIndexTrend && indexTrendChart) {
    const rows = d.emotionIndexTrend
    const inferredValues = rows.map(i => i.inferred)
    const inferredCount = inferredValues.filter(v => v !== null && v !== undefined).length
    // 推测确认不足两个点时不成线：单个数据点悬在图上像 bug，先从图例和数据里隐藏，
    // 等确认数据积累到 2 天以上再自动显示
    const showInferred = inferredCount >= 2
    indexTrendChart.set({
      legend: { data: showInferred ? ['手记情绪指数', '推测确认指数'] : ['手记情绪指数'], textStyle: { color: textColor() } },
      xAxis: { data: rows.map(i => i.date?.slice(5)) },
      series: [
        { name: '手记情绪指数', data: rows.map(i => i.manual) },
        { name: '推测确认指数', data: showInferred ? inferredValues : [] },
      ],
    })
  }

  // 3.6 无感推测 7 天趋势（生成柱 + 采用线）
  if (d.senseTrend && senseChart) {
    senseChart.set({
      xAxis: { data: d.senseTrend.map(i => i.date?.slice(5)) },
      series: [
        { name: '生成推测', data: d.senseTrend.map(i => i.generated) },
        { name: '用户采用', data: d.senseTrend.map(i => i.adopted) },
      ],
    })
  }

  // 3.7 预警等级分布
  if (d.riskStats && riskChart) {
    riskChart.set({
      series: [{ data: [
        { value: Number(d.riskStats.l1 || 0), itemStyle: { color: '#fbbf24' } },
        { value: Number(d.riskStats.l2 || 0), itemStyle: { color: '#fb923c' } },
        { value: Number(d.riskStats.l3 || 0), itemStyle: { color: '#ef4444' } },
      ] }],
    })
  }

  // 4. 情绪分布柱状图（merge 更新，理由同上）
  if (d.emotionDist && barChart) {
    barChart.set({
      xAxis: { data: d.emotionDist.labels || [] },
      series: [{ data: d.emotionDist.values || [] }],
    })
  }

  // 5. 视频播放排行（merge 更新，理由同上）
  if (d.videoRank && videoRankChart) {
    videoRankChart.set({
      yAxis: { data: d.videoRank.map(v => v.name).reverse() },
      series: [{ data: d.videoRank.map(v => v.value).reverse() }],
    })
  }

}

// 加载统计数据
const loadData = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await getDashboardStats()
    if (res.code === 200 && res.data) {
      const d = res.data

      // 1. 核心指标
      if (d.metrics) {
        stats.value = {
          totalUsers: d.metrics.totalUsers || 0,
          newUsersToday: d.metrics.newUsersToday || 0,
          activeUsersToday: d.metrics.activeUsersToday || 0,
          totalFeedbacks: d.metrics.totalFeedbacks || 0,
          pendingFeedbacks: d.metrics.pendingFeedbacks || 0,
          totalComments: d.metrics.totalComments || 0,
          pendingComments: d.metrics.pendingComments || 0,
          totalTests: d.metrics.totalTests || 0,
          totalTestRecords: d.metrics.totalTestRecords || 0,
          videoPlayCount: d.usageRatio?.videoPlayCount || 0,
          audioPlayCount: d.usageRatio?.audioPlayCount || 0,
          diaryTotal: d.metrics.diaryTotal || 0,
          diaryManual: d.metrics.diaryManual || 0,
          diaryInferred: d.metrics.diaryInferred || 0,
          aiChatTotal: d.metrics.aiChatTotal || 0,
        }
      }

      // 1.5 三大扩展模块：无感记录 / 情绪预警
      if (d.senseStats) {
        sense.value = {
          enabledUsers: Number(d.senseStats.enabledUsers || 0),
          predictions: Number(d.senseStats.predictions || 0),
          adopted: Number(d.senseStats.adopted || 0),
          hitRate: Number(d.senseStats.hitRate || 0),
        }
      }
      if (d.riskStats) {
        risk.value = {
          total: Number(d.riskStats.total || 0),
          unread: Number(d.riskStats.unread || 0),
          l1: Number(d.riskStats.l1 || 0),
          l2: Number(d.riskStats.l2 || 0),
          l3: Number(d.riskStats.l3 || 0),
        }
      }

      // 图表数据统一交给 renderCharts（主题切换重绘复用同一份逻辑）
      lastChartData = d
      renderCharts(d)
      // 6. 最近活跃用户
      if (d.activeUsers) {
        recentUsers.value = d.activeUsers.map(u => ({
          username: u.username,
          activity: u.activity,
          count: u.count,
          time: u.time ? new Date(u.time).toLocaleDateString() : '-',
        }))
      }

      // 记录刷新时间
      const now = new Date()
      lastRefreshTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    } else {
      errorMsg.value = res.msg || '加载数据失败'
    }
  } catch (e) {
    console.warn('加载统计数据失败:', e)
    errorMsg.value = e.response?.data?.msg || e.message || '网络异常，请稍后重试'
  } finally {
    loading.value = false
    // 数据加载完成后容器高度可能变化  补一次 resize
    // （全局监听只在窗口尺寸变化时触发，内容撑高不会被捕获）
    nextTick(() => {
      ;[trendChart, pieChart, barChart, videoRankChart, indexTrendChart, senseChart, riskChart].forEach((c) => c.resize())
    })
  }
}

// 主题 / 深色模式切换：App.vue 已经更新了 CSS 变量，但 ECharts 颜色是初始化时的快照，
// 必须重建配置（重新读取变量）+ 重放当前数据，否则图表要刷新页面才变色。
const onAdminThemeChange = () => {
  initCharts()
  renderCharts(lastChartData)
  nextTick(() => {
    ;[trendChart, pieChart, barChart, videoRankChart, indexTrendChart, senseChart, riskChart].forEach((c) => c.resize())
  })
}

onMounted(async () => {
  await nextTick()
  // 必须先初始化图表（DOM 已经渲染好了），再加载数据
  initCharts()
  loadData()
  window.addEventListener('admin-theme-updated', onAdminThemeChange)
})

onUnmounted(() => {
  window.removeEventListener('admin-theme-updated', onAdminThemeChange)
})

// 卸载时 dispose、窗口 resize 均由 useChart 全局托管
</script>

<style scoped>
.dashboard-wrap {
  position: relative;
  min-height: 400px;
}

/* 顶部工具栏 */
.dash-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 12px 20px;
  background: linear-gradient(135deg, var(--c-surface-solid), var(--c-bg2));
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.dash-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--c-text);
}
.dash-title-icon {
  width: 32px;
  height: 32px;
}
.dash-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}
.refresh-time {
  font-size: 13px;
  color: #8a9a8a;
}
.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
}
.refresh-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
/* 刷新按钮图标旋转（加载动画已被 CowLoading 组件替代） */
@keyframes spin {
  to { transform: rotate(360deg); }
}
.spin {
  display: inline-block;
  animation: spin 1s linear infinite;
}

/* 加载遮罩 - 覆盖在内容上面 */
.loading-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--c-surface);
  backdrop-filter: blur(2px);
  border-radius: 16px;
}

/* 错误遮罩 */
.error-mask {
  background: rgba(255, 250, 250, 0.9);
}
.error-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #ffcdd2;
  color: #c62828;
  font-size: 24px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.error-text {
  color: #c62828;
  font-size: 14px;
  margin-bottom: 16px;
}
.retry-btn {
  padding: 8px 24px;
  border-radius: 20px;
  border: none;
  background: linear-gradient(135deg, var(--c-hex), var(--c-main));
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-hex) 30%, transparent);
  transition: all 0.2s;
}
.retry-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px color-mix(in srgb, var(--c-hex) 40%, transparent);
}

/* 统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  border-radius: 16px;
  padding: 20px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}
.stat-card .s-icon {
  width: 76px;
  height: 76px;
  margin-bottom: 12px;
  border-radius: 24px;
  /* 深色模式下贴纸图直接放在深卡上会发暗，垫一层亮底保证可辨识（浅色模式几乎不可见） */
  background: color-mix(in srgb, var(--c-panel) 82%, transparent);
  box-shadow: inset 0 0 0 1px var(--c-surface-line);
  display: flex;
  align-items: center;
  justify-content: center;
}
.stat-card .s-icon img {
  width: 58px;
  height: 58px;
  object-fit: contain;
}
.stat-card .s-label {
  font-size: 13px;
  color: var(--c-text-weak);
  margin-bottom: 6px;
}
.stat-card .s-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--c-hex);
  line-height: 1.2;
}
.stat-card .s-trend {
  font-size: 12px;
  margin-top: 8px;
}
.stat-card .s-trend.up { color: #4caf50; }
.stat-card .s-trend.down { color: #9e9e9e; }
.stat-card .s-trend.warn { color: #e65100; font-weight: 600; }

/* 统计卡片背景：按行分两组，用主题色实时混合生成 —— 换肤/深色模式自动跟随 */
.bg-core { background: linear-gradient(135deg, color-mix(var(--c-main) 30%, var(--c-panel)), color-mix(var(--c-main) 13%, var(--c-panel))); }
.bg-ext  { background: linear-gradient(135deg, color-mix(var(--c-main) 13%, var(--c-panel)), color-mix(var(--c-main) 4%, var(--c-panel))); }

/* 图表内的活跃用户表格（与 chart-box 等高对齐） */
.active-table { width: 100%; font-size: 13px; }
.active-table th, .active-table td { padding: 8px 10px; text-align: left; }

/* 图表区域 */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.chart-box {
  background: var(--c-surface-solid);
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}
.chart-box h3 {
  font-size: 15px;
  color: var(--c-text);
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart-box .title-icon {
  width: 30px;
  height: 30px;
}
.chart-elem {
  width: 100%;
  height: 280px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-title .title-icon {
  width: 30px;
  height: 30px;
}

@media (max-width: 1200px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
}

/* ===== 待办区 ===== */
.todo-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 18px;
  background: var(--c-surface-solid); border-radius: 16px; padding: 12px 16px; box-shadow: 0 2px 12px var(--c-shadow-soft); }
.todo-title { font-size: 13px; font-weight: 600; color: var(--c-text); margin-right: 4px; }
.todo-item { display: flex; align-items: center; gap: 8px; padding: 7px 14px; border-radius: 20px;
  border: 1px solid color-mix(in srgb, var(--c-main) 40%, transparent); background: var(--c-surface-solid); cursor: pointer; transition: all .18s; font-size: 13px; color: var(--c-text); }
.todo-item img { width: 20px; height: 20px; object-fit: contain; }
/* hover 只加深背景与描边，不改文字颜色（浅底白字对比度差，会显得发白） */
.todo-item:hover { background: color-mix(in srgb, var(--c-main, #90db90) 26%, var(--c-panel)); border-color: var(--c-main, #90db90); transform: translateY(-1px); }
.todo-item .ti-num { font-weight: 700; color: var(--c-text); min-width: 18px; }
.todo-item.alert { border-color: #ef9a9a; background: #fff5f5; }
.todo-item.alert .ti-num { color: #c62828; }
.todo-item.alert:hover { background: #fdecec; border-color: #ef5350; }
@media (max-width: 768px) {
  .todo-bar { gap: 8px; padding: 10px 12px; }
  .todo-item { padding: 6px 10px; font-size: 12px; }
}

</style>
