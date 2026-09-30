<template>
  <div v-loading="loading" class="rewrite-workspace">
    <header v-if="project" class="workspace-header">
      <div>
        <router-link to="/rewrite" class="back-link">← 重写项目</router-link>
        <h1>{{ project.name }}</h1>
        <p>《{{ project.bookTitle }}》 · {{ project.versionName }} · {{ statusLabel(project.status) }} · 全书 {{ project.totalWordCount }} 字</p>
      </div>
      <div class="header-actions">
        <span class="save-indicator" :class="saveState">{{ saveLabel }}</span>
        <el-button :disabled="!chapter || !editable" @click="saveNow">保存</el-button>
        <el-button :disabled="!chapter" @click="toggleSource">{{ showSource ? '收起原文' : '原文对照' }}</el-button>
        <el-button :disabled="!chapter" @click="openHistory">历史记录</el-button>
        <el-button :disabled="!project" @click="openReader">阅读预览</el-button>
        <el-dropdown @command="handleProjectAction">
          <el-button>项目操作 ⌄</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="rename">修改项目名称</el-dropdown-item>
              <el-dropdown-item command="description">编辑项目说明</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'ACTIVE'" command="pause">暂停编辑</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'PAUSED'" command="resume">继续编辑</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'ARCHIVED'" command="restore">恢复项目</el-dropdown-item>
              <el-dropdown-item v-if="['ACTIVE', 'PAUSED'].includes(project.status)" command="archive">归档项目</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'ACTIVE'" command="complete">完成重写</el-dropdown-item>
              <el-dropdown-item divided command="delete">删除项目及重写版本</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div v-if="project" class="workspace-body">
      <aside class="chapter-sidebar">
        <div class="sidebar-heading">
          <strong>章节目录</strong>
          <div>
            <el-button v-if="editable" link type="primary" @click="addChapter">＋ 章节</el-button>
            <el-button v-if="editable" link @click="addVolume">＋ 卷</el-button>
          </div>
        </div>
        <p class="sidebar-progress">已完成 {{ project.completedCount }}/{{ project.chapterCount }} 章 · {{ project.progress }}%</p>
        <nav aria-label="重写章节">
          <template v-for="(item, index) in chapters" :key="item.id">
            <strong
              v-if="item.volumeTitle && (index === 0 || item.volumeTitle !== chapters[index - 1].volumeTitle)"
              class="volume-heading"
            >{{ item.volumeTitle }}</strong>
            <button
              type="button"
              class="chapter-link"
              :class="{ selected: item.id === chapter?.id }"
              @click="selectChapter(item.id)"
            >
              <span class="chapter-title">{{ item.title }}</span>
              <small>{{ chapterStatusLabel(item.status) }} · {{ item.wordCount }} 字</small>
            </button>
          </template>
        </nav>
        <div v-if="editable" class="deleted-chapters">
          <el-button link @click="loadDeletedChapters">已删除章节（{{ deletedChapters.length }}）</el-button>
          <button
            v-for="item in deletedChapters"
            :key="item.id"
            type="button"
            class="restore-chapter-button"
            @click="restoreDeletedChapter(item.id)"
          >
            恢复 {{ item.title }}
          </button>
        </div>
      </aside>

      <main v-if="chapter" class="editor-panel">
        <div class="chapter-toolbar">
          <div class="chapter-title-row">
            <input
              v-model="chapterTitle"
              aria-label="章节标题"
              maxlength="500"
              :readonly="!editable"
              @blur="saveTitle"
              @keydown.enter.prevent="saveTitle"
            />
            <span>{{ chapter.wordCount }} 字</span>
          </div>
          <div v-if="editable" class="chapter-tools">
            <el-button size="small" @click="setVolume">所属卷</el-button>
            <el-button size="small" :disabled="chapterIndex === 0" @click="moveChapter(-1)">上移</el-button>
            <el-button size="small" :disabled="chapterIndex === chapters.length - 1" @click="moveChapter(1)">下移</el-button>
            <el-button size="small" @click="splitChapter">从光标拆分</el-button>
            <el-button size="small" :disabled="chapterIndex >= chapters.length - 1" @click="mergeNext">合并下一章</el-button>
            <el-button size="small" type="danger" plain :disabled="chapters.length <= 1" @click="deleteChapter">删除章节</el-button>
          </div>
        </div>

        <div v-if="editable" class="chapter-status-control" role="group" aria-label="章节状态">
          <span class="chapter-status-slider" :style="{ transform: `translateX(${chapterStatusIndex * 100}%)` }" />
          <button
            v-for="(state, index) in chapterStatuses"
            :key="state.key"
            type="button"
            :aria-pressed="chapter.status === state.key"
            :tabindex="chapter.status === state.key ? 0 : -1"
            @click="setChapterStatus(state.key)"
            @keydown="handleChapterStatusKeydown($event, index)"
          >
            {{ state.label }}
          </button>
        </div>

        <div v-if="recoveredDraft" class="draft-notice">
          已恢复此设备上次未保存的文字，请保存或重新加载服务器内容。
          <el-button link @click="reloadChapter">重新加载</el-button>
        </div>
        <div v-if="saveState === 'conflict'" class="conflict-notice">
          其他窗口修改了本章。当前文字保留在编辑器中；请先复制，再重新加载服务器内容。
          <el-button size="small" @click="copyDraft">复制本地内容</el-button>
          <el-button size="small" @click="reloadChapter">重新加载</el-button>
        </div>

        <div class="writing-surface" :class="{ comparing: showSource }">
          <section v-if="showSource" class="source-pane">
            <div class="pane-label">
              <strong>源章节 · {{ chapter.sourceTitle || '无对应原文' }}</strong>
              <el-button v-if="editable && chapter.hasSource" link @click="restoreSource">恢复为原文</el-button>
            </div>
            <pre>{{ chapter.sourceContent || '这是新增章节，没有对应原文。' }}</pre>
          </section>
          <section class="draft-pane">
            <div class="pane-label"><strong>重写正文</strong><span>自动保存约 1 秒</span></div>
            <textarea
              ref="editorRef"
              v-model="draftContent"
              :readonly="!editable"
              aria-label="章节正文"
              placeholder="在这里开始重写章节……"
              spellcheck="false"
              @input="onContentInput"
              @keydown="handleEditorKeydown"
            />
          </section>
        </div>

        <footer class="editor-footer">
          <el-button :disabled="chapterIndex <= 0" @click="selectChapter(chapters[chapterIndex - 1].id)">← 上一章</el-button>
          <span>{{ chapterIndex + 1 }} / {{ chapters.length }}</span>
          <el-button :disabled="chapterIndex >= chapters.length - 1" @click="selectChapter(chapters[chapterIndex + 1].id)">下一章 →</el-button>
        </footer>
      </main>
      <div v-else class="editor-panel empty-panel">请选择章节</div>
    </div>
    <el-dialog v-model="historyOpen" title="章节历史" width="min(760px, 94vw)">
      <div class="history-dialog">
        <div class="history-list">
          <button
            v-for="item in history"
            :key="item.revision"
            type="button"
            :class="{ selected: selectedHistoryRevision === item.revision }"
            @click="showRevision(item.revision)"
          >
            修订 {{ item.revision }} · {{ item.reason }}
            <small>{{ item.createdAt }}</small>
          </button>
        </div>
        <pre>{{ historyContent || '选择一条历史记录查看正文' }}</pre>
      </div>
      <template #footer>
        <el-button @click="historyOpen = false">关闭</el-button>
        <el-button
          v-if="editable && selectedHistoryRevision !== null"
          type="primary"
          @click="restoreHistory"
        >恢复此修订</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import api from '@/utils/api'
