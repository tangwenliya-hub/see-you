import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': '/src' } },
  build: {
    rollupOptions: {
      output: {
        // 手动分包：把不常变动的第三方库拆成独立 chunk。
        // 好处：业务代码迭代后浏览器只需重新下载业务 chunk，
        // element-plus / vue / echarts 命中浏览器缓存。
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element'
            if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts'
            if (id.includes('vue') || id.includes('pinia')) return 'vendor-vue'
            return 'vendor-misc'
          }
        },
      },
    },
  },
  server: {
    port: 5173,
    // 代理后端！前端 /api/* → 后端 http://localhost:8080/*
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
      // 文件上传后访问图片的代理
      '/upload': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      // WebSocket 实时推送
      '/ws': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})