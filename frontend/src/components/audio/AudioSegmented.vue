<template>
  <div class="audio-segmented" role="radiogroup" :aria-label="label" @keydown="keydown">
    <div class="audio-segmented-track" :style="{ '--count': options.length, '--index': index }">
      <span class="audio-segmented-thumb" aria-hidden="true" />
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        role="radio"
        :aria-checked="modelValue === option.value"
        :tabindex="modelValue === option.value ? 0 : -1"
        @click="emit('update:modelValue', option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick } from 'vue'
const props = defineProps<{
  modelValue: string
  label: string
  options: Array<{ value: string; label: string }>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const index = computed(() =>
  Math.max(
    0,
    props.options.findIndex((item) => item.value === props.modelValue)
  )
)
async function keydown(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? props.options.length - 1
        : (index.value + (event.key === 'ArrowRight' ? 1 : -1) + props.options.length) %
          props.options.length
  emit('update:modelValue', props.options[next].value)
  const group = event.currentTarget as HTMLElement
  await nextTick()
  const button = group.querySelector<HTMLElement>('[aria-checked="true"]')
  button?.focus()
  button?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}
</script>
<style scoped>
.audio-segmented {
  overflow-x: auto;
  border-radius: 14px;
  background: var(--primary-alpha-10);
  padding: 3px;
}
.audio-segmented-track {
  position: relative;
  display: grid;
  grid-template-columns: repeat(var(--count), minmax(90px, 1fr));
  min-width: max-content;
  isolation: isolate;
}
.audio-segmented-thumb {
  position: absolute;
  inset: 0 auto 0 0;
  width: calc(100% / var(--count));
  border-radius: 11px;
  background: var(--surface-elevated, white);
  transform: translateX(calc(var(--index) * 100%));
  transition: transform 0.22s ease;
  z-index: -1;
}
button {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  padding: 10px 15px;
  cursor: pointer;
  white-space: nowrap;
  font: inherit;
}
button[aria-checked='true'] {
  color: var(--primary);
  font-weight: 600;
}
button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
  border-radius: 11px;
}
@media (prefers-reduced-motion: reduce) {
  .audio-segmented-thumb {
    transition: none;
  }
}
</style>
