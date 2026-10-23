<template>
  <el-dialog
    :model-value="visible"
    title="书籍对比"
    width="min(1320px, calc(100vw - 24px))"
    append-to-body
    top="4vh"
    class="book-comparison-dialog"
    :close-on-click-modal="false"
    @open="prepare"
    @close="emit('close')"
  >
    <div class="dialog-scroll-area">
      <p class="comparison-intro">左右独立选择书籍、版本和章节，核对详情与正文；对比不会执行合并。</p>
      <div class="comparison-status" role="status">{{ contentStatus }}</div>
      <div class="comparison-columns">
        <section v-for="(side, sideIndex) in sides" :key="sideIndex" class="comparison-pane">
          <header>
            <strong>{{ sideIndex === 0 ? '左侧书籍' : '右侧书籍' }}</strong>
            <el-select v-model="side.bookId" :aria-label="`${sideIndex === 0 ? '左侧' : '右侧'}书籍`" @change="loadSide(sideIndex, true)">
              <el-option v-for="book in books" :key="book.id" :value="book.id" :label="`${displayBookName(book.title)} · #${book.id}`" :disabled="book.id === sides[1 - sideIndex].bookId" />
            </el-select>
            <el-select
              v-if="side.versions.length"
              v-model="side.versionId"
              :disabled="side.loading"
              :aria-label="`${sideIndex === 0 ? '左侧' : '右侧'}版本`"
              @change="loadSide(sideIndex, false)"
            >
              <el-option v-for="version in side.versions" :key="version.id" :value="version.id" :label="`${version.displayName} · ${version.format.toUpperCase()}`" />
            </el-select>
          </header>
          <p v-if="side.loading" role="status">正在读取书籍详情和章节目录…</p>
          <el-alert v-if="side.error" :title="side.error" type="error" :closable="false" />
          <el-button v-if="side.error" @click="loadSide(sideIndex, true)">重试加载书籍</el-button>

          <template v-if="side.data">
            <dl class="comparison-details">
              <div><dt>书名</dt><dd>{{ displayBookName(side.data.book.title) }}</dd></div>
              <div><dt>作者</dt><dd>{{ side.data.book.author || '未知作者' }}</dd></div>
              <div><dt>来源</dt><dd>{{ sourceLabel(side.data.book) }}</dd></div>
              <div><dt>大小</dt><dd>{{ side.data.format === 'structured' ? '在线章节（无本地文件）' : sizeLabel(side.data.fileSize) }}</dd></div>
              <div><dt>章节</dt><dd>{{ chapterCountLabel(side.data.book.chapterCount) }} · 可对比 {{ side.data.chapters.length }} 章</dd></div>
              <div><dt>格式</dt><dd>{{ side.data.format.toUpperCase() }}</dd></div>
              <div><dt>ISBN</dt><dd>{{ side.data.book.isbn || '未填写' }}</dd></div>
              <div><dt>出版</dt><dd>{{ side.data.book.publisher || '未填写' }} {{ side.data.book.publishDate || '' }}</dd></div>
              <div v-if="side.data.book.sourcePath"><dt>目录来源</dt><dd>{{ side.data.book.sourcePath }}</dd></div>
            </dl>
            <p class="comparison-description">{{ side.data.book.description || '暂无简介' }}</p>
            <router-link :to="`/books/${side.bookId}`" target="_blank">打开完整详情</router-link>
            <el-alert v-if="side.data.message" :title="side.data.message" type="info" :closable="false" />

            <div v-if="side.data.chapters.length" class="comparison-catalogue">
              <h3>章节列表</h3>
              <el-input v-model="side.query" placeholder="搜索章节" :aria-label="`${sideIndex === 0 ? '左侧' : '右侧'}章节搜索`" clearable @input="side.page = 0" />
              <div class="comparison-chapter-list">
                <button
                  v-for="item in chapterPages[sideIndex].items"
                  :key="item.slot"
                  type="button"
                  :class="{ selected: side.chapterIndex === item.slot }"
                  :aria-pressed="side.chapterIndex === item.slot"
                  @click="selectChapter(sideIndex, item.slot)"
                >{{ item.slot + 1 }}. {{ item.chapter.title }}</button>
                <p v-if="!chapterPages[sideIndex].total">未找到章节</p>
              </div>
              <div v-if="chapterPages[sideIndex].total > chapterPageSize" class="chapter-pagination">
                <el-button :disabled="side.page === 0" @click="side.page--">上一页</el-button>
                <span>{{ side.page + 1 }} / {{ Math.ceil(chapterPages[sideIndex].total / chapterPageSize) }}</span>
                <el-button :disabled="(side.page + 1) * chapterPageSize >= chapterPages[sideIndex].total" @click="side.page++">下一页</el-button>
              </div>
            </div>

            <h3>章节正文 · {{ side.content?.title || side.data.chapters[side.chapterIndex]?.title || '未选择' }}</h3>
            <p v-if="side.contentLoading" role="status">正在读取章节正文…</p>
            <el-alert v-if="side.contentError" :title="side.contentError" type="error" :closable="false" />
            <el-button v-if="side.contentError" @click="loadChapter(sideIndex)">重试加载正文</el-button>
            <p v-if="side.content">{{ side.content.totalCharacters.toLocaleString() }} 字符</p>
            <el-alert v-if="side.content?.truncated" title="章节过长，仅展示前 50 万字符；请打开阅读器核对完整正文" type="warning" :closable="false" />
            <pre v-if="side.content" class="comparison-content">{{ side.content.content || '本章没有可提取的文本，可能包含图片。' }}</pre>
          </template>
        </section>
      </div>
    </div>
    <template #footer><el-button @click="emit('close')">关闭对比</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { computed, onUnmounted, reactive, watch } from 'vue'
