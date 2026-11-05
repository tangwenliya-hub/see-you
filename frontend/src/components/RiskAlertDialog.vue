<template>
  <!--
    情绪风险预警弹窗（全局，挂在 AppLayout 里，登录后任意页面最多弹一次）

    ⚠️ 伦理红线：这是一个**情绪关怀提示**，不是医疗诊断工具。
       弹窗顶部与底部都必须出现免责声明，且文案里不得出现「抑郁症」「患病」等结论性判断。
       该组件的 visible 只能由用户接口里的 ackStatus 驱动 —— 后端不做这个标记，前端就不弹。
  -->
  <el-dialog
    v-model="visible"
    :show-close="false"
    :close-on-click-modal="false"
    width="min(92vw, 460px)"
    class="rad-dialog"
    align-center
  >
    <div class="rad-root" :class="['lv' + (alert.level || 1), { crisis: alert.crisis }]">
      <div class="rad-banner">
        <span class="rad-badge">{{ alert.crisis ? '危机关怀' : (alert.levelName || '关怀提示') }}</span>
        <span class="rad-title">{{ alert.crisis ? '绪见想先确认一下：你现在还好吗？' : '绪见想和你说几句心里话' }}</span>
      </div>

      <p class="rad-date">{{ alert.alertDate }}</p>

      <!-- ① 判定依据：把统计翻译成人话，让用户知道为什么弹 -->
      <div class="rad-detail">
        {{ alert.triggerDetail }}
      </div>

      <!-- ② 温馨提示 + 自我调节建议（后端分级文案） -->
      <div class="rad-advice">{{ alert.advice }}</div>

      <!-- ③ 求助引导：固定资源位，不写成「你该去看医生」
           清单由后端下发（yml 的 app.alert.crisis-hotlines），避免前后端两处维护 -->
      <div class="rad-help">
        <p class="rad-help-title">如果需要，可以试着联系</p>
        <ul class="rad-help-list">
          <li v-for="(h, i) in hotlines" :key="i">
            {{ h.label }}<b v-if="h.tel">{{ h.tel }}</b>
          </li>
        </ul>
      </div>

      <!-- ④ 免责声明：前端第二道，和后端 RiskProperties.disclaimer 双保险 -->
      <p class="rad-disclaimer">
        本提示仅基于你的情绪记录做统计提醒，<b>不构成任何医疗诊断，也不能作为抑郁症等疾病的诊断依据</b>。
        如有身体或心理上的持续不适，请以专业医疗机构的意见为准。
      </p>

      <div class="rad-actions">
        <!-- 误报反馈：预警内容与真实状态明显不符时的一键「这不是我」。
             危机通道（红色关怀）不显示——安全方向宁可错弹不可漏接 -->
        <button
          v-if="!alert.crisis && !alert.misreport"
          class="rad-btn misreport"
          :disabled="misreporting"
          @click="doMisreport"
        >{{ misreporting ? '提交中…' : '这不是我（误报）' }}</button>
        <router-link to="/profile?tab=risk" class="rad-btn ghost" @click="close">查看全部记录</router-link>
        <button class="rad-btn primary" @click="doAck">我知道了</button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getRiskLatest, ackRisk, readRisk, misreportRisk } from '@/api/sense'

const visible = ref(false)
const alert = ref({})
const misreporting = ref(false)

/**
 * 求助渠道：以后端配置为准（app.alert.crisis-hotlines），
 * 后端没返回（旧版本 / 接口异常）时退回本地的同款清单，保证弹窗永远不会是空的。
 * 格式约定：「名称 + 空格 + 号码」→ 号码加粗；没有号码的整条显示。
 */
const FALLBACK_HOTLINES = [
  '全国 24 小时心理援助热线 12356',
  '北京心理危机研究与干预中心 010-82951332',
  '希望 24 热线 400-161-9995',
  '学校心理咨询中心 / 当地精神卫生中心',
]
const hotlines = computed(() => {
  const raw = alert.value?.crisisHotlines
  const list = Array.isArray(raw) && raw.length ? raw : FALLBACK_HOTLINES
  return list.map(s => {
    const m = String(s).match(/^(.*?)\s*([\d\-+]{5,})\s*$/)
    return m ? { label: m[1] + '：', tel: m[2] } : { label: String(s), tel: '' }
  })
})

/**
 * 一次会话只弹一次：即使用户没点「我知道了」，
 * 关掉之后也不在本页面生命周期内再骚扰（下次进入仍然是同一次 SPA 会话）。
 */
let shownOnce = false

async function load(force = false) {
  try {
    const res = await getRiskLatest()
    const d = res?.data
    if (d?.hasAlert) {
      alert.value = d
      // force=true：用户在日记页刚写下命中的内容，必须立刻弹，不受「一次会话只弹一次」限制
      if (force) shownOnce = false
      if (!shownOnce) {
        visible.value = true
        shownOnce = true
        markShownRead(d)
      }
    }
  } catch {
    /* 未登录 / 后端未部署：静默跳过，绝不能因为报错挡住页面 */
  }
}

// 日记保存后由页面派发：后端已判定命中了危机词，这里立即弹窗。
// ⚠️ AI 精判是异步的（保存后约 10 秒才落库）：立即检查很可能扑空，
//    所以首次检查没弹时，再在 6s/14s/30s 各补查一次，专等 AI 预警到位。
let retryTimers = []
const onCrisisCheck = (e) => {
  const delayed = e?.detail?.delayed
  load(!delayed)
  if (!delayed) {
    retryTimers.forEach(clearTimeout)
    retryTimers = [6000, 14000, 30000].map(ms => setTimeout(() => load(false), ms))
  }
}

