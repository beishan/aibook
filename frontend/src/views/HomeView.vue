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

    <!-- 最近加入 -->
    <section class="recent-added-section card glass">
      <div class="card-header recent-added-header">
        <div>
          <span class="section-kicker">NEW TO THE LIBRARY</span>
          <h2>最近加入</h2>
        </div>
        <button class="btn btn-text" @click="$router.push('/books')">查看全部书籍</button>
      </div>

      <div v-if="homeLoading" class="recent-added-grid" aria-label="正在加载最近加入的书籍">
        <div v-for="index in 6" :key="index" class="added-book-card skeleton-card">
          <div class="added-cover skeleton-block"></div>
          <div class="skeleton-line wide"></div>
          <div class="skeleton-line"></div>
        </div>
      </div>

      <div v-else-if="recentlyAddedBooks.length === 0" class="empty recent-added-empty">
        <div class="empty-icon">📥</div>
        <p>书库里还没有书籍</p>
        <button class="btn btn-primary" @click="$router.push('/books')">前往添加书籍</button>
      </div>

      <div v-else class="recent-added-grid">
        <article
          v-for="book in recentlyAddedBooks"
          :key="book.id"
          class="added-book-card"
          tabindex="0"
          @click="$router.push(`/books/${book.id}`)"
          @keydown.enter="$router.push(`/books/${book.id}`)"
        >
          <div class="added-cover">
            <img
              v-if="book.coverUrl"
              :src="getCoverUrl(book.coverUrl)"
              :class="{ 'is-hidden': isBookCoverHidden(book.id) }"
              :alt="`${book.title}封面`"
            />
            <div v-else class="added-no-cover">
              <span>{{ book.title.charAt(0) }}</span>
              <small>{{ book.format?.toUpperCase() }}</small>
            </div>
            <span class="format-badge">{{ book.format?.toUpperCase() }}</span>
          </div>
          <div class="added-book-info">
            <h3 :title="book.title">{{ book.title }}</h3>
            <p :title="book.author || '未知作者'">{{ book.author || '未知作者' }}</p>
            <time :datetime="book.createdAt">{{ formatAddedDate(book.createdAt) }}</time>
          </div>
        </article>
      </div>
    </section>

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
              <img
                v-if="book.coverUrl"
                :src="getCoverUrl(book.coverUrl)"
                :class="{ 'is-hidden': isBookCoverHidden(book.id) }"
                alt="封面"
              />
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
import type { Book } from '@/stores/book'
import { getCoverUrl } from '@/utils/cover'
import { isBookCoverHidden } from '@/utils/imagePrivacy'

const bookStore = useBookStore()

const stats = ref({
  totalBooks: 0,
  readingBooks: 0,
  favoriteBooks: 0,
  finishedBooks: 0,
})

const recentBooks = ref<any[]>([])
const wantedBooks = ref<any[]>([])
const recentlyAddedBooks = ref<Book[]>([])
const homeLoading = ref(true)

const statsList = computed(() => [
  { icon: '📚', label: '书籍总数', value: stats.value.totalBooks, color: '#007AFF' },
  { icon: '📖', label: '正在阅读', value: stats.value.readingBooks, color: '#34C759' },
  { icon: '⭐', label: '收藏书籍', value: stats.value.favoriteBooks, color: '#FF9500' },
  { icon: '✅', label: '已读完', value: stats.value.finishedBooks, color: '#FF3B30' },
])

onMounted(async () => {
  try {
    const [recentData, addedData, wantedData] = await Promise.all([
      bookStore.fetchBooks(0, 5, 'updatedAt', 'desc'),
      bookStore.fetchBooks(0, 6, 'createdAt', 'desc'),
      bookStore.fetchBooks(0, 20, 'createdAt', 'desc'),
    ])
    recentBooks.value = recentData.content
    recentlyAddedBooks.value = addedData.content
    stats.value.totalBooks = addedData.totalElements
    wantedBooks.value = wantedData.content.filter((b: any) => b.isWanted).slice(0, 5)
  } catch (error) {
    console.error('Failed to load home data:', error)
  } finally {
    homeLoading.value = false
  }
})

function formatAddedDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '最近加入'

  const today = new Date()
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const startOfDate = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const days = Math.round((startOfToday - startOfDate) / 86400000)
  if (days === 0) return '今天加入'
  if (days === 1) return '昨天加入'
  if (days > 1 && days < 7) return `${days} 天前加入`
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日加入`
}
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
  color: var(--text-on-page-bg);
  text-shadow: var(--text-on-page-bg-shadow);
  margin-bottom: var(--spacing-sm);
}

.page-subtitle {
  font-size: var(--font-size-lg);
  color: var(--text-on-page-bg-secondary);
}

/* 统计卡片 */
.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-xl);
}

/* 最近加入：横向书封陈列，作为主页的主要内容入口 */
.recent-added-section {
  margin-bottom: var(--spacing-xl);
}

.recent-added-header {
  padding-bottom: var(--spacing-md);
}

.recent-added-header h2 {
  margin: 3px 0 0;
  color: var(--text-primary);
  font-size: var(--font-size-xl);
}

.section-kicker {
  color: var(--primary-color);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.recent-added-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: clamp(12px, 2vw, 22px);
  padding: var(--spacing-lg);
}

.added-book-card {
  min-width: 0;
  padding: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
  outline: none;
  transition: transform var(--transition-fast);
}

.added-book-card:hover,
.added-book-card:focus-visible {
  transform: translateY(-5px);
}

.added-book-card:focus-visible .added-cover {
  outline: 2px solid var(--primary-color);
  outline-offset: 3px;
}

.added-cover {
  position: relative;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border-radius: 8px 13px 13px 8px;
  background: var(--bg-secondary);
  box-shadow: 0 8px 18px rgba(25, 32, 48, 0.18);
}

.added-cover::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.22), transparent);
  pointer-events: none;
}

.added-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: filter 0.2s ease, transform 260ms ease;
}

.added-book-card:hover .added-cover img {
  transform: scale(1.035);
}

.added-cover img.is-hidden,
.added-book-card:hover .added-cover img.is-hidden {
  filter: blur(16px);
  transform: scale(1.15);
}

.added-no-cover {
  width: 100%;
  height: 100%;
  padding: 18px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: white;
  text-align: center;
  background: var(--primary-gradient);
}

.added-no-cover span {
  font-size: clamp(28px, 4vw, 42px);
  font-weight: 700;
}

.added-no-cover small {
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.45);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.format-badge {
  position: absolute;
  right: 7px;
  bottom: 7px;
  padding: 3px 6px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 4px;
  color: white;
  background: rgba(15, 23, 42, 0.72);
  backdrop-filter: blur(6px);
  font-size: 9px;
  font-weight: 700;
}

.added-book-info {
  padding: 11px 2px 2px;
}

.added-book-info h3,
.added-book-info p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.added-book-info h3 {
  margin: 0 0 5px;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: 650;
}

.added-book-info p {
  margin: 0 0 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.added-book-info time {
  color: var(--primary-color);
  font-size: 11px;
}

.recent-added-empty {
  min-height: 230px;
}

.skeleton-card {
  cursor: default;
  pointer-events: none;
}

.skeleton-block,
.skeleton-line {
  background: linear-gradient(100deg, var(--bg-secondary) 30%, var(--surface-hover) 50%, var(--bg-secondary) 70%);
  background-size: 220% 100%;
  animation: home-skeleton 1.4s ease-in-out infinite;
}

.skeleton-line {
  width: 65%;
  height: 10px;
  margin: 10px 2px 0;
  border-radius: 99px;
}

.skeleton-line.wide { width: 90%; margin-top: 13px; }

@keyframes home-skeleton {
  to { background-position-x: -220%; }
}

.stat-card {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
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
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
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
  background: var(--surface-hover);
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
  transition: filter 0.2s ease, transform 0.2s ease;
}

.book-cover img.is-hidden {
  filter: blur(16px);
  transform: scale(1.18);
}

.no-cover {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
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
  background: var(--surface-hover);
}

.wanted-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--primary-alpha-10);
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

  .recent-added-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats-section {
    grid-template-columns: 1fr;
  }

  .recent-books {
    grid-template-columns: 1fr;
  }

  .recent-added-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px 14px;
    padding: var(--spacing-md);
  }
}

@media (prefers-reduced-motion: reduce) {
  .added-cover img,
  .book-cover img {
    transition: none;
  }
}
</style>
