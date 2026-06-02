<template>
  <div class="home-view">
    <!-- 欢迎区 -->
    <div class="welcome-section">
      <h1 class="page-title">欢迎回来</h1>
      <p class="page-subtitle">您的私人书库管理系统</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-section">
      <div
        v-for="stat in statsList"
        :key="stat.label"
        class="stat-card glass"
        :style="{ '--accent-color': stat.color }"
      >
        <div class="stat-icon-wrapper">
          <span class="stat-icon">{{ stat.icon }}</span>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-section">
      <!-- 最近阅读 -->
      <div class="card glass">
        <div class="card-header">
          <span>📖 最近阅读</span>
          <button class="btn btn-text" @click="$router.push('/books')">查看更多</button>
        </div>

        <div v-if="recentBooks.length === 0" class="empty">
          <div class="empty-icon">📚</div>
          <p>暂无最近阅读的书籍</p>
        </div>

        <div v-else class="recent-books">
          <div
            v-for="book in recentBooks"
            :key="book.id"
            class="recent-book-item"
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

      <!-- 想读书单 -->
      <div class="card glass">
        <div class="card-header">
          <span>⭐ 想读书单</span>
          <button class="btn btn-text" @click="$router.push('/shelf')">查看更多</button>
        </div>

        <div v-if="wantedBooks.length === 0" class="empty">
          <div class="empty-icon">📝</div>
          <p>暂无想读的书籍</p>
        </div>

        <div v-else class="wanted-list">
          <div
            v-for="book in wantedBooks"
            :key="book.id"
            class="wanted-item"
            @click="$router.push(`/books/${book.id}`)"
          >
            <div class="wanted-icon">📖</div>
            <div class="wanted-content">
              <div class="wanted-title">{{ book.title }}</div>
              <div class="wanted-author">{{ book.author || '未知作者' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useBookStore } from '@/stores/book'

const bookStore = useBookStore()

const stats = ref({
  totalBooks: 0,
  readingBooks: 0,
  favoriteBooks: 0,
  finishedBooks: 0,
})

const recentBooks = ref<any[]>([])
const wantedBooks = ref<any[]>([])

const statsList = computed(() => [
  { icon: '📚', label: '书籍总数', value: stats.value.totalBooks, color: '#007AFF' },
  { icon: '📖', label: '正在阅读', value: stats.value.readingBooks, color: '#34C759' },
  { icon: '⭐', label: '收藏书籍', value: stats.value.favoriteBooks, color: '#FF9500' },
  { icon: '✅', label: '已读完', value: stats.value.finishedBooks, color: '#FF3B30' },
])

onMounted(async () => {
  try {
    const data = await bookStore.fetchBooks(0, 5, 'updatedAt', 'desc')
    recentBooks.value = data.content
    stats.value.totalBooks = data.totalElements

    const wantedData = await bookStore.fetchBooks(0, 5, 'createdAt', 'desc')
    wantedBooks.value = wantedData.content.filter((b: any) => b.isWanted)
  } catch (error) {
    console.error('Failed to load home data:', error)
  }
})
</script>

<style scoped>
.home-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

/* 欢迎区 */
.welcome-section {
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
  font-size: var(--font-size-lg);
  color: rgba(255, 255, 255, 0.8);
}

/* 统计卡片 */
.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-xl);
}

.stat-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  transition: all var(--transition-normal);
  cursor: pointer;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}

.stat-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  background: var(--accent-color);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-icon {
  font-size: 28px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: var(--font-size-3xl);
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-top: var(--spacing-xs);
}

/* 内容区 */
.content-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--spacing-lg);
}

/* 卡片 */
.card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  font-weight: 600;
  font-size: var(--font-size-lg);
}

/* 空状态 */
.empty {
  text-align: center;
  color: var(--text-secondary);
  padding: var(--spacing-xl) var(--spacing-lg);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

/* 最近阅读 */
.recent-books {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
}

.recent-book-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.recent-book-item:hover {
  background: rgba(255, 255, 255, 0.5);
}

.book-cover {
  width: 60px;
  height: 80px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: var(--shadow-md);
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
  font-size: var(--font-size-xl);
  font-weight: 600;
}

.book-info {
  flex: 1;
  min-width: 0;
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

/* 想读书单 */
.wanted-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg);
}

.wanted-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.wanted-item:hover {
  background: rgba(255, 255, 255, 0.5);
}

.wanted-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: rgba(0, 122, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.wanted-content {
  flex: 1;
  min-width: 0;
}

.wanted-title {
  font-size: var(--font-size-base);
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wanted-author {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

/* 响应式 */
@media (max-width: 1024px) {
  .stats-section {
    grid-template-columns: repeat(2, 1fr);
  }

  .content-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-section {
    grid-template-columns: 1fr;
  }

  .recent-books {
    grid-template-columns: 1fr;
  }
}
</style>
