<template>
  <div class="write-page">
    <!-- 顶部横幅（动漫风，颜色跟随个人中心主题色） -->
    <AnimeHero icon="/img/写日记.png" title="记录今日心情" sub="把今天的心情，轻轻放进来 ">
      <template #left>
        <button class="back-btn" @click="$router.push('/diary')"><AppIcon name="arrow-left" /> 返回</button>
      </template>
      <template #right>
        <span class="write-today">{{ today }}</span>
      </template>
    </AnimeHero>

    <!-- 表单主体 -->
    <div class="write-card">
      <!-- ===== 轻量无感推测区 =====
           设计原则：系统只是「建议」，最终决定权永远在用户手上。
           推测结果不写库，只有用户点「采用并预填」并且没有手动改动时，
           保存才会标记为 source=2（图表可据此与纯手动记录分色）。 -->
      <div class="sf-sense" v-if="senseReady">
        <div class="sense-head">
          <div class="sense-titles">
            <b><AppIcon name="wand" /> 系统推测 <span class="sense-badge">推荐</span></b>
            <span>预测结果仅供参考，你可以随意修改或忽略，保存前都由你做主</span>
          </div>
        </div>

        <div v-if="suggestion" class="sense-body">
          <div class="sense-main">
            <span class="sense-emo-badge" :style="{ background: suggestionBg }">{{ suggestion.emotionName }}</span>
            <div class="sense-meta">
              <span class="sense-conf">置信度 {{ pctText(suggestion.confidence) }}</span>
              <span class="sense-reason">{{ suggestion.reason }}</span>
            </div>
          </div>
          <div v-if="suggestion.candidates && suggestion.candidates.length > 1" class="sense-cands">
            <span v-for="c in suggestion.candidates" :key="c.emotion" class="sense-cand">
              {{ c.name }} {{ Math.round(c.p * 100) }}%
            </span>
          </div>
          <div class="sense-actions">
            <button class="sense-adopt" @click="adoptSuggestion"><AppIcon name="sparkles" /> 采用并预填</button>
            <button class="sense-ignore" @click="ignoreSuggestion">不用了</button>
          </div>
          <!-- 历史推测（不是今天的）：说清采用后会记到哪天，避免用户以为是补记那天 -->
          <p v-if="predFromHistory && predFromHistory !== todayStr()" class="sense-note">
            这条推测来自 <b>{{ predFromHistory }}</b>，采用后会按<b>今天</b>记一篇日记。
          </p>
          <p v-if="adoptedMode" class="sense-applied">
            已按推测预填表单，你可以随意修改后再保存；一旦手动改动情绪，这条记录就归为你自己的判断。
          </p>
        </div>

        <div v-else class="sense-actions sense-actions--entry">
          <button class="sense-guess" :disabled="guessing" @click="guessFromText">
            <span v-if="guessing">推测中…</span><span v-else><AppIcon name="wand" /> 帮我猜一下</span>
          </button>
          <button class="sense-textscan" :disabled="scanning || !form.content.trim()" @click="scanText">
            <span v-if="scanning">识别中…</span><span v-else><AppIcon name="search" /> 从文字识别情绪</span>
          </button>
        </div>
        <p v-if="scanResult" class="sense-scan-result">{{ scanResult }}</p>
      </div>

      <!-- 无感记录未开启：一行轻量引导（总开关开着才显示，避免后端没部署时也冒出来） -->
      <div class="sf-sense sense-guide" v-else-if="senseFeatureOn">
        <span class="sg-text"><AppIcon name="wand" /> 开启「无感记录」后，系统可以帮你预填今日心情 —— 只是推荐，填什么仍由你做主</span>
        <router-link to="/profile?tab=sense" class="sg-btn">去开启</router-link>
      </div>

      <!-- 心情选择 -->
      <div class="sf-block">
        <label class="sf-label">心情 <span class="sf-req">· 必填</span></label>
        <div class="sf-emo-row">
          <button
            v-for="e in emotions"
            :key="e.val"
            type="button"
            class="sf-emo-btn"
            :class="{ active: form.emotion === e.val }"
            :style="form.emotion === e.val ? {background: e.bg, borderColor: e.bg, color: '#fff'} : {borderColor: e.bg}"
            @click="pickEmotion(e.val)"
          >
            <img class="emo-btn-icon" :src="e.icon" alt="" /> {{ e.label }}
          </button>
        </div>
      </div>

      <!-- 情绪强度 -->
      <div class="sf-block">
        <label class="sf-label">情绪强度 <span class="sf-opt">· 可选，默认中度</span></label>
        <div class="sf-intensity-row">
          <button
            v-for="lv in intensityLevels"
            :key="lv.val"
            type="button"
            class="sf-int-btn"
            :class="{ active: form.intensity === lv.val }"
            :style="form.intensity === lv.val ? { background: lv.bg, borderColor: lv.bg, color: '#fff' } : { borderColor: lv.bg, color: lv.deep }"
            @click="pickIntensity(lv.val)"
          >
            <b>{{ lv.label }}</b><i>{{ lv.desc }}</i>
          </button>
        </div>
      </div>

      <!-- 昨晚睡眠（生活因子，用于数据看板「睡眠 × 情绪」散点/回归） -->
      <div class="sf-block">
        <label class="sf-label">昨晚睡眠 <span class="sf-opt">· 可选，用于睡眠与情绪的关系分析</span></label>
        <div class="sf-sleep-row">
          <button
            v-for="h in sleepPresets"
            :key="h"
            type="button"
            class="sf-sleep-chip"
            :class="{ active: form.sleepHours === h }"
            @click="pickSleep(h)"
          >{{ h }} 小时</button>
          <div class="sf-sleep-custom" :class="{ active: isCustomSleep }">
            <input
              v-model.number="form.sleepHours"
              type="number" min="0" max="24" step="0.5"
              placeholder="自定义"
              class="sf-sleep-input"
            />
            <span class="sf-sleep-unit">小时</span>
          </div>
          <button v-if="form.sleepHours !== null && form.sleepHours !== ''" type="button" class="sf-sleep-clear" @click="form.sleepHours = null">不记了</button>
        </div>
        <p class="sf-sleep-tip">不填不影响保存；填了才能在数据看板看到「睡眠 <AppIcon name="arrows-horizontal" /> 情绪」的相关性</p>
      </div>

      <!-- 文字记录 -->
      <div class="sf-block">
        <label class="sf-label">记录 <span class="sf-req">· 必填</span></label>
        <textarea
          ref="contentRef"
          v-model="form.content"
          rows="6"
          maxlength="300"
          placeholder="此刻的心情、想法都可以写在这里..."
          class="sf-textarea"
        ></textarea>
        <span class="sf-counter">{{ form.content.length }}/300</span>
      </div>

      <!-- 图片上传 -->
      <div class="sf-block">
        <label class="sf-label">图片 <span class="sf-opt">· 可选</span></label>
        <div v-if="form.imageUrl" class="sf-img-preview">
          <img :src="form.imageUrl" alt="日记配图" />
          <button class="sf-img-remove" @click="form.imageUrl = ''" title="移除图片">×</button>
        </div>
        <label v-else class="sf-img-placeholder sf-img-upload">
          <input type="file" accept="image/*" @change="onPickImage" hidden />
          <span class="sf-ph-icon"><AppIcon name="image" /></span>
          <span class="sf-ph-text">点击选择图片</span>
          <span class="sf-ph-sub">支持 JPG / PNG，不超过 5MB，保存后展示原图</span>
        </label>
      </div>

      <!-- 保存按钮 -->
      <!-- 不再因「没写内容」而禁用：点了就告诉用户还差什么、差在哪，
           比一颗永远点不动的灰按钮更容易让人明白下一步该做什么 -->
      <div class="sf-btn-row">
        <button class="sf-save-btn" @click="saveDiary" :disabled="saving">
          <span v-if="saving">保存中...</span>
          <span v-else-if="!form.content.trim()">先写点什么，再保存</span>
          <span v-else><AppIcon name="check" /> 保存日记</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { EMOTIONS as emotions } from '@/utils/constants'  // 情绪字典唯一真源（utils/constants.js）