import { confirm, message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

interface RewriteProject {
  id: number
  bookId: number
  bookTitle: string
  rewriteVersionId: number
  name: string
  versionName: string
  description?: string
  status: string
  chapterCount: number
  completedCount: number
  progress: number
  totalWordCount: number
  currentChapterId: number
}

interface ChapterSummary {
  id: number
  title: string
  sortIndex: number
  status: string
  wordCount: number
  revision: number
  hasSource: boolean
  volumeTitle: string
}

interface ChapterDetail extends ChapterSummary {
  content: string
  sourceTitle: string | null
  sourceContent: string | null
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.projectId)
const project = ref<RewriteProject | null>(null)
const chapters = ref<ChapterSummary[]>([])
const deletedChapters = ref<ChapterSummary[]>([])
const chapter = ref<ChapterDetail | null>(null)
const editorRef = ref<HTMLTextAreaElement | null>(null)
const draftContent = ref('')
const chapterTitle = ref('')
const loading = ref(false)
const showSource = ref(false)
const recoveredDraft = ref(false)
const historyOpen = ref(false)
const history = ref<Array<{ revision: number; reason: string; createdAt: string }>>([])
const selectedHistoryRevision = ref<number | null>(null)
const historyContent = ref('')
const saveState = ref<'saved' | 'dirty' | 'saving' | 'error' | 'conflict'>('saved')
const editable = computed(() => project.value?.status === 'ACTIVE')
const chapterIndex = computed(() => chapters.value.findIndex(item => item.id === chapter.value?.id))
const chapterStatuses = [
  { key: 'NOT_STARTED', label: '未开始' },
  { key: 'WRITING', label: '重写中' },
  { key: 'REVIEW', label: '待复查' },
  { key: 'COMPLETED', label: '已完成' },
]
const chapterStatusIndex = computed(() => Math.max(0,
  chapterStatuses.findIndex(item => item.key === chapter.value?.status)))
const saveLabel = computed(() => ({
  saved: '已保存', dirty: '有未保存修改', saving: '正在保存…',
  error: '保存失败', conflict: '版本冲突',
}[saveState.value]))
let saveTimer: ReturnType<typeof setTimeout> | undefined
let savingPromise: Promise<boolean> | null = null

const statusLabel = (status: string) => ({
  ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', ARCHIVED: '已归档',
}[status] || status)
const chapterStatusLabel = (status: string) =>
  chapterStatuses.find(item => item.key === status)?.label || status
const draftKey = (chapterId: number) =>
  `aibook.rewriteDraft.${userStore.userInfo?.id || 'current'}.${projectId}.${chapterId}`

const storeDraft = (chapterId: number, content: string) => {
  try {
    sessionStorage.setItem(draftKey(chapterId), content)
  } catch {
    message.warning('本章内容超过浏览器临时草稿容量，请尽快连接服务器保存')
  }
}

const loadProject = async () => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}`)
  project.value = data
}

const loadChapters = async () => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}/chapters`)
  chapters.value = data || []
  await loadProject()
  await loadDeletedChapters()
}

