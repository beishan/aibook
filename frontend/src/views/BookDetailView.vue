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
            <select
              class="category-select"
              :value="book.categoryId || ''"
              @change="handleCategoryChange"
            >
              <option value="">未分类</option>
              <option
                v-for="category in categoryStore.flatTree"
                :key="category.id"
                :value="category.id"
              >
                {{ `${'　'.repeat(category.depth)}${category.name}` }}
              </option>
            </select>
            <el-select
              v-model="selectedTagIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="添加标签"
              class="book-tag-select"
              :disabled="savingTags"
              @change="handleTagsChange"
            >
              <el-option
                v-for="tag in tagStore.tags"
                :key="tag.id"
                :label="tag.name"
                :value="tag.id"
              >
                <span class="tag-option-dot" :style="{ backgroundColor: tag.color }"></span>
                <span>{{ tag.name }}</span>
              </el-option>
            </el-select>
            <button class="btn btn-text tag-manage-link" @click="$router.push('/tags')">
              管理标签
            </button>
          </div>

          <div class="book-actions">
            <button class="btn btn-primary btn-large" @click="handleRead">
              <span>📖</span>
              <span>{{ hasReadingProgress ? '继续阅读' : '开始阅读' }}</span>
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
            <button class="btn" @click="showAddToListDialog = true">
              <span>📚</span>
              <span>添加到书单</span>
            </button>
            <button class="btn btn-danger" @click="handleDelete">
              <span>🗑️</span>
              <span>移入回收站</span>
            </button>
          </div>

          <div v-if="hasReadingProgress" class="current-reading-card">
            <div class="current-reading-icon">📖</div>
            <div class="current-reading-content">
              <span class="current-reading-label">当前正在阅读</span>
              <strong class="current-reading-title">{{ currentReadingChapter }}</strong>
              <el-progress
                :percentage="readingProgress?.totalProgress || 0"
                :stroke-width="7"
                :show-text="false"
              />
            </div>
            <div class="current-reading-meta">
              <span>{{ readingProgress?.totalProgress || 0 }}%</span>
              <button class="btn btn-text" @click="handleRead">继续阅读 ›</button>
            </div>
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
            :class="{ active: activeTab === 'toc' }"
            @click="activeTab = 'toc'"
          >
            目录
            <span v-if="tocItems.length" class="tab-count">{{ tocItems.length }}</span>
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

        <!-- 目录 -->
        <div v-show="activeTab === 'toc'" class="tab-content">
          <div v-if="tocLoading" class="toc-loading">
            <div class="loading-spinner-small"></div>
            <span>正在读取书籍目录...</span>
          </div>
          <div v-else-if="tocError" class="toc-empty">
            <span class="toc-empty-icon">⚠️</span>
            <p>{{ tocError }}</p>
            <button class="btn btn-text" @click="loadToc">重新加载</button>
          </div>
          <div v-else-if="tocItems.length === 0" class="toc-empty">
            <span class="toc-empty-icon">📑</span>
            <p>这本书暂未解析出章节目录</p>
            <span class="toc-empty-hint">
              EPUB、TXT 和 Markdown 格式可通过“重新解析”更新目录。
            </span>
          </div>
          <div v-else class="book-toc">
            <div class="toc-summary">
              <span>章节目录</span>
              <span>共 {{ tocItems.length }} 章</span>
            </div>
            <button
              v-for="(chapter, index) in paginatedTocItems"
              :key="`${chapter.index}-${chapter.href || chapter.title}`"
              class="toc-row"
              :class="{ 'is-current': isCurrentChapter(chapter) }"
              :style="{ paddingLeft: `${18 + (chapter.depth || 0) * 22}px` }"
              @click="openChapter(chapter)"
            >
              <span class="toc-number">
                {{ String((tocCurrentPage - 1) * tocPageSize + index + 1).padStart(2, '0') }}
              </span>
              <span class="toc-title">{{ chapter.title }}</span>
              <span class="toc-read-action">
                {{ isCurrentChapter(chapter) ? '阅读中' : '阅读 ›' }}
              </span>
            </button>
            <div class="toc-pagination">
              <el-pagination
                v-model:current-page="tocCurrentPage"
                v-model:page-size="tocPageSize"
                :page-sizes="tocPageSizeOptions"
                :total="tocItems.length"
                :pager-count="5"
                layout="total, sizes, prev, pager, next"
                background
                @size-change="handleTocPageSizeChange"
              />
            </div>
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
            <div v-if="book.chapterCount !== undefined && book.chapterCount !== null" class="info-item list-item">
              <span class="info-label">章节数</span>
              <span class="info-value">{{ book.chapterCount }} 章</span>
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
            <div class="notes-header">
              <h3>📝 读书笔记</h3>
              <p class="notes-hint">记录你的阅读心得和感悟</p>
            </div>
            <textarea
              v-model="notes"
              class="textarea"
              rows="8"
              placeholder="在这里写下你的读书笔记..."
            ></textarea>
            <div class="notes-actions">
              <button class="btn btn-primary" @click="handleSaveNotes">
                <span>💾</span>
                <span>保存笔记</span>
              </button>
            </div>
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

    <AddToBookListDialog
      :visible="showAddToListDialog"
      :book="book"
      @close="showAddToListDialog = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, confirm } from '@/utils/message'
