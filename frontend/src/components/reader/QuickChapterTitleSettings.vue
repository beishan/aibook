<template>
  <section class="quick-title-settings" aria-label="小窗章节标题设置">
    <h3>章节标题</h3>
    <ReaderBlueGraySegmented
      :model-value="mode"
      :options="styles"
      label="章节标题样式"
      @update:model-value="$emit('style', $event as ChapterTitleStyle)"
    />
    <label class="quick-title-size">
      <span>独立字号</span>
      <input
        type="range"
        min="12"
        max="40"
        step="1"
        :value="resolvedFontSize"
        @input="$emit('size', Number(($event.target as HTMLInputElement).value))"
      />
      <output>{{ fontSize == null && mode === 'original' ? '默认' : `${resolvedFontSize}px` }}</output>
    </label>
    <div class="quick-title-footer">
      <span>{{ fontSize == null ? '字号跟随所选样式' : '标题字号独立于正文' }}</span>
      <button type="button" :disabled="fontSize == null" @click="$emit('size', null)">
        跟随样式
      </button>
    </div>
    <p>仅影响小窗正文中的章节标题；正文同款默认跟随正文字号。</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ReaderBlueGraySegmented from './ReaderBlueGraySegmented.vue'
import { chapterTitleFontSize, type ChapterTitleStyle } from '@/reader/chapterTitle'

const props = defineProps<{
  mode: ChapterTitleStyle
  fontSize: number | null
  bodyFontSize: number
}>()
defineEmits<{ style: [value: ChapterTitleStyle]; size: [value: number | null] }>()
const styles = [
  { value: 'original', label: '默认大标题' },
  { value: 'compact', label: '简洁小标题' },
  { value: 'body', label: '正文同款' },
  { value: 'divider', label: '横线居中' },
]
const resolvedFontSize = computed(() => chapterTitleFontSize({ style: props.mode, fontSize: props.fontSize, bodyFontSize: props.bodyFontSize }))
</script>

<style scoped>
.quick-title-settings {
  padding: 18px 0;
  border-bottom: 1px solid var(--blue-line, var(--border-color-light));
}

h3 {
  margin: 0 0 12px;
  font-size: 14px;
}

.quick-title-size {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
  font-size: 13px;
}

input {
  flex: 1;
  min-width: 0;
  accent-color: var(--primary);
}

output {
  min-width: 40px;
  text-align: right;
}

.quick-title-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
}

button {
  padding: 5px 8px;
  border: 1px solid var(--border-color-light);
  border-radius: 8px;
  background: var(--surface-card);
  color: var(--primary);
  cursor: pointer;
}

button:disabled {
  opacity: .5;
  cursor: default;
}

button:focus-visible,
input:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
