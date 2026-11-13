<template>
  <div class="booklist-view">
    <!-- 返回按钮 -->
    <div class="page-header">
      <button class="back-btn" @click="$router.back()">
        <span>‹</span>
        <span>返回</span>
      </button>
      <div v-if="bookList">
        <h1 class="page-title">{{ bookList.name }}</h1>
        <p class="page-subtitle">{{ bookList.description || '暂无描述' }} · {{ total }} 本书</p>
      </div>
    </div>

    <div class="view-toolbar">
      <AudioSegmented
        v-model="viewMode"
        label="书单显示方式"
        :options="viewOptions"
      />
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 书籍列表 -->
    <div
      v-else-if="books.length > 0"
      class="books-grid"
      :class="{ 'books-grid--small': viewMode === 'small', 'books-list': viewMode === 'list' }"
    >
      <div
        v-for="book in books"
        :key="book.id"
        class="book-card glass"
        @click="$router.push(`/books/${book.id}`)"
      >
        <div class="book-cover">
          <BookCardBadges
            v-if="viewMode !== 'list'"
            :format="book.format"
            :author="book.author"
          />
          <ShelfBookMenu
            :book="book"
            :title="displayBookName(book.title)"
            :disabled="pending.has(book.id)"
            @action="handleAction"
          />
          <img
            v-if="book.coverUrl && shouldLoadBookCover(book.id)"
            :src="getCoverThumbnailUrl(book.coverUrl, viewMode === 'list' ? 'list' : 'card')"
            alt="封面"
          />
          <div v-else class="no-cover"><BookCoverPlaceholderText :title="displayBookName(book.title)" /></div>
          <BookCoverPrivacyButton
            v-if="book.coverUrl"
            :book-id="book.id"
            :book-title="book.title"
            :compact="viewMode === 'list'"
          />
        </div>
        <div class="book-info">
          <div class="book-title" :title="displayBookName(book.title)">
            {{ displayBookName(book.title) }}
          </div>
          <div v-if="viewMode === 'list'" class="book-author">
            {{ book.author || '未知作者' }} · {{ book.format?.toUpperCase() || '未知格式' }}
          </div>
          <div
            v-if="book.totalProgress != null"
            class="book-progress"
            role="progressbar"
            :aria-label="`${displayBookName(book.title)}阅读进度`"
            :aria-valuenow="readingPercent(book.totalProgress)"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <span :style="{ width: `${readingPercent(book.totalProgress)}%` }" />
          </div>
          <small class="progress-label">
            {{ book.totalProgress == null ? '尚未开始' : `已读 ${readingPercent(book.totalProgress)}%` }}
          </small>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="loadError" class="empty glass">
      <p>书单加载失败，请重试</p>
      <button class="btn btn-primary" @click="loadBookList">重新加载</button>
    </div>
    <div v-else class="empty glass">
      <div class="empty-icon">📚</div>
      <p>书单中暂无书籍</p>
      <button class="btn btn-primary" @click="$router.push('/books')">去书库添加</button>
    </div>
    <div v-if="!loadError && total > 0" class="booklist-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[...SHELF_PAGE_SIZE_OPTIONS]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="loadBookList"
      />
    </div>
    <BookQuickReader ref="quickReader" @closed="refreshBook" />
    <BookEditDialog :visible="editingBook !== null" :book="editingBook" @close="editingBook = null" @saved="applyBook" />
    <AddToBookListDialog :visible="listBook !== null" :book="listBook" @close="listBook = null" />
    <ScraperDialog :book="scrapingBook" :visible="scrapingBook !== null" @close="scrapingBook = null" @running="setScrapeRunning" @refresh="scrapingBook && refreshBook(scrapingBook.id)" />
  </div>
</template>