import { useRouter, useRoute } from 'vue-router'
import { saveDiary as apiSaveDiary, uploadFile } from '../api'
import AnimeHero from '@/components/AnimeHero.vue'
import { ElMessage } from 'element-plus'
import {
  getTodayPrediction, generatePrediction, analyzeText,
  confirmPrediction, rejectPrediction, getSenseConfig,
} from '../api/sense'

const router = useRouter()
const route = useRoute()
const today = new Date().toLocaleDateString('zh-CN')

// 情绪配置（和 Diary.vue 保持一致）

const form = ref({ content: '', emotion: 1, intensity: 2, imageUrl: '', sleepHours: null })
const saving = ref(false)

// 昨晚睡眠时长：预设常用值 + 自定义输入（单位小时，0~24，支持 0.5）
const sleepPresets = [5, 6, 7, 8, 9]
const pickSleep = (h) => { form.value.sleepHours = form.value.sleepHours === h ? null : h }
const isCustomSleep = computed(() => {
  const v = form.value.sleepHours
  return v !== null && v !== '' && !Number.isNaN(Number(v)) && !sleepPresets.includes(Number(v))
})
// 统一成后端要的 number|null：空串/负数/超 24 一律当没填，避免脏数据进散点图
const normalizedSleep = () => {
  const v = form.value.sleepHours
  if (v === null || v === '' || Number.isNaN(Number(v))) return null
  const n = Number(v)
  if (n < 0 || n > 24) return null
  return Math.round(n * 2) / 2
}

