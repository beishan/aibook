<template>
  <section class="quick-reader-window-settings" aria-label="小窗窗口设置">
    <h3>小窗窗口</h3>
    <label for="quick-reader-mask-transparency">
      蒙板透明度 <output>{{ transparency }}%</output>
    </label>
    <input
      id="quick-reader-mask-transparency"
      type="range"
      min="0"
      max="100"
      step="1"
      :value="transparency"
      @input="update"
      @change="sendQuickReaderControl({ action: 'flush' })"
    />
    <p>0% 完全遮黑，100% 完全透明。拖动工具栏最右侧手柄可移动小窗。</p>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { sendQuickReaderControl } from '@/reader/quickWindowBridge'

const transparency = ref(50)
const receiveState = (event: MessageEvent) => {
  if (event.origin !== window.location.origin || event.source !== window.parent) return
  if (event.data?.type !== 'aibook:quick-reader-window-state') return
  const value = event.data.overlayTransparency
  if (typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 100) {
    transparency.value = value
  }
}
const update = (event: Event) => {
  const value = Number((event.target as HTMLInputElement).value)
  if (!Number.isInteger(value) || value < 0 || value > 100) return
  transparency.value = value
  sendQuickReaderControl({ action: 'mask', value })
}
onMounted(() => {
  window.addEventListener('message', receiveState)
  sendQuickReaderControl({ action: 'ready' })
})
onBeforeUnmount(() => {
  window.removeEventListener('message', receiveState)
  sendQuickReaderControl({ action: 'flush' })
})
</script>

<style scoped>
.quick-reader-window-settings {
  display: grid;
  gap: 14px;
  padding: 20px 0;
  border-bottom: 1px solid var(--blue-line, var(--border-color-light));
}
h3 {
  margin: 0;
  font-size: 14px;
}
label {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}
input {
  width: 100%;
  accent-color: var(--reader-control-accent, var(--primary));
}
p {
  margin: 0;
  color: var(--blue-muted, var(--text-secondary));
  font-size: 12px;
  line-height: 1.7;
}
input:focus-visible {
  outline: 2px solid var(--reader-control-accent, var(--primary));
  outline-offset: 2px;
}
</style>
