<template>
  <div class="books-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">书库</h1>
        <p class="page-subtitle">共 {{ bookStore.totalElements }} 本书</p>
      </div>
      <button class="btn btn-primary" @click="showUploadDialog = true">
        <span>📤</span>
        <span>上传书籍</span>
      </button>
    </div>

    <!-- 筛选区 -->
    <div class="filter-card glass">
      <div class="filter-row">
        <div class="search-box">
          <span class="search-icon">🔍</span>
          <input
            v-model="searchKeyword"
            type="text"
            class="input"
            placeholder="搜索书名、作者、ISBN..."
            @keyup.enter="handleSearch"
          />
        </div>
        <select v-model="filterFormat" class="select-input" @change="handleFilterChange('format')">
          <option value="">全部格式</option>
          <option value="epub">EPUB</option>
          <option value="pdf">PDF</option>
          <option value="txt">TXT</option>
          <option value="mobi">MOBI</option>
          <option value="docx">DOCX</option>
          <option value="html">HTML</option>
          <option value="md">Markdown</option>
        </select>
        <select v-model="filterStatus" class="select-input" @change="handleFilterChange('status')">
          <option value="">全部状态</option>
          <option value="UNREADING">未读</option>
          <option value="READING">正在阅读</option>
          <option value="FINISHED">已读完</option>
        </select>
        <select v-model="filterCategoryId" class="select-input" @change="handleFilterChange('category')">
          <option value="">全部分类</option>
          <option
            v-for="category in categoryStore.flatTree"
            :key="category.id"
            :value="String(category.id)"
          >
            {{ `${'　'.repeat(category.depth)}${category.name}` }}
          </option>
        </select>
        <select v-model="filterTagId" class="select-input" @change="handleFilterChange('tag')">
          <option value="">全部标签</option>
          <option
            v-for="tag in tagStore.tags"
            :key="tag.id"
            :value="String(tag.id)"
          >
            {{ tag.name }}
          </option>
        </select>
        <select v-model="sortBy" class="select-input" @change="loadBooks">
          <option value="createdAt">添加时间</option>
          <option value="title">书名</option>
          <option value="author">作者</option>
          <option value="updatedAt">最近阅读</option>
        </select>
        <div class="filter-actions">
          <button class="btn" @click="handleSearch">搜索</button>
          <button class="btn btn-text" @click="resetFilters">重置</button>
        </div>
      </div>
    </div>

    <!-- 视图切换 -->
    <div class="view-controls">
      <div class="btn-group">
        <button
          class="btn"
          :class="{ active: viewMode === 'card' }"
          @click="preferencesStore.setLibraryViewMode('card')"
        >
          <span>▦</span>
          <span>卡片</span>
        </button>
        <button
          class="btn"
          :class="{ active: viewMode === 'list' }"
          @click="preferencesStore.setLibraryViewMode('list')"
        >
          <span>☰</span>
          <span>列表</span>
        </button>
      </div>
      <div class="selection-controls">
        <button
          class="btn"
          @click="openBatchScraper('all-incomplete')"
          title="刮削所有缺少作者或简介的书籍"
        >
          <span>✨</span>
          <span>刮削所有不完整</span>
        </button>
        <button
          class="btn"
          :class="{ active: selectionMode }"
          @click="toggleSelectionMode"
        >
          <span>☑</span>
          <span>{{ selectionMode ? '退出多选' : '多选' }}</span>
        </button>
      </div>
    </div>

    <!-- 多选工具栏 -->
    <div v-if="selectionMode && selectedBooks.size > 0" class="batch-toolbar glass">
      <span class="selection-count">已选择 {{ selectedBooks.size }} 本</span>
      <div class="batch-actions">
        <button class="btn btn-text" @click="selectAllCurrentPage">全选当前页</button>
        <button class="btn btn-text" @click="clearSelection">取消全选</button>
        <select v-model="batchCategoryId" class="select-input batch-category-select">
          <option value="">设为未分类</option>
          <option
            v-for="category in categoryStore.flatTree"
            :key="category.id"
            :value="String(category.id)"
          >
            {{ category.path }}
          </option>
        </select>
        <button class="btn" @click="applyBatchCategory">
          <span>🗂️</span>
          <span>设置分类</span>
        </button>
        <el-select
          v-model="batchTagIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择标签"
          class="batch-tag-select"
        >
          <el-option
            v-for="tag in tagStore.tags"
            :key="tag.id"
            :label="tag.name"
            :value="tag.id"
          />
        </el-select>
        <select v-model="batchTagMode" class="select-input batch-tag-mode">
          <option value="ADD">添加标签</option>
          <option value="REMOVE">移除标签</option>
          <option value="REPLACE">替换标签</option>
        </select>
        <button class="btn" :disabled="batchTagIds.length === 0" @click="applyBatchTags">
          <span>🏷️</span>
          <span>应用标签</span>
        </button>
        <button class="btn btn-primary" @click="openBatchScraper('selected')">
          <span>✨</span>
          <span>批量刮削</span>
        </button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="bookStore.loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="bookStore.books.length === 0" class="empty glass">
      <div class="empty-icon">📚</div>
      <p>书库空空如也</p>
      <button class="btn btn-primary" @click="showUploadDialog = true">上传书籍</button>
    </div>

    <!-- 卡片视图 -->
    <div v-else-if="viewMode === 'card'" class="books-grid">
      <div
        v-for="book in bookStore.books"
        :key="book.id"
        class="book-card glass"
        :class="{ selected: selectionMode && selectedBooks.has(book.id) }"
        @click="handleCardClick(book.id)"
      >
        <!-- 多选复选框 -->
        <div v-if="selectionMode" class="book-select-checkbox" @click.stop>
          <input
            type="checkbox"
            :checked="selectedBooks.has(book.id)"
            @change="toggleBookSelection(book.id)"
          />
        </div>

        <div class="book-cover">
          <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" class="cover-image" />
          <div v-else class="no-cover">
            <span>{{ book.title.charAt(0) }}</span>
          </div>
          <div class="book-cover-actions" @click.stop>
            <button
              class="action-btn"
              :class="{ 'active-favorite': book.isFavorite }"
              @click.stop="handleToggleFavorite(book.id)"
              :aria-label="book.isFavorite ? '取消收藏' : '收藏'"
              :title="book.isFavorite ? '取消收藏' : '收藏'"
            >
              <span class="action-icon">{{ book.isFavorite ? '⭐' : '☆' }}</span>
            </button>
            <button
              class="action-btn"
              :class="{ 'active-wanted': book.isWanted }"
              @click.stop="handleToggleWanted(book.id)"
              :aria-label="book.isWanted ? '取消想读' : '想读'"
              :title="book.isWanted ? '取消想读' : '想读'"
            >
              <span class="action-icon">{{ book.isWanted ? '🔖' : '📑' }}</span>
            </button>
            <button
              class="action-btn"
              :class="{ 'is-processing': processingBookId === book.id }"
              aria-label="更多操作"
              title="更多操作"
              @click.stop
            >
              <el-dropdown
                trigger="click"
                :disabled="processingBookId === book.id"
                @click.stop
                @command="command => handleMoreCommand(command, book)"
              >
                <span class="more-trigger">
                  <el-icon><MoreFilled /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="scrape">✨ 刮削元数据</el-dropdown-item>
                    <el-dropdown-item command="reparse">🔄 重新解析</el-dropdown-item>
                    <el-dropdown-item command="edit">✏️ 编辑书籍</el-dropdown-item>
                    <el-dropdown-item divided command="delete">🗑️ 删除书籍</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </button>
          </div>
          <div class="book-format">{{ book.format.toUpperCase() }}</div>
        </div>

        <div class="book-info">
          <div class="book-title" :title="book.title">{{ book.title }}</div>
          <div class="book-author">{{ book.author || '未知作者' }}</div>
          <div v-if="book.categoryName" class="book-category">{{ book.categoryPath || book.categoryName }}</div>
          <div v-if="book.tags?.length" class="book-tag-list">
            <span
              v-for="tag in book.tags.slice(0, 3)"
              :key="tag.id"
              class="book-tag-chip"
              :style="getTagStyle(tag.color)"
            >
              {{ tag.name }}
            </span>
            <span v-if="book.tags.length > 3" class="book-tag-more">
              +{{ book.tags.length - 3 }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 列表视图 -->
    <div v-else class="books-list glass">
      <div
        v-for="row in bookStore.books"
        :key="row.id"
        class="book-list-item"
        :class="{ selected: selectionMode && selectedBooks.has(row.id) }"
        @click="handleListClick(row.id)"
      >
        <!-- 多选复选框 -->
        <div v-if="selectionMode" class="list-select-checkbox" @click.stop>
          <input
            type="checkbox"
            :checked="selectedBooks.has(row.id)"
            @change="toggleBookSelection(row.id)"
          />
        </div>

        <div class="book-cover-small">
          <img v-if="row.coverUrl" :src="getCoverUrl(row.coverUrl)" alt="封面" />
          <div v-else class="no-cover-small">{{ row.title.charAt(0) }}</div>
        </div>
        <div class="book-list-info">
          <div class="book-list-title">{{ row.title }}</div>
          <div class="book-list-author">{{ row.author || '未知作者' }}</div>
        </div>
        <div class="book-list-meta">
          <span class="tag tag-info">{{ row.format.toUpperCase() }}</span>
          <span class="tag" :class="getStatusClass(row.readingStatus)">
            {{ getStatusText(row.readingStatus) }}
          </span>
          <span v-if="row.categoryName" class="tag tag-info">{{ row.categoryName }}</span>
          <span
            v-for="tag in row.tags?.slice(0, 3)"
            :key="tag.id"
            class="book-tag-chip"
            :style="getTagStyle(tag.color)"
          >
            {{ tag.name }}
          </span>
          <span class="book-date">{{ formatDate(row.createdAt) }}</span>
        </div>
        <div class="book-list-actions">
          <button class="btn btn-text" @click.stop="$router.push(`/reader/${row.id}`)">阅读</button>
          <el-dropdown
            trigger="click"
            :disabled="processingBookId === row.id"
            @click.stop
            @command="command => handleMoreCommand(command, row)"
          >
            <button class="btn btn-text more-list-button" @click.stop>
              <el-icon><MoreFilled /></el-icon>
              <span>{{ processingBookId === row.id ? '处理中' : '更多' }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="scrape">✨ 刮削元数据</el-dropdown-item>
                <el-dropdown-item command="reparse">🔄 重新解析</el-dropdown-item>
                <el-dropdown-item command="edit">✏️ 编辑书籍</el-dropdown-item>
                <el-dropdown-item divided command="delete">🗑️ 删除书籍</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="bookStore.totalElements > 0">
      <button class="btn" :disabled="currentPage <= 1" @click="prevPage">
        <span>‹</span>
        <span>上一页</span>
      </button>
      <span class="page-info">第 {{ currentPage }} 页 / 共 {{ totalPages }} 页</span>
      <button class="btn" :disabled="currentPage >= totalPages" @click="nextPage">
        <span>下一页</span>
        <span>›</span>
      </button>
    </div>

    <!-- 上传对话框 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showUploadDialog" class="dialog-overlay" @click.self="showUploadDialog = false">
          <div class="dialog">
            <div class="dialog-header">
              <span>📤 上传书籍</span>
              <button class="dialog-close" @click="showUploadDialog = false">✕</button>
            </div>
            <div class="dialog-body">
              <FileUpload @success="handleUploadSuccess" />
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 批量刮削对话框 -->
    <BatchScraperDialog
      :visible="showBatchScraperDialog"
      :book-ids="Array.from(selectedBooks)"
      :mode="batchScraperMode"
      @close="showBatchScraperDialog = false"
      @complete="handleBatchScraperComplete"
    />

    <ScraperDialog
      ref="scraperDialog"
      :visible="showScraperDialog"
      @close="showScraperDialog = false"
      @refresh="loadBooks"
    />

    <BookEditDialog
      :visible="showEditDialog"
      :book="editingBook"
      @close="closeEditDialog"
      @saved="handleBookSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { MoreFilled } from '@element-plus/icons-vue'
import { message, confirm } from '@/utils/message'
import { useBookStore, type Book } from '@/stores/book'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import { usePreferencesStore } from '@/stores/preferences'
import FileUpload from '@/components/FileUpload.vue'
import BatchScraperDialog from '@/components/BatchScraperDialog.vue'
import BookEditDialog from '@/components/BookEditDialog.vue'
import ScraperDialog from '@/components/ScraperDialog.vue'
import { getCoverUrl } from '@/utils/cover'
import { scrapeBook } from '@/utils/scraper'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()
const preferencesStore = usePreferencesStore()
const { libraryViewMode: viewMode } = storeToRefs(preferencesStore)

const searchKeyword = ref('')
const filterFormat = ref('')
const filterStatus = ref('')
const filterCategoryId = ref('')
const filterTagId = ref('')
const sortBy = ref('createdAt')
const currentPage = ref(1)
const pageSize = ref(18)
const showUploadDialog = ref(false)
const showScraperDialog = ref(false)
const showEditDialog = ref(false)
const editingBook = ref<Book | null>(null)
const processingBookId = ref<number | null>(null)
const scraperDialog = ref<InstanceType<typeof ScraperDialog> | null>(null)

// 多选相关状态
const selectionMode = ref(false)
const selectedBooks = ref<Set<number>>(new Set())
const showBatchScraperDialog = ref(false)
const batchScraperMode = ref<'selected' | 'all-incomplete'>('selected')
const batchCategoryId = ref('')
const batchTagIds = ref<number[]>([])
const batchTagMode = ref<'ADD' | 'REMOVE' | 'REPLACE'>('ADD')

const totalPages = computed(() => Math.ceil(bookStore.totalElements / pageSize.value))

const handleUploadSuccess = () => {
  showUploadDialog.value = false
  loadBooks()
}

const loadBooks = async () => {
  if (searchKeyword.value) {
    await bookStore.searchBooks(searchKeyword.value, currentPage.value - 1, pageSize.value)
  } else {
    await bookStore.fetchBooks(currentPage.value - 1, pageSize.value, sortBy.value, 'desc', {
      format: filterFormat.value || undefined,
      status: filterStatus.value || undefined,
      categoryId: filterCategoryId.value ? Number(filterCategoryId.value) : undefined,
      includeChildren: Boolean(filterCategoryId.value),
      tagId: filterTagId.value ? Number(filterTagId.value) : undefined,
    })
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadBooks()
}

const handleFilterChange = (type: 'format' | 'status' | 'category' | 'tag') => {
  if (type !== 'format') filterFormat.value = ''
  if (type !== 'status') filterStatus.value = ''
  if (type !== 'category') filterCategoryId.value = ''
  if (type !== 'tag') filterTagId.value = ''
  currentPage.value = 1
  loadBooks()
}

const resetFilters = () => {
  searchKeyword.value = ''
  filterFormat.value = ''
  filterStatus.value = ''
  filterCategoryId.value = ''
  filterTagId.value = ''
  sortBy.value = 'createdAt'
  currentPage.value = 1
  loadBooks()
}

const handleToggleFavorite = async (id: number) => {
  try {
    await bookStore.toggleFavorite(id)
    message.success('操作成功')
  } catch (error) {
    message.error('操作失败')
  }
}

const handleToggleWanted = async (id: number) => {
  try {
    await bookStore.toggleWanted(id)
    message.success('操作成功')
  } catch (error) {
    message.error('操作失败')
  }
}

// 多选相关函数
const toggleSelectionMode = () => {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) {
    selectedBooks.value.clear()
  }
}

const toggleBookSelection = (bookId: number) => {
  if (selectedBooks.value.has(bookId)) {
    selectedBooks.value.delete(bookId)
  } else {
    selectedBooks.value.add(bookId)
  }
}

const selectAllCurrentPage = () => {
  bookStore.books.forEach(book => {
    selectedBooks.value.add(book.id)
  })
}

const clearSelection = () => {
  selectedBooks.value.clear()
}

const applyBatchCategory = async () => {
  try {
    await bookStore.updateBookCategories(
      Array.from(selectedBooks.value),
      batchCategoryId.value ? Number(batchCategoryId.value) : undefined,
    )
    message.success('书籍分类已更新')
    clearSelection()
  } catch {
    message.error('设置分类失败')
  }
}

const applyBatchTags = async () => {
  if (batchTagIds.value.length === 0) {
    message.warning('请先选择标签')
    return
  }
  try {
    await bookStore.updateBookTagsBatch(
      Array.from(selectedBooks.value),
      batchTagIds.value,
      batchTagMode.value,
    )
    const actionText = {
      ADD: '添加',
      REMOVE: '移除',
      REPLACE: '替换',
    }[batchTagMode.value]
    message.success(`已批量${actionText}标签`)
    batchTagIds.value = []
    clearSelection()
    await tagStore.fetchTags()
  } catch (error: any) {
    message.error(error.response?.data?.message || '批量设置标签失败')
  }
}

const openBatchScraper = (mode: 'selected' | 'all-incomplete') => {
  batchScraperMode.value = mode
  showBatchScraperDialog.value = true
}

const handleBatchScraperComplete = () => {
  showBatchScraperDialog.value = false
  loadBooks()
}

const handleCardClick = (bookId: number) => {
  if (selectionMode.value) {
    toggleBookSelection(bookId)
  } else {
    router.push(`/books/${bookId}`)
  }
}

const handleListClick = (bookId: number) => {
  if (selectionMode.value) {
    toggleBookSelection(bookId)
  } else {
    router.push(`/books/${bookId}`)
  }
}

const handleDelete = async (id: number) => {
  const result = await confirm('确定要删除这本书吗？')
  if (result) {
    try {
      await bookStore.deleteBook(id)
      message.success('删除成功')
    } catch (error) {
      message.error('删除失败')
    }
  }
}

const handleMoreCommand = async (command: string, book: Book) => {
  switch (command) {
    case 'scrape':
      await handleScrapeBook(book)
      break
    case 'reparse':
      await handleReparseBook(book)
      break
    case 'edit':
      editingBook.value = book
      showEditDialog.value = true
      break
    case 'delete':
      await handleDelete(book.id)
      break
  }
}

const handleScrapeBook = async (book: Book) => {
  showScraperDialog.value = true
  processingBookId.value = book.id
  await nextTick()
  try {
    await scraperDialog.value?.startScrape(() => scrapeBook(book.id))
  } finally {
    processingBookId.value = null
  }
}

const handleReparseBook = async (book: Book) => {
  processingBookId.value = book.id
  try {
    const result = await bookStore.reparseBook(book.id)
    const chapterText =
      typeof result.chapterCount === 'number'
        ? `，共 ${result.chapterCount} 章`
        : ''
    message.success(`${result.message || '解析完成'}${chapterText}`)
  } catch (error: any) {
    message.error(error.response?.data?.message || '重新解析失败')
  } finally {
    processingBookId.value = null
  }
}

const closeEditDialog = () => {
  showEditDialog.value = false
  editingBook.value = null
}

const handleBookSaved = (book: Book) => {
  const index = bookStore.books.findIndex(item => item.id === book.id)
  if (index !== -1) bookStore.books[index] = book
  void tagStore.fetchTags()
}

const getStatusClass = (status: string) => {
  switch (status) {
    case 'READING':
      return 'tag-primary'
    case 'FINISHED':
      return 'tag-success'
    default:
      return 'tag-info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'READING':
      return '正在阅读'
    case 'FINISHED':
      return '已读完'
    default:
      return '未读'
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

const getTagStyle = (color?: string) => {
  const safeColor = /^#[0-9a-fA-F]{6}$/.test(color || '') ? color! : '#64748B'
  return {
    color: safeColor,
    borderColor: `${safeColor}80`,
    backgroundColor: `${safeColor}14`,
  }
}

const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--
    loadBooks()
  }
}

const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
    loadBooks()
  }
}

