<template>
  <div class="test-page" :style="themeVars">
    <!-- ===== 顶部海报横幅（动漫风：渐变天空 + 吉祥物 + 星星 + 波浪底边） ===== -->
    <header class="hero">
      <div class="hero-blob b1"></div>
      <div class="hero-blob b2"></div>
      <span class="hero-star s1"><AppIcon name="sparkles" /></span>
      <span class="hero-star s2"><AppIcon name="sparkles" /></span>
      <span class="hero-star s3"><AppIcon name="sparkles" /></span>
      <span class="hero-cloud c1"><AppIcon name="cloud" /></span>
      <span class="hero-cloud c2"><AppIcon name="cloud" /></span>
      <div class="hero-inner">
        <div class="hero-title">
          <h1>测一测，更温柔地了解自己</h1>
          <p class="hero-sub">科学量表 · 匿名安心 · 结果即刻生成</p>
        </div>
        <!-- 吉祥物小熊：弹性布局中间项，自动落在标题与右侧统计的空档 -->
        <div class="hero-mascot">ʕ •ᴥ• ʔ</div>
        <div class="hero-meta">
          <div class="meta-chip"><b>{{ totalTests }}</b> 可用量表</div>
          <div class="meta-chip"><b>{{ myHistory.length }}</b> 我的测评</div>
          <div class="meta-chip"><b>{{ latestScore }}</b> 最近得分</div>
          <button class="history-btn" @click="scrollToHistory"><AppIcon name="clipboard" /> 我的测评记录</button>
        </div>
      </div>
      <svg class="hero-wave" viewBox="0 0 1440 70" preserveAspectRatio="none">
        <path d="M0,40 C240,80 480,0 720,30 C960,60 1200,10 1440,40 L1440,70 L0,70 Z" fill="#ffffff"/>
      </svg>
    </header>

    <!-- ===== 分类筛选磁贴 + 搜索（磁贴即筛选项，选中高亮） ===== -->
    <nav class="cat-nav">
      <div class="quick-tiles">
        <div
          v-for="(cat, i) in quickCats"
          :key="cat.key"
          :class="['quick-tile', { on: activeCat === cat.key }]"
          @click="activeCat = cat.key"
        >
          <div class="tile-icon" :style="{ background: tileBg(i) }"><AppIcon :name="cat.icon" /></div>
          <span class="tile-name">{{ cat.label }}</span>
        </div>
      </div>
      <div class="search-box">
        <span class="search-icon"><AppIcon name="search" /></span>
        <input v-model="searchKeyword" type="text" placeholder="搜索量表名称..." class="search-input"/>
        <button v-if="searchKeyword" class="search-clear" @click="searchKeyword = ''"><AppIcon name="close" /></button>
      </div>
    </nav>

    <!-- ===== 量表卡片网格 ===== -->
    <main class="scale-grid">
      <article
        v-for="paper in filteredPapers"
        :key="paper.testId"
        :class="['scale-card', cardColor(paper.testId)]"
        @click="showPaperDetail(paper)"
      >
        <!-- 头部：图标方块 + 标题 + 收藏 -->
        <div class="sc-head">
          <span class="sc-icon"><AppIcon :name="paperIcon(paper.testName)" /></span>
          <h2 class="scale-name">{{ paper.testName }}</h2>
          <button
            class="favorite-btn"
            :class="{ on: isFavorite(paper.testId) }"
            @click.stop="toggleFavorite(paper.testId)"
            :title="isFavorite(paper.testId) ? '取消收藏' : '收藏量表'"
          ><AppIcon :name="isFavorite(paper.testId) ? 'heart-solid' : 'heart'" /></button>
        </div>

        <!-- 元信息一行：规模徽章 · 题数 · 时长 -->
        <div class="scale-meta">
          <span class="sc-badge">{{ sizeLabel(paper) }}</span>
          <span class="meta-dot"></span>
          <span>{{ paper.questionCount || paper.questions || 10 }} 题</span>
          <span class="meta-dot"></span>
          <span>约 {{ estimateMinutes(paper) }} 分钟</span>
        </div>

        <!-- 描述：最多两行，超出截断 -->
        <p class="scale-desc">{{ paper.description || '专业心理测评量表，帮助你了解自己的心理状态。' }}</p>

        <!-- 底部：参与人数 + 通栏主按钮 -->
        <div class="sc-foot">
          <span class="sc-taken"><AppIcon name="users" /> {{ paper.takenCount || 0 }} 人已测</span>
          <button class="start-btn" @click.stop="showPaperDetail(paper)"><AppIcon name="sparkles" /> 开始测评</button>
        </div>
      </article>
    </main>

    <!-- ===== 空状态 ===== -->
    <div v-if="filteredPapers.length === 0" class="empty">
      <div class="empty-illus"><AppIcon name="flower" /></div>
      <p v-if="searchKeyword">没有找到与 "<strong>{{ searchKeyword }}</strong>" 相关的量表</p>
      <p v-else>这个分类还没有量表哦~</p>
      <div class="empty-hint">
        <p v-if="searchKeyword">试试搜索：</p>
        <p v-else>换个分类看看吧</p>
        <div class="hot-tags">
          <span v-for="tag in hotSearchTags" :key="tag" class="hot-tag" @click="searchKeyword = tag">{{ tag }}</span>
        </div>
      </div>
    </div>

    <!-- ===== 趋势图 ===== -->
    <section v-if="trendData.length > 1" class="trend-area">
      <h3 class="sec-title"><AppIcon name="trending-up" /> 测评趋势 · {{ trendPaperName }}</h3>
      <div class="trend-card">
        <div class="trend-chart">
          <svg viewBox="0 0 600 200" preserveAspectRatio="none" class="trend-svg">
            <line class="tl-axis" x1="40" y1="20" x2="40" y2="170"/>
            <line class="tl-axis" x1="40" y1="170" x2="580" y2="170"/>
            <polyline class="tl-line" :points="trendPolyline"/>
            <circle v-for="(point, i) in trendPoints" :key="i" class="tl-dot" :cx="point.x" :cy="point.y" r="5"/>
            <text v-for="(point, i) in trendPoints" :key="'t' + i" class="tl-num" :x="point.x" :y="point.y - 12" text-anchor="middle" font-size="12">{{ point.score }}</text>
          </svg>
          <div class="trend-labels">
            <span v-for="(item, i) in trendData" :key="i" class="trend-label">{{ item.date }}</span>
          </div>
        </div>
        <p class="trend-tip">{{ trendDirection }}</p>
      </div>
    </section>

    <!-- ===== 我的测评记录 ===== -->
    <section v-if="myHistory.length > 0" id="history" class="history-area">
      <h3 class="sec-title"><AppIcon name="file-text" /> 我的测评记录</h3>
      <div class="history-grid">
        <div v-for="h in myHistory" :key="h.id" class="history-card">
          <div class="his-head">
            <span class="his-name">{{ h.testName }}</span>
            <span class="his-date">{{ h.createTime || h.date }}</span>
          </div>
          <div class="his-body">
            <div class="his-score">
              <span class="score-num">{{ h.score ?? '--' }}</span>
              <span class="score-total">/{{ h.totalScore || '--' }}</span>
            </div>
            <div class="his-level" :class="levelClass(h.level || '正常')">{{ h.level || '正常' }}</div>
          </div>
          <p class="his-tip">{{ h.tip || '根据你的得分，建议继续关注自己的心理状态 ' }}</p>
          <button class="his-view" @click="viewDetail(h)">查看详情 <AppIcon name="arrow-right" /></button>
        </div>
      </div>
    </section>

    <!-- ===== 量表详情预览弹窗 ===== -->
    <div v-if="showDetail" class="modal-mask" @click.self="showDetail = false">
      <div class="modal-card detail-modal">
        <div class="detail-head">
          <div class="detail-icon">{{ paperIcon(detailPaper?.testName) }}</div>
          <div>
            <h3 class="detail-title">{{ detailPaper?.testName }}</h3>
            <div class="detail-tags">
              <span class="detail-tag"><AppIcon name="edit" /> {{ detailPaper?.questionCount || 10 }} 题</span>
              <span class="detail-tag"><AppIcon name="timer" /> 约 {{ estimateMinutes(detailPaper) }} 分钟</span>
              <span class="detail-tag"><AppIcon name="users" /> {{ detailPaper?.takenCount || 0 }} 人已测</span>
            </div>
          </div>
          <button class="close-btn" @click="showDetail = false"><AppIcon name="close" /></button>
        </div>

        <div class="detail-body">
          <div class="detail-section">
            <h4><AppIcon name="clipboard" /> 量表介绍</h4>
            <p>{{ detailPaper?.description || '专业心理测评量表，帮助你了解自己的心理状态。' }}</p>
          </div>
          <div class="detail-section">
            <h4><AppIcon name="user" /> 适用人群</h4>
            <p>适用于关注自身{{ detailPaper?.category === 'depress' ? '情绪状态' : detailPaper?.category === 'anxiety' ? '焦虑水平' : detailPaper?.category === 'stress' ? '压力状况' : detailPaper?.category === 'sleep' ? '睡眠质量' : detailPaper?.category === 'self' ? '自我认知' : detailPaper?.category === 'personality' ? '性格特点' : '心理健康' }}的成年人。建议在安静的环境下独立完成。</p>
          </div>
          <div class="detail-section">
            <h4><AppIcon name="alert-triangle" /> 注意事项</h4>
            <p>本测评仅供参考，不能替代专业医疗诊断。如测评结果显示需要关注，建议寻求专业心理咨询师的帮助。</p>
          </div>
        </div>

        <div class="detail-foot">
          <button class="foot-btn" @click="showDetail = false">取消</button>
          <button class="foot-btn primary" @click="confirmStartTest"><AppIcon name="sparkles" /> 开始答题</button>
        </div>
      </div>
    </div>

    <!-- ===== 沉浸式答题页（动漫风：气泡选项 + 吉祥物陪伴 + 漂浮星星） ===== -->
    <div v-if="showTest" class="quiz-screen">
      <div class="quiz-blob b1"></div>
      <div class="quiz-blob b2"></div>
      <div class="quiz-blob b3"></div>
      <span class="quiz-star qs1"><AppIcon name="sparkles" /></span>
      <span class="quiz-star qs2"><AppIcon name="heart" /></span>
      <span class="quiz-star qs3"><AppIcon name="sparkles" /></span>
      <span class="quiz-star qs4"><AppIcon name="heart" /></span>

      <header class="quiz-head">
        <button class="quiz-back" @click="closeTest">‹</button>
        <span class="quiz-name">{{ currentPaper?.testName }}</span>
        <button class="quiz-close" @click="closeTest"><AppIcon name="close" /></button>
      </header>

      <div class="quiz-body">
        <div class="quiz-num">#{{ String(currentQ + 1).padStart(2, '0') }}</div>
        <p class="q-text">{{ questions[currentQ]?.text || '题目加载中...' }}</p>

        <div class="q-options">
          <button
            v-for="(opt, i) in (questions[currentQ]?.options || [])"
            :key="i"
            :class="['opt-btn', { on: answers[currentQ] === opt.score }]"
            :style="{ animationDelay: (i * 0.07) + 's' }"
            @click="answers[currentQ] = opt.score"
          >
            <span class="opt-letter">{{ letterOf(i) }}</span>
            <span class="opt-text">{{ opt.label }}</span>
            <span v-if="answers[currentQ] === opt.score" class="opt-check"><AppIcon name="check" /></span>
          </button>
        </div>
      </div>

      <footer class="quiz-foot">
        <div class="quiz-mascot">{{ quizMascotLine }}</div>
        <div class="quiz-progress">
          <div class="quiz-progress-fill" :style="{ width: ((currentQ + 1) / questions.length * 100) + '%' }"></div>
        </div>
        <div class="quiz-foot-btns">
          <button class="quiz-nav ghost" :disabled="currentQ === 0" @click="currentQ--">上一题</button>
          <button v-if="currentQ < questions.length - 1" class="quiz-nav primary" :disabled="answers[currentQ] === undefined" @click="currentQ++">下一题</button>
          <button v-else class="quiz-nav primary" :disabled="submitting || answers.some(a => a === undefined)" @click="submitTest">{{ submitting ? '提交中…' : '提交测评' }}</button>
        </div>
      </footer>
    </div>

    <!-- ===== 结果海报弹窗（小红书结果页风格） ===== -->
    <div v-if="showResult" class="modal-mask" @click.self="showResult = false">
      <div class="result-wrap">
        <div class="result-poster">
          <div class="poster-slogan">\ {{ posterSlogan }} /</div>
          <div class="poster-pills">
            <span class="pill pill-label">{{ currentPaper?.testName }}</span>
            <span class="pill pill-result" :class="resultLevelClass">{{ resultLevelText }}</span>
          </div>
          <div class="poster-score-row">
            <div class="poster-score">
              <span class="ps-num">{{ resultScore }}</span>
              <span class="ps-total">/ {{ resultTotal }}</span>
            </div>
          </div>
          <p class="poster-desc">{{ resultTip }}</p>
          <div class="poster-tips">
            <span class="tips-label">小贴<br/>士</span>
            <span class="tips-divider"></span>
            <p class="tips-text">{{ resultTipsText }}</p>
          </div>
          <div class="poster-tags">
            <span v-for="tag in resultTags" :key="tag" class="poster-tag"># {{ tag }}</span>
          </div>
          <div class="poster-brand">
            <span class="poster-brand-logo"><AppIcon name="leaf" /></span>
            <span class="poster-brand-name">绪见 · 心理测评馆</span>
            <span class="poster-date">{{ today }}</span>
          </div>
        </div>
        <div class="result-actions">
          <button class="foot-btn" @click="showResult = false; showTest = false">关闭</button>
          <button class="foot-btn" @click="saveShareCard"><AppIcon name="camera" /> 保存卡片</button>
          <button class="foot-btn primary" @click="retakeTest"><AppIcon name="repeat" /> 重新测评</button>
        </div>
      </div>
    </div>

    <!-- ===== 加载动画 ===== -->
    <div v-if="isLoading" class="loading-mask">
      <div class="loading-spinner">
        <div class="loading-dots"><span></span><span></span><span></span></div>
        <p class="loading-text">(´･ω･)ﾉ 题目加载中…</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getTestPaperList, getRecordList, saveRecord, getQuestionList, getOptionList, matchResultGrade } from '../api'
