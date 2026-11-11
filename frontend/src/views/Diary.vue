<template>
  <div class="diary-page">
    <!-- ===== 顶部横幅 ===== -->
    <AnimeHero icon="/img/绪见日记.png" title="绪见日记" sub="记录每一天的心情，温柔对待自己">
      <template #right>
        <div class="hero-right">
          <div class="hero-meta">
            <div class="meta-card">
              <span class="meta-num">{{ diaryList.length }}</span>
              <span class="meta-label">已记录</span>
            </div>
            <div class="meta-card">
              <span class="meta-num">{{ streakDays }}</span>
              <span class="meta-label">连续天数</span>
            </div>
            <div class="meta-card">
              <img class="meta-emoji" :src="topEmotion.icon" alt="" />
              <span class="meta-emotion-name">{{ topEmotion.label }}</span>
              <span class="meta-label">本周主打</span>
            </div>
          </div>
          <button class="write-fab ghost-fab" @click="sleepBackfillVisible = true" title="批量补录近 7 天昨晚睡眠（数据看板散点用）"><AppIcon name="moon" /> 补睡眠</button>
          <button class="write-fab ghost-fab" @click="exportDiaryCsv" title="导出全部日记为 CSV"><AppIcon name="download" /> 导出</button>
          <button class="write-fab" @click="scrollToWrite"><img class="fab-icon" src="/img/写日记.png" alt="" /> 写日记</button>
        </div>
      </template>
    </AnimeHero>
    
    <!-- ===== 双栏：情绪统计 + 今日小结 ===== -->
    <section class="top-grid">
      <!-- 左：情绪统计图 -->
      <div class="chart-card glass sticky-note note-beige">

        <div class="card-head">
          <h3><img class="card-icon" src="/img/本月情绪趋势.png" alt="" /> 本月情绪趋势</h3>
          <div class="chart-tabs">
            <button :class="['tab', {on: chartRange==='week'}]" @click="chartRange='week'; renderChart()">本周</button>
            <button :class="['tab', {on: chartRange==='month'}]" @click="chartRange='month'; renderChart()">本月</button>
            <button :class="['tab', {on: chartRange==='all'}]" @click="chartRange='all'; renderChart()">全部</button>
          </div>
        </div>
        <div ref="chartRef" class="chart-box"></div>
        <div class="chart-legend">
          <span class="lg-item"><span class="lg-dot" style="background:#b8d49a"></span>积极</span>
          <span class="lg-item"><span class="lg-dot" style="background:#f8c9d8"></span>消极</span>
          <span class="lg-item"><span class="lg-dot" style="background:#a8d4f0"></span>中性</span>
        </div>
        
        <!-- 情绪分布 + 月度小结 -->
        <div class="chart-bottom">
          <div class="emotion-dist">
            <div class="dist-title">情绪分布</div>
            <div class="dist-bars">
              <div v-for="e in emotions" :key="e.val" class="dist-row">
                <img class="dist-emoji" :src="e.icon" alt="" />
                <span class="dist-label">{{ e.label }}</span>
                <div class="dist-bar-bg">
                  <div class="dist-bar-fill" :style="{ width: emotionDistPct(e.val) + '%', background: e.bg }"></div>
                </div>
                <span class="dist-num">{{ emotionDistCount(e.val) }}</span>
              </div>
            </div>
          </div>
          <div class="month-summary">
            <div class="dist-title"><img class="card-icon sm" src="/img/9月小结.png" alt="" /> {{ calMonth }}月小结</div>
            <div class="sum-stats">
              <div class="stat-item">
                <span class="stat-num">{{ monthCount }}</span>
                <span class="stat-lbl">本月记录</span>
              </div>
              <div class="stat-item">
                <span class="stat-num pos">{{ monthPosRate }}%</span>
                <span class="stat-lbl">积极占比</span>
              </div>
              <div class="stat-item">
                <img class="stat-emoji" :src="topEmotionMonth.icon" alt="" />
                <span class="stat-emotion-name">{{ topEmotionMonth.label }}</span>
                <span class="stat-lbl">主打情绪</span>
              </div>
            </div>
            <p class="month-text">{{ monthSummary }}</p>

            <!-- AI 寄语（嵌在月度小结里） -->
            <div class="chart-ai-whisper">
              <img class="ai-avatar" src="/img/AI悄悄话头像.jpg" alt="" />
              <p class="ai-text">{{ aiWhisper }}</p>
              <button class="ai-refresh" @click="refreshWhisper">换一条</button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右：今日小结 + 日历打卡 -->
      <div class="side-col">
       <!-- 锚点导航（tab 切换）===== -->
        <nav class="anchor-nav glass sticky-note note-yellow">
          <a :class="['an-btn', {on: diaryTab==='summary'}]" @click="diaryTab='summary'"><img class="card-icon" src="/img/今日小结.png" alt="" />今日小结</a>
          <a :class="['an-btn', {on: diaryTab==='calendar'}]" @click="diaryTab='calendar'"><img class="card-icon" src="/img/本月打卡.png" alt="" />本月打卡</a>
          <a :class="['an-btn', {on: diaryTab==='book'}]" @click="diaryTab='book'"><img class="card-icon" src="/img/我的日记本.png" alt="" />我的日记</a>
        </nav>
        <!-- 今日小结 -->
        <div v-if="diaryTab==='summary'" class="summary-card glass sticky-note note-pink">

          <div class="card-head">
            <h3><img class="card-icon" src="/img/今日小结.png" alt="" /> 今日小结</h3>
            <span class="today-chip-sm">{{ today }}</span>
          </div>
          <template v-if="todayDiary">
            <div class="sum-head">
              <img class="sum-emoji" :src="getEmotionIcon(todayDiary.emotion)" alt="" />
              <div class="sum-tags">
                <span class="tag" :style="{background:getEmotionBg(todayDiary.emotion)}">{{ getEmotionLabel(todayDiary.emotion) }}</span>
                <span v-if="todayDiary.intensity" class="tag intensity-tag">{{ getIntensityLabel(todayDiary.intensity) }}</span>
                <span v-if="isAutoFilled(todayDiary)" class="tag auto-tag"><AppIcon name="moon" /> 系统代记</span>
                <span v-for="k in extractKeywords(todayDiary.diaryContent)" :key="k" class="tag kw">#{{ k }}</span>
              </div>
            </div>
            <p class="sum-text">{{ todayDiary.diaryContent }}</p>
            <img v-if="todayDiary.imageUrl" :src="todayDiary.imageUrl" class="sum-img" alt="" />
            <!-- 代记的意义是「不开 App 也不断签」，但不能让它一直是机器写的 -->
            <div v-if="isAutoFilled(todayDiary)" class="sum-auto-note">
              <span>今天你没打开也没关系，系统按使用痕迹替你记了一条，连续打卡不会断。</span>
              <button class="sum-auto-btn" @click="editDiary(todayDiary)"><AppIcon name="pen" /> 补一句，变成我的日记</button>
            </div>
          </template>
          <div v-else class="sum-empty">
            <p>今天还没有记录哦~</p>
            <p class="sum-hint">写下第一篇，让心情被温柔接住 <AppIcon name="heart" /></p>
          </div>
        </div>

        <!-- 日历打卡 -->
        <div v-if="diaryTab==='calendar'" class="calendar-card glass sticky-note note-blue">

          <div class="card-head">
            <h3><img class="card-icon" src="/img/本月打卡.png" alt="" /> 本月打卡</h3>
            <div class="cal-nav">
              <!-- 年份选择（当前年±10） -->
              <select v-model.number="calYear" class="cal-picker">
                <option v-for="y in yearOptions" :key="y" :value="y">{{ y }}年</option>
              </select>
              <!-- 月份选择 1~12 -->
              <select v-model.number="calMonth" class="cal-picker">
                <option v-for="m in 12" :key="m" :value="m">{{ m }}月</option>
              </select>
              <!-- 回到今天 -->
              <button class="cal-today-btn" @click="jumpToToday">今天</button>
            </div>
          </div>
          <div class="cal-week">
            <span v-for="w in ['日','一','二','三','四','五','六']" :key="w">{{ w }}</span>
          </div>
          <div class="cal-grid">
            <div
              v-for="(d, i) in calCells"
              :key="i"
              :class="['cal-cell', {today: d.isToday, checked: d.hasDiary, other: !d.inMonth}]"
              :style="d.hasDiary ? {background: getEmotionBg(d.emotion)} : {}"
              @click="d.dateStr && openDiaryOfDay(d)"
            >
              <span v-if="d.dateStr" class="cal-num">{{ d.day }}</span>
              <img v-if="d.hasDiary" class="cal-dot" :src="getEmotionIcon(d.emotion)" alt="" />
            </div>
          </div>
        </div>
        
             <!-- 我的日记本（长方形本子风）===== -->
        <section v-if="diaryTab==='book'" class="book-area side-book">
          <div class="notebook">
            <!-- 装订线圈 -->
            <div class="nb-binding">
              <span v-for="i in 10" :key="i" class="nb-ring"></span>
            </div>
            <!-- 本子主体 -->
            <div class="nb-body">
              <!-- 本子封面头 -->
              <div class="nb-cover">
                <span class="nb-title"><img class="card-icon" src="/img/我的日记本.png" alt="" />我的日记本</span>
                <span class="nb-count"><img class="card-icon" src="/img/写日记.png" alt="" />{{ diaryList.length }} 篇</span>
              </div>
                           <!-- 横线纸内页（翻页）-->
              <div class="nb-pages">
                <div v-if="diaryList.length === 0" class="nb-empty">
                  <p>还没有写过日记哦~</p>
                  <p class="nb-empty-hint">写下第一篇吧 <AppIcon name="heart" /></p>
                </div>
                <template v-else>
                  <div class="nb-entry page-current" :key="currentDiary?.diaryId" @click="openDiaryOfDay(currentDiary?.diaryDate)">
                    <div class="nb-entry-head">
                      <span class="nb-entry-date">{{ currentDiary?.diaryDate }}</span>
                      <img class="nb-entry-emoji" :src="getEmotionIcon(currentDiary?.emotion)" alt="" />
                      <span class="nb-entry-mood" :style="{background:getEmotionBg(currentDiary?.emotion)}">{{ getEmotionLabel(currentDiary?.emotion) }}</span>
                      <span v-if="isAutoFilled(currentDiary)" class="nb-auto-tag"><AppIcon name="moon" /> 代记</span>
                    </div>
                    <p class="nb-entry-text">{{ currentDiary?.diaryContent || '' }}</p>
                    <img v-if="currentDiary?.imageUrl" :src="currentDiary?.imageUrl" class="nb-entry-img" alt="" />
                  </div>
                  <!-- 翻页控件 -->
                  <div class="nb-pager">
                    <button class="nb-page-btn" :disabled="diaryPageIndex >= diaryList.length - 1" @click="diaryPageIndex++"><AppIcon name="arrow-left" /> 上一篇</button>
                    <span class="nb-page-num">{{ diaryList.length - diaryPageIndex }} / {{ diaryList.length }}</span>
                    <button class="nb-page-btn" :disabled="diaryPageIndex <= 0" @click="diaryPageIndex--">下一篇 <AppIcon name="arrow-right" /></button>
                  </div>
                </template>
              </div>
              </div>
            </div>
        </section>
          </div>
        </section>

    <!-- ===== 查看某天日记弹窗 ===== -->
    <div v-if="dayDiaryTarget" class="modal-mask" @click.self="dayDiaryTarget = null">
      <div class="modal-card glass">
        <h3>{{ dayDiaryTarget.diaryDate }} · {{ getWeekday(dayDiaryTarget.diaryDate) }}</h3>
        <div class="day-diary">
          <img class="sum-emoji" :src="getEmotionIcon(dayDiaryTarget.emotion)" alt="" />
          <span class="tag" :style="{background:getEmotionBg(dayDiaryTarget.emotion)}">{{ getEmotionLabel(dayDiaryTarget.emotion) }}</span>
          <span v-if="dayDiaryTarget.intensity" class="tag intensity-tag">{{ getIntensityLabel(dayDiaryTarget.intensity) }}</span>
          <span v-if="isAutoFilled(dayDiaryTarget)" class="tag auto-tag"><AppIcon name="moon" /> 系统代记</span>
        </div>
        <p class="sum-text">{{ dayDiaryTarget.diaryContent }}</p>
        <img v-if="dayDiaryTarget.imageUrl" :src="dayDiaryTarget.imageUrl" class="sum-img" alt="" />
        <div class="modal-actions">
          <button class="save-btn" @click="editDiary(dayDiaryTarget); dayDiaryTarget = null"><template v-if="isAutoFilled(dayDiaryTarget)"><AppIcon name="pen" /> 补一句</template><template v-else><AppIcon name="pen" /> 编辑</template></button>
          <button class="cancel-btn danger-btn" @click="delDiary(dayDiaryTarget)"><AppIcon name="trash" /> 删除</button>
          <button class="cancel-btn" @click="dayDiaryTarget = null">关闭</button>
        </div>
      </div>
    </div>

    <!-- ===== 近 7 天睡眠补录 ===== -->
    <SleepBackfill v-model:show="sleepBackfillVisible" />

    <!-- ===== 编辑弹窗 ===== -->
    <div v-if="editTarget" class="modal-mask" @click.self="editTarget = null">
      <div class="modal-card glass">
        <h3><AppIcon name="pen" /> {{ isAutoFilled(editTarget) ? '把这条变成我的日记' : '编辑日记' }}</h3>
        <p v-if="isAutoFilled(editTarget)" class="edit-auto-hint">
          这条是系统按使用痕迹代记的。写上你的真实感受并保存，它就会变成为你亲笔记录，同时获得经验值。
        </p>
        <!-- 编辑时显示图片 -->
        <div v-if="editTarget.imageUrl" class="edit-img-wrap">
          <img :src="editTarget.imageUrl" alt="日记配图" class="edit-img" />
        </div>
        <div class="textarea-wrap">
          <textarea v-model="editContent" rows="5" maxlength="300" placeholder="修改你的日记..."></textarea>
          <span class="char-count">{{ editContent.length }}/300</span>
        </div>
        <!-- 补记昨晚睡眠（生活因子，喂给数据看板「睡眠 × 情绪」散点） -->
        <div class="edit-sleep-row">
          <span class="es-label"><AppIcon name="moon" /> 昨晚睡眠</span>
          <input v-model.number="editSleep" type="number" min="0" max="24" step="0.5" placeholder="不填" class="es-input" />
          <span class="es-unit">小时</span>
          <span class="es-tip">可选，用于睡眠与情绪分析</span>
        </div>
        <div class="modal-actions">
          <button class="cancel-btn" @click="editTarget = null">取消</button>
          <button class="save-btn" @click="confirmEdit"><AppIcon name="save" /> 保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { EMOTIONS as emotions } from '@/utils/constants'  // 情绪字典唯一真源（utils/constants.js）
