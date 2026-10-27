<template>
  <div
    class="blue-segmented"
    :style="{ '--segment-count': options.length, '--segment-index': selectedIndex }"
    role="radiogroup"
    :aria-label="label"
    @keydown="handleKeydown"
  >
    <div class="blue-segmented-track">
      <span class="blue-segmented-indicator" aria-hidden="true"></span>
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        role="radio"
        :aria-checked="modelValue === option.value"
        :tabindex="modelValue === option.value ? 0 : -1"
        @click="$emit('update:modelValue', option.value)"
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
  options: Array<{ value: string; label: string }>
  label: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const selectedIndex = computed(() => Math.max(0, props.options.findIndex(option => option.value === props.modelValue)))

const handleKeydown = async (event: KeyboardEvent) => {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  const index = event.key === 'Home' ? 0
    : event.key === 'End' ? props.options.length - 1
      : (selectedIndex.value + (event.key === 'ArrowRight' ? 1 : -1) + props.options.length) % props.options.length
  emit('update:modelValue', props.options[index].value)
  await nextTick()
  const selected = group.querySelector<HTMLElement>('[aria-checked="true"]')
  selected?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  selected?.focus()
}
</script>

<style scoped>
.blue-segmented {
  overflow-x: auto;
  border: 1px solid var(--blue-line, #dce5ef);
  border-radius: 11px;
  background: var(--blue-soft, #f4f7fb);
}

.blue-segmented-track {
  position: relative;
  display: grid;
  grid-template-columns: repeat(var(--segment-count), minmax(64px, 1fr));
  width: max(100%, calc(var(--segment-count) * 64px + 6px));
  padding: 3px;
  box-sizing: border-box;
  isolation: isolate;
}

.blue-segmented-indicator {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 3px;
  width: calc((100% - 6px) / var(--segment-count));
  border-radius: 8px;
  background: #1264cf;
  transform: translateX(calc(var(--segment-index) * 100%));
  transition: transform 220ms cubic-bezier(.2, .8, .2, 1);
  pointer-events: none;
  z-index: -1;
}

button {
  min-height: 34px;
  padding: 7px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--blue-muted, #6e7f9b);
  font: inherit;
  font-size: 14px;
  white-space: nowrap;
  cursor: pointer;
}

button[aria-checked='true'] {
  color: #fff;
}

button:focus-visible {
  outline: 2px solid #1264cf;
  outline-offset: -2px;
}

@media (prefers-reduced-motion: reduce) {
  .blue-segmented-indicator {
    transition: none;
  }
}
</style>
