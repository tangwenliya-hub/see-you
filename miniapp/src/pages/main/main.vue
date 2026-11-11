<template>
  <view class="page">
    <!-- 问候 -->
    <view class="hero">
      <text class="hello">你好，{{ nickname }}</text>
      <text class="sub">今天想记录点什么？</text>
    </view>

    <!-- 今日心情快选（对齐 H5：白卡 + 贴纸图 + 点过的格子显情绪色；点情绪先弹强度选择） -->
    <view class="card mood-card">
      <text class="mood-label">今天感觉怎么样</text>
      <view class="mood-row">
        <view
          v-for="e in emotions"
          :key="e.val"
          class="mood-btn"
          :class="{ done: todayEmotion === e.val }"
          :style="moodStyle(e)"
          @click="pickMood(e)"
        >
          <image class="mood-icon" :src="e.icon" mode="aspectFit" />
          <text class="mood-text">{{ e.label }}</text>
        </view>
      </view>
      <text class="mood-state">{{ todayMoodText }}</text>

      <!-- 情绪强度选择：点情绪后弹出，选完带情绪+强度去写日记（与 H5 交互一致） -->
      <view v-if="pickTarget" class="intensity-pick">
        <text class="ip-label">这份「{{ pickTarget.label }}」有多强烈？</text>
        <view class="ip-row">
          <view
            v-for="lv in intensityLevels"
            :key="lv.val"
            class="ip-btn"
            @click="goWriteWith(pickTarget, lv.val)"
          >
            <text class="ip-name">{{ lv.label }}</text>
            <text class="ip-desc">{{ lv.desc }}</text>
          </view>
          <view class="ip-cancel" @click="pickTarget = null">✕</view>
        </view>
      </view>
    </view>

    <!-- 数据概览 -->
    <view class="card">
      <text class="card-title">我的记录</text>
      <view class="stat-row">
        <view class="stat">
          <text class="stat-num">{{ diaryTotal }}</text>
          <text class="stat-label">日记</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ overview.currentStreak ?? 0 }}</text>
          <text class="stat-label">连续天数</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ overview.avgIndex ?? '--' }}</text>
          <text class="stat-label">平均指数</text>
        </view>
      </view>
    </view>

    <!-- 情绪关怀提醒（非医疗诊断，声明必须保留） -->
    <view v-if="latestAlert" class="card alert-card">
      <view class="alert-title">
        <view class="alert-icon li-icon li-ic-heart"></view>
        <text>绪见的关心</text>
      </view>
      <text class="alert-text">{{ latestAlert.triggerDetail || latestAlert.advice }}</text>
      <text class="alert-desc">{{ latestAlert.disclaimer || disclaimer }}</text>
    </view>

    <!-- 快捷入口（对齐 H5：圆角贴纸图标 + 小字，竖排） -->
    <view class="card quick-card">
      <text class="card-title">快捷入口</text>
      <view class="entry-row">
        <view class="entry-item" @click="go('/pages/diary/write')">
          <view class="entry-icon"><image class="entry-img" src="/static/emo/q-write.png" mode="aspectFit" /></view>
          <text class="entry-text">写日记</text>
        </view>
        <view class="entry-item" @click="go('/pages/diary/diary')">
          <view class="entry-icon"><image class="entry-img" src="/static/emo/q-diary.png" mode="aspectFit" /></view>
          <text class="entry-text">看日记</text>
        </view>
        <view class="entry-item" @click="go('/pages/assistant/assistant')">
          <view class="entry-icon"><image class="entry-img" src="/static/emo/q-ai.png" mode="aspectFit" /></view>
          <text class="entry-text">AI 陪伴</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getMyOverview, getRiskLatest, getDiaryList } from '../../api'
import { getUserInfo, ensureLogin } from '../../utils/storage'
import { EMOTIONS as emotions, emotionInfo } from '../../utils/emotions'

const DISCLAIMER = '本提示仅基于你自行记录的情绪数据做统计参考，不属于医疗诊断，不能替代专业评估。'

const overview = ref({})
const latestAlert = ref(null)
const todayDiary = ref(null)
const pickTarget = ref(null)
const diaryTotal = ref(0)
const disclaimer = DISCLAIMER

const nickname = computed(() => {
  const u = getUserInfo()
  return u?.nickname || u?.username || '朋友'
})

// 今天已打卡的情绪（对齐 H5 的 done 高亮）
const todayEmotion = computed(() => todayDiary.value?.emotion ?? null)
const todayMoodText = computed(() => {
  if (todayDiary.value?.emotion) return `今天的心情是「${emotionInfo(todayDiary.value.emotion).label}」`
  return '点一个表情，记下此刻的心情吧'
})

// 强度选择：与 H5 主页一致的三档
const intensityLevels = [
  { val: 1, label: '轻度', desc: '有一点点' },
  { val: 2, label: '中度', desc: '比较明显' },
  { val: 3, label: '重度', desc: '非常强烈' },
]

// 已打卡态的底色/描边/投影来自情绪字典的预计算色（WXSS 不支持 color-mix）
function moodStyle(e) {
  if (todayEmotion.value !== e.val) return {}
  return {
    background: e.soft,
    boxShadow: `0 0 0 4rpx ${e.ring}, 0 16rpx 28rpx -14rpx ${e.shadow}`,
  }
}

function pickMood(e) {
  // 再点同一个情绪 = 收起强度选择（与 H5 一致）
  pickTarget.value = pickTarget.value?.val === e.val ? null : e
}

