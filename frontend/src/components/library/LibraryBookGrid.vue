<template>
  <div class="library-books" :class="[layout === 'list' ? 'as-list' : '', `size-${size}`]">
    <article v-for="book in books" :key="book.id" class="library-book">
      <label v-if="selectable" class="select-book">
        <input type="checkbox" :checked="selected.includes(book.id)" @change="$emit('select', book.id)">
        <span class="sr-only">
          选择{{ displayBookName(book.title) }}
        </span>
      </label>
      <button
        class="book-open"
        type="button"
        :aria-label="`查看${displayBookName(book.title)}详情`"
        @click="$emit('open', book)"
      >
        <HomeBookCover :book="book" />
      </button>
      <div class="book-info">
        <button class="book-title" type="button" @click="$emit('open', book)">
          {{ displayBookName(book.title) }}
        </button>
        <p>
          {{ book.author || '佚名' }}
        </p>
        <div
          v-if="book.totalProgress != null"
          class="book-progress"
          role="progressbar"
          :aria-valuenow="readingPercent(book.totalProgress)"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-label="`${displayBookName(book.title)}阅读进度`"
        >
          <span :style="{ width: `${readingPercent(book.totalProgress)}%` }" />
        </div>
        <div class="book-actions">
          <small>
            {{ book.readingStatus === 'FINISHED' ? '已读完' : book.totalProgress != null ? `已读 ${readingPercent(book.totalProgress)}%` : '尚未开始' }}
          </small>
          <slot name="actions" :book="book" />
        </div>
      </div>
    </article>
  </div>
</template>
<script setup lang="ts">
import HomeBookCover from '@/components/HomeBookCover.vue';
import { useBookNameDisplay } from '@/composables/useBookNameDisplay';
import type { LibraryBook } from '@/utils/personalLibraries';
import { readingPercent } from '@/utils/home';
withDefaults(defineProps<{
    books: LibraryBook[];
    layout?: string;
    size?: string;
    selectable?: boolean;
    selected?: number[];
}>(), { layout: 'grid', size: 'medium', selected: () => [] });
defineEmits<{
    open: [
        book: LibraryBook
    ];
    select: [
        id: number
    ];
}>();
const { displayBookName } = useBookNameDisplay();
</script>
<style scoped>
.library-books {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(145px, 1fr));
  gap: 28px;
}
.library-books.size-small {
  grid-template-columns: repeat(auto-fill, minmax(115px, 1fr));
}
.library-books.size-large {
  grid-template-columns: repeat(auto-fill, minmax(185px, 1fr));
}
.library-book {
  position: relative;
  min-width: 0;
}
.book-open, .book-title {
  border: 0;
  background: transparent;
  color: var(--text-primary);
  padding: 0;
  cursor: pointer;
  text-align: left;
  font: inherit;
}
.book-open {
  width: 100%;
}
.book-title {
  display: block;
  font-weight: 600;
  margin-top: 12px;
  overflow-wrap: anywhere;
}
.book-info p {
  color: var(--text-secondary);
  margin: 5px 0 12px;
  font-size: 12px;
}
.book-progress {
  height: 3px;
  border-radius: 3px;
  background: var(--primary-alpha-10);
  margin: 10px 0;
}
.book-progress span {
  display: block;
  height: 100%;
  background: var(--primary);
  border-radius: inherit;
}
.book-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  flex-wrap: wrap;
}
.book-actions small {
  color: var(--text-secondary);
  font-size: 12px;
}
.select-book {
  position: absolute;
  top: 5px;
  left: 5px;
  z-index: 1;
  padding: 4px;
  background: var(--surface-elevated);
  border-radius: 5px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
.as-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.as-list .library-book {
  display: flex;
  gap: 18px;
  align-items: center;
}
.as-list .book-open {
  width: 75px;
  flex-shrink: 0;
}
.as-list .book-info {
  flex: 1;
  min-width: 0;
}
button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
  border-radius: 4px;
}
@media (max-width: 600px) {
  .library-books, .library-books.size-small, .library-books.size-large {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 22px 16px;
  }
  .as-list {
    grid-template-columns: 1fr !important;
  }
}
</style>
