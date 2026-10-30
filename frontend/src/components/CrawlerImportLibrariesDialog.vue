<template>
  <el-dialog
    :model-value="modelValue"
    title="采集书籍加入图书馆"
    width="min(560px, 94vw)"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="close"
  >
    <div v-loading="loading">
      <p>保存目标图书馆后会开启本书自动入库，正文采集成功后自动入库并加入所选图书馆。</p>
      <el-select
        v-model="targets"
        multiple
        filterable
        remote
        :remote-method="searchLibraries"
        :loading="searching"
        :disabled="loading || saving || loadFailed"
        :multiple-limit="20"
        placeholder="搜索并选择图书馆（最多 20 个）"
        aria-label="采集完成后加入的图书馆"
        class="library-targets"
      >
        <el-option
          v-for="library in libraries"
          :key="library.id"
          :label="library.name"
          :value="library.id"
        />
      </el-select>
      <p>已完成采集的书籍保存后立即入库并加入；部分失败或暂停的书籍等待续采成功。入库格式沿用本书自动入库设置。</p>
      <p>清空选择仅取消后续自动加入，不移除已有馆藏，也不关闭自动入库。重复执行不会重复添加馆藏。</p>
      <el-alert
        v-if="loadFailed"
        title="加载失败，请关闭后重试"
        type="error"
        :closable="false"
      />
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="close">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="loading || loadFailed || !bookId"
        @click="save"
      >
        保存设置
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { crawlerApi } from '@/utils/crawler'
import { personalLibraries } from '@/utils/personalLibraries'
import { message } from '@/utils/message'

const props = defineProps<{ modelValue: boolean; bookId?: number }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [bookId: number]
}>()
const targets = ref<number[]>([])
const libraries = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const searching = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
let generation = 0
let searchGeneration = 0

function close() {
  if (!saving.value) emit('update:modelValue', false)
}

async function searchLibraries(keyword = '') {
  const request = ++searchGeneration
  const session = generation
  searching.value = true
  try {
    const page = await personalLibraries.list({ keyword, size: 24 })
    if (request !== searchGeneration || session !== generation) return
    const selected = libraries.value.filter(item => targets.value.includes(item.id))
    libraries.value = [...new Map([...selected, ...page.content].map(item => [item.id, item])).values()]
  } finally {
    if (request === searchGeneration && session === generation) searching.value = false
  }
}

async function load() {
  const visible = props.modelValue
  const bookId = props.bookId
  const request = ++generation
  searchGeneration++
  searching.value = false
  if (!visible || !bookId) return
  targets.value = []
  libraries.value = []
  loading.value = true
  loadFailed.value = false
  try {
    const ids = await crawlerApi.importLibraries(bookId)
    const [page, selected] = await Promise.all([
      personalLibraries.list({ size: 24 }),
      Promise.all(ids.map(async id => {
        try {
          return await personalLibraries.get(id)
        } catch {
          return { id, name: `图书馆 #${id}（不可用，请移除或重试）` }
        }
      })),
    ])
    if (request !== generation) return
    targets.value = ids
    libraries.value = [...new Map([...selected, ...page.content].map(item => [item.id, item])).values()]
  } catch {
    if (request === generation) loadFailed.value = true
  } finally {
    if (request === generation) loading.value = false
  }
}

watch(() => [props.modelValue, props.bookId] as const, load)

async function save() {
  const bookId = props.bookId
  if (!bookId || loading.value || loadFailed.value || saving.value) return
  saving.value = true
  try {
    await crawlerApi.setImportLibraries(bookId, targets.value)
    emit('saved', bookId)
    emit('update:modelValue', false)
    message.success(targets.value.length ? '已保存自动入库及目标图书馆设置' : '已取消后续自动加入图书馆')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.library-targets {
  width: 100%;
}

p {
  color: var(--text-secondary);
  line-height: 1.7;
}
</style>