const loadDeletedChapters = async () => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}/deleted-chapters`)
  deletedChapters.value = data || []
}

const loadChapter = async (chapterId: number) => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}/chapters/${chapterId}`)
  chapter.value = data
  chapterTitle.value = data.title
  draftContent.value = data.content
  saveState.value = 'saved'
  recoveredDraft.value = false
  const locallySaved = sessionStorage.getItem(draftKey(chapterId))
  if (locallySaved !== null && locallySaved !== data.content) {
    draftContent.value = locallySaved
    saveState.value = 'dirty'
    recoveredDraft.value = true
  }
}

const reloadChapter = async () => {
  if (!chapter.value) return
  if (saveState.value !== 'saved') {
    const approved = await confirm(
      '重新加载将丢弃此设备尚未保存的文字。请先复制需要保留的内容。',
      '重新加载章节',
    )
    if (!approved) return
  }
  sessionStorage.removeItem(draftKey(chapter.value.id))
  await loadChapter(chapter.value.id)
}

const updateSummary = () => {
  if (!chapter.value) return
  const index = chapters.value.findIndex(item => item.id === chapter.value?.id)
  if (index >= 0) chapters.value[index] = { ...chapter.value }
}

const saveNow = async (): Promise<boolean> => {
  if (!chapter.value || !editable.value) return true
  if (saveTimer) clearTimeout(saveTimer)
  if (savingPromise) return savingPromise.then(() => saveNow())
  if (draftContent.value === chapter.value.content) {
    saveState.value = 'saved'
    return true
  }
  const activeChapter = chapter.value
  const content = draftContent.value
  saveState.value = 'saving'
  savingPromise = (async () => {
    try {
      const { data } = await api.put(
        `/api/rewrite/projects/${projectId}/chapters/${activeChapter.id}/content`,
        { content, revision: activeChapter.revision },
        { headers: { 'X-Suppress-Error-Toast': 'true' } },
      )
      if (chapter.value?.id !== activeChapter.id) return true
      chapter.value = data
      updateSummary()
      await loadProject()
      if (draftContent.value === content) {
        saveState.value = 'saved'
        recoveredDraft.value = false
        sessionStorage.removeItem(draftKey(activeChapter.id))
      } else {
        saveState.value = 'dirty'
      }
      return true
    } catch (error: any) {
      saveState.value = error.response?.status === 409 ? 'conflict' : 'error'
      storeDraft(activeChapter.id, draftContent.value)
      message.error(saveState.value === 'conflict' ? '章节已被其他窗口修改' : '保存失败，内容已留在本地草稿')
      return false
    } finally {
      savingPromise = null
    }
  })()
  return savingPromise
}

