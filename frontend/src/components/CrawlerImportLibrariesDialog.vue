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
      <el-input
        v-model="keyword"
        clearable
        :disabled="loading || saving || loadFailed"
        placeholder="搜索图书馆"
        aria-label="搜索图书馆"
      />
      <div class="library-selection-heading">
        <strong>选择图书馆</strong>
        <span>已选择 {{ targets.length }} / 20</span>
      </div>
      <el-checkbox-group
        v-model="targets"
        class="library-targets"
        :max="20"
        :disabled="loading || saving || loadFailed"
        aria-label="采集完成后加入的图书馆"
      >
        <el-checkbox
          v-for="library in visibleLibraries"
          :key="library.id"
          :label="library.id"
          :disabled="library.unavailable && !targets.includes(library.id)"
          class="library-target"
          border
        >
          <span class="library-name">{{ library.name }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <p v-if="!loading && !loadFailed && !visibleLibraries.length" class="library-empty">
        {{ keyword.trim() ? '没有匹配的图书馆' : '暂无图书馆，请先创建' }}
      </p>
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
import { computed, ref, watch } from 'vue'
import { crawlerApi } from '@/utils/crawler'
import { personalLibraries } from '@/utils/personalLibraries'
import { message } from '@/utils/message'

const props = defineProps<{ modelValue: boolean; bookId?: number }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [bookId: number]
}>()
const targets = ref<number[]>([])
const libraries = ref<{ id: number; name: string; unavailable?: boolean }[]>([])
const keyword = ref('')
const visibleLibraries = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  return libraries.value.filter(library => !query
    || library.name.toLocaleLowerCase().includes(query)
    || targets.value.includes(library.id))
})
const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
let generation = 0

function close() {
  if (!saving.value) emit('update:modelValue', false)
}

async function loadAllLibraries(request: number) {
  const all: { id: number; name: string }[] = []
  const pageSize = 96
  let pageNumber = 0
  while (true) {
    const page = await personalLibraries.list({ page: pageNumber, size: pageSize })
    if (request !== generation) return []
    all.push(...page.content)
    if (typeof page.totalElements === 'number') {
      if ((page.number + 1) * page.size >= page.totalElements) return all
      if (!page.content.length) throw new Error('图书馆列表加载不完整')
    } else if (page.content.length < pageSize) {
      return all
    }
    pageNumber++
  }
}

async function load() {
  const visible = props.modelValue
  const bookId = props.bookId
  const request = ++generation
  if (!visible || !bookId) return
  targets.value = []
  libraries.value = []
  keyword.value = ''
  loading.value = true
  loadFailed.value = false
  try {
    const ids = await crawlerApi.importLibraries(bookId)
    const [all, selected] = await Promise.all([
      loadAllLibraries(request),
      Promise.all(ids.map(async id => {
        try {
          return await personalLibraries.get(id)
        } catch {
          return { id, name: `图书馆 #${id}（不可用，请移除或重试）`, unavailable: true }
        }
      })),
    ])
    if (request !== generation) return
    targets.value = ids
    libraries.value = [...new Map([...selected, ...all].map(item => [item.id, item])).values()]
  } catch {
    if (request === generation) loadFailed.value = true
  } finally {
    if (request === generation) loading.value = false
  }
}

watch(() => [props.modelValue, props.bookId] as const, load, { immediate: true })

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
.library-selection-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 0 10px;
}

.library-selection-heading span {
  color: var(--text-secondary);
}

.library-targets {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(200px, 100%), 1fr));
  gap: 10px;
  max-height: min(38vh, 320px);
  overflow-y: auto;
  padding: 2px;
}

.library-targets :deep(.library-target) {
  display: flex;
  width: 100%;
  height: auto;
  min-height: 48px;
  margin: 0;
  white-space: normal;
}

.library-targets :deep(.el-checkbox.is-bordered + .el-checkbox.is-bordered) {
  margin-left: 0;
}

.library-targets :deep(.el-checkbox__label) {
  min-width: 0;
  overflow-wrap: anywhere;
}

.library-empty {
  text-align: center;
}

p {
  color: var(--text-secondary);
  line-height: 1.7;
}
</style>
