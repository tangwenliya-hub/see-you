<template>
  <Teleport to="body">
    <div v-if="show" class="sb-mask" @click.self="close">
      <div class="sb-card">
        <h3><AppIcon name="moon" /> 近 7 天睡眠补录</h3>
        <p class="sb-sub">给最近 7 天有日记的日子补上「昨晚睡眠」，数据看板的「睡眠 × 情绪」散点会立刻多出样本</p>

        <div v-if="loading" class="sb-loading">加载中...</div>
        <template v-else>
          <div class="sb-rows">
            <div
              v-for="r in rows"
              :key="r.date"
              class="sb-row"
              :class="{ disabled: r.count === 0, changed: isChanged(r) }"
            >
              <span class="sb-date">
                <b>{{ r.date.slice(5) }}</b>
                <i>{{ weekday(r.date) }}<template v-if="isToday(r.date)"> · 今天</template></i>
              </span>
              <template v-if="r.count > 0">
                <input
                  v-model.number="r.draft"
                  type="number" min="0" max="24" step="0.5"
                  class="sb-input" placeholder="不填"
                />
                <span class="sb-unit">小时</span>
                <button v-if="r.draft !== '' && r.draft !== null" class="sb-clear" title="清除该天睡眠值" @click="r.draft = ''">×</button>
              </template>
              <span v-else class="sb-none">当天没有日记</span>
            </div>
          </div>

          <div class="sb-actions">
            <span class="sb-changed" v-if="changedCount">{{ changedCount }} 天待保存</span>
            <button class="sb-cancel" @click="close">取消</button>
            <button class="sb-save" :disabled="!changedCount || saving" @click="save">
              {{ saving ? '保存中...' : '保存补录' }}
            </button>
          </div>
          <p class="sb-tip">留空 = 不改动 / 清除该天的睡眠值；只统计 0~24 小时，可带 .5</p>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getSleepWeek, batchSleep } from '../api'

const props = defineProps({ show: Boolean })
const emit = defineEmits(['update:show', 'saved'])

const loading = ref(false)
const saving = ref(false)
// rows: {date, count, sleepHours(后端原值), draft(输入框值)}
const rows = ref([])

const open = async () => {
  loading.value = true
  try {
    const res = await getSleepWeek()
    if (res?.code === 200 && Array.isArray(res.data)) {
      rows.value = res.data.map((d) => ({
        date: d.date,
        count: Number(d.count || 0),
        sleepHours: d.sleepHours ?? null,
        draft: d.sleepHours ?? '',
      }))
    } else {
      ElMessage.error(res?.msg || '加载失败')
      close()
    }
  } catch {
    ElMessage.error('网络错误，加载失败')
    close()
  } finally {
    loading.value = false
  }
}
watch(() => props.show, (v) => { if (v) open() })

// 空串 = 清除（hours 传 null）；有值且合法才提交
const isChanged = (r) => {
  const cur = r.draft === '' || r.draft === null ? null : Number(r.draft)
  const old = r.sleepHours ?? null
  if (cur === null && old === null) return false
  if (cur === null || old === null) return true
  return Number(cur) !== Number(old)
}
const changedCount = computed(() => rows.value.filter(isChanged).length)

const save = async () => {
  // 先整体校验，有一处非法就拦住
  for (const r of rows.value) {
    if (!isChanged(r) || r.draft === '' || r.draft === null) continue
    const n = Number(r.draft)
    if (Number.isNaN(n) || n < 0 || n > 24) {
      ElMessage.warning(`${r.date} 的睡眠时长请填 0 ~ 24 之间的数字（可带 .5）`)
      return
    }
  }
  const items = rows.value
    .filter(isChanged)
    .map((r) => ({
      date: r.date,
      hours: r.draft === '' || r.draft === null ? null : Math.round(Number(r.draft) * 2) / 2,
    }))
  if (!items.length) return
  saving.value = true
  try {
    const res = await batchSleep(items)
    if (res?.code === 200) {
      ElMessage.success(` 已补录 ${res.data ?? items.length} 天的睡眠`)
      emit('saved')
      close()
    } else {
      ElMessage.error('' + (res?.msg || '保存失败'))
    }
  } catch {
    ElMessage.error('网络错误，请重试')
  } finally {
    saving.value = false
  }
}

