<template>
  <button
    v-if="route.query.independentWindow !== '1'"
    class="quick-reader-toolbar-drag"
    type="button"
    aria-label="拖动小窗：拖动调整位置，也可使用方向键"
    title="拖动小窗，方向键微调位置"
    @pointerdown="start"
    @pointermove="move"
    @pointerup="stop"
    @pointercancel="stop"
    @lostpointercapture="stop"
    @keydown="moveWithKeyboard"
  >
    <span aria-hidden="true">⠿</span>
  </button>
</template>

<script setup lang="ts">
import { onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { sendQuickReaderControl } from '@/reader/quickWindowBridge'

const route = useRoute()

let pointer: { id: number; x: number; y: number } | null = null
const start = (event: PointerEvent) => {
  if (event.button !== 0 || pointer) return
  event.preventDefault()
  event.stopPropagation()
  const handle = event.currentTarget as HTMLElement
  handle.setPointerCapture(event.pointerId)
  pointer = { id: event.pointerId, x: event.screenX, y: event.screenY }
}
const move = (event: PointerEvent) => {
  if (!pointer || pointer.id !== event.pointerId) return
  sendQuickReaderControl({ action: 'move', dx: event.screenX - pointer.x, dy: event.screenY - pointer.y })
  pointer = { id: event.pointerId, x: event.screenX, y: event.screenY }
}
const finish = () => {
  if (!pointer) return
  pointer = null
  sendQuickReaderControl({ action: 'move-end' })
}
const stop = (event: PointerEvent) => {
  if (pointer?.id !== event.pointerId) return
  finish()
  const handle = event.currentTarget as HTMLElement
  if (handle.hasPointerCapture(event.pointerId)) handle.releasePointerCapture(event.pointerId)
}
const moveWithKeyboard = (event: KeyboardEvent) => {
  const step = event.shiftKey ? 48 : 16
  const deltas: Record<string, [number, number]> = {
    ArrowRight: [step, 0], ArrowLeft: [-step, 0],
    ArrowDown: [0, step], ArrowUp: [0, -step],
  }
  const delta = deltas[event.key]
  if (!delta) return
  event.preventDefault()
  event.stopPropagation()
  sendQuickReaderControl({ action: 'move', dx: delta[0], dy: delta[1], persist: true })
}
onBeforeUnmount(finish)
</script>

<style scoped>
button.quick-reader-toolbar-drag {
  display: inline-grid;
  place-items: center;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  min-height: 32px;
  margin-left: auto;
  padding: 0;
  border: 1px solid currentColor;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: move;
  touch-action: none;
  user-select: none;
  opacity: .75;
}
button.quick-reader-toolbar-drag:hover {
  background: #80808015;
  opacity: 1;
}
button.quick-reader-toolbar-drag:active {
  cursor: grabbing;
}
button.quick-reader-toolbar-drag:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}
span {
  font: 24px/1 sans-serif;
}
</style>
