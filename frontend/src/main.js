import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { initUserStorageSync } from './stores/user'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// Element Plus 深色主题变量：靠 html.dark 激活（App.vue 里与 dark-mode 一起切换），
// 否则 el-dialog / el-select / el-pagination 这些组件在深色下仍是白底
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/el-plus-theme.css'
import './styles/el-feedback.css'
import './styles/jelly-confirm.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import AppIcon from './components/AppIcon.vue'

// 生产环境静音调试日志（60+ 处 console.log 是开发期排查用的，不该带到用户控制台）
if (import.meta.env.PROD) {
  console.log = () => {}
  console.debug = () => {}
}

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
// 全站统一线性图标：全局注册，模板里直接写 <AppIcon name="moon" />，各页面不用 import
app.component('AppIcon', AppIcon)
app.use(createPinia())   // 必须在 router/组件挂载前：登录态 store 要在路由守卫里就能用
initUserStorageSync()    // 跨标签页同步登录态（A 标签登出 → B 标签立即生效）
app.use(router)
app.use(ElementPlus, { locale: zhCn })  // 中文语言包：分页/日期选择等组件文案
app.mount('#app')