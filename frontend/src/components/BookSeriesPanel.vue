<template>
  <section class="series-panel" aria-label="系列与阅读顺序">
    <div class="series-heading">
      <div>
        <span class="eyebrow">系列与阅读顺序</span>
        <router-link v-if="book.seriesName" :to="{ path: '/series', query: { name: book.seriesName } }">
          {{ book.seriesName }} <span>· {{ volumeLabel(book.seriesIndex) }} →</span>
        </router-link>
        <p v-else>将这本书加入系列，按卷序接着读。</p>
      </div>
      <button class="btn btn-sm" type="button" @click="editing = true">{{ book.seriesName ? '编辑系列' : '设置系列' }}</button>
    </div>
    <p v-if="loading" role="status">正在查找同系列书籍…</p>
    <p v-else-if="error" role="alert">{{ error }} <button class="btn btn-sm" @click="load">重试</button></p>
    <div v-else-if="nextBook" class="next-volume">
      <div><small>{{ book.readingStatus === 'FINISHED' ? '读完这本，接着读' : '下一本已入库' }}</small>
        <strong>{{ volumeLabel(nextBook.seriesIndex) }} · {{ nextBook.title }}</strong></div>
      <router-link class="btn" :to="`/reader/${nextBook.id}`">阅读下一卷 →</router-link>
    </div>
    <p v-else-if="book.seriesName && !loading" class="series-note">
      {{ book.seriesIndex == null ? '填写卷序后可推荐下一卷。' : '当前书库暂未登记更后面的卷册。' }}
    </p>
    <BookEditDialog :visible="editing" :book="book" @close="editing = false" @saved="emit('updated', $event)" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Book } from '@/stores/book'
import api from '@/utils/api'
import { nextSeriesBook, volumeLabel, type SeriesBook } from '@/utils/series'
import BookEditDialog from '@/components/BookEditDialog.vue'
const props = defineProps<{ book: Book }>()
const emit = defineEmits<{ (event: 'updated', book: Book): void }>()
const editing = ref(false)
const loading = ref(false)
const error = ref('')
const books = ref<SeriesBook[]>([])
let sequence = 0
const nextBook = computed(() => nextSeriesBook(books.value, props.book.id))
async function load() {
  const request = ++sequence
  books.value = []
  error.value = ''
  loading.value = false
  if (!props.book.seriesName) return
  loading.value = true
  try {
    const { data } = await api.get('/api/series/books', { params: { name: props.book.seriesName } })
    if (request === sequence) books.value = data
  } catch {
    if (request === sequence) error.value = '同系列书籍加载失败。'
  } finally {
    if (request === sequence) loading.value = false
  }
}
watch(() => [props.book.id, props.book.seriesName, props.book.seriesIndex], load, { immediate: true })
</script>

<style scoped>
.series-panel { margin: 20px 0; padding: 18px; border: 1px solid var(--border-color); border-left: 3px solid var(--primary); border-radius: 12px; }
.series-heading, .next-volume { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.series-heading > div, .next-volume > div { min-width: 0; }
.eyebrow, .next-volume small { display: block; margin-bottom: 6px; color: var(--text-secondary); font-size: 12px; }
.series-heading a { color: var(--text-primary); font-weight: 600; overflow-wrap: anywhere; }
.series-heading a span { color: var(--primary); }
.series-heading p, .series-note { margin: 0; color: var(--text-secondary); font-size: 13px; }
.series-note { margin-top: 12px; }
.next-volume { margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--border-color); }
.next-volume strong { display: block; font-size: 14px; overflow-wrap: anywhere; }
.btn { flex-shrink: 0; }
@media (max-width: 600px) { .next-volume { align-items: flex-start; flex-direction: column; } }
</style>
