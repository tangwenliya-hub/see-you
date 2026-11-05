<template>
  <view class="page">
    <view class="card user-card">
      <!-- 有头像就显示头像（sys_user.avatar 存的是 /user/xxx.jpg 相对路径，要拼后端地址），没有才用 emoji 兜底 -->
      <image v-if="avatarUrl" class="avatar-img" :src="avatarUrl" mode="aspectFill" />
      <text v-else class="avatar">🐮</text>
      <view class="user-meta">
        <text class="nickname">{{ nickname }}</text>
        <text class="muted">已记录 {{ diaryTotal }} 篇日记 · 共 {{ overview.totalDays || 0 }} 天</text>
      </view>
    </view>

    <view class="card">
      <text class="card-title">情绪分布</text>
      <view v-for="d in distribution" :key="d.emotion" class="dist-row">
        <text class="dist-label">{{ d.name }}</text>
        <view class="dist-bar-wrap">
          <view class="dist-bar" :style="{ width: d.percent + '%', background: d.bg }" />
        </view>
        <text class="dist-num">{{ d.count }}</text>
      </view>
      <text v-if="distribution.length === 0" class="muted">还没有足够数据</text>
    </view>

    <view class="card">
      <text class="card-title">连续记录</text>
      <text class="big-num">{{ overview.currentStreak ?? 0 }} 天</text>
      <text class="muted streak-sub">最长连续 {{ overview.longestStreak || 0 }} 天 · 积极占比 {{ overview.positiveRatio || 0 }}%</text>
    </view>

    <!-- 非医疗免责声明：与主项目一致，必须保留 -->
    <view class="card disclaimer">
      <text class="disclaimer-text">{{ disclaimer }}</text>
    </view>

    <view class="btn-ghost logout-btn" @click="doLogout">退出登录</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMyOverview, getMyEmotion, getDiaryList } from '../../api'
import { getUserInfo, clearSession, ensureLogin } from '../../utils/storage'
import { EMOTIONS, emotionInfo } from '../../utils/emotions'
import { BASE_URL } from '../../config'

const DISCLAIMER = '本提示仅基于你自行记录的情绪数据做统计参考，不属于医疗诊断，不能替代专业评估。'

const overview = ref({})
const distribution = ref([])
const diaryTotal = ref(0)
const disclaimer = DISCLAIMER

const nickname = computed(() => {
  const u = getUserInfo()
  return u?.nickname || u?.username || '朋友'
})
// 头像 URL：H5 的静态图由 Vite 同源提供（public 目录），小程序没有 Vite，
// 统一走后端 /upload/** 静态映射（WebConfig 已把前端 public 目录挂到该前缀下，且 JWT 已放行）。
// sys_user.avatar 存的是 /user/xxx.jpg 这类相对路径，转成 /upload/user/xxx.jpg 即可命中。
const avatarUrl = computed(() => {
  const a = getUserInfo()?.avatar
  if (!a) return ''
  if (a.startsWith('http')) return a
  return BASE_URL + '/upload' + a
})

async function load() {
  const [ov, em, dl] = await Promise.all([getMyOverview(), getMyEmotion(), getDiaryList({ page: 1, size: 1 })])
  if (ov?.code === 200) overview.value = ov.data || {}
  if (dl?.code === 200) diaryTotal.value = dl.data?.total || 0

  // /statistics/my/emotion 返回 { total, values, pie:[{name,value}], labels }，
  // pie 的顺序与情绪字典 1~9 一致，用 label 反查情绪色
  if (em?.code === 200 && Array.isArray(em.data?.pie)) {
    const pie = em.data.pie
    const total = pie.reduce((s, x) => s + (x.value || 0), 0) || 1
    distribution.value = pie.map((x, i) => {
      const info = EMOTIONS.find((e) => e.label === x.name) || EMOTIONS[i] || emotionInfo(i + 1)
      return {
        emotion: info.val,
        name: x.name || info.label,
        count: x.value ?? 0,
        percent: Math.round(((x.value || 0) / total) * 100),
        bg: info.bg,
      }
    })
  }
}

function doLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出吗？',
    success: (r) => {
      if (!r.confirm) return
      clearSession()
      uni.reLaunch({ url: '/pages/login/login' })
    },
  })
}

onShow(() => { if (ensureLogin()) load() })
</script>

<style scoped>
.page {
  padding: 24rpx 24rpx 60rpx;
}
.user-card {
  display: flex;
  align-items: center;
}
.avatar {
  font-size: 80rpx;
  margin-right: 24rpx;
}
.avatar-img {
  width: 110rpx;
  height: 110rpx;
  margin-right: 24rpx;
  flex-shrink: 0;
  border-radius: 50%;
  border: 2rpx solid rgba(255, 255, 255, 0.9);
  background: #eef7ee;
}
.nickname {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
}
.user-meta {
  flex: 1;
}
.card-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
}
.dist-row {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}
.dist-label {
  width: 100rpx;
  font-size: 26rpx;
}
.dist-bar-wrap {
  flex: 1;
  height: 20rpx;
  background: #f0f5f0;
  border-radius: 10rpx;
  overflow: hidden;
}
.dist-bar {
  height: 20rpx;
  border-radius: 10rpx;
}
.dist-num {
  width: 70rpx;
  text-align: right;
  font-size: 24rpx;
  color: #8a9a8a;
}
.big-num {
  display: block;
  font-size: 56rpx;
  font-weight: 700;
  color: #3fae52;
}
.streak-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
}
.disclaimer {
  background: #fff8e6;
}
.disclaimer-text {
  font-size: 24rpx;
  color: #a08a5a;
  line-height: 1.7;
}
.logout-btn {
  margin-top: 40rpx;
}
</style>
