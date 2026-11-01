<template>
  <div
    ref="group"
    class="reader-appearance-picker"
    role="radiogroup"
    aria-label="正式阅读器界面样式"
    @keydown="handleKeydown"
  >
    <span class="appearance-picker-indicator" :style="indicatorStyle" aria-hidden="true"></span>
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-label="option.label"
      :aria-checked="modelValue === option.value"
      :tabindex="modelValue === option.value ? 0 : -1"
      @click="$emit('update:modelValue', option.value)"
    >
      <span class="appearance-preview" :data-appearance="option.value" aria-hidden="true">
        <span class="appearance-preview-bar"></span>
        <span class="appearance-preview-page"><i></i><i></i><i></i></span>
      </span>
      <span class="appearance-picker-copy">
        <strong>{{ option.label }}</strong>
        <small v-if="option.description">{{ option.description }}</small>
      </span>
      <span class="appearance-picker-check" aria-hidden="true">{{ modelValue === option.value ? '✓' : '' }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  modelValue: string
  options: Array<{ value: string; label: string; description?: string }>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const group = ref<HTMLElement>()
const bounds = ref({ x: 0, y: 0, width: 0, height: 0 })
const indicatorStyle = computed(() => ({
  transform: `translate(${bounds.value.x}px, ${bounds.value.y}px)`,
  width: `${bounds.value.width}px`,
  height: `${bounds.value.height}px`,
  opacity: bounds.value.width ? 1 : 0,
}))
let observer: ResizeObserver | undefined
const measure = () => {
  const selected = group.value?.querySelector<HTMLElement>('[aria-checked="true"]')
  if (!selected) return
  bounds.value = {
    x: selected.offsetLeft,
    y: selected.offsetTop,
    width: selected.offsetWidth,
    height: selected.offsetHeight,
  }
}
watch(() => [props.modelValue, props.options], () => nextTick(measure), { deep: true })
onMounted(() => {
  observer = new ResizeObserver(measure)
  if (group.value) observer.observe(group.value)
  measure()
})
onBeforeUnmount(() => observer?.disconnect())

const handleKeydown = async (event: KeyboardEvent) => {
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
  if (!props.options.length) return
  event.preventDefault()
  const current = Math.max(0, props.options.findIndex(option => option.value === props.modelValue))
  const columns = getComputedStyle(group.value!).gridTemplateColumns.split(' ').length
  const step = event.key === 'ArrowDown' ? columns : event.key === 'ArrowUp' ? -columns
    : event.key === 'ArrowLeft' ? -1 : 1
  const index = event.key === 'Home' ? 0 : event.key === 'End' ? props.options.length - 1
    : (current + step + props.options.length) % props.options.length
  const reader = group.value?.closest('.reader-view')
  emit('update:modelValue', props.options[index].value)
  await nextTick()
  const selected = group.value?.querySelector<HTMLElement>('[aria-checked="true"]')
    || reader?.querySelector<HTMLElement>('.reader-appearance-picker [aria-checked="true"]')
  selected?.focus({ preventScroll: true })
}
</script>

<style scoped>
.reader-appearance-picker {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding: 6px;
  border: 1px solid var(--blue-line, var(--border-color-light, #dce5ef));
  border-radius: 16px;
  background: var(--blue-soft, var(--bg-secondary, #f4f7fb));
  isolation: isolate;
  container-type: inline-size;
}
.appearance-picker-indicator {
  position: absolute;
  top: 0;
  left: 0;
  z-index: -1;
  border: 1px solid color-mix(in srgb, var(--reader-control-accent, var(--primary, #1264cf)) 40%, transparent);
  border-radius: 11px;
  background: var(--blue-surface, var(--surface-elevated, #fff));
  box-sizing: border-box;
  transition: transform 220ms cubic-bezier(.2, .8, .2, 1), width 220ms, height 220ms;
  pointer-events: none;
}
button {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 76px;
  padding: 10px 8px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: var(--blue-ink, var(--text-primary, #263549));
  font: inherit;
  text-align: left;
  cursor: pointer;
}
button:hover {
  background: color-mix(in srgb, var(--reader-control-accent, var(--primary, #1264cf)) 5%, transparent);
}
button[aria-checked='true'] strong,
.appearance-picker-check {
  color: var(--reader-control-accent, var(--primary, #1264cf));
}
button:focus-visible {
  outline: 2px solid var(--reader-control-accent, var(--primary, #1264cf));
  outline-offset: -2px;
}
.appearance-picker-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}
strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
small {
  color: var(--blue-muted, var(--text-tertiary, #7183a0));
  font-size: 10px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.appearance-picker-check {
  width: 12px;
  margin-left: auto;
  flex-shrink: 0;
  font-size: 13px;
}
.appearance-preview {
  --preview-paper: #fff;
  --preview-bar: #dce4e9;
  position: relative;
  display: block;
  width: 34px;
  height: 42px;
  flex: 0 0 34px;
  overflow: hidden;
  border: 1px solid #00000014;
  border-radius: 5px;
  background: var(--preview-paper);
}
.appearance-preview-bar {
  display: block;
  height: 7px;
  background: var(--preview-bar);
}
.appearance-preview-page {
  display: grid;
  gap: 4px;
  padding: 9px 6px;
}
.appearance-preview-page i {
  height: 2px;
  background: #89979170;
}
.appearance-preview-page i:last-child {
  width: 65%;
}
[data-appearance='readingRoom'] {
  --preview-paper: #f2e9da;
  --preview-bar: #b2a58b;
}
[data-appearance='trialReader'] {
  --preview-paper: #fcfcfa;
  --preview-bar: #d9dfd9;
}
[data-appearance='blueGray'] {
  --preview-paper: #f1f6fc;
  --preview-bar: #91b9e2;
}
[data-appearance='warmPaper'] {
  --preview-paper: #faf5e9;
  --preview-bar: #b8c5a3;
}
[data-appearance='spreadsheet'],
[data-appearance='spreadsheetWarm'] {
  --preview-bar: #217346;
  background-image: linear-gradient(#a5b5a333 1px, transparent 1px),
    linear-gradient(90deg, #a5b5a333 1px, transparent 1px);
  background-size: 7px 7px;
}
[data-appearance='spreadsheetWarm'] {
  --preview-paper: #faf6eb;
  --preview-bar: #4b7651;
}
@container (max-width: 250px) {
  .appearance-preview {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .appearance-picker-indicator {
    transition: none;
  }
}
</style>
