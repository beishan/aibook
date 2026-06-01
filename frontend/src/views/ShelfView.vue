<template>
  <div class="shelf-view">
    <div class="page-header">
      <h2>我的书架</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showCreateListDialog = true">
          <el-icon><Plus /></el-icon>
          创建书单
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 收藏 -->
      <el-tab-pane label="收藏" name="favorite">
        <div v-if="favoriteBooks.length === 0" class="empty-state">
          <el-empty description="暂无收藏的书籍">
            <el-button type="primary" @click="$router.push('/books')">去书库看看</el-button>
          </el-empty>
        </div>
        <div v-else class="books-grid">
          <div
            v-for="book in favoriteBooks"
            :key="book.id"
            class="book-card"
            @click="$router.push(`/books/${book.id}`)"
          >
            <div class="book-cover">
              <el-image v-if="book.coverUrl" :src="book.coverUrl" fit="cover" />
              <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
            </div>
            <div class="book-info">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-author">{{ book.author || '未知作者' }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 正在阅读 -->
      <el-tab-pane label="正在阅读" name="reading">
        <div v-if="readingBooks.length === 0" class="empty-state">
          <el-empty description="暂无正在阅读的书籍">
            <el-button type="primary" @click="$router.push('/books')">去书库看看</el-button>
          </el-empty>
        </div>
        <div v-else class="books-grid">
          <div
            v-for="book in readingBooks"
            :key="book.id"
            class="book-card"
            @click="$router.push(`/reader/${book.id}`)"
          >
            <div class="book-cover">
              <el-image v-if="book.coverUrl" :src="book.coverUrl" fit="cover" />
              <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
            </div>
            <div class="book-info">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-author">{{ book.author || '未知作者' }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 已读完 -->
      <el-tab-pane label="已读完" name="finished">
        <div v-if="finishedBooks.length === 0" class="empty-state">
          <el-empty description="暂无已读完的书籍" />
        </div>
        <div v-else class="books-grid">
          <div
            v-for="book in finishedBooks"
            :key="book.id"
            class="book-card"
            @click="$router.push(`/books/${book.id}`)"
          >
            <div class="book-cover">
              <el-image v-if="book.coverUrl" :src="book.coverUrl" fit="cover" />
              <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
            </div>
            <div class="book-info">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-author">{{ book.author || '未知作者' }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 想读 -->
      <el-tab-pane label="想读" name="wanted">
        <div v-if="wantedBooks.length === 0" class="empty-state">
          <el-empty description="暂无想读的书籍">
            <el-button type="primary" @click="$router.push('/books')">去书库看看</el-button>
          </el-empty>
        </div>
        <div v-else class="books-grid">
          <div
            v-for="book in wantedBooks"
            :key="book.id"
            class="book-card"
            @click="$router.push(`/books/${book.id}`)"
          >
            <div class="book-cover">
              <el-image v-if="book.coverUrl" :src="book.coverUrl" fit="cover" />
              <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
            </div>
            <div class="book-info">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-author">{{ book.author || '未知作者' }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 自定义书单 -->
      <el-tab-pane label="我的书单" name="lists">
        <div v-if="bookLists.length === 0" class="empty-state">
          <el-empty description="暂无书单">
            <el-button type="primary" @click="showCreateListDialog = true">创建书单</el-button>
          </el-empty>
        </div>
        <div v-else class="book-lists">
          <div
            v-for="list in bookLists"
            :key="list.id"
            class="list-card"
            @click="handleViewList(list)"
          >
            <div class="list-header">
              <div class="list-name">{{ list.name }}</div>
              <div class="list-count">{{ list.bookCount }} 本书</div>
            </div>
            <div class="list-description">{{ list.description || '暂无描述' }}</div>
            <div class="list-books">
              <div
                v-for="book in list.books?.slice(0, 4)"
                :key="book.id"
                class="list-book-cover"
              >
                <el-image v-if="book.coverUrl" :src="book.coverUrl" fit="cover" />
                <div v-else class="no-cover-small">{{ book.title.charAt(0) }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建书单对话框 -->
    <el-dialog v-model="showCreateListDialog" title="创建书单" width="500px">
      <el-form :model="newListForm" label-width="80px">
        <el-form-item label="书单名称">
          <el-input v-model="newListForm.name" placeholder="请输入书单名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="newListForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入书单描述（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateListDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateList">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useBookStore } from '@/stores/book'
import api from '@/utils/api'

const bookStore = useBookStore()

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
  if (tab === 'lists') {
    loadBookLists()
  }
}

const handleCreateList = async () => {
  if (!newListForm.value.name.trim()) {
    ElMessage.warning('请输入书单名称')
    return
  }

  try {
    await api.post('/api/booklists', newListForm.value)
    ElMessage.success('创建成功')
    showCreateListDialog.value = false
    newListForm.value = { name: '', description: '' }
    loadBookLists()
  } catch (error) {
    ElMessage.error('创建失败')
  }
}

const handleViewList = (list: any) => {
  // TODO: 打开书单详情
  ElMessage.info(`查看书单: ${list.name}`)
}

onMounted(loadBooks)
</script>

<style scoped>
.shelf-view {
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

.empty-state {
  padding: 40px 0;
}

.books-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 20px;
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
  font-size: 48px;
  font-weight: bold;
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
}

.book-lists {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.list-card {
  background: white;
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: box-shadow 0.3s;
}

.list-card:hover {
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.list-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.list-count {
  font-size: 12px;
  color: #999;
}

.list-description {
  font-size: 14px;
  color: #666;
  margin-bottom: 15px;
}

.list-books {
  display: flex;
  gap: 8px;
}

.list-book-cover {
  width: 50px;
  height: 70px;
  border-radius: 4px;
  overflow: hidden;
}

.list-book-cover .el-image {
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
</style>
