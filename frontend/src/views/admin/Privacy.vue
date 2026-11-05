<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/隐私审计.png" class="title-icon" /> 隐私审计日志</div>
      <div style="display:flex;gap:10px;align-items:center">
        <button class="jelly-btn ghost" :disabled="exporting" @click="doExport">导出 CSV</button>
        <button class="jelly-btn primary" :disabled="loading" @click="loadAll">{{ loading ? '刷新中...' : '刷新数据' }}</button>
      </div>
    </div>

    <!-- 这一页的定位说明：它是系统未滥用用户数据的可验证凭据 -->
    <div class="ext-note info">
      <b>本页是「用户对自己数据全程可控」的可验证凭据。</b>
      这里只记录用户对本人无感数据做过什么操作（开启 / 关闭 / 确认 / 拒绝 …），
      <b>不记录日记正文、随笔原文与任何行为明细</b>，IP 为脱敏值。
      每一次授权变更都会在此留痕，用户可随时核对系统对自己数据做过什么。
    </div>

    <div class="ext-stat-grid">
      <div class="ext-stat">
        <div class="l"><img src="/img/隐私审计.png" />审计记录总数</div>
        <div class="v">{{ s.total }}</div>
        <div class="s">今日新增 {{ s.today }}</div>
      </div>
      <div class="ext-stat is-blue">
        <div class="l"><img src="/img/隐私审计.png" />涉及用户</div>
        <div class="v">{{ s.userCount }}</div>
        <div class="s">曾开启过无感记录的人数</div>
      </div>
      <div class="ext-stat is-purple">
        <div class="l"><img src="/img/系统参数.png" />开启 / 关闭</div>
        <div class="v">{{ s.grant }} / {{ s.revoke }}</div>
        <div class="s">授权与撤回次数</div>
      </div>
      <div class="ext-stat" :class="{ 'is-red': s.revokeUserCount > 0 }">
        <div class="l"><img src="/img/我的评论.png" />可撤回验证</div>
        <div class="v">{{ s.revokeUserCount }}</div>
        <div class="s">开启后又主动关闭的用户数</div>
      </div>
    </div>

    <div class="ext-chart-grid">
      <div class="ext-box">
        <h3><img src="/img/用户活跃趋势.png" /> 近 14 天隐私操作趋势</h3>
        <div ref="trendRef" class="ext-chart"></div>
      </div>
      <div class="ext-box">
        <h3><img src="/img/内容类型分布.png" /> 操作类型分布</h3>
        <div ref="actionRef" class="ext-chart"></div>
      </div>
    </div>

    <div class="admin-card">
      <div class="ext-filter">
        <el-select v-model="action" placeholder="全部操作类型" clearable @change="onFilter">
          <el-option v-for="a in (s.actionDist || [])" :key="a.action" :label="a.name" :value="a.action" />
        </el-select>
        <button class="jelly-btn ghost tiny" @click="loadAll">重置筛选</button>
        <span style="margin-left:auto;color:#9aa79a;font-size:12.5px">
          共 {{ total }} 条 · detail 字段超 120 字自动截断
        </span>
      </div>

      <div v-if="loading" class="table-loading">加载中...</div>
      <template v-else>
        <table v-if="list.length > 0" class="admin-table">
          <thead>
            <tr>
              <th>时间</th><th>用户</th><th>操作类型</th><th>说明</th><th>脱敏 IP</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in list" :key="l.logId">
              <td style="color:#8a9a8a">{{ fmtTime(l.createTime) }}</td>
              <td>{{ l.userName }}</td>
              <td><span class="status-tag on">{{ l.actionName }}</span></td>
              <td style="max-width:320px">{{ l.detail }}</td>
              <td style="color:#9aa79a">{{ l.ipMasked }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="table-empty">暂无隐私操作记录~</div>
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
import { adminPrivacyStats, adminPrivacyList } from '@/api'
import { useChart } from '@/composables/useChart'
import { accent, textColor, weakColor, hexA, categoryAxis, valueAxis } from '@/utils/adminTheme'
import { exportCsv, fmtTime } from '@/utils/exportCsv'

const s = ref({ total: 0, today: 0, userCount: 0, grant: 0, revoke: 0, revokeUserCount: 0, actionDist: [] })
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const action = ref(null)
const loading = ref(false)
const exporting = ref(false)

const trendRef = ref(null)
const actionRef = ref(null)
const trendChart = useChart(trendRef, 'admin-privacy-trend')
const actionChart = useChart(actionRef, 'admin-privacy-action')

const loadStats = async () => {
  const res = await adminPrivacyStats()
  if (res?.code !== 200) { ElMessage.error(res?.msg || '隐私审计统计加载失败'); return }
  const d = res.data || {}
  s.value = {
    total: Number(d.total || 0), today: Number(d.today || 0),
    userCount: Number(d.userCount || 0), grant: Number(d.grant || 0),
    revoke: Number(d.revoke || 0), revokeUserCount: Number(d.revokeUserCount || 0),
    actionDist: Array.isArray(d.actionDist) ? d.actionDist : [],
  }
  const trend = Array.isArray(d.trend) ? d.trend : []
  trendChart.setResponsive(() => ({
    tooltip: { trigger: 'axis', confine: true },
    grid: { left: 8, right: 14, top: 20, bottom: 24, containLabel: true },
    xAxis: categoryAxis(trend.map(i => String(i.date || '').slice(5))),
    yAxis: valueAxis({ minInterval: 1 }),
    series: [{
      name: '操作次数', type: 'line', smooth: true, symbolSize: 5,
      data: trend.map(i => i.count),
      lineStyle: { width: 2, color: accent() },
      itemStyle: { color: accent() },
      areaStyle: { color: hexA(accent(), 0.16) },
    }],
  }))
  const dist = s.value.actionDist
  actionChart.setResponsive(() => ({
    tooltip: { trigger: 'item', confine: true, formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', bottom: 0, textStyle: { color: textColor() } },
    series: [{
      name: '操作类型', type: 'pie', radius: ['40%', '66%'], center: ['50%', '44%'],
      label: { show: false },
      data: (dist.length ? dist : [{ name: '暂无数据', value: 0 }]).map((i, idx) => ({
        name: i.name, value: i.value,
        itemStyle: { color: ['#7bc47b', '#5b9bd5', '#9b7bd5', '#e59bb3', '#e69b5b', '#5bb8a6', '#d96b7b', '#adb5bd'][idx % 8] },
      })),
    }],
  }))
}

const loadList = async () => {
  const res = await adminPrivacyList({
    page: page.value, size: pageSize.value,
    action: action.value ?? undefined,
  })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '审计日志加载失败'); return }
  list.value = res.data?.rows || []
  total.value = Number(res.data?.total || 0)
}

const loadAll = async () => {
  loading.value = true
  try { await Promise.all([loadStats(), loadList()]) } finally { loading.value = false }
}

const onFilter = () => { page.value = 1; loadList() }

/** 导出当前筛选条件下的全部记录（服务端分页，这里取前 1000 条） */
const doExport = async () => {
  exporting.value = true
  try {
    const res = await adminPrivacyList({ page: 1, size: 1000, action: action.value ?? undefined })
    if (res?.code !== 200) { ElMessage.error(res?.msg || '导出失败'); return }
    const rows = res.data?.rows || []
    const r = exportCsv('隐私审计日志', rows, [
      { label: '时间', field: 'createTime', fmt: fmtTime },
      { label: '用户ID', field: 'userId' },
      { label: '用户名', field: 'userName' },
      { label: '操作类型', field: 'actionName' },
      { label: '说明', field: 'detail' },
      { label: '脱敏IP', field: 'ipMasked' },
    ])
    if (r.ok) ElMessage.success(`已导出 ${r.count} 条`)
    else ElMessage.warning(r.msg)
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await nextTick()
  loadAll()
})
</script>
