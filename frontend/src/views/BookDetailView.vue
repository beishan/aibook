<template>
  <div class="book-detail-view">
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="book" class="book-content">
      <div class="book-header">
        <div class="book-cover">
          <el-image
            v-if="book.coverUrl"
            :src="book.coverUrl"
            fit="cover"
            class="cover-image"
          />
          <div v-else class="no-cover">
            <span>{{ book.title.charAt(0) }}</span>
          </div>
        </div>

        <div class="book-info">
          <h1 class="book-title">{{ book.title }}</h1>
          <div class="book-meta">
            <span v-if="book.author">
              <el-icon><User /></el-icon>
              {{ book.author }}
            </span>
            <span v-if="book.publisher">
              <el-icon><OfficeBuilding /></el-icon>
              {{ book.publisher }}
            </span>
            <span v-if="book.isbn">
              <el-icon><Document /></el-icon>
              ISBN: {{ book.isbn }}
            </span>
          </div>

          <div class="book-tags">
            <el-tag size="small">{{ book.format.toUpperCase() }}</el-tag>
            <el-tag v-if="book.language" size="small" type="info">{{ book.language }}</el-tag>
            <el-tag
              v-for="tag in book.tagNames"
              :key="tag"
              size="small"
              type="success"
            >
              {{ tag }}
            </el-tag>
          </div>

          <div class="book-actions">
            <el-button type="primary" size="large" @click="handleRead">
              <el-icon><Reading /></el-icon>
              开始阅读
            </el-button>
            <el-button
              :type="book.isFavorite ? 'warning' : 'default'"
              @click="handleToggleFavorite"
            >
              <el-icon><Star /></el-icon>
              {{ book.isFavorite ? '已收藏' : '收藏' }}
            </el-button>
            <el-button
              :type="book.isWanted ? 'success' : 'default'"
              @click="handleToggleWanted"
            >
              <el-icon><Clock /></el-icon>
              {{ book.isWanted ? '想读中' : '想读' }}
            </el-button>
          </div>

          <div class="book-rating">
            <span class="rating-label">评分：</span>
            <el-rate v-model="book.rating" disabled show-score />
          </div>
        </div>
      </div>

      <div class="book-body">
        <el-tabs>
          <el-tab-pane label="简介">
            <div class="book-description">
              <p v-if="book.description">{{ book.description }}</p>
              <p v-else class="no-description">暂无简介</p>
            </div>
          </el-tab-pane>

          <el-tab-pane label="详细信息">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="书名">{{ book.title }}</el-descriptions-item>
              <el-descriptions-item label="作者">{{ book.author || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="ISBN">{{ book.isbn || '无' }}</el-descriptions-item>
              <el-descriptions-item label="出版社">{{ book.publisher || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="出版日期">{{ book.publishDate || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="格式">{{ book.format.toUpperCase() }}</el-descriptions-item>
              <el-descriptions-item label="语言">{{ book.language || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="文件大小">{{ formatFileSize(book.fileSize) }}</el-descriptions-item>
              <el-descriptions-item label="添加时间">{{ formatDate(book.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDate(book.updatedAt) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="笔记">
            <div class="book-notes">
              <el-input
                v-model="notes"
                type="textarea"
                :rows="6"
                placeholder="添加读书笔记..."
              />
              <el-button type="primary" style="margin-top: 10px" @click="handleSaveNotes">
                保存笔记
              </el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <div v-else class="empty-state">
      <el-empty description="书籍不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, OfficeBuilding, Document, Reading, Star, Clock } from '@element-plus/icons-vue'
import { useBookStore } from '@/stores/book'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()

const book = ref<any>(null)
const loading = ref(true)
const notes = ref('')

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
    ElMessage.success('操作成功')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleToggleWanted = async () => {
  try {
    book.value = await bookStore.toggleWanted(book.value.id)
    ElMessage.success('操作成功')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleSaveNotes = () => {
  // TODO: 保存笔记到后端
  ElMessage.success('笔记保存成功')
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
  padding: 20px;
}

.loading-state,
.empty-state {
  padding: 40px 0;
}

.book-content {
  background: white;
  border-radius: 8px;
  padding: 30px;
}

.book-header {
  display: flex;
  gap: 30px;
  margin-bottom: 30px;
}

.book-cover {
  width: 200px;
  height: 280px;
  flex-shrink: 0;
}

.cover-image {
  width: 100%;
  height: 100%;
  border-radius: 8px;
}

.no-cover {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 64px;
  font-weight: bold;
  border-radius: 8px;
}

.book-info {
  flex: 1;
}

.book-title {
  font-size: 28px;
  color: #333;
  margin: 0 0 15px 0;
}

.book-meta {
  display: flex;
  gap: 20px;
  color: #666;
  margin-bottom: 15px;
}

.book-meta span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.book-tags {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.book-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.book-rating {
  display: flex;
  align-items: center;
}

.rating-label {
  color: #666;
}

.book-body {
  border-top: 1px solid #eee;
  padding-top: 20px;
}

.book-description {
  line-height: 1.8;
  color: #666;
}

.no-description {
  color: #999;
  font-style: italic;
}

.book-notes {
  max-width: 600px;
}
</style>
