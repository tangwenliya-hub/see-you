<template>
  <div class="hp-root" :class="skyClass">
    <!-- ========== ① 首屏：插画氛围 + 问候 ========== -->
    <section class="hero" @mousemove="onHeroMove" @mouseleave="onHeroReset">
      <!-- 天空 / 光晕 / 云 / 插画 -->
      <div class="hero-sky"></div>
      <div class="hero-sun"></div>
      <div class="cloud-layer" :style="cloudStyle">
        <span class="cloud c1"></span>
        <span class="cloud c2"></span>
        <span class="cloud c3"></span>
        <span class="cloud c4"></span>
      </div>
      <div class="hero-art"></div>
      <div class="spark-layer">
        <span class="spark s1"></span>
        <span class="spark s2"></span>
        <span class="spark s3"></span>
        <span class="spark s4"></span>
        <span class="spark s5"></span>
        <span class="spark s6"></span>
      </div>
      <div class="hero-blend"></div>

      <div class="hero-inner">
        <div class="hero-card">
          <!-- 情绪提醒（轻量版）：连续记录中断后才出现，点一下直达写日记；关掉后当天不再打扰 -->
          <div v-if="reminder" class="remind-bar">
            <span class="rb-icon">🌱</span>
            <span class="rb-text">{{ reminder.text }}</span>
            <router-link to="/diary/write" class="rb-btn">去记录</router-link>
            <button class="rb-close" title="今天不再提醒" @click="dismissReminder">✕</button>
          </div>

          <div class="hc-top">
            <span class="hc-chip">{{ weekText }} · {{ dateText }}</span>
            <span class="hc-chip soft">{{ moodHint }}</span>
          </div>

          <h1 class="hero-hi">
            {{ greeting }}，<em class="hero-name">{{ nickname }}</em>
          </h1>

          <p class="hero-quote">{{ dailyQuote }}</p>

          <div class="hero-actions">
            <router-link to="/diary/write" class="hp-btn primary">写下今天的心情</router-link>
            <router-link to="/assistant" class="hp-btn ghost">
              和绪见AI聊聊<span class="btn-arrow">→</span>
            </router-link>
          </div>

          <p v-if="!isLoggedIn" class="hero-guest">
            还没有记录？<router-link to="/login">登入 / 注册</router-link> 后可以保存你的情绪轨迹
          </p>

          <!-- 今日心情打卡 -->
          <div v-if="isLoggedIn" class="mood-bar">
            <span class="mood-label">今天感觉怎么样</span>
            <div class="mood-row">
              <button
                v-for="e in emotions"
                :key="e.val"
                class="mood-btn"
                :class="{ done: todayEmotion === e.val }"
                :style="{ '--mood': e.bg }"
                :title="e.label"
                @click="pickMood(e)"
              >
                <img :src="e.icon" :alt="e.label" />
                <span>{{ e.label }}</span>
              </button>
            </div>
            <span class="mood-state">{{ todayMoodText }}</span>

            <!-- 情绪强度选择：点情绪后弹出，选完直接带去写日记 -->
            <div v-if="pickTarget" class="intensity-pick">
              <span class="ip-label">这份「{{ pickTarget.label }}」有多强烈？</span>
              <div class="ip-row">
                <button
                  v-for="lv in intensityLevels" :key="lv.val"
                  class="ip-btn" :style="{ '--ip': lv.bg }"
                  @click="goWriteWith(pickTarget, lv.val)"
                >
                  <b>{{ lv.label }}</b><i>{{ lv.desc }}</i>
                </button>
                <button class="ip-cancel" @click="pickTarget = null">✕</button>
              </div>
            </div>
          </div>

          <!-- 情绪风险预警消息卡（长期连续负面数据触发，非医疗诊断） -->
          <RiskAlertCard v-if="isLoggedIn" />

          <!-- 无感记录：今日推测（仅推荐，用户拥有最终决定权） -->
          <SenseSuggestCard v-if="isLoggedIn" />

          <!-- 快捷入口 -->
          <nav class="entry-row">
            <router-link v-for="q in quickEntries" :key="q.to + q.title" :to="q.to" class="entry-item">
              <span class="entry-icon"><img :src="q.icon" :alt="q.title" /></span>
              <span class="entry-text">{{ q.title }}</span>
            </router-link>
          </nav>

          <!-- 数据胶囊 -->
          <div v-if="isLoggedIn" class="stat-row">
            <router-link v-for="s in statList" :key="s.label" :to="s.to" class="stat-chip">
              <img :src="s.icon" :alt="s.label" />
              <span class="stat-num">{{ s.value }}</span>
              <span class="stat-text">{{ s.label }}</span>
            </router-link>
            <!-- 本月积极/消极占比小卡片（与日记页图表联动） -->
            <router-link to="/diary" class="mood-ratio">
              <div class="mr-head">
                <span class="mr-title">本月情绪天平</span>
                <span v-if="moodRatio.total" class="mr-legend">
                  <i class="dot pos"></i>积极 {{ moodRatio.posPct }}%
                  <i class="dot neg"></i>消极 {{ moodRatio.negPct }}%
                </span>
                <span v-else class="mr-legend">写下日记后，这里会出现你的情绪天平</span>
                <span class="mr-more">看图表 →</span>
              </div>
              <div v-if="moodRatio.total" class="mr-bar">
                <i class="pos" :style="{ width: moodRatio.posPct + '%' }"></i>
                <i class="neg" :style="{ width: moodRatio.negPct + '%' }"></i>
              </div>
            </router-link>
          </div>
        </div>
      </div>

      <div class="scroll-hint" @click="scrollToOverview">
        <span>看看最近的情绪轨迹</span>
        <span class="scroll-line"></span>
      </div>
    </section>

    <!-- ========== 内容区 ========== -->
    <div class="hp-body">
      <!-- ② 情绪概览 -->
      <section id="hp-overview" class="sec">
        <header class="sec-head rv">
          <div class="sec-head-text">
            <p class="sec-kicker">OVERVIEW</p>
            <h2 class="sec-title">我的情绪概览</h2>
            <p class="sec-sub">记录是疗愈的开始，回头看才发现自己走了多远</p>
          </div>
          <div class="sec-side">
            <img class="sec-mascot" src="/img/开心牛牛.png" alt="" />
            <router-link to="/diary" class="sec-more">全部日记 <span>→</span></router-link>
          </div>
        </header>

        <div v-if="isLoggedIn" class="grid-2">
          <article class="card trend-card rv">
            <div class="card-head">
              <h3>近 14 天情绪指数</h3>
              <span class="tag">{{ trendDays }} 天有记录</span>
            </div>

            <svg v-if="trendPoints.length > 1" class="trend-svg" viewBox="0 0 640 220" preserveAspectRatio="none">
              <defs>
                <linearGradient id="hpTrendGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="currentColor" stop-opacity="0.38" />
                  <stop offset="100%" stop-color="currentColor" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path class="trend-area" :d="areaPath" />
              <path class="trend-line" :d="linePath" />
              <circle v-for="(p, i) in trendPoints" :key="i" :cx="p.x" :cy="p.y" r="5" class="trend-dot" />
            </svg>
            <div v-if="trendPoints.length > 1" class="trend-axis">
              <span v-for="(p, i) in trendPoints" :key="'x' + i" :style="{ left: p.xPct + '%' }">{{ p.label }}</span>
            </div>
            <p v-else class="empty">还没有足够的记录 · 连续写 2 天日记，这里就会画出你的情绪曲线</p>
          </article>

          <article class="card rv">
            <div class="card-head">
              <h3>本月情绪分布</h3>
              <span class="tag">{{ currentMonth }}</span>
            </div>

            <div v-if="monthDist.length" class="dist-list">
              <div v-for="d in monthDist" :key="d.val" class="dist-row">
                <img class="dist-icon" :src="d.icon" :alt="d.label" />
                <span class="dist-label">{{ d.label }}</span>
                <span class="dist-bar"><i :style="{ width: d.pct + '%', background: d.bg }"></i></span>
                <span class="dist-pct">{{ d.pct }}%</span>
              </div>
            </div>
            <p v-else class="empty">本月还没有日记，写下第一篇吧</p>

            <div class="whisper">
              <img class="whisper-avatar" src="/img/AI对话历史.png" alt="" />
              <div class="whisper-body">
                <span class="whisper-tag">AI 寄语</span>
                <p class="whisper-text">{{ whisper || '记录心情本身就是一种疗愈，慢慢来也没关系。' }}</p>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="card guest-card rv">
          <h3>从这里开始，记录你的第一条情绪</h3>
          <p class="guest-desc">
            绪见会把你的心情画成曲线、生成周月小结，并推荐适合你的治愈音频与心理科普。
          </p>
          <div class="guest-actions">
            <router-link to="/login" class="hp-btn primary">立即登入</router-link>
            <router-link to="/test" class="hp-btn ghost">先做个测评<span class="btn-arrow">→</span></router-link>
          </div>
        </div>
      </section>

      <!-- ③ 今日推荐 -->
      <section class="sec">
        <header class="sec-head rv">
          <div class="sec-head-text">
            <p class="sec-kicker">FOR YOU</p>
            <h2 class="sec-title">今日推荐</h2>
            <p class="sec-sub">为你挑的治愈内容，点一下就能继续</p>
          </div>
          <div class="sec-side">
            <img class="sec-mascot" src="/img/平静牛牛.png" alt="" />
            <div class="tab-group">
              <button
                v-for="t in tabs" :key="t.key"
                class="tab-btn" :class="{ active: activeTab === t.key }"
                @click="activeTab = t.key"
              >{{ t.label }}</button>
            </div>
          </div>
        </header>

        <div class="rec-grid">
          <article
            v-for="item in recList" :key="item.id"
            class="rec-card rv" @click="$router.push(item.to)"
          >
            <div class="rec-thumb" :style="{ background: item.bg }">
              <img v-if="item.cover" :src="item.cover" :alt="item.title" @error="item.cover = ''" />
              <img v-else class="rec-fallback" :src="item.icon" :alt="item.title" />
            </div>
            <div class="rec-body">
              <h4 class="rec-title">{{ item.title }}</h4>
              <p class="rec-desc">{{ item.desc }}</p>
              <div class="rec-foot">
                <span class="rec-meta">{{ item.meta }}</span>
                <span class="rec-go">{{ item.go }}<i>→</i></span>
              </div>
            </div>
          </article>
          <p v-if="!recList.length" class="empty">{{ isLoggedIn ? '暂无推荐内容，去对应页面看看吧' : '登入后可查看这类推荐内容' }}</p>
        </div>
      </section>

      <!-- ④ 最近足迹 -->
      <section v-if="isLoggedIn" class="sec">
        <header class="sec-head rv">
          <div class="sec-head-text">
            <p class="sec-kicker">RECENT</p>
            <h2 class="sec-title">最近足迹</h2>
            <p class="sec-sub">你最近写下的心情和做过的测评</p>
          </div>
          <div class="sec-side">
            <img class="sec-mascot" src="/img/惊讶牛牛.png" alt="" />
          </div>
        </header>

        <div class="grid-2">
          <article class="card rv">
            <div class="card-head">
              <h3>最近日记</h3>
              <router-link to="/diary" class="card-more">全部 <span>→</span></router-link>
            </div>
            <ul v-if="recentDiaries.length" class="foot-list">
              <li v-for="d in recentDiaries" :key="d.id" class="foot-item" @click="$router.push('/diary')">
                <span class="foot-date">{{ d.day }}</span>
                <img class="foot-icon" :src="d.icon" :alt="d.label" />
                <div class="foot-body">
                  <p class="foot-title">{{ d.label }}的一天</p>
                  <p class="foot-desc">{{ d.content }}</p>
                </div>
              </li>
            </ul>
            <p v-else class="empty">还没有日记，点击上方「写下今天的心情」开始第一次记录</p>
          </article>

          <article class="card rv">
            <div class="card-head">
              <h3>最近测评</h3>
              <router-link to="/test" class="card-more">去测评 <span>→</span></router-link>
            </div>
            <ul v-if="recentRecords.length" class="foot-list">
              <li v-for="r in recentRecords" :key="r.id" class="foot-item" @click="$router.push('/test')">
                <span class="foot-date">{{ r.day }}</span>
                <div class="foot-score" :style="{ background: r.bg, color: r.fg }">{{ r.score }}</div>
                <div class="foot-body">
                  <p class="foot-title">{{ r.testName }}</p>
                  <p class="foot-desc">{{ r.level }} · {{ r.tip || '点击查看完整报告' }}</p>
                </div>
              </li>
            </ul>
            <p v-else class="empty">还没做过测评，去「绪见测试」挑一个量表试试</p>
          </article>
        </div>
      </section>

      <!-- ⑤ 治愈角落（页脚） -->
      <footer class="heal-corner rv">
        <div class="hc-crew">
          <img v-for="(m, i) in crew" :key="m" :src="m" class="crew-item" :style="{ animationDelay: (i * 0.5) + 's' }" alt="" />
        </div>
        <p class="hc-title">今天也谢谢你，愿意好好照顾自己的情绪</p>
        <p class="hc-slogan">绪见 · 每个人的坏情绪都值得被温柔对待</p>
        <nav class="hc-links">
          <router-link to="/feedback">反馈建议</router-link>
          <router-link to="/profile">个人中心</router-link>
          <a href="javascript:void(0)" @click="backToTop">回到顶部</a>
        </nav>
        <p class="hc-disclaimer">
          本站量表仅用于自我了解，不能替代专业诊断。若持续感到痛苦，请及时寻求专业机构或医生帮助。
        </p>
        <p class="hc-copy">© 2026 绪见</p>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { EMOTIONS as emotions } from '@/utils/constants'  // 情绪字典唯一真源（utils/constants.js）
