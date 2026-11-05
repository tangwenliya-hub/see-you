<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/无感记录后台图标.png" class="title-icon" /> 无感记录管理</div>
      <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
    </div>

    <div class="ext-note info">
      无感记录采用 <b>opt-in 自愿开启</b> 机制，默认全部关闭。后台只呈现聚合统计，
      <b>不展示任何用户的原始行为明细与随笔文本</b>，也不提供按人下钻的行为查询入口。
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat">
        <div class="l"><img src="/img/无感记录开启人数.png" />开启人数</div>
        <div class="v">{{ d.enabledUsers }}</div>
        <div class="s">开启率 {{ d.enableRate }}% · 总用户 {{ d.totalUsers }}</div>
      </div>
      <div class="ext-stat is-blue">
        <div class="l"><img src="/img/无感推测总数.png" />推测总数</div>
        <div class="v">{{ d.total }}</div>
        <div class="s">待确认 {{ d.pending }} · 已忽略 {{ d.ignored }} · 已过期 {{ d.expired }}</div>
      </div>
      <div class="ext-stat">
        <div class="l"><img src="/img/无感记录后台图标.png" />用户采纳率</div>
        <div class="v">{{ d.adoptRate }}%</div>
        <div class="s">已采纳 {{ d.adopted }} 条</div>
      </div>
      <div class="ext-stat is-purple">
        <div class="l"><img src="/img/无感记录后台图标.png" />平均置信度</div>
        <div class="v">{{ d.avgConfidence }}</div>
        <div class="s">词典词条 {{ d.lexiconSize }} 条</div>
      </div>
    </div>

    <div class="ext-chart-grid">
      <div class="ext-box">
        <h3><img src="/img/无感推测·生成与采用（近 7 天）.png" /> 近 14 天推测生成与采用</h3>
        <div ref="trendRef" class="ext-chart"></div>
      </div>
      <div class="ext-box">
        <h3><img src="/img/情绪词典.png" /> 系统推测情绪分布</h3>
        <div ref="distRef" class="ext-chart"></div>
      </div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <el-select v-model="status" placeholder="全部状态" clearable @change="onFilter">
          <el-option label="待确认" :value="0" />
          <el-option label="已采用" :value="1" />
          <el-option label="已忽略" :value="2" />
          <el-option label="已过期" :value="3" />
        </el-select>
        <span style="font-size:12px;color:#8a9a8a">命中 = 用户最终确认的情绪与系统推测一致</span>
      </div>

      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th>用户</th><th>目标日期</th><th>推测情绪</th><th>强度</th><th>置信度</th>
              <th>状态</th><th>最终情绪</th><th>命中</th><th>生成时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in list" :key="p.predictionId">
              <td>{{ p.userName }}</td>
              <td>{{ p.targetDate }}</td>
              <td>{{ p.suggestEmotionName }}</td>
              <td>{{ p.suggestIntensity ?? '-' }}</td>
              <td>{{ conf(p.confidence) }}</td>
              <td><span class="status-tag" :class="statusTone(p.confirmStatus)">{{ p.confirmStatusName }}</span></td>
              <td>{{ p.confirmEmotionName || '-' }}</td>
              <td>
                <span v-if="p.confirmEmotionName" :class="p.hit ? 'status-tag on' : 'status-tag warn'">
                  {{ p.hit ? '命中' : '已修正' }}
                </span>
                <span v-else style="color:#b0bdb0">-</span>
              </td>
              <td style="color:#8a9a8a">{{ fmt(p.createTime) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无推测记录~</div>
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
import { adminSenseStats, adminSensePredictions } from '@/api'
import { useChart } from '@/composables/useChart'
import { accent, textColor, weakColor, borderColor, hexA, EMOTION_COLORS, categoryAxis, valueAxis } from '@/utils/adminTheme'

const d = ref({ totalUsers: 0, enabledUsers: 0, enableRate: 0, total: 0, adopted: 0, ignored: 0, expired: 0, pending: 0, adoptRate: 0, avgConfidence: 0, lexiconSize: 0 })
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref(null)
const loading = ref(false)

const trendRef = ref(null)
const distRef = ref(null)
const trendChart = useChart(trendRef, 'admin-sense-trend')
const distChart = useChart(distRef, 'admin-sense-dist')

const conf = (v) => v == null ? '-' : (Number(v) * 100).toFixed(0) + '%'
const fmt = (t) => t ? String(t).replace('T', ' ').slice(0, 16) : '-'
const statusTone = (s) => ({ 1: 'on', 2: 'danger', 3: 'warn' }[s] || '')

const loadStats = async () => {
  const res = await adminSenseStats()
  if (res?.code !== 200) { ElMessage.error(res?.msg || '无感统计加载失败'); return }
  const r = res.data || {}
  d.value = {
    totalUsers: Number(r.totalUsers || 0), enabledUsers: Number(r.enabledUsers || 0),
    enableRate: Number(r.enableRate || 0), total: Number(r.total || 0),
    adopted: Number(r.adopted || 0), ignored: Number(r.ignored || 0),
    expired: Number(r.expired || 0), pending: Number(r.pending || 0),
    adoptRate: Number(r.adoptRate || 0), avgConfidence: Number(r.avgConfidence || 0),
    lexiconSize: Number(r.lexiconSize || 0),
  }
  const trend = Array.isArray(r.trend) ? r.trend : []
  trendChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', confine: true },
    legend: { data: ['生成推测', '用户采用'], textStyle: { color: textColor() } },
    grid: { left: 8, right: 14, top: 36, bottom: 24, containLabel: true },
    xAxis: categoryAxis(trend.map(i => String(i.date || '').slice(5))),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [
      { name: '生成推测', type: 'bar', barWidth: 14, data: trend.map(i => i.generated), itemStyle: { borderRadius: [6, 6, 0, 0], color: '#93c5fd' } },
      { name: '用户采用', type: 'line', smooth: true, data: trend.map(i => i.adopted), itemStyle: { color: accent() }, lineStyle: { width: 3 } },
    ],
  }))

  const dist = r.emotionDist || {}
  const names = Object.keys(dist)
  distChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, confine: true },
    grid: { left: 8, right: 14, top: 20, bottom: 24, containLabel: true },
    xAxis: categoryAxis(names),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [{
      type: 'bar', barWidth: 18, data: names.map((n, i) => ({
        value: dist[n], itemStyle: { borderRadius: [6, 6, 0, 0], color: EMOTION_COLORS[i % EMOTION_COLORS.length] },
      })),
      label: { show: true, position: 'top', color: textColor(), fontSize: 11 },
    }],
  }))
}

const loadList = async () => {
  const res = await adminSensePredictions({
    page: page.value, size: pageSize.value, status: status.value ?? undefined,
  })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '推测记录加载失败'); return }
  list.value = res.data?.list || []
  total.value = Number(res.data?.total || 0)
}

const loadAll = async () => {
  loading.value = true
  try { await Promise.all([loadStats(), loadList()]) } finally { loading.value = false }
}
const onFilter = () => { page.value = 1; loadList() }

onMounted(async () => { await nextTick(); loadAll() })
</script>
