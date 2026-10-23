<template>
  <section class="book-name-settings card glass" aria-labelledby="book-name-display-heading">
    <header>
      <h2 id="book-name-display-heading">书籍名称显示</h2>
      <p>设置书库、书架等页面的书名显示方式，自动保存到当前账户。</p>
    </header>

    <div class="setting-field">
      <span id="book-name-visibility-label">显示书名</span>
      <div
        class="name-segmented"
        role="radiogroup"
        aria-labelledby="book-name-visibility-label"
        @keydown="handleVisibilityKey"
      >
        <span class="segment-indicator" :style="{ transform: `translateX(${preferences.hideBookNames ? 100 : 0}%)` }" aria-hidden="true" />
        <button
          v-for="option in visibilityOptions"
          :key="String(option.hidden)"
          type="button"
          role="radio"
          :aria-checked="preferences.hideBookNames === option.hidden"
          :tabindex="preferences.hideBookNames === option.hidden ? 0 : -1"
          :class="{ active: preferences.hideBookNames === option.hidden }"
          @click="preferences.setHideBookNames(option.hidden)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <div class="setting-field">
      <span id="book-name-mode-label">隐藏模式</span>
      <div class="name-segmented" role="radiogroup" aria-labelledby="book-name-mode-label" @keydown="handleModeKey">
        <span class="segment-indicator" :style="{ transform: `translateX(${preferences.bookNameMaskMode === 'PREFIX' ? 100 : 0}%)` }" aria-hidden="true" />
        <button
          v-for="option in modeOptions"
          :key="option.value"
          type="button"
          role="radio"
          :aria-checked="preferences.bookNameMaskMode === option.value"
          :tabindex="preferences.bookNameMaskMode === option.value ? 0 : -1"
          :class="{ active: preferences.bookNameMaskMode === option.value }"
          @click="preferences.setBookNameMaskMode(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <p>首尾显示保留第一个和最后一个文字；首 N 字显示保留指定数量，其余用 * 代替。</p>
    </div>

    <div v-if="preferences.bookNameMaskMode === 'PREFIX'" class="prefix-field">
      <label for="book-name-prefix-length">保留前多少个文字</label>
      <el-input-number
        id="book-name-prefix-length"
        :model-value="preferences.bookNamePrefixLength"
        :min="1"
        :max="100"
        :step="1"
        :precision="0"
        aria-label="书名前缀文字数"
        @change="updatePrefixLength"
      />
      <small>可选 1–100，默认 2；书名不足 N 个文字时完整显示。</small>
    </div>

    <div class="name-preview" aria-live="polite">
      <span>效果预览</span>
      <strong>{{ preview }}</strong>
    </div>
    <p class="display-note">仅改变书名展示，不修改书籍数据、搜索和下载文件名；编辑书名时仍显示原名。首尾模式下，一至两个字的书名保持原文。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick } from 'vue'
import { usePreferencesStore } from '@/stores/preferences'
import { maskBookName, type BookNameMaskMode } from '@/utils/bookName'

const preferences = usePreferencesStore()
const visibilityOptions = [{ label: '显示', hidden: false }, { label: '隐藏', hidden: true }]
const modeOptions: Array<{ label: string; value: BookNameMaskMode }> = [
  { label: '首尾显示', value: 'HEAD_TAIL' },
  { label: '首 N 字显示', value: 'PREFIX' },
]
const preview = computed(() => maskBookName(
  '春日书房里的阅读时光', preferences.hideBookNames,
  preferences.bookNameMaskMode, preferences.bookNamePrefixLength,
))

function updatePrefixLength(value: number | undefined) {
  if (value !== undefined) preferences.setBookNamePrefixLength(value)
}

async function handleSegmentKey(event: KeyboardEvent, select: (last: boolean) => void) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  const current = Array.from(group.querySelectorAll('button')).findIndex(button => button.getAttribute('aria-checked') === 'true')
  const last = event.key === 'Home' ? false : event.key === 'End' ? true : current === 0
  select(last)
  await nextTick()
  const button = group.querySelector<HTMLButtonElement>('button[aria-checked="true"]')
  button?.focus()
  button?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}

function handleVisibilityKey(event: KeyboardEvent) {
  void handleSegmentKey(event, value => preferences.setHideBookNames(value))
}

function handleModeKey(event: KeyboardEvent) {
  void handleSegmentKey(event, last => preferences.setBookNameMaskMode(last ? 'PREFIX' : 'HEAD_TAIL'))
}
</script>

<style scoped>
.book-name-settings {
  display: grid;
  gap: 24px;
  margin-bottom: 24px;
  padding: 28px;
}

.book-name-settings h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.book-name-settings p,
.prefix-field small {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.6;
}

.setting-field,
.prefix-field {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.name-segmented {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  width: min(100%, 360px);
  max-width: 100%;
  overflow-x: auto;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-card);
  isolation: isolate;
}

.segment-indicator {
  position: absolute;
  inset: 4px auto 4px 4px;
  z-index: -1;
  width: max(120px, calc((100% - 8px) / 2));
  border-radius: 10px;
  background: var(--primary-alpha-10);
  transition: transform 0.25s ease;
}

.name-segmented button {
  min-height: 40px;
  padding: 8px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
}

.name-segmented button.active {
  color: var(--primary);
  font-weight: 600;
}

.name-segmented button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}

.name-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-elevated);
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .book-name-settings {
    padding: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .segment-indicator {
    transition: none;
  }
}
</style>