const onContentInput = () => {
  if (!chapter.value) return
  saveState.value = 'dirty'
  storeDraft(chapter.value.id, draftContent.value)
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => { void saveNow() }, 1000)
}

const selectChapter = async (chapterId: number) => {
  if (chapter.value?.id === chapterId) return
  if (!(await saveNow())) return
  await loadChapter(chapterId)
  void api.patch(`/api/rewrite/projects/${projectId}`, {
    currentChapterId: chapterId,
  }).then(({ data }) => { project.value = data }).catch(() => undefined)
}

const saveTitle = async () => {
  if (!chapter.value || !editable.value || chapterTitle.value === chapter.value.title) return
  if (!(await saveNow())) return
  const { data } = await api.patch(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}`,
    { title: chapterTitle.value, revision: chapter.value.revision },
  )
  chapter.value = data
  updateSummary()
}

const setChapterStatus = async (status: string) => {
  if (!chapter.value || !editable.value || chapter.value.status === status) return
  if (!(await saveNow())) return
  const { data } = await api.patch(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}`,
    { status, revision: chapter.value.revision },
  )
  chapter.value = data
  updateSummary()
  await loadProject()
}

const handleChapterStatusKeydown = (event: KeyboardEvent, index: number) => {
  const next = event.key === 'ArrowRight' ? (index + 1) % chapterStatuses.length
    : event.key === 'ArrowLeft' ? (index - 1 + chapterStatuses.length) % chapterStatuses.length : -1
  if (next < 0) return
  event.preventDefault()
  void setChapterStatus(chapterStatuses[next].key)
  ;(event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('button')[next]?.focus()
}

const addChapter = async () => {
  if (!(await saveNow())) return
  const title = window.prompt('新章节标题', '新章节')
  if (!title?.trim()) return
  const { data } = await api.post(`/api/rewrite/projects/${projectId}/chapters`, {
    title: title.trim(),
    volumeTitle: chapter.value?.volumeTitle || '',
  })
  await loadChapters()
  await loadChapter(data.id)
}

const addVolume = async () => {
  if (!(await saveNow())) return
  const volumeTitle = window.prompt('新卷标题', '新卷')
  if (!volumeTitle?.trim()) return
  const { data } = await api.post(`/api/rewrite/projects/${projectId}/chapters`, {
    title: '新章节',
    volumeTitle: volumeTitle.trim(),
  })
  await loadChapters()
  await loadChapter(data.id)
}

const setVolume = async () => {
  if (!chapter.value || !(await saveNow())) return
  const volumeTitle = window.prompt('所属卷标题；留空则移出卷', chapter.value.volumeTitle || '')
  if (volumeTitle === null) return
  const { data } = await api.patch(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}`,
    { volumeTitle, revision: chapter.value.revision },
  )
  chapter.value = data
  updateSummary()
}

const deleteChapter = async () => {
  if (!chapter.value || !editable.value) return
  if (!(await confirm(`删除“${chapter.value.title}”？此操作会从阅读版本移除该章节。`, '删除章节'))) return
  if (!(await saveNow())) return
  const id = chapter.value.id
  await api.delete(`/api/rewrite/projects/${projectId}/chapters/${id}`)
  sessionStorage.removeItem(draftKey(id))
  await loadChapters()
  if (chapters.value.length) await loadChapter(chapters.value[0].id)
}

const restoreDeletedChapter = async (chapterId: number) => {
  if (!(await saveNow())) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/deleted-chapters/${chapterId}/restore`,
  )
  await loadChapters()
  await loadChapter(data.id)
}

