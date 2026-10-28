<template>
  <div class="blue-settings-content" :class="{ 'paper-settings-content': paper }">
    <section class="blue-type-settings">
      <label class="blue-setting-row">
        <span>字体</span>
        <select
          :value="typography.fontFamily"
          @change="$emit('font', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="font in fonts" :key="font.value" :value="font.value">
            {{ font.value === 'default' ? '系统宋体' : font.label }}
          </option>
        </select>
      </label>
      <div class="blue-setting-row">
        <span>字体大小</span>
        <div class="blue-size-stepper">
          <button
            type="button"
            aria-label="减小字号"
            :disabled="typography.fontSize <= 12"
            @click="$emit('typography', { fontSize: typography.fontSize - 1 })"
          >
            −
          </button>
          <output aria-live="polite">
            {{ typography.fontSize }}
            <small>px</small>
          </output>
          <button
            type="button"
            aria-label="增大字号"
            :disabled="typography.fontSize >= 28"
            @click="$emit('typography', { fontSize: typography.fontSize + 1 })"
          >
            ＋
          </button>
        </div>
      </div>
      <label class="blue-setting-row">
        <span>行间距</span>
        <input
          :value="typography.lineHeight"
          type="range"
          min="1.2"
          max="2.5"
          step="0.1"
          @input="$emit('typography', { lineHeight: Number(($event.target as HTMLInputElement).value) })"
        />
        <output>{{ typography.lineHeight }}</output>
      </label>
    </section>
    <section v-if="paper && !fixedLayout" class="paper-alignment-settings">
      <h3>对齐方式</h3>
      <ReaderBlueGraySegmented
        :model-value="limitedAlignment && typography.textAlign === 'center' ? 'left' : typography.textAlign || 'justify'"
        :options="limitedAlignment ? alignments.filter(option => option.value !== 'center') : alignments"
        label="对齐方式"
        @update:model-value="$emit('typography', { textAlign: $event as 'left' | 'center' | 'justify' })"
      />
      <p v-if="limitedAlignment">Readium 支持左对齐和两端对齐；居中对齐请切换稳定引擎。</p>
    </section>
    <section class="blue-mode-settings">
      <div class="blue-setting-stack">
        <span>页面宽度</span>
        <ReaderBlueGraySegmented
          :model-value="typography.contentWidth"
          :options="widths"
          label="页面宽度"
          @update:model-value="$emit('typography', { contentWidth: $event as ReaderContentWidth })"
        />
      </div>
      <div v-if="textFormat" class="blue-setting-stack">
        <span>阅读模式</span>
        <ReaderBlueGraySegmented
          :model-value="pagination ? 'pagination' : 'scroll'"
          :options="modes"
          label="阅读模式"
          @update:model-value="$emit('mode', $event as 'scroll' | 'pagination')"
        />
      </div>
    </section>
    <section class="blue-color-settings">
      <h3>主题色</h3>
      <div class="blue-theme-swatches">
        <button
          v-for="theme in themes"
          :key="theme.value"
          type="button"
          :class="{ active: typography.backgroundColor === theme.value }"
          :aria-pressed="typography.backgroundColor === theme.value"
          @click="$emit('typography', { backgroundColor: theme.value })"
        >
          <span :style="{ background: theme.color }"></span>
          {{ theme.label }}
        </button>
      </div>
    </section>
    <section class="blue-display-settings">
      <label class="blue-switch-row">
        <span>显示阅读进度</span>
        <input
          type="checkbox"
          role="switch"
          :checked="settings.showProgress"
          @change="$emit('settings', { showProgress: ($event.target as HTMLInputElement).checked })"
        />
      </label>
    </section>
    <section class="blue-advanced-settings">
      <h3>更多排版设置</h3>
      <label class="blue-setting-row">
        <span>段落间距</span>
        <input
          :value="typography.paragraphSpacing"
          type="range"
          min="0"
          max="40"
          step="2"
          @input="$emit('typography', { paragraphSpacing: Number(($event.target as HTMLInputElement).value) })"
        />
        <output>{{ typography.paragraphSpacing }}</output>
      </label>
      <div class="blue-setting-stack">
        <span>屏幕模式</span>
        <ReaderBlueGraySegmented
          :model-value="settings.screenMode"
          :options="screens"
          label="屏幕模式"
          @update:model-value="$emit('settings', { screenMode: $event as 'single' | 'double' })"
        />
      </div>
      <label class="blue-setting-row blue-wallpaper">
        <span>背景壁纸</span>
        <select
          :value="typography.backgroundImageId"
          @change="$emit('typography', { backgroundImageId: ($event.target as HTMLSelectElement).value })"
        >
          <option value="none">无背景</option>
          <option v-for="background in backgrounds" :key="background.id" :value="background.id">
            {{ background.name }}
          </option>
        </select>
      </label>
      <label class="blue-switch-row">
        <span>首行缩进</span>
        <input
          type="checkbox"
          role="switch"
          :checked="settings.textIndent"
          @change="$emit('settings', { textIndent: ($event.target as HTMLInputElement).checked })"
        />
      </label>
    </section>
    <section v-if="epub">
      <h3>EPUB 阅读引擎</h3>
      <ReaderBlueGraySegmented
        :model-value="settings.epubEngine"
        :options="engines"
        label="EPUB 阅读引擎"
        @update:model-value="$emit('settings', { epubEngine: $event as 'epubjs' | 'readium' })"
      />
      <p>Readium 实验引擎暂不支持书内搜索和高亮，可切回稳定引擎。</p>
    </section>
    <section class="blue-appearance-settings">
      <h3>界面样式</h3>
      <ReaderBlueGraySegmented
        :model-value="settings.appearance"
        :options="appearances"
        label="正式阅读器界面样式"
        @update:model-value="$emit('appearance', $event)"
      />
    </section>
    <p class="blue-preference-hint">
      <ReaderBlueGrayIcon name="cloud" />
      偏好设置随账户保存
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ReaderContentWidth, ReaderSettings, ReaderTypography } from '@/stores/preferences'
import ReaderBlueGraySegmented from './ReaderBlueGraySegmented.vue'
import ReaderBlueGrayIcon from './ReaderBlueGrayIcon.vue'