import * as echarts from '../lib/echarts.js'
import { useChart } from '@/composables/useChart'
import AnimeHero from '@/components/AnimeHero.vue'
import SleepBackfill from '@/components/SleepBackfill.vue'
import { getDiaryList, saveDiary as apiSaveDiary, updateDiary as apiUpdateDiary, claimDiary as apiClaimDiary, deleteDiary as apiDeleteDiary, generateWhisper, uploadFile, fetchAllPages } from '../api'
import request from '../api'

// 导出我的全部日记为 CSV（后端生成带 BOM 的 Excel 友好文件）
const exporting = ref(false)
const exportDiaryCsv = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await request.get('/diary/export', { responseType: 'blob' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `绪见日记导出_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.warn('[导出] 失败', e)
  } finally { exporting.value = false }
}
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

// ===== 情绪配置 =====
const getEmotionIcon = (v) => emotions.find(e => e.val === v)?.icon || ''
const getEmotionBg = (v) => emotions.find(e => e.val === v)?.bg || '#e0f5df'
const getEmotionLabel = (v) => emotions.find(e => e.val === v)?.label || '未知'
const getIntensityLabel = (v) => ({ 1: '轻度', 2: '中度', 3: '重度' }[v] || '')

// ===== 情绪极性（用于图表，9种情绪全覆盖）=====
const positiveEmos = [1, 2, 7, 8]  // 开心 平静 兴奋 满足  积极
const neutralEmos = [3, 6, 9]      // 疲惫 焦虑 其他  中性
const negativeEmos = [4, 5]        // 难过 生气  消极

// ===== AI 寄语库 =====
const whisperPool = {
  good: ['今天的心情被温柔接住啦 ', '你值得所有美好，慢慢来也没关系 ', '愿今天的阳光都为你而洒 '],
  calm: ['平静是一种难得的力量 ', '呼吸放缓，世界会变柔软 ', '在喧嚣中守住自己的节奏 '],
  tired: ['今天辛苦了，好好休息是对自己最好的奖励 ', '累了就停下来，你不是机器 ', '疲惫是身体在提醒你要好好爱自己 '],
  sad: ['眼泪是情绪在洗澡，洗完就会好起来的 ', '难过的时候不要一个人扛，说出来会轻一点 ', '黑夜会过去，你值得被拥抱 '],
  angry: ['深呼吸三分钟，世界会安静下来 ', '生气时别做决定，先喝杯温水 ', '情绪没有对错，重要的是如何与之相处 '],
  anxious: ['焦虑是因为你想变得更好，谢谢你的认真 ', '一步一步来，不必什么都今天做完 ', '把大目标拆小，每一小步都算数 '],
  default: ['记录心情本身就是一种疗愈 ', '每一天都是独一无二的礼物 ', '你已经做得很好了，继续加油 ']
}

// ===== 状态 =====
const diaryTab = ref('summary')
const diaryList = ref([])
const diaryPageIndex = ref(0)
const currentDiary = computed(() => diaryList.value[diaryPageIndex.value] || null)
const form = ref({ content: '', emotion: 1, imageUrl: '' })
const flippedId = ref(null)
const fanY = ref(0)
const dragStartX = ref(0)
const dragging = ref(false)
const editTarget = ref(null)
const editContent = ref('')
const editSleep = ref('')   // 编辑时可补记昨晚睡眠（小时）
const sleepBackfillVisible = ref(false)   // 近 7 天睡眠批量补录弹窗
// 新增
const chartRef = ref(null)
// 图表实例交给 useChart 托管（统一 init / resize / dispose）
const chart = useChart(chartRef, 'diary-trend')

// ===== 主题色：跟随全站 --c-hex / --c-main，切主题后图表颜色一起变 =====
const cssVar = (name, fallback) => {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name)
    return (v || '').trim() || fallback
  } catch { return fallback }
}
const accent = () => cssVar('--c-hex', '#7bc47b')
const accentSoft = () => cssVar('--c-main', '#90db90')
const hexA = (hex, a) => {
  const h = (hex || '').replace('#', '')
  if (h.length !== 6) return `rgba(123,196,123,${a})`
  const n = parseInt(h, 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
}
const chartRange = ref('month')  // week / month / all
const calYear = ref(new Date().getFullYear())
const calMonth = ref(new Date().getMonth() + 1)
const dayDiaryTarget = ref(null)
const aiWhisper = ref('')

// ===== 计算 =====
const today = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
})

const topEmotion = computed(() => {
  const emoCount = {}
  diaryList.value.forEach(d => { emoCount[d.emotion] = (emoCount[d.emotion] || 0) + 1 })
  let top = 0, max = 0
  Object.entries(emoCount).forEach(([k, v]) => { if (v > max) { max = v; top = Number(k) } })
  return emotions.find(e => e.val === top) || emotions[0]
})

const streakDays = computed(() => {
  if (diaryList.value.length === 0) return 0
  const dates = new Set(diaryList.value.map(d => d.diaryDate))
  let count = 0
  const d = new Date()
  while (dates.has(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`)) {
    count++; d.setDate(d.getDate() - 1)
  }
  return count
})