const moveChapter = async (direction: number) => {
  if (!chapter.value || !(await saveNow())) return
  const order = chapters.value.map(item => item.id)
  const current = order.indexOf(chapter.value.id)
  const next = current + direction
  if (next < 0 || next >= order.length) return
  ;[order[current], order[next]] = [order[next], order[current]]
  await api.put(`/api/rewrite/projects/${projectId}/chapters/order`, order)
  await loadChapters()
}

const splitChapter = async () => {
  if (!chapter.value || !editorRef.value || !(await saveNow())) return
  const position = editorRef.value.selectionStart
  const newTitle = window.prompt('新章节标题', '新章节')
  if (!newTitle?.trim()) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/split`,
    { position, newTitle: newTitle.trim(), revision: chapter.value.revision },
  )
  await loadChapters()
  await loadChapter(data.id)
}

const mergeNext = async () => {
  if (!chapter.value || !(await saveNow())) return
  const next = chapters.value[chapterIndex.value + 1]
  if (!next) return
  const title = window.prompt(`合并“${chapter.value.title}”和“${next.title}”，请输入合并后标题`, chapter.value.title)
  if (!title?.trim()) return
  if (!(await confirm(`将下一章“${next.title}”合并到当前章节。`, '合并章节'))) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/merge-next`,
    { title: title.trim(), revision: chapter.value.revision },
  )
  await loadChapters()
  await loadChapter(data.id)
}

const restoreSource = async () => {
  if (!chapter.value?.hasSource || !editable.value) return
  if (!(await confirm('将当前章节正文恢复为创建项目时的源章节内容？当前内容会保留恢复记录。', '恢复原文'))) return
  if (!(await saveNow())) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/restore-source`,
    { revision: chapter.value.revision },
  )
  chapter.value = data
  draftContent.value = data.content
  sessionStorage.removeItem(draftKey(data.id))
  saveState.value = 'saved'
  updateSummary()
  await loadProject()
}

const openHistory = async () => {
  if (!chapter.value) return
  if (!(await saveNow())) return
  const { data } = await api.get(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/revisions`,
  )
  history.value = data || []
  selectedHistoryRevision.value = null
  historyContent.value = ''
  historyOpen.value = true
}

const showRevision = async (revision: number) => {
  if (!chapter.value) return
  const { data } = await api.get(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/revisions/${revision}`,
  )
  selectedHistoryRevision.value = revision
  historyContent.value = data.content
}

const restoreHistory = async () => {
  if (!chapter.value || selectedHistoryRevision.value === null) return
  if (!(await confirm('恢复历史正文后会生成新修订，当前正文仍保留在历史中。', '恢复章节历史'))) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/revisions/${selectedHistoryRevision.value}/restore`,
    { revision: chapter.value.revision },
  )
  chapter.value = data
  draftContent.value = data.content
  sessionStorage.removeItem(draftKey(data.id))
  saveState.value = 'saved'
  updateSummary()
  historyOpen.value = false
  await loadProject()
}

const toggleSource = () => { showSource.value = !showSource.value }
const openReader = async () => {
  if (!project.value || !(await saveNow())) return
  await router.push({ path: `/reader/${project.value.bookId}`,
    query: { versionId: String(project.value.rewriteVersionId) } })
}