import { useRouter } from 'vue-router'
import request, { getDiaryList, getRecordList, getTestPaperList, generateWhisper, fetchAllPages } from '../api'
import '../styles/home.css'
import SenseSuggestCard from '../components/SenseSuggestCard.vue'
import RiskAlertCard from '../components/RiskAlertCard.vue'

const router = useRouter()

// ===== 登录态 =====
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))
const isLoggedIn = computed(() => !!localStorage.getItem('token') && !!userInfo.value)
const userId = computed(() => userInfo.value?.userId || userInfo.value?.id || null)
const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '朋友')

// ===== 首屏文案 =====
const now = new Date()
const hour = now.getHours()
const greeting = hour < 6 ? '夜深了' : hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'
const weekNames = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const weekText = weekNames[now.getDay()]
const dateText = `${now.getMonth() + 1} 月 ${now.getDate()} 日`

// 天空色随一天的时间变化（清晨 / 白天 / 黄昏 / 夜晚）
const skyClass = computed(() => {
  if (hour < 8) return 'sky-dawn'
  if (hour < 17) return 'sky-day'
  if (hour < 20) return 'sky-dusk'
  return 'sky-night'
})

const quotePool = [
  '允许自己慢下来，情绪也需要喘口气',
  '今天不用很厉害，好好吃饭就已经很棒了',
  '你已经做得很好了，剩下的交给时间',
  '难过的时候不要一个人扛，说出来会轻一点',
  '每一次记录，都是在和自己好好相处',
  '累了就停下来，你不是机器',
  '慢慢来也没关系，路还很长',
]
const dailyQuote = quotePool[(now.getMonth() * 31 + now.getDate()) % quotePool.length]

