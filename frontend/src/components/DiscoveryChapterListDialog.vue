<template>
  <el-dialog
    :model-value="modelValue"
    title="采集章节信息"
    width="min(1000px, 96vw)"
    append-to-body
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert title="只采集章节名称、顺序和链接，不采集正文。每本书生成一个任务，加入所属网站队列。" type="info" :closable="false" />
    <div class="chapter-book-picker">
      <section aria-label="待选书籍">
        <header><strong>待选书籍</strong><span>共 {{ total }} 本</span></header>
        <div class="picker-search">
          <el-input v-model="keyword" clearable placeholder="搜索书名、作者或标签" aria-label="搜索待选书籍" @keyup.enter="search" @clear="search" />
          <el-button :disabled="loading || submitting" @click="search">搜索</el-button>
        </div>
        <el-select v-model="siteId" clearable placeholder="全部采集网站" aria-label="待选书籍网站" :disabled="submitting" @change="search">
          <el-option v-for="site in sites" :key="site.id" :label="site.siteName" :value="site.id" />
        </el-select>
        <el-button :disabled="loading || submitting || !available.length" @click="addCurrentPage">添加当前页可选书籍 →</el-button>
        <div v-loading="loading" class="picker-list">
          <el-alert v-if="loadFailed" title="加载失败，请重新搜索" type="error" :closable="false" />
          <article v-for="book in available" :key="book.id">
            <div><strong>{{ book.bookName }}</strong><small>{{ book.author || '未知作者' }} · {{ book.siteName }}</small></div>
            <el-button :disabled="loading || submitting || isActive(book)" :aria-label="`添加${book.bookName}`" @click="add(book)">
              {{ isActive(book) ? '已有任务' : '添加 →' }}
            </el-button>
          </article>
          <el-empty v-if="!loading && !loadFailed && !available.length" :image-size="50" description="当前页无待选书籍" />
        </div>
        <el-pagination v-model:current-page="page" :page-size="20" :total="total" :pager-count="5" layout="prev, pager, next" :disabled="loading || submitting" @current-change="loadBooks" />
      </section>
      <section aria-label="已选书籍">
        <header><strong>已选书籍 · {{ selected.length }} 本</strong><el-button :disabled="submitting || !selected.length" @click="clearSelection">清空</el-button></header>
        <div class="picker-list selected-list">
          <article v-for="book in selected" :key="book.id">
            <div><strong>{{ book.bookName }}</strong><small>{{ book.author || '未知作者' }} · {{ book.siteName }}</small></div>
            <el-button :disabled="submitting" :aria-label="`移除${book.bookName}`" @click="remove(book.id)">← 移除</el-button>
          </article>
          <el-empty v-if="!selected.length" :image-size="50" description="从左侧添加需要采集章节信息的书籍" />
        </div>
      </section>
    </div>
    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selected.length" @click="submit">采集章节信息（{{ selected.length }} 本）</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { crawlerApi, type CrawlerBook, type CrawlerSite, type CrawlerTask } from '@/utils/crawler'
import { message } from '@/utils/message'

const props = defineProps<{
  modelValue: boolean
  sites: CrawlerSite[]
  initialBooks: CrawlerBook[]
  initialSiteId?: number
  isActive: (book: CrawlerBook) => boolean
}>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'created', tasks: CrawlerTask[]): void
}>()
const keyword = ref('')
const siteId = ref<number>()
const page = ref(1)
const total = ref(0)
const books = ref<CrawlerBook[]>([])
const selected = ref<CrawlerBook[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const submitting = ref(false)
let requestId = 0
const selectedIds = computed(() => new Set(selected.value.map(book => book.id)))
const available = computed(() => books.value.filter(book => !selectedIds.value.has(book.id)))

watch(() => props.modelValue, open => {
  ++requestId
  if (!open) return
  keyword.value = ''
  siteId.value = props.initialSiteId
  page.value = 1
  books.value = []
  total.value = 0
  selected.value = props.initialBooks.filter(book => !props.isActive(book))
  void loadBooks()
})

async function loadBooks() {
  const request = ++requestId
  loading.value = true
  loadFailed.value = false
  try {
    const result = await crawlerApi.discoveryCandidates({
      page: page.value - 1,
      size: 20,
      keyword: keyword.value.trim() || undefined,
      siteId: siteId.value,
      excludedIds: [...selectedIds.value],
    })
    if (request !== requestId) return
    const lastPage = Math.max(1, Math.ceil(result.totalElements / 20))
    if (page.value > lastPage) {
      page.value = lastPage
      return await loadBooks()
    }
    books.value = result.content
    total.value = result.totalElements
  } catch {
    if (request === requestId) {
      books.value = []
      total.value = 0
      loadFailed.value = true
    }
  } finally {
    if (request === requestId) loading.value = false
  }
}

function search() {
  page.value = 1
  void loadBooks()
}

async function add(book: CrawlerBook) {
  if (loading.value || submitting.value || props.isActive(book) || selectedIds.value.has(book.id)) return
  selected.value = [...selected.value, book]
  await loadBooks()
}

async function addCurrentPage() {
  if (loading.value || submitting.value) return
  const additions = available.value.filter(book => !props.isActive(book))
  if (!additions.length) return
  selected.value = [...selected.value, ...additions]
  await loadBooks()
}

async function remove(id: number) {
  if (submitting.value) return
  selected.value = selected.value.filter(book => book.id !== id)
  await loadBooks()
}

async function clearSelection() {
  if (submitting.value) return
  selected.value = []
  await loadBooks()
}

async function submit() {
  if (submitting.value || !selected.value.length) return
  if (selected.value.some(book => props.isActive(book))) {
    message.warning('部分已选书籍已有活动任务，请移除后重试')
    return
  }
  submitting.value = true
  try {
    const tasks = await crawlerApi.batchChapterLists(selected.value.map(book => book.id))
    message.success(`已创建 ${tasks.length} 个章节信息采集任务，已加入各书所属网站队列`)
    emit('created', tasks)
    emit('update:modelValue', false)
  } catch (error: any) {
    message.error(error.response?.data?.message || '创建章节信息采集任务失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.chapter-book-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
  margin-top: 20px;
}
.chapter-book-picker section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
}
.chapter-book-picker header,
.picker-search,
.picker-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.picker-list {
  min-height: 280px;
  max-height: 380px;
  overflow: auto;
}
.selected-list {
  max-height: 540px;
}
.picker-list article {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.picker-list article > div {
  min-width: 0;
  overflow-wrap: anywhere;
}
.picker-list small {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
}
.picker-list .el-button {
  flex-shrink: 0;
  min-height: 44px;
}
.chapter-book-picker :deep(.el-pagination) {
  justify-content: center;
}
@media (max-width: 640px) {
  .chapter-book-picker {
    grid-template-columns: minmax(0, 1fr);
  }
  .picker-list {
    min-height: 140px;
    max-height: 260px;
  }
}
</style>