const handleProjectAction = async (action: string) => {
  if (!project.value || !(await saveNow())) return
  if (action === 'rename' || action === 'description') {
    const oldValue = action === 'rename' ? project.value.name : project.value.description || ''
    const value = window.prompt(action === 'rename' ? '项目名称' : '项目说明', oldValue)
    if (value === null || (action === 'rename' && !value.trim())) return
    const { data } = await api.patch(`/api/rewrite/projects/${projectId}`,
      action === 'rename' ? { name: value.trim() } : { description: value.trim() })
    project.value = data
    return
  }
  if (action === 'delete') {
    const count = project.value.chapterCount
    const approved = await confirm(
      `将删除重写项目、重写版本和 ${count} 个有效章节，并清除该版本的阅读进度。源版本保留。此操作无法恢复。`,
      '删除项目及版本',
    )
    if (!approved) return
    await api.delete(`/api/rewrite/projects/${projectId}`, {
      params: { deleteVersion: true },
    })
    await router.push('/rewrite')
    return
  }
  if (action === 'archive' && !(await confirm('归档后项目从默认列表隐藏，已保存版本仍可阅读。', '归档项目'))) return
  if (action === 'complete' && !(await confirm('完成后章节将固化，若需继续修改须创建新的重写项目。', '完成重写'))) return
  const path = action === 'restore' ? 'pause' : action
  try {
    const { data } = await api.post(`/api/rewrite/projects/${projectId}/${path}`,
      path === 'complete' ? { force: false } : undefined,
      { headers: { 'X-Suppress-Error-Toast': 'true' } },
    )
    project.value = data
    message.success('项目状态已更新')
  } catch (error: any) {
    if (path !== 'complete' || error.response?.status !== 409) {
      message.error(error.response?.data?.message || '项目操作失败')
      return
    }
    const unfinished = project.value.chapterCount - project.value.completedCount
    if (!(await confirm(`仍有 ${unfinished} 章未标记完成。确定继续完成重写？`, '确认未完成章节'))) return
    const { data } = await api.post(`/api/rewrite/projects/${projectId}/complete`, { force: true })
    project.value = data
    message.success('重写版本已完成')
  }
}

const copyDraft = async () => {
  await navigator.clipboard.writeText(draftContent.value)
  message.success('本地文字已复制')
}

const handleEditorKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void saveNow()
  }
}

const beforeUnload = (event: BeforeUnloadEvent) => {
  if (saveState.value !== 'saved') {
    event.preventDefault()
  }
}