/**
 * 弹出即标已读：「用户看到」和「用户知晓」是两件事——
 * 已读（isRead）在弹窗出现时触发，确认（ackStatus）仍由「我知道了」按钮驱动。
 * 之前已读只挂在 ack 上，用户看完弹窗直接关掉，记录里永远显示未读。
 */
function markShownRead(d) {
  if (!d?.alertId) return
  readRisk(d.alertId).catch(() => {})
  window.dispatchEvent(new CustomEvent('risk-alert-read'))
}

onMounted(() => {
  load()
  window.addEventListener('risk-crisis-check', onCrisisCheck)
})
onBeforeUnmount(() => {
  retryTimers.forEach(clearTimeout)
  window.removeEventListener('risk-crisis-check', onCrisisCheck)
})

function close() {
  visible.value = false
}

async function doAck() {
  const id = alert.value?.alertId
  close()
  if (!id) return
  try {
    await ackRisk(id)
    ElMessage.success('已记录，绪见会继续陪着你')
    // 通知全局刷新未读红点
    window.dispatchEvent(new CustomEvent('risk-alert-read'))
  } catch {
    /* 静默：下次进来还会再提示一次，属于安全方向 */
  }
}

/**
 * 标记误报（「这不是我」）：后端会同时把这条记录置为已确认，
 * 之后不会再弹；标记结果进后台统计，用于计算预警准确率。
 * 危机关怀（红色弹窗）不提供该按钮，后端也会拒绝。
 */
async function doMisreport() {
  const id = alert.value?.alertId
  if (!id || misreporting.value) return
  misreporting.value = true
  try {
    await misreportRisk(id)
    alert.value.misreport = true
    close()
    ElMessage.success('已收到你的反馈，这条提示不会再打扰你')
    window.dispatchEvent(new CustomEvent('risk-alert-read'))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.msg || '标记失败，请稍后再试')
  } finally {
    misreporting.value = false
  }
}

// 留一个手动入口：用户在预警记录页点「再看一次」
defineExpose({ load })
</script>

<style scoped>
.rad-root {
  padding: 4px 2px 0;
  box-sizing: border-box;
}
.rad-banner {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 6px;
}
.rad-badge {
  font-size: 12px; font-weight: 700; color: #fff;
  padding: 3px 10px; border-radius: 10px; white-space: nowrap;
  background: #f0a15c;
}
.lv2 .rad-badge { background: #ee8f6a; }
.lv3 .rad-badge { background: #dd6f6f; }
/* 危机通道：红色 + 左描边，和「统计趋势」的提示在视觉上区分开 */
.crisis .rad-badge { background: #d65757; }
.crisis .rad-detail {
  background: #fdeaea; color: #8a4b45;
  border-left: 3px solid rgba(214, 87, 87, 0.65);
}
.crisis .rad-advice { color: #6b4141; }
.rad-title { font-size: 16px; font-weight: 700; color: #3c4d5f; }
.rad-date { margin: 0 0 10px; font-size: 11px; color: #a3b2c2; }

.rad-detail {
  font-size: 12.5px; line-height: 1.75; color: #6b7f93;
  background: #f6f8fa; border-radius: 12px;
  padding: 10px 12px; margin-bottom: 12px;
  overflow-wrap: anywhere;
}

.rad-advice {
  font-size: 13.5px; line-height: 1.85; color: #44576b;
  white-space: pre-wrap; overflow-wrap: anywhere;
  margin-bottom: 14px;
  max-height: 34vh; overflow-y: auto;
}

.rad-help {
  background: rgba(168, 212, 240, 0.16);
  border: 1px dashed rgba(168, 212, 240, 0.8);
  border-radius: 12px; padding: 10px 12px; margin-bottom: 12px;
}
.rad-help-title { margin: 0 0 6px; font-size: 12.5px; font-weight: 700; color: #4a6b8a; }
.rad-help-list { margin: 0; padding-left: 18px; }
.rad-help-list li { font-size: 12.5px; line-height: 1.9; color: #5d7186; }

.rad-disclaimer {
  margin: 0 0 14px;
  font-size: 11.5px; line-height: 1.75; color: #b0705f;
  background: #fdf3edd9; border-radius: 10px; padding: 8px 10px;
  overflow-wrap: anywhere;
}
.rad-disclaimer b { color: #a8493a; }

.rad-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.rad-btn {
  flex: 1 1 auto; min-height: 38px; padding: 0 14px;
  border: none; border-radius: 12px;
  font-size: 13.5px; font-family: inherit; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  text-decoration: none;
  transition: transform 0.15s ease;
}
.rad-btn:active { transform: scale(0.98); }
.rad-btn.primary { background: #7fb3d5; color: #fff; }
.rad-btn.ghost   { background: var(--c-bg2); color: #5d7186; }
.rad-btn.misreport {
  background: transparent; color: #9aa9ba;
  border: 1px dashed #c4cfda; flex: 0 0 auto;
  font-size: 12px;
}
.rad-btn.misreport:hover:not(:disabled) { color: #8a5a6c; border-color: #e8a3b8; }

@media (max-width: 520px) {
  .rad-actions .rad-btn { flex: 1 1 100%; }
  .rad-advice { max-height: 30vh; }
}
</style>
