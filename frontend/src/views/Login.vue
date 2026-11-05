<template>
  <div class="login-page">
    <!-- 返回欢迎页：登录/注册中途也可以反悔 -->
    <router-link to="/" class="back-home">← 返回首页</router-link>
    <div class="login-card">
      <!-- 左侧滑动图 -->
      <div class="pre-box" :style="{ transform: flag ? 'translateX(0%)' : 'translateX(100%)', backgroundColor: flag ? '#e1f0e5' : '#ddeec7' }">
        <h1><img class="brand-cow" src="/img/绪见后台管理.png" alt="绪见" />绪见</h1>
        <p>个人情绪记录、分析与陪伴平台</p>
        <div class="img-box">
          <img :src="flag ? '/img/登入.jpg' : '/img/注册1.jpg'" alt="" />
        </div>
      </div>

      <!-- 注册表单 -->
      <div class="register-form" :class="{ 'form-hidden': flag }">
        <div class="title-box"><h1>注册</h1></div>
        <div class="input-box">
          <input type="text" v-model="regForm.username" placeholder="用户名（2-20个字符）" maxlength="20" @keyup.enter="doRegister" />
          <p class="reg-hint warn">⚠️ 用户名将作为登录账号，注册后不可修改</p>
          <input type="text" v-model="regForm.nickname" placeholder="昵称（选填，页面里展示的名字）" maxlength="20" @keyup.enter="doRegister" />
          <!-- 邮箱必填：忘记密码靠它接收验证码，不绑邮箱的账号无法自助找回 -->
          <input type="email" v-model="regForm.email" placeholder="邮箱（必填，用于找回密码）" @keyup.enter="doRegister" />
          <p v-if="regForm.email" class="reg-hint" :class="emailOk ? 'ok' : 'err'">
            {{ emailOk ? '✅ 邮箱格式正确' : '✗ 邮箱格式不正确' }}
          </p>
          <input type="password" v-model="regForm.password" placeholder="密码（至少6位）" @keyup.enter="doRegister" />
          <!-- 密码强度提示 -->
          <div class="pwd-strength" v-if="regForm.password">
            <span class="s-label">密码强度</span>
            <span class="s-bars"><i v-for="n in 3" :key="n" :class="{ on: strength.level >= n, [strength.level]: true }"></i></span>
            <span class="s-text" :class="strength.level">{{ strength.text }}</span>
          </div>
          <input type="password" v-model="regForm.confirm" placeholder="确认密码" @keyup.enter="doRegister" />
          <p v-if="regForm.confirm" class="reg-hint" :class="regForm.confirm === regForm.password ? 'ok' : 'err'">
            {{ regForm.confirm === regForm.password ? '✅ 两次密码一致' : '✗ 两次输入的密码不一致' }}
          </p>
        </div>
        <!-- 用户协议勾选 -->
        <div class="agree-row" :class="{ shake: agreeShake }">
          <input type="checkbox" id="agreeChk" v-model="agreeChecked" />
          <label for="agreeChk">我已阅读并同意 <a @click.stop="agreeVisible = true">《用户协议与隐私政策》</a></label>
        </div>
        <div class="btn-box">
          <button :disabled="regLoading" @click="doRegister">{{ regLoading ? '注册中…' : '注 册' }}</button>
          <p @click="switchMode">已有账号？去登录</p>
        </div>
        <p class="privacy-note">🔒 您的情绪数据仅用于个人记录与分析</p>
      </div>

      <!-- 登录表单 -->
      <div class="login-form" :class="{ 'form-hidden': !flag }">
        <div class="title-box"><h1>登录</h1></div>
        <div class="input-box">
          <input type="text" v-model="loginForm.username" placeholder="用户名" maxlength="20" />
          <input type="password" v-model="loginForm.password" placeholder="密码" @keyup.enter="doLogin" />
          <!-- 图形验证码（服务端生成） -->
          <div class="captcha-row">
            <input type="text" v-model="captchaInput" placeholder="验证码" maxlength="4" @keyup.enter="doLogin" />
            <img
              class="captcha-img"
              v-if="captchaImage"
              :src="captchaImage"
              title="点击刷新验证码"
              alt="验证码"
              @click="refreshCaptcha"
            />
            <span v-else class="captcha-loading" @click="refreshCaptcha">{{ captchaFailed ? '加载失败，点击重试' : '加载中…' }}</span>
          </div>
          <!-- 记住我 / 忘记密码 -->
          <div class="aux-row">
            <label class="remember"><input type="checkbox" v-model="rememberMe" /> 记住我</label>
            <a class="forgot-link" @click="forgotPwd">忘记密码？</a>
          </div>
          <!-- 登录失败内联提示 -->
          <p class="form-error" v-if="loginError">⚠️ {{ loginError }}</p>
        </div>
        <div class="btn-box">
          <button :disabled="loginLoading" @click="doLogin">{{ loginLoading ? '登录中…' : '登 录' }}</button>
          <p @click="switchMode">没有账号？去注册</p>
        </div>

        <p class="privacy-note">🔒 您的情绪数据仅用于个人记录与分析</p>
      </div>
    </div>

    <!-- 忘记密码弹窗（三步：账号 → 验证码 → 新密码） -->
    <div class="agree-mask" v-if="forgotVisible" @click.self="forgotVisible = false">
      <div class="agree-dialog forgot-dialog">
        <button class="dlg-close" title="关闭" @click="forgotVisible = false">✕</button>
        <h3>找回密码</h3>
        <div class="agree-body">
          <template v-if="forgotStep === 1">
            <p class="fp-tip">输入你的用户名或绑定邮箱，我们会发送一封重置验证码邮件。</p>
            <input class="fp-input" v-model="forgotForm.account" placeholder="用户名 / 邮箱" @keyup.enter="sendForgotCode" />
          </template>
          <template v-else-if="forgotStep === 2">
            <p class="fp-tip">
              验证码已发送至 <b>{{ forgotEmail }}</b>
              <span v-if="forgotDevCode" class="fp-dev">（邮件未配置，开发模式验证码：{{ forgotDevCode }}）</span>
            </p>
            <input class="fp-input" v-model="forgotForm.code" placeholder="6 位验证码" maxlength="6" />
            <input class="fp-input" type="password" v-model="forgotForm.password" placeholder="新密码（至少 6 位）" />
          </template>
          <template v-else>
            <p class="fp-done">✅ 密码已重置，请用新密码登录。</p>
          </template>
        </div>
        <button class="agree-ok" v-if="forgotStep === 1" :disabled="forgotLoading" @click="sendForgotCode">
          {{ forgotLoading ? '发送中…' : '发送验证码' }}
        </button>
        <button class="agree-ok" v-else-if="forgotStep === 2" :disabled="forgotLoading" @click="submitReset">
          {{ forgotLoading ? '提交中…' : '确认重置' }}
        </button>
        <button class="agree-ok" v-else @click="forgotVisible = false">好的，去登录</button>
      </div>
    </div>

    <!-- 注册成功引导：选头像 + 主题色 -->
    <div class="agree-mask onboard-mask" v-if="onboardVisible">
      <div class="agree-dialog onboard-dialog">
        <button class="dlg-close" title="关闭" @click="skipOnboard">✕</button>
        <h3>欢迎加入绪见</h3>
        <p class="ob-sub">挑一个你喜欢的形象和主题色，马上开始记录</p>
        <div class="ob-block">
          <span class="ob-label">选择头像</span>
          <div class="ob-avatars">
            <button
              v-for="a in avatarChoices" :key="a"
              class="ob-avatar" :class="{ on: onboardForm.avatar === a }"
              @click="onboardForm.avatar = a"
            >
              <img :src="a" alt="" />
            </button>
          </div>
        </div>
        <div class="ob-block">
          <span class="ob-label">选择主题色</span>
          <div class="ob-themes">
            <button
              v-for="t in themeChoices" :key="t.key"
              class="ob-theme" :class="{ on: onboardForm.theme === t.key }"
              :style="{ background: t.color }"
              @click="onboardForm.theme = t.key"
            >{{ t.name }}</button>
          </div>
        </div>
        <button class="agree-ok" @click="finishOnboard">进入绪见</button>
        <button class="ob-skip" @click="onboardVisible = false">以后再说</button>
      </div>
    </div>

    <!-- 用户协议弹窗 -->
    <div class="agree-mask" v-if="agreeVisible" @click.self="agreeVisible = false">
      <div class="agree-dialog">
        <button class="dlg-close" title="关闭" @click="agreeVisible = false">✕</button>
        <h3>用户协议与隐私政策</h3>
        <div class="agree-body">
          <p><b>1. 数据用途</b>：您的情绪数据仅用于个人记录与分析，不会用于任何商业用途，也不会提供给任何第三方。</p>
          <p><b>2. 数据存储</b>：日记、测评等数据仅保存在本系统数据库中，账号密码经加密存储。</p>
          <p><b>3. 您的权利</b>：您可随时在系统内查看、修改、删除自己的记录；注销账号前请联系管理员。</p>
          <p><b>4. 温馨提示</b>：本系统不能替代专业心理咨询与医疗诊断，如您正处于严重的情绪困扰中，请及时寻求专业帮助。</p>
        </div>
        <button class="agree-ok" @click="acceptAgree">我已阅读并同意</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register, updateUser, getCaptcha, forgotSendCode, forgotReset } from '../api'
