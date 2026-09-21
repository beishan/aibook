<template>
  <div class="author-management-view">
    <div class="page-header">
      <div>
        <h1 class="page-title">书籍作者</h1>
        <p class="page-subtitle">扫描、新增和编辑书籍时会自动登记识别到的作者</p>
      </div>
      <el-button type="primary" @click="dialogVisible = true">新增作者</el-button>
    </div>

    <div class="summary-grid">
      <div class="summary-card glass">
        <span class="summary-value">{{ summary.totalAuthorCount }}</span>
        <span class="summary-label">作者总数</span>
      </div>
      <div class="summary-card glass">
        <span class="summary-value">{{ summary.activeAuthorCount }}</span>
        <span class="summary-label">有关联书籍</span>
      </div>
      <div class="summary-card glass">
        <span class="summary-value">{{ summary.relatedBookCount }}</span>
        <span class="summary-label">书籍关联次数</span>
      </div>
    </div>

    <div class="author-card glass">
      <div class="author-toolbar">
        <el-input
          v-model="keyword"
          clearable
          class="author-search"
          placeholder="搜索作者名称"
          aria-label="搜索作者"
        >
          <template #prefix>🔍</template>
        </el-input>
        <el-select v-model="sort" class="author-sort" aria-label="作者排序">
          <el-option label="名称：正序" value="NAME_ASC" />
          <el-option label="名称：倒序" value="NAME_DESC" />
          <el-option label="加入时间：最新" value="CREATED_DESC" />
          <el-option label="加入时间：最早" value="CREATED_ASC" />
        </el-select>
        <span>{{ keyword.trim() ? `找到 ${totalElements} 位作者` : `共 ${summary.totalAuthorCount} 位作者` }}</span>
      </div>

      <div v-if="loading" class="state-panel">正在加载作者...</div>
      <div v-else-if="summary.totalAuthorCount === 0" class="state-panel empty-state">
        <span class="empty-icon">✍️</span>
        <p>还没有作者记录，扫描带作者信息的书籍后会自动出现在这里</p>
        <el-button type="primary" @click="dialogVisible = true">新增第一位作者</el-button>
      </div>
      <div v-else-if="authors.length === 0" class="state-panel empty-state">
        <span class="empty-icon">🔍</span>
        <p>没有找到“{{ keyword.trim() }}”</p>
        <el-button @click="keyword = ''">清除搜索</el-button>
      </div>
      <template v-else>
        <div class="author-table-header author-row">
          <span>作者名称</span>
          <span>关联书籍</span>
          <span>加入时间</span>
        </div>
        <div v-for="author in authors" :key="author.id" class="author-row author-item">
          <div class="author-name">
            <span class="author-avatar">{{ author.name.slice(0, 1).toLocaleUpperCase() }}</span>
            <strong>{{ author.name }}</strong>
          </div>
          <span>{{ author.bookCount }} 本</span>
          <span>{{ formatChinaDateTime(author.createdAt) }}</span>
        </div>
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="totalElements"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadAuthors"
            @size-change="handlePageSizeChange"
          />
        </div>
      </template>
    </div>

    <el-dialog v-model="dialogVisible" title="新增作者" width="min(440px, 92vw)" @closed="authorName = ''">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="作者名称">
          <el-input
            v-model.trim="authorName"
            maxlength="255"
            show-word-limit
            autofocus
            placeholder="请输入作者名称"
            @keyup.enter="createAuthor"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!authorName.trim()" @click="createAuthor">
          确认新增
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import api from '@/utils/api'
import { formatChinaDateTime } from '@/utils/dateTime'
import { message } from '@/utils/message'
import { usePreferencesStore } from '@/stores/preferences'

interface Author {
  id: number
  name: string
  bookCount: number
  createdAt: string
}