watch(
  () => route.query.search,
  (newSearch) => {
    if (newSearch) {
      searchKeyword.value = newSearch as string
      handleSearch()
    }
  },
  { immediate: true }
)

onMounted(() => {
  void preferencesStore.hydrate()
  categoryStore.refresh()
  tagStore.fetchTags()
  if (!route.query.search) {
    loadBooks()
  }
})
</script>

<style scoped>
.books-view {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-xl);
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

/* 筛选区 */
.filter-card {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.filter-row {
  display: flex;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  align-items: center;
}

.search-box {
  position: relative;
  flex: 1;
  min-width: 250px;
}

.search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
}

.search-box .input {
  padding-left: 40px;
}

.select-input {
  padding: 12px 16px;
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--font-size-base);
  background: var(--bg-primary);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  min-width: 140px;
  cursor: pointer;
  outline: none;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
}

/* 视图切换 */
.view-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.btn-group {
  display: inline-flex;
  background: var(--surface-card);
  border-radius: var(--radius-lg);
  padding: 4px;
  gap: 2px;
  border: 1px solid var(--border-color-light);
}

.btn-group .btn {
  padding: 8px 16px;
  border-radius: var(--radius-md);
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  transition: all 0.2s ease;
}

.btn-group .btn:hover {
  background: var(--surface-hover);
  transform: none;
  box-shadow: none;
}

