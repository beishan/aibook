<template>
  <div class="series-view">
    <header class="page-heading">
      <div><router-link to="/books" class="back-link">‹ 返回书库</router-link>
        <h1>系列与阅读顺序</h1><p>把散落的卷册，接成一段完整的阅读。</p></div>
      <span class="collection-count">{{ series.length }} <small>个系列</small></span>
    </header>
    <div v-if="summaryLoading" class="state glass" role="status">正在整理系列…</div>
    <div v-else-if="summaryError" class="state glass" role="alert">{{ summaryError }} <button class="btn" @click="loadSeries">重试</button></div>
    <div v-else-if="!series.length" class="state glass">
      <h2>从第一套书开始</h2><p>在书库编辑书籍，填写相同的系列名称和各自卷序，即可在这里按顺序浏览。</p>
      <router-link class="btn btn-primary" to="/books">去书库设置系列</router-link>
    </div>
    <div v-else class="series-layout">
      <aside class="series-sidebar glass" aria-label="选择系列">
        <label for="series-search">我的系列</label>
        <input id="series-search" v-model="keyword" class="input" placeholder="查找系列名称" type="search" />
        <nav>
          <router-link v-for="item in filteredSeries" :key="item.name"
            :to="{ path: '/series', query: { name: item.name } }"
            class="series-choice" :class="{ selected: selectedName === item.name }"
            :aria-current="selectedName === item.name ? 'page' : undefined">
            <strong>{{ item.name }}</strong><small>{{ item.bookCount }} 本 · 已读 {{ item.finishedCount }} 本</small>
          </router-link>
          <p v-if="!filteredSeries.length" class="muted">没有匹配的系列</p>
        </nav>
      </aside>
      <main class="series-content glass">
        <div class="series-title"><span class="eyebrow">按卷序阅读</span><h2>{{ selectedName || '选择一个系列' }}</h2>
          <p v-if="selectedSummary">已入库 {{ selectedSummary.bookCount }} 本 · 已读 {{ selectedSummary.finishedCount }} 本</p>
          <progress v-if="selectedSummary" :value="selectedSummary.finishedCount" :max="selectedSummary.bookCount" aria-label="系列阅读进度" />
        </div>
        <div v-if="booksLoading" class="state" role="status">正在加载卷册…</div>
        <div v-else-if="booksError" class="state" role="alert">{{ booksError }} <button class="btn" @click="loadBooks">重试</button></div>
        <template v-else>
          <div v-if="gaps.length || duplicateIndices.length" class="collection-note">
            <p v-if="gaps.length">可能缺少第 {{ gaps.join('、') }} 卷。仅按已登记的整数卷号推测。</p>
            <p v-if="duplicateIndices.length">第 {{ duplicateIndices.join('、') }} 卷有多条记录，可检查卷序或不同版本。</p>
          </div>
          <ol v-if="books.length" class="volume-list">
            <li v-for="book in books" :key="book.id" class="volume-row">
              <span class="volume-number">{{ book.seriesIndex ?? '—' }}</span>
              <router-link :to="`/books/${book.id}`" class="cover" :aria-label="`查看${book.title}`">
                <img v-if="book.coverUrl && !isBookCoverHidden(book.id)" :src="getCoverThumbnailUrl(book.coverUrl, 96)" alt="" loading="lazy" decoding="async" />
                <span v-else aria-hidden="true">{{ book.title.charAt(0) }}</span>
              </router-link>
              <div class="volume-info"><span class="volume-label">{{ volumeLabel(book.seriesIndex) }}</span>
                <router-link :to="`/books/${book.id}`" class="volume-title">{{ book.title }}</router-link>
                <small>{{ book.author || '未知作者' }} · {{ book.format.toUpperCase() }}</small>
              </div>
              <span class="reading-state" :class="{ finished: book.readingStatus === 'FINISHED' }">{{ statusLabel(book.readingStatus) }}</span>
              <button class="btn btn-sm" :disabled="editingId !== null" @click="editBook(book.id)">编辑</button>
            </li>
          </ol>
          <div v-else class="state">此系列暂无可显示的书籍。</div>
          <p v-if="books.some(book => book.seriesIndex == null)" class="muted">未填写卷序的书籍排在末尾，编辑后会自动调整顺序。</p>
        </template>
      </main>
    </div>
    <BookEditDialog :visible="editingBook !== null" :book="editingBook" @close="editingBook = null" @saved="onSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/utils/api'