import { ElMessage } from 'element-plus'

const route = useRoute()

// ==================== 主题色跟随 ====================
// 配色来源：个人中心  个性化设置  主题颜色（localStorage.profile_theme）。
// 本页所有颜色（英雄区/答题页/结果海报/卡片/按钮/canvas 分享图）都从主题色实时推导，
// 用户换主题色，这一页整体跟着变。Profile 切换时会派发 profile-updated 事件。
const FALLBACK_THEME = { name: 'pink', label: '少女粉', hex: '#e59bb3', main: '#f2b8cd', bg1: '#fcf4f7', bg2: '#fae8ef' }

function loadTheme() {
  try {
    const t = JSON.parse(localStorage.getItem('profile_theme') || 'null')
    if (t && /^#[0-9a-fA-F]{6}$/.test(t.hex || '')) return t
  } catch (e) { /* 损坏的本地存储：走兜底 */ }
  return FALLBACK_THEME
}
const theme = ref(loadTheme())

// ---- 颜色小工具（hex  HSL、按比例混色），不引第三方库 ----
function hexToRgb(hex) {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}
function rgbToHex(r, g, b) {
  const c = (v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')
  return '#' + c(r) + c(g) + c(b)
}
function hexToHsl(hex) {
  let [r, g, b] = hexToRgb(hex).map(v => v / 255)
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  let h = 0, s = 0
  const l = (max + min) / 2
  if (max !== min) {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    if (max === r) h = ((g - b) / d + (g < b ? 6 : 0))
    else if (max === g) h = (b - r) / d + 2
    else h = (r - g) / d + 4
    h *= 60
  }
  return { h, s, l }
}
function hslToHex(h, s, l) {
  const f = (n) => {
    const k = (n + h / 30) % 12
    const a = s * Math.min(l, 1 - l)
    const v = l - a * Math.max(-1, Math.min(k - 3, Math.min(9 - k, 1)))
    return v * 255
  }
  return rgbToHex(f(0), f(8), f(4))
}
/** 色相旋转 deg 度（保持饱和度/亮度） */
function rotateHue(hex, deg) {
  const { h, s, l } = hexToHsl(hex)
  return hslToHex((h + deg + 360) % 360, s, l)
}
/** 线性混色：w 是 other 的权重（0~1） */
function mixColor(hex, other, w) {
  const a = hexToRgb(hex), b = hexToRgb(other)
  return rgbToHex(a[0] + (b[0] - a[0]) * w, a[1] + (b[1] - a[1]) * w, a[2] + (b[2] - a[2]) * w)
}
const tint = (hex, w) => mixColor(hex, '#ffffff', w)   // 变浅
const shade = (hex, w) => mixColor(hex, '#000000', w)  // 变深

/**
 * 主题派生色板  根元素 CSS 变量。
 * 同一个 hex 推出：主色 / 深文字 / 中文字 / 弱文字、英雄区三段渐变、
 * 答题页三段渐变、结果海报三段渐变、五张卡片底色（色相微旋转制造层次）。
 */
const themeVars = computed(() => {
  const t = theme.value
  const hex = t.hex, main = t.main || tint(hex, 0.18)
  return {
    '--t-hex': hex,
    '--t-main': main,
    '--t-strong': shade(hex, 0.28),
    '--t-deep': shade(hex, 0.5),
    '--t-text': shade(hex, 0.32),
    '--t-mid': shade(hex, 0.18),
    '--t-weak': mixColor(shade(hex, 0.12), '#9c8f94', 0.4),
    '--t-hero1': tint(main, 0.28),
    '--t-hero2': main,
    '--t-hero3': hex,
    '--t-quiz1': tint(rotateHue(main, -14), 0.12),
    '--t-quiz2': main,
    '--t-quiz3': tint(hex, 0.1),
    '--t-poster1': tint(main, 0.38),
    '--t-poster2': tint(main, 0.18),
    '--t-poster3': tint(hex, 0.22),
    '--t-card1': '#ffffff',
    '--t-card2': '#ffffff',
    '--t-card3': '#ffffff',
    '--t-card4': '#ffffff',
    '--t-card5': '#ffffff',
    '--t-border': tint(hex, 0.78),
  }
})

// 快捷磁贴的六格渐变：主色色相微旋转，整体和主题一个色系
const tileBg = (i) => {
  const degs = [0, -28, 150, 205, 42, -58]
  const c = rotateHue(theme.value.hex, degs[i % degs.length])
  return `linear-gradient(135deg, ${tint(c, 0.72)}, ${tint(c, 0.5)})`
}

// Profile 里切了主题  立即换肤（同标签页事件 + 跨标签页 storage）
const onThemeChange = () => { theme.value = loadTheme() }

// 统一取当前登录用户 ID（登录接口返回的是 userId，个别老数据可能只有 id）
const getUid = () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
  return info?.userId ?? info?.id ?? null
}

// ===== 分类磁贴：第一个是「全部」，点击即筛选 =====
const quickCats = [
  { key: 'all',         label: '全部',  icon: 'sparkles' },
  { key: 'anxiety',     label: '焦虑',  icon: 'waves' },
  { key: 'depress',     label: '抑郁',  icon: 'cloud-rain' },
  { key: 'stress',      label: '压力',  icon: 'leaf' },
  { key: 'sleep',       label: '睡眠',  icon: 'moon' },
  { key: 'self',        label: '自我',  icon: 'heart' },
  { key: 'personality', label: '人格',  icon: 'face-other' },
]
const activeCat = ref('all')

// ===== 搜索 =====
const searchKeyword = ref('')
const hotSearchTags = ['焦虑', '抑郁', '睡眠', '压力', '人格', 'PHQ-9']

// ===== 收藏 =====
const favorites = ref(new Set())
const FAVORITES_KEY = 'see_you_favorite_tests'

const loadFavorites = () => {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY)
    if (raw) {
      const arr = JSON.parse(raw)
      favorites.value = new Set(arr)
    }
  } catch (e) {}
}
const saveFavorites = () => {
  localStorage.setItem(FAVORITES_KEY, JSON.stringify([...favorites.value]))
}
const isFavorite = (id) => favorites.value.has(id)
const toggleFavorite = (id) => {
  if (favorites.value.has(id)) {
    favorites.value.delete(id)
  } else {
    favorites.value.add(id)
  }
  saveFavorites()
}

// ===== 加载状态 =====
const isLoading = ref(false)

// ===== 量表详情弹窗 =====
const showDetail = ref(false)
const detailPaper = ref(null)
const showPaperDetail = (paper) => {
  detailPaper.value = paper
  showDetail.value = true
}
const confirmStartTest = () => {
  showDetail.value = false
  startTest(detailPaper.value)
}

