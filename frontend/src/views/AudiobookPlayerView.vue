<template>
  <main class="audio-player-page">
    <header>
      <router-link class="btn" :to="`/audiobooks/${route.params.id}`">← 返回书籍详情</router-link
      ><strong>汗牛充栋 · 收听</strong><button class="btn" @click="start">重新打开收听</button>
    </header>
    <p v-if="loading" role="status">正在准备音频…</p>
    <p v-if="failure" class="audio-error" role="alert">{{ failure }}</p>
    <div v-if="player.book?.id === Number(route.params.id)" class="audio-player-layout">
      <section class="audio-player-main glass">
        <img
          v-if="player.book.coverUrl && shouldLoadBookCover(player.book.bookId)"
          :src="getCoverThumbnailUrl(player.book.coverUrl, 'detail')"
          :alt="displayBookName(player.book.title)"
          class="audio-art"
        />
        <div v-else class="audio-art audio-cover-placeholder" aria-hidden="true"><BookCoverPlaceholderText :title="displayBookName(player.book.title)" /></div>
        <h1>{{ displayBookName(player.book.title) }}</h1>
        <p>{{ player.book.author }} · 演播 {{ player.book.narrator || '未填写' }}</p>
        <h2>{{ player.chapter?.title || player.track?.title }}</h2>
        <AudioControls />
        <div class="audio-total">
          <progress :value="player.percent" max="100" aria-label="全书内容进度" /><span
            >全书进度 {{ player.percent }}%</span
          ><span
            >内容位置 {{ audioTime(player.totalPosition) }} /
            {{ audioTime(player.book.durationMs) }}</span
          >
        </div>
        <p class="audio-note">阅读与收听分别记录进度。网页后台播放能力取决于浏览器。</p>
      </section>
      <aside class="audio-player-list glass">
        <AudioSegmented
          v-model="tab"
          label="播放器侧栏"
          :options="[
            { value: 'chapters', label: '章节' },
            { value: 'bookmarks', label: '书签' },
          ]"
        />
        <input
          v-model="search"
          class="input"
          :placeholder="tab === 'chapters' ? '搜索章节' : '搜索书签'"
          :aria-label="tab === 'chapters' ? '搜索章节' : '搜索书签'"
        />
        <div v-if="tab === 'chapters'" class="audio-scroll">
          <button
            v-for="(chapter, index) in chapters"
            :key="chapter.id"
            class="audio-chapter-button"
            :class="{ active: player.chapter?.id === chapter.id }"
            :aria-current="player.chapter?.id === chapter.id ? 'true' : undefined"
            @click="
              player.selectTrack(chapter.segments[0].trackId, chapter.segments[0].startMs, true)
            "
          >
            <span>{{ index + 1 }}</span
            ><strong>{{ chapter.title }}</strong
            ><span v-if="player.chapter?.id === chapter.id">{{ player.playing ? 'Ⅱ' : '▶' }}</span>
          </button>
        </div>
        <div v-else class="audio-scroll">
          <p v-if="!bookmarks.length">暂无匹配书签</p>
          <div v-for="bookmark in bookmarks" :key="bookmark.id" class="audio-bookmark-row">
            <button
              class="audio-chapter-button"
              @click="player.selectTrack(bookmark.trackId, bookmark.positionMs, true)"
            >
              <strong>{{ bookmark.title }}</strong
              ><small>{{ audioTime(bookmark.positionMs) }}</small>
            </button>
            <button class="btn" aria-label="删除书签" @click="removeBookmark(bookmark.id)">
              ✕
            </button>
          </div>
        </div>
      </aside>
    </div>
  </main>
</template>
<script setup lang="ts">
import BookCoverPlaceholderText from '@/components/BookCoverPlaceholderText.vue'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import api from '@/utils/api'
import { useAudioPlayerStore } from '@/stores/audioPlayer'
import { audioApi, audioTime } from '@/utils/audiobook'
import AudioControls from '@/components/audio/AudioControls.vue'
import AudioSegmented from '@/components/audio/AudioSegmented.vue'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import { getCoverThumbnailUrl } from '@/utils/cover'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
const player = useAudioPlayerStore(),
  route = useRoute()
