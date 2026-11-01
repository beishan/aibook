<template>
  <header class="sheet-header">
    <div class="sheet-titlebar">
      <span class="sheet-app-icon" aria-hidden="true">▦</span>
      <strong :title="title">汗牛充栋 · {{ title }}.xlsx</strong>
      <button type="button" @click="$emit('panel', 'search')">⌕ 搜索本书</button>
      <button type="button" @click="$emit('close')">{{ quickWindow ? '关闭小窗 ×' : '返回书库' }}</button>
      <QuickReaderDrag v-if="quickWindow" />
    </div>
    <nav class="sheet-menus" aria-label="表格阅读功能区">
      <button type="button" @click="$emit('settings')">文件与设置</button>
      <ReaderBlueGraySegmented v-model="ribbon" :options="ribbons" label="表格功能区" />
    </nav>
    <div class="sheet-ribbon" @mousedown="preserveSelection">
      <fieldset class="sheet-fonts" :disabled="fixedLayout">
        <legend>字体与排版</legend>
        <select :value="typography.fontFamily" aria-label="正文字体"
          @change="$emit('font', ($event.target as HTMLSelectElement).value)">
          <option v-for="font in fonts" :key="font.value" :value="font.value">{{ font.label }}</option>
        </select>
        <input type="number" min="12" max="28" :value="typography.fontSize" aria-label="正文字号"
          @change="changeSize" />
        <select :value="typography.lineHeight" aria-label="正文行距"
          @change="$emit('typography', { lineHeight: Number(($event.target as HTMLSelectElement).value) })">
          <option v-for="height in [1.2, 1.5, 1.8, 2, 2.5]" :key="height" :value="height">行距 {{ height }}</option>
        </select>
      </fieldset>
      <fieldset v-if="ribbon === 'read'">
        <legend>章节导航</legend>
        <button type="button" :disabled="!canPrevious" @click="$emit('previous')">
          <ReaderBlueGrayIcon name="left" />
          {{ previousLabel }}
        </button>
        <button type="button" :disabled="!canNext" @click="$emit('next')">
          <ReaderBlueGrayIcon name="right" />
          {{ nextLabel }}
        </button>
        <button type="button" @click="$emit('panel', 'toc')">
          <ReaderBlueGrayIcon name="menu" />
          目录
        </button>
        <button type="button" @click="$emit('bookmark')">
          <ReaderBlueGrayIcon name="bookmark" />
          添加书签
        </button>
      </fieldset>
      <fieldset v-else-if="ribbon === 'notes'">
        <legend>选文与批注</legend>
        <button type="button" :disabled="!selection" @click="$emit('highlight')">
          <ReaderBlueGrayIcon name="highlight" />
          高亮
        </button>
        <button type="button" :disabled="!selection" @click="$emit('note')">
          <ReaderBlueGrayIcon name="note" />
          添加批注
        </button>
        <button type="button" :disabled="!selection" @click="$emit('copy')">
          <ReaderBlueGrayIcon name="copy" />
          复制原文
        </button>
        <button type="button" @click="$emit('panel', 'notes')">
          <ReaderBlueGrayIcon name="menu" />
          全部批注
        </button>
      </fieldset>
      <fieldset v-else>
        <legend>阅读视图</legend>
        <button type="button" @click="$emit('settings')">
          <b aria-hidden="true">Aa</b>
          主题与排版
        </button>
        <button type="button" @click="$emit('panel', 'search')">
          <ReaderBlueGrayIcon name="search" />
          书内搜索
        </button>
        <button type="button" @click="$emit('fullscreen')">
          <ReaderBlueGrayIcon name="fullscreen" />
          切换全屏
        </button>
      </fieldset>
      <fieldset class="sheet-common-actions">
        <legend>阅读工具</legend>
        <button type="button" @click="$emit('panel', 'highlights')">
          <ReaderBlueGrayIcon name="highlight" />
          划线与摘录
        </button>
        <button type="button" @click="$emit('settings')">
          <ReaderBlueGrayIcon name="settings" />
          阅读设置
        </button>
      </fieldset>
    </div>
    <div class="sheet-formula" aria-label="阅读定位栏">
      <span class="sheet-cell-address">正文</span>
      <i aria-hidden="true">ƒx</i>
      <span class="sheet-formula-text" :title="selection || chapter">{{ selection || chapter }}</span>
    </div>
    <div class="sheet-column-ruler" aria-hidden="true">
      <span>◩</span><span v-for="letter in 'ABCDEFGHIJKLMN'" :key="letter">{{ letter }}</span>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ReaderTypography } from '@/stores/preferences'
import QuickReaderDrag from './QuickReaderDrag.vue'
import ReaderBlueGrayIcon from './ReaderBlueGrayIcon.vue'
import ReaderBlueGraySegmented from './ReaderBlueGraySegmented.vue'

defineProps<{
  title: string
  chapter: string
  selection: string
  typography: ReaderTypography
  fonts: Array<{ value: string; label: string }>
  quickWindow: boolean
  fixedLayout: boolean
  canPrevious: boolean
  canNext: boolean
  previousLabel: string
  nextLabel: string
}>()
const emit = defineEmits<{
  close: []
  settings: []
  panel: [panel: string]
  typography: [patch: Partial<ReaderTypography>]
  font: [value: string]
  previous: []
  next: []
  bookmark: []
  highlight: []
  note: []
  copy: []
  fullscreen: []
}>()
const ribbon = ref('read')
const ribbons = [
  { value: 'read', label: '阅读' },
  { value: 'notes', label: '批注' },
  { value: 'view', label: '视图' },
]
const changeSize = (event: Event) => {
  const input = event.target as HTMLInputElement
  const size = Number(input.value)
  if (input.value && Number.isFinite(size)) {
    emit('typography', { fontSize: Math.min(28, Math.max(12, Math.round(size))) })
  }
}
// Toolbar buttons must keep a selected passage available to the annotation actions.
const preserveSelection = (event: MouseEvent) => {
  if ((event.target as HTMLElement).closest('button')) event.preventDefault()
}
</script>
