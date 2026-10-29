<template>
  <div v-if="book" class="audio-detail">
    <header>
      <router-link class="btn" to="/audiobooks">← 返回有声书库</router-link>
      <button class="btn" @click="showWorkEdit = true">编辑作品资料与封面</button>
      <button class="btn" @click="showEdit = true">编辑版本资料</button>
    </header>
    <section class="audio-hero glass">
      <img
        v-if="book.coverUrl && shouldLoadBookCover(book.bookId)"
        :src="getCoverThumbnailUrl(book.coverUrl, 'detail')"
        :alt="displayBookName(book.title)"
      />
      <div v-else class="audio-cover-placeholder">♫</div>
      <div class="audio-summary">
        <small>有声书 · {{ book.language }}</small>
        <h1>{{ displayBookName(book.title) }}</h1>
        <p>{{ book.author || '未知作者' }}</p>
        <p>{{ book.description || '暂无版本简介' }}</p>
        <label
          >演播版本
          <select
            :value="book.id"
            @change="router.push(`/audiobooks/${($event.target as HTMLSelectElement).value}`)"
          >
            <option v-for="edition in book.editions" :key="edition.id" :value="edition.id">
              {{ edition.name }} · {{ edition.narrator || '未填写演播者' }}
            </option>
          </select>
        </label>
        <p>
          {{ book.narrator || '未填写演播者' }} · {{ book.tracks.length }} 集 ·
          {{ audioTime(book.durationMs) }}
        </p>
        <div class="audio-actions">
          <router-link class="btn btn-primary" :to="`/audiobooks/${book.id}/listen`">{{
            book.progress.status === 'NEW' ? '开始收听' : '继续收听'
          }}</router-link>
          <router-link v-if="book.ebookFormat" class="btn" :to="`/books/${book.bookId}`"
            >阅读电子版</router-link
          >
          <button class="btn" @click="favorite">
            {{ work?.isFavorite ? '取消收藏' : '收藏作品' }}
          </button>
          <button class="btn" @click="showImport = true">添加 / 重新探测音轨</button>
          <button class="btn" @click="setStatus(false)">重新开始</button>
          <button class="btn" @click="setStatus(true)">标记听完</button>
          <button class="btn" @click="recycle">移入有声版本回收站</button>
        </div>
        <small>阅读与收听分别记录进度；切换演播版本不会自动对齐章节。</small>
      </div>
    </section>
    <section class="audio-chapters glass">
      <AudioSegmented
        v-model="tab"
        label="有声书内容"
        :options="[
          { value: 'chapters', label: '章节' },
          { value: 'tracks', label: '音轨顺序' },
          { value: 'bookmarks', label: '书签' },
        ]"
      />
      <template v-if="tab === 'chapters'">
        <button class="btn" :disabled="!book.tracks.length" @click="showChapters = true">
          编辑章节
        </button>
        <div v-for="(chapter, index) in book.chapters" :key="chapter.id" class="audio-row">
          <span>{{ String(index + 1).padStart(2, '0') }}</span
          ><strong>{{ chapter.title }}</strong>
          <button class="btn" @click="listen(chapter.segments[0])">收听</button>
        </div>
      </template>
      <template v-if="tab === 'tracks'">
        <p>调整音轨顺序会终止当前播放会话；原有书签及音轨内位置保留。</p>
        <div v-for="(track, index) in book.tracks" :key="track.id" class="audio-row">
          <span>{{ index + 1 }}</span
          ><strong>{{ track.title }}</strong
          ><small>{{ audioTime(track.durationMs) }}</small>
          <button
            class="btn"
            :disabled="index === 0"
            aria-label="上移音轨"
            @click="reorder(index, -1)"
          >
            ↑
          </button>
          <button
            class="btn"
            :disabled="index === book.tracks.length - 1"
            aria-label="下移音轨"
            @click="reorder(index, 1)"
          >
            ↓
          </button>
        </div>
      </template>
      <template v-if="tab === 'bookmarks'">
        <p v-if="!book.bookmarks.length">尚无音频书签。收听时可为当前时间点添加书签。</p>
        <div v-for="bookmark in book.bookmarks" :key="bookmark.id" class="audio-row">
          <strong>{{ bookmark.title }}</strong
          ><small>{{ audioTime(bookmark.positionMs) }}</small>
          <button
            class="btn"
            @click="
              listen({
                trackId: bookmark.trackId,
                startMs: bookmark.positionMs,
              })
            "
          >
            跳转
          </button>
          <button class="btn" @click="removeBookmark(bookmark.id)">删除</button>
        </div>
      </template>
    </section>
    <BookEditDialog
      :visible="showWorkEdit"
      :book="work"
      @close="showWorkEdit = false"
      @saved="load"
    />
    <AudioChapterEditor v-model="showChapters" :book="book" @saved="load" />
    <el-dialog v-model="showEdit" title="编辑有声版本资料" width="min(600px, 94vw)" append-to-body>
      <form class="audio-edit" @submit.prevent="saveEdit">
        <label>版本名称<input v-model="draft.name" class="input" required maxlength="200" /></label>
        <label>演播者<input v-model="draft.narrator" class="input" maxlength="200" /></label>
        <label>语言<input v-model="draft.language" class="input" maxlength="80" /></label>
        <label
          >版本简介<textarea v-model="draft.description" class="input" rows="5" maxlength="20000" />
        </label>
        <button class="btn btn-primary">保存资料</button>
      </form>
    </el-dialog>
    <AudiobookImport
      v-model="showImport"
      :edition-id="book.id"
      :book-id="book.bookId"
      @imported="load"
    />
  </div>
  <p v-else role="status">正在加载有声版本…</p>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import api from '@/utils/api'