import { applyUserPrefs, clearUserPrefs } from '../utils/userPrefs'
import { useUserStore } from '../stores/user'
import '../styles/login.css'

const router = useRouter()
const route = useRoute()
const flag = ref(true) // true=登录模式, false=注册模式

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', nickname: '', email: '', password: '', confirm: '' })
// 邮箱格式实时校验：与找回密码共用的正则，前后端口径一致
const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const emailOk = computed(() => EMAIL_RE.test(regForm.email.trim()))

const loginLoading = ref(false)
const regLoading = ref(false)
const loginError = ref('')

// ===== 记住我 =====
const rememberMe = ref(localStorage.getItem('remember_username') ? true : false)
if (rememberMe.value) loginForm.username = localStorage.getItem('remember_username') || ''

// ===== 图形验证码（服务端生成，防绕过） =====
const captchaKey = ref('')
const captchaImage = ref('')
const captchaInput = ref('')
const captchaFailed = ref(false)

const refreshCaptcha = async () => {
  captchaInput.value = ''
  captchaFailed.value = false
  try {
    const res = await getCaptcha()
    if (res?.code === 200 && res.data) {
      captchaKey.value = res.data.captchaKey
      captchaImage.value = res.data.captchaImage
    } else {
      captchaFailed.value = true
    }
  } catch (e) {
    // 加载失败：给出可重试提示，不再永远停在"加载中…"
    captchaFailed.value = true
  }
}

