<template>
  <!--
    今日情绪推测卡片

    设计红线（对应论文的「用户拥有最终决定权」）：
      1. 卡片只做展示与引导，不自动写入任何日记数据；
      2. 「去确认 / 修改」跳到写日记页，用户改完再保存才会落库（且 diary.source=2 标记来源）；
      3. 数据源只认 JWT —— 前端不传 userId，看不见别人的推测。
  -->
  <div v-if="data" class="sc-root">
    <div class="sc-head">
      <span class="sc-title"><AppIcon name="sparkles" /> 今日情绪推测</span>
      <span class="sc-tip">仅供参考 · 以你的感受为准</span>
    </div>

    <div class="sc-main">
      <div class="sc-emo-wrap" :style="{ background: emoBg }">
        <img class="sc-emo" :src="emoIcon" :alt="data.emotionName" />
      </div>
      <div class="sc-info">
        <p class="sc-emoline">
          <b>{{ data.emotionName }}</b>
          <span class="sc-conf">置信度 {{ conf }}%</span>
        </p>
        <p class="sc-reason">{{ data.reason || '根据你最近的行为与记录推测' }}</p>
        <div v-if="cands.length > 1" class="sc-cands">
          <span v-for="c in cands" :key="c.emotion" class="sc-cand">
            {{ c.name }} {{ pct(c.p) }}%
          </span>
        </div>
      </div>
    </div>

    <div class="sc-actions">
      <button class="sc-btn plain" :disabled="busy" @click="doReject">忽略</button>
      <button class="sc-btn ghost" :disabled="busy" @click="doGuess">
        {{ reGuessing ? '重算中…' : '重新猜' }}
      </button>
      <router-link to="/diary/write" class="sc-btn primary">去确认 / 修改</router-link>
    </div>

    <p class="sc-note">在你点「保存」之前，这条推测不会变成正式记录。</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { emotionInfo } from '@/utils/constants'
import { getTodayPrediction, generatePrediction, rejectPrediction } from '@/api/sense'

const data = ref(null)
const busy = ref(false)
const reGuessing = ref(false)

const emo = computed(() => emotionInfo(data.value?.emotion))
const emoIcon = computed(() => emo.value.icon)
const emoBg = computed(() => emo.value.bg)
const conf = computed(() => Math.round(Number(data.value?.confidence || 0) * 100))
const cands = computed(() => Array.isArray(data.value?.candidates) ? data.value.candidates : [])
const pct = (p) => Math.round(Number(p || 0) * 100)

async function load() {
  try {
    const res = await getTodayPrediction()
    const d = res?.data
    data.value = d?.hasSuggestion ? d : null
  } catch {
    data.value = null   // 未登录 / 未开启 / 后端异常：一律不渲染，不打扰
  }
}

async function doGuess() {
  reGuessing.value = true
  busy.value = true
  try {
    const res = await generatePrediction(null)
    if (res?.code !== 200) {
      // 业务失败（如 403 未开启）：透出后端 msg，不给含糊的兜底文案
      ElMessage.warning({ message: res?.msg || '无感记录未开启，可到「个人中心  无感记录」开启', grouping: true })
    } else if (res.data?.hasSuggestion) {
      data.value = res.data
      ElMessage.success('已重新生成推测')
    } else {
      data.value = null
      ElMessage.info({ message: res.data?.reason || '线索还不够，这次先不猜了', grouping: true })
    }
  } catch (e) {
    ElMessage.warning({ message: e?.response?.data?.msg || '暂时无法生成推测', grouping: true })
  } finally {
    reGuessing.value = false
    busy.value = false
  }
}

async function doReject() {
  if (!data.value?.predictionId) return
  busy.value = true
  try {
    await rejectPrediction(data.value.predictionId)
    data.value = null
    ElMessage.success('已忽略这条推测')
  } catch {
    ElMessage.warning('操作失败，请稍后再试')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sc-root {
  margin: 16px 0 4px;
  padding: 14px 16px 12px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(168, 212, 240, 0.22), rgba(255, 201, 216, 0.18));
  border: 1px solid rgba(168, 212, 240, 0.45);
  box-sizing: border-box;
  animation: scIn 0.45s ease both;
}
@keyframes scIn {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: none; }
}

.sc-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.sc-title { font-size: 14px; font-weight: 700; color: #4a6b8a; }
.sc-tip   { font-size: 11px; color: #8a9bab; }

.sc-main { display: flex; align-items: center; gap: 12px; }
.sc-emo-wrap {
  width: 48px; height: 48px; flex: 0 0 48px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.sc-emo { width: 38px; height: 38px; object-fit: contain; }
.sc-info { flex: 1; min-width: 0; }
.sc-emoline {
  display: flex; align-items: baseline; gap: 8px;
  margin: 0 0 3px; font-size: 15px; color: #33465c;
}
.sc-emoline b { font-size: 17px; }
.sc-conf {
  font-size: 11px; color: #fff; background: #7fb3d5;
  padding: 1px 7px; border-radius: 8px; white-space: nowrap;
}
.sc-reason {
  margin: 0; font-size: 12px; line-height: 1.6; color: #6b7f93;
  overflow-wrap: anywhere;
}
.sc-cands { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 7px; }
.sc-cand {
  font-size: 11px; color: #5d7186; background: rgba(255, 255, 255, 0.75);
  padding: 2px 8px; border-radius: 9px; white-space: nowrap;
}

.sc-actions {
  display: flex; gap: 8px; margin-top: 12px;
  flex-wrap: wrap;
}
.sc-btn {
  flex: 1 1 auto;
  min-height: 34px;
  padding: 0 12px;
  border: none; border-radius: 12px;
  font-size: 13px; font-family: inherit;
  cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  text-decoration: none;
  transition: transform 0.15s ease, opacity 0.15s ease;
}
.sc-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.sc-btn:not(:disabled):active { transform: scale(0.97); }
.sc-btn.primary { background: #7fb3d5; color: #fff; }
.sc-btn.ghost   { background: rgba(255, 255, 255, 0.85); color: #5d7186; }
.sc-btn.plain   { background: transparent; color: #9aabbd; }

.sc-note {
  margin: 8px 0 0; font-size: 11px; color: #a3b2c2; text-align: right;
}

/* 移动端：整块变成纵向，按钮撑满一行好点 */
@media (max-width: 700px) {
  .sc-head { flex-direction: column; gap: 2px; }
  .sc-actions .sc-btn { flex: 1 1 100%; }
}
</style>
