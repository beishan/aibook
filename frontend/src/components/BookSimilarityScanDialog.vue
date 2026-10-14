<template>
  <el-dialog
    :model-value="visible"
    title="扫描相似书名"
    width="min(900px, calc(100vw - 24px))"
    append-to-body
    :close-on-click-modal="false"
    class="book-similarity-dialog"
    @close="handleClose"
  >
    <p class="scan-description">
      扫描当前账户的整个书库，不受当前分页和筛选影响。忽略空格、标点、大小写与常见版本后缀，
      查找同名或相似度达到 80% 的书名；不足四个字符的书名仅匹配同名。
    </p>
    <p class="scan-description">
      分组表示书籍间存在相似关系，不保证组内每两本都相同。请核对作者、卷册和内容，勾选后选择保留的书籍。
    </p>

    <div class="scan-toolbar">
      <span v-if="task">
        已检查 {{ task.processedBooks }} / {{ task.totalBooks }} 本
        <template v-if="task.status === 'COMPLETED'">
          · {{ task.totalGroups }} 组 · {{ task.matchedBooks }} 本候选
        </template>
      </span>
      <span v-else>正在创建扫描任务…</span>
      <el-button :loading="starting" :disabled="running || busy || !!loadingGroup" @click="startScan">
        重新扫描
      </el-button>
    </div>

    <el-progress v-if="running" :percentage="progress" :stroke-width="10" />
    <p v-if="running" class="scan-status" role="status">
      {{ task?.message }}，关闭窗口后仍会继续扫描。
    </p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-button v-if="error && task && task.status !== 'FAILED'" class="retry-button" @click="loadPage(currentPage)">
      重试获取进度
    </el-button>
    <el-empty
      v-if="task?.status === 'COMPLETED' && task.totalGroups === 0"
      description="未发现相似书名"
    />

    <div v-if="task?.status === 'COMPLETED'" class="similarity-groups" :aria-busy="busy">
      <section v-for="group in task.groups" :key="group.id" class="similarity-group">
        <header class="group-header">
          <div>
            <strong>{{ group.books[0]?.title }}</strong>
            <p>{{ group.books.length }} 本 · {{ group.similarity === 100 ? '规范化后同名' : `匹配相似度 ${group.similarity}% 起` }}</p>
          </div>
          <el-button
            type="primary"
            :disabled="busy || !!loadingGroup || (selections[group.id]?.length || 0) < 2"
            :loading="loadingGroup === group.id"
            @click="openMerge(group)"
          >
            合并所选 {{ selections[group.id]?.length || 0 }} 本
          </el-button>
        </header>
        <p v-if="hasDifferentAuthors(group)" class="author-warning">作者不同，请确认是否为同一本书。</p>
        <el-checkbox-group v-model="selections[group.id]" class="candidate-list" :disabled="!!loadingGroup || busy">
          <div v-for="book in group.books" :key="book.id" class="candidate-row">
            <el-checkbox :value="book.id" class="candidate-selection">
              <span class="candidate-copy">
                <strong>{{ book.title }}</strong>
                <small>{{ book.author || '未知作者' }} · {{ formatLabel(book.format) }} · #{{ book.id }}</small>
              </span>
            </el-checkbox>
            <router-link :to="`/books/${book.id}`" target="_blank">详情</router-link>
          </div>
        </el-checkbox-group>
      </section>
    </div>

    <div v-if="task && task.totalGroups > pageSize" class="scan-pagination">
      <el-button :disabled="busy || currentPage === 0 || !!loadingGroup" @click="loadPage(currentPage - 1)">上一页</el-button>
      <span>{{ currentPage + 1 }} / {{ Math.ceil(task.totalGroups / pageSize) }}</span>
      <el-button
        :disabled="busy || (currentPage + 1) * pageSize >= task.totalGroups || !!loadingGroup"
        @click="loadPage(currentPage + 1)"
      >下一页</el-button>
    </div>

    <template #footer>
      <el-button :disabled="!!loadingGroup || showMerge" @click="handleClose">
        {{ running ? '后台运行' : '关闭' }}
      </el-button>
    </template>
  </el-dialog>

  <BookMergeDialog
    :visible="showMerge"
    :books="mergeBooks"
    @close="showMerge = false"
    @complete="handleMerged"
  />
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import type { Book } from '@/stores/book'
import api from '@/utils/api'
import { message } from '@/utils/message'
import BookMergeDialog from '@/components/BookMergeDialog.vue'

interface Candidate {
  id: number
  title: string
  author?: string
  format: string
}

interface SimilarityGroup {
  id: string
  similarity: number
  books: Candidate[]
}

interface ScanTask {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  message: string
  totalBooks: number
  processedBooks: number
  matchedBooks: number
  totalGroups: number
  groups: SimilarityGroup[]
}

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ close: []; merged: [] }>()
const pageSize = 20
const task = ref<ScanTask | null>(null)
const currentPage = ref(0)
const starting = ref(false)
const busy = ref(false)
const error = ref('')
const selections = ref<Record<string, number[]>>({})
const loadingGroup = ref<string | null>(null)
const showMerge = ref(false)
const mergeBooks = ref<Book[]>([])
let timer: ReturnType<typeof setTimeout> | undefined
let generation = 0

