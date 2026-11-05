<script>
/**
 * 应用入口：只做全局样式与登录态兜底，不写页面逻辑。
 */
import { isLoggedIn } from './utils/storage'

export default {
  onLaunch() {
    // 未登录时允许停留在登录页，其余页面由各自 onShow 里判断
    console.log('[绪见] 小程序启动，登录态：', isLoggedIn())
  },
}
</script>

<style>
/* 全局：小程序不支持 * 选择器和标签选择器，样式必须写在类上 */
page {
  /* 对齐 H5 hp-root 的顶部暖白 → 薄荷绿渐变 */
  background: linear-gradient(180deg, #fffaf6 0%, #f6f9f6 45%, #f0f7f0 100%);
  background-attachment: fixed;
  min-height: 100vh;
  font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #2f3e2f;
}
.container {
  padding: 24rpx;
}
.card {
  /* 对齐 H5 卡片：半透明白 + 细白描边 + 柔和投影 */
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 12rpx 28rpx -18rpx rgba(80, 110, 80, 0.3);
}
.muted {
  color: #8a9a8a;
  font-size: 26rpx;
}
.btn-primary {
  background: #3fae52;
  color: #ffffff;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  text-align: center;
}
.btn-ghost {
  background: #ffffff;
  color: #3fae52;
  border: 2rpx solid #3fae52;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  text-align: center;
}
</style>
