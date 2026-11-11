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
  background: linear-gradient(135deg, #9ed9a8 0%, #6fbf8f 100%);
  color: #ffffff;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  text-align: center;
  box-shadow: 0 14rpx 26rpx -14rpx rgba(111, 191, 143, 0.72);
}
.btn-ghost {
  background: rgba(255, 255, 255, 0.86);
  color: #5aa96c;
  border: 1.5rpx solid #bfe3cc;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
}

/* ===== 统一线性图标库 =====
   规范：24×24 网格 / stroke-width 1.8 / 圆角端点 / fill none，
   每个图标的描边色与所在位置文字色一致（内联 SVG data URI，小程序不支持 <svg> 标签），
   使用：<view class="li-icon li-ic-xxx"></view>，尺寸由页面样式控制。 */
.li-icon {
  display: block;
  flex-shrink: 0;
  background-repeat: no-repeat;
  background-position: center;
  background-size: 100% 100%;
}
/* 用户（输入框文字 #43544a） */
.li-ic-user {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2343544a' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Ccircle cx='12' cy='8' r='3.6'/%3E%3Cpath d='M5.5 19.5c.9-3.2 3.4-5 6.5-5s5.6 1.8 6.5 5'/%3E%3C/svg%3E");
}
/* 锁 · 输入框（#43544a） */
.li-ic-lock {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2343544a' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Crect x='5.5' y='10.8' width='13' height='8.7' rx='2.4'/%3E%3Cpath d='M8.5 10.8V8.2a3.5 3.5 0 0 1 7 0v2.6'/%3E%3Cpath d='M12 14.2v2.2'/%3E%3C/svg%3E");
}
/* 盾牌 · 验证码（#43544a） */
.li-ic-shield {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2343544a' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 3.6 18.8 6v5.3c0 4.5-2.8 7.6-6.8 9.1-4-1.5-6.8-4.6-6.8-9.1V6L12 3.6z'/%3E%3Cpath d='M9.5 11.9l1.9 1.9 3.3-3.6'/%3E%3C/svg%3E");
}
/* 锁 · 隐私提示（文字 #9ab8a6） */
.li-ic-lock-note {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%239ab8a6' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Crect x='5.5' y='10.8' width='13' height='8.7' rx='2.4'/%3E%3Cpath d='M8.5 10.8V8.2a3.5 3.5 0 0 1 7 0v2.6'/%3E%3Cpath d='M12 14.2v2.2'/%3E%3C/svg%3E");
}
/* 心 · 绪见的关心（标题 #6b5a2f） */
.li-ic-heart {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%236b5a2f' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 19.8C8.2 17.2 4 13.6 4 9.6 4 7 6 5 8.4 5c1.5 0 2.9.8 3.6 2 .7-1.2 2.1-2 3.6-2C18 5 20 7 20 9.6c0 4-4.2 7.6-8 10.2z'/%3E%3C/svg%3E");
}
/* 星光 · AI 副标题（#8a9a8a） */
.li-ic-sparkle {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%238a9a8a' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 3.5c.9 4.6 3 6.7 7.6 7.6-4.6.9-6.7 3-7.6 7.6-.9-4.6-3-6.7-7.6-7.6 4.6-.9 6.7-3 7.6-7.6z'/%3E%3Cpath d='M18.5 15.5c.4 2 1.3 2.9 3.3 3.3-2 .4-2.9 1.3-3.3 3.3-.4-2-1.3-2.9-3.3-3.3 2-.4 2.9-1.3 3.3-3.3z'/%3E%3C/svg%3E");
}
/* 挥手 · 欢迎语（#3d5145） */
.li-ic-wave {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%233d5145' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2'/%3E%3Cpath d='M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2'/%3E%3Cpath d='M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8'/%3E%3Cpath d='M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15'/%3E%3C/svg%3E");
}
/* 日记本 · 空态（#8a9a8a） */
.li-ic-book {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%238a9a8a' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M4 19.5A2.5 2.5 0 0 1 6.5 17H20'/%3E%3Cpath d='M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'/%3E%3C/svg%3E");
}
/* 对勾 · 保存按钮（按钮文字白色） */
.li-ic-check {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23ffffff' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M5 12.5l4.5 4.5L19 7'/%3E%3C/svg%3E");
}
/* 用户头像圈 · 无头像兜底（品牌绿 #5aa96c） */
.li-ic-avatar {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%235aa96c' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Ccircle cx='12' cy='12' r='9.3'/%3E%3Ccircle cx='12' cy='9.5' r='2.8'/%3E%3Cpath d='M6.2 18.4c1-2.6 3.1-4 5.8-4s4.8 1.4 5.8 4'/%3E%3C/svg%3E");
}
</style>
