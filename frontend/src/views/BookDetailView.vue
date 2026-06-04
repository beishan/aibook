<template>
  <div class="book-detail-view">
    <!-- 加载中 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 书籍内容 -->
    <div v-else-if="book" class="book-content glass">
      <!-- 返回按钮 -->
      <button class="back-btn" @click="$router.back()">
        <span>‹</span>
        <span>返回</span>
      </button>

      <!-- 书籍头部 -->
      <div class="book-header">
        <div class="book-cover">
          <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" class="cover-image" />
          <div v-else class="no-cover">
            <span>{{ book.title.charAt(0) }}</span>
          </div>
        </div>

        <div class="book-info">
          <!-- 书名显示/编辑 -->
          <div class="book-title-wrapper">
            <h1 v-if="!editingTitle" class="book-title">{{ book.title }}</h1>
            <div v-else class="title-edit-group">
              <input
                v-model="editTitleValue"
                class="title-edit-input"
                placeholder="请输入书名"
                @keyup.enter="saveTitle"
                @keyup.escape="cancelEditTitle"
                :disabled="savingTitle"
                ref="titleInput"
              />
              <div class="title-edit-actions">
                <button class="btn btn-primary btn-sm" @click="saveTitle" :disabled="savingTitle">
                  {{ savingTitle ? '保存中...' : '保存' }}
                </button>
                <button class="btn btn-sm" @click="cancelEditTitle" :disabled="savingTitle">取消</button>
              </div>
            </div>
            <button v-if="!editingTitle" class="btn-edit-title" @click="startEditTitle" title="编辑书名">
              ✏️
            </button>
          </div>

          <div class="book-meta">
            <span v-if="book.author" class="meta-item">
              <span class="meta-icon">👤</span>
              <span>{{ book.author }}</span>
            </span>
            <span v-if="book.publisher" class="meta-item">
              <span class="meta-icon">🏢</span>
              <span>{{ book.publisher }}</span>
            </span>
            <span v-if="book.isbn" class="meta-item">
              <span class="meta-icon">📄</span>
              <span>ISBN: {{ book.isbn }}</span>
            </span>
          </div>

          <div class="book-tags">
            <span class="tag tag-primary">{{ book.format.toUpperCase() }}</span>
            <span v-if="book.language" class="tag tag-info">{{ book.language }}</span>
            <span v-for="tag in book.tagNames" :key="tag" class="tag tag-success">
              {{ tag }}
            </span>
          </div>

          <div class="book-actions">
            <button class="btn btn-primary btn-large" @click="handleRead">
              <span>📖</span>
              <span>开始阅读</span>
            </button>
            <button class="btn" :class="book.isFavorite ? 'btn-warning' : ''" @click="handleToggleFavorite">
              <span>{{ book.isFavorite ? '⭐' : '☆' }}</span>
              <span>{{ book.isFavorite ? '已收藏' : '收藏' }}</span>
            </button>
            <button class="btn" :class="book.isWanted ? 'btn-success' : ''" @click="handleToggleWanted">
              <span>{{ book.isWanted ? '✓' : '○' }}</span>
              <span>{{ book.isWanted ? '想读中' : '想读' }}</span>
            </button>
            <button class="btn btn-scrape" @click="handleScrape" :disabled="scraping">
              <span>🔍</span>
              <span>{{ scraping ? '刮削中...' : '刮削元数据' }}</span>
            </button>
            <button class="btn" @click="handleDownloadCover" :disabled="downloadingCover">
              <span>🖼️</span>
              <span>{{ downloadingCover ? '下载中...' : '下载封面' }}</span>
            </button>
            <button
              v-if="book.format === 'txt' || book.format === 'md'"
              class="btn"
              @click="handleReparse"
              :disabled="reparsing"
            >
              <span>📑</span>
              <span>{{ reparsing ? '解析中...' : '重新解析章节' }}</span>
            </button>
          </div>

          <div class="book-rating">
            <span class="rating-label">评分：</span>
            <div class="rating-stars">
              <span
                v-for="i in 5"
                :key="i"
                class="star"
                :class="{ active: i <= book.rating }"
                @click="setRating(i)"
              >
                ★
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 内容区 -->
      <div class="book-body">
        <!-- 选项卡 -->
        <div class="tabs">
          <div
            class="tab-item"
            :class="{ active: activeTab === 'description' }"
            @click="activeTab = 'description'"
          >
            简介
          </div>
          <div
            class="tab-item"
            :class="{ active: activeTab === 'info' }"
            @click="activeTab = 'info'"
          >
            详细信息
          </div>
          <div
            class="tab-item"
            :class="{ active: activeTab === 'notes' }"
            @click="activeTab = 'notes'"
          >
            笔记
          </div>
        </div>

        <!-- 简介 -->
        <div v-show="activeTab === 'description'" class="tab-content">
          <div class="book-description">
            <p v-if="book.description">{{ book.description }}</p>
            <p v-else class="no-description">暂无简介</p>
          </div>
        </div>

        <!-- 详细信息 -->
        <div v-show="activeTab === 'info'" class="tab-content">
          <div class="info-list grouped-list">
            <div class="info-item list-item">
              <span class="info-label">书名</span>
              <span class="info-value">{{ book.title }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">作者</span>
              <span class="info-value">{{ book.author || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">ISBN</span>
              <span class="info-value">{{ book.isbn || '无' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">出版社</span>
              <span class="info-value">{{ book.publisher || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">出版日期</span>
              <span class="info-value">{{ book.publishDate || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">格式</span>
              <span class="info-value">{{ book.format.toUpperCase() }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">语言</span>
              <span class="info-value">{{ book.language || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">文件大小</span>
              <span class="info-value">{{ formatFileSize(book.fileSize) }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">添加时间</span>
              <span class="info-value">{{ formatDate(book.createdAt) }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">更新时间</span>
              <span class="info-value">{{ formatDate(book.updatedAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 笔记 -->
        <div v-show="activeTab === 'notes'" class="tab-content">
          <div class="book-notes">
            <textarea
              v-model="notes"
              class="textarea"
              rows="6"
              placeholder="添加读书笔记..."
            ></textarea>
            <button class="btn btn-primary" style="margin-top: var(--spacing-md)" @click="handleSaveNotes">
              <span>💾</span>
              <span>保存笔记</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty glass">
      <div class="empty-icon">📚</div>
      <p>书籍不存在</p>
      <button class="btn btn-primary" @click="$router.back()">返回书库</button>
    </div>

    <!-- 刮削对话框 -->
    <ScraperDialog
      ref="scraperDialog"
      :visible="showScraperDialog"
      @close="showScraperDialog = false"
      @refresh="loadBook"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from '@/utils/message'
import { useBookStore } from '@/stores/book'
import { scrapeBook, downloadCover } from '@/utils/scraper'
import { getCoverUrl } from '@/utils/cover'
import ScraperDialog from '@/components/ScraperDialog.vue'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()

const book = ref<any>(null)
const loading = ref(true)
const notes = ref('')
const activeTab = ref('description')
const scraping = ref(false)
const reparsing = ref(false)
const downloadingCover = ref(false)
const showScraperDialog = ref(false)
const scraperDialog = ref<InstanceType<typeof ScraperDialog> | null>(null)

// 编辑书名相关
const editingTitle = ref(false)
const editTitleValue = ref('')
const savingTitle = ref(false)

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    book.value = await bookStore.fetchBookById(id)
    notes.value = book.value.notes || ''
  } catch (error) {
    console.error('Failed to load book:', error)
  } finally {
    loading.value = false
  }
}

const handleRead = () => {
  router.push(`/reader/${book.value.id}`)
}

const handleToggleFavorite = async () => {
  try {
    book.value = await bookStore.toggleFavorite(book.value.id)
    message.success('操作成功')
  } catch (error) {
    message.error('操作失败')
  }
}

const handleToggleWanted = async () => {
  try {
    book.value = await bookStore.toggleWanted(book.value.id)
    message.success('操作成功')
  } catch (error) {
    message.error('操作失败')
  }
}

const setRating = (rating: number) => {
  book.value.rating = rating
}

const handleSaveNotes = async () => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/notes`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ notes: notes.value })
    })
    const result = await response.json()
    if (result.success) {
      message.success('笔记保存成功')
    } else {
      message.error(result.message || '保存失败')
    }
  } catch (error) {
    message.error('保存失败')
  }
}

const handleScrape = async () => {
  if (!book.value) return

  showScraperDialog.value = true
  scraping.value = true

  // 等待对话框渲染
  await nextTick()

  if (scraperDialog.value) {
    scraperDialog.value.startScrape(async () => {
      const result = await scrapeBook(book.value.id)
      // 刷新书籍数据
      if (result.success && result.book) {
        book.value = result.book
      }
      return result
    })
  }

  scraping.value = false
}

// 编辑书名
const startEditTitle = () => {
  editTitleValue.value = book.value.title
  editingTitle.value = true
}

const cancelEditTitle = () => {
  editingTitle.value = false
  editTitleValue.value = ''
}

const saveTitle = async () => {
  if (!editTitleValue.value.trim()) {
    message.error('书名不能为空')
    return
  }

  if (editTitleValue.value === book.value.title) {
    editingTitle.value = false
    return
  }

  savingTitle.value = true
  try {
    const updatedBook = await bookStore.updateBookMetadata(book.value.id, {
      title: editTitleValue.value.trim()
    })
    book.value = updatedBook
    editingTitle.value = false
    message.success('书名修改成功')
  } catch (error) {
    message.error('书名修改失败')
  } finally {
    savingTitle.value = false
  }
}

const handleDownloadCover = async () => {
  if (!book.value) return

  downloadingCover.value = true
  try {
    const result = await downloadCover(book.value.id)
    if (result.success) {
      message.success('封面下载成功')
      // 刷新书籍数据
      await loadBook()
    } else {
      message.error(result.message || '封面下载失败')
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '封面下载失败')
  } finally {
    downloadingCover.value = false
  }
}

const handleReparse = async () => {
  if (!book.value) return
  reparsing.value = true
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/parse-chapters`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` }
    })
    const result = await response.json()
    if (result.success) {
      book.value.chapterInfo = result.chapterInfo
      message.success('章节解析完成')
    } else {
      message.error(result.message || '解析失败')
    }
  } catch {
    message.error('解析失败')
  } finally {
    reparsing.value = false
  }
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '未知'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(loadBook)
</script>

<style scoped>
.book-detail-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
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

/* 书籍内容 */
.book-content {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
}

/* 返回按钮 */
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
  margin-bottom: var(--spacing-lg);
}

.back-btn:hover {
  background: var(--bg-tertiary);
}

/* 书籍头部 */
.book-header {
  display: flex;
  gap: var(--spacing-xl);
  margin-bottom: var(--spacing-xl);
}

.book-cover {
  width: 220px;
  height: 300px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
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
  font-size: 72px;
  font-weight: 600;
}

.book-info {
  flex: 1;
}

.book-title-wrapper {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.book-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.2;
}

.btn-edit-title {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 18px;
  padding: 4px;
  opacity: 0.6;
  transition: opacity var(--transition-fast);
  flex-shrink: 0;
  margin-top: 8px;
}

.btn-edit-title:hover {
  opacity: 1;
}

.title-edit-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.title-edit-input {
  width: 100%;
  padding: 8px 12px;
  font-size: var(--font-size-2xl);
  font-weight: 600;
  border: 2px solid var(--primary);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  color: var(--text-primary);
  outline: none;
}

.title-edit-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.2);
}

.title-edit-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.btn-sm {
  padding: 6px 16px;
  font-size: var(--font-size-sm);
}

.book-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.meta-icon {
  font-size: 16px;
}

.book-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-lg);
}

.book-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.btn-large {
  padding: 14px 28px;
  font-size: var(--font-size-lg);
}

.btn-scrape {
  background: linear-gradient(135deg, #5856D6 0%, #AF52DE 100%);
  color: white;
}

.btn-scrape:hover {
  opacity: 0.9;
}

.btn-scrape:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.book-rating {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.rating-label {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.rating-stars {
  display: flex;
  gap: 4px;
}

.star {
  color: var(--text-tertiary);
  font-size: 24px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.star:hover {
  transform: scale(1.2);
}

.star.active {
  color: #FF9500;
}

/* 内容区 */
.book-body {
  border-top: 1px solid var(--border-light);
  padding-top: var(--spacing-xl);
}

.book-description {
  line-height: 1.8;
  color: var(--text-secondary);
  font-size: var(--font-size-base);
}

.no-description {
  color: var(--text-tertiary);
  font-style: italic;
}

/* 信息列表 */
.info-list {
  background: var(--bg-primary);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.info-item {
  display: flex;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-light);
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  width: 100px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.info-value {
  flex: 1;
  color: var(--text-primary);
}

/* 笔记 */
.book-notes {
  max-width: 600px;
}

/* 响应式 */
@media (max-width: 768px) {
  .book-header {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .book-cover {
    width: 180px;
    height: 250px;
  }

  .book-meta,
  .book-tags,
  .book-actions {
    justify-content: center;
  }

  .book-rating {
    justify-content: center;
  }
}
</style>
