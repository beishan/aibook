<template>
  <el-dialog
    :model-value="visible"
    title="合并书籍"
    width="min(760px, calc(100vw - 32px))"
    append-to-body
    destroy-on-close
    class="book-merge-dialog"
    :close-on-click-modal="!merging"
    :close-on-press-escape="!merging"
    @open="prepareMerge"
    @close="handleClose"
  >
    <div v-if="loading" class="merge-loading" role="status">
      <span class="merge-spinner" aria-hidden="true"></span>
      <p>正在读取所选书籍的版本…</p>
    </div>

    <template v-else>
      <el-alert
        title="请选择合并后保留的书籍；其他书籍的全部版本和关联数据会迁移到目标书籍。"
        type="warning"
        :closable="false"
        show-icon
      />

      <section class="merge-section">
        <div class="merge-heading">
          <div><span>01</span><strong>选择保留的目标书籍</strong></div>
          <small>合并完成后仅保留这本书</small>
        </div>
        <div class="merge-target-list" role="radiogroup" aria-label="合并后保留的书籍">
          <button
            v-for="book in books"
            :key="book.id"
            type="button"
            role="radio"
            :aria-checked="targetBookId === book.id"
            :class="{ selected: targetBookId === book.id }"
            @click="targetBookId = book.id"
          >
            <span class="merge-book-mark">{{ book.title.slice(0, 1) }}</span>
            <span class="merge-book-copy">
              <strong>{{ book.title }}</strong>
              <small>{{ book.author || '未知作者' }} · {{ versionsFor(book.id).length }} 个版本</small>
            </span>
            <span class="merge-radio" aria-hidden="true"></span>
          </button>
        </div>
      </section>

      <section class="merge-section">
        <div class="merge-heading">
          <div><span>02</span><strong>确认迁入版本</strong></div>
          <small>共 {{ sourceBooks.length }} 本源书 · {{ sourceVersionCount }} 个版本</small>
        </div>
        <div class="merge-source-list">
          <article v-for="book in sourceBooks" :key="book.id" class="merge-source-book">
            <header>
              <div><strong>{{ book.title }}</strong><small>{{ book.author || '未知作者' }}</small></div>
              <el-tag size="small" effect="plain">全部迁入</el-tag>
            </header>
            <div class="merge-version-list">
              <div v-for="version in versionsFor(book.id)" :key="version.id" class="merge-version-row">
                <span>{{ formatLabel(version.format) }}</span>
                <div><strong>{{ version.displayName }}</strong><small>{{ versionMeta(version) }}</small></div>
                <em v-if="version.primaryVersion">原始版本</em>
              </div>
            </div>
          </article>
        </div>
      </section>

      <div class="merge-consequence">
        <strong>合并后的处理</strong>
        <p>目标书籍保留；其余 {{ sourceBooks.length }} 本书会从书库中消失。原始文件不会被删除，版本、阅读进度、书签、批注和书单关联会迁移到目标书籍。</p>
      </div>
    </template>

    <template #footer>
      <el-button :disabled="merging" @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="merging"
        :disabled="loading || !canMerge"
        @click="submitMerge"
      >
        确认合并
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Book } from '@/stores/book'
import api from '@/utils/api'
import { confirm, message } from '@/utils/message'

interface BookVersion {
  id: number
  displayName: string
  format: string
  fileSize?: number
  primaryVersion: boolean
  chapterCount?: number
}

const props = defineProps<{
  visible: boolean
  books: Book[]
}>()

const emit = defineEmits<{
  close: []
  complete: [payload: { targetBookId: number; sourceBookCount: number; versionCount: number }]
}>()

const targetBookId = ref<number | null>(null)
const versionsByBook = ref<Map<number, BookVersion[]>>(new Map())
const loading = ref(false)
const merging = ref(false)

const sourceBooks = computed(() => props.books.filter(book => book.id !== targetBookId.value))
const sourceVersionCount = computed(() => sourceBooks.value.reduce(
  (total, book) => total + versionsFor(book.id).length,
  0,
))
const canMerge = computed(() => props.books.length >= 2
  && targetBookId.value !== null
  && sourceBooks.value.length > 0
  && sourceBooks.value.every(book => versionsFor(book.id).length > 0))

const versionsFor = (bookId: number) => versionsByBook.value.get(bookId) || []

const prepareMerge = async () => {
  if (props.books.length < 2) {
    message.warning('请至少选择两本书籍')
    emit('close')
    return
  }
  targetBookId.value = props.books[0]?.id ?? null
  versionsByBook.value = new Map()
  loading.value = true
  try {
    const results = await Promise.all(props.books.map(async book => {
      const { data } = await api.get<BookVersion[]>(`/api/books/${book.id}/versions`)
      return [book.id, data || []] as const
    }))
    versionsByBook.value = new Map(results)
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍版本加载失败')
    emit('close')
  } finally {
    loading.value = false
  }
}