// ===== 密码强度 =====
const strength = computed(() => {
  const p = regForm.password
  if (!p) return { level: 0, text: '' }
  let score = 0
  if (p.length >= 6) score++
  if (p.length >= 10 && /[a-zA-Z]/.test(p) && /\d/.test(p)) score++
  if (/[a-zA-Z]/.test(p) && /\d/.test(p) && /[^a-zA-Z0-9]/.test(p)) score++
  const map = ['weak', 'mid', 'strong']
  const txt = ['弱', '中', '强']
  const idx = Math.min(Math.max(score, 1), 3) - 1
  return { level: idx + 1, cls: map[idx], text: txt[idx] }
})

// ===== 用户协议 =====
const agreeChecked = ref(false)
const agreeVisible = ref(false)
const agreeShake = ref(false)
const acceptAgree = () => { agreeChecked.value = true; agreeVisible.value = false }

// ===== 切换登录/注册 =====
const switchMode = () => {
  flag.value = !flag.value
  loginError.value = ''
  if (flag.value) refreshCaptcha()
}

// ===== 忘记密码（邮箱验证码三步流程） =====
const forgotVisible = ref(false)
const forgotStep = ref(1)
const forgotLoading = ref(false)
const forgotToken = ref('')
const forgotEmail = ref('')
const forgotDevCode = ref('')
const forgotForm = reactive({ account: '', code: '', password: '' })

const forgotPwd = () => {
  forgotVisible.value = true
  forgotStep.value = 1
  forgotEmail.value = ''
  forgotDevCode.value = ''
  forgotForm.account = loginForm.username || ''
  forgotForm.code = ''
  forgotForm.password = ''
}