import { useBookStore } from '@/stores/book'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import api from '@/utils/api'
import { scrapeBook, downloadCover } from '@/utils/scraper'
import { getCoverUrl } from '@/utils/cover'
import ScraperDialog from '@/components/ScraperDialog.vue'
import AddToBookListDialog from '@/components/AddToBookListDialog.vue'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

const book = ref<any>(null)
const loading = ref(true)
const notes = ref('')
const activeTab = ref('description')
const scraping = ref(false)
const reparsing = ref(false)
const downloadingCover = ref(false)
const showScraperDialog = ref(false)
const tocLoading = ref(false)
const tocError = ref('')

interface TocItem {
  index: number
  title: string
  href?: string
  startIndex?: number
  endIndex?: number
  depth?: number
}

interface ReadingProgress {
  currentChapter?: string
  currentChapterTitle?: string
  totalProgress?: number
  lastReadAt?: string
}

const tocItems = ref<TocItem[]>([])
const readingProgress = ref<ReadingProgress | null>(null)
const tocCurrentPage = ref(1)
const tocPageSize = ref(20)
const tocPageSizeOptions = [20, 50, 100]
const paginatedTocItems = computed(() => {
  const start = (tocCurrentPage.value - 1) * tocPageSize.value
  return tocItems.value.slice(start, start + tocPageSize.value)
})
const currentReadingChapter = computed(() => {
  const title = readingProgress.value?.currentChapterTitle?.trim()
  if (title) return title

  const legacyChapter = readingProgress.value?.currentChapter?.trim()
  if (legacyChapter && !legacyChapter.startsWith('epubcfi(')) return legacyChapter

  return '章节信息将在继续阅读后更新'
})
const hasReadingProgress = computed(() => Boolean(
  readingProgress.value?.lastReadAt
  || readingProgress.value?.currentChapterTitle
  || readingProgress.value?.currentChapter
  || (readingProgress.value?.totalProgress || 0) > 0,
))

// 书单相关
const showAddToListDialog = ref(false)
const scraperDialog = ref<InstanceType<typeof ScraperDialog> | null>(null)

// 编辑书名相关
const editingTitle = ref(false)
const editTitleValue = ref('')
const savingTitle = ref(false)
const selectedTagIds = ref<number[]>([])
const savingTags = ref(false)

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    book.value = await bookStore.fetchBookById(id)
    notes.value = book.value.notes || ''
    selectedTagIds.value = (book.value.tags || []).map((tag: any) => tag.id)
    await Promise.all([loadToc(), loadReadingProgress()])
    syncTocPageToCurrentChapter()
  } catch (error) {
    console.error('Failed to load book:', error)
  } finally {
    loading.value = false
  }
}