const moodHintPool = [
  '今天也要好好照顾自己',
  '情绪没有对错，先接住它',
  '写下一句，也算记录',
  '慢慢来，比较快',
  '你不需要时刻都开心',
]
const moodHint = moodHintPool[(now.getMonth() * 7 + now.getDate()) % moodHintPool.length]

const crew = [
  '/img/开心牛牛.png',
  '/img/生气牛牛.png',
  '/img/伤心牛牛.png',
  '/img/平静牛牛.png',
  '/img/惊讶牛牛.png',
]

// ===== 视差 & 滚动浮现 =====
const cloudStyle = ref({})
let rafId = null
const onHeroMove = (e) => {
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = null
    const x = (e.clientX / window.innerWidth - 0.5) * 2   // -1 ~ 1
    const y = (e.clientY / window.innerHeight - 0.5) * 2
    cloudStyle.value = {
      '--px': `${-x * 12}px`,
      '--py': `${-y * 8}px`,
      '--ax': `${-x * 22}px`,
      '--ay': `${-y * 14}px`,
    }
  })
}
const onHeroReset = () => {
  cloudStyle.value = { '--px': '0px', '--py': '0px', '--ax': '0px', '--ay': '0px' }
}
onHeroReset()

let io = null
const initReveal = () => {
  const els = document.querySelectorAll('.rv:not(.in)')
  if (!els.length) return
  if (!('IntersectionObserver' in window)) { els.forEach(el => el.classList.add('in')); return }
  if (io) io.disconnect()
  io = new IntersectionObserver((entries) => {
    entries.forEach((en, i) => {
      if (en.isIntersecting) {
        setTimeout(() => en.target.classList.add('in'), i * 90)
        io.unobserve(en.target)
      }
    })
  }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' })
  els.forEach(el => io.observe(el))
}

// ===== 情绪配置（与 Diary / WriteDiary 一致）=====
const emotionInfo = (v) => emotions.find(e => e.val === v) || emotions[8]
const scoreMap = { 1: 100, 7: 95, 8: 85, 2: 70, 9: 50, 3: 50, 6: 40, 5: 25, 4: 20 }
const scoreOf = (v) => scoreMap[v] ?? 50

const pad = (n) => String(n).padStart(2, '0')
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const todayKey = ymd(now)

// ===== 数据 =====
const diaries = ref([])
const records = ref([])
const audios = ref([])
const videos = ref([])
const papers = ref([])
const collectCount = ref(0)
const whisper = ref('')

const quickEntries = [
  { to: '/diary', icon: '/img/绪见日记.png', title: '日记' },
  { to: '/assistant', icon: '/img/AI对话历史.png', title: 'AI' },
  { to: '/test', icon: '/img/测评量表.png', title: '测评' },
  { to: '/fm', icon: '/img/治愈音频.png', title: 'FM' },
  { to: '/classroom', icon: '/img/心理视频.png', title: '学堂' },
  { to: '/profile?tab=weekReport', icon: '/img/情绪周报.png', title: '周报' },
  { to: '/profile?tab=fav', icon: '/img/我的收藏.png', title: '收藏' },
  { to: '/feedback', icon: '/img/用户反馈.png', title: '反馈' },
]

const loadAll = async () => {
  // 游客路径也必须 await：否则 loadAll 立即 resolve，initReveal 会在推荐卡渲染前跑完，
  // 后出现的 .rv 卡片永远不会被浮现观察器接管 → opacity:0（表现为推荐区一片空白）
  if (!isLoggedIn.value) { await loadPublic(); return }
  const uid = userId.value
  try {
    // 翻页拉全（后端单页封顶 100），主页 14 天趋势/连续天数/今日打卡都依赖这份列表
    const all = await fetchAllPages(page => getDiaryList({ page, size: 100, userId: uid }))
    diaries.value = (Array.isArray(all) ? all : [])
      // 后端字段是 diaryContent（不是 content），取错会导致摘要恒为空
      .map(d => ({ id: d.diaryId || d.id, date: d.diaryDate, emotion: d.emotion, content: d.diaryContent || '', source: d.source }))
      .sort((a, b) => String(b.date).localeCompare(String(a.date)))
  } catch (e) { diaries.value = [] }

  try {
    const res = await getRecordList({ userId: uid, page: 1, size: 20 })
    const list = res?.data?.records || res?.data || []
    records.value = (Array.isArray(list) ? list : []).map(r => ({
      id: r.recordId || r.id,
      testName: r.paperName || r.testName || '心理测评',
      score: r.score ?? 0,
      level: r.level || '',
      tip: r.tip || '',
      time: r.createTime || '',
    }))
  } catch (e) { records.value = [] }

  try {
    const res = await generateWhisper(uid)
    if (res?.code === 200 && res.data) whisper.value = res.data
  } catch (e) { /* 忽略 */ }

  await loadPublic()
}

const loadPublic = async () => {
  try {
    const res = await request.get('/audio/list', { params: { page: 1, size: 50 } })
    const list = Array.isArray(res?.data) ? res.data : (res?.data?.records || [])
    audios.value = list
      .map(a => ({ audioId: a.audioId || a.id, audioName: a.audioName || a.audio_name || '治愈音频', category: a.category || '舒缓', coverImg: a.coverImg || a.cover_img || '', playCount: a.playCount || a.play_count || 0 }))
      .sort((x, y) => y.playCount - x.playCount)
      .slice(0, 4)
  } catch (e) { audios.value = [] }

  try {
    const res = await request.get('/video/list', { params: { page: 1, size: 50 } })
    const list = Array.isArray(res?.data) ? res.data : (res?.data?.records || [])
    videos.value = list
      .map(v => ({ videoId: v.videoId || v.id, videoName: v.videoName || v.video_name || '心理视频', category: v.category || '科普', coverImg: v.coverImg || v.cover_img || '', playCount: v.playCount || v.play_count || 0, description: v.description || '' }))
      .sort((x, y) => y.playCount - x.playCount)
      .slice(0, 4)
  } catch (e) { videos.value = [] }

  try {
    const res = await getTestPaperList()
    const data = res?.data
    const list = Array.isArray(data) ? data : (data?.records || [])
    papers.value = list
      .map(p => ({ testId: p.testId, testName: p.testName, desc: p.testDesc || '专业心理测评量表，帮助你了解自己的心理状态。', questionCount: p.questionCount || 10, takenCount: p.takenCount || 0 }))
      .sort((x, y) => y.takenCount - x.takenCount)
      .slice(0, 4)
  } catch (e) { papers.value = [] }

  if (isLoggedIn.value) {
    try {
      // 只要总数：优先用分页 total，不会被单页 100 上限卡住
      const res = await request.get('/collect/list', { params: { userId: userId.value, page: 1, size: 100 } })
      const d = res?.data
      collectCount.value = d?.total ?? (Array.isArray(d) ? d.length : (d?.records?.length || 0))
    } catch (e) { collectCount.value = 0 }
  }
}

// ===== 统计 =====
const streakDays = computed(() => {
  if (!diaries.value.length) return 0
  const dates = new Set(diaries.value.map(d => String(d.date).slice(0, 10)))
  let n = 0
  const d = new Date()
  if (!dates.has(ymd(d))) d.setDate(d.getDate() - 1)
  while (dates.has(ymd(d))) { n++; d.setDate(d.getDate() - 1) }
  return n
})

const monthKey = `${now.getFullYear()}-${pad(now.getMonth() + 1)}`
const currentMonth = `${now.getMonth() + 1} 月`
const monthTotal = computed(() => diaries.value.filter(d => String(d.date).startsWith(monthKey)).length)

const statList = computed(() => ([
  { label: '天连续记录', value: streakDays.value, to: '/diary', icon: '/img/本月打卡.png' },
  { label: '篇本月日记', value: monthTotal.value, to: '/diary', icon: '/img/我的日记本.png' },
  { label: '次完成测评', value: records.value.length, to: '/test', icon: '/img/测评量表.png' },
  { label: '个收藏', value: collectCount.value, to: '/profile?tab=fav', icon: '/img/我的收藏.png' },
]))

// ===== 情绪提醒（轻量版）=====
// 规则：登录 + 写过日记 + 距上次记录 ≥2 天才提醒；从没写过的新用户不打扰；关闭后当天不再出现。
// 数据完全复用已加载的 diaries，不额外发请求。
const REMIND_DISMISS_KEY = 'remindDismissedAt'
const remindDismissedAt = ref(localStorage.getItem(REMIND_DISMISS_KEY) || '')
const lastDiaryDate = computed(() => {
  if (!diaries.value.length) return null
  return diaries.value.map(d => String(d.date).slice(0, 10)).sort().pop() || null
})
const gapDays = computed(() => {
  if (!lastDiaryDate.value) return null
  const last = new Date(`${lastDiaryDate.value}T00:00:00`).getTime()
  const today0 = new Date(`${todayKey}T00:00:00`).getTime()
  return Math.max(0, Math.round((today0 - last) / 86400000))
})
const reminder = computed(() => {
  if (!isLoggedIn.value) return null
  if (gapDays.value === null || gapDays.value < 2) return null      // 新用户 / 今天或昨天刚写过
  if (remindDismissedAt.value === todayKey) return null             // 今天已手动关掉
  return { gap: gapDays.value, text: `你已经 ${gapDays.value} 天没写日记啦，今天也来记录一下吧` }
})
const dismissReminder = () => {
  remindDismissedAt.value = todayKey
  localStorage.setItem(REMIND_DISMISS_KEY, todayKey)
}

const todayDiary = computed(() => diaries.value.find(d => String(d.date).slice(0, 10) === todayKey) || null)
const todayEmotion = computed(() => todayDiary.value?.emotion ?? null)
/** source=3：系统自动代记，用户当天没打开 App 也算「今天记过了」 */
const todayIsAuto = computed(() => Number(todayDiary.value?.source) === 3)
const todayMoodText = computed(() => {
  if (!todayDiary.value) return '点一下情绪，再补一句话，今天的日记就完成啦'
  if (todayIsAuto.value) return `今天系统已替你记下：${emotionInfo(todayDiary.value.emotion).label}（无感代记，打卡不会断）`
  return `今天已记录：${emotionInfo(todayDiary.value.emotion).label}`
})

// 本月积极 / 消极情绪占比（积极：开心·兴奋·满足·平静；消极：疲惫·焦虑·难过·生气）
const POSITIVE = [1, 7, 8, 2]
const NEGATIVE = [3, 6, 4, 5]
const moodRatio = computed(() => {
  const list = diaries.value.filter(d => String(d.date).startsWith(monthKey))
  let pos = 0, neg = 0
  list.forEach(d => {
    if (POSITIVE.includes(d.emotion)) pos++
    else if (NEGATIVE.includes(d.emotion)) neg++
  })
  const total = pos + neg
  if (!total) return { total: 0, posPct: 0, negPct: 0 }
  const posPct = Math.round((pos / total) * 100)
  return { total, pos, neg, posPct, negPct: 100 - posPct }
})

const pickTarget = ref(null)
const intensityLevels = [
  { val: 1, label: '轻度', desc: '有一点点', bg: '#b8d49a' },
  { val: 2, label: '中度', desc: '比较明显', bg: '#ffd09a' },
  { val: 3, label: '重度', desc: '非常强烈', bg: '#f0b98c' },
]
const pickMood = (e) => {
  // 再点同一个情绪 = 收起强度选择
  pickTarget.value = pickTarget.value?.val === e.val ? null : e
}
const goWriteWith = (e, level) => {
  router.push({ path: '/diary/write', query: { emotion: e.val, intensity: level } })
}

// 近 14 天趋势
const trendPoints = computed(() => {
  const map = {}
  diaries.value.forEach(d => { const k = String(d.date).slice(0, 10); if (!map[k]) map[k] = d.emotion })
  const pts = []
  for (let i = 13; i >= 0; i--) {
    const dt = new Date(now)
    dt.setDate(dt.getDate() - i)
    const k = ymd(dt)
    if (map[k] == null) continue
    const dayIndex = 13 - i
    pts.push({
      x: 30 + Math.round((dayIndex / 13) * 580),
      y: 190 - Math.round((scoreOf(map[k]) / 100) * 150),
      xPct: Math.round((dayIndex / 13) * 100),
      label: `${dt.getMonth() + 1}/${dt.getDate()}`,
      score: scoreOf(map[k]),
    })
  }
  return pts
})
const trendDays = computed(() => trendPoints.value.length)
// 平滑曲线（相邻点用水平控制点的三次贝塞尔）
const linePath = computed(() => {
  const p = trendPoints.value
  if (p.length < 2) return ''
  let d = `M ${p[0].x} ${p[0].y}`
  for (let i = 0; i < p.length - 1; i++) {
    const cx = (p[i].x + p[i + 1].x) / 2
    d += ` C ${cx} ${p[i].y} ${cx} ${p[i + 1].y} ${p[i + 1].x} ${p[i + 1].y}`
  }
  return d
})
const areaPath = computed(() => {
  const pts = trendPoints.value
  if (pts.length < 2) return ''
  return `${linePath.value} L ${pts[pts.length - 1].x} 190 L ${pts[0].x} 190 Z`
})

// 本月情绪分布
const monthDist = computed(() => {
  const list = diaries.value.filter(d => String(d.date).startsWith(monthKey))
  if (!list.length) return []
  const cnt = {}
  list.forEach(d => { cnt[d.emotion] = (cnt[d.emotion] || 0) + 1 })
  const total = list.length
  return Object.entries(cnt)
    .map(([k, v]) => {
      const info = emotionInfo(Number(k))
      return { val: k, label: info.label, icon: info.icon, bg: info.bg, count: v, pct: Math.round((v / total) * 100) }
    })
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)
})

