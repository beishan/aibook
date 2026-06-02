<template>
  <div class="shelf-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">我的书架</h1>
        <p class="page-subtitle">管理您的阅读收藏</p>
      </div>
      <button class="btn btn-primary" @click="showCreateListDialog = true">
        <span>➕</span>
        <span>创建书单</span>
      </button>
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
      <div v-else class="books-grid">
        <div
          v-for="book in favoriteBooks"
          :key="book.id"
          class="book-card glass"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div class="book-cover">
            <img v-if="book.coverUrl" :src="book.coverUrl" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div class="book-info">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
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
      <div v-else class="books-grid">
        <div
          v-for="book in readingBooks"
          :key="book.id"
          class="book-card glass"
          @click="$router.push(`/reader/${book.id}`)"
        >
          <div class="book-cover">
            <img v-if="book.coverUrl" :src="book.coverUrl" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div class="book-info">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
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
      <div v-else class="books-grid">
        <div
          v-for="book in finishedBooks"
          :key="book.id"
          class="book-card glass"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div class="book-cover">
            <img v-if="book.coverUrl" :src="book.coverUrl" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div class="book-info">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
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
      <div v-else class="books-grid">
        <div
          v-for="book in wantedBooks"
          :key="book.id"
          class="book-card glass"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div class="book-cover">
            <img v-if="book.coverUrl" :src="book.coverUrl" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div class="book-info">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
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
              <img v-if="book.coverUrl" :src="book.coverUrl" alt="封面" />
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
import { ref, computed, onMounted } from 'vue'
import { message } from '@/utils/message'
import { useBookStore } from '@/stores/book'
import api from '@/utils/api'

const bookStore = useBookStore()

const tabs = [
  { key: 'favorite', label: '收藏', icon: '⭐' },
  { key: 'reading', label: '正在阅读', icon: '📖' },
  { key: 'finished', label: '已读完', icon: '✅' },
  { key: 'wanted', label: '想读', icon: '📝' },
  { key: 'lists', label: '我的书单', icon: '📚' },
]

const activeTab = ref('favorite')
const showCreateListDialog = ref(false)
const bookLists = ref<any[]>([])

const newListForm = ref({
  name: '',
  description: '',
})

const favoriteBooks = computed(() => bookStore.books.filter((b) => b.isFavorite))
const readingBooks = computed(() => bookStore.books.filter((b) => b.readingStatus === 'READING'))
const finishedBooks = computed(() => bookStore.books.filter((b) => b.readingStatus === 'FINISHED'))
const wantedBooks = computed(() => bookStore.books.filter((b) => b.isWanted))

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
  message.info(`查看书单: ${list.name}`)
}

onMounted(loadBooks)
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
  color: white;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  margin-bottom: var(--spacing-sm);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: rgba(255, 255, 255, 0.8);
}

/* 空状态 */
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
}

.book-cover img {
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
}

/* 书单网格 */
.book-lists {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--spacing-lg);
}

.list-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
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
  background: linear-gradient(135deg, #007AFF 0%, #5AC8FA 100%);
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

/* 响应式 */
@media (max-width: 768px) {
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