function goWriteWith(e, level) {
  pickTarget.value = null
  uni.navigateTo({ url: `/pages/diary/write?emotion=${e.val}&intensity=${level}` })
}

function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function load() {
  const [ov, risk, dl] = await Promise.all([getMyOverview(), getRiskLatest(), getDiaryList({ page: 1, size: 20 })])
  if (ov?.code === 200) overview.value = ov.data || {}
  // 后端无预警时返回 { hasAlert:false }，只在 hasAlert 为 true 时显示关怀卡
  if (risk?.code === 200 && risk.data?.hasAlert) latestAlert.value = risk.data
  else latestAlert.value = null
  if (dl?.code === 200 && dl.data) {
    // 后端分页返回 total（真实日记总数），records 用于判断今天是否已打卡
    diaryTotal.value = dl.data.total || (dl.data.records || []).length
    const rec = (dl.data.records || []).find((d) => String(d.date || d.diaryDate || '').slice(0, 10) === todayStr())
    todayDiary.value = rec || null
  } else {
    diaryTotal.value = 0
    todayDiary.value = null
  }
}

// tabBar 页面必须用 switchTab，navigateTo 会静默失败（快捷入口里日记/AI 是 tab 页）
const TAB_PAGES = ['/pages/main/main', '/pages/diary/diary', '/pages/assistant/assistant', '/pages/profile/profile']
function go(url) {
  if (TAB_PAGES.includes(url)) uni.switchTab({ url })
  else uni.navigateTo({ url })
}

onShow(async () => {
  // 未登录/token 失效时先回登录页，不要先请求再跳（会闪一下空数据）
  if (!ensureLogin()) return
  await load()
})
onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page {
  padding: 24rpx;
}
.hero {
  padding: 32rpx 8rpx 24rpx;
}
.hello {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #3d5145;
}
.sub {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #8a9a8a;
}
.card-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #44604a;
  margin-bottom: 20rpx;
}

/* ===== 今日心情（对齐 H5 mood-row）===== */
.mood-label {
  display: block;
  font-size: 24rpx;
  color: #9aa39d;
  margin-bottom: 16rpx;
}
.mood-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.mood-btn {
  width: calc((100% - 24rpx) / 3);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 18rpx 8rpx 14rpx;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.62);
  box-sizing: border-box;
}
.mood-icon {
  width: 52rpx;
  height: 52rpx;
  transition: transform 0.3s ease;
}
.mood-btn:active .mood-icon {
  transform: scale(1.16) rotate(-4deg);
}
.mood-text {
  font-size: 23rpx;
  color: #8a9490;
}
.mood-btn.done .mood-text {
  color: #4a5a50;
  font-weight: 600;
}
.mood-state {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #a3ada6;
}

/* ===== 强度选择（对齐 H5 intensity-pick）===== */
.intensity-pick {
  margin-top: 20rpx;
  padding: 20rpx 24rpx;
  border-radius: 28rpx;
  background: #ebf8eb;
  border: 1px dashed rgba(123, 196, 123, 0.5);
  animation: rise-in 0.35s ease both;
}
@keyframes rise-in {
  from { opacity: 0; transform: translateY(12rpx); }
  to { opacity: 1; transform: translateY(0); }
}
.ip-label {
  display: block;
  font-size: 24rpx;
  color: #7d8a82;
  margin-bottom: 14rpx;
}
.ip-row {
  display: flex;
  gap: 12rpx;
  align-items: stretch;
}
.ip-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 14rpx 8rpx;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.85);
}
.ip-btn:active {
  transform: translateY(-4rpx);
  background: #fdf3e3;
}
.ip-name {
  font-size: 25rpx;
  font-weight: 600;
  color: #4a5a50;
}
.ip-desc {
  font-size: 20rpx;
  color: #a3ada6;
}
.ip-cancel {
  flex: 0 0 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22rpx;
  color: #a3ada6;
  font-size: 26rpx;
}

/* ===== 我的记录 ===== */
.stat-row {
  display: flex;
}
.stat {
  flex: 1;
  text-align: center;
}
.stat-num {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #5aa96c;
}
.stat-label {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #8a9a8a;
}

/* ===== 绪见的关心（非医疗免责声明必须保留）===== */
.alert-card {
  background: rgba(255, 248, 230, 0.92);
  border-color: rgba(230, 200, 140, 0.35);
}
.alert-title {
  display: flex;
  align-items: center;
  gap: 10rpx;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
  color: #6b5a2f;
}
.alert-icon {
  width: 30rpx;
  height: 30rpx;
}
.alert-text {
  display: block;
  font-size: 26rpx;
  line-height: 1.7;
  color: #5a5240;
}
.alert-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: #a08a5a;
}

/* ===== 快捷入口（对齐 H5 entry-row）===== */
.quick-card {
  padding-bottom: 12rpx;
}
.entry-row {
  display: flex;
  gap: 16rpx;
}
.entry-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.entry-icon {
  width: 96rpx;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 30rpx;
  background: linear-gradient(150deg, #ddf0dd, rgba(255, 255, 255, 0.9));
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 10rpx 20rpx -12rpx rgba(110, 150, 100, 0.5);
}
.entry-item:active .entry-icon {
  transform: translateY(-4rpx);
}
.entry-img {
  width: 52rpx;
  height: 52rpx;
}
.entry-text {
  font-size: 23rpx;
  color: #8a9490;
}
</style>