// ===== 默认量表数据 =====
const defaultPapers = [
  { testId: 1, testName: 'PHQ-9 抑郁症状筛查量表', category: 'depress', level: '中风险',
    description: '评估过去两周抑郁症状频率。总分 0-27，≥10 分提示中度抑郁可能。',
    questionCount: 9, duration: '约 3 分钟', takenCount: 128 },
  { testId: 2, testName: 'GAD-7 焦虑筛查量表',     category: 'anxiety', level: '中风险',
    description: '评估过去两周焦虑症状频率。总分 0-21，≥10 分提示可能存在焦虑障碍。',
    questionCount: 7, duration: '约 2 分钟', takenCount: 96 },
  { testId: 3, testName: 'PSS-10 压力感知量表',    category: 'stress',  level: '中风险',
    description: '评估过去一个月压力感知水平。总分 0-40，≥20 分表示压力较高，需干预。',
    questionCount: 10, duration: '约 3 分钟', takenCount: 87 },
  { testId: 4, testName: 'ISI 失眠严重程度量表',   category: 'sleep',   level: '中风险',
    description: '评估过去两周失眠的严重程度。总分 0-28，≥15 分提示临床显著失眠。',
    questionCount: 7, duration: '约 3 分钟', takenCount: 65 },
  { testId: 5, testName: 'PHQ-15 躯体化症状量表', category: 'depress', level: '低风险',
    description: '评估过去四周躯体不适症状。总分 0-30，≥10 分提示显著躯体化问题。',
    questionCount: 15, duration: '约 5 分钟', takenCount: 54 },
  { testId: 6, testName: 'SAS 焦虑自评量表',       category: 'anxiety', level: '中风险',
    description: '经典焦虑自评量表，共 20 题。标准分 ≥50 分提示存在焦虑症状。',
    questionCount: 20, duration: '约 8 分钟', takenCount: 76 },
  { testId: 7, testName: 'SDS 抑郁自评量表',       category: 'depress', level: '中风险',
    description: '经典抑郁自评量表，共 20 题。标准分 ≥53 分提示存在抑郁症状。',
    questionCount: 20, duration: '约 8 分钟', takenCount: 82 },
  { testId: 8, testName: 'SES 自尊量表',            category: 'self',    level: '低风险',
    description: 'Rosenberg 自尊量表，共 10 题。得分越高自尊水平越高。',
    questionCount: 10, duration: '约 3 分钟', takenCount: 43 },
  { testId: 9, testName: '内外向人格测试',          category: 'personality', level: '探索型',
    description: '艾森克内外向量表简化版，共 12 题，了解你的性格倾向是内向还是外向。',
    questionCount: 12, duration: '约 3 分钟', takenCount: 156 },
]
const papers = ref([...defaultPapers])

// ===== 我的测评记录 =====
const myHistory = ref([])

// ===== 弹窗状态 =====
const showTest = ref(false)
const currentQ = ref(0)

// 答题吉祥物的陪伴台词：随进度换话，营造「有小伙伴陪你做题」的动漫感
const quizMascotLine = computed(() => {
  const n = questions.value.length || 1
  const p = currentQ.value / Math.max(1, n - 1)
  if (submitting.value) return '(๑•̀ㅂ•́)و 正在生成你的结果…'
  if (currentQ.value === 0) return '(´,,•ω•,,) 我陪你一起做吧！'
  if (currentQ.value === n - 1) return '٩(๑•̀ω•́๑)۶ 最后一题，冲呀！'
  if (p > 0.6) return '(๑˃̵ᴗ˂̵)و 已经过半啦，好棒！'
  return 'ฅ^•ﻌ•^ฅ 慢慢选，不用着急～'
})
const answers = ref([])
const currentPaper = ref(null)

// 选项字母徽标：A / B / C / D
const letterOf = (i) => ['A', 'B', 'C', 'D', 'E', 'F'][i] || String(i + 1)

// ===== 题库 =====
const defaultQuestions = [
  { text: '做事时提不起劲或没有兴趣', options: [
    { label: '完全不会', score: 0 }, { label: '好几天', score: 1 },
    { label: '一半以上天数', score: 2 }, { label: '几乎每天', score: 3 }
  ]},
  { text: '感到心情低落、沮丧或绝望', options: [
    { label: '完全不会', score: 0 }, { label: '好几天', score: 1 },
    { label: '一半以上天数', score: 2 }, { label: '几乎每天', score: 3 }
  ]},
  { text: '入睡困难、睡不安稳或睡眠过多', options: [
    { label: '完全不会', score: 0 }, { label: '好几天', score: 1 },
    { label: '一半以上天数', score: 2 }, { label: '几乎每天', score: 3 }
  ]},
  { text: '感觉疲倦或没有活力', options: [
    { label: '完全不会', score: 0 }, { label: '好几天', score: 1 },
    { label: '一半以上天数', score: 2 }, { label: '几乎每天', score: 3 }
  ]},
  { text: '食欲改变或体重变化', options: [
    { label: '完全不会', score: 0 }, { label: '好几天', score: 1 },
    { label: '一半以上天数', score: 2 }, { label: '几乎每天', score: 3 }
  ]},
]

const personalityQuestions = [
  { text: '在社交聚会中，你通常会？', options: [
    { label: '主动与很多人交谈，包括陌生人', score: 3 },
    { label: '和熟悉的朋友聊天为主', score: 2 },
    { label: '找个安静的角落待着', score: 1 },
    { label: '尽量找借口提前离开', score: 0 }
  ]},
  { text: '周末你更喜欢？', options: [
    { label: '约朋友出去玩，越热闹越好', score: 3 },
    { label: '和一两个好友小聚', score: 2 },
    { label: '在家追剧/看书/打游戏', score: 1 },
    { label: '一个人宅着最舒服', score: 0 }
  ]},
  { text: '在团队讨论中，你通常？', options: [
    { label: '积极发言，主导讨论', score: 3 },
    { label: '有想法时会表达', score: 2 },
    { label: '更多时候在倾听', score: 1 },
    { label: '尽量不说话，听别人说', score: 0 }
  ]},
  { text: '你更倾向于通过什么方式恢复精力？', options: [
    { label: '和朋友聚会聊天', score: 3 },
    { label: '参加有趣的活动', score: 2 },
    { label: '一个人安静待着', score: 1 },
    { label: '彻底独处，不见任何人', score: 0 }
  ]},
  { text: '面对陌生人时，你？', options: [
    { label: '很容易自来熟', score: 3 },
    { label: '打个招呼没问题', score: 2 },
    { label: '有点紧张，等对方先开口', score: 1 },
    { label: '能避开就避开', score: 0 }
  ]},
  { text: '你更喜欢什么样的工作环境？', options: [
    { label: '开放式办公，人多热闹', score: 3 },
    { label: '有同事但不吵的环境', score: 2 },
    { label: '独立小办公室', score: 1 },
    { label: '居家办公，不见人最好', score: 0 }
  ]},
  { text: '朋友们怎么形容你？', options: [
    { label: '活泼开朗，社交达人', score: 3 },
    { label: '热情友善，好相处', score: 2 },
    { label: '安静温和，话不多', score: 1 },
    { label: '内向腼腆，比较宅', score: 0 }
  ]},
  { text: '做决定时你更倾向于？', options: [
    { label: '先行动，边做边想', score: 3 },
    { label: '想一下就行动', score: 2 },
    { label: '想清楚再行动', score: 1 },
    { label: '反复思考，尽量不做决定', score: 0 }
  ]},
  { text: '你对新环境的适应速度？', options: [
    { label: '很快，马上就能融入', score: 3 },
    { label: '一两天就能适应', score: 2 },
    { label: '需要一周左右', score: 1 },
    { label: '很慢，需要很久才能习惯', score: 0 }
  ]},
  { text: '你更享受？', options: [
    { label: '成为人群中的焦点', score: 3 },
    { label: '和朋友一起说说笑笑', score: 2 },
    { label: '和一两个知心朋友深聊', score: 1 },
    { label: '一个人沉浸在自己的世界里', score: 0 }
  ]},
  { text: '长时间独处后你会？', options: [
    { label: '受不了，赶紧找人玩', score: 3 },
    { label: '有点闷，想找人聊聊天', score: 2 },
    { label: '感觉挺好，还能继续', score: 1 },
    { label: '神清气爽，越待越舒服', score: 0 }
  ]},
  { text: '你认为自己是？', options: [
    { label: '典型的外向者', score: 3 },
    { label: '偏外向但也有安静的时候', score: 2 },
    { label: '偏内向但社交也没问题', score: 1 },
    { label: '典型的内向者', score: 0 }
  ]},
]

const questions = ref([...defaultQuestions])

// ===== 统计 =====
const totalTests = computed(() => papers.value.length)
const latestScore = computed(() => myHistory.value[0]?.score ?? '--')
const filteredPapers = computed(() => {
  let list = papers.value
  if (activeCat.value !== 'all') {
    list = list.filter(p => p.category === activeCat.value)
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(p =>
      p.testName.toLowerCase().includes(kw) ||
      (p.description && p.description.toLowerCase().includes(kw))
    )
  }
  // 注意：不能直接 list.sort()，那会原地修改 papers.value（computed 里改源数据）
  return [...list].sort((a, b) => {
    const fa = isFavorite(a.testId) ? 1 : 0
    const fb = isFavorite(b.testId) ? 1 : 0
    return fb - fa
  })
})

// ===== 趋势图数据 =====
const trendPaperName = computed(() => {
  if (myHistory.value.length === 0) return ''
  const counts = {}
  myHistory.value.forEach(h => {
    counts[h.testName] = (counts[h.testName] || 0) + 1
  })
  const maxName = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] || ''
  return maxName
})
const trendData = computed(() => {
  const name = trendPaperName.value
  if (!name) return []
  const records = myHistory.value
    .filter(h => h.testName === name)
    .slice(0, 6)
    .reverse()
  return records.map(h => ({
    date: h.createTime ? h.createTime.slice(5, 10) : '--',
    score: h.score || 0,
    total: h.totalScore || 1
  }))
})
const trendPoints = computed(() => {
  const data = trendData.value
  if (data.length < 2) return []
  const maxScore = Math.max(...data.map(d => d.total))
  const padding = 40
  const chartW = 600 - padding * 2
  const chartH = 150
  const stepX = chartW / (data.length - 1)
  return data.map((d, i) => ({
    x: padding + i * stepX,
    y: 170 - (d.score / maxScore) * chartH,
    score: d.score
  }))
})
const trendPolyline = computed(() => {
  return trendPoints.value.map(p => `${p.x},${p.y}`).join(' ')
})
const trendDirection = computed(() => {
  const data = trendData.value
  if (data.length < 2) return ''
  const first = data[0].score
  const last = data[data.length - 1].score
  if (last < first) return ' 你的得分呈下降趋势，状态在好转 ~'
  if (last > first) return ' 得分有所上升，建议多关注自己的状态 ~'
  return ' 得分保持平稳，持续关注就好 ~'
})