const { displayBookName } = useBookNameDisplay()
const tab = ref('chapters'),
  search = ref(''),
  loading = ref(false),
  failure = ref('')
const chapters = computed(
  () =>
    player.book?.chapters.filter((item) =>
      item.title.toLowerCase().includes(search.value.toLowerCase())
    ) || []
)
const bookmarks = computed(
  () =>
    player.book?.bookmarks.filter((item) =>
      item.title.toLowerCase().includes(search.value.toLowerCase())
    ) || []
)
async function start() {
  loading.value = true
  failure.value = ''
  try {
    await player.open(Number(route.params.id))
  } catch (error: any) {
    if (error.response?.status === 409) {
      try {
        await ElMessageBox.confirm(error.response.data.message, '收听会话冲突', {
          confirmButtonText: '接管并继续',
          cancelButtonText: '取消',
        })
        await player.open(Number(route.params.id), true)
      } catch {
        failure.value = '未接管会话，或接管失败；可重新打开收听。'
      }
    } else failure.value = error.response?.data?.message || '无法打开收听，请重试'
  } finally {
    loading.value = false
  }
}
async function load() {
  if (player.book?.id !== Number(route.params.id)) await start()
  if (route.query.track && player.book?.id === Number(route.params.id)) {
    await player.selectTrack(Number(route.query.track), Number(route.query.position) || 0, true)
  }
}
async function removeBookmark(id: number) {
  await api.delete(`/api/audiobooks/bookmarks/${id}`)
  if (player.book) player.book.bookmarks = (await audioApi.detail(player.book.id)).bookmarks
}
watch(() => route.fullPath, load, { immediate: true })
</script>
<style scoped>
.audio-player-page {
  color: var(--text-primary);
  max-width: 1560px;
  padding: 24px 24px 150px;
  margin: auto;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.audio-player-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 1fr);
  gap: 20px;
}
.audio-player-main {
  padding: 32px;
  border-radius: 24px;
  text-align: center;
  min-width: 0;
}
.audio-art {
  width: 260px;
  height: 260px;
  object-fit: cover;
  border-radius: 20px;
  margin: 0 auto 20px;
}
.audio-cover-placeholder {
  display: grid;
  place-items: center;
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-size: 70px;
}
h1 {
  font-size: 30px;
}
h2 {
  font-size: 20px;
  margin: 24px 0;
}
p {
  color: var(--text-secondary);
  margin: 12px 0;
}
.audio-total {
  margin-top: 28px;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 13px;
}
progress {
  width: 100%;
  height: 8px;
  accent-color: var(--primary);
}
.audio-note {
  font-size: 12px;
}
.audio-error {
  color: var(--danger);
}
.audio-player-list {
  border-radius: 24px;
  padding: 18px;
  min-width: 0;
}
.audio-player-list > .input {
  margin: 16px 0;
  width: 100%;
}
.audio-scroll {
  max-height: 610px;
  overflow: auto;
}
.audio-chapter-button {
  width: 100%;
  display: flex;
  gap: 14px;
  padding: 16px 12px;
  border: 0;
  border-bottom: 1px solid var(--border-color-light);
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  border-radius: 12px;
  cursor: pointer;
  font: inherit;
}
.audio-chapter-button strong {
  flex: 1;
  overflow-wrap: anywhere;
}
.audio-chapter-button.active {
  background: var(--primary-alpha-15);
  color: var(--primary);
}
.audio-chapter-button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.audio-bookmark-row {
  display: flex;
  align-items: center;
}
@media (max-width: 900px) {
  .audio-player-layout {
    grid-template-columns: 1fr;
  }
  .audio-player-page {
    padding: 12px 10px 140px;
  }
  .audio-player-main {
    padding: 20px;
  }
  .audio-art {
    width: 210px;
    height: 210px;
  }
  .audio-scroll {
    max-height: 400px;
  }
}
</style>