<script setup lang="ts">
import { openIndependentReader } from '@/utils/independentReader'
import AudioSegmented from '@/components/audio/AudioSegmented.vue'
import ShelfBookMenu from '@/components/ShelfBookMenu.vue'
import BookQuickReader from '@/components/BookQuickReader.vue'
import BookEditDialog from '@/components/BookEditDialog.vue'
import AddToBookListDialog from '@/components/AddToBookListDialog.vue'
import ScraperDialog from '@/components/ScraperDialog.vue'
import { useBookStore, type Book } from '@/stores/book'
import { message, confirm } from '@/utils/message'
import BookCoverPlaceholderText from '@/components/BookCoverPlaceholderText.vue'
import BookCardBadges from '@/components/BookCardBadges.vue'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/utils/api'
import { getCoverThumbnailUrl } from '@/utils/cover'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import BookCoverPrivacyButton from '@/components/BookCoverPrivacyButton.vue'
import { SHELF_PAGE_SIZE_OPTIONS, usePreferencesStore } from '@/stores/preferences'
import { readingPercent } from '@/utils/home'

const { displayBookName } = useBookNameDisplay()

const route = useRoute()
const router = useRouter()
const preferencesStore = usePreferencesStore()
const viewOptions = [
  { value: 'grid', label: '卡片' },
  { value: 'small', label: '小卡片' },
  { value: 'list', label: '列表' },
]
const viewMode = computed({
  get: () => preferencesStore.bookListViewMode,
  set: (value: string) => {
    void preferencesStore.setBookListViewMode(value)
  },
})
type BookCard = Book & { totalProgress?: number | null }
interface BookListPage {
  id: number
  name: string
  description?: string
  books: { content: BookCard[]; totalElements: number }
}
const bookList = ref<BookListPage | null>(null)
const books = ref<BookCard[]>([])
const loading = ref(true)
const store = useBookStore()
const pending = ref(new Set<number>())
const quickReader = ref<InstanceType<typeof BookQuickReader> | null>(null)
const editingBook = ref<Book | null>(null)
const listBook = ref<Book | null>(null)
const scrapingBook = ref<Book | null>(null)

function setScrapeRunning(running: boolean) {
  if (!scrapingBook.value) return
  if (running) pending.value.add(scrapingBook.value.id)
  else pending.value.delete(scrapingBook.value.id)
}

function applyBook(updated: Book) {
  books.value = books.value.map(book => book.id === updated.id ? { ...book, ...updated } : book)
}

async function refreshBook(id: number) {
  try {
    const { data } = await api.get<Book>(`/api/books/${id}`)
    applyBook(data)
  } catch {
    message.warning('刷新书籍信息失败，请重新打开书单')
  }
}

async function handleAction(command: string, book: Book) {
  if (pending.value.has(book.id)) return
  if (command === 'independent-read') {
    openIndependentReader(router, book)
    return
  }
  pending.value.add(book.id)
  try {
    const { data: fullBook } = await api.get<Book>(`/api/books/${book.id}`)
    book = fullBook
    switch (command) {
      case 'favorite':
        applyBook(await store.toggleFavorite(book.id))
        break
      case 'wanted':
        applyBook(await store.toggleWanted(book.id))
        break
      case 'shelf':
        applyBook(book.onShelf ? await store.removeFromShelf(book.id) : await store.addToShelf(book.id))
        break
      case 'quick-read':
        quickReader.value?.open(book)
        break
      case 'edit':
        editingBook.value = book
        break
      case 'add-to-list':
        listBook.value = book
        break
      case 'repair':
        router.push(`/books/${book.id}/repair`)
        break
      case 'scrape':
        if (!scrapingBook.value) scrapingBook.value = book
        break
      case 'random-cover':
        applyBook(await store.randomizeBookCover(book.id))
        message.success('已随机更换书籍封面')
        break
      case 'reparse': {
        const result = await store.reparseBook(book.id)
        if (result.book) applyBook(result.book)
        message.success(result.message || '重新解析完成')
        break
      }
      case 'delete':
        if (!await confirm(`确定将《${displayBookName(book.title)}》移入回收站吗？原始文件会保留，可从回收站恢复。`, '移入回收站')) return
        await store.deleteBook(book.id)
        await loadBookList()
        message.success('已移入回收站')
        break
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍操作失败，请稍后重试')
  } finally {
    pending.value.delete(book.id)
  }
}
const loadError = ref(false)
const page = ref(1)
const pageSize = computed({
  get: () => preferencesStore.shelfPageSize,
  set: (value: number) => preferencesStore.setShelfPageSize(value),
})
const total = ref(0)
let requestSequence = 0

const loadBookList = async () => {
  const listId = route.params.id
  const request = ++requestSequence
  loading.value = true
  loadError.value = false
  try {
    const response = await api.get<BookListPage>(`/api/booklists/${listId}/books`, {
      params: { page: page.value - 1, size: pageSize.value },
    })
    if (request !== requestSequence) return
    bookList.value = response.data
    total.value = response.data.books.totalElements
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (page.value > lastPage) {
      page.value = lastPage
      await loadBookList()
      return
    }
    books.value = response.data.books.content
  } catch (error) {
    if (request !== requestSequence) return
    books.value = []
    loadError.value = true
  } finally {
    if (request === requestSequence) loading.value = false
  }
}

watch(() => route.params.id, () => {
  page.value = 1
  books.value = []
  bookList.value = null
  total.value = 0
  void loadBookList()
}, { immediate: true })
watch(pageSize, () => {
  page.value = 1
  void loadBookList()
})
onMounted(() => { void preferencesStore.hydrate() })
</script>

<style scoped>
.view-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--spacing-lg);
  min-width: 0;
}
.book-progress {
  height: 4px;
  margin-top: 10px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--border-color-light);
}
.book-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--primary);
}
.progress-label {
  display: block;
  margin-top: 6px;
  color: var(--text-secondary);
}
.booklist-pagination {
  display: flex;
  justify-content: center;
  max-width: 100%;
  margin-top: var(--spacing-xl);
  overflow-x: auto;
}
.booklist-view {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-xl);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: var(--font-size-base);
  cursor: pointer;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.back-btn:hover {
  background: var(--bg-tertiary);
}

