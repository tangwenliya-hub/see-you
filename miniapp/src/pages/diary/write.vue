<template>
  <view class="page">
    <view class="card">
      <text class="card-title">今天的心情</text>
      <view class="emo-grid">
        <view
          v-for="e in emotions"
          :key="e.val"
          class="emo-item"
          :class="{ active: form.emotion === e.val }"
          :style="form.emotion === e.val ? { background: e.soft, boxShadow: `0 0 0 4rpx ${e.ring}, 0 16rpx 28rpx -14rpx ${e.shadow}` } : {}"
          @click="form.emotion = e.val"
        >
          <image class="emo-icon" :src="e.icon" mode="aspectFit" />
          <text class="emo-label">{{ e.label }}</text>
        </view>
      </view>
    </view>

    <view class="card">
      <text class="card-title">强度</text>
      <view class="intensity-row">
        <view
          v-for="lv in intensity"
          :key="lv.val"
          class="intensity-item"
          :class="{ active: form.intensity === lv.val }"
          @click="form.intensity = lv.val"
        >{{ lv.label }}</view>
      </view>
    </view>

    <view class="card">
      <text class="card-title">写点什么</text>
      <textarea
        class="editor"
        v-model="form.content"
        placeholder="今天发生了什么？心里是什么感觉…"
        placeholder-class="ph"
        maxlength="2000"
      />
      <text class="counter">{{ form.content.length }} / 2000</text>
    </view>

    <view class="btn-primary save-btn" :class="{ disabled: saving || !form.content.trim() }" @click="save">
      <view v-if="!saving" class="save-icon li-icon li-ic-check"></view>
      <text>{{ saving ? '保存中…' : '保存日记' }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { saveDiary } from '../../api'
import { getUserInfo } from '../../utils/storage'
import { EMOTIONS as emotions, INTENSITY as intensity } from '../../utils/emotions'

const form = ref({ content: '', emotion: 1, intensity: 2 })
const saving = ref(false)

// 从主页心情快选跳过来时预选情绪与强度（与 H5 交互一致：emotion + intensity 双参数）
onLoad((query) => {
  const e = Number(query?.emotion)
  if (e >= 1 && e <= 9) form.value.emotion = e
  const i = Number(query?.intensity)
  if (i >= 1 && i <= 3) form.value.intensity = i
})

function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function save() {
  if (!form.value.content.trim()) {
    return uni.showToast({ title: '先写点什么吧', icon: 'none' })
  }
  const info = getUserInfo()
  if (!info) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    return uni.reLaunch({ url: '/pages/login/login' })
  }

  saving.value = true
  try {
    const res = await saveDiary({
      userId: info.userId || info.id,
      diaryContent: form.value.content.trim(),
      emotion: form.value.emotion,
      intensity: form.value.intensity || null,
      diaryDate: today(),
      isPublic: 0,          // 与主项目一致：日记恒定私密
      imageUrl: null,
    })
    if (res?.code === 200) {
      uni.showToast({ title: '已记录', icon: 'success' })
      setTimeout(() => uni.navigateBack({ delta: 1 }), 600)
    } else {
      uni.showToast({ title: res?.msg || '保存失败', icon: 'none' })
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page {
  padding: 24rpx 24rpx 60rpx;
}
.card-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #44604a;
  margin-bottom: 20rpx;
}
.emo-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.emo-item {
  width: calc((100% - 24rpx) / 3);
  border-radius: 28rpx;
  padding: 18rpx 0 14rpx;
  text-align: center;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-sizing: border-box;
}
.emo-item.active {
  background: #eaf6ec;
}
.emo-icon {
  display: block;
  width: 52rpx;
  height: 52rpx;
  margin: 0 auto 8rpx;
}
.emo-label {
  display: block;
  font-size: 23rpx;
  color: #8a9490;
}
.emo-item.active .emo-label {
  color: #4a5a50;
  font-weight: 600;
}
.intensity-row {
  display: flex;
  gap: 12rpx;
}
.intensity-item {
  flex: 1;
  text-align: center;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 22rpx;
  padding: 20rpx 0;
  font-size: 26rpx;
  color: #8a9490;
  box-sizing: border-box;
}
.intensity-item.active {
  background: #5aa96c;
  border-color: #5aa96c;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 12rpx 24rpx -12rpx rgba(90, 169, 108, 0.6);
}
.editor {
  width: 100%;
  height: 320rpx;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 22rpx;
  padding: 20rpx;
  font-size: 28rpx;
  line-height: 1.7;
  box-sizing: border-box;
}
.ph {
  color: #b5c4b5;
}
.counter {
  display: block;
  text-align: right;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #b5c4b5;
}
.disabled {
  opacity: 0.5;
}
.save-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
}
.save-icon {
  width: 30rpx;
  height: 30rpx;
}
</style>
