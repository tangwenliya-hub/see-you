import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig(({ mode }) => {
  // 读取 .env / .env.production 里 VITE_ 开头的变量
  const env = loadEnv(mode, process.cwd(), 'VITE_')

  return {
    plugins: [uni()],
    define: {
      // ⚠️ 小程序平台（mp-weixin）里 import.meta.env 会被编译成空对象 {}，
      //    读不到 .env 里的变量。所以这里在编译期把它替换成字符串字面量。
      //    没配置时是空串，src/config.js 会回退到 localhost。
      __API_BASE__: JSON.stringify(env.VITE_API_BASE || ''),
    },
  }
})
