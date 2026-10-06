<template>
  <section class="cover-display-settings card glass">
    <div class="settings-copy">
      <span class="settings-icon" aria-hidden="true">🖼️</span>
      <div>
        <h2>书籍封面显示</h2>
        <p>选择“不显示”后，网页端所有页面将使用文字占位，并停止加载书籍封面图片。</p>
      </div>
    </div>

    <div
      class="cover-display-segmented"
      :class="{ 'is-hidden-mode': allBookCoversHidden }"
      role="radiogroup"
      aria-label="书籍封面是否显示"
      @keydown="handleKeydown"
    >
      <span class="segment-indicator" aria-hidden="true"></span>
      <button
        type="button"
        role="radio"
        :aria-checked="!allBookCoversHidden"
        :tabindex="allBookCoversHidden ? -1 : 0"
        :class="{ active: !allBookCoversHidden }"
        @click="updateVisibility(false)"
      >
        显示
      </button>
      <button
        type="button"
        role="radio"
        :aria-checked="allBookCoversHidden"
        :tabindex="allBookCoversHidden ? 0 : -1"
        :class="{ active: allBookCoversHidden }"
        @click="updateVisibility(true)"
      >
        不显示
      </button>
    </div>

    <div class="behavior-note" :class="{ active: allBookCoversHidden }">
      <strong>{{ allBookCoversHidden ? '当前不会加载封面' : '当前正常加载封面' }}</strong>
      <span>{{ allBookCoversHidden ? '已有封面数据不会被删除，重新选择“显示”即可恢复。' : '单本书仍可使用封面上的按钮单独隐藏。' }}</span>
    </div>

    <section class="size-settings" aria-labelledby="cover-size-heading">
      <div class="size-settings-heading">
        <div>
          <h3 id="cover-size-heading">封面图片尺寸</h3>
          <p>按视图选择后端缩略图尺寸，设置会保存到当前账户。</p>
        </div>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="savingSizes || !sizeSettingsChanged"
          @click="saveSizes"
        >
          {{ savingSizes ? '保存中…' : '保存尺寸设置' }}
        </button>
      </div>
      <div class="size-settings-grid">
        <label v-for="view in coverViews" :key="view.key" class="size-settings-field">
          <span>{{ view.label }}</span>
          <small>{{ view.description }}</small>
          <select v-model="sizeDraft[view.key]">
            <option v-for="option in bookCoverImageSizeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
      </div>
      <p class="size-settings-note">
        缩略图保持原图比例，宽度按所选上限生成并缓存在服务器；不支持缩略的格式会回退原图。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  bookCoverImageSizes,
  bookCoverImageSizeOptions,
  saveBookCoverImageSizes,
  type BookCoverView,
} from '@/utils/bookCoverImageSizes'
import { message } from '@/utils/message'
import {
  allBookCoversHidden,
  setAllBookCoversHidden,
} from '@/utils/imagePrivacy'

const coverViews: Array<{ key: BookCoverView; label: string; description: string }> = [
  { key: 'card', label: '卡片视图', description: '书库网格、首页、书架和发现卡片' },
  { key: 'list', label: '列表视图', description: '紧凑列表、系列和统计记录' },
  { key: 'detail', label: '详情视图', description: '书籍详情与阅读页面' },
]
const sizeDraft = reactive({ ...bookCoverImageSizes })
const savingSizes = ref(false)
const sizeSettingsChanged = computed(() =>
  coverViews.some(view => sizeDraft[view.key] !== bookCoverImageSizes[view.key]),
)

const saveSizes = async () => {
  savingSizes.value = true
  try {
    await saveBookCoverImageSizes({ ...sizeDraft })
    message.success('封面图片尺寸已保存到账户')
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存封面图片尺寸失败')
  } finally {
    savingSizes.value = false
  }
}

const updateVisibility = (hidden: boolean) => {
  if (allBookCoversHidden.value === hidden) return
  setAllBookCoversHidden(hidden)
  message.success(hidden ? '已停止加载书籍封面' : '已恢复显示书籍封面')
}

const handleKeydown = (event: KeyboardEvent) => {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const hidden = event.key === 'ArrowRight' || event.key === 'End'
  updateVisibility(hidden)
  requestAnimationFrame(() => {
    const target = (event.currentTarget as HTMLElement)
      .querySelector<HTMLButtonElement>(`button[aria-checked="true"]`)
    target?.focus()
  })
}
</script>

<style scoped>
.cover-display-settings {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
  padding: 28px;
}

.settings-copy { display: flex; gap: 16px; align-items: center; }
.settings-icon { display: grid; width: 48px; height: 48px; place-items: center; border-radius: 15px; background: var(--primary-alpha-10); font-size: 24px; }
.settings-copy h2 { margin: 0; color: var(--text-primary); font-size: 20px; }
.settings-copy p { max-width: 620px; margin: 6px 0 0; color: var(--text-secondary); line-height: 1.6; }

.cover-display-segmented {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, minmax(92px, 1fr));
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-hover);
  isolation: isolate;
}

.segment-indicator {
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 4px;
  z-index: -1;
  width: calc((100% - 8px) / 2);
  border: 1px solid var(--border-color-light);
  border-radius: 10px;
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
  transition: transform .24s cubic-bezier(.2, .8, .2, 1);
}

.cover-display-segmented.is-hidden-mode .segment-indicator { transform: translateX(100%); }
.cover-display-segmented button { min-height: 38px; padding: 0 16px; border: 0; border-radius: 10px; background: transparent; color: var(--text-secondary); cursor: pointer; font: inherit; font-weight: 600; }
.cover-display-segmented button.active { color: var(--primary); }
.cover-display-segmented button:focus-visible { outline: 2px solid var(--primary); outline-offset: -2px; }

.behavior-note { grid-column: 1 / -1; display: grid; gap: 4px; padding: 14px 16px; border: 1px solid var(--border-color-light); border-radius: 12px; background: var(--surface-hover); color: var(--text-secondary); }
.behavior-note strong { color: var(--text-primary); }
.behavior-note.active { border-color: color-mix(in srgb, var(--primary) 30%, var(--border-color-light)); background: var(--primary-alpha-10); }

.size-settings {
  display: grid;
  gap: 16px;
  grid-column: 1 / -1;
  padding: 18px;
  border: 1px solid var(--border-color-light);
  border-radius: 14px;
  background: var(--surface-hover);
}
.size-settings-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.size-settings-heading h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 15px;
}
.size-settings-heading p,
.size-settings-note {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.size-settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.size-settings-field {
  display: grid;
  align-content: start;
  gap: 7px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
}
.size-settings-field small {
  min-height: 2.6em;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 400;
  line-height: 1.4;
}
.size-settings-field select {
  width: 100%;
  min-height: 40px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
}
.size-settings-field select:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.size-settings-note { margin: 0; }

@media (max-width: 720px) {
  .cover-display-settings { grid-template-columns: minmax(0, 1fr); padding: 20px; }
  .cover-display-segmented { width: 100%; }
  .size-settings-heading { align-items: stretch; flex-direction: column; }
  .size-settings-heading .btn { width: 100%; }
  .size-settings-grid { grid-template-columns: minmax(0, 1fr); }
  .size-settings-field small { min-height: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .segment-indicator { transition: none; }
}
</style>
