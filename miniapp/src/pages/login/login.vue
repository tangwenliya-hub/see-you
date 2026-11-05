<template>
  <view class="login-page">
    <view class="brand">
      <text class="brand-name">绪见</text>
      <text class="brand-sub">记录每一天的情绪</text>
    </view>

    <view class="card">
      <input class="field" v-model="form.username" placeholder="用户名" placeholder-class="ph" />
      <input class="field" v-model="form.password" password placeholder="密码" placeholder-class="ph" />

      <view class="captcha-row">
        <input class="field captcha-input" v-model="form.captchaCode" placeholder="验证码" placeholder-class="ph" />
        <image
          v-if="captchaImage"
          class="captcha-img"
          :src="captchaImage"
          mode="aspectFit"
          @click="refreshCaptcha"
        />
        <view v-else class="captcha-fallback" @click="refreshCaptcha">点击加载</view>
      </view>

      <view class="btn-primary" @click="doLogin">
        {{ loading ? '登录中…' : '登录' }}
      </view>
      <view class="btn-ghost" @click="goRegister">还没有账号？去注册</view>
    </view>

    <view v-if="isDev" class="dev-tip">接口地址：{{ baseUrl }}</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCaptcha, login, register } from '../../api'
import { setToken, setUserInfo, getRemembered, setRemembered } from '../../utils/storage'
import { BASE_URL, IS_DEV } from '../../config'

const form = ref({ username: getRemembered(), password: '', captchaKey: '', captchaCode: '' })
const captchaImage = ref('')
const loading = ref(false)
const isDev = IS_DEV
const baseUrl = BASE_URL

/** 后端返回的可能是纯 base64，小程序 image 需要 data URI 前缀 */
function normalizeImage(src) {
  if (!src) return ''
  return src.startsWith('data:') ? src : `data:image/png;base64,${src}`
}

async function refreshCaptcha() {
  const res = await getCaptcha()
  if (res?.code === 200 && res.data) {
    form.value.captchaKey = res.data.captchaKey
    captchaImage.value = normalizeImage(res.data.captchaImage)
  } else {
    uni.showToast({ title: res?.msg || '验证码加载失败', icon: 'none' })
  }
}

async function doLogin() {
  if (!form.value.username.trim()) return uni.showToast({ title: '请输入用户名', icon: 'none' })
  if (!form.value.password) return uni.showToast({ title: '请输入密码', icon: 'none' })
  if (!form.value.captchaCode.trim()) return uni.showToast({ title: '请输入验证码', icon: 'none' })

  loading.value = true
  try {
    // 注意：业务失败不会 reject，必须自己判断 code（主项目踩过的坑）
    const res = await login({
      username: form.value.username.trim(),
      password: form.value.password,
      captchaKey: form.value.captchaKey,
      captchaCode: form.value.captchaCode.trim(),
    })
    if (res?.code === 200 && res.data) {
      setToken(res.data.token || res.data)
      setUserInfo(res.data.userInfo || res.data.user || res.data)
      setRemembered(form.value.username.trim())
      uni.showToast({ title: '登录成功', icon: 'success' })
      uni.switchTab({ url: '/pages/main/main' })
    } else {
      uni.showToast({ title: res?.msg || '登录失败', icon: 'none' })
      refreshCaptcha()
      form.value.captchaCode = ''
    }
  } finally {
    loading.value = false
  }
}

function goRegister() {
  uni.showModal({
    title: '注册',
    content: '为保证账号安全，请在网页端完成注册（需邮箱验证与协议确认）后，返回此处登录。',
    showCancel: false,
  })
}

onMounted(() => refreshCaptcha())
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 80rpx 40rpx;
  background: linear-gradient(180deg, #e6f7ea 0%, #f2fbf4 40%);
}
.brand {
  text-align: center;
  margin-bottom: 60rpx;
}
.brand-name {
  display: block;
  font-size: 64rpx;
  font-weight: 800;
  color: #3fae52;
  letter-spacing: 8rpx;
}
.brand-sub {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #8a9a8a;
}
.field {
  height: 88rpx;
  background: #f6faf6;
  border-radius: 16rpx;
  padding: 0 24rpx;
  font-size: 30rpx;
  margin-bottom: 24rpx;
}
.ph {
  color: #b5c4b5;
}
.captcha-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.captcha-input {
  flex: 1;
  margin-bottom: 24rpx;
}
.captcha-img {
  width: 200rpx;
  height: 88rpx;
  background: #f6faf6;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
}
.captcha-fallback {
  width: 200rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  color: #3fae52;
  font-size: 26rpx;
  margin-bottom: 24rpx;
}
.btn-primary {
  margin-top: 12rpx;
}
.btn-ghost {
  margin-top: 24rpx;
}
.dev-tip {
  margin-top: 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: #b5c4b5;
}
</style>