.btn-group .btn.active {
  background: var(--primary);
  color: white;
  box-shadow: 0 2px 8px var(--primary-alpha-30);
}

.selection-controls {
  display: flex;
  gap: var(--spacing-sm);
}

.selection-controls .btn {
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  background: var(--surface-card);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.selection-controls .btn:hover {
  background: var(--surface-hover);
  border-color: var(--primary);
  color: var(--primary);
  transform: none;
  box-shadow: none;
}

.selection-controls .btn.active {
  background: var(--primary);
  border-color: var(--primary);
  color: white;
  box-shadow: 0 2px 8px var(--primary-alpha-30);
}

/* 批量操作工具栏 */
.batch-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  animation: slideDown 0.3s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.selection-count {
  font-weight: 500;
  color: var(--text-primary);
}

.batch-actions {
  display: flex;
  gap: var(--spacing-sm);
  align-items: center;
}

.batch-actions .btn {
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
}

.batch-actions .btn-text {
  color: var(--text-secondary);
  background: transparent;
  border: none;
}

.batch-actions .btn-text:hover {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.batch-category-select {
  min-width: 180px;
  padding: 8px 12px;
}

.batch-tag-select {
  width: 210px;
}

.batch-tag-mode {
  min-width: 112px;
  padding: 8px 12px;
}

/* 复选框样式 */
.book-select-checkbox {
  position: absolute;
  z-index: 10;
  top: var(--spacing-md);
  left: var(--spacing-md);
}

.list-select-checkbox {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: var(--spacing-md);
  flex-shrink: 0;
}

.book-select-checkbox input,
.list-select-checkbox input {
  width: 20px;
  height: 20px;
  cursor: pointer;
  accent-color: var(--primary);
}

/* 选中状态 */
.book-card.selected {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-alpha-30);
}

.book-list-item.selected {
  background: var(--primary-alpha-10);
}

/* 加载中和空状态 */
.loading,
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

.loading-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-md);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