import type { Book } from '@/stores/book'
import api from '@/utils/api'
import { chapterCountLabel, sizeLabel, sourceLabel, type ComparisonCandidate } from '@/utils/bookComparison'

const { displayBookName } = useBookNameDisplay()

interface ChapterEntry { index: number; title: string }
interface ComparisonData {
  book: Book
  versionId: number
  format: string
  fileSize?: number
  contentSupported: boolean
  message: string
  chapters: ChapterEntry[]
}
interface ChapterContent { index: number; title: string; content: string; totalCharacters: number; truncated: boolean }
interface Side {
  bookId: number
  versionId?: number
  versions: { id: number; displayName: string; format: string; primaryVersion: boolean }[]
  data: ComparisonData | null
  chapterIndex: number
  query: string
  page: number
  content: ChapterContent | null
  loading: boolean
  contentLoading: boolean
  error: string
  contentError: string
}
const props = defineProps<{ visible: boolean; books: ComparisonCandidate[] }>()
const emit = defineEmits<{ close: [] }>()
const newSide = (): Side => ({
  bookId: 0, versions: [], data: null, chapterIndex: 0, query: '', page: 0,
  content: null, loading: false, contentLoading: false, error: '', contentError: '',
})
const sides = reactive<Side[]>([newSide(), newSide()])
const summaryEpoch = [0, 0]
const contentEpoch = [0, 0]
const chapterPageSize = 100
const chapterPages = computed(() => sides.map(side => {
  const matching = (side.data?.chapters || []).map((chapter, slot) => ({ chapter, slot }))
    .filter(item => item.chapter.title.toLocaleLowerCase().includes(side.query.trim().toLocaleLowerCase()))
  return { total: matching.length, items: matching.slice(side.page * chapterPageSize, (side.page + 1) * chapterPageSize) }
}))
const contentStatus = computed(() => {
  const [left, right] = sides
  if (left.contentLoading || right.contentLoading) return '正在加载所选章节…'
  if (!left.content || !right.content) return '请选择两侧章节进行正文对比'
  if (!left.content.content.trim() || !right.content.content.trim()) return '所选章节缺少可提取文本，不能据此判断内容相同'
  const equal = left.content.content.replace(/\r\n/g, '\n').trim() === right.content.content.replace(/\r\n/g, '\n').trim()
  if (left.content.truncated || right.content.truncated) return equal ? '已展示部分一致，完整正文尚未核对' : '已展示部分存在差异'
  return equal ? '所选章节文本一致' : '所选章节文本不同，请对照下方正文'
})