const sendForgotCode = async () => {
  if (!forgotForm.account.trim()) return ElMessage.warning('请输入用户名或邮箱')
  forgotLoading.value = true
  try {
    const res = await forgotSendCode(forgotForm.account.trim())
    if (res?.code === 200 && res.data) {
      forgotEmail.value = res.data.email || ''
      forgotToken.value = res.data.token || ''
      // 后端未配置邮件服务时会把验证码直接下发（开发/内网场景）
      forgotDevCode.value = res.data.devCode || ''
      forgotStep.value = 2
      ElMessage.success(res.data.sent ? '验证码已发送，请查收邮件' : '邮件服务未配置，已生成验证码')
    } else {
      ElMessage.error(res?.msg || '发送失败')
    }
  } catch (e) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    forgotLoading.value = false
  }
}

const submitReset = async () => {
  if (!forgotForm.code.trim()) return ElMessage.warning('请输入验证码')
  if (forgotForm.password.length < 6) return ElMessage.warning('新密码至少 6 位')
  forgotLoading.value = true
  try {
    const res = await forgotReset({ token: forgotToken.value, code: forgotForm.code.trim(), password: forgotForm.password })
    if (res?.code === 200) {
      forgotStep.value = 3
    } else {
      ElMessage.error(res?.msg || '重置失败')
    }
  } catch (e) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    forgotLoading.value = false
  }
}

// ===== 注册成功引导 =====
const onboardVisible = ref(false)
const onboardUser = ref(null)
const avatarChoices = ['/img/开心牛牛.png', '/img/平静牛牛.png', '/img/伤心牛牛.png', '/img/生气牛牛.png', '/img/惊讶牛牛.png']
const themeChoices = [
  { key: 'green', name: '薄荷绿', color: '#8cc269' },
  { key: 'blue', name: '天空蓝', color: '#7bb8e8' },
  { key: 'purple', name: '薰衣草', color: '#b39ddb' },
  { key: 'pink', name: '樱花粉', color: '#f8a5c2' },
]
const onboardForm = reactive({ avatar: avatarChoices[0], theme: 'green' })

const finishOnboard = async () => {
  const u = onboardUser.value
  const prefs = { avatar: onboardForm.avatar, theme: onboardForm.theme }
  if (u?.userId) {
    // 已登录（第三方登录等场景）：直接保存
    try {
      await updateUser({ userId: u.userId, ...prefs })
      applyUserPrefs({ ...prefs })
    } catch (e) { /* 失败也不阻塞 */ }
    onboardVisible.value = false
    router.push('/main')
    return
  }
  // 刚注册还没登录：先暂存，首次登录成功后自动应用
  localStorage.setItem('pending_onboard_prefs', JSON.stringify(prefs))
  onboardVisible.value = false
  flag.value = true      // 回到登录模式
  refreshCaptcha()
  ElMessage.success('设置已保存，登录后立即生效 💚')
}

// 跳过引导：同样暂存偏好，等登录后生效
const skipOnboard = () => {
  localStorage.setItem('pending_onboard_prefs', JSON.stringify({ avatar: onboardForm.avatar, theme: onboardForm.theme }))
  onboardVisible.value = false
  flag.value = true
  refreshCaptcha()
}

// ===== 登录 =====
const doLogin = async () => {
  loginError.value = ''
  if (!loginForm.username || !loginForm.password) {
    loginError.value = '请输入用户名和密码'
    return
  }
  if (!captchaInput.value.trim()) {
    loginError.value = '请输入验证码'
    return
  }
  loginLoading.value = true
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password,
      captchaKey: captchaKey.value,
      captchaCode: captchaInput.value.trim(),
    })
    if (res.code === 200) {
      // 记住我：只记用户名（token 本来就会持久化，下次免登录）
      if (rememberMe.value) localStorage.setItem('remember_username', loginForm.username)
      else localStorage.removeItem('remember_username')
      // 先清掉上一个账号留下的个性化设置，避免串号
      clearUserPrefs()
      // 登录态写入 Pinia store（store 内部负责同步持久化到 localStorage）
      useUserStore().setSession(res.data.token, res.data)
      // 用这个账号自己的设置（主题/头像框/背景/深色模式）覆盖本地
      applyUserPrefs(res.data)
      // 注册引导里选好的头像/主题色，首次登录时落库
      const pending = localStorage.getItem('pending_onboard_prefs')
      if (pending) {
        try {
          const prefs = JSON.parse(pending)
          await updateUser({ userId: res.data.userId, ...prefs })
          applyUserPrefs({ ...res.data, ...prefs })
          useUserStore().setUserInfo({ ...res.data, ...prefs })
        } catch (e) { /* 忽略 */ }
        localStorage.removeItem('pending_onboard_prefs')
      }
      ElMessage.success('登录成功！')
      // role=1 管理员跳后台，普通用户跳主页
      if (res.data.role === 1 || res.data.role === '1' || res.data.role === 'admin') {
        router.push('/admin/dashboard')
      } else {
        router.push('/main')
      }
    } else {
      loginError.value = res.msg || '用户名或密码错误'
      refreshCaptcha()
    }
  } catch (e) {
    loginError.value = '登录失败，请检查网络'
    refreshCaptcha()
  } finally {
    loginLoading.value = false
  }
}