const recentDiaries = computed(() => diaries.value.slice(0, 3).map(d => {
  const info = emotionInfo(d.emotion)
  const parts = String(d.date).slice(5).split('-')
  return {
    id: d.id,
    day: `${parts[0]}/${parts[1]}`,
    icon: info.icon,
    label: info.label,
    content: d.content || '这天没有留下文字，但记录本身已足够珍贵。',
  }
}))

const levelColor = (level = '') => {
  if (level.includes('正常') || level.includes('无') || level.includes('低')) return { bg: '#eaf5e9', fg: '#3f6f20' }
  if (level.includes('重')) return { bg: '#fdeaea', fg: '#a63a3a' }
  return { bg: '#fdf3e3', fg: '#8a5b12' }
}
const recentRecords = computed(() => records.value.slice(0, 3).map(r => {
  const c = levelColor(r.level)
  return {
    id: r.id,
    day: String(r.time).slice(5, 10).replace('-', '/') || '--',
    testName: r.testName,
    score: r.score,
    level: r.level || '已完成',
    tip: r.tip,
    bg: c.bg,
    fg: c.fg,
  }
}))

// ===== 今日推荐 =====
const tabs = [
  { key: 'audio', label: '治愈音频' },
  { key: 'video', label: '心理视频' },
  { key: 'paper', label: '心理测评' },
]
const activeTab = ref('audio')
// 切 Tab 后 recList 变化会渲染一批新的 .rv 卡片，必须重新挂浮现观察器，否则新卡片永远透明
watch(activeTab, () => nextTick(() => setTimeout(initReveal, 60)))

