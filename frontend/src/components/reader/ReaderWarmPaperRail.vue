<template>
  <aside class="warm-tool-rail" aria-label="纸感阅读工具">
    <button
      class="warm-brand"
      type="button"
      aria-label="返回首页"
      @click="$router.push('/')"
    >
      <ReaderBlueGrayIcon name="book" />
      <strong>汗牛充栋</strong>
      <small>私人图书馆</small>
    </button>
    <nav aria-label="阅读工具">
      <button
        v-for="tool in tools"
        :key="tool.value"
        type="button"
        :class="{ active: tool.value === activePanel || (tool.value === 'settings' && settingsOpen) }"
        :aria-pressed="tool.value === activePanel || (tool.value === 'settings' && settingsOpen)"
        :aria-label="tool.label"
        @click="activate(tool.value)"
      >
        <ReaderBlueGrayIcon :name="tool.icon" />
        <span>{{ tool.label }}</span>
      </button>
    </nav>
    <img
      class="warm-botanical"
      src="../../assets/reader-warm-paper-botanical.svg"
      alt=""
      aria-hidden="true"
    />
  </aside>
</template>

<script setup lang="ts">
import ReaderBlueGrayIcon from './ReaderBlueGrayIcon.vue'

defineProps<{ activePanel: string; settingsOpen: boolean }>()
const emit = defineEmits<{
  panel: [value: string]
  settings: []
  fullscreen: []
}>()
const tools = [
  { value: 'toc', label: '目录', icon: 'menu' },
  { value: 'search', label: '搜索', icon: 'search' },
  { value: 'bookmarks', label: '书签', icon: 'bookmark' },
  { value: 'highlights', label: '划线', icon: 'highlight' },
  { value: 'settings', label: '设置', icon: 'settings' },
  { value: 'fullscreen', label: '全屏', icon: 'fullscreen' },
]
const activate = (value: string) => {
  if (value === 'settings') emit('settings')
  else if (value === 'fullscreen') emit('fullscreen')
  else emit('panel', value)
}
</script>