const loadChapter = async (sideIndex: number) => {
  const side = sides[sideIndex]
  const request = ++contentEpoch[sideIndex]
  side.content = null
  side.contentError = ''
  if (!side.data?.chapters[side.chapterIndex] || !side.data.contentSupported) {
    side.contentLoading = false
    return
  }
  side.contentLoading = true
  try {
    const { data } = await api.get<ChapterContent>(`/api/books/${side.bookId}/comparison/chapters/${side.chapterIndex}`, {
      params: { versionId: side.data.versionId },
    })
    if (request === contentEpoch[sideIndex] && props.visible) side.content = data
  } catch (cause: any) {
    if (request === contentEpoch[sideIndex]) side.contentError = cause.response?.data?.message || '章节正文加载失败'
  } finally {
    if (request === contentEpoch[sideIndex]) side.contentLoading = false
  }
}

const loadSide = async (sideIndex: number, reloadVersions: boolean) => {
  const side = sides[sideIndex]
  const request = ++summaryEpoch[sideIndex]
  contentEpoch[sideIndex]++
  side.data = null
  side.content = null
  side.error = ''
  side.contentError = ''
  side.contentLoading = false
  side.loading = true
  side.query = ''
  side.page = 0
  if (reloadVersions) {
    side.versionId = undefined
    side.versions = []
  }
  try {
    if (reloadVersions) {
      const { data } = await api.get<Side['versions']>(`/api/books/${side.bookId}/versions`)
      if (request !== summaryEpoch[sideIndex] || !props.visible) return
      side.versions = data
      side.versionId = data.find(version => version.primaryVersion)?.id ?? data[0]?.id
    }
    const { data } = await api.get<ComparisonData>(`/api/books/${side.bookId}/comparison`, {
      params: { versionId: side.versionId },
    })
    if (request !== summaryEpoch[sideIndex] || !props.visible) return
    side.data = data
    side.versionId = data.versionId
    side.chapterIndex = 0
    await loadChapter(sideIndex)
  } catch (cause: any) {
    if (request === summaryEpoch[sideIndex]) side.error = cause.response?.data?.message || '书籍详情加载失败'
  } finally {
    if (request === summaryEpoch[sideIndex]) side.loading = false
  }
}

const selectChapter = (sideIndex: number, slot: number) => {
  sides[sideIndex].chapterIndex = slot
  loadChapter(sideIndex)
}

const invalidateRequests = () => {
  for (let index = 0; index < 2; index++) {
    summaryEpoch[index]++
    contentEpoch[index]++
  }
}

const prepare = () => {
  invalidateRequests()
  sides[0] = { ...newSide(), bookId: props.books[0]?.id || 0 }
  sides[1] = { ...newSide(), bookId: props.books[1]?.id || 0 }
  if (sides[0].bookId && sides[1].bookId) {
    loadSide(0, true)
    loadSide(1, true)
  }
}

watch(() => props.visible, visible => { if (!visible) invalidateRequests() })
onUnmounted(invalidateRequests)
</script>

<style scoped>
.dialog-scroll-area {
  max-height: calc(100dvh - 200px);
  overflow-y: auto;
  padding-right: 2px;
}

.comparison-intro {
  color: var(--text-secondary);
  line-height: 1.7;
}

.comparison-status {
  margin: 12px 0;
  padding: 12px;
  border-radius: 10px;
  background: var(--primary-alpha-10);
  font-weight: 600;
}

.comparison-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.comparison-pane {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.comparison-pane header {
  display: grid;
  gap: 10px;
}

.comparison-details div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px;
  padding: 5px 0;
}

.comparison-details dt {
  white-space: nowrap;
  color: var(--text-tertiary);
}

.comparison-details dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.comparison-description {
  max-height: 120px;
  overflow-y: auto;
  white-space: pre-wrap;
  line-height: 1.7;
}

.comparison-pane a {
  color: var(--primary);
}

.comparison-chapter-list {
  display: grid;
  max-height: 220px;
  overflow-y: auto;
  margin-top: 10px;
  gap: 4px;
}

.comparison-chapter-list button {
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: var(--surface-hover);
  color: var(--text-primary);
  cursor: pointer;
  text-align: left;
  overflow-wrap: anywhere;
}

.comparison-chapter-list button.selected {
  border-color: var(--primary);
  color: var(--primary);
}

.comparison-chapter-list button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}

.comparison-content {
  min-height: 200px;
  max-height: 480px;
  overflow-y: auto;
  padding: 14px;
  border: 1px solid var(--border-color-light);
  border-radius: 8px;
  background: var(--surface-card);
  font: inherit;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.chapter-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 760px) {
  .comparison-columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .comparison-pane {
    padding: 10px;
  }
}
</style>