const props = defineProps<{
  paper?: boolean
  fixedLayout?: boolean
  settings: ReaderSettings
  typography: ReaderTypography
  appearances: Array<{ value: string; label: string }>
  fonts: Array<{ value: string; label: string }>
  backgrounds: Array<{ id: string; name: string }>
  pagination: boolean
  textFormat: boolean
  epub: boolean
}>()
defineEmits<{
  typography: [value: Partial<ReaderTypography>]
  settings: [value: Partial<ReaderSettings>]
  appearance: [value: string]
  font: [value: string]
  mode: [value: 'scroll' | 'pagination']
}>()
const widths = [
  { value: 'narrow', label: '窄' },
  { value: 'wide', label: '适中' },
  { value: 'full', label: '宽' },
]
const modes = [{ value: 'pagination', label: '翻页' }, { value: 'scroll', label: '滚动' }]
const screens = [{ value: 'single', label: '单栏' }, { value: 'double', label: '双栏' }]
const engines = [{ value: 'epubjs', label: 'epub.js（稳定）' }, { value: 'readium', label: 'Readium（实验）' }]
const dayThemes = [
  { value: 'auto', label: '日间', color: '#fcfdff' },
  { value: '#f5f5dc', label: '纸张', color: '#f5f5dc' },
  { value: '#2d2d2d', label: '夜间', color: '#2d2d2d' },
]
const alignments = [
  { value: 'left', label: '左对齐' },
  { value: 'center', label: '居中对齐' },
  { value: 'justify', label: '两端对齐' },
]
const limitedAlignment = computed(() => props.epub && props.settings.epubEngine === 'readium')
const themes = computed(() => props.paper ? [
  { value: 'auto', label: '宣纸白', color: '#faf8f2' },
  { value: '#e8f5e9', label: '青草绿', color: '#dfe7d6' },
  { value: '#2d2d2d', label: '墨夜黑', color: '#2d2d2d' },
] : dayThemes)
</script>

<style scoped>
.blue-settings-content {
  color: var(--blue-ink);
}

section {
  display: grid;
  gap: 24px;
  padding: 24px 0;
  border-bottom: 1px solid var(--blue-line);
}

section:first-child {
  padding-top: 0;
}

h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
}

.blue-setting-row {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
}

.blue-setting-row > span {
  flex: 0 0 72px;
  color: var(--blue-muted);
}

.blue-setting-row output {
  min-width: 24px;
  font-variant-numeric: tabular-nums;
}

select {
  width: 100%;
  min-width: 0;
  min-height: 42px;
  padding: 8px 10px;
  border: 1px solid var(--blue-line);
  border-radius: 9px;
  background: var(--blue-surface);
  color: var(--blue-ink);
  font: inherit;
  font-size: 14px;
}

.blue-size-stepper {
  display: flex;
  align-items: center;
  margin-left: auto;
  overflow: hidden;
  border: 1px solid var(--blue-line);
  border-radius: 9px;
}

.blue-size-stepper button {
  width: 42px;
  height: 42px;
  border: 0;
  background: transparent;
  color: var(--blue-ink);
  font-size: 22px;
  cursor: pointer;
}

.blue-size-stepper output {
  min-width: 70px;
  text-align: center;
}

.blue-size-stepper small {
  color: var(--blue-muted);
  font-size: 12px;
}

input[type='range'] {
  flex: 1;
  width: 100%;
  min-width: 0;
  accent-color: var(--reader-control-accent, #1264cf);
}

.blue-setting-stack {
  display: grid;
  gap: 12px;
  font-size: 14px;
}

.blue-setting-stack > span {
  color: var(--blue-muted);
}

.blue-theme-swatches {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.blue-theme-swatches button {
  display: grid;
  gap: 9px;
  border: 0;
  background: transparent;
  color: var(--blue-muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.blue-theme-swatches button > span {
  height: 44px;
  border: 1px solid var(--blue-line);
  border-radius: 9px;
}

.blue-theme-swatches .active {
  color: var(--reader-control-accent, #1264cf);
}

.blue-theme-swatches .active > span {
  outline: 2px solid var(--reader-control-accent, #1264cf);
  outline-offset: 2px;
}

.blue-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  font-size: 14px;
  cursor: pointer;
}

input[role='switch'] {
  appearance: none;
  width: 44px;
  height: 25px;
  padding: 3px;
  border-radius: 20px;
  background: #c7d1dd;
  cursor: pointer;
}

input[role='switch']::before {
  display: block;
  width: 19px;
  height: 19px;
  border-radius: 50%;
  background: #fff;
  content: '';
  transition: transform 180ms;
}

input[role='switch']:checked {
  background: var(--reader-control-accent, #1264cf);
}

input[role='switch']:checked::before {
  transform: translateX(19px);
}

.blue-preference-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 22px 0 0;
}

p {
  margin: 0;
  color: var(--blue-muted);
  font-size: 12px;
  line-height: 1.8;
}

button:disabled {
  opacity: .4;
  cursor: default;
}

button:focus-visible, select:focus-visible, input:focus-visible {
  outline: 2px solid var(--reader-control-accent, #1264cf);
  outline-offset: 3px;
}

@media (prefers-reduced-motion: reduce) {
  input[role='switch']::before {
    transition: none;
  }
}
</style>