// ===== 辅助 =====
const paperIcon = (name) => {
  if (!name) return 'notebook'
  if (name.includes('焦虑')) return 'waves'
  if (name.includes('抑郁')) return 'cloud-rain'
  if (name.includes('压力')) return 'leaf'
  if (name.includes('失眠') || name.includes('睡眠')) return 'moon'
  if (name.includes('自尊')) return 'heart'
  if (name.includes('躯体')) return 'heart-hand'
  if (name.includes('人格') || name.includes('内外向')) return 'face-other'
  return 'notebook'
}
// 卡片底色：五张一轮回，底色已由主题变量 --t-card1..5 派生
const cardColor = (id) => {
  const pool = ['card-t1', 'card-t2', 'card-t3', 'card-t4', 'card-t5']
  return pool[(id - 1) % pool.length]
}
const levelClass = (lv) => {
  if (!lv) return 'lv-mid'
  if (lv.includes('高') || lv.includes('重度')) return 'lv-high'
  if (lv.includes('低') || lv.includes('正常')) return 'lv-low'
  return 'lv-mid'
}

// 后端 PsyTestPaper 没有 level / duration 字段，这里改用真实题量推算规模与耗时
const sizeLabel = (p) => {
  const n = Number(p?.questionCount || 0)
  if (n <= 0) return '量表'
  if (n <= 10) return '快速测评'
  if (n <= 30) return '标准量表'
  return '完整量表'
}
// 按每题约 12 秒估算，最少 1 分钟
const estimateMinutes = (p) => {
  const n = Number(p?.questionCount || p?.questions || 10)
  return Math.max(1, Math.round(n * 12 / 60))
}

// ===== 操作 =====
const startTest = async (paper) => {
  currentPaper.value = paper
  isLoading.value = true
  showTest.value = true
  answers.value = []
  currentQ.value = 0
  questions.value = []
  // 锁定背景滚动（全屏答题页）
  document.body.style.overflow = 'hidden'

  try {
    const qRes = await getQuestionList({ testId: paper.testId, page: 1, size: 100 })
    const qList = qRes.data?.records || qRes.data || []
    if (qList.length > 0) {
      const questionsWithOpts = []
      for (const q of qList) {
        try {
          const oRes = await getOptionList({ questionId: q.questionId, page: 1, size: 20 })
          const oList = oRes.data?.records || oRes.data || []
          questionsWithOpts.push({
            text: q.questionContent,
            options: oList.map(o => ({
              label: o.optionText,
              score: o.score ?? 0
            }))
          })
        } catch (e) {
          questionsWithOpts.push({
            text: q.questionContent,
            options: [{ label: '选项 A', score: 0 }, { label: '选项 B', score: 1 }]
          })
        }
      }
      questions.value = questionsWithOpts
      answers.value = new Array(questionsWithOpts.length).fill(undefined)
    } else {
      useFallbackQuestions(paper)
    }
  } catch (e) {
    useFallbackQuestions(paper)
  } finally {
    isLoading.value = false
  }
}

const useFallbackQuestions = (paper) => {
  if (paper.testName.includes('人格') || paper.testName.includes('内外向')) {
    questions.value = [...personalityQuestions]
  } else {
    questions.value = [...defaultQuestions]
  }
  answers.value = new Array(questions.value.length).fill(undefined)
}
const closeTest = () => {
  showTest.value = false
  currentPaper.value = null
  document.body.style.overflow = ''
}

const resultScore = ref(0)
const resultTotal = ref(15)
const resultLevelText = ref('')
const resultTip = ref('')
const resultLevelClass = ref('lv-mid')

// ===== 结果海报 =====
// 海报顶部的一句口号：按测评结果走心化
const posterSlogan = computed(() => {
  if (resultLevelClass.value === 'lv-high') return '让被看见，就是被治愈的开始。'
  if (resultLevelClass.value === 'lv-mid') return '照顾好情绪，从看见它开始。'
  return '看见自己，是温柔的第一步。'
})
// 「小贴士」一句话：按结果等级给一句可执行的建议
const resultTipsText = computed(() => {
  if (resultLevelClass.value === 'lv-high') return '别一个人扛着，把今天的结果讲给信任的人听，必要时寻求专业帮助。'
  if (resultLevelClass.value === 'lv-mid') return '试试规律作息 + 每天 10 分钟正念呼吸，把感受写进「绪见」日记里。'
  return '保持现在的节奏，把好的状态延续下去。'
})
// 海报底部的 # 话题词：结合量表名与结果等级生成
const resultTags = computed(() => {
  const name = currentPaper.value?.testName || '心理测评'
  const short = name.replace(/量表|测试|简化版|症状/g, '').trim().slice(0, 6)
  const byLevel = {
    'lv-low':  ['心理自测', short + '打卡', '情绪管理', '自我觉察'],
    'lv-mid':  ['情绪关注', short + '日记', '正念练习', '自我觉察'],
    'lv-high': ['心理健康', short + '关注', '温柔对待自己', '求助是勇敢'],
  }
  return byLevel[resultLevelClass.value] || byLevel['lv-mid']
})

// ===== 分享卡片 =====
const today = new Date().toLocaleDateString('zh-CN')

// 总分：优先用后端量表的 maxScore；没有就按每题最大选项分累加；再没有才按题数×3 兜底
const computeTotal = () => {
  const declared = Number(currentPaper.value?.maxScore)
  if (declared > 0) return declared
  let sum = 0
  let usable = false
  questions.value.forEach(q => {
    const opts = Array.isArray(q?.options) ? q.options : []
    if (opts.length > 0) {
      usable = true
      sum += Math.max(...opts.map(o => Number(o.score ?? 0)))
    }
  })
  if (usable && sum > 0) return sum
  return questions.value.length * 3
}

const submitting = ref(false)
const submitTest = async () => {
  if (submitting.value) return // 防连点/重复提交
  submitting.value = true
  try { await doSubmitTest() } finally { submitting.value = false }
}
const doSubmitTest = async () => {
  const score = answers.value.reduce((s, v) => s + (v || 0), 0)
  resultScore.value = score
  resultTotal.value = computeTotal()
  const isPersonality = currentPaper.value?.testName?.includes('人格') || currentPaper.value?.testName?.includes('内外向')

  if (isPersonality) {
    const ratio = score / resultTotal.value
    if (ratio >= 0.75) {
      resultLevelText.value = '典型外向 '
      resultLevelClass.value = 'lv-low'
      resultTip.value = '你是个十足的社交达人！热情开朗，喜欢与人交往，在人群中如鱼得水。你的能量来自外部世界，朋友和社交活动让你充满活力~'
    } else if (ratio >= 0.5) {
      resultLevelText.value = '偏外向 '
      resultLevelClass.value = 'lv-low'
      resultTip.value = '你偏向外向性格，喜欢社交但也享受独处。既能在人群中发光，也懂得给自己充电的时间，是很平衡的状态~'
    } else if (ratio >= 0.25) {
      resultLevelText.value = '偏内向 '
      resultLevelClass.value = 'lv-mid'
      resultTip.value = '你偏向内向性格，内心世界丰富而深刻。独处让你充满能量，深思熟虑是你的优势。不用勉强自己社交，按自己的节奏来就好~'
    } else {
      resultLevelText.value = '典型内向 '
      resultLevelClass.value = 'lv-mid'
      resultTip.value = '你是典型的内向者，内心有一个丰富的小世界。深度思考、专注专注是你的超能力。记住：内向不是缺陷，是一种独特的力量 '
    }
  } else {
    const ratio = score / resultTotal.value
    if (ratio < 0.2) { resultLevelText.value = '状态良好 '; resultLevelClass.value = 'lv-low'
      resultTip.value = '你的心理状态看起来很不错，继续保持规律作息和积极的心态吧！' }
    else if (ratio < 0.45) { resultLevelText.value = '轻度波动 '; resultLevelClass.value = 'lv-low'
      resultTip.value = '有一些轻微的情绪波动，建议关注自己的感受，适当放松。' }
    else if (ratio < 0.7) { resultLevelText.value = '中度关注 '; resultLevelClass.value = 'lv-mid'
      resultTip.value = '得分提示可能存在一些困扰，建议尝试放松技巧，或与信任的人聊聊。' }
    else { resultLevelText.value = '建议干预 '; resultLevelClass.value = 'lv-high'
      resultTip.value = '得分较高，建议寻求专业心理咨询师的帮助。请记住，求助是勇敢的表现 ' }
  }
  // 优先采用后台为该量表配置的结果分级；未配置或接口异常时沿用上面的兜底文案
  try {
    const tid = currentPaper.value?.testId
    if (tid) {
      const gres = await matchResultGrade(tid, score)
      const g = gres?.data
      if (g && g.gradeName) {
        resultLevelText.value = g.gradeName
        resultTip.value = [g.description, g.suggestion].filter(Boolean).join(' ')
        // 后台配置的分级文案  配色同步切换（良好/轻度=主题色，中度=黄，重度/干预=红）
        const name = g.gradeName
        if (/良好|正常|健康/.test(name)) resultLevelClass.value = 'lv-low'
        else if (/轻度/.test(name)) resultLevelClass.value = 'lv-low'
        else if (/中度/.test(name)) resultLevelClass.value = 'lv-mid'
        else if (/重视|重度|干预|严重/.test(name)) resultLevelClass.value = 'lv-high'
      }
    }
  } catch (e) {
    // 忽略：拿不到分级配置时继续用本地文案
  }
  // 保存测评记录（userId / testId 必须与后端实体一致，否则存不进去）
  const uid = getUid()
  let saved = false
  try {
    if (!uid) throw new Error('未登录')
    const res = await saveRecord({
      userId: uid,
      testId: currentPaper.value?.testId,
      score,
      totalScore: resultTotal.value,
      level: resultLevelText.value.split(' ')[0],
      tip: resultTip.value
    })
    saved = res?.code === 200
    if (!saved) console.warn('[测评] 保存记录失败:', res)
  } catch (e) {
    console.error('[测评] 保存记录异常:', e)
  }
  if (!saved) {
    ElMessage.warning('本次测评结果未能保存到记录（请确认已登录），结果仅在本页显示')
  }
  showTest.value = false
  document.body.style.overflow = ''
  showResult.value = true
  myHistory.value.unshift({
    id: Date.now(),
    testName: currentPaper.value.testName,
    score, totalScore: resultTotal.value,
    level: resultLevelText.value.split(' ')[0],
    tip: resultTip.value,
    createTime: new Date().toLocaleString('zh-CN')
  })
}

// 结果海报：canvas 存图（配色跟随当前主题）
const showResult = ref(false)

