<template>
  <div class="cow-loading" :class="[sizeClass, { 'is-overlay': overlay }]">
    <div class="cow-stage">
      <div class="cow-arc"></div>
      <div class="cow-orbit">
        <span></span><span></span><span></span><span></span>
      </div>
      <div class="cow-orbit reverse">
        <span></span><span></span><span></span>
      </div>
      <!-- width/height 是 HTML 原生属性：在 CSS（scoped 样式）尚未注入时也生效，
           防止 675px 原图在样式加载前按原始尺寸闪现 -->
      <img class="cow-img" :width="IMG_SIZE[size] || 138" :height="IMG_SIZE[size] || 138"
           src="/img/cow加载.png" alt="加载中" draggable="false" />
    </div>
    <div v-if="text" class="cow-text">
      {{ text }}<span class="cow-dots"></span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 提示文字，传空字符串则不显示
  text: { type: String, default: '加载中' },
  // sm 小号（列表/弹窗内） | md 中号（页面区域） | lg 大号（全屏/首屏）
  size: { type: String, default: 'md' },
  // true = 绝对定位铺满父容器（父容器需 position: relative/absolute）
  overlay: { type: Boolean, default: false },
})

const sizeClass = computed(() => `cow-${props.size}`)

// 与 CSS 中 .cow-img 三档尺寸保持一致，用于 HTML 属性兜底
const IMG_SIZE = { lg: 200, md: 138, sm: 92 }
</script>

<style scoped>
.cow-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  user-select: none;
}

/* 全屏/容器遮罩模式 */
.cow-loading.is-overlay {
  position: absolute;
  inset: 0;
  z-index: 999;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(3px);
  border-radius: inherit;
}

/* ---------- 尺寸 ---------- */
.cow-lg .cow-stage { width: 260px; height: 260px; }
.cow-md .cow-stage { width: 180px; height: 180px; }
.cow-sm .cow-stage { width: 120px; height: 120px; }

.cow-lg .cow-img { width: 200px; height: 200px; }
.cow-md .cow-img { width: 138px; height: 138px; }
.cow-sm .cow-img { width: 92px; height: 92px; }

.cow-lg .cow-text { font-size: 30px; letter-spacing: 6px; }
.cow-md .cow-text { font-size: 21px; letter-spacing: 4px; }
.cow-sm .cow-text { font-size: 15px; letter-spacing: 2px; }

/* ---------- 奶牛舞台 ---------- */
.cow-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: cow-float 3s ease-in-out infinite;
}

.cow-img {
  position: relative;
  z-index: 2;
  object-fit: contain;
  filter: drop-shadow(0 10px 18px rgba(76, 175, 80, 0.22));
}

/* 旋转光点 */
.cow-orbit {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  animation: cow-spin 4.5s linear infinite;
}
.cow-orbit.reverse {
  inset: 14%;
  animation: cow-spin-rev 6s linear infinite;
}
.cow-orbit span {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: radial-gradient(circle, #d9ffdf 0%, #66e07a 55%, rgba(102, 224, 122, 0) 100%);
  box-shadow: 0 0 10px 3px rgba(102, 224, 122, 0.5);
}
.cow-orbit span:nth-child(1) { top: -3px; left: 50%; transform: translateX(-50%); }
.cow-orbit span:nth-child(2) { top: 22%; right: -3px; width: 9px; height: 9px; }
.cow-orbit span:nth-child(3) { bottom: -1px; left: 26%; width: 10px; height: 10px; }
.cow-orbit span:nth-child(4) { bottom: 22%; left: -1px; width: 7px; height: 7px; }

/* 旋转弧线 */
.cow-arc {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 3px solid transparent;
  border-bottom-color: rgba(102, 224, 122, 0.6);
  border-top-color: rgba(102, 224, 122, 0.22);
  animation: cow-spin 2.8s linear infinite;
}

/* ---------- 文字 ---------- */
.cow-text {
  font-weight: 800;
  color: transparent;
  background: linear-gradient(90deg, #3fae52, #7ee88e, #3fae52);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  animation: cow-shine 2.2s linear infinite;
}

.cow-dots::after {
  content: '';
  animation: cow-dots 1.5s steps(4, end) infinite;
}

@keyframes cow-spin { to { transform: rotate(360deg); } }
@keyframes cow-spin-rev { to { transform: rotate(-360deg); } }
@keyframes cow-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}
@keyframes cow-shine { to { background-position: 200% center; } }
@keyframes cow-dots {
  0% { content: ''; }
  25% { content: '.'; }
  50% { content: '..'; }
  75% { content: '...'; }
  100% { content: ''; }
}

/* 暗色模式适配 */
:global(:root.dark-mode) .cow-loading.is-overlay {
  background: rgba(31, 41, 55, 0.82);
}
</style>
