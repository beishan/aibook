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
        <p class="page-subtitle">{{ bookList.description || '暂无描述' }} · {{ bookList.bookCount }} 本书</p>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 书籍列表 -->
    <div v-else-if="books.length > 0" class="books-grid">
      <div
        v-for="book in books"
        :key="book.id"
        class="book-card glass"
        @click="$router.push(`/books/${book.id}`)"
      >
        <div class="book-cover">
          <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
          <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
        </div>
        <div class="book-info">
          <div class="book-title">{{ book.title }}</div>
          <div class="book-author">{{ book.author || '未知作者' }}</div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty glass">
      <div class="empty-icon">📚</div>
      <p>书单中暂无书籍</p>
      <button class="btn btn-primary" @click="$router.push('/books')">去书库添加</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/utils/api'
import { getCoverUrl } from '@/utils/cover'

const route = useRoute()
const router = useRouter()

const bookList = ref<any>(null)
const books = ref<any[]>([])
const loading = ref(true)

const loadBookList = async () => {
  const listId = route.params.id
  try {
    const response = await api.get(`/api/booklists/${listId}`)
    bookList.value = response.data
    books.value = response.data.books || []
  } catch (error) {
    console.error('Failed to load book list:', error)
  } finally {
    loading.value = false
  }
}

onMounted(loadBookList)
</script>

<style scoped>
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
  height: 220px;
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
</style>