const loadReadingProgress = async () => {
  if (!book.value) return
  try {
    const response = await api.get(`/api/reading-progress/book/${book.value.id}`)
    readingProgress.value = response.data || null
  } catch (error) {
    readingProgress.value = null
    console.error('Failed to load reading progress:', error)
  }
}

const isCurrentChapter = (chapter: TocItem) => {
  const title = readingProgress.value?.currentChapterTitle
    || (
      readingProgress.value?.currentChapter?.startsWith('epubcfi(')
        ? ''
        : readingProgress.value?.currentChapter
    )
  return Boolean(title && chapter.title.trim() === title.trim())
}

const syncTocPageToCurrentChapter = () => {
  const currentIndex = tocItems.value.findIndex(isCurrentChapter)
  if (currentIndex >= 0) {
    tocCurrentPage.value = Math.floor(currentIndex / tocPageSize.value) + 1
  }
}

const handleRead = () => {
  router.push(`/reader/${book.value.id}`)
}

const loadToc = async () => {
  if (!book.value) return
  tocLoading.value = true
  tocError.value = ''
  tocCurrentPage.value = 1
  try {
    const response = await api.get(`/api/books/${book.value.id}/toc`)
    tocItems.value = response.data || []
  } catch (error: any) {
    tocItems.value = []
    tocError.value = error.response?.data?.message || '目录读取失败'
  } finally {
    tocLoading.value = false
  }
}

const handleTocPageSizeChange = () => {
  syncTocPageToCurrentChapter()
}