const submitMerge = async () => {
  const target = props.books.find(book => book.id === targetBookId.value)
  if (!target || !canMerge.value) return
  const approved = await confirm(
    `确定将 ${sourceBooks.value.length} 本书完整合并到《${target.title}》吗？\n\n合并后其他书籍会从书库中消失，全部版本及关联阅读数据会迁移到目标书籍。`,
    '确认合并书籍',
  )
  if (!approved) return

  merging.value = true
  try {
    await api.post(`/api/books/${target.id}/versions/import`, {
      sourceHandling: 'MERGE',
      sources: sourceBooks.value.map(book => ({
        bookId: book.id,
        versionIds: versionsFor(book.id).map(version => version.id),
      })),
    })
    const payload = {
      targetBookId: target.id,
      sourceBookCount: sourceBooks.value.length,
      versionCount: sourceVersionCount.value,
    }
    message.success(`已合并 ${payload.sourceBookCount} 本书，共迁入 ${payload.versionCount} 个版本`)
    emit('complete', payload)
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍合并失败')
  } finally {
    merging.value = false
  }
}

const handleClose = () => {
  if (merging.value) return
  emit('close')
}

const formatLabel = (format: string) => format === 'structured' ? '在线' : format.toUpperCase()

const versionMeta = (version: BookVersion) => {
  const parts: string[] = []
  if (version.fileSize) {
    parts.push(version.fileSize < 1024 * 1024
      ? `${Math.max(1, Math.round(version.fileSize / 1024))} KB`
      : `${(version.fileSize / 1024 / 1024).toFixed(1)} MB`)
  }
  if (version.chapterCount != null) parts.push(`${version.chapterCount} 章`)
  return parts.join(' · ') || '版本文件'
}
</script>

<style scoped>
.merge-loading { display: grid; min-height: 260px; place-content: center; place-items: center; gap: 14px; color: var(--text-secondary); }
.merge-spinner { width: 32px; height: 32px; border: 3px solid var(--primary-alpha-20); border-top-color: var(--primary); border-radius: 50%; animation: merge-spin .8s linear infinite; }
.merge-section { margin-top: 22px; }
.merge-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.merge-heading > div { display: flex; align-items: center; gap: 9px; }
.merge-heading > div > span { display: grid; width: 27px; height: 27px; place-items: center; border-radius: 8px; background: var(--primary-alpha-10); color: var(--primary); font-size: 10px; font-weight: 800; }
.merge-heading small { color: var(--text-tertiary); }
.merge-target-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.merge-target-list button { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 11px; padding: 12px; border: 1px solid var(--border-color); border-radius: 14px; background: var(--surface-card); color: var(--text-primary); cursor: pointer; text-align: left; }
.merge-target-list button:hover { border-color: color-mix(in srgb, var(--primary) 45%, var(--border-color)); }
.merge-target-list button.selected { border-color: var(--primary); background: var(--primary-alpha-10); box-shadow: 0 0 0 2px var(--primary-alpha-10); }
.merge-book-mark { display: grid; width: 38px; height: 48px; place-items: center; border-radius: 7px; background: linear-gradient(145deg, var(--primary), var(--primary-light)); color: white; font: 600 18px 'Songti SC', serif; }
.merge-book-copy { display: grid; min-width: 0; gap: 4px; }
.merge-book-copy strong, .merge-book-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.merge-book-copy small { color: var(--text-secondary); font-size: 11px; }
.merge-radio { width: 17px; height: 17px; border: 2px solid var(--border-color); border-radius: 50%; }
.selected .merge-radio { border: 5px solid var(--primary); background: white; }
.merge-source-list { display: grid; max-height: 300px; gap: 10px; overflow: auto; padding-right: 4px; }
.merge-source-book { overflow: hidden; border: 1px solid var(--border-color-light); border-radius: 14px; background: var(--surface-card); }
.merge-source-book > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid var(--border-color-light); background: var(--surface-hover); }
.merge-source-book > header > div { display: grid; min-width: 0; gap: 2px; }
.merge-source-book > header small { color: var(--text-secondary); font-size: 11px; }
.merge-version-list { display: grid; }
.merge-version-row { display: grid; grid-template-columns: 46px minmax(0, 1fr) auto; align-items: center; gap: 11px; padding: 10px 14px; border-bottom: 1px solid var(--border-color-light); }
.merge-version-row:last-child { border-bottom: 0; }
.merge-version-row > span { display: grid; height: 27px; place-items: center; border-radius: 7px; background: var(--primary-alpha-10); color: var(--primary); font-size: 10px; font-weight: 800; }
.merge-version-row > div { display: grid; min-width: 0; gap: 2px; }
.merge-version-row strong, .merge-version-row small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.merge-version-row small { color: var(--text-tertiary); font-size: 10px; }
.merge-version-row em { color: var(--text-tertiary); font-size: 10px; font-style: normal; }
.merge-consequence { margin-top: 18px; padding: 14px 16px; border: 1px solid color-mix(in srgb, var(--warning) 28%, var(--border-color)); border-radius: 14px; background: color-mix(in srgb, var(--warning) 8%, var(--surface-card)); }
.merge-consequence p { margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.65; }
@keyframes merge-spin { to { transform: rotate(360deg); } }
@media (max-width: 640px) { .merge-target-list { grid-template-columns: 1fr; } .merge-heading { align-items: flex-start; flex-direction: column; gap: 5px; } }
@media (prefers-reduced-motion: reduce) { .merge-spinner { animation: none; } }
</style>
