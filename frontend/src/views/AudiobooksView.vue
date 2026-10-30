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
      <button class="btn btn-primary" @click="showImport = true">＋ 导入有声书</button>
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
        @click="status === 'RECYCLED' && $event.preventDefault()"
      >
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
    <AudiobookImport v-model="showImport" @imported="(id) => router.push(`/audiobooks/${id}`)" />
  </div>
</template>
<script setup lang="ts">
import BookCoverPlaceholderText from '@/components/BookCoverPlaceholderText.vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/utils/api'
import { audioApi, audioTime } from '@/utils/audiobook'
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
const continuing = computed(() => items.value.find((item) => item.status === 'LISTENING'))
let request = 0
function resetAndLoad() {
  page.value = 0
  void load()
}
function previousPage() {
  page.value--
  void load()
}
function nextPage() {
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
  max-width: 1600px;
  margin: auto;
  padding: 24px;
  padding-bottom: 260px;
  color: var(--text-primary);
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
    padding: 16px 10px 240px;
  }
  .audio-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped>
.audio-favorite-filter { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
</style>
