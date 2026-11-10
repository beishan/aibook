<template>
  <div class="audio-library">
    <header class="audio-library-header">
      <div class="library-heading">
        <div class="library-title-row">
          <h1>书库</h1>
          <AudioSegmented
            model-value="audio"
            label="书库内容"
            :options="[
              { value: 'ebook', label: '电子书' },
              { value: 'audio', label: '有声书' },
            ]"
            @update:model-value="(value) => value === 'ebook' && router.push('/books')"
          />
        </div>
        <p>{{ total }} 个演播版本，阅读与收听分别记录进度</p>
      </div>
      <div class="audio-header-actions">
        <button class="btn" :disabled="status === 'RECYCLED' || preparing" @click="toggleSelection">
          {{ selectionMode ? '退出多选' : '批量处理' }}
        </button>
        <button class="btn btn-primary" @click="showImport = true">＋ 导入有声书</button>
      </div>
    </header>
    <section class="audio-filter glass">
      <form
        @submit.prevent="resetAndLoad"
      >
        <input
          v-model="keyword"
          class="input"
          placeholder="搜索书名、作者或演播者"
          aria-label="搜索有声书"
        />
        <button class="btn">搜索</button>
      </form>
      <label class="audio-favorite-filter">
        <input v-model="favoriteOnly" type="checkbox" @change="resetAndLoad" />
        仅收藏作品
      </label>
      <select
        v-model="status"
        aria-label="收听状态"
        @change="resetAndLoad"
      >
        <option value="">全部状态</option>
        <option value="NEW">未收听</option>
        <option value="LISTENING">正在收听</option>
        <option value="FINISHED">已听完</option>
        <option value="RECYCLED">有声版本回收站</option>
      </select>
    </section>
    <section v-if="selectionMode" class="audio-batch-bar glass" aria-label="批量处理有声书">
      <span>已选 {{ selectedIds.length }} 本（仅当前页）</span>
      <button class="btn" :disabled="!items.length" @click="selectPage">全选当前页</button>
      <button class="btn" :disabled="!selectedIds.length" @click="selectedIds = []">清空选择</button>
      <button class="btn btn-primary" :disabled="!selectedIds.length || preparing" @click="openCleanup">
        {{ preparing ? '加载章节中…' : '批量清理章节名' }}
      </button>
    </section>
    <section v-if="continuing" class="audio-continue glass">
      <div>
        <small>继续收听</small>
        <h2>{{ displayBookName(continuing.title) }}</h2>
        <p>
          {{ continuing.narrator || '未填写演播者' }} · 上次位置
          {{ audioTime(continuing.positionMs || 0) }}
        </p>
      </div>
      <router-link class="btn btn-primary" :to="`/audiobooks/${continuing.id}/listen`"
        >继续收听</router-link
      >
    </section>
    <p v-if="loading" role="status">正在加载有声书…</p>
    <section v-else-if="!items.length" class="audio-empty glass">
      <h2>这里还没有符合条件的有声书</h2>
      <p>上传 MP3、M4A、M4B，或从已配置的 NAS 目录探测导入。</p>
      <button class="btn btn-primary" @click="showImport = true">导入有声书</button>
    </section>
    <div v-else class="audio-grid">
      <router-link
        v-for="item in items"
        :key="item.id"
        :to="`/audiobooks/${item.id}`"
        class="audio-card glass"
        :role="selectionMode ? 'checkbox' : undefined"
        :aria-checked="selectionMode ? selectedIds.includes(item.id) : undefined"
        @keydown.space="handleCardSpace($event, item.id)"
        @click="handleCardClick($event, item.id)"
      >
        <span v-if="selectionMode" class="audio-select" :class="{ selected: selectedIds.includes(item.id) }"
          :aria-label="selectedIds.includes(item.id) ? '已选择' : '未选择'" aria-hidden="true">
          {{ selectedIds.includes(item.id) ? '✓' : '' }}
        </span>
        <img
          v-if="item.coverUrl && shouldLoadBookCover(item.bookId)"
          :src="getCoverThumbnailUrl(item.coverUrl, 'detail')"
          :alt="`${displayBookName(item.title)}封面`"
        />
        <div v-else class="audio-cover-placeholder" aria-hidden="true"><BookCoverPlaceholderText :title="displayBookName(item.title)" /></div>
        <div class="audio-card-summary">
          <small class="audio-badge">{{ item.ebookFormat ? '电子 + 有声' : '有声版' }}</small>
          <h2>{{ displayBookName(item.title) }}</h2>
          <p>{{ item.author || '未知作者' }}</p>
          <p>{{ item.narrator || '未填写演播者' }}</p>
          <strong>{{ item.name }}</strong>
          <small>{{ item.trackCount }} 集 · {{ audioTime(item.durationMs) }}</small>
          <small>{{ statusName(item.status) }}</small>
          <button v-if="status === 'RECYCLED'" class="btn" @click.stop.prevent="restore(item.id)">
            恢复版本
          </button>
        </div>
      </router-link>
    </div>
    <AudioListeningStatistics />
    <footer class="audio-pagination">
      <button
        class="btn"
        :disabled="page === 0"
        @click="previousPage"
      >
        上一页</button
      ><span>{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 24)) }}</span>
      <button
        class="btn"
        :disabled="(page + 1) * 24 >= total"
        @click="nextPage"
      >
        下一页
      </button>
    </footer>
    <el-dialog v-model="showCleanup" title="批量清理章节名" width="min(760px, 94vw)" append-to-body
      :close-on-click-modal="!applying" :close-on-press-escape="!applying" :show-close="!applying">
      <p>对选中的 {{ cleanupBooks.length }} 个演播版本，按原文删除章节名称中的文字。保存会结束受影响版本的播放会话，不修改音轨标题或原始文件。</p>
      <label class="cleanup-input">要删除的文字
        <input v-model="cleanupText" class="input" :disabled="applying"
          placeholder="例如：【持续更新关注闲鱼：大猫券码】" />
      </label>
      <p v-if="cleanupText" role="status">匹配 {{ cleanupCount }} 个章节，涉及 {{ cleanupMatches.length }} 本有声书。</p>
      <p v-if="cleanupHasEmpty" role="alert" class="cleanup-error">有章节清理后名称为空，请调整删除文字。</p>
      <div v-if="cleanupMatches.length" class="cleanup-preview">
        <section v-for="book in cleanupMatches" :key="book.id" class="cleanup-book">
          <strong>{{ book.title }} · {{ book.name }}（{{ book.changes.length }} 章）</strong>
          <div v-for="change in book.changes.slice(0, 2)" :key="change.id" class="cleanup-example">
            <span>{{ change.before }}</span>
            <span aria-hidden="true">↓</span>
            <strong>{{ change.after || '清理后为空' }}</strong>
          </div>
          <small v-if="book.changes.length > 2">仅展示前 2 章</small>
        </section>
      </div>
      <p v-if="cleanupResults.length">如需处理剩余书籍，请关闭窗口后重新预览。</p>
      <div v-if="cleanupResults.length" role="status" class="cleanup-results">
        <p v-for="result in cleanupResults" :key="result.id">{{ result.title }}：{{ result.message }}</p>
      </div>
      <template #footer>
        <button class="btn" :disabled="applying" @click="showCleanup = false">关闭</button>
        <button class="btn btn-primary" :disabled="applying || !cleanupCount || cleanupHasEmpty || cleanupResults.length > 0"
          @click="applyBulkCleanup">{{ applying ? '处理中…' : '确认清理' }}</button>
      </template>
    </el-dialog>
    <AudiobookImport v-model="showImport" @imported="(id) => router.push(`/audiobooks/${id}`)" />
  </div>