// 情绪强度（1轻度 2中度 3重度）
const intensityLevels = [
  { val: 1, label: '轻度', desc: '有一点点', bg: '#b8d49a', deep: '#5c7a42' },
  { val: 2, label: '中度', desc: '比较明显', bg: '#ffd09a', deep: '#a3742a' },
  { val: 3, label: '重度', desc: '非常强烈', bg: '#f0b98c', deep: '#a3642a' },
]

// 主页心情打卡跳转过来时，预选对应心情与强度（?emotion=1~9&intensity=1~3）
const emotionFromQuery = Number(route.query.emotion)
if (emotions.some(e => e.val === emotionFromQuery)) {
  form.value.emotion = emotionFromQuery
}
const intensityFromQuery = Number(route.query.intensity)
if ([1, 2, 3].includes(intensityFromQuery)) {
  form.value.intensity = intensityFromQuery
}

// 图片上传
const onPickImage = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
    if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片不能超过 5MB'); e.target.value = ''; return }
    try {
      const res = await uploadFile(file, 'diary')
      if (res?.code === 200 && res.data?.url) {
        form.value.imageUrl = res.data.url
      } else {
        ElMessage.error('上传失败：' + (res?.msg || '未知错误'))
      }
    } catch {
      ElMessage.error('网络错误，上传失败')
    } finally {
    e.target.value = ''
  }
}

// ==================== 轻量无感推测 ====================
const suggestion = ref(null)      // 今日待确认推测
const guessing = ref(false)
const scanning = ref(false)
const scanResult = ref('')
const adoptedMode = ref(false)    // true = 本次保存视为「采用系统推测」
// 无感模块是否可用（后端总开关 + 用户开关都开了才显示这块卡片，
// 否则用户点「帮我猜一下」必然 403，只会造成误导）
const senseReady = ref(false)
// 后端总开关开着但用户还没开：显示一行轻量引导，而不是藏得无影无踪
const senseFeatureOn = ref(false)

/** 探测无感模块可用性：任何失败（未登录/未开启/后端未部署）都静默隐藏卡片 */
async function loadSenseReady() {
  try {
    const res = await getSenseConfig()
    const d = res?.data
    senseFeatureOn.value = res?.code === 200 && !!d?.featureEnabled   // 后端总开关
    senseReady.value = senseFeatureOn.value && !!d?.enabled           // 用户自己的开关
  } catch { senseReady.value = false }
}

const suggestionBg = computed(() => {
  const e = emotions.find((x) => x.val === suggestion.value?.emotion)
  return e?.bg || '#b8d49a'
})
const pctText = (p) => Math.round((p || 0) * 100) + '%'

/** 载入今日推测（没有就静默跳过，不打扰用户） */
async function loadTodaySuggestion() {
  try {
    const res = await getTodayPrediction()
    if (res?.code === 200 && res.data?.hasSuggestion) suggestion.value = res.data
  } catch { /* 未开启 / 未登录：静默降级为纯手动录入 */ }
}

// 从「推测历史  去处理」跳过来：记录那条推测的归属日期（可能不是今天）
const predFromHistory = ref('')

/**
 * 承接推测历史页的「去处理」跳转，把那条待确认推测直接显示成卡片。
 *
 * 不做这件事的话，用户点完「去处理」到写日记页却只看到「帮我猜一下」按钮，
 * 会以为跳转失败了。参数用 pred* 前缀，避开上面既有的 ?emotion= 预选逻辑 ——
 * 情绪必须由用户点「采用并预填」来确认，这样保存才会走 confirm 接口把推测状态改成「已采纳」。
 */
