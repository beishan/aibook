<template>
  <div class="books-view">
    <div class="page-header">
      <h2>书库</h2>
      <div class="header-actions">
        <el-button type="primary" @click="$router.push('/books/upload')">
          <el-icon><Upload /></el-icon>
          上传书籍
        </el-button>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-row :gutter="20">
        <el-col :span="8">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索书名、作者、ISBN..."
            prefix-icon="Search"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterFormat" placeholder="格式" clearable>
            <el-option label="全部" value="" />
            <el-option label="EPUB" value="epub" />
            <el-option label="PDF" value="pdf" />
            <el-option label="TXT" value="txt" />
            <el-option label="MOBI" value="mobi" />
            <el-option label="DOCX" value="docx" />
            <el-option label="HTML" value="html" />
            <el-option label="Markdown" value="md" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterStatus" placeholder="阅读状态" clearable>
            <el-option label="全部" value="" />
            <el-option label="未读" value="UNREADING" />
            <el-option label="正在阅读" value="READING" />
            <el-option label="已读完" value="FINISHED" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="sortBy" placeholder="排序方式" @change="loadBooks">
            <el-option label="添加时间" value="createdAt" />
            <el-option label="书名" value="title" />
            <el-option label="作者" value="author" />
            <el-option label="最近阅读" value="updatedAt" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button @click="handleSearch">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 视图切换 -->
    <div class="view-toggle">
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="card">
          <el-icon><Grid /></el-icon>
        </el-radio-button>
        <el-radio-button value="list">
          <el-icon><List /></el-icon>
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 加载中 -->
    <div v-if="bookStore.loading" class="loading-state">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 空状态 -->
    <div v-else-if="bookStore.books.length === 0" class="empty-state">
      <el-empty description="书库空空如也，快去上传书籍吧">
        <el-button type="primary" @click="$router.push('/books/upload')">上传书籍</el-button>
      </el-empty>
    </div>

    <!-- 卡片视图 -->
    <div v-else-if="viewMode === 'card'" class="books-grid">
      <div
        v-for="book in bookStore.books"
        :key="book.id"
        class="book-card"
        @click="$router.push(`/books/${book.id}`)"
      >
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
          <div class="book-format">{{ book.format.toUpperCase() }}</div>
        </div>

        <div class="book-info">
          <div class="book-title" :title="book.title">{{ book.title }}</div>
          <div class="book-author">{{ book.author || '未知作者' }}</div>
          <div class="book-actions">
            <el-button
              :type="book.isFavorite ? 'warning' : 'info'"
              :icon="Star"
              circle
              size="small"
              @click.stop="handleToggleFavorite(book.id)"
            />
            <el-button
              :type="book.isWanted ? 'success' : 'info'"
              :icon="Clock"
              circle
              size="small"
              @click.stop="handleToggleWanted(book.id)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 列表视图 -->
    <el-table v-else :data="bookStore.books" style="width: 100%">
      <el-table-column label="书籍" min-width="300">
        <template #default="{ row }">
          <div class="book-list-item" @click="$router.push(`/books/${row.id}`)">
            <div class="book-cover-small">
              <el-image v-if="row.coverUrl" :src="row.coverUrl" fit="cover" />
              <div v-else class="no-cover-small">{{ row.title.charAt(0) }}</div>
            </div>
            <div class="book-list-info">
              <div class="book-list-title">{{ row.title }}</div>
              <div class="book-list-author">{{ row.author || '未知作者' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="format" label="格式" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.format.toUpperCase() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阅读状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.readingStatus)" size="small">
            {{ getStatusText(row.readingStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="添加时间" width="120">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="$router.push(`/reader/${row.id}`)">
            阅读
          </el-button>
          <el-button type="info" link @click="handleDelete(row.id)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination" v-if="bookStore.totalElements > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="bookStore.totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadBooks"
        @current-change="loadBooks"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, Clock, Upload, Grid, List } from '@element-plus/icons-vue'
import { useBookStore } from '@/stores/book'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()

const searchKeyword = ref('')
const filterFormat = ref('')
const filterStatus = ref('')
const sortBy = ref('createdAt')
const viewMode = ref<'card' | 'list'>('card')
const currentPage = ref(1)
const pageSize = ref(10)

const loadBooks = async () => {
  if (searchKeyword.value) {
    await bookStore.searchBooks(searchKeyword.value, currentPage.value - 1, pageSize.value)
  } else {
    await bookStore.fetchBooks(currentPage.value - 1, pageSize.value, sortBy.value, 'desc')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadBooks()
}

const resetFilters = () => {
  searchKeyword.value = ''
  filterFormat.value = ''
  filterStatus.value = ''
  sortBy.value = 'createdAt'
  currentPage.value = 1
  loadBooks()
}

const handleToggleFavorite = async (id: number) => {
  try {
    await bookStore.toggleFavorite(id)
    ElMessage.success('操作成功')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleToggleWanted = async (id: number) => {
  try {
    await bookStore.toggleWanted(id)
    ElMessage.success('操作成功')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (id: number) => {
  await ElMessageBox.confirm('确定要删除这本书吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })

  try {
    await bookStore.deleteBook(id)
    ElMessage.success('删除成功')
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const getStatusType = (status: string) => {
  switch (status) {
    case 'READING':
      return 'primary'
    case 'FINISHED':
      return 'success'
    default:
      return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'READING':
      return '正在阅读'
    case 'FINISHED':
      return '已读完'
    default:
      return '未读'
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

// 监听路由查询参数
watch(
  () => route.query.search,
  (newSearch) => {
    if (newSearch) {
      searchKeyword.value = newSearch as string
      handleSearch()
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (!route.query.search) {
    loadBooks()
  }
})
</script>

<style scoped>
.books-view {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: #333;
}

.filter-card {
  margin-bottom: 20px;
}

.view-toggle {
  margin-bottom: 20px;
}

.loading-state,
.empty-state {
  padding: 40px 0;
}

.books-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.book-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s, box-shadow 0.3s;
}

.book-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
}

.book-cover {
  height: 240px;
  position: relative;
}

.cover-image {
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
  font-size: 48px;
  font-weight: bold;
}

.book-format {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.book-info {
  padding: 12px;
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
  margin-bottom: 8px;
}

.book-actions {
  display: flex;
  gap: 8px;
}

.book-list-item {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.book-cover-small {
  width: 40px;
  height: 56px;
  border-radius: 4px;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}

.book-cover-small .el-image {
  width: 100%;
  height: 100%;
}

.no-cover-small {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  font-weight: bold;
}

.book-list-info {
  flex: 1;
}

.book-list-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.book-list-author {
  font-size: 12px;
  color: #999;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
