<template>
  <!--
    果冻风确认弹层（jellyConfirm 的实体组件）：临时小操作（删除/退出/取消点赞…）统一用它。
    手机端：贴底弹层 + 下滑/点遮罩关闭，关闭后停留在原页面原位置；
    桌面端：BottomSheet 自动切居中卡片形态。
    纯原生元素实现（createApp 动态挂载，不带全局组件上下文），不依赖 AppIcon / Element Plus。
  -->
  <BottomSheet :open="visible" :title="title" @close="finish(false)">
    <div class="jc-body">
      <p class="jc-msg" :class="{ danger }">{{ message }}</p>
      <div class="jc-actions">
        <button class="jc-btn cancel" @click="finish(false)">{{ cancelText }}</button>
        <button :class="['jc-btn', danger ? 'danger' : 'ok']" @click="finish(true)">{{ confirmText }}</button>
      </div>
    </div>
  </BottomSheet>
</template>

<script setup>
import { ref } from 'vue'
import BottomSheet from './BottomSheet.vue'

defineProps({
  message: { type: String, required: true },
  title: { type: String, default: '提示' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '再想想' },
  danger: { type: Boolean, default: false },
})

const visible = ref(true)
const emit = defineEmits(['result'])
let settled = false
function finish(ok) {
  if (settled) return
  settled = true
  visible.value = false
  // 等收起动画走完再卸载，避免闪断
  setTimeout(() => emit('result', ok), 180)
}
</script>

<style scoped>
.jc-body { padding: 4px 2px 2px; }
.jc-msg {
  margin: 6px 4px 20px;
  font-size: 14.5px;
  line-height: 1.7;
  color: #5c4a56;
  white-space: pre-wrap;
  word-break: break-word;
}
.jc-msg.danger { color: #c2544f; }
.jc-actions { display: flex; gap: 10px; }
.jc-btn {
  flex: 1;
  height: 44px;
  border: 0;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  letter-spacing: 1px;
  transition: filter 0.15s, transform 0.1s;
}
.jc-btn:active { transform: scale(0.97); }
.jc-btn.cancel { background: #f3e9ee; color: #7a6270; }
.jc-btn.cancel:hover { filter: brightness(0.97); }
.jc-btn.ok {
  background: linear-gradient(135deg, #f7a8c0, #ef8aae);
  color: #fff;
  box-shadow: 0 4px 14px rgba(239, 138, 174, 0.35);
}
.jc-btn.ok:hover { filter: brightness(1.04); }
.jc-btn.danger {
  background: linear-gradient(135deg, #e57373, #d9534f);
  color: #fff;
  box-shadow: 0 4px 14px rgba(217, 83, 79, 0.32);
}
.jc-btn.danger:hover { filter: brightness(1.04); }

:root.dark-mode .jc-msg { color: #d8c8d2; }
:root.dark-mode .jc-msg.danger { color: #ef9a94; }
:root.dark-mode .jc-btn.cancel { background: #3a3140; color: #c9b8c4; }
</style>