onBeforeRouteLeave(async () => await saveNow())
onMounted(async () => {
  loading.value = true
  try {
    await loadChapters()
    if (chapters.value.length) {
      const preferred = chapters.value.find(item => item.id === project.value?.currentChapterId)
      await loadChapter(preferred?.id || chapters.value[0].id)
    }
  } finally {
    loading.value = false
  }
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<style scoped>
.rewrite-workspace { max-width: 1600px; min-height: 75vh; margin: 0 auto; padding: 20px 22px 110px; color: var(--text-primary, #24342d); }
.workspace-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; padding: 12px 0 24px; }
.workspace-header h1 { margin: 8px 0 4px; font-size: 28px; }
.workspace-header p { margin: 0; color: var(--text-secondary, #748078); }
.back-link { color: var(--el-color-primary); text-decoration: none; }
.header-actions, .chapter-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.save-indicator { font-size: 12px; color: var(--text-secondary, #748078); }
.save-indicator.dirty, .save-indicator.error, .save-indicator.conflict { color: #bb6b29; }
.workspace-body { display: grid; grid-template-columns: minmax(210px, 250px) minmax(0, 1fr); gap: 18px; align-items: start; }
.chapter-sidebar, .editor-panel { border: 1px solid var(--el-border-color-light, #d8dfd9); border-radius: 18px; background: var(--el-bg-color, #fff); }
.chapter-sidebar { max-height: calc(100vh - 170px); overflow: auto; padding: 15px; }
.sidebar-heading { display: flex; align-items: center; justify-content: space-between; }
.sidebar-progress { margin: 9px 0 18px; font-size: 12px; color: var(--text-secondary, #748078); }
.chapter-sidebar nav { display: grid; gap: 5px; }
.chapter-link { display: grid; gap: 3px; width: 100%; padding: 10px 12px; border: 0; border-radius: 10px; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.chapter-link.selected { background: color-mix(in srgb, var(--el-color-primary) 13%, transparent); }
.chapter-link:hover { background: color-mix(in srgb, var(--el-color-primary) 8%, transparent); }
.chapter-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chapter-link small { color: var(--text-secondary, #748078); }
.volume-heading { display: block; padding: 12px 10px 4px; font-size: 12px; color: var(--el-color-primary); }
.deleted-chapters { display: grid; gap: 4px; margin-top: 14px; padding-top: 10px; border-top: 1px solid #8883; }
.restore-chapter-button { padding: 7px; border: 0; border-radius: 8px; background: #8881; color: inherit; text-align: left; cursor: pointer; }
.editor-panel { min-width: 0; padding: 22px; }
.chapter-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.chapter-title-row { display: flex; align-items: center; gap: 10px; flex: 1; }
.chapter-title-row input { min-width: 120px; flex: 1; border: 0; border-bottom: 1px solid #8884; background: transparent; color: inherit; font-size: 22px; font-weight: 700; }
.chapter-title-row input:focus { outline: none; border-color: var(--el-color-primary); }
.chapter-title-row span { font-size: 12px; color: var(--text-secondary, #748078); }
.chapter-status-control { display: flex; position: relative; max-width: 480px; margin: 20px 0; padding: 4px; border-radius: 14px; background: rgba(128, 145, 135, .14); }
.chapter-status-slider { position: absolute; inset: 4px auto 4px 4px; width: calc((100% - 8px) / 4); border-radius: 10px; background: var(--el-bg-color, #fff); box-shadow: 0 2px 8px #0002; transition: transform .25s ease; }
.chapter-status-control button { z-index: 1; flex: 1; padding: 8px; border: 0; background: none; color: inherit; cursor: pointer; }
.chapter-status-control button[aria-pressed='true'] { font-weight: 700; }
.draft-notice, .conflict-notice { padding: 10px 14px; margin-bottom: 12px; border-radius: 10px; background: #fff1d8; color: #744711; }
.writing-surface { display: grid; grid-template-columns: 1fr; gap: 16px; }
.writing-surface.comparing { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
.source-pane, .draft-pane { min-width: 0; }
.pane-label { display: flex; justify-content: space-between; align-items: center; min-height: 32px; color: var(--text-secondary, #748078); font-size: 12px; }
.source-pane pre, .draft-pane textarea { box-sizing: border-box; width: 100%; min-height: 55vh; margin: 0; padding: 22px; border: 1px solid #8883; border-radius: 12px; background: var(--el-fill-color-lighter, #fafbf9); color: inherit; font-family: inherit; font-size: 16px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.source-pane pre { overflow: auto; }
.draft-pane textarea { resize: vertical; outline-color: var(--el-color-primary); }
.editor-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 20px; }
.empty-panel { min-height: 300px; display: grid; place-items: center; }
.history-dialog { display: grid; grid-template-columns: 210px minmax(0, 1fr); gap: 14px; min-height: 300px; }
.history-list { display: grid; align-content: start; gap: 6px; max-height: 50vh; overflow: auto; }
.history-list button { display: grid; gap: 4px; padding: 9px; border: 0; border-radius: 9px; background: #8881; color: inherit; text-align: left; cursor: pointer; }
.history-list button.selected { background: color-mix(in srgb, var(--el-color-primary) 18%, transparent); }
.history-list small { color: var(--text-secondary, #748078); }
.history-dialog pre { margin: 0; padding: 16px; max-height: 50vh; overflow: auto; border-radius: 10px; background: var(--el-fill-color-lighter, #fafbf9); white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 820px) { .workspace-body { grid-template-columns: 1fr; } .chapter-sidebar { max-height: 180px; } .writing-surface.comparing { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .chapter-status-slider { transition: none; } }
</style>