// ===== 今日日记 =====
const todayStr = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const todayDiary = computed(() => diaryList.value.find(d => d.diaryDate === todayStr()) || null)

// ===== 日历网格 =====
const calCells = computed(() => {
  const firstDay = new Date(calYear.value, calMonth.value - 1, 1).getDay()
  const daysInMonth = new Date(calYear.value, calMonth.value, 0).getDate()
  const cells = []
  const todayS = todayStr()
  const diaryMap = {}
  diaryList.value.forEach(d => { diaryMap[d.diaryDate] = d.emotion })

  // 上月填充
  const prevDays = new Date(calYear.value, calMonth.value - 1, 0).getDate()
  for (let i = firstDay - 1; i >= 0; i--) {
    const day = prevDays - i
    const prevM = calMonth.value - 1
    const y = prevM === 0 ? calYear.value - 1 : calYear.value
    const m = prevM === 0 ? 12 : prevM
    const dateStr = `${y}-${String(m).padStart(2,'0')}-${String(day).padStart(2,'0')}`
    cells.push({ day, inMonth: false, dateStr, hasDiary: !!diaryMap[dateStr], emotion: diaryMap[dateStr] || 0, isToday: false })
  }
  // 当月
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = `${calYear.value}-${String(calMonth.value).padStart(2,'0')}-${String(d).padStart(2,'0')}`
    cells.push({ day: d, inMonth: true, dateStr, hasDiary: !!diaryMap[dateStr], emotion: diaryMap[dateStr] || 0, isToday: dateStr === todayS })
  }
  // 下月填充
  const remain = 42 - cells.length
  for (let d = 1; d <= remain; d++) {
    const nextM = calMonth.value + 1
    const y = nextM === 13 ? calYear.value + 1 : calYear.value
    const m = nextM === 13 ? 1 : nextM
    const dateStr = `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`
    cells.push({ day: d, inMonth: false, dateStr, hasDiary: !!diaryMap[dateStr], emotion: diaryMap[dateStr] || 0, isToday: false })
  }
  return cells
})
const changeMonth = (delta) => {
  let m = calMonth.value + delta
  let y = calYear.value
  if (m < 1) { m = 12; y-- }
  if (m > 12) { m = 1; y++ }
  calYear.value = y
  calMonth.value = m
}
// 回到今天
const jumpToToday = () => {
  const n = new Date()
  calYear.value = n.getFullYear()
  calMonth.value = n.getMonth() + 1
}
// 年份下拉选项（当前年 ±10 年）
const yearOptions = computed(() => {
  const now = new Date().getFullYear()
  const arr = []
  for (let y = now - 10; y <= now + 10; y++) arr.push(y)
  return arr
})
// 兼容两种入参：日历格子（{ dateStr }）或直接的日期字符串
const openDiaryOfDay = (cell) => {
  const dateStr = typeof cell === 'string' ? cell : (cell?.dateStr || cell?.diaryDate)
  if (!dateStr) return
  const diary = diaryList.value.find(d => d.diaryDate === dateStr)
  if (diary) dayDiaryTarget.value = diary
}

// ===== 关键词提取（简单版）=====
const extractKeywords = (text) => {
  if (!text) return []
  const kws = ['开心','难过','生气','焦虑','平静','疲惫','朋友','家人','工作','学习','美食','运动','阳光','雨','梦','希望','累','温暖','孤独','幸福']
  const found = kws.filter(k => text.includes(k)).slice(0, 3)
  if (found.length > 0) return found
  // 回退：取前3个双字词
  const words = text.match(/[\u4e00-\u9fa5]{2,4}/g) || []
  return [...new Set(words)].slice(0, 3)
}

