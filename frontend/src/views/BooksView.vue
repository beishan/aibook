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
        <select v-model="filterFormat" class="select-input">
          <option value="">全部格式</option>
          <option value="epub">EPUB</option>
          <option value="pdf">PDF</option>
          <option value="txt">TXT</option>
          <option value="mobi">MOBI</option>
          <option value="docx">DOCX</option>
          <option value="html">HTML</option>
          <option value="md">Markdown</option>
        </select>
        <select v-model="filterStatus" class="select-input">
          <option value="">全部状态</option>
          <option value="UNREADING">未读</option>
          <option value="READING">正在阅读</option>
          <option value="FINISHED">已读完</option>
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
          @click="viewMode = 'card'"
        >
          <span>▦</span>
          <span>卡片</span>
        </button>
        <button
          class="btn"
          :class="{ active: viewMode === 'list' }"
          @click="viewMode = 'list'"
        >
          <span>☰</span>
          <span>列表</span>
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
        @click="$router.push(`/books/${book.id}`)"
      >
        <div class="book-cover">
          <img v-if="book.coverUrl" :src="book.coverUrl" alt="封面" class="cover-image" />
          <div v-else class="no-cover">
            <span>{{ book.title.charAt(0) }}</span>
          </div>
          <div class="book-format tag">{{ book.format.toUpperCase() }}</div>
        </div>

        <div class="book-info">
          <div class="book-title" :title="book.title">{{ book.title }}</div>
          <div class="book-author">{{ book.author || '未知作者' }}</div>
          <div class="book-actions">
            <button
              class="action-btn"
              :class="{ 'active': book.isFavorite }"
              @click.stop="handleToggleFavorite(book.id)"
            >
              {{ book.isFavorite ? '⭐' : '☆' }}
            </button>
            <button
              class="action-btn"
              :class="{ 'active': book.isWanted }"
              @click.stop="handleToggleWanted(book.id)"
            >
              {{ book.isWanted ? '✓' : '○' }}
            </button>
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
        @click="$router.push(`/books/${row.id}`)"
      >
        <div class="book-cover-small">
          <img v-if="row.coverUrl" :src="row.coverUrl" alt="封面" />
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
          <span class="book-date">{{ formatDate(row.createdAt) }}</span>
        </div>
        <div class="book-list-actions">
          <button class="btn btn-text" @click.stop="$router.push(`/reader/${row.id}`)">阅读</button>
          <button class="btn btn-text btn-danger" @click.stop="handleDelete(row.id)">删除</button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, confirm } from '@/utils/message'
import { useBookStore } from '@/stores/book'
import FileUpload from '@/components/FileUpload.vue'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()

const searchKeyword = ref('')
const filterFormat = ref('')
const filterStatus = ref('')
const sortBy = ref('createdAt')
const viewMode = ref<'card' | 'list'>('card')
const currentPage = ref(1)
const pageSize = ref(12)
const showUploadDialog = ref(false)

const totalPages = computed(() => Math.ceil(bookStore.totalElements / pageSize.value))

const handleUploadSuccess = () => {
  showUploadDialog.value = false
  loadBooks()
}

const loadBooks = async () => {
  if (searchKeyword.value) {
    await bookStore.searchBooks(searchKeyword.value, currentPage.value - 1, pageSize.value)
  } else {
    await bookStore.fetchBooks(currentPage.value - 1, pageSize.value, sortBy.value, 'desc')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadBooks()
}

const resetFilters = () => {
  searchKeyword.value = ''
  filterFormat.value = ''
  filterStatus.value = ''
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
  color: white;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  margin-bottom: var(--spacing-sm);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: rgba(255, 255, 255, 0.8);
}

/* 筛选区 */
.filter-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
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
  justify-content: flex-end;
  margin-bottom: var(--spacing-lg);
}

/* 加载中和空状态 */
.loading,
.empty {
  text-align: center;
  padding: var(--spacing-xl) var(--spacing-lg);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
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
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-xl);
}

.book-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
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
  height: 260px;
  position: relative;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-cover {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #007AFF 0%, #5AC8FA 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 56px;
  font-weight: 600;
}

.book-format {
  position: absolute;
  top: var(--spacing-md);
  right: var(--spacing-md);
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: white;
  font-size: var(--font-size-xs);
  padding: 4px 10px;
  border-radius: var(--radius-full);
}

.book-info {
  padding: var(--spacing-md);
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

.book-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.action-btn {
  padding: 6px 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--bg-secondary);
  cursor: pointer;
  font-size: var(--font-size-base);
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--bg-tertiary);
}

.action-btn.active {
  background: rgba(255, 149, 0, 0.15);
}

/* 书籍列表 */
.books-list {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: var(--spacing-xl);
}

.book-list-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.book-list-item:last-child {
  border-bottom: none;
}

.book-list-item:hover {
  background: rgba(255, 255, 255, 0.5);
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
  background: linear-gradient(135deg, #007AFF 0%, #5AC8FA 100%);
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
  background: rgba(255, 59, 48, 0.1) !important;
}

/* 分页 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--spacing-md);
}

.page-info {
  color: rgba(255, 255, 255, 0.8);
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
</style>