function applyPredictionFromQuery() {
  const q = route.query
  if (!q?.predId) return
  const conf = Number(q.predConf)
  predFromHistory.value = q.predDate || ''
  suggestion.value = {
    predictionId: q.predId,
    emotion: Number(q.predEmotion) || 1,
    emotionName: q.predName || '',
    confidence: Number.isFinite(conf) ? conf : 0,
    reason: q.predDate ? `${q.predDate} 的系统推测，等你确认` : '系统推测，等你确认',
  }
}

/** 本地日期 YYYY-MM-DD（保存与提示共用，避免多处各写一遍） */
function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 「帮我猜一下」：融合行为 + 文本 + 时序三通路 */
async function guessFromText() {
  guessing.value = true
  try {
    const res = await generatePrediction(form.value.content?.trim() || '')
    if (res?.code === 200 && res.data?.hasSuggestion) {
      suggestion.value = res.data
      scanResult.value = ''
    } else if (res?.code !== 200) {
      // 业务失败（如 403 未开启无感记录）：拦截器不 reject，data 为空，
      // 必须显式透出后端 msg，不能落进「给不出推测」的兜底文案误导用户
      ElMessage.warning({ message: res?.msg || '无感记录未开启，可到「个人中心  无感记录」开启', grouping: true })
    } else {
      ElMessage.info({ message: res?.data?.reason || '暂时给不出推测', grouping: true })
    }
  } catch (e) {
    // 功能未开启（403）：明确告诉用户，而不是默默失败
    ElMessage.warning({ message: e?.response?.data?.msg || '无感推测暂不可用', grouping: true })
  } finally {
    guessing.value = false
  }
}

/** 「从文字识别情绪」：只用词典做即时分析（原文不落库） */
async function scanText() {
  const text = form.value.content?.trim()
  if (!text) { ElMessage.info('先写点什么再识别吧'); return }
  scanning.value = true
  try {
    const res = await analyzeText(text)
    const d = res?.data
    if (res?.code === 200 && d?.hit) {
      form.value.emotion = d.emotion
      form.value.intensity = d.intensity || 2
      scanResult.value = `识别到「${d.emotionName}」${d.hitWords?.length ? '（命中：' + d.hitWords.join('、') + '）' : ''}`
      scanResult.value += ' —— 已填入下方，可自行修改。'
    } else {
      scanResult.value = d?.reason || '没有匹配到情绪词，你可以自己选一个。'
    }
  } catch (e) {
    ElMessage.warning(e?.response?.data?.msg || '文本识别暂不可用')
  } finally {
    scanning.value = false
  }
}

/** 采用推测  预填充表单（此时仍不入库，等用户点保存） */
function adoptSuggestion() {
  if (!suggestion.value) return
  form.value.emotion = suggestion.value.emotion
  form.value.intensity = suggestion.value.intensity || 2
  adoptedMode.value = true
}

/** 忽略：告诉后端这条推测不要了 */
async function ignoreSuggestion() {
  const id = suggestion.value?.predictionId
  suggestion.value = null
  adoptedMode.value = false
  if (!id) return
  try { await rejectPrediction(id) } catch { /* 忽略失败不影响用户继续写 */ }
}

/** 用户手动点了情绪/强度  视为「我自己决定的」，取消推测模式 */
function pickEmotion(val) { form.value.emotion = val; adoptedMode.value = false }
function pickIntensity(val) { form.value.intensity = val; adoptedMode.value = false }

onMounted(async () => {
  await loadSenseReady()
  await loadTodaySuggestion()
  // 用户是从推测历史点「去处理」来的：那条推测优先显示（覆盖今日推测）
  applyPredictionFromQuery()
})

// 保存日记
const contentRef = ref(null)

/** 必填项检查：返回「还差什么」清单。以后新增必填字段只需要往这里加一条 */
function missingFields() {
  const missing = []
  if (!form.value.content.trim()) missing.push('文字记录（标了「必填」的那一栏）')
  return missing
}

