<template>
  <div class="bookmarks">
    <div class="bookmarks-header">
      <span>书签</span>
      <el-button type="primary" link @click="handleAddBookmark">
        <el-icon><Plus /></el-icon>
        添加书签
      </el-button>
    </div>

    <div v-if="bookmarks.length === 0" class="empty-bookmarks">
      <el-empty description="暂无书签" :image-size="60" />
    </div>

    <div v-else class="bookmarks-list">
      <div
        v-for="bookmark in bookmarks"
        :key="bookmark.id"
        class="bookmark-item"
        @click="handleClickBookmark(bookmark)"
      >
        <div class="bookmark-info">
          <div class="bookmark-title">{{ bookmark.title || '书签' }}</div>
          <div class="bookmark-position">第 {{ bookmark.page }} 页</div>
          <div class="bookmark-time">{{ formatTime(bookmark.createdAt) }}</div>
        </div>
        <el-button
          type="danger"
          link
          @click.stop="handleDeleteBookmark(bookmark)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
      page: 1, // TODO: 获取当前页码
    })
    bookmarks.value.push(response.data)
    ElMessage.success('书签添加成功')
  } catch (error) {
    ElMessage.error('书签添加失败')
  }
}

const handleClickBookmark = (bookmark: Bookmark) => {
  emit('goto-page', bookmark.page)
}

const handleDeleteBookmark = async (bookmark: Bookmark) => {
  await ElMessageBox.confirm('确定要删除这个书签吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })

  try {
    await api.delete(`/api/books/${props.bookId}/bookmarks/${bookmark.id}`)
    bookmarks.value = bookmarks.value.filter((b) => b.id !== bookmark.id)
    ElMessage.success('删除成功')
  } catch (error) {
    ElMessage.error('删除失败')
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
  padding: 10px;
}

.bookmarks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  font-weight: 500;
}

.empty-bookmarks {
  padding: 20px 0;
}

.bookmarks-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bookmark-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-radius: 6px;
  background: #f5f7fa;
  cursor: pointer;
  transition: background-color 0.3s;
}

.bookmark-item:hover {
  background: #ecf5ff;
}

.bookmark-info {
  flex: 1;
}

.bookmark-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.bookmark-position {
  font-size: 12px;
  color: #666;
  margin-bottom: 2px;
}

.bookmark-time {
  font-size: 12px;
  color: #999;
}
</style>
