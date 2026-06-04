<template>
  <div class="shelf-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">我的书架</h1>
        <p class="page-subtitle">管理您的阅读收藏</p>
      </div>
      <div class="header-actions">
        <!-- 视图切换 -->
        <div class="view-toggle">
          <button
            class="view-btn"
            :class="{ active: viewMode === 'grid' }"
            @click="viewMode = 'grid'"
            title="网格视图"
          >
            <span>▦</span>
          </button>
          <button
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            @click="viewMode = 'list'"
            title="列表视图"
          >
            <span>☰</span>
          </button>
        </div>
        <!-- 卡片大小（仅网格模式显示） -->
        <div v-if="viewMode === 'grid'" class="card-size-selector">
          <button
            v-for="size in cardSizes"
            :key="size.value"
            class="size-btn"
            :class="{ active: cardSize === size.value }"
            @click="cardSize = size.value"
            :title="size.label"
          >
            {{ size.icon }}
          </button>
        </div>
        <button class="btn btn-primary" @click="showCreateListDialog = true">
          <span>➕</span>
          <span>创建书单</span>
        </button>
      </div>
    </div>

    <!-- 选项卡 -->
    <div class="tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="handleTabChange(tab.key)"
      >
        <span class="tab-icon">{{ tab.icon }}</span>
        <span>{{ tab.label }}</span>
      </div>
    </div>

    <!-- 收藏 -->
    <div v-show="activeTab === 'favorite'" class="tab-content">
      <div v-if="favoriteBooks.length === 0" class="empty glass">
        <div class="empty-icon">⭐</div>
        <p>暂无收藏的书籍</p>
        <button class="btn btn-primary" @click="$router.push('/books')">去书库看看</button>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in favoriteBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 正在阅读 -->
    <div v-show="activeTab === 'reading'" class="tab-content">
      <div v-if="readingBooks.length === 0" class="empty glass">
        <div class="empty-icon">📖</div>
        <p>暂无正在阅读的书籍</p>
        <button class="btn btn-primary" @click="$router.push('/books')">去书库看看</button>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in readingBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/reader/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 已读完 -->
    <div v-show="activeTab === 'finished'" class="tab-content">
      <div v-if="finishedBooks.length === 0" class="empty glass">
        <div class="empty-icon">✅</div>
        <p>暂无已读完的书籍</p>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in finishedBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 想读 -->
    <div v-show="activeTab === 'wanted'" class="tab-content">
      <div v-if="wantedBooks.length === 0" class="empty glass">
        <div class="empty-icon">📝</div>
        <p>暂无想读的书籍</p>
        <button class="btn btn-primary" @click="$router.push('/books')">去书库看看</button>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in wantedBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 我的书单 -->
    <div v-show="activeTab === 'lists'" class="tab-content">
      <div v-if="bookLists.length === 0" class="empty glass">
        <div class="empty-icon">📚</div>
        <p>暂无书单</p>
        <button class="btn btn-primary" @click="showCreateListDialog = true">创建书单</button>
      </div>
      <div v-else class="book-lists">
        <div
          v-for="list in bookLists"
          :key="list.id"
          class="list-card glass"
          @click="handleViewList(list)"
        >
          <div class="list-header">
            <div class="list-name">{{ list.name }}</div>
            <div class="list-count tag">{{ list.bookCount }} 本书</div>
          </div>
          <div class="list-description">{{ list.description || '暂无描述' }}</div>
          <div class="list-books">
            <div
              v-for="book in list.books?.slice(0, 4)"
              :key="book.id"
              class="list-book-cover"
            >
              <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
              <div v-else class="no-cover-small">{{ book.title.charAt(0) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建书单对话框 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showCreateListDialog" class="dialog-overlay" @click.self="showCreateListDialog = false">
          <div class="dialog">
            <div class="dialog-header">
              <span>📚 创建书单</span>
              <button class="dialog-close" @click="showCreateListDialog = false">✕</button>
            </div>
            <div class="dialog-body">
              <div class="form-group">
                <label class="form-label">书单名称</label>
                <input v-model="newListForm.name" type="text" class="input" placeholder="请输入书单名称" />
              </div>
              <div class="form-group">
                <label class="form-label">描述 <span class="optional">（可选）</span></label>
                <textarea v-model="newListForm.description" class="textarea" placeholder="请输入书单描述"></textarea>
              </div>
            </div>
            <div class="dialog-footer">
              <button class="btn" @click="showCreateListDialog = false">取消</button>
              <button class="btn btn-primary" @click="handleCreateList">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/utils/message'
import { useBookStore } from '@/stores/book'
import api from '@/utils/api'
import { getCoverUrl } from '@/utils/cover'

const router = useRouter()
const bookStore = useBookStore()

const STORAGE_KEY = 'shelf-settings'

const tabs = [
  { key: 'favorite', label: '收藏', icon: '⭐' },
  { key: 'reading', label: '正在阅读', icon: '📖' },
  { key: 'finished', label: '已读完', icon: '✅' },
  { key: 'wanted', label: '想读', icon: '📝' },
  { key: 'lists', label: '我的书单', icon: '📚' },
]

const cardSizes = [
  { value: 'small', label: '小', icon: '▪' },
  { value: 'medium', label: '中', icon: '▫' },
  { value: 'large', label: '大', icon: '◻' },
]

const activeTab = ref('favorite')
const showCreateListDialog = ref(false)
const bookLists = ref<any[]>([])
const viewMode = ref<'grid' | 'list'>('grid')
const cardSize = ref<'small' | 'medium' | 'large'>('medium')

const newListForm = ref({
  name: '',
  description: '',
})

const favoriteBooks = computed(() => bookStore.books.filter((b) => b.isFavorite))
const readingBooks = computed(() => bookStore.books.filter((b) => b.readingStatus === 'READING'))
const finishedBooks = computed(() => bookStore.books.filter((b) => b.readingStatus === 'FINISHED'))
const wantedBooks = computed(() => bookStore.books.filter((b) => b.isWanted))

// 加载设置
const loadSettings = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const settings = JSON.parse(saved)
      if (settings.viewMode) viewMode.value = settings.viewMode
      if (settings.cardSize) cardSize.value = settings.cardSize
    }
  } catch (e) { /* ignore */ }
}

