<template>
  <div>
    <div class="page-toolbar">
      <div class="page-title"><img src="/img/系统参数.png" class="title-icon" /> 系统参数配置</div>
      <div style="display:flex;gap:10px">
        <button class="jelly-btn ghost" @click="onReset('alert')">预警恢复默认</button>
        <button class="jelly-btn ghost" @click="onReset('sense')">无感恢复默认</button>
        <button class="jelly-btn primary" :disabled="saving" @click="onSave">{{ saving ? '保存中...' : '保存并生效' }}</button>
      </div>
    </div>

    <div class="ext-note">
      ⚠️ {{ disclaimer }}
      这里的参数调整的是<b>统计层面的提示阈值</b>，不是医学判定标准；改动后立即生效，无需重启服务。
    </div>

    <div v-if="loading" class="table-loading">加载中...</div>
    <template v-else>
      <div v-for="g in groups" :key="g.key" class="admin-card" style="margin-bottom:18px">
        <div class="set-group-title" style="margin-bottom:10px">
          <img :src="g.icon" /> {{ g.title }}
        </div>
        <div class="ext-note info" style="margin-bottom:10px">{{ g.desc }}</div>
        <div v-for="it in g.items" :key="it.key" class="set-row">
          <div class="sr-main">
            <div class="sr-label">
              {{ it.label }}
              <span v-if="it.unit" style="font-weight:400;color:#8a9a8a">（{{ it.unit }}）</span>
              <span v-if="it.overridden" class="status-tag warn" style="margin-left:6px">已自定义</span>
            </div>
            <div class="sr-desc">{{ it.desc }}</div>
            <div class="sr-key">{{ it.key }}</div>
          </div>
          <div class="sr-ctl">
            <el-switch v-if="it.type === 'bool'" v-model="form[it.key]" />
            <el-input-number
              v-else
              v-model="form[it.key]"
              :step="it.type === 'double' ? 0.05 : 1"
              :precision="it.type === 'double' ? 2 : 0"
              :min="it.type === 'double' ? -999 : 0"
              size="small"
              style="width:150px" />
          </div>
          <div class="sr-def">默认 {{ fmtDef(it) }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { jellyConfirm } from '@/utils/confirm'
import { adminSettingsGet, adminSettingsSave, adminSettingsReset } from '@/api'

const loading = ref(true)
const saving = ref(false)
const items = ref([])
const form = ref({})
const disclaimer = ref('')

const GROUPS = [
  {
    key: 'alert', title: '情绪风险预警阈值', icon: '/img/情绪预警后台图标.png',
    desc: '两条通道：趋势通道按窗口统计判定（负面占比、低落天数与下滑趋势共同越界才提示，避免单次情绪波动误报）；危机通道由日记正文命中危机词立即触发，不受窗口与冷静期约束。',
  },
  {
    key: 'sense', title: '轻量无感记录参数', icon: '/img/无感记录后台图标.png',
    desc: '控制推测算法的保守程度：置信度阈值越高，系统越「宁可不猜」；KNN 邻居数越大，结果越平滑。',
  },
]

const groups = computed(() => GROUPS.map(g => ({
  ...g,
  items: items.value.filter(i => i.group === g.key),
})))

const fmtDef = (it) => {
  if (it.defaultValue === undefined || it.defaultValue === null || it.defaultValue === '') return '-'
  if (it.type === 'bool') return it.defaultValue === 'true' ? '开启' : '关闭'
  return it.defaultValue
}

const load = async () => {
  loading.value = true
  try {
    const res = await adminSettingsGet()
    if (res?.code !== 200) { ElMessage.error(res?.msg || '参数加载失败'); return }
    items.value = res.data?.items || []
    disclaimer.value = res.data?.disclaimer || ''
    const m = {}
    items.value.forEach(it => {
      m[it.key] = it.type === 'bool'
        ? String(it.value) === 'true'
        : it.type === 'double'
          ? Number(it.value)
          : Number(it.value)
    })
    form.value = m
  } finally { loading.value = false }
}

const onSave = async () => {
  saving.value = true
  try {
    const res = await adminSettingsSave({ ...form.value })
    if (res?.code !== 200) { ElMessage.error(res?.msg || '保存失败'); return }
    ElMessage.success({ message: '参数已保存并立即生效', grouping: true })
    load()
  } finally { saving.value = false }
}

const onReset = (group) => {
  jellyConfirm(`确定把「${group === 'alert' ? '情绪预警' : '无感记录'}」的参数恢复为 yml 默认值吗？`, { title: '恢复默认' })
    .then(async (ok) => {
      if (!ok) return
      const res = await adminSettingsReset(group)
      if (res?.code !== 200) { ElMessage.error(res?.msg || '恢复失败'); return }
      ElMessage.success({ message: '已恢复默认', grouping: true })
      load()
    }).catch(() => {})
}

onMounted(load)
</script>