const close = () => emit('update:show', false)
const weekday = (s) => '日一二三四五六'[new Date(s + 'T00:00:00').getDay()]
const isToday = (s) => s === new Date().toLocaleDateString('sv-SE') // sv-SE: YYYY-MM-DD
</script>

<style scoped>
.sb-mask {
  position: fixed; inset: 0; z-index: 2000;
  background: rgba(60, 80, 65, 0.35);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
}
.sb-card {
  width: 430px; max-width: 94vw; max-height: 86vh; overflow: auto;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 20px; padding: 24px 26px 18px;
  box-shadow: 0 18px 50px rgba(80, 120, 95, 0.25);
  box-sizing: border-box;
}
.sb-card h3 { margin: 0 0 6px; font-size: 17px; color: #446c3a; letter-spacing: 2px; }
.sb-sub { margin: 0 0 14px; font-size: 12px; color: #9aab8e; line-height: 1.6; }
.sb-loading { text-align: center; color: #9aab8e; padding: 30px 0; }

.sb-rows { display: flex; flex-direction: column; gap: 8px; }
.sb-row {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 12px; border-radius: 12px;
  background: rgba(140, 194, 105, 0.07);
  border: 1px solid rgba(140, 194, 105, 0.18);
  transition: all 0.2s;
}
.sb-row.changed { background: rgba(140, 194, 105, 0.14); border-color: #8cc269; }
.sb-row.disabled { opacity: 0.55; }
.sb-date { width: 92px; display: flex; flex-direction: column; line-height: 1.3; }
.sb-date b { font-size: 13.5px; color: #44603a; }
.sb-date i { font-style: normal; font-size: 11px; color: #9aab8e; }
.sb-input {
  width: 64px; padding: 6px 8px; border-radius: 999px;
  border: 1.5px solid #d0e0d0; background: #fff;
  font-size: 13px; color: #444; font-family: inherit; text-align: center; outline: none;
}
.sb-input:focus { border-color: #8cc269; box-shadow: 0 0 0 3px rgba(140,194,105,0.12); }
.sb-input::-webkit-outer-spin-button,
.sb-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.sb-input[type=number] { -moz-appearance: textfield; appearance: textfield; }
.sb-unit { font-size: 12px; color: #8aa88a; }
.sb-clear {
  width: 20px; height: 20px; border-radius: 50%; border: none;
  background: rgba(0,0,0,0.08); color: #8a9a8a;
  font-size: 13px; line-height: 1; cursor: pointer; flex-shrink: 0;
}
.sb-clear:hover { background: #ff5b6a; color: #fff; }
.sb-none { font-size: 12px; color: #b5c2b0; }

.sb-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 16px; }
.sb-changed { font-size: 12px; color: #6fbf6f; margin-right: auto; }
.sb-cancel {
  padding: 8px 18px; border-radius: 999px; border: 1.5px solid #d0e0d0;
  background: #fff; color: #6b7a72; font-size: 13px; cursor: pointer;
}
.sb-cancel:hover { border-color: #b8d49a; }
.sb-save {
  padding: 8px 22px; border-radius: 999px; border: none;
  background: linear-gradient(135deg, #8cc269, #6eb464);
  color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
  box-shadow: 0 4px 14px rgba(140,194,105,0.32);
}
.sb-save:disabled { background: #ccc; box-shadow: none; cursor: not-allowed; }
.sb-tip { margin: 10px 0 0; font-size: 11px; color: #b5c2b0; line-height: 1.6; }
</style>