// 保存设置
const saveSettings = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      viewMode: viewMode.value,
      cardSize: cardSize.value,
    }))
  } catch (e) { /* ignore */ }
}

// 监听设置变化
watch([viewMode, cardSize], saveSettings)

const loadBooks = async () => {
  await bookStore.fetchBooks(0, 100, 'createdAt', 'desc')
}

const loadBookLists = async () => {
  try {
    const response = await api.get('/api/booklists')
    bookLists.value = response.data
  } catch (error) {
    console.error('Failed to load book lists:', error)
  }
}

const handleTabChange = (tab: string) => {
  activeTab.value = tab
  if (tab === 'lists') {
    loadBookLists()
  }
}

const handleCreateList = async () => {
  if (!newListForm.value.name.trim()) {
    message.warning('请输入书单名称')
    return
  }

  try {
    await api.post('/api/booklists', newListForm.value)
    message.success('创建成功')
    showCreateListDialog.value = false
    newListForm.value = { name: '', description: '' }
    loadBookLists()
  } catch (error) {
    message.error('创建失败')
  }
}

const handleViewList = (list: any) => {
  router.push(`/booklists/${list.id}`)
}

onMounted(() => {
  loadSettings()
  loadBooks()
})
</script>

<style scoped>
.shelf-view {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

/* 视图切换 */
.view-toggle {
  display: flex;
  background: var(--surface-card);
  border: var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.view-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 16px;
}

.view-btn:hover {
  background: var(--bg-secondary);
}

.view-btn.active {
  background: var(--primary);
  color: white;
}

/* 卡片大小选择器 */
.card-size-selector {
  display: flex;
  background: var(--surface-card);
  border: var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.size-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 12px;
}

.size-btn:hover {
  background: var(--bg-secondary);
}

.size-btn.active {
  background: var(--primary);
  color: white;
}

/* 空状态 */
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

/* 书籍网格 */
.books-grid {
  display: grid;
  gap: var(--spacing-md);
}

/* 小卡片 */
.books-grid.card-small {
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
}

.books-grid.card-small .book-cover {
  height: 160px;
}

.books-grid.card-small .no-cover {
  font-size: 36px;
}

.books-grid.card-small .book-info {
  padding: var(--spacing-xs) var(--spacing-sm);
}

.books-grid.card-small .book-title {
  font-size: var(--font-size-xs);
}

.books-grid.card-small .book-author {
  font-size: 10px;
}

/* 中卡片（默认） */
.books-grid.card-medium {
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
}

.books-grid.card-medium .book-cover {
  height: 220px;
}

.books-grid.card-medium .no-cover {
  font-size: 48px;
}

/* 大卡片 */
.books-grid.card-large {
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
}

.books-grid.card-large .book-cover {
  height: 300px;
}

.books-grid.card-large .no-cover {
  font-size: 64px;
}

.books-grid.card-large .book-info {
  padding: var(--spacing-md) var(--spacing-lg);
}

.books-grid.card-large .book-title {
  font-size: var(--font-size-lg);
}

.books-grid.card-large .book-author {
  font-size: var(--font-size-base);
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
  height: 200px;
}

.book-cover img {
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

/* 书籍列表 */
.books-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.book-list-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.book-list-item:hover {
  transform: translateX(4px);
  box-shadow: var(--shadow-md);
}

.book-list-cover {
  width: 50px;
  height: 70px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
}

.book-list-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.book-list-cover .no-cover {
  font-size: 24px;
}

.book-list-info {
  flex: 1;
  min-width: 0;
}

.book-list-info .book-title {
  font-size: var(--font-size-base);
  margin-bottom: var(--spacing-xs);
}

.book-list-info .book-author {
  font-size: var(--font-size-sm);
}

.book-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.book-format {
  background: var(--bg-secondary);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-weight: 500;
}

/* 书单网格 */
.book-lists {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--spacing-lg);
}

.list-card {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.list-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.list-name {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.list-count {
  font-size: var(--font-size-xs);
}

.list-description {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
  line-height: 1.5;
}

.list-books {
  display: flex;
  gap: var(--spacing-sm);
}

.list-book-cover {
  width: 50px;
  height: 70px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.list-book-cover img {
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
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.optional {
  font-weight: 400;
  color: var(--text-tertiary);
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
  background: var(--surface-elevated);
  border-radius: var(--radius-xl);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  font-weight: 600;
  font-size: var(--font-size-lg);
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
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.form-label {
  display: block;
  margin-bottom: var(--spacing-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.input,
.textarea {
  width: 100%;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: var(--font-size-base);
  transition: all var(--transition-fast);
  box-sizing: border-box;
}

.input::placeholder,
.textarea::placeholder {
  color: var(--text-tertiary);
}

.input:focus,
.textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-20);
}

.textarea {
  min-height: 80px;
  resize: vertical;
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

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: var(--spacing-md);
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .tabs {
    flex-wrap: wrap;
    justify-content: center;
  }

  .tab-item {
    padding: 8px 12px;
    font-size: var(--font-size-xs);
  }
}
</style>
