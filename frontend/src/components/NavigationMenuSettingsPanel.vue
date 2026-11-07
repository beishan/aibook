<template>
  <section class="card glass navigation-order-settings">
    <h2>导航菜单</h2>
    <p>调整顶部导航、侧边栏和手机导航的顺序。“工具”下的功能一起移动，Dock 使用自己的导航配置。</p>
    <ol
      class="navigation-order-list"
      @pointermove="dragMove"
      @pointerup="endDrag"
      @pointercancel="cancelDrag"
      @lostpointercapture="cancelDrag"
    >
      <li v-for="(path, index) in draft" :key="path" :data-path="path" :class="{ dragging: dragPath === path }">
        <div class="navigation-order-item">
          <button
            type="button"
            class="navigation-drag-handle"
            :disabled="saving"
            :aria-label="`拖拽调整${label(path)}顺序，或使用上下方向键`"
            title="拖拽调整顺序，也可使用上下方向键"
            @pointerdown="startDrag($event, path)"
            @keydown.up.prevent="move(draft.indexOf(path), -1)"
            @keydown.down.prevent="move(draft.indexOf(path), 1)"
          >⋮⋮</button>
          <span>{{ label(path) }}</span>
        </div>
        <div>
          <el-button :disabled="saving || !!dragPath || index === 0" :aria-label="`上移${label(path)}`" @click="move(index, -1)">上移</el-button>
          <el-button :disabled="saving || !!dragPath || index === draft.length - 1" :aria-label="`下移${label(path)}`" @click="move(index, 1)">下移</el-button>
        </div>
      </li>
    </ol>
    <div class="navigation-order-actions">
      <el-button :disabled="saving || !!dragPath" @click="draft = defaultNavigationMenuOrder()">恢复默认</el-button>
      <el-button type="primary" :disabled="!!dragPath" :loading="saving" @click="save">保存菜单顺序</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { usePreferencesStore } from '@/stores/preferences'
import { defaultNavigationMenuOrder, navigationMenuOptions } from '@/utils/navigationMenu'
import { message } from '@/utils/message'
const preferences = usePreferencesStore()
const draft = ref(defaultNavigationMenuOrder())
const saving = ref(false)
const dragPath = ref<string | null>(null)
let dragPointer: number | null = null
let originalOrder: string[] = []
watch(() => preferences.navigationMenuOrder, value => { draft.value = [...value] }, { immediate: true })
const label = (path: string) => navigationMenuOptions.find(item => item.path === path)?.title || path
function move(index: number, direction: number) {
  const target = index + direction
  if (saving.value || target < 0 || target >= draft.value.length) return
  const next = [...draft.value]
  const moving = next[index]
  next[index] = next[target]
  next[target] = moving
  draft.value = next
}
function startDrag(event: PointerEvent, path: string) {
  if (saving.value || dragPath.value || event.button !== 0) return
  event.preventDefault()
  dragPath.value = path
  dragPointer = event.pointerId
  originalOrder = [...draft.value]
  const list = (event.currentTarget as HTMLElement).closest<HTMLOListElement>('.navigation-order-list')
  list?.setPointerCapture(event.pointerId)
}
function dragMove(event: PointerEvent) {
  if (event.pointerId !== dragPointer || !dragPath.value) return
  const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLLIElement>('.navigation-order-list li')
  const targetPath = target?.dataset.path
  const from = draft.value.indexOf(dragPath.value)
  const to = targetPath ? draft.value.indexOf(targetPath) : -1
  if (from < 0 || to < 0 || from === to) return
  const next = [...draft.value]
  next.splice(from, 1)
  next.splice(to, 0, dragPath.value)
  draft.value = next
}
function endDrag(event: PointerEvent) {
  if (event.pointerId !== dragPointer) return
  dragPointer = null
  dragPath.value = null
  originalOrder = []
}
function cancelDrag(event: PointerEvent) {
  if (event.pointerId !== dragPointer) return
  draft.value = originalOrder
  dragPointer = null
  dragPath.value = null
  originalOrder = []
}
async function save() {
  if (saving.value) return
  saving.value = true
  try {
    await preferences.saveNavigationMenuOrder([...draft.value])
    message.success('导航菜单顺序已保存到账户')
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存导航顺序失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.navigation-order-settings {
  padding: 24px;
}
.navigation-order-settings p {
  margin: 12px 0 20px;
  color: var(--text-secondary);
}
.navigation-order-list {
  display: grid;
  gap: 10px;
  list-style: none;
  padding: 0;
}
.navigation-order-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px;
}
.navigation-order-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.navigation-drag-handle {
  min-width: 40px;
  min-height: 40px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 20px;
  cursor: grab;
  touch-action: none;
}
.navigation-drag-handle:active { cursor: grabbing; }
.navigation-drag-handle:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.navigation-order-list li.dragging { border-color: var(--primary); background: var(--primary-alpha-10); }
.navigation-order-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
</style>