const running = computed(() => task.value?.status === 'PENDING' || task.value?.status === 'RUNNING')
const progress = computed(() => task.value?.totalBooks
  ? Math.min(100, Math.round(task.value.processedBooks / task.value.totalBooks * 100))
  : 0)
const formatLabel = (format: string) => format === 'structured' ? '在线章节' : format?.toUpperCase()
const hasDifferentAuthors = (group: SimilarityGroup) => new Set(
  group.books.map(book => book.author?.trim()).filter(Boolean),
).size > 1

const stopPolling = () => {
  if (timer) clearTimeout(timer)
  timer = undefined
}

const acceptTask = (data: ScanTask) => {
  task.value = data
  if (data.status === 'COMPLETED') {
    selections.value = Object.fromEntries(data.groups.map(group => [group.id, group.books.map(book => book.id)]))
  } else if (data.status === 'FAILED') {
    error.value = data.message
  }
  if (running.value && props.visible) {
    timer = setTimeout(() => loadPage(0), 1000)
  }
}

const startScan = async () => {
  stopPolling()
  const request = ++generation
  starting.value = true
  error.value = ''
  task.value = null
  selections.value = {}
  currentPage.value = 0
  try {
    const { data } = await api.post<ScanTask>('/api/books/similarity-scans')
    if (request === generation) acceptTask(data)
  } catch (cause: any) {
    if (request === generation) error.value = cause.response?.data?.message || '创建扫描任务失败，请重试'
  } finally {
    if (request === generation) starting.value = false
  }
}

const loadPage = async (page: number) => {
  if (!task.value) return
  stopPolling()
  const request = ++generation
  busy.value = true
  error.value = ''
  try {
    const { data } = await api.get<ScanTask>(`/api/books/similarity-scans/${task.value.taskId}`, {
      params: { page, size: pageSize },
    })
    if (request !== generation) return
    currentPage.value = page
    acceptTask(data)
  } catch (cause: any) {
    if (request === generation) {
      error.value = cause.response?.data?.message || '获取扫描结果失败，请重试'
      if (cause.response?.status === 404 && task.value) task.value.status = 'FAILED'
    }
  } finally {
    if (request === generation) busy.value = false
  }
}

const openMerge = async (group: SimilarityGroup) => {
  const ids = selections.value[group.id] || []
  if (ids.length < 2) return
  loadingGroup.value = group.id
  try {
    const books = await Promise.all(ids.map(async id => (await api.get<Book>(`/api/books/${id}`)).data))
    mergeBooks.value = books
    showMerge.value = true
  } catch (cause: any) {
    message.error(cause.response?.data?.message || '书籍信息已变化，请重新扫描')
  } finally {
    loadingGroup.value = null
  }
}

const handleMerged = () => {
  showMerge.value = false
  mergeBooks.value = []
  emit('merged')
  // 合并后重新扫描整个书库，避免继续显示已删除的候选记录。
  startScan()
}

const handleClose = () => {
  if (loadingGroup.value || showMerge.value) return
  emit('close')
}

watch(() => props.visible, visible => {
  stopPolling()
  generation++
  starting.value = false
  busy.value = false
  if (!visible) return
  if (!task.value) startScan()
  else loadPage(currentPage.value)
})

onUnmounted(() => {
  generation++
  stopPolling()
})
</script>

<style scoped>
.scan-description {
  margin: 0 0 10px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.scan-toolbar,
.group-header,
.scan-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.scan-toolbar {
  margin: 18px 0;
  flex-wrap: wrap;
}

.scan-status {
  color: var(--text-secondary);
}

.similarity-groups {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}

.similarity-group {
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-card);
  min-width: 0;
}

.group-header > div {
  min-width: 0;
  overflow-wrap: anywhere;
}

.group-header p {
  margin: 5px 0 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.author-warning {
  color: var(--warning);
  font-size: 12px;
}

.candidate-list {
  display: grid;
  max-height: 320px;
  margin-top: 12px;
  overflow-y: auto;
}

.candidate-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--border-color-light);
  cursor: pointer;
}

.candidate-selection {
  flex: 1;
  min-width: 0;
  height: auto;
  margin-right: 0;
}

.candidate-selection :deep(.el-checkbox__label) {
  flex: 1;
  min-width: 0;
  white-space: normal;
}

.candidate-copy {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 4px;
  overflow-wrap: anywhere;
}

.candidate-copy small {
  color: var(--text-secondary);
}

.candidate-row a {
  flex-shrink: 0;
  color: var(--primary);
}

.scan-pagination {
  justify-content: center;
  margin-top: 20px;
}

.retry-button {
  margin-top: 12px;
}

@media (max-width: 640px) {
  .group-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .similarity-group {
    padding: 12px;
  }
}
</style>
