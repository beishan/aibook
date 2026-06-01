<template>
  <div class="home-view">
    <div class="welcome-section">
      <h1>欢迎使用汗牛充栋</h1>
      <p>您的私人书库管理系统</p>
    </div>

    <el-row :gutter="20" class="stats-section">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #409eff">
            <el-icon :size="24"><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalBooks }}</div>
            <div class="stat-label">书籍总数</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #67c23a">
            <el-icon :size="24"><Reading /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.readingBooks }}</div>
            <div class="stat-label">正在阅读</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #e6a23c">
            <el-icon :size="24"><Star /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.favoriteBooks }}</div>
            <div class="stat-label">收藏书籍</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background-color: #f56c6c">
            <el-icon :size="24"><Finished /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.finishedBooks }}</div>
            <div class="stat-label">已读完</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-section">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最近阅读</span>
              <el-button type="primary" text @click="$router.push('/books')">查看更多</el-button>
            </div>
          </template>

          <div v-if="recentBooks.length === 0" class="empty-state">
            <el-empty description="暂无最近阅读的书籍" />
          </div>

          <div v-else class="recent-books">
            <div
              v-for="book in recentBooks"
              :key="book.id"
              class="recent-book-item"
              @click="$router.push(`/reader/${book.id}`)"
            >
              <div class="book-cover">
                <el-image
                  v-if="book.coverUrl"
                  :src="book.coverUrl"
                  fit="cover"
                />
                <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
              </div>
              <div class="book-info">
                <div class="book-title">{{ book.title }}</div>
                <div class="book-author">{{ book.author || '未知作者' }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>想读书单</span>
              <el-button type="primary" text @click="$router.push('/shelf')">查看更多</el-button>
            </div>
          </template>

          <div v-if="wantedBooks.length === 0" class="empty-state">
            <el-empty description="暂无想读的书籍" />
          </div>

          <div v-else class="wanted-list">
            <div
              v-for="book in wantedBooks"
              :key="book.id"
              class="wanted-item"
              @click="$router.push(`/books/${book.id}`)"
            >
              <span class="wanted-title">{{ book.title }}</span>
              <span class="wanted-author">{{ book.author || '' }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Document, Reading, Star, Finished } from '@element-plus/icons-vue'
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

onMounted(async () => {
  try {
    const data = await bookStore.fetchBooks(0, 5, 'updatedAt', 'desc')
    recentBooks.value = data.content

    // 获取统计数据
    stats.value.totalBooks = data.totalElements

    // 获取想读书单
    const wantedData = await bookStore.fetchBooks(0, 5, 'createdAt', 'desc')
    wantedBooks.value = wantedData.content.filter((b: any) => b.isWanted)
  } catch (error) {
    console.error('Failed to load home data:', error)
  }
})
</script>

<style scoped>
.home-view {
  padding: 20px;
}

.welcome-section {
  text-align: center;
  margin-bottom: 30px;
}

.welcome-section h1 {
  font-size: 28px;
  color: #333;
  margin-bottom: 10px;
}

.welcome-section p {
  color: #666;
  font-size: 16px;
}

.stats-section {
  margin-bottom: 30px;
}

.stat-card {
  cursor: pointer;
}

.stat-card .el-card__body {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-right: 15px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-top: 5px;
}

.content-section {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.empty-state {
  padding: 20px 0;
}

.recent-books {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
}

.recent-book-item {
  display: flex;
  align-items: center;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
  width: calc(50% - 8px);
}

.recent-book-item:hover {
  background-color: #f5f7fa;
}

.book-cover {
  width: 50px;
  height: 70px;
  border-radius: 4px;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}

.book-cover .el-image {
  width: 100%;
  height: 100%;
}

.no-cover {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
}

.book-info {
  flex: 1;
  overflow: hidden;
}

.book-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-author {
  font-size: 12px;
  color: #999;
}

.wanted-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.wanted-item {
  display: flex;
  flex-direction: column;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.wanted-item:hover {
  background-color: #f5f7fa;
}

.wanted-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.wanted-author {
  font-size: 12px;
  color: #999;
}
</style>