interface AuthorPage {
  content: Author[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  summary: {
    totalAuthorCount: number
    activeAuthorCount: number
    relatedBookCount: number
  }
}

const authors = ref<Author[]>([])
const totalElements = ref(0)
const summary = reactive({ totalAuthorCount: 0, activeAuthorCount: 0, relatedBookCount: 0 })
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const authorName = ref('')
const keyword = ref('')
const currentPage = ref(1)
const sort = ref('NAME_ASC')
const preferencesStore = usePreferencesStore()
const { authorPageSize: pageSize } = storeToRefs(preferencesStore)
let searchTimer: ReturnType<typeof setTimeout> | undefined
let loadSequence = 0

const loadAuthors = async () => {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const { data } = await api.get<AuthorPage>('/api/authors', { params: {
      page: currentPage.value - 1,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      sort: sort.value,
    } })
    if (sequence !== loadSequence) return
    authors.value = data.content
    totalElements.value = data.totalElements
    Object.assign(summary, data.summary)
    const lastPage = Math.max(1, data.totalPages)
    if (currentPage.value > lastPage) {
      currentPage.value = lastPage
      await loadAuthors()
    }
  } catch (error: any) {
    if (sequence !== loadSequence) return
    message.error(error.response?.data?.message || '作者列表加载失败')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

const handlePageSizeChange = (value: number) => {
  preferencesStore.setAuthorPageSize(value)
  currentPage.value = 1
  void loadAuthors()
}

watch(keyword, () => {
  currentPage.value = 1
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { void loadAuthors() }, 250)
})
watch(sort, () => {
  currentPage.value = 1
  void loadAuthors()
})

const createAuthor = async () => {
  const name = authorName.value.trim()
  if (!name || saving.value) return
  saving.value = true
  try {
    await api.post('/api/authors', { name })
    message.success('作者新增成功')
    dialogVisible.value = false
    authorName.value = ''
    currentPage.value = 1
    await loadAuthors()
  } catch (error: any) {
    message.error(error.response?.data?.message || '作者新增失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await preferencesStore.hydrate()
  await loadAuthors()
})
onUnmounted(() => { if (searchTimer) clearTimeout(searchTimer) })
</script>

<style scoped>
.author-management-view { display: flex; flex-direction: column; gap: 20px; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-title { margin: 0 0 6px; font-size: 26px; }
.page-subtitle { margin: 0; color: var(--text-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.summary-card { display: flex; flex-direction: column; gap: 5px; padding: 20px; border-radius: 14px; }
.summary-value { font-size: 28px; font-weight: 700; }
.summary-label, .author-toolbar, .author-item > span { color: var(--text-secondary); }
.author-card { padding: 20px; border-radius: 16px; }
.author-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; }
.author-toolbar > span { margin-left: auto; white-space: nowrap; }
.author-search { max-width: 360px; }
.author-sort { width: 180px; }
.author-row { display: grid; grid-template-columns: minmax(180px, 1fr) 120px 190px; align-items: center; gap: 16px; }
.author-table-header { padding: 10px 14px; color: var(--text-secondary); font-size: 13px; border-bottom: 1px solid var(--border-color); }
.author-item { min-height: 66px; padding: 8px 14px; border-bottom: 1px solid var(--border-color); }
.author-name { display: flex; align-items: center; gap: 12px; min-width: 0; }
.author-name strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.author-avatar { display: grid; place-items: center; width: 36px; height: 36px; flex: none; border-radius: 50%; color: var(--primary-color); background: color-mix(in srgb, var(--primary-color) 14%, transparent); font-weight: 700; }
.state-panel { padding: 52px 20px; text-align: center; color: var(--text-secondary); }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.empty-state p { margin: 0; }
.empty-icon { font-size: 34px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 18px; }
@media (max-width: 720px) {
  .summary-grid { grid-template-columns: 1fr; }
  .author-toolbar, .page-header { align-items: stretch; flex-direction: column; }
  .author-search { max-width: none; }
  .author-sort { width: 100%; }
  .author-toolbar > span { margin-left: 0; }
  .author-row { grid-template-columns: minmax(0, 1fr) 82px; }
  .author-row > :last-child { display: none; }
  .pagination-wrap { overflow-x: auto; justify-content: flex-start; }
}
</style>