const openChapter = (chapter: TocItem) => {
  const query = book.value.format === 'epub' && chapter.href
    ? { chapterHref: chapter.href }
    : { chapterTitle: chapter.title }
  router.push({
    path: `/reader/${book.value.id}`,
    query,
  })
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

const handleCategoryChange = async (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  try {
    book.value = await bookStore.updateBookCategory(
      book.value.id,
      value ? Number(value) : undefined,
    )
    message.success('分类已更新')
  } catch {
    message.error('分类更新失败')
  }
}

const handleTagsChange = async () => {
  const previousIds = (book.value.tags || []).map((tag: any) => tag.id)
  savingTags.value = true
  try {
    book.value = await bookStore.updateBookTags(book.value.id, selectedTagIds.value)
    selectedTagIds.value = (book.value.tags || []).map((tag: any) => tag.id)
    message.success('标签已更新')
    await tagStore.fetchTags()
  } catch (error: any) {
    selectedTagIds.value = previousIds
    message.error(error.response?.data?.message || '标签更新失败')
  } finally {
    savingTags.value = false
  }
}

const handleDelete = async () => {
  const result = await confirm(
    '确定将这本书移入回收站吗？\n\nNAS 上的原始文件不会被删除或移动，可随时恢复。',
    '移入回收站',
  )
  if (result) {
    try {
      await bookStore.deleteBook(book.value.id)
      message.success('已移入回收站')
      router.push('/books')
    } catch (error) {
      message.error('移入回收站失败')
    }
  }
}

const setRating = async (rating: number) => {
  try {
    book.value.rating = rating
    await bookStore.updateBookMetadata(book.value.id, { rating })
    message.success('评分已更新')
  } catch (error) {
    message.error('评分更新失败')
  }
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
      await loadToc()
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

onMounted(() => {
  loadBook()
  categoryStore.refresh()
  tagStore.fetchTags()
})
</script>

<style scoped>
.book-detail-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

.category-select {
  padding: 5px 10px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--surface-card);
  color: var(--text-primary);
}

.tab-count {
  min-width: 20px;
  margin-left: 5px;
  padding: 1px 6px;
  border-radius: 999px;
  color: var(--primary);
  font-size: 11px;
  background: var(--primary-alpha-10);
}

.toc-loading,
.toc-empty {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: var(--text-secondary);
}

.toc-empty p {
  margin: 0;
}

.toc-empty-icon {
  font-size: 34px;
}

.toc-empty-hint {
  font-size: 12px;
}

.book-toc {
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.toc-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 13px;
}

.toc-summary span:first-child {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.toc-row {
  display: grid;
  width: 100%;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding-top: 13px;
  padding-right: 18px;
  padding-bottom: 13px;
  border: none;
  border-bottom: 1px solid var(--border-color-light);
  color: var(--text-primary);
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.toc-row:last-child {
  border-bottom: none;
}

.toc-row:hover {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.toc-row.is-current {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.toc-row.is-current .toc-title {
  font-weight: 600;
}

.toc-row.is-current .toc-read-action {
  opacity: 1;
  transform: translateX(0);
}

.toc-number {
  color: var(--text-tertiary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.toc-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toc-read-action {
  color: var(--text-secondary);
  font-size: 12px;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.toc-row:hover .toc-read-action {
  opacity: 1;
  transform: translateX(0);
}

.toc-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 16px 18px;
  border-top: 1px solid var(--border-color);
  overflow-x: auto;
}

.toc-pagination :deep(.el-pagination) {
  flex-shrink: 0;
}

.book-tag-select {
  width: 240px;
}

.tag-option-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 50%;
}

.tag-manage-link {
  padding: 4px 8px;
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

/* 书籍内容 */
.book-content {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
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
  background: var(--primary-gradient);
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
  align-items: center;
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
  box-shadow: 0 0 0 3px var(--primary-alpha-20);
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

.current-reading-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  max-width: 680px;
  margin-bottom: var(--spacing-lg);
  padding: 14px 16px;
  border: 1px solid var(--primary-alpha-20);
  border-radius: var(--radius-lg);
  background: var(--primary-alpha-10);
}

.current-reading-icon {
  display: flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--surface-card);
  font-size: 19px;
}

.current-reading-content {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.current-reading-label {
  color: var(--text-secondary);
  font-size: 12px;
}

.current-reading-title {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-reading-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--primary);
  font-size: 13px;
  font-weight: 600;
}

.current-reading-meta .btn {
  padding: 5px 8px;
  white-space: nowrap;
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

.btn-danger {
  color: var(--danger);
}

.btn-danger:hover {
  background: rgba(255, 59, 48, 0.1);
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
  color: var(--warning);
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
  background: var(--bg-primary);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.notes-header {
  margin-bottom: var(--spacing-lg);
}

.notes-header h3 {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--spacing-sm) 0;
}

.notes-hint {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin: 0;
}

.book-notes .textarea {
  min-height: 200px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  font-size: var(--font-size-base);
  line-height: 1.6;
  resize: vertical;
  width: 100%;
  box-sizing: border-box;
}

.book-notes .textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-10);
  outline: none;
}

.notes-actions {
  margin-top: var(--spacing-md);
  display: flex;
  justify-content: flex-end;
}

.notes-actions .btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
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

  .current-reading-card {
    grid-template-columns: auto minmax(0, 1fr);
    text-align: left;
  }

  .current-reading-meta {
    grid-column: 2;
    justify-content: space-between;
  }

  .toc-pagination {
    justify-content: flex-start;
    padding: 14px 12px;
  }
}

/* 弹窗样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.dialog {
  width: 420px;
  max-width: 90vw;
  max-height: 80vh;
  background: var(--surface-elevated);
  border-radius: var(--radius-xl);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  font-weight: 600;
  font-size: var(--font-size-lg);
  flex-shrink: 0;
}

.dialog-close {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  border: none;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.dialog-close:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.dialog-body {
  padding: var(--spacing-lg);
  overflow-y: auto;
  flex: 1;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
  flex-shrink: 0;
}

.loading-spinner-small {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
