<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/测评记录.png" class="title-icon" /> 测评答题记录</div>
      <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat"><div class="l"><img src="/img/测评量表.png" />答题总数</div><div class="v">{{ s.total }}</div><div class="s">全部量表累计</div></div>
      <div class="ext-stat is-blue"><div class="l"><img src="/img/用户管理.png" />参与人数</div><div class="v">{{ s.users }}</div><div class="s">做过至少一次测评</div></div>
      <div class="ext-stat is-purple"><div class="l"><img src="/img/测评记录.png" />平均得分</div><div class="v">{{ s.avgScore }}</div><div class="s">跨量表均值，仅供参考</div></div>
      <div class="ext-stat"><div class="l"><img src="/img/内容类型分布.png" />结果等级数</div><div class="v">{{ levelCount }}</div><div class="s">右侧为等级分布</div></div>
    </div>

    <div class="ext-chart-grid">
      <div class="ext-box">
        <h3><img src="/img/内容类型分布.png" /> 各量表参与次数</h3>
        <div ref="paperRef" class="ext-chart"></div>
      </div>
      <div class="ext-box">
        <h3><img src="/img/测评记录.png" /> 测评结果等级分布</h3>
        <div ref="levelRef" class="ext-chart"></div>
      </div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <div class="admin-search">
          <img src="/img/搜索.png" class="search-icon" />
          <input v-model="keyword" placeholder="搜索用户名..." @input="onSearchInput" />
        </div>
      </div>
      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead><tr><th>用户</th><th>量表</th><th>得分</th><th>等级</th><th>结果建议</th><th>提交时间</th></tr></thead>
          <tbody>
            <tr v-for="r in list" :key="r.recordId">
              <td>{{ r.userName }}</td>
              <td style="max-width:200px">{{ r.testName }}</td>
              <td><b>{{ r.score }}</b><span style="color:#8a9a8a"> / {{ r.totalScore ?? '-' }}</span></td>
              <td><span class="status-tag" :class="r.level && r.level.includes('良') ? 'on' : ''">{{ r.level || '未分级' }}</span></td>
              <td style="max-width:260px;color:#8a9a8a">{{ r.tip || '-' }}</td>
              <td style="color:#8a9a8a">{{ fmt(r.createTime) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无答题记录~</div>
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
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { adminRecordStats, adminRecordList } from '@/api'
import { useChart } from '@/composables/useChart'
import { accent, accentSoft, textColor, weakColor, borderColor, hexA, categoryAxis, valueAxis } from '@/utils/adminTheme'
import * as echarts from '../../lib/echarts.js'

const s = ref({ total: 0, users: 0, avgScore: 0 })
const byLevel = ref({})
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(false)
let searchTimer = null

const paperRef = ref(null)
const levelRef = ref(null)
const paperChart = useChart(paperRef, 'admin-record-paper')
const levelChart = useChart(levelRef, 'admin-record-level')

const levelCount = computed(() => Object.keys(byLevel.value || {}).length)
const fmt = (t) => t ? String(t).replace('T', ' ').slice(0, 16) : '-'
const LEVEL_PALETTE = ['#8ce99a', '#a5d8ff', '#ffd43b', '#ffc9c9', '#d0bfff', '#b2f2bb', '#ffec99']

const loadStats = async () => {
  const res = await adminRecordStats()
  if (res?.code !== 200) { ElMessage.error(res?.msg || '测评统计加载失败'); return }
  const r = res.data || {}
  s.value = { total: Number(r.total || 0), users: Number(r.users || 0), avgScore: Number(r.avgScore || 0) }
  byLevel.value = r.byLevel || {}
  const byPaper = Array.isArray(r.byPaper) ? r.byPaper : []

  paperChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, confine: true },
    grid: { left: 8, right: 14, top: 20, bottom: 24, containLabel: true },
    xAxis: categoryAxis(byPaper.map(i => i.name)),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [{
      type: 'bar', barWidth: 22,
      itemStyle: {
        borderRadius: [8, 8, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: accent() }, { offset: 1, color: accentSoft() },
        ]),
      },
      label: { show: true, position: 'top', color: textColor(), fontSize: 11 },
      data: byPaper.map(i => i.value),
    }],
  }))

  const levels = Object.keys(byLevel.value)
  levelChart.setResponsive(() => ({
    tooltip: { trigger: 'item', confine: true, formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: textColor() } },
    series: [{
      name: '结果等级', type: 'pie', radius: ['42%', '68%'], center: ['50%', '45%'],
      label: { show: false },
      data: levels.map((k, i) => ({
        name: k, value: byLevel.value[k],
        itemStyle: { color: LEVEL_PALETTE[i % LEVEL_PALETTE.length] },
      })),
    }],
  }))
}

const loadList = async () => {
  const res = await adminRecordList({
    page: page.value, size: pageSize.value, keyword: keyword.value.trim() || undefined,
  })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '答题记录加载失败'); return }
  list.value = res.data?.list || []
  total.value = Number(res.data?.total || 0)
}

const loadAll = async () => {
  loading.value = true
  try { await Promise.all([loadStats(), loadList()]) } finally { loading.value = false }
}
const onSearchInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadList() }, 400)
}

onMounted(async () => { await nextTick(); loadAll() })
onBeforeUnmount(() => clearTimeout(searchTimer))
</script>