import BookEditDialog from '@/components/BookEditDialog.vue'
import AudioChapterEditor from '@/components/audio/AudioChapterEditor.vue'
import { audioApi, audioTime, type Audiobook } from '@/utils/audiobook'
import { useAudioPlayerStore } from '@/stores/audioPlayer'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import { getCoverThumbnailUrl } from '@/utils/cover'
import AudioSegmented from '@/components/audio/AudioSegmented.vue'
import AudiobookImport from '@/components/audio/AudiobookImport.vue'
const book = ref<Audiobook | null>(null),
  showImport = ref(false),
  showEdit = ref(false),
  tab = ref('chapters')
const work = ref<any>(null),
  showWorkEdit = ref(false),
  showChapters = ref(false)
const draft = ref({ name: '', narrator: '', language: '', description: '' })
watch(showEdit, (value) => {
  if (value && book.value)
    draft.value = {
      name: book.value.name,
      narrator: book.value.narrator,
      language: book.value.language,
      description: book.value.description,
    }
})
const route = useRoute(),
  router = useRouter(),
  player = useAudioPlayerStore()
const { displayBookName } = useBookNameDisplay()
async function load() {
  book.value = await audioApi.detail(Number(route.params.id))
  work.value = (await api.get(`/api/books/${book.value.bookId}`)).data
}
async function favorite() {
  if (!book.value) return
  await api.put(`/api/books/${book.value.bookId}/favorite`)
  await load()
}
watch(() => route.params.id, load, { immediate: true })
async function listen(segment: { trackId: number; startMs: number }) {
  if (!book.value) return
  await router.push({
    path: `/audiobooks/${book.value.id}/listen`,
    query: { track: segment.trackId, position: segment.startMs },
  })
}
async function reorder(index: number, offset: number) {
  if (!book.value) return
  const ids = book.value.tracks.map((track) => track.id)
  ;[ids[index], ids[index + offset]] = [ids[index + offset], ids[index]]
  await api.put(`/api/audiobooks/${book.value.id}/tracks/order`, ids)
  if (player.book?.id === book.value.id) await player.close()
  await load()
}
async function removeBookmark(id: number) {
  await api.delete(`/api/audiobooks/bookmarks/${id}`)
  await load()
}
async function saveEdit() {
  if (!book.value) return
  await api.put(`/api/audiobooks/${book.value.id}`, {
    ...book.value,
    ...draft.value,
  })
  showEdit.value = false
  await load()
}
async function setStatus(finished: boolean) {
  if (!book.value) return
  try {
    await ElMessageBox.confirm(
      finished ? '标记此演播版本为已听完？' : '将此演播版本的进度重置为开头？',
      '调整收听状态'
    )
    if (player.book?.id === book.value.id) await player.close()
    await api.post(`/api/audiobooks/${book.value.id}/progress/status`, null, {
      params: { finished },
    })
    await load()
  } catch {
    /* Keep the current position when cancelled. */
  }
}
async function recycle() {
  if (!book.value) return
  try {
    await ElMessageBox.confirm('此操作仅回收有声版本，保留原文件及进度。', '移入有声版本回收站')
    await api.delete(`/api/audiobooks/${book.value.id}`)
    if (player.book?.id === book.value.id) await player.close()
    await router.push('/audiobooks')
  } catch {
    /* Cancellation or API error. */
  }
}
</script>
<style scoped>
.audio-detail {
  padding: 24px 24px 260px;
  max-width: 1500px;
  margin: auto;
  color: var(--text-primary);
}
header,
.audio-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-bottom: 20px;
}
.audio-hero {
  display: grid;
  grid-template-columns: 230px 1fr;
  gap: 32px;
  padding: 24px;
  border-radius: 24px;
}
.audio-hero img,
.audio-cover-placeholder {
  width: 230px;
  height: 320px;
  object-fit: cover;
  border-radius: 16px;
}
.audio-cover-placeholder {
  display: grid;
  place-items: center;
  background: var(--primary-alpha-10);
  font-size: 60px;
  color: var(--primary);
}
.audio-edit {
  display: grid;
  gap: 16px;
}
.audio-summary {
  display: grid;
  gap: 16px;
  align-content: start;
}
h1 {
  font-size: 34px;
}
p,
small {
  color: var(--text-secondary);
  line-height: 1.7;
}
.audio-summary p {
  white-space: pre-wrap;
}
.audio-actions {
  justify-content: flex-start;
  margin: 0;
}
label {
  display: grid;
  gap: 8px;
}
select {
  padding: 12px;
  background: var(--bg-surface);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.audio-chapters {
  padding: 20px;
  margin-top: 24px;
  border-radius: 24px;
}
.audio-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--border-color-light);
}
.audio-row strong {
  flex: 1;
  overflow-wrap: anywhere;
}
@media (max-width: 700px) {
  .audio-detail {
    padding: 12px 10px 240px;
  }
  .audio-hero {
    grid-template-columns: 1fr;
    padding: 18px;
  }
  .audio-hero img,
  .audio-cover-placeholder {
    width: 150px;
    height: 210px;
    margin: auto;
  }
  .audio-row {
    flex-wrap: wrap;
  }
}
</style>
