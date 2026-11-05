<template>
  <view class="page">
    <view v-if="list.length === 0 && loaded" class="empty">
      <text class="empty-emoji">📔</text>
      <text class="empty-text">还没有日记，写第一篇吧</text>
      <view class="btn-primary empty-btn" @click="goWrite">写日记</view>
    </view>

    <view
      v-for="d in list"
      :key="d.diaryId"
      class="card diary-card"
      @click="openDetail(d)"
    >
      <view class="diary-head">
        <image class="diary-emoji" :src="emotionOf(d.emotion).icon" mode="aspectFit" />
        <text class="diary-emotion">{{ emotionOf(d.emotion).label }}</text>
        <text class="diary-date">{{ d.diaryDate }}</text>
      </view>
      <text class="diary-content">{{ d.diaryContent }}</text>
      <text v-if="d.source === 2" class="diary-tag">系统推测 · 已确认</text>
    </view>

    <view class="fab" @click="goWrite">＋</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { getDiaryList } from '../../api'
import { ensureLogin } from '../../utils/storage'
import { emotionInfo } from '../../utils/emotions'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
// 首次加载完成前不渲染空态，避免一进页面闪一下「还没有日记」
const loaded = ref(false)

const emotionOf = (v) => emotionInfo(v)

async function load(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; hasMore.value = true }
  if (!hasMore.value) return

  loading.value = true
  try {
    const res = await getDiaryList({ page: page.value, size: 20 })
    if (res?.code === 200 && res.data) {
      const records = res.data.records || []
      list.value = reset ? records : list.value.concat(records)
      hasMore.value = records.length >= 20
      if (hasMore.value) page.value += 1
    } else {
      uni.showToast({ title: res?.msg || '加载失败', icon: 'none' })
    }
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function goWrite() {
  uni.navigateTo({ url: '/pages/diary/write' })
}

function openDetail(d) {
  uni.showActionSheet({
    itemList: ['查看完整内容'],
    success: () => {
      uni.showModal({
        title: `${d.diaryDate} · ${emotionOf(d.emotion).label}`,
        content: d.diaryContent,
        showCancel: false,
      })
    },
  })
}

onShow(() => { if (ensureLogin()) load(true) })
onPullDownRefresh(async () => { await load(true); uni.stopPullDownRefresh() })
onReachBottom(() => load(false))
</script>

<style scoped>
.page {
  padding: 24rpx 24rpx 140rpx;
}
.empty {
  padding: 160rpx 0;
  text-align: center;
}
.empty-emoji {
  display: block;
  font-size: 90rpx;
}
.empty-text {
  display: block;
  margin-top: 16rpx;
  color: #8a9a8a;
  font-size: 28rpx;
}
.empty-btn {
  width: 320rpx;
  margin: 40rpx auto 0;
}
.diary-head {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}
.diary-emoji {
  width: 44rpx;
  height: 44rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
}
.diary-emotion {
  font-size: 28rpx;
  font-weight: 600;
}
.diary-date {
  margin-left: auto;
  font-size: 24rpx;
  color: #8a9a8a;
}
.diary-content {
  display: block;
  font-size: 28rpx;
  line-height: 1.7;
  color: #3f4f3f;
}
.diary-tag {
  display: inline-block;
  margin-top: 14rpx;
  padding: 4rpx 14rpx;
  background: #eef7ee;
  color: #5a8a5a;
  border-radius: 999rpx;
  font-size: 22rpx;
}
.fab {
  position: fixed;
  right: 40rpx;
  bottom: 60rpx;
  width: 100rpx;
  height: 100rpx;
  line-height: 96rpx;
  text-align: center;
  border-radius: 50rpx;
  background: #3fae52;
  color: #ffffff;
  font-size: 56rpx;
  box-shadow: 0 8rpx 20rpx rgba(63, 174, 82, 0.35);
}
</style>
