<template>
  <button
    class="book-cover-privacy-button"
    :class="{ 'is-hidden': hidden, compact }"
    type="button"
    :aria-label="hidden ? `显示《${bookTitle}》封面` : `隐藏《${bookTitle}》封面`"
    :title="hidden ? '显示此书封面' : '隐藏此书封面'"
    @click.stop="toggleBookCover(bookId)"
  >
    <el-icon aria-hidden="true"><View v-if="hidden" /><Hide v-else /></el-icon>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Hide, View } from '@element-plus/icons-vue'
import { isBookCoverHidden, toggleBookCover } from '@/utils/imagePrivacy'

const props = withDefaults(defineProps<{
  bookId: number
  bookTitle?: string
  compact?: boolean
}>(), {
  bookTitle: '该书籍',
  compact: false,
})

const hidden = computed(() => isBookCoverHidden(props.bookId))
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
</style>
