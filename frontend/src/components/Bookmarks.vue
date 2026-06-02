<template>
  <div class="bookmarks">
    <div class="bookmarks-header">
      <span>📑 书签</span>
      <button class="btn btn-text" @click="handleAddBookmark">
        <span>+</span>
        <span>添加书签</span>
      </button>
    </div>

    <div v-if="bookmarks.length === 0" class="empty">
      <div class="empty-icon">📑</div>
      <p>暂无书签</p>
    </div>

    <div v-else class="bookmarks-list">
      <div
        v-for="bookmark in bookmarks"
        :key="bookmark.id"
        class="bookmark-item"
        @click="handleClickBookmark(bookmark)"
      >
        <div class="bookmark-icon">🔖</div>
        <div class="bookmark-info">
          <div class="bookmark-title">{{ bookmark.title || '书签' }}</div>
          <div class="bookmark-meta">
            <span>第 {{ bookmark.page }} 页</span>
            <span>·</span>
            <span>{{ formatTime(bookmark.createdAt) }}</span>
          </div>
        </div>
        <button class="btn btn-icon btn-small" @click.stop="handleDeleteBookmark(bookmark)">
          <span>🗑️</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, confirm } from '@/utils/message'
import api from '@/utils/api'

const props = defineProps<{
  bookId: number
}>()

const emit = defineEmits<{
  (e: 'goto-page', page: number): void
}>()

interface Bookmark {
  id: number
  title: string
  page: number
  createdAt: string
}

const bookmarks = ref<Bookmark[]>([])

const loadBookmarks = async () => {
  try {
    const response = await api.get(`/api/books/${props.bookId}/bookmarks`)
    bookmarks.value = response.data
  } catch (error) {
    console.error('Failed to load bookmarks:', error)
  }
}

const handleAddBookmark = async () => {
  try {
    const response = await api.post(`/api/books/${props.bookId}/bookmarks`, {
      title: '书签',
      page: 1,
    })
    bookmarks.value.push(response.data)
    message.success('书签添加成功')
  } catch (error) {
    message.error('书签添加失败')
  }
}

const handleClickBookmark = (bookmark: Bookmark) => {
  emit('goto-page', bookmark.page)
}

const handleDeleteBookmark = async (bookmark: Bookmark) => {
  const result = await confirm('确定要删除这个书签吗？')
  if (result) {
    try {
      await api.delete(`/api/books/${props.bookId}/bookmarks/${bookmark.id}`)
      bookmarks.value = bookmarks.value.filter((b) => b.id !== bookmark.id)
      message.success('删除成功')
    } catch (error) {
      message.error('删除失败')
    }
  }
}

const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN')
}

onMounted(loadBookmarks)
</script>

<style scoped>
.bookmarks {
  padding: var(--spacing-md);
}

.bookmarks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
  font-weight: 600;
  font-size: var(--font-size-lg);
}

.empty {
  text-align: center;
  color: var(--text-secondary);
  padding: var(--spacing-xl);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

.bookmarks-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.bookmark-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.bookmark-item:hover {
  background: rgba(0, 122, 255, 0.1);
}

.bookmark-icon {
  font-size: 20px;
}

.bookmark-info {
  flex: 1;
  min-width: 0;
}

.bookmark-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bookmark-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.btn-icon {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: var(--radius-full);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-xs);
}

.btn-icon:hover {
  background: rgba(255, 59, 48, 0.1);
  color: var(--danger);
}
</style>
