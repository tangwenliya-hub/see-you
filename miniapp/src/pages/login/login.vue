<template>
  <view class="login-page">
    <!-- 柔和背景光斑（纯 CSS 装饰，不依赖图片资源） -->
    <view class="blob blob-a"></view>
    <view class="blob blob-b"></view>

    <!-- 品牌区：开心贴纸 + 名称 + 标语 -->
    <view class="brand">
      <view class="brand-logo">
        <image class="brand-logo-img" src="/static/emo/e1.png" mode="aspectFit" />
      </view>
      <text class="brand-name">绪见</text>
      <text class="brand-sub">记录每一天的情绪</text>
    </view>

    <!-- 登录卡片 -->
    <view class="login-card">
      <view class="field" :class="{ focused: focusKey === 'username' }">
        <text class="field-icon">👤</text>
        <input
          class="field-input"
          v-model="form.username"
          placeholder="用户名"
          placeholder-class="ph"
          confirm-type="next"
          @focus="focusKey = 'username'"
          @blur="focusKey = ''"
        />
      </view>

      <view class="field" :class="{ focused: focusKey === 'password' }">
        <text class="field-icon">🔒</text>
        <input
          class="field-input"
          v-model="form.password"
          password
          placeholder="密码"
          placeholder-class="ph"
          confirm-type="done"
          @focus="focusKey = 'password'"
          @blur="focusKey = ''"
          @confirm="doLogin"
        />
      </view>

      <view class="captcha-row">
        <view class="field captcha-field" :class="{ focused: focusKey === 'captcha' }">
          <text class="field-icon">🛡️</text>
          <input
            class="field-input"
            v-model="form.captchaCode"
            placeholder="验证码"
            placeholder-class="ph"
            maxlength="6"
            @focus="focusKey = 'captcha'"
            @blur="focusKey = ''"
            @confirm="doLogin"
          />
        </view>
        <image
          v-if="captchaImage"
          class="captcha-img"
          :src="captchaImage"
          mode="aspectFit"
          @click="refreshCaptcha"
        />
        <view v-else class="captcha-fallback" @click="refreshCaptcha">点击刷新</view>
      </view>

      <view class="login-submit" hover-class="submit-press" @click="doLogin">
        {{ loading ? '登录中…' : '登 录' }}
      </view>
      <view class="login-register" hover-class="link-press" @click="goRegister">
        还没有账号？去注册
      </view>
    </view>

    <text class="privacy-note">🔒 您的情绪数据仅用于个人记录与分析</text>
    <view v-if="isDev" class="dev-tip">接口地址：{{ baseUrl }}</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCaptcha, login } from '../../api'
import { setToken, setUserInfo, getRemembered, setRemembered } from '../../utils/storage'
import { BASE_URL, IS_DEV } from '../../config'

const form = ref({ username: getRemembered(), password: '', captchaKey: '', captchaCode: '' })
const captchaImage = ref('')
const loading = ref(false)
const focusKey = ref('')
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
/* ===== 页面底色：奶绿柔渐变 + 装饰光斑 ===== */
.login-page {
  position: relative;
  min-height: 100vh;
  padding: 56rpx 44rpx 56rpx;
  box-sizing: border-box;
  overflow: hidden;
  background: linear-gradient(180deg, #d7ecdc 0%, #e9f5ec 38%, #f7fbf8 100%);
}
.blob {
  position: absolute;
  border-radius: 50%;
  z-index: 0;
  pointer-events: none;
}
.blob-a {
  top: -120rpx;
  right: -90rpx;
  width: 360rpx;
  height: 360rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.65), rgba(255, 255, 255, 0) 70%);
}
.blob-b {
  bottom: 80rpx;
  left: -130rpx;
  width: 340rpx;
  height: 340rpx;
  background: radial-gradient(circle, rgba(158, 217, 168, 0.34), rgba(158, 217, 168, 0) 70%);
}

/* ===== 品牌区 ===== */
.brand {
  position: relative;
  z-index: 1;
  text-align: center;
  margin-bottom: 44rpx;
}
.brand-logo {
  width: 132rpx;
  height: 132rpx;
  margin: 0 auto 22rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 16rpx 34rpx -20rpx rgba(90, 169, 108, 0.55);
}
.brand-logo-img {
  width: 84rpx;
  height: 84rpx;
}
.brand-name {
  display: block;
  font-size: 52rpx;
  font-weight: 800;
  color: #3d5a46;
  letter-spacing: 12rpx;
}
.brand-sub {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #7e9384;
  letter-spacing: 2rpx;
}

/* ===== 登录卡片 ===== */
.login-card {
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 32rpx;
  padding: 44rpx 34rpx 38rpx;
  box-shadow: 0 24rpx 48rpx -28rpx rgba(90, 140, 100, 0.42);
}

/* ===== 输入框：图标 + 柔描边 + 聚焦柔光（对齐 H5 login.css input）===== */
.field {
  display: flex;
  align-items: center;
  height: 92rpx;
  background: rgba(255, 255, 255, 0.82);
  border: 1.5rpx solid #d7e8dd;
  border-radius: 22rpx;
  padding: 0 26rpx;
  margin-bottom: 24rpx;
  box-sizing: border-box;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}
.field.focused {
  background: rgba(255, 255, 255, 0.96);
  border-color: #7fbf9d;
  box-shadow: 0 0 0 6rpx rgba(127, 191, 157, 0.18);
}
.field-icon {
  width: 40rpx;
  font-size: 30rpx;
  margin-right: 16rpx;
  flex-shrink: 0;
}
.field-input {
  flex: 1;
  height: 92rpx;
  font-size: 30rpx;
  color: #43544a;
  background: transparent;
}
.ph {
  color: #a9bdb0;
}

/* ===== 验证码行 ===== */
.captcha-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
}
.captcha-field {
  flex: 1;
  margin-bottom: 24rpx;
}
.captcha-img {
  width: 196rpx;
  height: 92rpx;
  border-radius: 22rpx;
  border: 1.5rpx solid #d7e8dd;
  background: #ffffff;
  flex-shrink: 0;
}
.captcha-fallback {
  width: 196rpx;
  height: 92rpx;
  border-radius: 22rpx;
  border: 1.5rpx dashed #a9cfb8;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5aa96c;
  font-size: 24rpx;
  flex-shrink: 0;
  box-sizing: border-box;
}

/* ===== 主按钮：薄荷绿渐变（对齐 H5 button）===== */
.login-submit {
  margin-top: 14rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  text-align: center;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
  letter-spacing: 6rpx;
  background: linear-gradient(135deg, #9ed9a8 0%, #6fbf8f 100%);
  box-shadow: 0 14rpx 26rpx -14rpx rgba(111, 191, 143, 0.72);
}
.submit-press {
  transform: scale(0.97);
  opacity: 0.92;
}
.login-register {
  margin-top: 28rpx;
  text-align: center;
  font-size: 26rpx;
  color: #6fbf8f;
}
.link-press {
  opacity: 0.6;
}

/* ===== 底部辅助文字 ===== */
.privacy-note {
  position: relative;
  z-index: 1;
  display: block;
  margin-top: 34rpx;
  text-align: center;
  font-size: 22rpx;
  color: #9ab8a6;
  letter-spacing: 1rpx;
}
.dev-tip {
  position: relative;
  z-index: 1;
  margin-top: 14rpx;
  text-align: center;
  font-size: 20rpx;
  color: #b9cdc0;
}
</style>
