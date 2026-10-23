<template>
  <span class="home-book-cover" :class="{ 'is-compact': compact }">
    <img
      v-if="book.coverUrl && displayCover"
      :src="getCoverThumbnailUrl(book.coverUrl, compact ? 'list' : 'card')"
      :alt="`${displayBookName(book.title)}封面`"
      :loading="eager ? 'eager' : 'lazy'"
    />
    <span v-else class="cover-letter" aria-hidden="true">
      <span>{{ displayBookName(book.title).slice(0, 2) }}</span>
      <small>{{ book.format?.toUpperCase() }}</small>
    </span>
  </span>
</template>

<script setup lang="ts">
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { computed } from 'vue'
import type { HomeBook } from '@/utils/home'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import { getCoverThumbnailUrl } from '@/utils/cover'

const { displayBookName } = useBookNameDisplay()

const props = defineProps<{ book: HomeBook; compact?: boolean; eager?: boolean }>()
const displayCover = computed(() => props.book.coverHidden == null
  ? shouldLoadBookCover(props.book.id)
  : !props.book.coverHidden)
</script>

<style scoped>
.home-book-cover {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border-radius: 4px 7px 7px 4px;
  background: var(--primary-alpha-10);
  box-shadow: 2px 4px 10px rgb(0 0 0 / 12%);
}
.home-book-cover::after {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(90deg, rgb(0 0 0 / 18%), transparent);
  content: '';
}
.home-book-cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-letter {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  height: 100%;
  padding: 10px;
  color: var(--primary);
  background: linear-gradient(145deg, var(--primary-alpha-10), var(--surface-card));
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: clamp(20px, 2.8vw, 38px);
}
.cover-letter small {
  font-family: inherit;
  font-size: 10px;
  letter-spacing: .12em;
}
.is-compact .cover-letter {
  gap: 4px;
  font-size: 17px;
}
.is-compact .cover-letter small {
  font-size: 8px;
}
</style>