import { message } from '@/utils/message'
import { getCoverThumbnailUrl } from '@/utils/cover'
import { isBookCoverHidden } from '@/utils/imagePrivacy'
import { missingVolumeRanges, volumeLabel, type SeriesBook, type SeriesSummary } from '@/utils/series'
import { useBookStore, type Book } from '@/stores/book'
import BookEditDialog from '@/components/BookEditDialog.vue'
const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()
const series = ref<SeriesSummary[]>([])
const books = ref<SeriesBook[]>([])
const keyword = ref('')
const summaryLoading = ref(true)
const summaryError = ref('')
const booksLoading = ref(false)
const booksError = ref('')
const editingBook = ref<Book | null>(null)
const editingId = ref<number | null>(null)
let bookSequence = 0
const selectedName = computed(() => typeof route.query.name === 'string' ? route.query.name : '')
const filteredSeries = computed(() => series.value.filter(item => item.name.toLocaleLowerCase().includes(keyword.value.trim().toLocaleLowerCase())))
const selectedSummary = computed(() => series.value.find(item => item.name === selectedName.value))
const gaps = computed(() => missingVolumeRanges(books.value))
const duplicateIndices = computed(() => {
  const counts = new Map<number, number>()
  for (const book of books.value) if (book.seriesIndex != null) counts.set(book.seriesIndex, (counts.get(book.seriesIndex) || 0) + 1)
  return [...counts].filter(([, count]) => count > 1).map(([index]) => index)
})
const statusNames: Record<string, string> = { FINISHED: '已读完', READING: '正在读', UNREADING: '未读' }
const statusLabel = (status: string) => statusNames[status] || '未读'
async function loadSeries() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    series.value = (await api.get('/api/series')).data
    if (!series.value.some(item => item.name === selectedName.value)) {
      await router.replace({ path: '/series', query: series.value.length ? { name: series.value[0].name } : {} })
    }
  } catch { summaryError.value = '系列加载失败，请重试。' }
  finally { summaryLoading.value = false }
}
async function loadBooks() {
  const request = ++bookSequence
  books.value = []
  booksError.value = ''
  booksLoading.value = false
  if (!selectedName.value) return
  booksLoading.value = true
  try {
    const { data } = await api.get('/api/series/books', { params: { name: selectedName.value } })
    if (request === bookSequence) books.value = data
  } catch { if (request === bookSequence) booksError.value = '卷册加载失败，请重试。' }
  finally { if (request === bookSequence) booksLoading.value = false }
}
async function editBook(id: number) {
  editingId.value = id
  try { editingBook.value = await bookStore.fetchBookById(id) }
  catch { message.error('书籍加载失败') }
  finally { editingId.value = null }
}
async function onSaved() {
  editingBook.value = null
  await loadSeries()
  await loadBooks()
}
watch(selectedName, loadBooks, { immediate: true })
onMounted(loadSeries)
</script>

<style scoped>
.series-view { max-width: 1180px; margin: 0 auto; padding: 28px 0; }
.page-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 28px; gap: 20px; }
.back-link { color: var(--text-secondary); font-size: 13px; }
h1 { margin: 12px 0 8px; font-size: clamp(25px, 3vw, 34px); letter-spacing: -.04em; }
.page-heading p, .muted { color: var(--text-secondary); font-size: 13px; }
.collection-count { color: var(--primary); font-size: 42px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.collection-count small { font-size: 13px; color: var(--text-secondary); }
.series-layout { display: grid; grid-template-columns: 250px minmax(0, 1fr); align-items: start; gap: 22px; }
.series-sidebar { padding: 20px 14px; border-radius: 16px; }
.series-sidebar label { display: block; font-weight: 600; margin: 0 6px 12px; }
.series-sidebar input { box-sizing: border-box; width: 100%; margin-bottom: 14px; }
.series-sidebar nav { max-height: 65vh; overflow-y: auto; }
.series-choice { display: block; padding: 14px 12px; border-left: 3px solid transparent; color: var(--text-primary); border-radius: 4px; }
.series-choice strong, .series-choice small { display: block; overflow-wrap: anywhere; }
.series-choice strong { font-size: 14px; }
.series-choice small { color: var(--text-secondary); margin-top: 6px; font-size: 12px; }
.series-choice.selected { border-left-color: var(--primary); background: var(--primary-alpha-10); }
.series-content { min-width: 0; border-radius: 16px; padding: 28px; }
.eyebrow { font-size: 11px; letter-spacing: .15em; color: var(--primary); }
.series-title h2 { margin: 10px 0; font-size: 28px; overflow-wrap: anywhere; }
.series-title p { font-size: 13px; color: var(--text-secondary); }
progress { width: 100%; height: 5px; accent-color: var(--primary); margin: 4px 0 22px; }
.collection-note { padding: 12px 16px; border: 1px solid var(--border-color); border-radius: 8px; font-size: 12px; color: var(--text-secondary); }
.collection-note p { margin: 4px 0; line-height: 1.7; }
.volume-list { list-style: none; padding: 0; margin: 10px 0; }
.volume-row { display: flex; gap: 16px; align-items: center; padding: 20px 0; border-bottom: 1px solid var(--border-color); }
.volume-number { min-width: 32px; color: var(--text-tertiary); font-size: 20px; font-variant-numeric: tabular-nums; }
.cover { width: 48px; height: 68px; flex-shrink: 0; border-radius: 5px; overflow: hidden; background: var(--primary-alpha-10); color: var(--primary); display: grid; place-items: center; }
.cover img { width: 100%; height: 100%; object-fit: cover; }
.volume-info { flex: 1; min-width: 0; }
.volume-label { color: var(--primary); font-size: 11px; }
.volume-title { display: block; margin: 5px 0 6px; font-weight: 600; color: var(--text-primary); overflow-wrap: anywhere; }
.volume-info small { font-size: 12px; color: var(--text-secondary); }
.reading-state { white-space: nowrap; color: var(--text-secondary); font-size: 12px; }
.reading-state.finished { color: var(--primary); }
.state { padding: 50px 24px; text-align: center; border-radius: 16px; color: var(--text-secondary); }
.state p { line-height: 1.8; max-width: 440px; margin: 16px auto 24px; }
a:focus-visible, button:focus-visible { outline: 2px solid var(--primary); outline-offset: 3px; }
@media (max-width: 760px) {
  .series-layout { grid-template-columns: 1fr; }
  .series-sidebar nav { display: flex; overflow-x: auto; max-height: none; }
  .series-choice { flex: 0 0 160px; }
  .series-content { padding: 20px 16px; }
  .volume-row { gap: 10px; flex-wrap: wrap; }
  .volume-number { display: none; }
  .volume-info { min-width: 130px; }
  .page-heading { align-items: flex-start; }
  .collection-count { font-size: 28px; }
}
</style>
