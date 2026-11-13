<template>
  <div class="shelf-table-scroll" role="region" aria-label="书架书籍表格" tabindex="0">
    <table class="shelf-books-table">
      <caption class="sr-only">书架书籍</caption>
      <thead>
        <tr>
          <th scope="col" class="cover-column">封面</th>
          <th scope="col" class="title-column">书名</th>
          <th scope="col">作者</th>
          <th scope="col">格式</th>
          <th scope="col">阅读状态</th>
          <th scope="col">{{ formatAddedAt ? '加入时间' : '出版社' }}</th>
          <th scope="col" class="actions-column">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(book, index) in books" :key="book.id">
          <td>
            <div class="table-cover">
              <img
                v-if="book.coverUrl && shouldLoadBookCover(book.id)"
                :src="getCoverThumbnailUrl(book.coverUrl, 'list')"
                alt="封面"
                loading="lazy"
              />
              <BookCoverPlaceholderText v-else :title="displayBookName(book.title)" />
              <BookCoverPrivacyButton
                v-if="book.coverUrl"
                :book-id="book.id"
                :book-title="book.title"
                compact
              />
            </div>
          </td>
          <th scope="row" class="title-cell">
            <router-link :to="`/books/${book.id}`">
              {{ displayBookName(book.title) }}
            </router-link>
          </th>
          <td>{{ book.author || '未知作者' }}</td>
          <td>{{ book.format === 'structured' ? '在线章节' : book.format?.toUpperCase() || '—' }}</td>
          <td>{{ readingStatusLabel(book.readingStatus) }}</td>
          <td>{{ formatAddedAt ? formatAddedAt(book.shelfAddedAt) : book.publisher || '—' }}</td>
          <td class="table-actions" @click.stop>
            <slot name="actions" :book="book" :index="index" />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import type { Book } from '@/stores/book'
import BookCoverPlaceholderText from '@/components/BookCoverPlaceholderText.vue'
import BookCoverPrivacyButton from '@/components/BookCoverPrivacyButton.vue'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { getCoverThumbnailUrl } from '@/utils/cover'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'

defineProps<{
  books: Book[]
  formatAddedAt?: (value?: string) => string
}>()

const { displayBookName } = useBookNameDisplay()

function readingStatusLabel(status: string) {
  if (status === 'FINISHED') return '已读完'
  if (status === 'READING') return '正在阅读'
  return '未读'
}
</script>

<style scoped>
.shelf-table-scroll {
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--surface-card);
}
.shelf-table-scroll:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.shelf-books-table {
  width: 100%;
  min-width: 780px;
  border-collapse: collapse;
  text-align: left;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}
th,
td {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  vertical-align: middle;
}
thead th {
  background: var(--bg-secondary);
  font-weight: 600;
  white-space: nowrap;
}
tbody tr:last-child > * {
  border-bottom: 0;
}
tbody tr:hover {
  background: var(--bg-secondary);
}
tbody td {
  max-width: 240px;
  overflow-wrap: anywhere;
}
.cover-column {
  width: 64px;
}
.title-column {
  width: 30%;
}
.title-cell {
  min-width: 180px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.title-cell a {
  color: var(--primary);
  text-decoration: none;
}
.title-cell a:hover {
  text-decoration: underline;
}
.title-cell a:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.table-cover {
  position: relative;
  width: 40px;
  height: 56px;
  overflow: hidden;
  background: var(--bg-secondary);
  border-radius: 3px;
}
.table-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.actions-column {
  width: 64px;
}
.table-actions {
  position: relative;
  text-align: center;
}
.table-actions :deep(.book-menu-trigger) {
  position: static;
  margin: 0 auto;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