const saveDiary = async () => {
  // 点了保存但还有必填项没填：明确告诉用户差什么，并把光标放到第一个缺口上
  const missing = missingFields()
  if (missing.length) {
    ElMessage.warning({
      message: '还差这些才能保存：' + missing.join('、'),
      grouping: true,
    })
    // 把光标带到第一个缺口：Element Plus 的 textarea 组件实例直接暴露 focus()
    const box = contentRef.value
    if (box) {
      if (typeof box.focus === 'function') box.focus()
      else box.$el?.focus?.()
      box.$el?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
    }
    return
  }
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { ElMessage.warning('请先登录'); router.push('/login'); return }

  const diaryDate = todayStr()
  const sleepHours = normalizedSleep()
  if (form.value.sleepHours !== null && form.value.sleepHours !== '' && sleepHours === null) {
    ElMessage.warning('睡眠时长请填 0 ~ 24 之间的数字（可以带 .5）')
    return
  }

  saving.value = true
  try {
    // 采用推测且未手动改动  走 confirm 接口，落一条 source=2 的日记（图表上标为「系统推测」）
    if (adoptedMode.value && suggestion.value?.predictionId) {
      const res = await confirmPrediction(suggestion.value.predictionId, {
        emotion: form.value.emotion,
        intensity: form.value.intensity || null,
        content: form.value.content,
        sleepHours,
        diaryDate,
      })
      if (res?.code === 200) {
        ElMessage.success('已记录（来自系统推测，你确认后的版本）')
        router.push('/diary')
        return
      }
      ElMessage.error('' + (res?.msg || '保存失败'))
      return
    }

    const res = await apiSaveDiary({
      userId: info.userId,
      diaryContent: form.value.content,
      emotion: form.value.emotion,
      intensity: form.value.intensity || null,
      diaryDate,
      isPublic: 0, // 全部日记恒定私密（原公开/私密开关已移除，项目无公开场景）
      imageUrl: form.value.imageUrl || null,
      sleepHours,
    })
    if (res.code === 200) {
      ElMessage.success('日记已记录！')
      await router.push('/diary')
      // 危机通道：后端保存时已扫描正文，命中危机词就生成了提示 
      // 跳转完成后让全局关怀弹窗立刻拉一次（未命中时它什么也不做）。
      // 必须在跳转后派发：弹窗监听器挂在常驻的 AppLayout 上，跳转中派发可能被路由切换吞掉。
      window.dispatchEvent(new CustomEvent('risk-crisis-check'))
    } else {
      ElMessage.error('' + (res.msg || '保存失败'))
    }
  } catch {
    ElMessage.error('网络错误，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.write-page {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(135deg, #fef9e7 0%, #fde4ec 50%, #e4f0fb 100%);
  padding: 0 32px 60px;   /* 顶部横幅贴顶，不留白边 */
  width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;
}
/* 柔和装饰光斑 */
.write-page::before, .write-page::after {
  content: ''; position: fixed; border-radius: 50%;
  filter: blur(70px); pointer-events: none; z-index: 0;
}
.write-page::before { width: 380px; height: 380px; left: -120px; top: -100px; background: rgba(184, 212, 154, 0.35); }
.write-page::after { width: 420px; height: 420px; right: -140px; bottom: -120px; background: rgba(248, 201, 216, 0.30); }

.write-header {
  position: relative; z-index: 1;
  display: flex; align-items: center;
  gap: 16px;
  padding: 16px 22px;
  margin: 0 auto 22px;
  max-width: 820px;
  border-radius: 20px;
  background: linear-gradient(120deg, rgba(255,255,255,0.85), rgba(255,255,255,0.6));
  box-shadow: 0 6px 22px rgba(140, 194, 105, 0.14);
  backdrop-filter: blur(10px);
  box-sizing: border-box;
}
.back-btn {
  background: rgba(255,255,255,0.9); border: none;
  padding: 8px 16px; border-radius: 999px;
  font-size: 14px; color: #555; cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: all 0.2s; flex-shrink: 0;
}
.back-btn:hover { background: #fff; transform: translateX(-2px); }

.write-head-center { flex: 1; text-align: center; min-width: 0; }
.write-title {
  font-size: 22px; font-weight: 700; color: var(--c-text);
  letter-spacing: 3px; margin: 0;
  display: flex; align-items: center; justify-content: center; gap: 8px;
}
.title-icon { width: 34px; height: 34px; object-fit: contain; }
.write-head-sub { margin: 4px 0 0; font-size: 12px; color: var(--c-text-weak); letter-spacing: 2px; }
/* 日期现在位于动漫风渐变横幅右侧，改白色保证对比度 */
.write-today { font-size: 13px; color: rgba(255,255,255,0.95); flex-shrink: 0; letter-spacing: 1px; }

.write-card {
  position: relative; z-index: 1;
  background: rgba(255,255,255,0.82);
  border-radius: 22px;
  padding: 32px 40px;
  max-width: 820px;
  margin: 0 auto;
  box-sizing: border-box;
  box-shadow:
    0 4px 12px rgba(0,0,0,0.05),
    0 20px 40px color-mix(in srgb, var(--c-main) 12%, transparent);
  backdrop-filter: blur(10px);
}

/* ===== 轻量无感推测区 ===== */
.sf-sense {
  margin-bottom: 24px;
  padding: 16px 18px;  border-radius: 16px;
  background: linear-gradient(135deg, rgba(240, 249, 255, 0.9) 0%, rgba(245, 240, 255, 0.9) 100%);
  border: 1.5px dashed #a5c8e8;
  box-sizing: border-box;
}
/* 未开启时的一行引导条：紧凑、可点、不喧宾夺主 */
.sf-sense.sense-guide {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  flex-wrap: wrap;
}
.sense-guide .sg-text { flex: 1; min-width: 220px; font-size: 12.5px; line-height: 1.7; color: #7a8ba0; }
.sense-guide .sg-btn {
  flex: 0 0 auto;
  padding: 7px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #a5c8e8, #8ab6dd);
  color: #fff;
  font-size: 13px;
  text-decoration: none;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.sense-guide .sg-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 14px -6px rgba(138, 182, 221, 0.8); }
.sense-head { display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.sense-titles { flex: 1; min-width: 0; }
.sense-titles b { font-size: 15px; color: #3d5a80; letter-spacing: 1px; display: block; }
.sense-titles span { font-size: 12px; color: var(--c-text-weak); display: block; margin-top: 3px; }
.sense-badge {
  font-size: 11px; padding: 1px 7px; border-radius: 999px;
  background: #a5c8e8; color: #fff; margin-left: 6px; font-weight: 500;
}
.sense-body { margin-top: 12px; }
.sense-main { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.sense-emo-badge {
  padding: 7px 18px; border-radius: 999px; color: #fff;
  font-size: 15px; font-weight: 700; letter-spacing: 1px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.sense-meta { flex: 1; min-width: 0; }
.sense-conf { display: block; font-size: 12px; color: #5a7fa0; font-weight: 600; }
.sense-reason { display: block; font-size: 12px; color: var(--c-text-weak); margin-top: 2px; line-height: 1.6; }
.sense-cands { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.sense-cand {
  font-size: 11.5px; padding: 3px 10px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.85); color: #6b8aa8; border: 1px solid rgba(165, 200, 232, 0.6);
}
.sense-actions { display: flex; gap: 10px; margin-top: 14px; flex-wrap: wrap; }
.sense-actions--entry { margin-top: 4px; }
.sense-adopt, .sense-guess {
  padding: 9px 18px; border-radius: 999px; border: none;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff;
  font-size: 13.5px; font-weight: 600; cursor: pointer;
  box-shadow: 0 4px 12px rgba(140, 194, 105, 0.3); transition: all 0.25s;
}
.sense-adopt:hover, .sense-guess:hover:not(:disabled) { transform: translateY(-2px); }
.sense-ignore, .sense-textscan {
  padding: 9px 18px; border-radius: 999px;
  border: 1.5px solid var(--c-border); background: rgba(255, 255, 255, 0.9);
  color: var(--c-text-weak); font-size: 13.5px; cursor: pointer; transition: all 0.25s;
}
.sense-ignore:hover, .sense-textscan:hover:not(:disabled) { background: #fff; border-color: var(--c-hex); }
.sense-guess:disabled, .sense-textscan:disabled { opacity: 0.55; cursor: not-allowed; }
.sense-applied {
  margin: 10px 0 0; font-size: 12px; color: var(--c-text); line-height: 1.6;
  padding: 8px 12px; border-radius: 10px; background: rgba(184, 212, 154, 0.18);
}
.sense-scan-result {
  margin: 10px 0 0; font-size: 12.5px; color: #5a7fa0; line-height: 1.6;
}
.sense-note {
  margin: 10px 0 0; font-size: 12px; color: #8a5b12; line-height: 1.6;
  padding: 8px 12px; border-radius: 10px; background: rgba(240, 190, 130, 0.16);
}

/* ===== 表单样式（与 Diary.vue 保持一致）===== */
.sf-block { margin-bottom: 22px; position: relative; }
.sf-label { font-size: 15px; color: #444; margin-bottom: 12px; letter-spacing: 1px; display: block; }
.sf-req { color: #999; font-size: 13px; margin-left: 2px; }
.sf-opt { color: #bbb; font-size: 13px; margin-left: 2px; }

.sf-emo-row { display: flex; flex-wrap: wrap; gap: 16px; }
.sf-emo-btn {
  padding: 14px 28px; border-radius: 999px; border: 2px solid var(--c-border);
  background: #fff; cursor: pointer; transition: all 0.25s;
  font-size: 16px; color: #555; display: inline-flex; align-items: center; gap: 8px;
  font-weight: 500;
  flex-shrink: 0;
}
.sf-emo-btn:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(0,0,0,0.12); }
.sf-emo-btn.active { box-shadow: 0 4px 16px rgba(0,0,0,0.14); font-weight: 700; color: #fff; }
.emo-btn-icon { width: 30px; height: 30px; object-fit: contain; }

/* 情绪强度 */
.sf-intensity-row { display: flex; flex-wrap: wrap; gap: 14px; }
.sf-int-btn {
  flex: 1;
  min-width: 140px;
  padding: 12px 18px;
  border-radius: 16px;
  border: 2px solid var(--c-border);
  background: #fff;
  cursor: pointer;
  transition: all 0.25s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.sf-int-btn b { font-size: 15px; letter-spacing: 1px; }
.sf-int-btn i { font-style: normal; font-size: 11.5px; color: var(--c-text-weak); }
.sf-int-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,0.1); }
.sf-int-btn.active { box-shadow: 0 4px 14px rgba(0,0,0,0.12); font-weight: 700; }
.sf-int-btn.active i { color: rgba(255,255,255,0.85); }

/* 昨晚睡眠 */
.sf-sleep-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.sf-sleep-chip {
  padding: 9px 18px; border-radius: 999px;
  border: 2px solid var(--c-border); background: #fff;
  font-size: 14px; color: var(--c-text); cursor: pointer;
  transition: all 0.22s; flex-shrink: 0;
}
.sf-sleep-chip:hover { transform: translateY(-2px); border-color: #b8d49a; box-shadow: 0 6px 14px color-mix(in srgb, var(--c-main) 16%, transparent); }
.sf-sleep-chip.active {
  background: var(--c-hex); border-color: var(--c-hex); color: #fff; font-weight: 700;
  box-shadow: 0 4px 14px color-mix(in srgb, var(--c-main) 32%, transparent);
}
.sf-sleep-custom {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; border-radius: 999px;
  border: 2px solid var(--c-border); background: #fff;
  transition: all 0.22s;
}
.sf-sleep-custom:focus-within { border-color: var(--c-hex); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 12%, transparent); }
.sf-sleep-custom.active { border-color: var(--c-hex); background: color-mix(in srgb, var(--c-main) 8%, transparent); }
.sf-sleep-input {
  width: 62px; border: none; outline: none; background: transparent;
  font-size: 14px; color: #444; font-family: inherit; text-align: center;
}
.sf-sleep-input::-webkit-outer-spin-button,
.sf-sleep-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.sf-sleep-input[type=number] { -moz-appearance: textfield; appearance: textfield; }
.sf-sleep-input::placeholder { color: #bbb; }
.sf-sleep-unit { font-size: 12.5px; color: var(--c-text-weak); }
.sf-sleep-clear {
  border: none; background: none; color: #b5a37a;
  font-size: 12.5px; cursor: pointer; padding: 4px 2px;
  text-decoration: underline; text-underline-offset: 2px;
}
.sf-sleep-clear:hover { color: #d98e5a; }
.sf-sleep-tip { margin: 8px 0 0; font-size: 11.5px; color: var(--c-text-weak); line-height: 1.6; }

.sf-textarea {
  width: 100%; box-sizing: border-box;
  padding: 20px 22px 40px; border-radius: 14px;
  border: 1.5px solid #eee; background: #fff;
  font-size: 16px; font-family: inherit; line-height: 1.8;
  resize: vertical; color: #333; transition: all 0.2s;
  min-height: 180px;
}
.sf-textarea:focus { outline: none; border-color: var(--c-hex); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-main) 12%, transparent); }
.sf-textarea::placeholder { color: #bbb; }
.sf-counter { position: absolute; right: 16px; bottom: 10px; font-size: 11px; color: #bbb; }

/* 图片 */
.sf-img-placeholder {
  text-align: center; padding: 34px 0 30px;
  border: 2px dashed var(--c-border); border-radius: 16px;
  color: var(--c-text-weak); font-size: 14px;
  background: rgba(255,255,255,0.6);
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  cursor: pointer; transition: all 0.25s;
  user-select: none;
}
.sf-ph-icon { font-size: 30px; line-height: 1; }
.sf-ph-text { font-size: 15px; font-weight: 600; color: var(--c-text-weak); letter-spacing: 1px; }
.sf-ph-sub { font-size: 11.5px; color: var(--c-text-weak); }
.sf-img-placeholder:hover { border-color: var(--c-hex); color: var(--c-hex); background: color-mix(in srgb, var(--c-main) 8%, transparent); transform: translateY(-1px); }
.sf-img-preview {
  position: relative; width: 100%;
  border-radius: 16px; overflow: hidden;
  border: 1px solid #eee;
  background:
    linear-gradient(45deg, color-mix(in srgb, var(--c-main) 6%, #fff) 25%, transparent 25%, transparent 75%, color-mix(in srgb, var(--c-main) 6%, #fff) 75%),
    linear-gradient(45deg, color-mix(in srgb, var(--c-main) 6%, #fff) 25%, transparent 25%, transparent 75%, color-mix(in srgb, var(--c-main) 6%, #fff) 75%);
  background-size: 18px 18px;
  background-position: 0 0, 9px 9px;
  background-color: #fff;
}
/* 完整显示整张图：不裁切、等比缩放、居中 */
.sf-img-preview img {
  display: block; max-width: 100%; max-height: 320px;
  width: auto; height: auto; margin: 0 auto;
  object-fit: contain;
}
.sf-img-remove {
  position: absolute; top: 8px; right: 8px;
  width: 28px; height: 28px; border-radius: 50%;
  border: none; background: rgba(0,0,0,0.55);
  color: #fff; font-size: 18px; line-height: 1;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.sf-img-remove:hover { background: #ff5b6a; transform: scale(1.1); }

/* 保存按钮 */
.sf-btn-row { display: flex; flex-direction: column; gap: 12px; }

.sf-save-btn {
  width: 100%; padding: 16px; border-radius: 14px;
  border: none; background: linear-gradient(135deg, var(--c-hex) 0%, #6eb464 100%);
  color: #fff; font-size: 16px; font-weight: 600; letter-spacing: 3px;
  cursor: pointer; transition: all 0.3s;
  box-shadow: 0 6px 20px color-mix(in srgb, var(--c-main) 35%, transparent);
}
.sf-save-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 10px 26px color-mix(in srgb, var(--c-main) 45%, transparent); }
.sf-save-btn:disabled { background: #ccc; box-shadow: none; cursor: not-allowed; opacity: 0.8; }

.sf-ai-btn {
  width: 100%; padding: 14px; border-radius: 14px;
  border: 2px solid #f8c9d8;
  background: linear-gradient(135deg, rgba(253, 231, 238, 0.8) 0%, rgba(205, 232, 248, 0.8) 100%);
  color: #e879a0; font-size: 15px; font-weight: 600;
  cursor: pointer; transition: all 0.3s;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  box-shadow: 0 4px 14px rgba(248, 201, 216, 0.2);
}
.sf-ai-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(248, 201, 216, 0.35);
  background: linear-gradient(135deg, #fde7ee 0%, #cde8f8 100%);
}
.sf-ai-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* ===== 移动端适配 ===== */
@media (max-width: 700px) {
  .write-page { padding: 0 14px 50px; }
  .write-header { flex-wrap: wrap; padding: 14px 16px; gap: 10px; }
  .write-head-center { order: -1; width: 100%; }
  .write-card { padding: 24px 18px; }
  .sf-emo-row { gap: 10px; }
  .sf-emo-btn { padding: 10px 16px; font-size: 14px; }
  .emo-btn-icon { width: 24px; height: 24px; }
  .sf-sense { padding: 13px 14px; }
  /* 手机上按钮撑满一行，方便拇指点按 */
  .sense-actions > button { flex: 1 1 100%; min-height: 44px; }
  .sense-main { gap: 8px; }
}
@media (max-width: 375px) {
  /* 超小屏：心情按钮改为两列，避免一行挤三个导致横向溢出 */
  .sf-emo-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
  .sf-emo-btn { padding: 10px 8px; justify-content: center; font-size: 13px; }
}
</style>