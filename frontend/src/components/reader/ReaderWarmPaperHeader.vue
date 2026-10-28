<template>
  <header class="warm-reader-header">
    <button
      class="warm-return"
      type="button"
      :aria-label="quickWindow ? '关闭小窗' : '返回书库'"
      @click="$emit('back')"
    >
      <ReaderBlueGrayIcon name="left" />
      <span>{{ quickWindow ? '关闭' : '返回' }}</span>
    </button>
    <span class="warm-header-divider" aria-hidden="true"></span>
    <strong class="warm-header-book" :title="title">{{ title }}</strong>
    <span class="warm-header-chapter" :title="chapter">／ {{ chapter }}</span>
    <nav aria-label="正文工具">
      <button
        class="warm-aa"
        type="button"
        aria-label="阅读设置"
        @click="$emit('settings')"
      >
        Aa
      </button>
      <button
        type="button"
        :aria-label="night ? '切换日间' : '切换夜间'"
        :aria-pressed="night"
        @click="$emit('night')"
      >
        <ReaderBlueGrayIcon :name="night ? 'sun' : 'moon'" />
      </button>
      <button
        v-if="fullscreen"
        type="button"
        aria-label="退出全屏"
        @click="$emit('fullscreen')"
      >
        <ReaderBlueGrayIcon name="fullscreen" />
      </button>
      <details class="warm-more-menu">
        <summary aria-label="更多阅读操作">
          <ReaderBlueGrayIcon name="more" />
        </summary>
        <div>
          <button type="button" @click="choose('notes')">
            <ReaderBlueGrayIcon name="note" />
            查看笔记
          </button>
          <button type="button" @click="choose('download')">
            <ReaderBlueGrayIcon name="download" />
            下载书籍
          </button>
        </div>
      </details>
    </nav>
  </header>
</template>

<script setup lang="ts">
import ReaderBlueGrayIcon from './ReaderBlueGrayIcon.vue'

defineProps<{
  title: string
  chapter: string
  quickWindow: boolean
  fullscreen: boolean
  night: boolean
}>()
const emit = defineEmits<{
  back: []
  settings: []
  night: []
  notes: []
  download: []
  fullscreen: []
}>()
const choose = (value: 'notes' | 'download') => {
  if (value === 'notes') emit('notes')
  else emit('download')
  document.querySelector('.warm-more-menu')?.removeAttribute('open')
}
</script>