.page-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: var(--text-on-page-bg);
  text-shadow: var(--text-on-page-bg-shadow);
  margin-bottom: var(--spacing-sm);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--text-on-page-bg-secondary);
}

.loading {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--text-secondary);
}

.loading-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-md);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.books-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--spacing-md);
}

.book-card {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.book-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
}

.book-cover {
  position: relative;
  height: 220px;
  overflow: hidden;
}

.book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: filter 0.2s ease, transform 0.2s ease;
}

.book-cover img.is-hidden {
  filter: blur(16px);
  transform: scale(1.15);
}

.no-cover {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 48px;
  font-weight: 600;
}

.book-info {
  padding: var(--spacing-sm) var(--spacing-md);
}

.book-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-author {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.empty {
  text-align: center;
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

@media (prefers-reduced-motion: reduce) {
  .book-cover img {
    transition: none;
  }
}

@media (max-width: 640px) {
  .booklist-view {
    padding: 12px 0;
  }

  .page-header {
    gap: 12px;
    margin-bottom: 20px;
  }

  .back-btn {
    padding: 8px 11px;
  }

  .page-title {
    font-size: clamp(24px, 8vw, 34px);
    overflow-wrap: anywhere;
  }

  .page-subtitle {
    font-size: 13px;
  }

  .books-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .book-cover {
    height: auto;
    aspect-ratio: 2 / 3;
  }

  .book-card:hover {
    transform: none;
  }

  .book-info {
    padding: 9px 10px 11px;
  }
}
.books-grid--small {
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}
.books-grid--small .book-cover {
  height: auto;
  aspect-ratio: 2 / 3;
}
.books-grid--small .book-info {
  padding: 8px;
}
.books-list {
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}
.books-list .book-card {
  display: flex;
  align-items: center;
  position: relative;
  padding-right: 44px;
}
.books-list .book-card:hover {
  transform: none;
}
.books-list .book-cover {
  position: static;
  flex: 0 0 64px;
  height: 96px;
  aspect-ratio: auto;
}
.books-list :deep(.book-cover-privacy-button) {
  left: 36px;
  right: auto;
}
.books-list .book-info {
  flex: 1;
  min-width: 0;
}
.books-list .book-progress {
  max-width: 320px;
}
.books-list .book-author {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 640px) {
  .books-grid--small {
    grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
    gap: 8px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .book-card {
    transition: none;
  }
}
</style>