// ===== 注册 =====
const doRegister = async () => {
  const name = regForm.username.trim()
  if (!name || !regForm.password || !regForm.confirm) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (name.length < 2) {
    ElMessage.warning('用户名至少 2 个字符')
    return
  }
  if (regForm.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (regForm.password !== regForm.confirm) {
    ElMessage.warning('两次密码不一致')
    return
  }
  // 邮箱必填：忘记密码的验证码发到这里，没邮箱的账号只能找管理员重置
  const email = regForm.email.trim()
  if (!email) {
    ElMessage.warning('请填写邮箱（用于找回密码）')
    return
  }
  if (!EMAIL_RE.test(email)) {
    ElMessage.warning('邮箱格式不正确，请检查后重试')
    return
  }
  if (!agreeChecked.value) {
    agreeShake.value = true
    setTimeout(() => (agreeShake.value = false), 500)
    ElMessage.warning('请先阅读并勾选《用户协议与隐私政策》')
    return
  }
  regLoading.value = true
  try {
    const res = await register({
      username: name,
      password: regForm.password,
      nickname: regForm.nickname.trim() || null,  // 不填就落 NULL，首页问候回落显示用户名
      email,
    })
    if (res.code === 200) {
      ElMessage.success('注册成功！先挑个形象吧')
      const pwd = regForm.password
      regForm.username = regForm.nickname = regForm.email = regForm.password = regForm.confirm = ''
      agreeChecked.value = false
      // 进入新手引导：选头像 + 主题色，完成后回到登录页（带好用户名）
      onboardUser.value = { userId: null, username: name }
      onboardVisible.value = true
      loginForm.username = name
      loginForm.password = pwd
      if (rememberMe.value) localStorage.setItem('remember_username', name)
    } else {
      ElMessage.error(res.msg || '注册失败')
    }
  } catch (e) {
    ElMessage.error('注册失败，请检查网络')
  } finally {
    regLoading.value = false
  }
}

// 气泡动效（复用原来的 JS）
let bubbleTimer = null
onMounted(() => {
  // 支持 /login?mode=register 直达注册表单（顶栏"注册"入口）
  if (route.query.mode === 'register' && flag.value) switchMode()
  const body = document.querySelector('.login-page')
  // 光斑泡泡（原始动效）
  const bubleCreate = () => {
    const buble = document.createElement('span')
    const r = Math.random() * 5 + 25
    buble.style.width = r + 'px'
    buble.style.height = r + 'px'
    buble.style.left = Math.random() * innerWidth + 'px'
    body.appendChild(buble)
    setTimeout(() => buble.remove(), 4000)
  }
  bubbleTimer = setInterval(bubleCreate, 200)
  refreshCaptcha()
})
onBeforeUnmount(() => { if (bubbleTimer) clearInterval(bubbleTimer) })
</script>

<style scoped>
.login-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  display: flex;
  background: linear-gradient(to right, rgb(247, 209, 215), rgb(191, 227, 241));
}
/* 弹窗右上角关闭按钮 + ESC 关闭 */
.dlg-close {
  position: absolute; top: 12px; right: 14px;
  width: 28px; height: 28px; line-height: 1;
  display: flex; align-items: center; justify-content: center;
  border: none; border-radius: 50%;
  background: rgba(0, 0, 0, .05); color: #86958999;
  font-size: 14px; cursor: pointer; transition: all .2s;
}
.dlg-close:hover { background: rgba(0, 0, 0, .1); color: #374151; transform: rotate(90deg); }
.agree-dialog { position: relative; }

/* 协议弹窗 */
.agree-mask {
  position: fixed; inset: 0; z-index: 99;
  background: rgba(0,0,0,0.35);
  display: flex; align-items: center; justify-content: center;
}
.agree-dialog {
  width: 480px; max-width: 92vw; max-height: 78vh;
  background: #fff; border-radius: 16px; padding: 26px 30px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.25);
  display: flex; flex-direction: column;
}
.agree-dialog h3 { margin: 0 0 14px; font-size: 18px; color: var(--c-text); text-align: center; }
.agree-body { overflow-y: auto; font-size: 13px; color: #666; line-height: 1.9; }
.agree-body p { margin: 0 0 10px; }
.agree-ok {
  /* 抵消 login.css `.login-page button` 的全局污染（宽100px/高30px/line-height/margin） */
  display: block; width: 100%; height: auto; min-height: 42px;
  margin: 16px 0 0; padding: 10px 0; line-height: normal;
  border: none; border-radius: 10px;
  background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff;
  font-size: 14px; cursor: pointer; box-sizing: border-box;
}
.agree-ok:hover { opacity: 0.9; }

/* 验证码图片 */
.captcha-img {
  height: 42px; border-radius: 10px; cursor: pointer;
  border: 1.5px solid #c6e0d2; flex: 0 0 120px;
}
.captcha-img:hover { opacity: .85; }
.captcha-loading {
  flex: 0 0 120px; height: 42px; line-height: 42px; text-align: center;
  font-size: 12px; color: #8aa; cursor: pointer;
  border: 1.5px dashed #c6e0d2; border-radius: 10px;
}

/* 找回密码弹窗 */
.forgot-dialog { max-width: 420px; }
.fp-tip { font-size: 13px; color: #6b7f72; margin-bottom: 14px !important; }
.fp-dev { color: #c08a2e; }
.fp-input {
  width: 100%; height: 40px; margin-bottom: 12px; padding: 0 12px;
  border: 1.5px solid #c6e0d2; border-radius: 10px; background: rgba(255,255,255,.9);
  color: #5a6b60; font-size: 14px; box-sizing: border-box; outline: none;
}
.fp-input:focus { border-color: #9ccfb2; box-shadow: 0 0 0 3px rgba(140,194,105,.18); }
.fp-done { text-align: center; font-size: 14px; color: var(--c-hex); }

/* 注册引导 */
.onboard-dialog { max-width: 460px; }
.ob-sub { margin: 0 0 16px; text-align: center; font-size: 13px; color: var(--c-text-weak); }
.ob-block { margin-bottom: 16px; }
.ob-label { display: block; font-size: 13px; color: #6b7f72; margin-bottom: 8px; }
.ob-avatars { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.ob-avatar {
  width: 52px; height: 52px; border-radius: 50%; border: 2px solid transparent;
  background: rgba(255,255,255,.8); cursor: pointer; padding: 3px; transition: all .25s;
}
.ob-avatar img { width: 100%; height: 100%; object-fit: contain; }
.ob-avatar.on { border-color: var(--c-hex); box-shadow: 0 6px 14px -6px color-mix(in srgb, var(--c-hex) 80%, transparent); transform: translateY(-2px); }
.ob-themes { display: flex; gap: 10px; justify-content: center; }
.ob-theme {
  flex: 1; height: 34px; border: none; border-radius: 999px; color: #fff;
  font-size: 13px; font-weight: 600; cursor: pointer; opacity: .8; transition: all .25s;
}
.ob-theme.on { opacity: 1; transform: translateY(-2px); box-shadow: 0 8px 16px -8px rgba(0,0,0,.4); }
.ob-skip {
  display: block; width: 100%; margin-top: 10px; padding: 6px;
  background: none; border: none; color: #9ab09a; font-size: 12px; cursor: pointer;
}
.ob-skip:hover { color: #6b8f78; }
</style>
