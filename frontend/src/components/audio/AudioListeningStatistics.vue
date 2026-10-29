<template>
  <section v-if="days.length" class="audio-statistics glass" aria-label="收听统计">
    <h2>收听统计</h2>
    <dl>
      <div>
        <dt>最近 365 天有效收听</dt>
        <dd>{{ audioTime(total) }}</dd>
      </div>
      <div>
        <dt>最近 7 天有效收听</dt>
        <dd>{{ audioTime(week) }}</dd>
      </div>
      <div>
        <dt>收听天数</dt>
        <dd>{{ days.filter((day) => day.elapsedMs > 0).length }}</dd>
      </div>
    </dl>
    <small>统计实际收听时间，倍速、拖动和重复同步不会按内容位置累计。</small>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/utils/api'
import { audioTime } from '@/utils/audiobook'
const days = ref<Array<{ activityDate: string; elapsedMs: number; daysAgo: number }>>([])
const total = computed(() => days.value.reduce((sum, day) => sum + day.elapsedMs, 0))
const week = computed(() =>
  days.value.filter((day) => day.daysAgo < 7).reduce((sum, day) => sum + day.elapsedMs, 0)
)
onMounted(async () => {
  try {
    days.value = (await api.get('/api/audiobooks/activity')).data
  } catch {
    /* Library management remains available without statistics. */
  }
})
</script>
<style scoped>
.audio-statistics {
  padding: 20px;
  border-radius: 20px;
  margin: 24px 0;
}
h2 {
  font-size: 20px;
}
dl {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
  margin: 16px 0;
}
dt,
small {
  color: var(--text-secondary);
}
dd {
  font-size: 24px;
  margin-top: 8px;
}
</style>