/* 书籍网格 */
.books-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-xl);
}

.book-card {
  position: relative;
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
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.12);
}

.book-cover {
  height: 200px;
  position: relative;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-cover {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 40px;
  font-weight: 600;
}

.book-format {
  position: absolute;
  bottom: 8px;
  left: 8px;
  padding: 2px 6px;
  color: rgba(255, 255, 255, 0.95);
  font-size: 10px;
  font-weight: 500;
  line-height: 1.3;
  letter-spacing: 0.04em;
  background: rgba(15, 23, 42, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 4px;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.book-info {
  padding: var(--spacing-sm) var(--spacing-md);
}

.book-title {
  font-size: var(--font-size-base);
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-author {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
}

.book-category {
  margin-bottom: var(--spacing-sm);
  color: var(--primary);
  font-size: var(--font-size-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-tag-list {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 20px;
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
}

.book-tag-chip {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  max-width: 92px;
  padding: 2px 6px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 10px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-tag-more {
  color: var(--text-secondary);
  font-size: 10px;
}

.book-cover-actions {
  position: absolute;
  z-index: 3;
  top: 0;
  right: 0;
  left: 0;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  padding: 9px 10px;
  background: rgba(15, 23, 42, 0.58);
  border-bottom: 1px solid rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  opacity: 0;
  transform: translateY(-105%);
  transition:
    transform 0.24s ease,
    opacity 0.2s ease;
}

.book-card:hover .book-cover-actions,
.book-card:focus-within .book-cover-actions {
  opacity: 1;
  transform: translateY(0);
}

.action-btn {
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  color: rgba(255, 255, 255, 0.96);
  position: relative;
  overflow: hidden;
}

.action-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: currentColor;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.action-btn:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.28);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.action-btn:hover::before {
  opacity: 0.08;
}

.action-btn:active {
  transform: translateY(0);
}

.action-icon {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn.active-favorite {
  background: rgba(254, 243, 199, 0.88);
  border-color: rgba(253, 230, 138, 0.94);
  color: #b45309;
}

.action-btn.active-wanted {
  background: rgba(252, 231, 243, 0.88);
  border-color: rgba(251, 207, 232, 0.94);
  color: #be185d;
}

.more-trigger {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: inherit;
  outline: none;
}

.action-btn.is-processing {
  opacity: 0.55;
  cursor: wait;
}

.more-list-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 书籍列表 */
.books-list {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: var(--spacing-xl);
}

.book-list-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.book-list-item:last-child {
  border-bottom: none;
}

.book-list-item:hover {
  background: var(--surface-hover);
}

.book-cover-small {
  width: 50px;
  height: 70px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-right: var(--spacing-md);
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}

.book-cover-small img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-cover-small {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.book-list-info {
  flex: 1;
  min-width: 0;
}

.book-list-title {
  font-size: var(--font-size-base);
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-list-author {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.book-list-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: 0 var(--spacing-lg);
}

.book-date {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  min-width: 80px;
  text-align: right;
}

.book-list-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.btn-danger {
  color: var(--danger) !important;
}

.btn-danger:hover {
  background: var(--danger-alpha-10, rgba(255, 59, 48, 0.1)) !important;
}

/* 分页 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--spacing-md);
}

.page-info {
  color: var(--text-on-page-bg-secondary);
  font-size: var(--font-size-sm);
}

/* 响应式 */
@media (max-width: 768px) {
  .filter-row {
    flex-direction: column;
  }

  .search-box {
    min-width: 100%;
  }

  .book-list-meta {
    display: none;
  }
}

@media (hover: none), (pointer: coarse) {
  .book-cover-actions {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .book-cover-actions {
    transition: none;
  }
}
</style>
