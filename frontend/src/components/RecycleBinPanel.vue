<template>
  <section class="recycle-bin-panel" :class="{ compact }">
    <div v-if="showCleanupSettings" class="cleanup-settings card glass">
      <div class="cleanup-copy">
        <strong>自动清理</strong>
        <span>按书籍进入回收站的时间计算。清理后系统记录不可恢复，但 NAS 原始文件仍会保留。</span>
      </div>
      <div class="cleanup-control">
        <el-select
          v-model="retentionDays"
          :loading="settingsLoading"
          aria-label="回收站自动清理时间"
        >
          <el-option
            v-for="option in retentionOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button
          type="primary"
          :loading="settingsSaving"
          @click="saveCleanupSettings"
        >
          保存设置
        </el-button>
      </div>
    </div>

    <el-alert
      title="这里只管理系统中的书籍记录，恢复、永久移除或清空回收站都不会删除或移动 NAS 上的原始文件。"
      type="info"
      :closable="false"
      show-icon
      class="safety-alert"
    />

    <div class="trash-toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索书名、作者或原始路径"
        class="trash-search"
        @keyup.enter="loadTrash(0)"
        @clear="loadTrash(0)"
      >
        <template #append>
          <el-button :icon="Search" @click="loadTrash(0)" />
        </template>
      </el-input>
      <div class="toolbar-actions">
        <span v-if="selectedIds.length" class="selected-count">已选 {{ selectedIds.length }} 本</span>
        <el-button :disabled="selectedIds.length === 0" :icon="RefreshRight" @click="restoreSelected">
          批量恢复
        </el-button>
        <el-button
          :disabled="selectedIds.length === 0"
          type="danger"
          plain
          :icon="Delete"
          @click="removeSelected"
        >
          永久移除
        </el-button>
        <el-button :disabled="total === 0" type="danger" :icon="DeleteFilled" @click="clearTrash">
          一键清空
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="books"
      row-key="id"
      :max-height="compact ? 380 : 520"
      empty-text="回收站是空的"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column label="书籍文件" min-width="260">
        <template #default="{ row }">
          <div class="book-cell">
            <div class="trash-cover">
              <img
                v-if="row.coverUrl"
                :src="getCoverUrl(row.coverUrl)"
                :class="{ 'is-hidden': isBookCoverHidden(row.id) }"
                alt=""
              />
              <span v-else>{{ row.title?.charAt(0) || '书' }}</span>
            </div>
            <div class="book-summary">
              <strong :title="row.title">{{ row.title }}</strong>
              <span>{{ row.author || '未知作者' }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="格式" width="82">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.format?.toUpperCase() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="删除时间" width="168">
        <template #default="{ row }">{{ formatDateTime(row.deletedAt) }}</template>
      </el-table-column>
      <el-table-column label="原始路径" min-width="220" show-overflow-tooltip prop="filePath" />
      <el-table-column label="操作" width="154" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="restoreBooks([row.id])">恢复</el-button>
          <el-button link type="danger" @click="removeBooks([row.id])">永久移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="total > 0" class="trash-pagination">
      <span>共 {{ total }} 本</span>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="page => loadTrash(page - 1)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Delete, DeleteFilled, RefreshRight, Search } from '@element-plus/icons-vue'
import { useBookStore, type Book } from '@/stores/book'
import { getCoverUrl } from '@/utils/cover'
import { isBookCoverHidden } from '@/utils/imagePrivacy'
import { confirm, message } from '@/utils/message'
import { formatChinaDateTime } from '@/utils/dateTime'
import api from '@/utils/api'

const props = withDefaults(defineProps<{
  compact?: boolean
  showCleanupSettings?: boolean
}>(), {
  compact: false,
  showCleanupSettings: false,
})
const emit = defineEmits<{ changed: [] }>()

const bookStore = useBookStore()
const books = ref<Book[]>([])
const loading = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = props.compact ? 10 : 20
const total = ref(0)
const selectedIds = ref<number[]>([])
const retentionDays = ref(0)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const retentionOptions = [
  { value: 0, label: '不自动清理' },
  { value: 7, label: '保留 7 天' },
  { value: 15, label: '保留 15 天' },
  { value: 30, label: '保留 30 天' },
  { value: 60, label: '保留 60 天' },
  { value: 90, label: '保留 90 天' },
]

const loadTrash = async (page = currentPage.value - 1) => {
  loading.value = true
  try {
    const data = await bookStore.fetchTrash(page, pageSize, keyword.value.trim())
    books.value = data.content
    total.value = data.totalElements
    currentPage.value = data.number + 1
    selectedIds.value = []
  } catch {
    message.error('加载回收站失败')
  } finally {
    loading.value = false
  }
}

const loadCleanupSettings = async () => {
  settingsLoading.value = true
  try {
    const response = await api.get('/api/books/trash/settings')
    retentionDays.value = Number(response.data.retentionDays || 0)
  } catch {
    message.error('加载自动清理设置失败')
  } finally {
    settingsLoading.value = false
  }
}

const saveCleanupSettings = async () => {
  settingsSaving.value = true
  try {
    const response = await api.put('/api/books/trash/settings', {
      retentionDays: retentionDays.value,
    })
    retentionDays.value = Number(response.data.retentionDays || 0)
    message.success(retentionDays.value > 0
      ? `回收站将自动清理超过 ${retentionDays.value} 天的记录`
      : '回收站自动清理已关闭')
  } catch {
    message.error('保存自动清理设置失败')
  } finally {
    settingsSaving.value = false
  }
}

const handleSelectionChange = (selection: Book[]) => {
  selectedIds.value = selection.map(book => book.id)
}

const restoreBooks = async (bookIds: number[]) => {
  try {
    await bookStore.restoreTrashBooks(bookIds)
    message.success(`已恢复 ${bookIds.length} 本书`)
    emit('changed')
    await reloadAfterRemoval(bookIds.length)
  } catch {
    message.error('恢复失败')
  }
}

const restoreSelected = () => restoreBooks(selectedIds.value)

const removeBooks = async (bookIds: number[]) => {
  const accepted = await confirm(
    `确定从系统中永久移除选中的 ${bookIds.length} 本书吗？\n\nNAS 上的原始文件会保留，并且不会在后续扫描中被重新导入。`,
    '永久移除',
  )
  if (!accepted) return
  try {
    await bookStore.permanentlyRemoveTrashBooks(bookIds)
    message.success(`已永久移除 ${bookIds.length} 条书籍记录，原始文件仍保留`)
    emit('changed')
    await reloadAfterRemoval(bookIds.length)
  } catch {
    message.error('永久移除失败')
  }
}

const removeSelected = () => removeBooks(selectedIds.value)

const clearTrash = async () => {
  const accepted = await confirm(
    `确定清空回收站中的 ${total.value} 本书吗？\n\nNAS 上的所有原始文件都会保留，并且不会在后续扫描中被重新导入。`,
    '清空回收站',
  )
  if (!accepted) return
  try {
    await bookStore.emptyTrash()
    message.success('回收站已清空，NAS 原始文件未作改动')
    emit('changed')
    await loadTrash(0)
  } catch {
    message.error('清空回收站失败')
  }
}

const reloadAfterRemoval = async (removedCount: number) => {
  const nextTotal = Math.max(0, total.value - removedCount)
  const lastPage = Math.max(1, Math.ceil(nextTotal / pageSize))
  await loadTrash(Math.min(currentPage.value, lastPage) - 1)
}

const formatDateTime = (value?: string) => value
  ? formatChinaDateTime(value, {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  })
  : '-'

onMounted(() => {
  void loadTrash(0)
  if (props.showCleanupSettings) void loadCleanupSettings()
})
</script>

<style scoped>
.recycle-bin-panel { min-width: 0; }
.cleanup-settings { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 18px; margin-bottom: 16px; }
.cleanup-copy { display: grid; gap: 5px; }
.cleanup-copy strong { color: var(--text-primary); font-size: 15px; }
.cleanup-copy span { color: var(--text-tertiary); font-size: 12px; line-height: 1.55; }
.cleanup-control { display: flex; align-items: center; flex: 0 0 auto; gap: 9px; }
.cleanup-control :deep(.el-select) { width: 160px; }
.safety-alert { margin-bottom: 18px; }
.trash-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.trash-search { width: min(330px, 100%); }
.toolbar-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.selected-count { color: var(--el-text-color-secondary); font-size: 13px; }
.book-cell { display: flex; align-items: center; min-width: 0; gap: 12px; }
.trash-cover { display: grid; width: 38px; height: 52px; flex: 0 0 auto; place-items: center; overflow: hidden; border-radius: 5px; background: var(--el-fill-color); color: var(--el-text-color-secondary); }
.trash-cover img { width: 100%; height: 100%; object-fit: cover; transition: filter .2s ease, transform .2s ease; }
.trash-cover img.is-hidden { filter: blur(16px); transform: scale(1.2); }
.book-summary { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.book-summary strong, .book-summary span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.book-summary span { color: var(--el-text-color-secondary); font-size: 13px; }
.trash-pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 18px; color: var(--el-text-color-secondary); font-size: 13px; }
.compact .safety-alert { margin-bottom: 12px; }
.compact .trash-toolbar { margin-bottom: 12px; }
@media (max-width: 760px) {
  .cleanup-settings, .trash-toolbar { align-items: stretch; flex-direction: column; }
  .cleanup-control, .trash-search { width: 100%; }
  .cleanup-control :deep(.el-select) { flex: 1; width: auto; }
  .toolbar-actions { justify-content: flex-start; }
}
@media (prefers-reduced-motion: reduce) { .trash-cover img { transition: none; } }
</style>