const saveShareCard = () => {
  const hex = theme.value.hex
  const main = theme.value.main || tint(hex, 0.18)
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  canvas.width = 640
  canvas.height = 960

  // 主题色渐变底
  const gradient = ctx.createLinearGradient(0, 0, 0, 960)
  gradient.addColorStop(0, tint(main, 0.42))
  gradient.addColorStop(0.5, tint(main, 0.2))
  gradient.addColorStop(1, tint(hex, 0.24))
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, 640, 960)

  // 装饰泡泡
  ctx.fillStyle = 'rgba(255,255,255,0.28)'
  for (let i = 0; i < 14; i++) {
    ctx.beginPath()
    ctx.arc(Math.random() * 640, Math.random() * 240, Math.random() * 22 + 6, 0, Math.PI * 2)
    ctx.fill()
  }

  // 白色海报卡
  const cardX = 40, cardY = 110, cardW = 560, cardH = 700
  ctx.fillStyle = 'rgba(255,255,255,0.92)'
  roundRect(ctx, cardX, cardY, cardW, cardH, 32)
  ctx.fill()

  const strong = shade(hex, 0.28)
  const deep = shade(hex, 0.5)
  const weak = mixColor(shade(hex, 0.12), '#9c8f94', 0.4)

  // 顶部口号
  ctx.fillStyle = strong
  ctx.font = 'bold 24px "Microsoft YaHei", sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('\\ ' + posterSlogan.value + ' /', 320, 80)

  // 量表名胶囊 + 结果胶囊
  ctx.fillStyle = tint(hex, 0.35)
  roundRect(ctx, 100, 160, 200, 52, 26)
  ctx.fill()
  ctx.fillStyle = hex
  roundRect(ctx, 320, 160, 220, 52, 26)
  ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 20px "Microsoft YaHei", sans-serif'
  const paperName = (currentPaper.value?.testName || '心理测评').slice(0, 8)
  ctx.fillText(paperName, 200, 193)
  ctx.fillText(resultLevelText.value.slice(0, 8), 430, 193)

  // 大分数
  ctx.fillStyle = strong
  ctx.font = 'bold 88px "Microsoft YaHei", sans-serif'
  ctx.fillText(String(resultScore.value), 290, 330)
  ctx.font = 'bold 30px "Microsoft YaHei", sans-serif'
  ctx.fillStyle = deep
  ctx.fillText('/ ' + resultTotal.value, 420, 330)

  // 结果描述（自动换行）
  ctx.fillStyle = shade(hex, 0.38)
  ctx.font = '18px "Microsoft YaHei", sans-serif'
  let line = ''
  let y = 400
  const tip = resultTip.value
  for (let i = 0; i < tip.length; i++) {
    const testLine = line + tip[i]
    if (ctx.measureText(testLine).width > 480 && i > 0) {
      ctx.fillText(line, 320, y)
      line = tip[i]
      y += 30
    } else {
      line = testLine
    }
  }
  ctx.fillText(line, 320, y)

  // 小贴士
  ctx.fillStyle = strong
  ctx.font = 'bold 20px "Microsoft YaHei", sans-serif'
  ctx.fillText('TIPS', 320, y + 56)
  ctx.fillStyle = weak
  ctx.font = '17px "Microsoft YaHei", sans-serif'
  let tipLine = ''
  let ty = y + 90
  const tips = resultTipsText.value
  for (let i = 0; i < tips.length; i++) {
    const testLine = tipLine + tips[i]
    if (ctx.measureText(testLine).width > 460 && i > 0) {
      ctx.fillText(tipLine, 320, ty)
      tipLine = tips[i]
      ty += 28
    } else {
      tipLine = testLine
    }
  }
  ctx.fillText(tipLine, 320, ty)

  // # 话题词：逐个绘制，超宽自动换行
  ctx.font = 'bold 18px "Microsoft YaHei", sans-serif'
  ctx.fillStyle = strong
  let tagX = 90
  let tagY = y + 150
  resultTags.value.forEach((tag) => {
    const t = '# ' + tag
    const w = ctx.measureText(t).width
    if (tagX + w > 550) { tagX = 90; tagY += 34 }
    ctx.fillText(t, tagX, tagY)
    tagX += w + 36
  })

  // 底部品牌
  ctx.fillStyle = deep
  ctx.font = 'bold 22px "Microsoft YaHei", sans-serif'
  ctx.fillText(' 绪见 · 心理测评馆', 320, 900)

  const link = document.createElement('a')
  link.download = `绪见测评报告_${today}.png`
  link.href = canvas.toDataURL('image/png')
  link.click()
}
// canvas 圆角矩形工具
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}
const retakeTest = () => {
  showResult.value = false
  startTest(currentPaper.value)
}
const viewDetail = (h) => {
  alert(`${h.testName}\n得分：${h.score}/${h.totalScore}\n级别：${h.level}\n建议：${h.tip}`)
}
const scrollToHistory = () => {
  nextTick(() => {
    document.getElementById('history')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

// ===== 加载 =====
onMounted(async () => {
  loadFavorites()
  window.addEventListener('profile-updated', onThemeChange)
  window.addEventListener('storage', onThemeChange)
  try {
    const res = await getTestPaperList()
    if (res.code === 200) {
      const data = res.data
      const records = Array.isArray(data) ? data : (data?.records || [])
      if (records.length > 0) {
        // maxScore 是后端真实满分（PsyTestPaper.maxScore），计分时优先用它
        papers.value = records.map(r => ({
          testId: r.testId,
          testName: r.testName,
          description: r.testDesc || '专业心理测评量表，帮助你了解自己的心理状态。',
          questionCount: r.questionCount || 10,
          maxScore: r.maxScore ?? null,
          takenCount: r.takenCount || 0,
          category: pickCat(r.testName)
        }))
      }
    }
  } catch (e) {
    // 量表列表拉取失败时降级使用默认数据
  }
  // 深链支持：/test?testId=xx  直接打开对应量表详情（主页推荐卡点进来）
  const qId = Number(route.query.testId)
  if (qId) {
    const paper = papers.value.find(p => p.testId === qId)
    if (paper) showPaperDetail(paper)
  }
  // 深链支持：/test?paper=名称  按量表名匹配打开（AI 推荐胶囊无 testId 时的兜底）
  const qName = route.query.paper
  if (qName && !qId) {
    const paper = papers.value.find(p =>
      p.testName === qName || p.testName.includes(qName) || qName.includes(p.testName)
    )
    if (paper) showPaperDetail(paper)
    else ElMessage.info('没有找到「' + qName + '」，先看看全部测评吧~')
  }
  try {
    const uid = getUid()
    if (uid) {
      const h = await getRecordList({ userId: uid, page: 1, size: 50 })
      if (h.code === 200 && h.data?.records) {
        myHistory.value = h.data.records.map(r => ({
          id: r.recordId || r.id,
          testName: r.paperName || r.testName,
          score: r.score,
          totalScore: r.totalScore,
          level: r.level,
          tip: r.tip,
          createTime: r.createTime
        }))
      }
    }
  } catch (e) {}
})

onBeforeUnmount(() => {
  window.removeEventListener('profile-updated', onThemeChange)
  window.removeEventListener('storage', onThemeChange)
  // 全屏答题页可能还开着：卸载时恢复滚动
  document.body.style.overflow = ''
})

const pickCat = (name) => {
  if (!name) return 'all'
  const n = name.toLowerCase()
  if (n.includes('焦虑') || n.includes('gad') || n.includes('sas') || n.includes('stai') ||
      n.includes('bai') || n.includes('hama') || n.includes('pswq') || n.includes('spin') ||
      n.includes('pdss') || n.includes('acq') || n.includes('cas') || n.includes('masq') ||
      n.includes('lsas') || n.includes('tafs') || n.includes(' aq') || n.includes('fne') ||
      n.includes('hads'))
    return 'anxiety'
  if (n.includes('抑郁') || n.includes('phq') || n.includes('sds') || n.includes('bdi') ||
      n.includes('ces') || n.includes('hamd') || n.includes('gds') || n.includes('epds') ||
      n.includes('mdq') || n.includes('qids') || n.includes('dass') || n.includes('ids') ||
      n.includes('hscl') || n.includes('rads') || n.includes('cdrs') || n.includes('scl'))
    return 'depress'
  if (n.includes('压力') || n.includes('pss') || n.includes('cope') || n.includes('rrs') ||
      n.includes('cerq') || n.includes('mbi'))
    return 'stress'
  if (n.includes('失眠') || n.includes('睡眠') || n.includes('sleep') || n.includes('psqi') ||
      n.includes('isi') || n.includes('dbas') || n.includes('ess'))
    return 'sleep'
  if (n.includes('人格') || n.includes('内外向') || n.includes('epq') || n.includes('mbti') ||
      n.includes('大五') || n.includes('ecr') || n.includes('rsq'))
    return 'personality'
  if (n.includes('自尊') || n.includes('ses') || n.includes('孤独') || n.includes('ucla') ||
      n.includes('atq') || n.includes('sns') || n.includes('who') || n.includes('swls') ||
      n.includes('panas') || n.includes('lot') || n.includes('心理韧性') || n.includes('nyc') ||
      n.includes('y-bocs') || n.includes('强迫') || n.includes('aaq'))
    return 'self'
  return 'all'
}
</script>

<style scoped>
/* ===== 页面容器（动漫风：波点纸感底色，全部颜色走 --t-* 主题变量） ===== */
.test-page {
  --t-hex: #e59bb3;
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
  padding: 0 0 60px;
  overflow-x: hidden;
  background:
    radial-gradient(circle at 8% 6%, var(--t-hero1) 0%, transparent 34%),
    radial-gradient(circle at 94% 18%, var(--t-poster2) 0%, transparent 30%),
    /* 波点纹理：动漫纸感的灵魂 */
    radial-gradient(rgba(255,255,255,0.75) 1.6px, transparent 1.6px),
    linear-gradient(180deg, var(--t-bg2) 0%, #fff 45%, var(--t-bg1) 100%);
  background-size: auto, auto, 26px 26px, auto;
  transition: background 0.3s;
}

/* ===== 顶部海报横幅（动漫风：渐变天空 + 吉祥物 + 星星 + 波浪） ===== */
.hero {
  width: 100%;
  padding: 44px 28px 58px;
  box-sizing: border-box;
  background: linear-gradient(135deg, var(--t-hero1) 0%, var(--t-hero2) 45%, var(--t-hero3) 100%);
  border-radius: 0 0 36px 36px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 12px 32px var(--t-poster2);
  transition: background 0.3s;
}
.hero-blob {
  position: absolute;
  border-radius: 50%;
  background: rgba(255,255,255,0.25);
  filter: blur(2px);
}
.hero-blob.b1 { width: 220px; height: 220px; top: -80px; right: -40px; }
.hero-blob.b2 { width: 140px; height: 140px; bottom: -60px; left: 12%; background: rgba(255,255,255,0.18); }
/* 一闪一闪的星星 */
.hero-star {
  position: absolute; color: rgba(255,255,255,0.95);
  animation: twinkle 2.4s ease-in-out infinite;
  text-shadow: 0 0 10px rgba(255,255,255,0.9);
  pointer-events: none;
}
.hero-star.s1 { font-size: 20px; top: 24%; left: 30%; }
.hero-star.s2 { font-size: 14px; top: 58%; left: 46%; animation-delay: 0.8s; }
.hero-star.s3 { font-size: 16px; top: 18%; right: 26%; animation-delay: 1.4s; }
@keyframes twinkle {
  0%, 100% { opacity: 0.35; transform: scale(0.85) rotate(0deg); }
  50% { opacity: 1; transform: scale(1.2) rotate(18deg); }
}
/* 慢慢飘的云 */
.hero-cloud {
  position: absolute; color: rgba(255,255,255,0.5);
  font-size: 44px; pointer-events: none;
  animation: cloudDrift 26s linear infinite;
}
.hero-cloud.c1 { top: 12%; left: -60px; }
.hero-cloud.c2 { top: 55%; left: -120px; font-size: 32px; animation-delay: -13s; animation-duration: 34s; }
@keyframes cloudDrift {
  from { transform: translateX(0); }
  to { transform: translateX(calc(100vw + 200px)); }
}
/* 吉祥物：上下浮动的小熊（站在标题与统计胶囊之间的空档，避免压住胶囊） */
/* 吉祥物小熊：弹性布局中间项，自动站在标题与右侧统计的空档；窄屏让位 */
.hero-mascot {
  font-size: 46px; font-weight: 700;
  color: rgba(255,255,255,0.96);
  text-shadow: 0 3px 0 var(--t-poster2), 0 8px 18px var(--t-poster2);
  animation: mascotBob 2.6s ease-in-out infinite;
  pointer-events: none;
  letter-spacing: -2px;
  line-height: 1;
  flex-shrink: 0;
  user-select: none;
}
@keyframes mascotBob {
  0%, 100% { transform: translateY(0) rotate(-4deg); }
  50% { transform: translateY(-10px) rotate(4deg); }
}
/* 窄屏时标题和胶囊会挤到一起，装饰全部让位 */
@media (max-width: 1180px) {
  .hero-mascot { display: none; }
  .hero-star.s3 { display: none; }
}
/* 波浪底边 */
.hero-wave {
  position: absolute; bottom: -2px; left: 0;
  width: 100%; height: 46px; display: block;
  pointer-events: none;
}
.hero-inner {
  position: relative; z-index: 2;
  display: flex; justify-content: space-between; align-items: center;
  gap: 20px; flex-wrap: wrap; max-width: 1400px; margin: 0 auto;
}
.hero-title h1 {
  font-size: 34px; font-weight: 800; letter-spacing: 2px;
  color: #fff; margin: 0 0 8px;
  line-height: 1.25;
  text-shadow: 0 2px 0 var(--t-poster2), 0 3px 12px var(--t-poster2);
}
.hero-sub { font-size: 15px; color: rgba(255,255,255,0.92); margin: 0; letter-spacing: 2px; }
.hero-meta { display: flex; flex-direction: row; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: flex-end; }
.meta-chip {
  background: rgba(255,255,255,0.88);
  border-radius: 18px; padding: 8px 16px;
  font-size: 13px; color: var(--t-mid);
  border: 2px solid rgba(255,255,255,0.9);
  outline: 1.5px solid var(--t-poster2);
  backdrop-filter: blur(6px);
}
.meta-chip b { font-size: 18px; color: var(--t-strong); margin-right: 4px; }
.history-btn {
  background: var(--t-hex); color: #fff;
  border: none; padding: 10px 20px; border-radius: 22px;
  font-weight: 700; font-size: 14px; cursor: pointer;
  font-family: inherit; transition: all 0.2s;
  border: 2px solid #fff;
  outline: 1.5px solid var(--t-poster2);
  box-shadow: 0 6px 16px var(--t-poster2);
}
.history-btn:hover { transform: translateY(-2px) rotate(-1deg); filter: brightness(1.05); }

/* ===== 分类磁贴 + 搜索（磁贴即筛选项：白描边圈 + 选中主题色描边高亮） ===== */
.cat-nav {
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px; padding: 22px 22px 8px;
  flex-wrap: wrap;
}
.quick-tiles {
  display: flex; gap: 20px; justify-content: flex-start;
  flex-wrap: wrap;
}
.quick-tile {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  cursor: pointer; transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  width: 76px;
}
.quick-tile:hover { transform: translateY(-5px) rotate(-5deg) scale(1.06); }
.quick-tile:nth-child(odd) { animation: tileWiggle 3.2s ease-in-out infinite; }
.quick-tile:nth-child(even) { animation: tileWiggle 3.2s ease-in-out infinite 1.6s; }
@keyframes tileWiggle {
  0%, 88%, 100% { transform: rotate(0deg); }
  92% { transform: rotate(-4deg); }
  96% { transform: rotate(4deg); }
}
.tile-icon {
  width: 62px; height: 62px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px;
  border: 3px solid #fff;
  outline: 2px solid var(--t-poster2);
  box-shadow: 0 6px 0 var(--t-poster1), 0 10px 18px var(--t-poster1);
  transition: box-shadow 0.2s;
}
.quick-tile:hover .tile-icon { box-shadow: 0 2px 0 var(--t-poster1), 0 6px 12px var(--t-poster1); }
.tile-name { font-size: 13px; color: var(--t-mid); font-weight: 700; }
/* 选中态：主题色描边 + 名字变色，替代原文字药丸 */
.quick-tile.on .tile-icon {
  outline: 3px solid var(--t-hex);
  box-shadow: 0 6px 0 var(--t-hex), 0 10px 18px var(--t-poster1);
  transform: scale(1.08);
}
.quick-tile.on .tile-name { color: var(--t-hex); }
.quick-tile.on { transform: scale(1.04); }

/* ===== 搜索框 ===== */
.search-box {
  display: flex; align-items: center;
  background: rgba(255,255,255,0.9);
  border-radius: 22px; padding: 9px 16px;
  border: 2px solid var(--t-poster2);
  min-width: 200px; flex: 0 1 auto; transition: all 0.2s;
}
.search-box:focus-within {
  border-color: var(--t-hex);
  box-shadow: 0 4px 14px var(--t-poster1);
  transform: scale(1.02);
}
.search-icon { font-size: 14px; margin-right: 8px; }
.search-input {
  border: none; background: transparent;
  font-size: 14px; color: var(--t-text);
  outline: none; flex: 1; font-family: inherit;
}
.search-input::placeholder { color: var(--t-weak); }
.search-clear {
  background: none; border: none;
  color: var(--t-weak); cursor: pointer; font-size: 12px; padding: 0 0 0 6px;
}
.search-clear:hover { color: var(--t-mid); }

/* ===== 量表卡片网格 ===== */
.scale-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 8px 22px 0;
  box-sizing: border-box;
}
@media (max-width: 1024px) { .scale-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px)  {
  .scale-grid { grid-template-columns: repeat(2, 1fr); }
  .hero-inner { flex-direction: column; align-items: flex-start; }
  .hero-meta { width: 100%; flex-direction: row; flex-wrap: wrap; align-items: center; }
}
@media (max-width: 480px) { .scale-grid { grid-template-columns: 1fr; } }

/* 卡片：动漫贴纸风 —— 厚描边 + 硬投影 + 悬停摇晃（白底统一，颜色走主题） */
.card-t1, .card-t2, .card-t3, .card-t4, .card-t5 { background: var(--t-card1); }

/* 语义色（历史记录等级 / 结果分级）：绿=良好 黄=关注 红=干预，不随主题变 */
.lv-low  { background: #e3f4e6; color: #3d8a4a; }
.lv-mid  { background: #fdf0d6; color: #c08a3e; }
.lv-high { background: #fddadb; color: #d05050; }

.scale-card {
  border: 2.5px solid var(--t-poster2);
  border-radius: 26px;
  padding: 20px;
  position: relative;
  display: flex; flex-direction: column;
  min-height: 240px;
  cursor: pointer;
  /* 贴纸的灵魂：硬偏移投影，而不是模糊阴影 */
  box-shadow: 5px 6px 0 var(--t-poster1);
  transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.22s;
}
/* 右上角的小星星装饰 */
.scale-card::after {
  content: '';
  position: absolute; top: 10px; right: 38px;
  width: 13px; height: 13px; color: var(--t-poster2);
  opacity: 0.7;
  transition: all 0.25s;
  background: currentColor;
  -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23000'%3E%3Cpath d='M12 3.2l1.8 4.6 4.6 1.8-4.6 1.8L12 16l-1.8-4.6L5.6 9.6l4.6-1.8z'/%3E%3C/svg%3E") center/contain no-repeat;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23000'%3E%3Cpath d='M12 3.2l1.8 4.6 4.6 1.8-4.6 1.8L12 16l-1.8-4.6L5.6 9.6l4.6-1.8z'/%3E%3C/svg%3E") center/contain no-repeat;

}
.scale-card:hover {
  transform: translateY(-6px) rotate(-1.2deg);
  border-color: var(--t-hex);
  box-shadow: 8px 10px 0 var(--t-poster1), 0 16px 30px var(--t-poster1);
  animation: cardWiggle 0.4s ease;
}
.scale-card:hover::after {
  opacity: 1;
  transform: scale(1.4) rotate(20deg);
  color: var(--t-hex);
}
@keyframes cardWiggle {
  0% { transform: translateY(-6px) rotate(0deg); }
  30% { transform: translateY(-7px) rotate(-2deg); }
  60% { transform: translateY(-6px) rotate(0.8deg); }
  100% { transform: translateY(-6px) rotate(-1.2deg); }
}
.favorite-btn {
  background: none; border: none; font-size: 17px;
  cursor: pointer; padding: 0; flex-shrink: 0;
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1); line-height: 1;
}
.favorite-btn:hover { transform: scale(1.35) rotate(12deg); }
.favorite-btn.on { animation: heartPop 0.35s ease; }
@keyframes heartPop {
  0% { transform: scale(0.6); }
  55% { transform: scale(1.45) rotate(-10deg); }
  100% { transform: scale(1); }
}

/* 头部：圆形渐变徽章 + 标题 + 收藏 */
.sc-head { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 10px; }
.sc-icon {
  width: 50px; height: 50px; flex-shrink: 0;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 25px;
  background: linear-gradient(135deg, var(--t-poster1), var(--t-poster2));
  border: 2.5px solid #fff;
  outline: 2px solid var(--t-poster2);
  box-shadow: 0 3px 0 var(--t-poster1);
}
.scale-name {
  flex: 1;
  font-size: 16px; font-weight: 800; color: var(--t-deep);
  margin: 0; line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 元信息一行：徽章 · 题数 · 时长 */
.scale-meta {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: var(--t-mid);
  margin-bottom: 8px;
}
.sc-badge {
  background: var(--t-poster1); color: var(--t-strong);
  font-size: 11px; font-weight: 700;
  padding: 2px 10px; border-radius: 10px;
  border: 1.5px dashed var(--t-poster2);
  white-space: nowrap;
  transform: rotate(-1.5deg);
}
.meta-dot { width: 4px; height: 4px; border-radius: 50%; background: var(--t-weak); flex-shrink: 0; }

/* 描述：最多两行 */
.scale-desc {
  font-size: 13px; color: var(--t-weak); line-height: 1.65;
  margin: 0 0 12px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 底部：人数 + 通栏按钮（果冻按钮 + 高光扫过） */
.sc-foot { margin-top: auto; display: flex; flex-direction: column; gap: 10px; }
.sc-taken { font-size: 12px; color: var(--t-weak); }
.start-btn {
  position: relative;
  width: 100%; padding: 12px 0; border-radius: 20px;
  font-size: 14.5px; font-weight: 700; cursor: pointer;
  transition: all 0.22s cubic-bezier(0.34, 1.56, 0.64, 1);
  border: 2.5px solid #fff; outline: 2px solid var(--t-poster2);
  font-family: inherit;
  background: linear-gradient(135deg, var(--t-main), var(--t-hex));
  color: #fff;
  box-shadow: 0 5px 0 var(--t-poster2), 0 8px 16px var(--t-poster2);
  overflow: hidden;
}
/* 高光扫过动画：像贴纸反光一样 */
.start-btn::before {
  content: '';
  position: absolute; top: 0; left: -80%;
  width: 50%; height: 100%;
  background: linear-gradient(105deg, transparent, rgba(255,255,255,0.5), transparent);
  transform: skewX(-20deg);
  animation: btnShine 3.4s ease-in-out infinite;
}
@keyframes btnShine {
  0%, 60% { left: -80%; }
  85%, 100% { left: 130%; }
}
.start-btn:hover {
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 8px 0 var(--t-poster2), 0 14px 24px var(--t-poster2);
}
.start-btn:active { transform: translateY(1px); box-shadow: 0 2px 0 var(--t-poster2); }

/* ===== 空状态 ===== */
.empty {
  text-align: center; padding: 60px 20px;
  margin: 20px 22px 0;
  background: rgba(255,255,255,0.7); border-radius: 22px;
  box-sizing: border-box;
}
.empty-illus { font-size: 48px; margin-bottom: 12px; }
.empty p { margin: 0; color: var(--t-mid); }
.empty p strong { color: var(--t-deep); }
.empty-hint { font-size: 13px; color: var(--t-weak); margin-top: 12px !important; }
.hot-tags { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; margin-top: 10px; }
.hot-tag {
  background: var(--t-poster1); color: var(--t-strong);
  padding: 5px 14px; border-radius: 14px;
  font-size: 13px; cursor: pointer; transition: all 0.2s; font-weight: 600;
}
.hot-tag:hover { background: var(--t-hex); color: #fff; }

/* ===== 趋势图 ===== */
.trend-area { width: 100%; margin: 32px auto 0; padding: 0 22px; box-sizing: border-box; }
.sec-title {
  font-size: 20px; font-weight: 800; color: var(--t-deep);
  margin-bottom: 16px; padding-left: 12px;
  border-left: 4px solid var(--t-hex);
}
.trend-card {
  background: rgba(255,255,255,0.9); border-radius: 26px;
  border: 2.5px solid var(--t-poster2);
  padding: 20px 24px;
  box-shadow: 5px 6px 0 var(--t-poster1);
}
.trend-chart { width: 100%; overflow-x: auto; }
.trend-svg { width: 100%; height: 200px; min-width: 500px; }
.tl-axis { stroke: var(--t-poster2); stroke-width: 1; fill: none; }
.tl-line { stroke: var(--t-hex); stroke-width: 3; fill: none; stroke-linecap: round; stroke-linejoin: round; }
.tl-dot { fill: var(--t-hex); stroke: #fff; stroke-width: 2; }
.tl-num { fill: var(--t-strong); font-size: 12px; }
.trend-labels { display: flex; justify-content: space-between; padding: 0 30px; margin-top: 4px; }
.trend-label { font-size: 11px; color: var(--t-weak); }
.trend-tip { text-align: center; font-size: 13px; color: var(--t-mid); margin: 12px 0 0; }

/* ===== 历史记录 ===== */
.history-area { width: 100%; margin: 32px auto 0; padding: 0 22px; box-sizing: border-box; }
.history-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.history-card {
  background: rgba(255,255,255,0.9); border-radius: 26px;
  border: 2.5px solid var(--t-poster2);
  padding: 18px 20px;
  box-shadow: 5px 6px 0 var(--t-poster1);
  transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.history-card:hover { transform: translateY(-4px) rotate(-0.8deg); box-shadow: 7px 9px 0 var(--t-poster1); }
.his-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.his-name { font-size: 15px; font-weight: 800; color: var(--t-deep); }
.his-date { font-size: 12px; color: var(--t-weak); }
.his-body { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.his-score { display: flex; align-items: baseline; }
.score-num { font-size: 32px; font-weight: 800; color: var(--t-strong); }
.score-total { font-size: 13px; color: var(--t-weak); }
.his-level { font-size: 12px; padding: 3px 12px; border-radius: 10px; font-weight: 700; }
.his-tip { font-size: 13px; color: var(--t-mid); margin: 8px 0 10px; line-height: 1.5; }
.his-view { background: transparent; border: none; color: var(--t-hex); font-size: 13px; cursor: pointer; padding: 0; font-weight: 700; }
.his-view:hover { color: var(--t-strong); }

/* ===== Modal ===== */
.modal-mask {
  position: fixed; inset: 0;
  /* 深色遮罩：主题深色打底 + 半透明（color-mix 不支持时退回 45% 黑） */
  background: rgba(40, 20, 30, 0.45);
  backdrop-filter: blur(6px);
  display: flex; align-items: center; justify-content: center;
  z-index: 999;
  animation: maskFade 0.2s ease;
  padding: 20px;
}
@supports (background: color-mix(in srgb, red 50%, transparent)) {
  .modal-mask { background: color-mix(in srgb, var(--t-deep) 55%, transparent); }
}
@keyframes maskFade { from { opacity: 0; } to { opacity: 1; } }
.modal-card {
  border-radius: 26px; padding: 28px 32px; max-width: 520px; width: 90%;
  background: #fff;
  border: 2.5px solid var(--t-poster2);
  box-shadow: 8px 10px 0 var(--t-poster1), 0 24px 60px var(--t-poster2);
  animation: modalPop 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  max-height: 86vh; overflow-y: auto;
}
@keyframes modalPop { from { opacity: 0; transform: scale(0.92) translateY(8px); } to { opacity: 1; transform: scale(1); } }
.close-btn {
  background: none; border: none; font-size: 20px;
  color: var(--t-weak); cursor: pointer; padding: 0; line-height: 1;
}
.close-btn:hover { color: var(--t-mid); }

/* 量表详情弹窗 */
.detail-modal { max-width: 480px; }
.detail-head { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; position: relative; }
.detail-head .close-btn { position: absolute; top: 0; right: 0; }
.detail-icon { font-size: 36px; }
.detail-title { font-size: 20px; color: var(--t-deep); margin: 0; }
.detail-tags { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px; }
.detail-tag {
  font-size: 12px; color: var(--t-mid);
  background: var(--t-poster1);
  padding: 3px 10px; border-radius: 8px;
}
.detail-body { display: flex; flex-direction: column; gap: 16px; margin-bottom: 20px; }
.detail-section h4 { font-size: 15px; color: var(--t-deep); margin: 0 0 6px; }
.detail-section p { font-size: 14px; color: var(--t-mid); line-height: 1.7; margin: 0; }
.detail-foot { display: flex; justify-content: flex-end; gap: 10px; }

/* 通用按钮 */
.foot-btn {
  padding: 10px 22px; border-radius: 18px; border: none;
  font-size: 14px; font-weight: 700; cursor: pointer;
  background: var(--t-poster1); color: var(--t-mid);
  transition: all 0.2s; font-family: inherit;
}
.foot-btn:hover:not(:disabled) { filter: brightness(0.97); }
.foot-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.foot-btn.primary {
  background: linear-gradient(135deg, var(--t-main), var(--t-hex)); color: #fff;
  box-shadow: 0 6px 16px var(--t-poster2);
}
.foot-btn.primary:hover:not(:disabled) { transform: translateY(-1px); }

/* ===== 沉浸式答题页 ===== */
.quiz-screen {
  position: fixed; inset: 0; z-index: 1000;
  background:
    radial-gradient(circle at 10% 8%, rgba(255,255,255,0.5) 0%, transparent 30%),
    linear-gradient(160deg, var(--t-quiz1) 0%, var(--t-quiz2) 40%, var(--t-quiz3) 100%);
  display: flex; flex-direction: column;
  animation: quizIn 0.3s ease;
  overflow-y: auto;
  transition: background 0.3s;
}
@keyframes quizIn { from { opacity: 0; transform: scale(1.03); } to { opacity: 1; transform: scale(1); } }
.quiz-blob { position: absolute; border-radius: 50%; pointer-events: none; }
.quiz-blob.b1 { width: 260px; height: 260px; background: rgba(255,255,255,0.22); top: -60px; right: -50px; }
.quiz-blob.b2 { width: 180px; height: 180px; background: rgba(255,255,255,0.3); bottom: 8%; left: -60px; }
.quiz-blob.b3 { width: 120px; height: 120px; background: rgba(255,255,255,0.25); top: 30%; right: -30px; }
/* 漂浮的星星和爱心 */
.quiz-star {
  position: absolute; color: rgba(255,255,255,0.85);
  pointer-events: none; text-shadow: 0 0 10px rgba(255,255,255,0.8);
  animation: starFloat 5s ease-in-out infinite;
}
.quiz-star.qs1 { font-size: 22px; top: 16%; left: 10%; }
.quiz-star.qs2 { font-size: 18px; top: 30%; right: 12%; animation-delay: 1.2s; }
.quiz-star.qs3 { font-size: 15px; top: 60%; left: 7%; animation-delay: 2.4s; }
.quiz-star.qs4 { font-size: 20px; bottom: 24%; right: 8%; animation-delay: 3.6s; }
@keyframes starFloat {
  0%, 100% { transform: translateY(0) rotate(0deg); opacity: 0.5; }
  50% { transform: translateY(-16px) rotate(14deg); opacity: 1; }
}

.quiz-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; position: relative; z-index: 2;
}
.quiz-back, .quiz-close {
  width: 40px; height: 40px; border-radius: 50%;
  background: rgba(255,255,255,0.8); border: 2px solid #fff;
  outline: 1.5px solid var(--t-poster2);
  font-size: 20px; color: var(--t-strong); cursor: pointer;
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1); line-height: 1;
  box-shadow: 0 3px 0 var(--t-poster1);
}
.quiz-back:hover, .quiz-close:hover { background: #fff; transform: scale(1.12) rotate(-6deg); }
.quiz-name {
  font-size: 15px; font-weight: 700; color: var(--t-deep);
  max-width: 60%; text-align: center;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.quiz-body {
  flex: 1; position: relative; z-index: 2;
  width: 100%; max-width: 620px; margin: 0 auto;
  padding: 12px 24px 20px;
  box-sizing: border-box;
  display: flex; flex-direction: column;
}
.quiz-num {
  font-size: 52px; font-weight: 300; font-style: italic;
  color: rgba(255,255,255,0.95);
  text-align: center;
  letter-spacing: 3px;
  text-shadow: 0 2px 12px var(--t-poster2);
  margin: 8px 0 4px;
  font-family: Georgia, 'Times New Roman', serif;
}
.q-text {
  font-size: 19px; font-weight: 700; color: var(--t-deep);
  line-height: 1.7; text-align: center;
  margin: 6px 0 26px;
  padding: 0 8px;
}
/* 选项 = 逐个弹入的气泡卡 */
.q-options { display: flex; flex-direction: column; gap: 16px; }
.opt-btn {
  display: flex; align-items: center; gap: 16px;
  width: 100%; text-align: left;
  padding: 18px 22px; border-radius: 24px;
  background: rgba(255,255,255,0.94);
  border: 2.5px solid #fff;
  outline: 2px solid var(--t-poster2);
  font-size: 15.5px; cursor: pointer;
  transition: all 0.18s cubic-bezier(0.34, 1.56, 0.64, 1);
  font-family: inherit; color: var(--t-text);
  box-shadow: 0 4px 0 var(--t-poster1);
  animation: optBounceIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) backwards;
}
@keyframes optBounceIn {
  from { opacity: 0; transform: translateY(18px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.opt-btn:hover {
  background: #fff;
  transform: translateY(-3px) rotate(-0.6deg);
  box-shadow: 0 7px 0 var(--t-poster1), 0 10px 18px var(--t-poster1);
}
.opt-btn.on {
  background: #fff;
  border-color: var(--t-hex);
  outline-color: var(--t-hex);
  box-shadow: 0 6px 0 var(--t-poster2), 0 10px 20px var(--t-poster2);
  transform: scale(1.02);
}
.opt-letter {
  font-style: italic; font-weight: 700;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 22px; color: var(--t-hex);
  min-width: 34px;
}
.opt-btn.on .opt-letter { color: var(--t-strong); }
.opt-text { flex: 1; line-height: 1.55; }
.opt-check {
  width: 26px; height: 26px; border-radius: 50%;
  background: var(--t-hex); color: #fff;
  border: 2px solid #fff;
  outline: 1.5px solid var(--t-poster2);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 800;
  animation: popIn 0.2s ease;
}
@keyframes popIn { from { transform: scale(0.4); opacity: 0; } to { transform: scale(1); opacity: 1; } }

.quiz-foot {
  position: sticky; bottom: 0; z-index: 2;
  padding: 14px 24px 22px;
  width: 100%; max-width: 620px; margin: 0 auto;
  box-sizing: border-box;
}
/* 吉祥物陪伴台词：气泡框 + 弹跳 */
.quiz-mascot {
  display: inline-block;
  background: rgba(255,255,255,0.92);
  border: 2px solid #fff;
  outline: 1.5px solid var(--t-poster2);
  color: var(--t-strong);
  font-size: 13px; font-weight: 700;
  padding: 7px 16px; border-radius: 16px 16px 16px 4px;
  margin-bottom: 12px;
  box-shadow: 0 3px 0 var(--t-poster1);
  animation: mascotBob 2.6s ease-in-out infinite;
}
.quiz-progress {
  height: 8px; border-radius: 5px;
  background: rgba(255,255,255,0.45);
  border: 1.5px solid rgba(255,255,255,0.6);
  overflow: hidden; margin-bottom: 14px;
}
.quiz-progress-fill {
  height: 100%; border-radius: 4px;
  background: repeating-linear-gradient(45deg, #fff 0 8px, rgba(255,255,255,0.82) 8px 16px);
  box-shadow: 0 0 8px rgba(255,255,255,0.8);
  transition: width 0.3s ease;
}
.quiz-foot-btns { display: flex; justify-content: center; gap: 14px; }
.quiz-nav {
  padding: 13px 44px; border-radius: 26px;
  border: 2.5px solid #fff; outline: 2px solid var(--t-poster2);
  font-size: 15px; font-weight: 700; cursor: pointer;
  font-family: inherit;
  transition: all 0.22s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 4px 0 var(--t-poster2);
}
.quiz-nav.ghost {
  background: rgba(255,255,255,0.6); color: var(--t-strong);
}
.quiz-nav.ghost:hover:not(:disabled) { background: rgba(255,255,255,0.85); transform: translateY(-2px); }
.quiz-nav.primary {
  background: #fff; color: var(--t-strong);
  box-shadow: 0 5px 0 var(--t-poster2), 0 8px 18px var(--t-poster2);
}
.quiz-nav.primary:hover:not(:disabled) { transform: translateY(-3px); }
.quiz-nav:active:not(:disabled) { transform: translateY(1px); box-shadow: 0 2px 0 var(--t-poster2); }
.quiz-nav:disabled { opacity: 0.45; cursor: not-allowed; }

/* ===== 结果海报 ===== */
.result-wrap { display: flex; flex-direction: column; align-items: center; gap: 16px; width: 100%; max-width: 430px; }
.result-poster {
  width: 100%;
  background: linear-gradient(170deg, var(--t-poster1) 0%, var(--t-poster2) 55%, var(--t-poster3) 100%);
  border-radius: 28px;
  border: 3px solid #fff;
  outline: 2.5px solid var(--t-poster2);
  padding: 26px 22px 20px;
  box-sizing: border-box;
  box-shadow: 8px 10px 0 var(--t-poster2), 0 24px 60px var(--t-poster2);
  position: relative; overflow: hidden;
  max-height: 72vh; overflow-y: auto;
  animation: modalPop 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  transition: background 0.3s;
}
.result-poster::before {
  content: '';
  position: absolute; width: 180px; height: 180px; border-radius: 50%;
  background: rgba(255,255,255,0.3); top: -60px; right: -50px;
}
/* 海报角落的星星装饰 */
.result-poster::after {
  content: '';
  position: absolute; bottom: 74px; left: 20px;
  width: 60px; height: 12px;
  color: rgba(255,255,255,0.7);
  pointer-events: none;
  background: currentColor;
  -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23000'%3E%3Cpath d='M12 3.2l1.8 4.6 4.6 1.8-4.6 1.8L12 16l-1.8-4.6L5.6 9.6l4.6-1.8z'/%3E%3C/svg%3E") left center/12px 12px repeat-x;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23000'%3E%3Cpath d='M12 3.2l1.8 4.6 4.6 1.8-4.6 1.8L12 16l-1.8-4.6L5.6 9.6l4.6-1.8z'/%3E%3C/svg%3E") left center/12px 12px repeat-x;
}
.poster-slogan {
  text-align: center;
  font-size: 15px; font-weight: 700; color: #fff;
  letter-spacing: 2px;
  text-shadow: 0 1px 6px var(--t-poster2);
  margin-bottom: 18px;
}
.poster-pills {
  display: flex; justify-content: center; gap: 10px;
  flex-wrap: wrap; margin-bottom: 16px;
}
.pill {
  padding: 8px 20px; border-radius: 20px;
  font-size: 14px; font-weight: 800;
  max-width: 46%;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.pill-label { background: rgba(255,255,255,0.85); color: var(--t-strong); }
.pill-result { background: var(--t-hex); color: #fff; box-shadow: 0 4px 12px var(--t-poster2); }
/* 结果语义色：中度=橙 干预=红（良好/轻度走主题主色） */
.pill-result.lv-mid { background: #f0a04a; box-shadow: 0 4px 12px rgba(200, 120, 40, 0.35); }
.pill-result.lv-high { background: #e05555; box-shadow: 0 4px 12px rgba(180, 40, 40, 0.35); }
.poster-score-row { display: flex; justify-content: center; margin-bottom: 10px; }
.poster-score { display: flex; align-items: baseline; gap: 4px; }
.ps-num {
  font-size: 62px; font-weight: 800; color: var(--t-strong);
  font-family: Georgia, serif; font-style: italic;
  line-height: 1;
}
.ps-total { font-size: 20px; font-weight: 700; color: var(--t-strong); font-family: Georgia, serif; }
.poster-desc {
  font-size: 14px; color: var(--t-text); line-height: 1.8;
  background: rgba(255,255,255,0.75);
  border-radius: 16px; padding: 14px 16px;
  margin: 0 0 14px;
}
.poster-tips {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 16px;
}
.tips-label {
  font-size: 13px; font-weight: 800; color: var(--t-strong);
  line-height: 1.3; text-align: center;
  writing-mode: horizontal-tb;
  white-space: nowrap;
}
.tips-divider { width: 2px; height: 34px; background: var(--t-poster2); border-radius: 1px; }
.tips-text { flex: 1; font-size: 13px; color: var(--t-mid); line-height: 1.7; margin: 0; }
.poster-tags {
  display: flex; flex-wrap: wrap; gap: 8px;
  margin-bottom: 16px;
}
.poster-tag {
  background: rgba(255,255,255,0.8);
  color: var(--t-strong);
  font-size: 12.5px; font-weight: 700;
  padding: 5px 12px; border-radius: 12px;
}
.poster-brand {
  display: flex; align-items: center; justify-content: space-between;
  border-top: 1.5px dashed var(--t-poster2);
  padding-top: 12px;
}
.poster-brand-logo { font-size: 18px; }
.poster-brand-name { flex: 1; font-size: 13px; font-weight: 800; color: var(--t-strong); margin-left: 8px; }
.poster-date { font-size: 12px; color: var(--t-mid); }
.result-actions { display: flex; gap: 12px; flex-wrap: wrap; justify-content: center; }

/* ===== 加载动画（动漫风：跳跳糖小球） ===== */
.loading-mask {
  position: fixed; inset: 0;
  background: rgba(255, 250, 252, 0.9);
  backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1100;
  animation: maskFade 0.2s ease;
}
@supports (background: color-mix(in srgb, red 50%, transparent)) {
  .loading-mask { background: color-mix(in srgb, var(--t-bg2) 88%, transparent); }
}
.loading-spinner { display: flex; flex-direction: column; align-items: center; gap: 18px; }
.spinner-ring { display: none; }
.loading-dots { display: flex; gap: 10px; }
.loading-dots span {
  width: 16px; height: 16px; border-radius: 50%;
  background: var(--t-hex);
  border: 2px solid #fff;
  outline: 1.5px solid var(--t-poster2);
  animation: dotJump 0.9s ease-in-out infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.15s; background: var(--t-main); }
.loading-dots span:nth-child(3) { animation-delay: 0.3s; background: var(--t-strong); }
@keyframes dotJump {
  0%, 100% { transform: translateY(0); }
  40% { transform: translateY(-14px) scale(1.1); }
  60% { transform: translateY(-6px); }
}
.loading-text { font-size: 15px; color: var(--t-strong); letter-spacing: 2px; }
</style>