// ===== 跳转独立写日记页面 =====
const scrollToWrite = () => {
  router.push('/diary/write')
}
const scrollToBook = () => {
  document.querySelector('.book-area')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
// ===== 情绪分布统计 =====
const monthDiaries = computed(() => {
  const prefix = `${calYear.value}-${String(calMonth.value).padStart(2,'0')}`
  return diaryList.value.filter(d => String(d.diaryDate).startsWith(prefix))
})
const emotionDistCount = (val) => monthDiaries.value.filter(d => d.emotion === val).length
const emotionDistPct = (val) => {
  if (monthDiaries.value.length === 0) return 0
  return Math.round(emotionDistCount(val) / monthDiaries.value.length * 100)
}
const monthCount = computed(() => monthDiaries.value.length)
const monthPosRate = computed(() => {
  if (monthDiaries.value.length === 0) return 0
  const pos = monthDiaries.value.filter(d => positiveEmos.includes(d.emotion)).length
  return Math.round(pos / monthDiaries.value.length * 100)
})
const topEmotionMonth = computed(() => {
  if (monthDiaries.value.length === 0) return emotions[0]
  const emoCount = {}
  monthDiaries.value.forEach(d => { emoCount[d.emotion] = (emoCount[d.emotion] || 0) + 1 })
  let top = 0, max = 0
  Object.entries(emoCount).forEach(([k, v]) => { if (v > max) { max = v; top = Number(k) } })
  return emotions.find(e => e.val === top) || emotions[0]
})
const monthSummary = computed(() => {
  if (monthDiaries.value.length === 0) return '本月还没有记录，期待你的第一篇 '
  const top = topEmotionMonth.value
  const pos = monthPosRate.value
  // 注意：top.icon 是图片路径（/img/xx.png），不能拼进文案里，只用文字 label
  if (pos >= 60) return `本月「${top.label}」情绪居多，整体状态很不错！继续保持 `
  if (pos >= 40) return `本月情绪起伏较多，主打「${top.label}」。记得照顾好自己 `
  return `本月「${top.label}」偏多，心情有点低落，会好起来的 `
})

// ===== 日期工具 =====
const formatDay = (v) => v ? String(v).slice(-2) : ''
const formatMonth = (v) => v ? String(v).slice(5, 7) + '月' : ''
const formatFullDate = (v) => v ? String(v).slice(0, 10) : ''
const getWeekday = (v) => {
  const days = ['日', '一', '二', '三', '四', '五', '六']
  const d = new Date(formatFullDate(v))
  return '星期' + days[d.getDay()]
}
const truncate = (s, n) => s ? (s.length > n ? s.slice(0, n) + '…' : s) : ''

// ===== 3D 卡片扇形展开 =====
const cardStyle = (i) => {
  const total = diaryList.value.length
  const spread = 14
  const offset = i - (total - 1) / 2
  const rotateY = offset * spread
  const translateZ = -Math.abs(offset) * 30
  const translateX = offset * 20
  return {
    transform: `rotateY(${rotateY}deg) translateZ(${translateZ}px) translateX(${translateX}px)`,
    zIndex: total - i,
    transition: 'transform 0.6s cubic-bezier(.34,1.56,.64,1)',
  }
}
// ===== 锚点导航：平滑滚动 =====
const scrollToAnchor = (id) => {
  const el = document.getElementById(id)
  if (!el) return
  const offset = 100  // 顶部 sticky 导航 + hero 留出空间
  const top = el.getBoundingClientRect().top + window.scrollY - offset
  window.scrollTo({ top, behavior: 'smooth' })
}
const getCardTransform = (i) => {
  const n = diaryList.value.length
  const spread = Math.min(140, 20 * n)
  const angle = n > 1 ? -spread / 2 + (spread / (n - 1)) * i : 0
  return {
    transform: `rotateY(${angle}deg) translateZ(100px)`,
    left: '50%',
    marginLeft: '-100px',
    zIndex: i
  }
}
const getDay = (date) => date ? date.split('-')[2]?.replace(/^0/, '') || '' : ''
const getMonth = (date) => date ? parseInt(date.split('-')[1]) || '' : ''

// ===== 交互 =====
const flipCard = (d) => {
  flippedId.value = flippedId.value === d.diaryId ? null : d.diaryId
}
const onDragStart = (e) => { dragging.value = true; dragStartX.value = e.clientX }
const onDragMove = (e) => {
  if (!dragging.value) return
  fanY.value += (e.clientX - dragStartX.value) * 0.15
  dragStartX.value = e.clientX
}
const onDragEnd = () => { dragging.value = false }

// ===== ECharts 情绪趋势图 =====
const renderChart = () => {
  if (!chartRef.value) return

  let records = [...diaryList.value].sort((a, b) => a.diaryDate.localeCompare(b.diaryDate))
  let rangeLabel = '近7天'
  if (chartRange.value === 'week') {
    const d = new Date(); d.setDate(d.getDate() - 6)
    const from = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    records = records.filter(r => r.diaryDate >= from)
    rangeLabel = '近7天'
  } else if (chartRange.value === 'month') {
    const d = new Date(); d.setDate(1)
    const from = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-01`
    records = records.filter(r => r.diaryDate >= from)
    rangeLabel = '本月'
  }

  // 补全空天
  const map = {}
  records.forEach(r => { map[r.diaryDate] = r.emotion })
  let start, end
  if (records.length > 0) {
    start = records[0].diaryDate
    end = records[records.length - 1].diaryDate
  } else {
    const d = new Date()
    end = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    const prev = new Date(d); prev.setDate(d.getDate() - 6)
    start = `${prev.getFullYear()}-${String(prev.getMonth()+1).padStart(2,'0')}-${String(prev.getDate()).padStart(2,'0')}`
  }
  const dates = [], lineData = [], posData = [], negData = []
  let cur = new Date(start), last = new Date(end)
  while (cur <= last) {
    const ds = `${cur.getFullYear()}-${String(cur.getMonth()+1).padStart(2,'0')}-${String(cur.getDate()).padStart(2,'0')}`
    dates.push(`${cur.getMonth()+1}/${cur.getDate()}`)
    const emo = map[ds] || null
    // 情绪数值：1开心(100) 2平静(70) 3疲惫(50) 4难过(20) 5生气(25) 6焦虑(40)
    // 情绪指数：9种全覆盖
    const scoreMap = {1:100, 7:90, 8:85, 2:70, 9:55, 3:50, 6:40, 5:25, 4:20}
    const score = emo ? scoreMap[emo] : null
    lineData.push(score)
    posData.push(emo ? (positiveEmos.includes(emo) ? 1 : 0) : 0)
    negData.push(emo ? (negativeEmos.includes(emo) ? 1 : 0) : 0)
    cur.setDate(cur.getDate() + 1)
  }

  const option = {
    grid: { top: 24, bottom: 36, left: 22, right: 22, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: cssVar('--c-panel', '#ffffff'),
      borderColor: hexA(accent(), 0.45),
      textStyle: { color: cssVar('--c-text', '#3d4d3d') },
      formatter: (ps) => {
        let s = `<b>${ps[0].axisValue}</b><br/>`
        ps.forEach(p => { if (p.value != null) s += `${p.marker} ${p.seriesName}: ${p.value}<br/>` })
        return s
      }
    },
    xAxis: {
      type: 'category', data: dates, boundaryGap: false,
      axisLine: { lineStyle: { color: cssVar('--c-border', '#b0c4b0') } },
      axisLabel: { color: cssVar('--c-text-weak', '#6b7b6b'), fontSize: 10 },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value', min: 0, max: 100,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: cssVar('--c-text-weak', '#6b7b6b'), fontSize: 10 },
      splitLine: { lineStyle: { color: cssVar('--c-border', 'rgba(180,200,180,0.2)') } }
    },
    series: [
      {
        name: '情绪指数', type: 'line', smooth: true,
        data: lineData,
        symbol: 'circle', symbolSize: 8,
        itemStyle: { color: accent() },
        lineStyle: { color: accent(), width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: hexA(accent(), 0.4) },
            { offset: 1, color: hexA(accentSoft(), 0.1) }
          ])
        },
        connectNulls: true
      }
    ]
  }
  // 每次都是整份配置（日期区间会变），必须 notMerge，否则旧日期会残留在 x 轴上
  chart.set(option, { notMerge: true })
}

// ===== AI 智能寄语（先调后端 AI 接口，失败则兜底本地池）=====
const pickWhisper = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  aiWhisper.value = ' 绪见 AI 正在为你生成...'
  try {
    const res = await generateWhisper(info?.userId)
    if (res?.code === 200 && res.data) {
      aiWhisper.value = res.data
      return
    }
  } catch {
    // AI 调用失败，静默走本地兜底
  }
  // 兜底：本地随机文案池（9种情绪全覆盖）
  const emoToPool = (e) => {
    if ([1, 7, 8].includes(e)) return whisperPool.good      // 开心 兴奋 满足  good
    if (e === 2) return whisperPool.calm                   // 平静  calm
    if (e === 3) return whisperPool.tired                  // 疲惫  tired
    if (e === 4) return whisperPool.sad                    // 难过  sad
    if (e === 5) return whisperPool.angry                  // 生气  angry
    if (e === 6) return whisperPool.anxious                // 焦虑  anxious
    return whisperPool.default                              // 其他/未知  default
  }
  let pool = whisperPool.default
  if (todayDiary.value) {
    pool = emoToPool(todayDiary.value.emotion)
  } else if (topEmotion.value) {
    pool = emoToPool(topEmotion.value.val)
  }
  aiWhisper.value = pool[Math.floor(Math.random() * pool.length)]
}
const refreshWhisper = () => pickWhisper()

// ===== CRUD =====
const loadDiaries = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { router.replace('/login'); return }
  try {
    // 后端单页封顶 100 条，必须翻页拉全，否则"已记录"满 100 后永远是 100
    const all = await fetchAllPages(page => getDiaryList({ page, size: 100, userId: info.userId }))
    diaryList.value = [...all].sort((a, b) => new Date(b.diaryDate) - new Date(a.diaryDate))
  diaryPageIndex.value = 0
  } catch {
    diaryList.value = []
  }
}

// ===== 图片选择 + 上传 =====
const onPickImage = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) { alert('图片不能超过 5MB'); e.target.value = ''; return }
  try {
    const res = await uploadFile(file, 'diary')
    if (res?.code === 200 && res.data?.url) {
      form.value.imageUrl = res.data.url   // 存 /diary/xxx.jpg
    } else {
      alert(' 上传失败：' + (res?.msg || '未知错误'))
    }
  } catch {
    alert(' 网络错误，上传失败')
  } finally {
    e.target.value = ''
  }
}

const saveDiary = async () => {
  if (!form.value.content.trim()) return
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  const d = new Date()
  const diaryDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  try {
    const res = await apiSaveDiary({
      userId: info.userId,
      diaryContent: form.value.content,
      emotion: form.value.emotion,
      diaryDate,
      isPublic: 0, // 恒定私密
      imageUrl: form.value.imageUrl || null,
    })
    if (res.code === 200) {
      form.value.content = ''
      form.value.emotion = 1
      form.value.imageUrl = ''
      await loadDiaries()
      alert(' 日记已记录！')
      // 危机通道：后端在保存时已扫描正文，命中危机词就会生成提示 
      // 这里让全局弹窗立刻重新拉一次（没有命中时它什么也不做）
      window.dispatchEvent(new CustomEvent('risk-crisis-check'))
    } else {
      alert(' ' + (res.msg || '保存失败'))
    }
  } catch (e) {
    alert(' 网络错误，请重试')
  }
}

const editDiary = (d) => {
  editTarget.value = d
  editContent.value = d.diaryContent || ''
  editSleep.value = d.sleepHours ?? d.sleep_hours ?? ''
}
/** source=3 是系统自动代记（用户当天没打开 App 由定时任务补的） */
const isAutoFilled = (d) => Number(d?.source) === 3

const confirmEdit = async () => {
  if (!editContent.value.trim()) return
  // 睡眠时长：留空 = 不改动（后端 updateById 忽略 null），填了才带上
  let sleepHours
  if (editSleep.value !== '' && editSleep.value !== null) {
    const n = Number(editSleep.value)
    if (Number.isNaN(n) || n < 0 || n > 24) { alert(' 睡眠时长请填 0 ~ 24 之间的数字（可带 .5）'); return }
    sleepHours = Math.round(n * 2) / 2
  }
  const payload = {
    diaryId: editTarget.value.diaryId,
    diaryContent: editContent.value,
    emotion: editTarget.value.emotion,
    diaryDate: editTarget.value.diaryDate,
    imageUrl: editTarget.value.imageUrl || null,
    ...(sleepHours !== undefined ? { sleepHours } : {}),
  }
  try {
    // 代记走 claim：source 由 3 变成 1，才算用户亲笔（否则 update 会保留 3，经验值永远拿不到）
    const res = isAutoFilled(editTarget.value)
      ? await apiClaimDiary(editTarget.value.diaryId, payload)
      : await apiUpdateDiary(payload)
    if (res.code === 200) {
      editTarget.value = null
      await loadDiaries()
      window.dispatchEvent(new CustomEvent('risk-crisis-check'))
    } else {
      alert(' ' + (res.msg || '修改失败'))
    }
  } catch {
    alert(' 网络错误')
  }
}
// 入参可以是 diaryId，也可以是日记对象
const delDiary = async (d) => {
  const id = typeof d === 'object' && d ? d.diaryId : d
  if (!id) return
  if (!confirm('确定删除这篇日记吗？删除后无法恢复。')) return
  try {
    await apiDeleteDiary(id)
    flippedId.value = null
    dayDiaryTarget.value = null
    editTarget.value = null
    await loadDiaries()
    alert(' 日记已删除')
  } catch { alert(' 删除失败') }
}

onMounted(async () => {
  await loadDiaries()
  await nextTick()
  renderChart()
  pickWhisper()
  // resize / dispose 由 useChart 全局托管，无需单独注册
})

watch(diaryList, async (list) => {
  if (diaryPageIndex.value >= list.length) diaryPageIndex.value = Math.max(0, list.length - 1)
  await nextTick()
  renderChart()
  pickWhisper()
}, { deep: true })
</script>

<style scoped>
/* ===== 页面容器 ===== */
.diary-page {
  position: relative;
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
  padding: 0 0 60px;   /* 顶部横幅贴顶，不留白边 */
  overflow-x: hidden;
}
.diary-page::before {
  content: '';
  position: absolute;
  inset: 0;
  min-height: 100vh;
  background:
    repeating-linear-gradient(
      transparent 0px,
      transparent 27px,
      color-mix(in srgb, var(--c-hex) 22%, transparent) 28px
    ),
    linear-gradient(180deg, var(--c-bg1) 0%, color-mix(in srgb, var(--c-main) 28%, #fff) 50%, color-mix(in srgb, var(--c-bg2) 70%, #fff) 100%);
  z-index: -2;
}
.diary-page::after {
  content: '';
  position: absolute;
  inset: 0;
  min-height: 100vh;
  background:
    radial-gradient(circle at 15% 10%, color-mix(in srgb, var(--c-main) 38%, transparent) 0%, transparent 35%),
    radial-gradient(circle at 85% 30%, color-mix(in srgb, var(--c-hex) 30%, transparent) 0%, transparent 35%),
    radial-gradient(circle at 50% 90%, color-mix(in srgb, var(--c-main) 24%, transparent) 0%, transparent 35%);
  z-index: -1;
  pointer-events: none;
}

/* ===== 通用 ===== */
.glass {
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.75);
  box-shadow: 0 10px 30px rgba(110, 150, 100, 0.12);
  border-radius: 22px;
}

/* ===== 手账便签纸风格 ===== */
.sticky-note {
  position: relative;
  border-radius: 14px;
  border: none;
  box-shadow:
    0 2px 4px rgba(0, 0, 0, 0.06),
    0 12px 20px rgba(0, 0, 0, 0.08),
    inset 0 0 0 1px rgba(0, 0, 0, 0.04);
  background-image:
    linear-gradient(180deg, rgba(255,255,255,0.4) 0%, rgba(255,255,255,0) 100%);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.sticky-note:hover {
  transform: translateY(-3px);
  box-shadow:
    0 6px 12px rgba(0, 0, 0, 0.08),
    0 20px 36px rgba(0, 0, 0, 0.12);
  z-index: 5;
}
.note-beige {
  background: #fef9e7;
}
.note-pink {
  background: #fde4ec;
}
.note-blue {
  background: #e4f0fb;
}
.note-green {
  background: var(--c-bg2);
}
.note-yellow {
  background: #fff5d6;
}
.note-coral {
  background: #fde8d8;
}



/* ===== 顶部横幅（手账本封面风） ===== */
.hero {
  width: 100%;
  margin: 0 0 16px;
  padding: 24px 24px;
  box-sizing: border-box;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  background: 
    radial-gradient(circle at 20% 20%, rgba(255,255,255,0.25) 0%, transparent 40%),
    linear-gradient(135deg, var(--c-bg2) 0%, color-mix(in srgb, var(--c-main) 30%, #fff) 40%, color-mix(in srgb, var(--c-main) 45%, #fff) 100%);
  box-shadow: 
    inset 0 1px 0 rgba(255,255,255,0.6),
    inset 0 -1px 0 rgba(0,0,0,0.05),
    0 8px 24px rgba(100, 140, 90, 0.18),
    0 2px 6px rgba(100, 140, 90, 0.1);
  position: relative;
  overflow: hidden;
  border-radius: 0;
}
/* 手账本缝线装饰（上下虚线） */
.hero::before {
  content: '';
  position: absolute;
  top: 8px; left: 20px; right: 20px;
  height: 0;
  border-top: 1.5px dashed rgba(255,255,255,0.7);
}
.hero::after {
  content: '';
  position: absolute;
  bottom: 8px; left: 20px; right: 20px;
  height: 0;
  border-top: 1.5px dashed rgba(255,255,255,0.7);
}
/* 角落小花朵装饰 */
.hero-title::before {
  content: '';
  position: absolute;
  top: -2px; left: -16px;
  width: 16px; height: 16px;
  color: rgba(255,255,255,0.8);
  background: currentColor;
  -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23000' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z'/%3E%3Cpath d='M12 16.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 1 1 12 7.5 4.5 4.5 0 1 1 16.5 12 4.5 4.5 0 1 1 12 16.5z'/%3E%3C/svg%3E") center/contain no-repeat;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23000' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z'/%3E%3Cpath d='M12 16.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 1 1 12 7.5 4.5 4.5 0 1 1 16.5 12 4.5 4.5 0 1 1 12 16.5z'/%3E%3C/svg%3E") center/contain no-repeat;

}
.hero-title {
  position: relative;
}
.hero-title h1 {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--c-text);
  text-shadow: 1px 1px 0 rgba(255,255,255,0.5);
  font-family: 'Kaiti', '楷体', 'STKaiti', serif;
  margin: 0;
}
.hero-sub {
  font-size: 16px;
  color: var(--c-text);
  margin-top: 6px;
  letter-spacing: 2px;
  font-family: 'Kaiti', '楷体', 'STKaiti', serif;
}
.hero-meta {
  display: flex;
  gap: 12px;
}
.meta-card {
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: none;
  text-align: center;
  min-width: 70px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s;
}
.meta-card:nth-child(2) { transform: rotate(0deg); }
.meta-card:nth-child(3) { transform: rotate(0deg); }
.meta-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(80, 120, 70, 0.18);
}
.meta-num {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--c-text);
  font-family: 'Kaiti', serif;
}
.meta-emoji { width: 32px; height: 32px; object-fit: contain; }
.meta-emotion-name { display: block; font-size: 16px; font-weight: 700; color: var(--c-text); margin-top: 2px; }
.meta-label {
  display: block;
  font-size: 12px;
  color: var(--c-text);
  margin-top: 2px;
  letter-spacing: 1px;
}

/* ===== 写日记区（简洁表单版）===== */
.write-area { width: 100%; margin: 0 0 12px; }
.write-card {
  padding: 28px 32px;
  background: rgba(255, 253, 248, 0.95) !important;
  box-shadow:
    0 2px 8px rgba(0,0,0,0.04),
    0 20px 50px rgba(0,0,0,0.08) !important;
  border-radius: 20px;
}
.write-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.write-title-main {
  font-size: 22px;
  font-weight: 700;
  color: #3d3d3d;
  letter-spacing: 4px;
}
.write-close {
  font-size: 28px;
  color: #999;
  cursor: pointer;
  line-height: 1;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}
.write-close:hover { background: rgba(0,0,0,0.06); color: #333; }

.form-block { margin-bottom: 24px; position: relative; }
.form-label {
  font-size: 15px;
  color: #444;
  margin-bottom: 12px;
  letter-spacing: 1px;
}
.form-required {
  color: #888;
  font-size: 13px;
  margin-left: 2px;
}
.form-optional {
  color: #bbb;
  font-size: 13px;
  margin-left: 2px;
}

/* 心情网格 3x3 */
.emo-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.emo-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px 0;
  border-radius: 14px;
  border: 1.5px solid #eee;
  background: #fff;
  cursor: pointer;
  transition: all 0.25s;
}
.emo-item:hover {
  border-color: var(--c-border);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0,0,0,0.08);
}
.emo-item.on {
  border-color: var(--c-hex);
  background: var(--c-bg1);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--c-main) 20%, transparent);
}
.emo-emoji { font-size: 26px; line-height: 1; }
.emo-text { font-size: 14px; color: #555; letter-spacing: 1px; }

/* 文字输入 */
.form-block textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1.5px solid #eee;
  background: #fff;
  font-size: 15px;
  font-family: inherit;
  line-height: 1.7;
  resize: vertical;
  color: #333;
  transition: all 0.2s;
}
.form-block textarea:focus {
  outline: none;
  border-color: var(--c-hex);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 12%, transparent);
}
.form-block textarea::placeholder { color: #bbb; }


.save-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(110, 180, 100, 0.45);
}
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 日记本 3D 展示 ===== */
.book-area { width: 100%; margin: 0; }
.book-header { text-align: center; margin-bottom: 28px; }
.book-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--c-text);
  letter-spacing: 4px;
}
.book-tip { font-size: 12px; color: var(--c-text-weak); margin-top: 8px; letter-spacing: 2px; }

/* 空状态 */
.empty-state {
  padding: 60px 20px;
  text-align: center;
  color: var(--c-text-weak);
}
.empty-illustration {
  font-size: 64px;
  margin-bottom: 16px;
  filter: drop-shadow(0 4px 12px color-mix(in srgb, var(--c-main) 30%, transparent));
}
.empty-state p { font-size: 15px; letter-spacing: 2px; }
.empty-hint { font-size: 12px; margin-top: 8px; color: var(--c-text-weak); }

/* 3D 舞台 */
.book-stage {
  position: relative;
  height: 440px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  user-select: none;
}
.book-stage:active { cursor: grabbing; }

.book-fan {
  position: relative;
  width: 200px;
  height: 340px;
  transform-style: preserve-3d;
  transition: transform 0.15s ease-out;
}

/* ===== 3D 卡片 ===== */
.diary-3d-card {
  position: absolute;
  width: 200px;
  height: 340px;
  left: 0;
  top: 0;
  transform-style: preserve-3d;
  cursor: pointer;
}
.diary-3d-card.flipped {
  transform: rotateY(180deg) !important;
  z-index: 9999 !important;
}

.face {
  position: absolute;
  inset: 0;
  border-radius: 20px;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  padding: 22px 20px;
  display: flex;
  flex-direction: column;
}
.front { transform: translateZ(4px); }
.back { transform: rotateY(180deg) translateZ(4px); }

/* 正面 */
.front-date { text-align: center; margin-bottom: 8px; }
.date-day {
  display: block;
  font-size: 46px;
  font-weight: 700;
  color: var(--c-text);
  line-height: 1;
}
.date-month {
  display: block;
  font-size: 14px;
  color: var(--c-text-weak);
  letter-spacing: 3px;
  margin-top: 4px;
}
.front-emoji {
  width: 72px; height: 72px; object-fit: contain;
  display: block; margin: 10px auto;
  filter: drop-shadow(0 4px 8px rgba(0,0,0,0.15));
}
.front-preview {
  font-size: 13px;
  color: var(--c-text);
  line-height: 1.7;
  text-align: center;
  flex: 1;
  overflow: hidden;
}
.front-hint {
  text-align: center;
  font-size: 11px;
  color: rgba(74, 90, 74, 0.6);
  letter-spacing: 2px;
  margin-top: 6px;
}
.front-decor {
  position: absolute;
  top: -40px;
  right: -40px;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
}

/* 背面 */
.flip-back {
  align-self: flex-start;
  padding: 4px 12px;
  border-radius: 999px;
  border: none;
  background: rgba(255, 255, 255, 0.6);
  color: var(--c-text);
  font-size: 11px;
  cursor: pointer;
  margin-bottom: 10px;
}
.back-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.back-emoji { width: 30px; height: 30px; object-fit: contain; }
.back-date { font-size: 13px; font-weight: 600; color: var(--c-text); }
.back-weekday { font-size: 12px; color: var(--c-text-weak); }
.back-content {
  flex: 1;
  font-size: 14px;
  line-height: 1.8;
  color: var(--c-text);
  overflow-y: auto;
  padding-right: 4px;
  max-height: 200px;
  white-space: pre-wrap;
  word-break: break-all;
}
.back-actions { display: flex; gap: 8px; margin-top: 12px; }
.act-btn {
  flex: 1;
  padding: 8px;
  border-radius: 12px;
  border: none;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.act-btn.edit { background: rgba(255, 255, 255, 0.7); color: var(--c-text); }
.act-btn.edit:hover { background: #fff; transform: translateY(-1px); }
.act-btn.del { background: rgba(255, 255, 255, 0.7); color: #c95858; }
.act-btn.del:hover { background: #fff; transform: translateY(-1px); }

.back-decor {
  position: absolute;
  bottom: -30px;
  left: -30px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
}

/* 拖动提示箭头 */
.hint-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: var(--c-text);
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.1);
  user-select: none;
  z-index: 10;
  transition: all 0.2s;
}
.hint-arrow:hover { transform: translateY(-50%) scale(1.1); }
.hint-arrow.left  { left: 10%; }
.hint-arrow.right { right: 10%; }

/* ===== 图例 ===== */
.legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--c-text-weak);
}
.legend-icon { width: 16px; height: 16px; object-fit: contain;
  letter-spacing: 1px;
}
.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

/* ===== 弹窗 ===== */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(30, 50, 30, 0.3);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}
@keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }
.modal-card { width: 440px; max-width: 92vw; padding: 28px; }
.modal-card h3 { margin: 0 0 16px; font-size: 18px; color: var(--c-text); letter-spacing: 2px; }
.modal-actions { margin-top: 16px; display: flex; justify-content: flex-end; gap: 12px; }
/* 编辑弹窗：补记昨晚睡眠 */
.edit-sleep-row {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-top: 12px; padding: 8px 12px; border-radius: 12px;
  background: color-mix(in srgb, var(--c-main) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--c-main) 22%, transparent);
}
.edit-sleep-row .es-label { font-size: 13px; color: #5c7a42; letter-spacing: 1px; }
.edit-sleep-row .es-input {
  width: 62px; padding: 5px 8px; border-radius: 999px;
  border: 1.5px solid var(--c-border); background: #fff;
  font-size: 13px; color: #444; font-family: inherit; text-align: center; outline: none;
}
.edit-sleep-row .es-input:focus { border-color: var(--c-hex); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 12%, transparent); }
.edit-sleep-row .es-unit { font-size: 12.5px; color: var(--c-text-weak); }
.edit-sleep-row .es-tip { font-size: 11px; color: var(--c-text-weak); margin-left: auto; }
.cancel-btn {
  padding: 10px 24px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background: rgba(255, 255, 255, 0.8);
  color: var(--c-text-weak);
  font-size: 13px;
  cursor: pointer;
  letter-spacing: 2px;
  transition: all 0.2s;
}
.cancel-btn:hover { background: #fff; }
.danger-btn { border-color: #f0c2c2; color: #b4574f; background: rgba(255, 244, 244, 0.9); }
.danger-btn:hover { background: #ffeaea; border-color: #e39b9b; }

/* ===== Hero 右侧 ===== */
.hero-right { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.write-fab {
  padding: 8px 14px;
  border-radius: 20px;
  border: 1px solid rgba(255,255,255,0.9);
  background: rgba(255,255,255,0.85);
  color: var(--c-text);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(100,140,90,0.15);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  position: relative;
  transition: all 0.2s;
  font-family: 'Kaiti', '楷体', 'STKaiti', serif;
}
.write-fab:hover { 
  transform: translateY(-2px); 
  box-shadow: 0 4px 12px rgba(100,140,90,0.25);
  background: #fff;
}
.fab-icon { width: 18px; height: 18px; object-fit: contain; }

/* 页面通用图标类 */
.title-icon { width: 44px; height: 44px; object-fit: contain; vertical-align: -12px; margin-right: 8px; }
.title-icon.lg { width: 52px; height: 52px; vertical-align: -14px; }
.card-icon { width: 22px; height: 22px; object-fit: contain; vertical-align: middle; margin-right: 4px; }
.card-icon.sm { width: 16px; height: 16px; }
.write-fab:hover { transform: translateY(-2px); box-shadow: 0 10px 24px rgba(110,180,100,0.45); }
/* 导出按钮：同款胶囊的弱化版 */
.write-fab.ghost-fab { font-weight: 500; color: var(--c-text-weak); }
.write-fab.ghost-fab:hover { box-shadow: 0 6px 16px rgba(110,180,100,0.25); }

/* ===== 双栏布局 ===== */
.top-grid {
  width: 100%;
  margin: 0 0 8px;
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  align-items: start;
  box-sizing: border-box;
}
.chart-card, .summary-card, .calendar-card {
  min-height: 190px;
  padding: 8px 10px;
  width: 100%;
  box-sizing: border-box;
}
.chart-card { 
  min-width: 0; 
  padding: 16px 20px; 
}
.side-col { min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.card-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 4px;
}
.card-head h3 {
  font-size: 16px; font-weight: 700; color: var(--c-text);
  letter-spacing: 2px; margin: 0;
}

/* 图表 */
.chart-tabs { display: flex; gap: 6px; }
.tab {
  padding: 4px 12px; border-radius: 999px; border: 1px solid var(--c-border);
  background: rgba(255,255,255,0.7); color: var(--c-text-weak);
  font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.tab.on { background: var(--c-hex); border-color: var(--c-hex); color: #fff; font-weight: 600; }
.chart-box {
  width: 100%;
  height: 260px;
  margin: 0 auto;
  min-width: 0;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 12px;
  padding: 12px;
  box-sizing: border-box;
  border: 1px dashed rgba(140, 180, 140, 0.35);
}
.chart-legend {
  display: flex; justify-content: center; gap: 16px; margin-top: 6px;
}
.lg-item { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--c-text-weak); }
.lg-dot { width: 10px; height: 10px; border-radius: 50%; }
/* ===== 图表底部（情绪分布 + 月度小结） ===== */
.chart-bottom {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed rgba(140, 180, 140, 0.35);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
/* 卡片内嵌的快捷情绪标签 */
/* ===== chart-quick-tags 情绪标签 ===== */
.chart-quick-tags {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed rgba(180, 200, 180, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;   /* ← 居中 */
  gap: 10px;
  flex-wrap: wrap;
  row-gap: 8px;
}
.chart-quick-tags .quick-label { font-size: 12px; color: var(--c-text); font-weight: 600; }
.chart-quick-tags .quick-row { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
.chart-quick-tags .qk-btn {
  padding: 5px 12px; border-radius: 999px;
  border: 1.5px solid; background: rgba(255,255,255,0.7);
  font-size: 12px; cursor: pointer; color: var(--c-text);
  transition: all 0.2s;
}
.chart-quick-tags .qk-btn.on { box-shadow: 0 2px 6px rgba(0,0,0,0.1); }

/* ===== 嵌在月度小结里的 AI 寄语 ===== */
.chart-ai-whisper {
  margin-top: 14px;
  padding: 18px 22px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(253,232,216,0.85), rgba(255,240,220,0.9));
  border: 1px solid rgba(240,180,140,0.4);
  display: flex; align-items: center; gap: 16px;
  min-height: 72px;
}
.chart-ai-whisper .ai-avatar {
  width: 48px; height: 48px; border-radius: 50%; flex-shrink: 0;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.chart-ai-whisper .ai-text {
  flex: 1; margin: 0; font-size: 16px; line-height: 1.6;
  color: #6b5040;
}
.chart-ai-whisper .ai-refresh {
  padding: 7px 16px; border-radius: 999px; border: none;
  background: linear-gradient(135deg, #ffb89a, #ff9570);
  color: #fff; font-size: 13px; cursor: pointer; flex-shrink: 0;
  transition: all 0.2s;
  font-weight: 600;
}
.chart-ai-whisper .ai-refresh:hover { transform: scale(1.05); filter: brightness(1.06); box-shadow: 0 4px 12px rgba(255, 149, 112, 0.4); }
.dist-title {
  font-size: 15px;
  color: var(--c-text);
  font-weight: 700;
  letter-spacing: 2px;
  margin-bottom: 14px;
}
.dist-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 14px;
}
.dist-emoji { width: 24px; height: 24px; object-fit: contain; flex-shrink: 0; }
.dist-label { font-size: 13px; color: #555; min-width: 36px; flex-shrink: 0; font-weight: 500; }
.dist-bar-bg {
  flex: 1; height: 14px; border-radius: 7px;
  background: rgba(180, 200, 180, 0.2); overflow: hidden;
}
.dist-bar-fill {
  height: 100%; border-radius: 7px;
  transition: width 0.6s cubic-bezier(.34,1.56,.64,1);
}
.dist-num { width: 24px; text-align: right; color: var(--c-text-weak); font-size: 13px; font-weight: 600; }

.sum-stats {
  display: flex; gap: 12px; margin-bottom: 14px;
}
.stat-item {
  flex: 1; text-align: center;
  padding: 12px 6px; border-radius: 12px;
  background: rgba(255,255,255,0.5);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.stat-num {
  display: block; font-size: 24px; font-weight: 700; color: var(--c-text);
}
.stat-num.pos { color: var(--c-hex); }
.stat-emoji { width: 32px; height: 32px; object-fit: contain; display: block; margin: 0 auto; }
.stat-emotion-name { display: block; font-size: 16px; font-weight: 700; color: var(--c-text); margin-top: 4px; text-align: center; }
.stat-lbl { display: block; font-size: 12px; color: var(--c-text-weak); margin-top: 2px; letter-spacing: 1px; }
.month-text {
  margin: 0; font-size: 14px; line-height: 1.8;
  color: var(--c-text-weak); padding: 12px 14px; border-radius: 12px;
  background: rgba(184, 212, 170, 0.15);
}
.month-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
/* 侧边栏卡片正常高度 */
.side-col .summary-card {
  min-height: 160px;
}
.side-col .calendar-card {
  flex: none;
}

/* 今日小结 */
.today-chip-sm {
  padding: 4px 12px; border-radius: 999px;
  background: color-mix(in srgb, var(--c-main) 30%, transparent); color: var(--c-text);
  font-size: 11px; letter-spacing: 1px;
}
.sum-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.sum-emoji { width: 36px; height: 36px; object-fit: contain; }
.sum-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.tag {
  padding: 3px 10px; border-radius: 999px;
  font-size: 11px; color: var(--c-text);
}
.tag.kw { background: rgba(205,232,248,0.5) !important; }
.tag.intensity-tag { background: rgba(255,255,255,0.85) !important; border: 1px dashed rgba(74,90,74,0.35); color: #6b7a6b; }
.sum-text { margin: 0; font-size: 14px; line-height: 1.8; color: var(--c-text); }
.sum-empty { text-align: center; padding: 24px 0; color: var(--c-text-weak); }
.sum-empty p { margin: 0; font-size: 15px; letter-spacing: 2px; }
.sum-hint { font-size: 13px !important; margin-top: 8px !important; color: var(--c-text-weak) !important; }
/* ===== 页面内锚点导航（便签纸风 sticky 悬浮）===== */
/* ===== 页面内锚点导航（和右侧卡片同宽）===== */
.anchor-nav {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 10px;
  padding: 10px 14px;
  margin-bottom: 14px;
  border-radius: 14px;
  background: #fff5d6 !important;
  box-shadow: 0 4px 14px rgba(180, 150, 80, 0.18) !important;
}
.an-label {
  font-size: 12px;
  color: #8a7a4f;
  letter-spacing: 1px;
  padding-right: 8px;
  border-right: 1.5px dashed rgba(138, 122, 79, 0.3);
}
.an-btn {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.65);
  border: 1.5px solid transparent;
  text-decoration: none;
  transition: all 0.25s;
  cursor: pointer;
  white-space: nowrap;
}
.an-btn:hover {
  background: #fff;
  border-color: color-mix(in srgb, var(--c-main) 45%, transparent);
  color: var(--c-hex);
  transform: translateY(-1px);
  box-shadow: 0 4px 10px color-mix(in srgb, var(--c-main) 20%, transparent);
}
/* ===== 日记配图：自适应容器，不溢出 ===== */
.sum-img {
  display: block;
  max-width: 100%;
  max-height: 260px;
  width: auto;              /* 不强制拉满宽度，避免高图被裁 */
  object-fit: contain;      /* 完整显示整张图 */
  border-radius: 12px;
  margin: 14px auto 0;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}
.back-img {
  display: block;
  max-width: 100%;
  width: 100%;
  max-height: 160px;
  object-fit: cover;
  border-radius: 10px;
  margin-top: 10px;
}
.edit-img-wrap { margin-bottom: 14px; }
.edit-img {
  display: block;
  max-width: 100%;
  width: 100%;
  max-height: 240px;
  object-fit: cover;
  border-radius: 10px;
}
/* 日历 - 重新设计的头部 */
.cal-nav { display: flex; align-items: center; gap: 8px; }
.cal-picker {
  padding: 4px 10px;
  border: 1.5px solid color-mix(in srgb, var(--c-main) 35%, transparent);
  border-radius: 999px;
  background: rgba(255,255,255,0.85);
  font-size: 13px;
  color: var(--c-text);
  cursor: pointer;
  outline: none;
  transition: all 0.2s;
  font-family: inherit;
  appearance: none;
  -webkit-appearance: none;
  background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%23446c3a'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  padding-right: 22px;
}
.cal-picker:hover {
  border-color: var(--c-hex);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--c-main) 20%, transparent);
}
.cal-today-btn {
  padding: 4px 12px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}
.cal-today-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(140,194, 105,0.4);
}

/* 日历格子恢复正常尺寸（之前调大过数字） */
  .cal-week {
    display: grid; grid-template-columns: repeat(7,1fr);
    text-align: center; font-size: 13px; color: var(--c-text-weak);
    margin-bottom: 1px;
  }
.cal-grid {
    display: grid; grid-template-columns: repeat(7,1fr); gap: 0px;
  }
  .cal-cell {
    height: 64px; border-radius: 3px;
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    font-size: 14px; cursor: pointer; position: relative;
    background: rgba(255,255,255,0.4);
    transition: all 0.2s;
  }
.cal-cell.other { opacity: 0.35; }
.cal-cell.today { outline: 1.5px solid var(--c-hex); outline-offset: -1px; }
.cal-cell.checked .cal-num { font-weight: 600; color: var(--c-text); }
.cal-cell:not(.other):hover { transform: scale(1.05); }
.cal-num { font-size: 14px; color: var(--c-text); line-height: 1; }
.cal-dot { width: 18px; height: 18px; object-fit: contain; margin-top: 2px; }

/* ===== 快捷标签行 ===== */
.quick-tags {
  width: 100%; margin: 0 0 6px; padding: 14px 24px;
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
}
.quick-label { font-size: 12px; color: var(--c-text); letter-spacing: 1px; font-weight: 600; flex-shrink: 0; }
.quick-row { display: flex; gap: 8px; flex-wrap: wrap; }
.qk-btn {
  padding: 5px 12px; border-radius: 999px;
  border: 1.5px solid; background: rgba(255,255,255,0.6);
  font-size: 12px; cursor: pointer;
  color: var(--c-text); transition: all 0.2s;
}
.qk-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.qk-btn.on { font-weight: 600; }

/* ===== AI 寄语 ===== */
.ai-whisper {
  width: 100%; margin: 0 0 12px; padding: 20px 24px;
  display: flex; align-items: flex-start; gap: 16px;
  position: relative; overflow: hidden;
}
.ai-left { display: flex; gap: 12px; align-items: center; position: relative; z-index: 1; }
.ai-avatar {
  width: 48px; height: 48px; border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-main) 30%, transparent);
  border: 2px solid #fff;
}
.ai-title h4 { margin: 0; font-size: 14px; color: var(--c-text); letter-spacing: 2px; }
.ai-title .ai-date { font-size: 11px; color: var(--c-text-weak); letter-spacing: 1px; }
.ai-content {
  flex: 1; position: relative; z-index: 1;
  display: flex; align-items: center; gap: 14px;
}
.ai-text {
  flex: 1; margin: 0; font-size: 14px; line-height: 1.7;
  color: var(--c-text); font-style: italic;
}
/* （原通用 .ai-refresh 规则已删：与 .chart-ai-whisper .ai-refresh 冲突，
   hover 白底白字导致按钮隐形 + rotate 翻转文字，见 2026-10-08 修复） */

/* ===== 日历选中日记弹窗 ===== */
.day-diary { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.day-diary .tag { background: rgba(184,212,170,0.6) !important; }

/* ===== 写日记区（匹配 .sf- 前缀模板）===== */
.write-area .write-card {
  padding: 28px 32px;
  background: rgba(255, 253, 248, 0.96) !important;
}

/* 头部 */
.simple-form-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 22px;
}
.sf-title { font-size: 20px; font-weight: 700; color: #3d3d3d; letter-spacing: 3px; margin: 0; }
.sf-close {
  font-size: 24px; color: #999; cursor: pointer; line-height: 1;
  width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;
  border-radius: 50%; border: none; background: none;
  transition: all 0.2s;
}
.sf-close:hover { background: rgba(0,0,0,0.06); color: #333; }

/* 表单块 */
.sf-block { margin-bottom: 22px; position: relative; }
.sf-label { font-size: 15px; color: #444; margin-bottom: 12px; letter-spacing: 1px; display: block; }
.sf-req { color: #999; font-size: 13px; margin-left: 2px; }
.sf-opt { color: #bbb; font-size: 13px; margin-left: 2px; }

/* 心情按钮行（标签式） */
.sf-emo-row { display: flex; flex-wrap: wrap; gap: 10px; }
.sf-emo-btn {
  padding: 8px 18px; border-radius: 999px; border: 1.5px solid var(--c-border);
  background: #fff; cursor: pointer; transition: all 0.25s;
  font-size: 13px; color: #555; display: inline-flex; align-items: center; gap: 4px;
}
.sf-emo-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 14px rgba(0,0,0,0.08); }
.emo-btn-icon { width: 18px; height: 18px; object-fit: contain; }
.sf-emo-btn.active { box-shadow: 0 4px 12px rgba(0,0,0,0.1); font-weight: 600; color: #fff; }

/* 文字输入 */
.sf-textarea {
  width: 100%; box-sizing: border-box;
  padding: 16px 18px 36px; border-radius: 12px;
  border: 1.5px solid #eee; background: #fff;
  font-size: 15px; font-family: inherit; line-height: 1.7;
  resize: vertical; color: #333; transition: all 0.2s;
}
.sf-textarea:focus { outline: none; border-color: var(--c-hex); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 12%, transparent); }
.sf-textarea::placeholder { color: #bbb; }
.sf-counter { position: absolute; right: 16px; bottom: 10px; font-size: 11px; color: #bbb; }

/* 图片占位 / 上传入口 */
.sf-img-placeholder {
  text-align: center; padding: 22px 0;
  border: 2px dashed #e0e0e0; border-radius: 12px;
  color: #aaa; font-size: 14px;
  background: rgba(255,255,255,0.6);
  display: block; cursor: pointer; transition: all 0.25s;
  user-select: none;
}
.sf-img-upload:hover {
  border-color: var(--c-hex); color: var(--c-hex);
  background: color-mix(in srgb, var(--c-main) 8%, transparent);
  transform: translateY(-1px);
}

/* 图片预览 */
.sf-img-preview {
  position: relative; width: 100%;
  border-radius: 12px; overflow: hidden;
  border: 1px solid #eee; background: #fff;
}
.sf-img-preview img {
  width: 100%; max-height: 260px; object-fit: cover;
  display: block;
}
.sf-img-remove {
  position: absolute; top: 8px; right: 8px;
  width: 28px; height: 28px; border-radius: 50%;
  border: none; background: rgba(0,0,0,0.55);
  color: #fff; font-size: 18px; line-height: 1;
  cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.25);
}
.sf-img-remove:hover {
  background: #ff5b6a; transform: scale(1.1);
}

/* 保存按钮 */
.sf-save-btn {
  width: 100%; padding: 14px 0; border: none; border-radius: 12px;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff;
  font-size: 15px; font-weight: 600; letter-spacing: 3px;
  cursor: pointer; transition: all 0.25s;
  box-shadow: 0 4px 16px rgba(110,180,100,0.35);
}
.sf-save-btn:hover:not(:disabled) {
  transform: translateY(-2px); box-shadow: 0 8px 24px rgba(110,180,100,0.45);
}
.sf-save-btn:disabled { background: #ccc; cursor: not-allowed; box-shadow: none; }

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .diary-page { padding: 0 20px 60px; }
  .hero { flex-direction: column; align-items: flex-start; }
  .hero-right { width: 100%; justify-content: space-between; }
  .top-grid { grid-template-columns: 1fr; }
  .book-stage { height: 380px; }
  .book-fan { width: 160px; height: 280px; }
  .diary-3d-card { width: 160px; height: 280px; }
  .date-day { font-size: 36px; }
  .front-emoji { width: 58px; height: 58px; }
  .ai-whisper { flex-direction: column; }
}
@media (max-width: 600px) {
  .book-stage { height: 340px; }
  .book-fan { width: 140px; height: 260px; }
  .diary-3d-card { width: 140px; height: 260px; }
  .face { padding: 16px; }
  .date-day { font-size: 30px; }
  .front-emoji { width: 50px; height: 50px; }
  .hero-meta { width: 100%; justify-content: space-between; }
  .meta-card { min-width: 0; padding: 8px 12px; flex: 1; }
  .chart-box { height: 180px; }
  .chart-card, .summary-card, .calendar-card { padding: 16px; }
}

/* ===== Tab 导航激活态 ===== */
.an-btn.on {
  background: #fff !important;
  color: var(--c-hex) !important;
  border-color: color-mix(in srgb, var(--c-main) 60%, transparent) !important;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-main) 30%, transparent);
  font-weight: 700;
}

/* ===== 长方形日记本 ===== */
.side-book { width: 100%; padding: 0; }

.notebook {
  position: relative;
  display: flex;
  background: #f5e6c8;
  border-radius: 6px 10px 10px 6px;
  box-shadow:
    0 2px 4px rgba(0,0,0,0.1),
    0 8px 20px rgba(120, 90, 40, 0.25),
    inset 0 0 0 1px rgba(120, 90, 40, 0.15);
  padding: 14px 14px 14px 26px;
}

/* 装订线圈（左侧） */
.nb-binding {
  position: absolute;
  left: 6px;
  top: 18px;
  bottom: 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
}
.nb-ring {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #8a6a3a;
  background: #f5e6c8;
  box-shadow: inset 0 0 2px rgba(0,0,0,0.2);
}

/* 本子主体 */
.nb-body {
  flex: 1;
  background: #fffdf5;
  border-radius: 4px 6px 6px 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 360px;
}

/* 封面头 */
.nb-cover {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color: #fff;
}
.nb-title { font-size: 14px; font-weight: 700; letter-spacing: 1px; }
.nb-count { font-size: 11px; opacity: 0.9; }

/* 横线纸页面 */
.nb-pages {
  flex: 1;
  padding: 8px 0;
  position: relative;
  background-color: #fffdf5;
  background-image:
    linear-gradient(to bottom, transparent 23px, var(--c-border) 23px, var(--c-border) 24px, transparent 24px);
  background-size: 100% 24px;
  background-position: 0 6px;
}

/* 空状态 */
.nb-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--c-text-weak);
}
.nb-empty p { margin: 0; font-size: 14px; }
.nb-empty-hint { margin-top: 6px !important; font-size: 12px !important; color: var(--c-text-weak) !important; }

/* 日记条目 */

/* 翻页日记单页 */
.nb-entry {
  padding: 14px 18px;
  cursor: pointer;
  transition: background 0.2s;
  min-height: 240px;
  animation: pageIn 0.35s ease;
}
@keyframes pageIn {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}
.nb-entry:hover { background: color-mix(in srgb, var(--c-main) 6%, transparent); }
.nb-entry-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--c-border);
}
.nb-entry-date { font-size: 13px; color: var(--c-text); font-weight: 700; font-family: "Kaiti", serif; }
.nb-entry-emoji { width: 22px; height: 22px; object-fit: contain; }
.nb-entry-mood { font-size: 12px; padding: 2px 10px; border-radius: 10px; font-weight: 600; color: var(--c-text); }
.nb-entry-text {
  margin: 0; font-size: 14px; line-height: 28px; color: #3a4a3a;
  font-family: "Kaiti", "楷体", serif; white-space: pre-wrap;
}
.nb-entry-img { max-width: 100%; max-height: 160px; object-fit: cover; border-radius: 8px; margin-top: 10px; display: block; }

/* 翻页控件 */
.nb-pager {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 18px 14px;
  border-top: 1px solid rgba(180, 190, 180, 0.3);
}
.nb-page-btn {
  background: linear-gradient(135deg, var(--c-main), var(--c-hex));
  color: #fff; border: none; padding: 6px 16px; border-radius: 16px;
  font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s;
  font-family: inherit;
}
.nb-page-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 3px 10px rgba(110,163,106,0.4); }
.nb-page-btn:disabled { background: var(--c-bg2); cursor: not-allowed; opacity: 0.6; }
.nb-page-num { font-size: 12px; color: #7a8a7a; font-weight: 600; font-family: "Kaiti", serif; }

/* ===== 系统代记（source=3）=====
   用低饱和暮色区分「机器代记」与「亲笔」：不能太抢眼（用户没参与），
   但必须一眼看出这条不是自己写的。 */
.auto-tag {
  background: linear-gradient(135deg, #efe7ff, #e4eeff) !important;
  color: #6b5aa8 !important;
  border: 1px dashed rgba(150, 128, 205, 0.55);
}
.nb-auto-tag {
  margin-left: auto;
  font-size: 11px; padding: 2px 8px; border-radius: 10px;
  color: #6b5aa8; background: #efe7ff;
  border: 1px dashed rgba(150, 128, 205, 0.5);
  white-space: nowrap;
}
.sum-auto-note {
  margin-top: 14px; padding: 12px 14px;
  display: flex; flex-wrap: wrap; align-items: center; gap: 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px dashed rgba(150, 128, 205, 0.45);
  font-size: 13px; line-height: 1.65; color: #6b6280;
  font-family: "Kaiti", "楷体", serif;
}
.sum-auto-btn {
  flex-shrink: 0; margin-left: auto;
  border: none; cursor: pointer;
  padding: 7px 14px; border-radius: 999px;
  background: linear-gradient(135deg, #a795d8, #8f7cc9);
  color: #fff; font-size: 12.5px; font-weight: 600;
  font-family: inherit;
  transition: transform 0.2s, box-shadow 0.2s;
}
.sum-auto-btn:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(143, 124, 201, 0.35); }
.edit-auto-hint {
  margin: 0 0 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f4f0ff;
  border: 1px dashed rgba(150, 128, 205, 0.45);
  color: #6b5aa8; font-size: 12.5px; line-height: 1.65;
}
</style>