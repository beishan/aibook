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
  </section>
</template>

<script setup lang="ts">
import { message } from '@/utils/message'
import {
  allBookCoversHidden,
  setAllBookCoversHidden,
} from '@/utils/imagePrivacy'

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

@media (max-width: 720px) {
  .cover-display-settings { grid-template-columns: minmax(0, 1fr); padding: 20px; }
  .cover-display-segmented { width: 100%; }
}

@media (prefers-reduced-motion: reduce) {
  .segment-indicator { transition: none; }
}
</style>
