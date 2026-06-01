<template>
  <div class="metadata-search">
    <el-dialog
      v-model="visible"
      title="搜索书籍元数据"
      width="600px"
      @close="handleClose"
    >
      <el-form :model="searchForm" label-width="80px">
        <el-form-item label="ISBN">
          <el-input
            v-model="searchForm.isbn"
            placeholder="输入 ISBN 搜索"
            clearable
          />
        </el-form-item>

        <el-form-item label="书名">
          <el-input
            v-model="searchForm.title"
            placeholder="输入书名搜索"
            clearable
          />
        </el-form-item>

        <el-form-item label="作者">
          <el-input
            v-model="searchForm.author"
            placeholder="输入作者名搜索（可选）"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading">
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 搜索结果 -->
      <div v-if="searchResult" class="search-result">
        <el-divider>搜索结果</el-divider>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="书名">{{ searchResult.title }}</el-descriptions-item>
          <el-descriptions-item label="作者">{{ searchResult.author || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="ISBN">{{ searchResult.isbn || '无' }}</el-descriptions-item>
          <el-descriptions-item label="出版社">{{ searchResult.publisher || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="出版日期">{{ searchResult.publishDate || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="页数">{{ searchResult.pageCount || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="简介" :span="2">
            {{ searchResult.description || '暂无简介' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="searchResult.coverUrl" class="cover-preview">
          <img :src="searchResult.coverUrl" alt="封面预览" />
        </div>

        <div class="result-actions">
          <el-button type="primary" @click="handleApply">应用到书籍</el-button>
          <el-button @click="searchResult = null">重新搜索</el-button>
        </div>
      </div>

      <div v-else-if="searched && !loading" class="no-result">
        <el-empty description="未找到相关书籍" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/utils/api'

const props = defineProps<{
  modelValue: boolean
  bookId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'apply', metadata: any): void
}>()

const visible = ref(props.modelValue)
const loading = ref(false)
const searched = ref(false)
const searchResult = ref<any>(null)

const searchForm = reactive({
  isbn: '',
  title: '',
  author: '',
})

const handleSearch = async () => {
  if (!searchForm.isbn && !searchForm.title) {
    ElMessage.warning('请输入 ISBN 或书名')
    return
  }

  loading.value = true
  searched.value = true
  searchResult.value = null

  try {
    let response
    if (searchForm.isbn) {
      response = await api.get(`/api/metadata/isbn/${searchForm.isbn}`)
    } else {
      response = await api.get('/api/metadata/search', {
        params: {
          title: searchForm.title,
          author: searchForm.author || undefined,
        },
      })
    }
    searchResult.value = response.data
  } catch (error: any) {
    if (error.response?.status === 404) {
      ElMessage.info('未找到相关书籍')
    } else {
      ElMessage.error('搜索失败')
    }
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.isbn = ''
  searchForm.title = ''
  searchForm.author = ''
  searchResult.value = null
  searched.value = false
}

const handleApply = () => {
  if (searchResult.value) {
    emit('apply', searchResult.value)
    handleClose()
  }
}

const handleClose = () => {
  emit('update:modelValue', false)
  handleReset()
}
</script>

<style scoped>
.search-result {
  margin-top: 20px;
}

.cover-preview {
  text-align: center;
  margin: 20px 0;
}

.cover-preview img {
  max-width: 150px;
  max-height: 200px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}

.no-result {
  padding: 20px 0;
}
</style>