</template>
<script setup lang="ts">
import BookCoverPlaceholderText from '@/components/BookCoverPlaceholderText.vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/utils/api'
import { audioApi, audioTime, type Audiobook } from '@/utils/audiobook'
import { previewChapterCleanup } from '@/utils/audioChapterCleanup'
import { useAudioPlayerStore } from '@/stores/audioPlayer'
import { message } from '@/utils/message'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import { getCoverThumbnailUrl } from '@/utils/cover'
import AudioListeningStatistics from '@/components/audio/AudioListeningStatistics.vue'
import AudioSegmented from '@/components/audio/AudioSegmented.vue'
import AudiobookImport from '@/components/audio/AudiobookImport.vue'
const router = useRouter()
const { displayBookName } = useBookNameDisplay()
const favoriteOnly = ref(false)
const keyword = ref(''),
  status = ref(''),
  page = ref(0),
  total = ref(0)
const items = ref<any[]>([]),
  loading = ref(false),
  showImport = ref(false)
const selectionMode = ref(false)
const selectedIds = ref<number[]>([])
const preparing = ref(false)
const applying = ref(false)
const showCleanup = ref(false)
const cleanupText = ref('')
const cleanupBooks = ref<Audiobook[]>([])
const cleanupResults = ref<Array<{ id: number; title: string; message: string }>>([])
const cleanupMatches = computed(() => cleanupBooks.value.flatMap((book) => {
  const changes = previewChapterCleanup(book.chapters.map((chapter) => chapter.title), cleanupText.value)
    .map((item) => ({ ...item, id: book.chapters[item.index].id }))
  return changes.length ? [{ ...book, changes }] : []
}))
const cleanupCount = computed(() => cleanupMatches.value.reduce((sum, book) => sum + book.changes.length, 0))
const cleanupHasEmpty = computed(() => cleanupMatches.value.some((book) => book.changes.some((item) => !item.after)))
const continuing = computed(() => items.value.find((item) => item.status === 'LISTENING'))
let request = 0
function resetAndLoad() {
  selectedIds.value = []
  if (status.value === 'RECYCLED') selectionMode.value = false
  page.value = 0
  void load()
}
function previousPage() {
  selectedIds.value = []
  page.value--
  void load()
}
function nextPage() {
  selectedIds.value = []
  page.value++
  void load()
}
async function load() {
  const selected = ++request
  loading.value = true
  try {
    const data = await audioApi.list({
      keyword: keyword.value,
      status: status.value,
      favorite: favoriteOnly.value,
      page: page.value,
      size: 24,
    })
    if (selected === request) {
      items.value = data.content
      total.value = data.totalElements
    }
  } finally {
    if (selected === request) loading.value = false
  }
}
function toggleSelection() {
  selectionMode.value = !selectionMode.value
  selectedIds.value = []
}
function selectPage() {
  selectedIds.value = items.value.map((item) => item.id)
}
function handleCardSpace(event: KeyboardEvent, id: number) {
  if (!selectionMode.value) return
  event.preventDefault()
  toggleSelectedId(id)
}
function toggleSelectedId(id: number) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((selected) => selected !== id)
    : [...selectedIds.value, id]
}
function handleCardClick(event: MouseEvent, id: number) {
  if (status.value === 'RECYCLED') event.preventDefault()
  if (!selectionMode.value) return
  event.preventDefault()
  toggleSelectedId(id)
}
async function openCleanup() {
  preparing.value = true
  try {
    cleanupBooks.value = await Promise.all(selectedIds.value.map((id) => audioApi.detail(id)))
    cleanupText.value = ''
    cleanupResults.value = []
    showCleanup.value = true
  } catch {
    message.error('加载所选有声书失败，请重试')
  } finally {
    preparing.value = false
  }
}
async function applyBulkCleanup() {
  if (applying.value || !cleanupCount.value || cleanupHasEmpty.value) return
  applying.value = true
  cleanupResults.value = []
  const player = useAudioPlayerStore()
  const succeeded = new Set<number>()
  try {
    for (const book of cleanupMatches.value) {
      try {
        if (player.book?.id === book.id) await player.close()
        await api.put(`/api/audiobooks/${book.id}/chapter-titles`, {
          contentRevision: book.contentRevision,
          changes: book.changes.map(({ id, before, after }) => ({ id, before, after })),
        })
        succeeded.add(book.id)
        cleanupResults.value.push({ id: book.id, title: book.title, message: `已清理 ${book.changes.length} 章` })
      } catch (error: any) {
        cleanupResults.value.push({ id: book.id, title: book.title, message: error.response?.data?.message || '处理失败，请重新预览' })
      }
    }
    cleanupBooks.value = cleanupBooks.value.filter((book) => !succeeded.has(book.id))
    selectedIds.value = selectedIds.value.filter((id) => !succeeded.has(id))
    if (!selectedIds.value.length) selectionMode.value = false
  } finally {
    applying.value = false
  }
}
async function restore(id: number) {
  await api.post(`/api/audiobooks/${id}/restore`)
  await load()
}
function statusName(value: string) {
  return { NEW: '未收听', LISTENING: '正在收听', FINISHED: '已听完' }[value] || value
}
onMounted(load)
</script>
<style scoped>
.audio-library {
  width: 100%;
  min-width: 0;
  max-width: none;
  margin: 0 auto;
  padding: var(--spacing-lg) 0 260px;
  color: var(--text-primary);
}
.audio-header-actions,
.audio-batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.audio-batch-bar {
  padding: 14px 16px;
  margin-bottom: 20px;
  border-radius: 16px;
}
.audio-select {
  flex: 0 0 22px;
  height: 22px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  text-align: center;
}
.audio-select.selected {
  color: white;
  background: var(--primary);
  border-color: var(--primary);
}
.cleanup-input {
  display: grid;
  gap: 8px;
  margin: 16px 0;
}
.cleanup-preview {
  max-height: 40vh;
  overflow: auto;
}
.cleanup-book {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);
}
.cleanup-example {
  display: grid;
  gap: 4px;
  padding: 8px;
  overflow-wrap: anywhere;
}
.cleanup-example span:first-child {
  color: var(--text-secondary);
}
.cleanup-error {
  color: var(--danger, #c0392b);
}
.audio-library-header,
.audio-filter,
.audio-continue,
.audio-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.library-heading {
  min-width: 0;
}
.library-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 24px;
  margin-bottom: 10px;
}
.library-title-row h1 {
  margin-bottom: 0;
}
.library-title-row .audio-segmented {
  max-width: 100%;
}
h1 {
  font-size: 30px;
  margin-bottom: 10px;
}
p,
small {
  color: var(--text-secondary);
}
.audio-filter {
  padding: 16px;
  border-radius: 20px;
  margin: 24px 0;
}
.audio-filter form {
  flex: 1;
  display: flex;
  gap: 10px;
  min-width: 200px;
}
.audio-filter form input {
  width: 100%;
}
.audio-filter button {
  white-space: nowrap;
  flex-shrink: 0;
}
select {
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-primary);
  border-radius: 10px;
  padding: 10px;
}
.audio-continue {
  padding: 24px;
  border-radius: 24px;
  margin-bottom: 24px;
}
.audio-continue h2 {
  margin: 8px 0;
}
.audio-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
}
.audio-card {
  border-radius: 20px;
  padding: 16px;
  display: flex;
  gap: 16px;
  color: inherit;
  text-decoration: none;
  min-width: 0;
}
.audio-card img,
.audio-cover-placeholder {
  width: 105px;
  height: 155px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}
.audio-cover-placeholder {
  background: var(--primary-alpha-10);
  display: grid;
  place-items: center;
  color: var(--primary);
  font-size: 40px;
}
.audio-card-summary {
  display: grid;
  gap: 7px;
  min-width: 0;
}
.audio-card h2 {
  font-size: 19px;
  overflow-wrap: anywhere;
}
.audio-badge {
  color: var(--primary);
}
.audio-pagination {
  justify-content: center;
  padding-top: 24px;
}
.audio-empty {
  padding: 48px 24px;
  text-align: center;
  border-radius: 24px;
  display: grid;
  gap: 18px;
  justify-items: center;
}
@media (max-width: 600px) {
  .audio-library {
    padding: 16px 0 240px;
  }
  .audio-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped>
.audio-favorite-filter { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
</style>
