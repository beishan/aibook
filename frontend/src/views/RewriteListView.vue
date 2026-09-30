<template>
  <div class="rewrite-page">
    <header class="rewrite-header">
      <div>
        <p class="eyebrow">书库 · 创作</p>
        <h1>书籍重写</h1>
        <p>保留原版，创建可随时阅读的独立重写版本。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建重写</el-button>
    </header>

    <div class="status-tabs" role="tablist" aria-label="项目状态" @keydown="handleTabKeydown">
      <span class="status-slider" :style="{ transform: `translateX(${activeTabIndex * 100}%)` }" />
      <button
        v-for="(tab, index) in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        :aria-selected="filter === tab.key"
        :tabindex="filter === tab.key ? 0 : -1"
        :class="{ active: filter === tab.key }"
        @click="filter = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="project-search">
      <el-input v-model="projectKeyword" clearable placeholder="搜索项目名、书名或作者" @keyup.enter="searchProjects" />
      <el-button @click="searchProjects">搜索</el-button>
    </div>

    <div v-loading="loading" class="project-grid">
      <el-empty v-if="!loading && !visibleProjects.length" description="暂无符合条件的重写项目" />
      <article v-for="project in visibleProjects" :key="project.id" class="project-card">
        <div class="project-card-top">
          <span class="project-state">{{ statusLabel(project.status) }}</span>
          <span>{{ project.completedCount }}/{{ project.chapterCount }} 章</span>
        </div>
        <h2>{{ project.name }}</h2>
        <p class="project-book">《{{ project.bookTitle }}》 · {{ project.versionName }}</p>
        <p class="project-description">{{ project.description || '继续编辑并对照原文' }}</p>
        <div class="project-progress" role="progressbar" :aria-valuenow="project.progress" aria-valuemin="0" aria-valuemax="100">
          <span :style="{ width: `${project.progress}%` }" />
        </div>
        <footer>
          <small>{{ project.progress }}% · {{ formatTime(project.updatedAt) }}</small>
          <el-button link type="primary" @click="router.push(`/rewrite/${project.id}`)">
            {{ project.status === 'ACTIVE' ? '继续重写' : '查看项目' }} →
          </el-button>
        </footer>
      </article>
    </div>
    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next"
      @current-change="loadProjects"
    />

    <el-dialog v-model="createOpen" title="新建书籍重写" width="min(680px, 94vw)" destroy-on-close>
      <div class="create-form">
        <label for="rewrite-book-search">选择书籍</label>
        <div class="search-row">
          <el-input
            id="rewrite-book-search"
            v-model="bookKeyword"
            placeholder="搜索书名或作者"
            @keyup.enter="loadBooks"
          />
          <el-button @click="loadBooks">搜索</el-button>
        </div>
        <el-select
          v-model="selectedBookId"
          filterable
          placeholder="请选择书籍"
          class="wide-input"
          @change="loadVersions"
        >
          <el-option
            v-for="book in bookOptions"
            :key="book.id"
            :label="`${book.title} · ${book.author || '未知作者'}`"
            :value="book.id"
          />
        </el-select>

        <label for="rewrite-source-version">选择源版本</label>
        <el-select
          id="rewrite-source-version"
          v-model="selectedVersionId"
          placeholder="请选择版本"
          class="wide-input"
          @change="preview = null"
        >
          <el-option
            v-for="version in versionOptions"
            :key="version.id"
            :label="`${version.displayName} · ${formatLabel(version.format)}`"
            :value="version.id"
            :disabled="!supported(version.format)"
          />
        </el-select>
        <p class="form-hint">第一阶段支持在线章节、TXT 和 EPUB；创建时不会修改源版本。</p>
        <el-button :disabled="!selectedVersionId" :loading="previewing" @click="loadPreview">
          解析并预览章节
        </el-button>

        <section v-if="preview" class="preview-box">
          <strong>已识别 {{ preview.chapterCount }} 章</strong>
          <label v-if="preview.singleChapterAvailable" class="single-option">
            <input v-model="singleChapter" type="checkbox" /> 作为单章导入
          </label>
          <div v-for="(chapter, index) in preview.chapters" :key="index" class="preview-chapter">
            <el-input
              v-model="chapter.title"
              maxlength="500"
              :disabled="singleChapter"
              aria-label="章节标题"
            />
            <small>{{ chapter.length }} 字符</small>
            <p>{{ chapter.excerpt || '空章节' }}</p>
            <label v-if="index > 0 && !singleChapter" class="merge-option">
              <input v-model="chapter.mergeWithPrevious" type="checkbox" />
              与上一章合并（移除此处章节边界）
            </label>
          </div>
        </section>

        <label for="rewrite-name">项目名称</label>
        <el-input id="rewrite-name" v-model="projectName" maxlength="200" placeholder="例如：精修第一稿" />
        <label for="rewrite-version-name">新版本名称</label>
        <el-input id="rewrite-version-name" v-model="versionName" maxlength="255" placeholder="重写版" />
        <label for="rewrite-description">项目说明（可选）</label>
        <el-input id="rewrite-description" v-model="description" type="textarea" :rows="2" maxlength="10000" />
      </div>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!preview || !projectName.trim() || !versionName.trim()" :loading="creating" @click="createProject">
          创建并开始重写
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/utils/api'
import { message } from '@/utils/message'

