<template>
  <section class="polling-options-settings">
    <header class="settings-heading">
      <div>
        <p class="settings-eyebrow">CRAWLER REFRESH</p>
        <h2>采集页面自动刷新选项</h2>
        <p>设置所有用户在书籍爬虫页面可选的刷新间隔。用户当前选择仍按各自账户保存。</p>
      </div>
      <el-button :loading="loading" @click="loadOptions">刷新</el-button>
    </header>

    <div class="polling-options-card">
      <div class="option-entry">
        <el-input-number
          v-model="draftInterval"
          :min="1"
          :max="3600"
          :step="1"
          controls-position="right"
          aria-label="新增刷新间隔秒数"
          @keydown.enter.prevent="addInterval"
        />
        <el-button type="primary" :disabled="!canAddInterval" @click="addInterval">添加选项</el-button>
      </div>
      <div class="interval-list" aria-label="当前自动刷新间隔选项">
        <el-tag
          v-for="interval in intervals"
          :key="interval"
          closable
          :disable-transitions="true"
          @close="removeInterval(interval)"
        >{{ interval }} 秒</el-tag>
        <span v-if="!intervals.length" class="empty-hint">至少添加一个间隔选项。</span>
      </div>
      <p class="field-hint">支持 1–3600 秒，最多 20 项；保存后所有用户的下拉选项会更新。</p>
      <footer>
        <el-button type="primary" :loading="saving" :disabled="!intervals.length" @click="saveOptions">
          保存配置
        </el-button>
      </footer>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/utils/api'
import { message } from '@/utils/message'

const intervals = ref<number[]>([])
const draftInterval = ref<number | undefined>()
const loading = ref(false)
const saving = ref(false)
const canAddInterval = computed(() => {
  const value = draftInterval.value
  return Number.isInteger(value)
    && value !== undefined
    && value >= 1
    && value <= 3600
    && !intervals.value.includes(value)
    && intervals.value.length < 20
})

async function loadOptions() {
  loading.value = true
  try {
    const { data } = await api.get<number[]>('/api/crawler-settings/polling-interval-options')
    intervals.value = [...data].sort((left, right) => left - right)
  } catch (error: any) {
    message.error(error.response?.data?.message || '自动刷新选项加载失败')
  } finally {
    loading.value = false
  }
}

function addInterval() {
  if (!canAddInterval.value || draftInterval.value === undefined) return
  intervals.value = [...intervals.value, draftInterval.value].sort((left, right) => left - right)
  draftInterval.value = undefined
}

function removeInterval(interval: number) {
  intervals.value = intervals.value.filter(value => value !== interval)
}

async function saveOptions() {
  if (!intervals.value.length) return
  saving.value = true
  try {
    const { data } = await api.put<number[]>('/api/crawler-settings/polling-interval-options', {
      intervals: intervals.value,
    })
    intervals.value = data
    message.success('自动刷新选项已保存')
  } catch (error: any) {
    message.error(error.response?.data?.message || '自动刷新选项保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadOptions)
</script>

<style scoped>
.polling-options-settings {
  display: grid;
  gap: 18px;
}

.settings-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.settings-eyebrow {
  margin: 0 0 5px;
  color: var(--primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.settings-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
}

.settings-heading p:last-child,
.field-hint,
.empty-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

.settings-heading p:last-child {
  margin: 6px 0 0;
}

.polling-options-card {
  display: grid;
  gap: 18px;
  padding: 22px;
  border: 1px solid var(--border-color-light);
  border-radius: 18px;
  background: var(--surface-elevated);
}

.option-entry {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.option-entry :deep(.el-input-number) {
  width: 180px;
}

.interval-list {
  display: flex;
  min-height: 34px;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
}

.field-hint {
  margin: -8px 0 0;
}

footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
  border-top: 1px solid var(--border-color-light);
}

@media (max-width: 560px) {
  .settings-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .polling-options-card {
    padding: 16px;
  }
}
</style>
