<template>
  <!--
    情绪关怀消息卡片（常驻式）

    与弹窗的分工：弹窗负责「首次强提醒」，这张卡片负责「事后可回看」。
    用户点过「我知道了」之前，它一直留在首页；之后自动收起，不再占版面。

    两种来源（后端 triggerRule 区分）：
      · 趋势通道（L1/L2/L3_*）：7 天窗口统计，展示低落天数与负面占比；
      · 危机通道（CRISIS_KEYWORD）：日记正文命中危机词，立即提示，且默认展开——
        这类提示不能被折叠状态藏起来。
  -->
  <div v-if="alert" class="rac-root" :class="['lv' + (alert.level || 1), { crisis: alert.crisis }]">
    <div class="rac-head" @click="open = !open">
      <span class="rac-icon"><AppIcon :name="alert.crisis ? 'heart-hand' : 'sparkles'" /></span>
      <div class="rac-headline">
        <p class="rac-title">
          {{ alert.crisis ? '危机关怀提示 · 请先看这条' : ('情绪关怀提示 · ' + alert.levelName) }}
          <span class="rac-badge" v-if="alert.ackStatus === 0">未确认</span>
        </p>
        <p class="rac-sub">{{ alert.alertDate }} · {{ shortDetail }}</p>
      </div>
      <span class="rac-fold">{{ open ? '收起' : '展开' }}</span>
    </div>

    <div v-if="open" class="rac-body">
      <p class="rac-block">{{ alert.triggerDetail }}</p>
      <p v-if="alert.crisis && alert.crisisHotline" class="rac-hotline">
        如果此刻很难受，现在就拨：<b>{{ alert.crisisHotline }}</b>
      </p>
      <div class="rac-block pre">{{ alert.advice }}</div>
      <p class="rac-disclaimer">
        本提示仅基于情绪记录做统计提醒，<b>不构成医疗诊断，也不能作为抑郁症等疾病的诊断依据</b>。
      </p>
      <div class="rac-actions">
        <router-link to="/profile?tab=risk" class="rac-btn ghost">全部记录</router-link>
        <button v-if="alert.ackStatus === 0" class="rac-btn primary" @click="doAck">我知道了</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRiskLatest, ackRisk } from '@/api/sense'

const alert = ref(null)
const open = ref(false)

const shortDetail = computed(() => {
  const a = alert.value
  if (!a) return ''
  // 危机通道不走统计窗口，没有低落天数/占比可讲，只说命中原因
  if (a.crisis) return '日记中出现了需要被认真对待的表达'
  return `窗口内低落 ${a.maxDowncastDays ?? 0} 天 · 负面占比 ${Math.round(Number(a.negativeRatio || 0) * 100)}%`
})

async function load() {
  try {
    const res = await getRiskLatest()
    alert.value = res?.data?.hasAlert ? res.data : null
    // 危机提示默认展开：折叠起来等于没提醒
    open.value = !!alert.value?.crisis
  } catch {
    alert.value = null
  }
}

async function doAck() {
  const id = alert.value?.alertId
  if (!id) return
  try {
    await ackRisk(id)
    ElMessage.success('已记录')
    window.dispatchEvent(new CustomEvent('risk-alert-read'))
    await load()
    open.value = false
  } catch {
    ElMessage.warning('操作失败，请稍后再试')
  }
}

defineExpose({ load })
onMounted(load)
</script>

<style scoped>
.rac-root {
  margin: 14px 0 4px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 243, 237, 0.85);
  border: 1px solid rgba(240, 161, 92, 0.45);
  box-sizing: border-box;
}
.lv2.rac-root { background: rgba(253, 240, 232, 0.9); border-color: rgba(238, 143, 106, 0.5); }
.lv3.rac-root { background: rgba(253, 234, 234, 0.92); border-color: rgba(221, 111, 111, 0.55); }
/* 危机通道：比三级趋势预警更"当面"，左描边加粗，避免和普通卡片混在一起 */
.rac-root.crisis {
  background: rgba(253, 232, 232, 0.96);
  border: 1px solid rgba(214, 87, 87, 0.7);
  border-left: 4px solid #d65757;
}

.rac-head {
  display: flex; align-items: center; gap: 10px; cursor: pointer;
}
.rac-icon { font-size: 18px; flex: 0 0 auto; }
.rac-headline { flex: 1; min-width: 0; }
.rac-title { margin: 0; font-size: 13.5px; font-weight: 700; color: #6b4a3a; }
.rac-badge {
  font-size: 10px; color: #fff; background: #f0a15c;
  padding: 1px 6px; border-radius: 7px; margin-left: 6px; white-space: nowrap;
}
.rac-sub { margin: 2px 0 0; font-size: 11.5px; color: #9a877a; }
.rac-fold { font-size: 11.5px; color: #b09481; flex: 0 0 auto; white-space: nowrap; }

.rac-body { margin-top: 10px; }
.rac-block {
  margin: 0 0 8px; font-size: 12.5px; line-height: 1.8; color: #6b5548;
  overflow-wrap: anywhere;
}
.rac-block.pre { white-space: pre-wrap; }
.rac-hotline {
  margin: 0 0 8px; padding: 8px 10px;
  font-size: 12.5px; line-height: 1.7; color: #9c3f3f;
  background: rgba(255, 255, 255, 0.85); border-radius: 9px;
}
.rac-hotline b { color: #b03030; }
.rac-disclaimer {
  margin: 0 0 10px; font-size: 11px; line-height: 1.7; color: #b0705f;
  background: rgba(255, 255, 255, 0.7); border-radius: 9px; padding: 7px 9px;
}
.rac-disclaimer b { color: #a8493a; }

.rac-actions { display: flex; gap: 8px; }
.rac-btn {
  flex: 1 1 auto; min-height: 32px; padding: 0 12px;
  border: none; border-radius: 10px; font-size: 12.5px; font-family: inherit;
  cursor: pointer; text-decoration: none;
  display: inline-flex; align-items: center; justify-content: center;
}
.rac-btn.primary { background: #f0a15c; color: #fff; }
.rac-btn.ghost   { background: rgba(255, 255, 255, 0.85); color: #8a7264; }

@media (max-width: 700px) {
  .rac-actions .rac-btn { flex: 1 1 100%; }
}
</style>