interface RewriteProject {
  id: number
  name: string
  bookTitle: string
  versionName: string
  description: string
  status: string
  chapterCount: number
  completedCount: number
  progress: number
  updatedAt: string
}

interface BookOption {
  id: number
  title: string
  author?: string
}

interface VersionOption {
  id: number
  displayName: string
  format: string
}

interface Preview {
  previewToken: string
  chapterCount: number
  singleChapterAvailable: boolean
  chapters: Array<{ title: string; length: number; excerpt: string; mergeWithPrevious?: boolean }>
}

const route = useRoute()
const router = useRouter()
const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'ACTIVE', label: '进行中' },
  { key: 'PAUSED', label: '已暂停' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'ARCHIVED', label: '已归档' },
]
const filter = ref('ALL')
const activeTabIndex = computed(() => tabs.findIndex(tab => tab.key === filter.value))
const projects = ref<RewriteProject[]>([])
const projectKeyword = ref('')
const loading = ref(false)
const total = ref(0)
const pageSize = 20
const currentPage = ref(1)
const visibleProjects = computed(() => projects.value)

const createOpen = ref(false)
const bookKeyword = ref('')
const bookOptions = ref<BookOption[]>([])
const versionOptions = ref<VersionOption[]>([])
const selectedBookId = ref<number | null>(null)
const selectedVersionId = ref<number | null>(null)
const preview = ref<Preview | null>(null)
const previewing = ref(false)
const creating = ref(false)
const singleChapter = ref(false)
const projectName = ref('')
const versionName = ref('重写版')
const description = ref('')

const statusLabel = (status: string) => ({
  ACTIVE: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
}[status] || status)
const formatLabel = (format: string) => format === 'structured' ? '在线章节' : format.toUpperCase()
const supported = (format: string) => ['structured', 'txt', 'epub'].includes(format.toLowerCase())
const formatTime = (value: string) => value ? new Date(value).toLocaleString('zh-CN') : ''

