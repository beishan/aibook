<template>
  <button
    class="book-cover-privacy-button"
    :class="{ 'is-hidden': hidden, compact, inline }"
    type="button"
    :aria-label="hidden ? `显示《${bookTitle}》封面` : `隐藏《${bookTitle}》封面`"
    :title="hidden ? '显示此书封面，保存到本书并优先于全局设置' : '隐藏此书封面，保存到本书并优先于全局设置'"
    :disabled="saving"
    :aria-busy="saving"
    @click.stop="handleToggle"
  >
    <el-icon aria-hidden="true"><View v-if="hidden" /><Hide v-else /></el-icon>
    <span v-if="inline">{{ saving ? '保存中…' : hidden ? '显示封面' : '隐藏封面' }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Hide, View } from '@element-plus/icons-vue'
import { isBookCoverHidden, toggleBookCover } from '@/utils/imagePrivacy'
import { message } from '@/utils/message'

const props = withDefaults(defineProps<{
  bookId: number
  bookTitle?: string
  compact?: boolean
  inline?: boolean
}>(), {
  bookTitle: '该书籍',
  compact: false,
  inline: false,
})

const hidden = computed(() => isBookCoverHidden(props.bookId))
const saving = ref(false)
async function handleToggle() {
  if (saving.value) return
  saving.value = true
  try {
    await toggleBookCover(props.bookId)
  } catch (error: any) {
    message.error(error.response?.data?.message || error.message || '封面显示设置保存失败，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.book-cover-privacy-button {
  position: absolute;
  z-index: 2;
  bottom: 8px;
  right: 8px;
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  color: rgba(255, 255, 255, 0.96);
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 50%;
  cursor: pointer;
  backdrop-filter: blur(8px);
  place-items: center;
}

.book-cover-privacy-button:hover,
.book-cover-privacy-button:focus-visible,
.book-cover-privacy-button.is-hidden {
  background: color-mix(in srgb, var(--primary) 82%, rgba(15, 23, 42, 0.7));
}

.book-cover-privacy-button:focus-visible {
  outline: 2px solid white;
  outline-offset: 2px;
}

.book-cover-privacy-button.compact {
  bottom: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  font-size: 12px;
}
.book-cover-privacy-button:disabled {
  cursor: wait;
  opacity: .65;
}
.book-cover-privacy-button.inline {
  position: static;
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 36px;
  border-color: var(--primary-alpha-20);
  border-radius: 8px;
  background: var(--surface-card);
  color: var(--primary);
  font: inherit;
  font-size: 12px;
}
.book-cover-privacy-button.inline:focus-visible {
  outline-color: var(--primary);
}
</style>
