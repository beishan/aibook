<template>
  <el-popover
    v-model:visible="open"
    trigger="click"
    placement="bottom-end"
    :width="248"
    :offset="8"
    :show-arrow="false"
    :persistent="false"
    popper-class="shelf-book-menu-popper"
  >
    <template #reference>
      <button
        ref="trigger"
        type="button"
        class="book-menu-trigger"
        :aria-label="title + '的操作菜单'"
        :aria-expanded="open"
        aria-haspopup="menu"
        title="书籍操作"
        @click.stop
        @keydown="handleTriggerKeydown"
      >⋯</button>
    </template>
    <div class="shelf-menu-heading" @click.stop>
      <div>
        <small>书籍操作</small>
        <strong :title="title">{{ title }}</strong>
      </div>
      <button type="button" aria-label="关闭操作菜单" @click="close(true)">
        <el-icon><Close /></el-icon>
      </button>
    </div>
    <div
      ref="menu"
      class="shelf-menu-items"
      role="menu"
      :aria-label="title + '的书籍操作'"
      @click.stop
      @keydown="handleKeydown"
    >
      <ShelfBookActions
        :book="book"
        expanded
        :hide-shelf="hideShelf"
        :disabled="disabled"
        @action="act"
      />
      <div class="shelf-menu-divider" role="separator"></div>
      <slot :close="close"></slot>
      <div class="shelf-menu-divider" role="separator"></div>
      <button
        type="button"
        role="menuitem"
        class="shelf-menu-action menu-danger"
        :disabled="disabled"
        @click="act('delete', book)"
      >
        <el-icon><Delete /></el-icon>
        <span>移入回收站</span>
      </button>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Close, Delete } from '@element-plus/icons-vue'
import type { Book } from '@/stores/book'
import ShelfBookActions from './ShelfBookActions.vue'

defineProps<{ book: Book; title: string; hideShelf?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ action: [command: string, book: Book] }>()
const open = ref(false)
const trigger = ref<HTMLButtonElement>()
const menu = ref<HTMLElement>()
const close = (restore = false) => {
  open.value = false
  if (restore) void nextTick(() => trigger.value?.focus({ preventScroll: true }))
}
const act = (command: string, book: Book) => {
  close()
  emit('action', command, book)
}
const focusFirst = () => {
  if (menu.value) menu.value.scrollTop = 0
  menu.value?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus({ preventScroll: true })
}
const closeOnEscape = (event: KeyboardEvent) => {
  if (event.key !== 'Escape' || !open.value) return
  event.preventDefault()
  event.stopPropagation()
  close(true)
}
watch(open, async visible => {
  document.removeEventListener('keydown', closeOnEscape, true)
  if (!visible) return
  document.addEventListener('keydown', closeOnEscape, true)
  await nextTick()
  if (open.value) focusFirst()
}, { flush: 'post' })
onBeforeUnmount(() => document.removeEventListener('keydown', closeOnEscape, true))
const handleTriggerKeydown = async (event: KeyboardEvent) => {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  event.stopPropagation()
  open.value = true
  await nextTick()
  const buttons = Array.from(menu.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') || [])
  const selected = event.key === 'End' || event.key === 'ArrowUp' ? buttons.at(-1) : buttons[0]
  selected?.focus({ preventScroll: true })
  selected?.scrollIntoView({ block: 'nearest' })
}
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    event.stopPropagation()
    close(true)
    return
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  const buttons = Array.from(menu.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') || [])
  if (!buttons.length) return
  event.preventDefault()
  event.stopPropagation()
  const current = buttons.indexOf(document.activeElement as HTMLButtonElement)
  const index = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1
    : (current + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length
  buttons[index].focus({ preventScroll: true })
  buttons[index].scrollIntoView({ block: 'nearest' })
}
</script>

<style>
.el-popover.el-popper.shelf-book-menu-popper {
  max-width: calc(100vw - 24px);
  padding: 6px;
  border: 1px solid var(--border-color-light, #e2e8e3);
  border-radius: 14px;
  background: var(--surface-elevated, #fff);
  color: var(--text-primary, #26372e);
  box-shadow: 0 10px 32px #142c251c;
  box-sizing: border-box;
}
.shelf-menu-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 10px 12px;
  border-bottom: 1px solid var(--border-color-light, #e2e8e3);
}
.shelf-menu-heading > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.shelf-menu-heading small {
  color: var(--text-tertiary, #7c8980);
  font-size: 11px;
}
.shelf-menu-heading strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}
.shelf-menu-heading > button {
  display: grid;
  flex: 0 0 28px;
  height: 28px;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary, #63776c);
  cursor: pointer;
}
.shelf-menu-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: min(440px, calc(100dvh - 138px));
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 6px 0;
  scrollbar-width: thin;
}
.shelf-menu-items .common-action,
.shelf-menu-items .shelf-menu-action {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex: 0 0 auto;
  gap: 10px;
  width: 100%;
  height: auto;
  min-height: 36px;
  padding: 8px 12px;
  margin: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-primary, #26372e);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
}
.shelf-menu-items .el-dropdown {
  display: block;
}
.shelf-menu-items .action-icon,
.shelf-menu-items .el-icon {
  display: inline-grid;
  flex: 0 0 18px;
  width: 18px;
  place-items: center;
  color: var(--text-secondary, #63776c);
  font-size: 16px;
}
.shelf-menu-items button:hover:not(:disabled),
.shelf-menu-heading > button:hover {
  background: var(--surface-hover, #edf4ee);
  color: var(--primary, #347452);
}
.shelf-menu-items button:focus-visible,
.shelf-menu-heading > button:focus-visible {
  outline: 2px solid var(--primary, #347452);
  outline-offset: -2px;
}
.shelf-menu-items button:disabled {
  opacity: .45;
  cursor: not-allowed;
}
.shelf-menu-items .menu-danger,
.shelf-menu-items .menu-danger .el-icon {
  color: var(--danger, #c55252);
}
.shelf-menu-divider {
  height: 1px;
  flex: 0 0 1px;
  margin: 5px 10px;
  background: var(--border-color-light, #e2e8e3);
}

.shelf-book-menu-popper .shelf-menu-items .common-action.active {
  color: var(--primary, #347452);
  background: var(--surface-hover, #edf4ee);
}
.shelf-menu-items .common-action.active .el-icon {
  color: inherit;
}
</style>

<style scoped>
.book-menu-trigger {
  position: absolute;
  z-index: 4;
  top: 7px;
  right: 7px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid color-mix(in srgb, var(--text-primary) 12%, transparent);
  border-radius: 8px;
  background: var(--surface-card, #fff);
  color: var(--text-primary, #26372e);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}
.book-menu-trigger:hover {
  background: var(--surface-hover);
}
.book-menu-trigger:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
</style>