const recList = computed(() => {
  if (activeTab.value === 'audio') {
    return audios.value.map(a => ({
      id: 'a' + a.audioId,
      title: a.audioName,
      desc: a.category ? `${a.category}频道 · 适合睡前与放松` : '适合睡前与放松',
      meta: `${a.playCount} 次播放`,
      cover: a.coverImg,
      icon: '/img/治愈音频.png',
      bg: '#eef4f9',
      go: '去收听',
      to: { path: '/fm', query: { audioId: a.audioId || a.id } },
    }))
  }
  if (activeTab.value === 'video') {
    return videos.value.map(v => ({
      id: 'v' + v.videoId,
      title: v.videoName,
      desc: v.description || (v.category ? `${v.category} · 心理科普` : '心理科普视频'),
      meta: `${v.playCount} 次播放`,
      cover: v.coverImg,
      icon: '/img/心理视频.png',
      bg: '#f5eef7',
      go: '去观看',
      to: { path: '/classroom', query: { videoId: v.videoId } },
    }))
  }
  return papers.value.map(p => ({
    id: 'p' + p.testId,
    title: p.testName,
    desc: p.desc,
    meta: `${p.questionCount} 题 · ${p.takenCount} 人测过`,
    cover: '',
    icon: '/img/测评量表.png',
    bg: '#eef5e9',
    go: '去测评',
    to: { path: '/test', query: { testId: p.testId } },
  }))
})

// ===== 交互 =====
const scrollToOverview = () => {
  document.getElementById('hp-overview')?.scrollIntoView({ behavior: 'smooth' })
}
const backToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  // 数据加载完成后再挂浮现观察器（推荐卡等是数据到达后才渲染的）
  loadAll().finally(() => setTimeout(initReveal, 200))
  requestAnimationFrame(() => {
    document.querySelectorAll('.hero-card > *').forEach((el, i) => {
      el.style.animationDelay = `${0.12 + i * 0.09}s`
    })
  })
})

onBeforeUnmount(() => {
  if (io) io.disconnect()
  if (rafId) cancelAnimationFrame(rafId)
})
</script>