const handleTabKeydown = (event: KeyboardEvent) => {
  const index = activeTabIndex.value
  const next = event.key === 'ArrowRight' ? Math.min(index + 1, tabs.length - 1)
    : event.key === 'ArrowLeft' ? Math.max(index - 1, 0) : -1
  if (next < 0) return
  event.preventDefault()
  filter.value = tabs[next].key
  ;(event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('button')[next]?.focus()
}

const loadProjects = async () => {
  loading.value = true
  try {
    const { data } = await api.get('/api/rewrite/projects', {
      params: {
        page: currentPage.value - 1,
        size: pageSize,
        status: filter.value === 'ALL' ? undefined : filter.value,
        keyword: projectKeyword.value.trim() || undefined,
      },
    })
    projects.value = data.content || []
    total.value = data.totalElements || 0
  } finally {
    loading.value = false
  }
}

const searchProjects = () => {
  currentPage.value = 1
  void loadProjects()
}

const loadBooks = async () => {
  const path = bookKeyword.value.trim() ? '/api/books/search' : '/api/books'
  const { data } = await api.get(path, {
    params: { keyword: bookKeyword.value.trim(), page: 0, size: 20 },
  })
  bookOptions.value = data.content || []
}

const loadVersions = async () => {
  selectedVersionId.value = null
  preview.value = null
  versionOptions.value = []
  if (!selectedBookId.value) return
  const { data } = await api.get(`/api/books/${selectedBookId.value}/versions`)
  versionOptions.value = data || []
  selectedVersionId.value = versionOptions.value.find(version => supported(version.format))?.id || null
}

const openCreate = async () => {
  createOpen.value = true
  await loadBooks()
}

const loadPreview = async () => {
  if (!selectedBookId.value || !selectedVersionId.value) return
  previewing.value = true
  try {
    const { data } = await api.post('/api/rewrite/projects/preview', {
      bookId: selectedBookId.value,
      sourceVersionId: selectedVersionId.value,
    })
    preview.value = data
    singleChapter.value = false
    if (!projectName.value) projectName.value = `重写《${bookOptions.value.find(book => book.id === selectedBookId.value)?.title || '书籍'}》`
  } finally {
    previewing.value = false
  }
}

const createProject = async () => {
  if (!preview.value) return
  creating.value = true
  try {
    const { data } = await api.post('/api/rewrite/projects', {
      previewToken: preview.value.previewToken,
      name: projectName.value.trim(),
      versionName: versionName.value.trim(),
      description: description.value.trim(),
      singleChapter: singleChapter.value,
      chapterOverrides: preview.value.chapters.map(chapter => ({
        title: chapter.title,
        mergeWithPrevious: Boolean(chapter.mergeWithPrevious),
      })),
    })
    createOpen.value = false
    message.success('已创建重写版本')
    await router.push(`/rewrite/${data.id}`)
  } finally {
    creating.value = false
  }
}

onMounted(async () => {
  await loadProjects()
  const bookId = Number(route.query.bookId)
  if (!Number.isFinite(bookId) || bookId <= 0) return
  const { data } = await api.get(`/api/books/${bookId}`)
  bookOptions.value = [{ id: data.id, title: data.title, author: data.author }]
  selectedBookId.value = bookId
  createOpen.value = true
  await loadVersions()
  const versionId = Number(route.query.versionId)
  if (versionOptions.value.some(version => version.id === versionId && supported(version.format))) {
    selectedVersionId.value = versionId
  }
})

watch(filter, () => {
  currentPage.value = 1
  void loadProjects()
})
</script>

<style scoped>
.rewrite-page { max-width: 1200px; margin: 0 auto; padding: 32px 24px 110px; color: var(--text-primary, #24342d); }
.rewrite-header { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 28px; }
.rewrite-header h1 { margin: 4px 0 8px; font-size: clamp(28px, 4vw, 40px); }
.rewrite-header p { margin: 0; color: var(--text-secondary, #728078); }
.eyebrow { font-size: 12px; letter-spacing: .12em; text-transform: uppercase; }
.status-tabs { position: relative; display: flex; width: min(100%, 560px); overflow-x: auto; padding: 4px; border-radius: 15px; background: rgba(128, 145, 135, .15); }
.status-slider { position: absolute; inset: 4px auto 4px 4px; width: calc((100% - 8px) / 5); border-radius: 11px; background: var(--el-bg-color, #fff); box-shadow: 0 2px 10px #0001; transition: transform .25s ease; }
.status-tabs button { position: relative; flex: 1 0 90px; border: 0; background: none; padding: 9px 8px; color: inherit; cursor: pointer; }
.status-tabs button.active { font-weight: 700; }
.status-tabs button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; border-radius: 10px; }
.project-search { display: flex; gap: 8px; width: min(100%, 560px); margin-top: 16px; }
.project-grid { min-height: 260px; display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 18px; margin-top: 24px; }
.project-card { padding: 22px; border: 1px solid var(--el-border-color-light, #d8dfd9); border-radius: 20px; background: var(--el-bg-color, #fff); box-shadow: 0 10px 32px #183b2510; }
.project-card-top, .project-card footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.project-card-top { font-size: 12px; color: var(--text-secondary, #728078); }
.project-state { color: var(--el-color-primary); font-weight: 700; }
.project-card h2 { margin: 20px 0 7px; font-size: 20px; }
.project-book, .project-description { color: var(--text-secondary, #728078); }
.project-description { min-height: 42px; }
.project-progress { height: 7px; margin: 20px 0; border-radius: 8px; background: #e6ebe7; overflow: hidden; }
.project-progress span { display: block; height: 100%; border-radius: inherit; background: var(--el-color-primary); }
.project-card footer small { color: var(--text-secondary, #728078); }
.create-form { display: grid; gap: 10px; max-height: 65vh; overflow: auto; padding-right: 4px; }
.create-form label { margin-top: 8px; font-weight: 600; }
.search-row { display: flex; gap: 8px; }
.wide-input { width: 100%; }
.form-hint { margin: 0; font-size: 12px; color: var(--text-secondary, #728078); }
.preview-box { max-height: 250px; overflow: auto; padding: 14px; border-radius: 12px; background: rgba(128, 145, 135, .1); }
.single-option { display: block; font-size: 13px; }
.preview-chapter { padding: 10px 0; border-bottom: 1px solid #8883; }
.preview-chapter strong { margin-right: 10px; }
.preview-chapter small { color: #888; }
.preview-chapter p { margin: 5px 0 0; font-size: 12px; color: var(--text-secondary, #728078); white-space: pre-wrap; }
.merge-option { display: flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 400 !important; }
@media (prefers-reduced-motion: reduce) { .status-slider { transition: none; } }
</style>
