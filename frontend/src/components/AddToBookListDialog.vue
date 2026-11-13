<template>
  <el-dialog
    :model-value="visible"
    :title="book ? `加入书单 · ${displayBookName(book.title)}` : `加入书单 · ${targetBookIds.length} 本书`"
    width="min(560px, 92vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    @open="loadBookLists"
    @close="closeDialog"
  >
    <div v-loading="loading" class="book-list-selector">
      <el-empty v-if="loadFailed" description="加载书单失败">
        <el-button @click="loadBookLists">重试</el-button>
      </el-empty>
      <el-empty v-else-if="!loading && bookLists.length === 0" description="暂无书单">
        <el-button type="primary" @click="goToCreateList">创建书单</el-button>
      </el-empty>

      <el-checkbox-group v-else v-model="selectedListIds" class="book-list-options">
        <label
          v-for="list in bookLists"
          :key="list.id"
          class="book-list-option"
          :class="{ selected: selectedListIds.includes(list.id) }"
        >
          <el-checkbox :label="list.id" :disabled="submitting || existingListIds.has(list.id)">
            <div class="list-summary">
              <strong>{{ list.name }}</strong>
              <span>{{ list.description || '暂无描述' }}</span>
            </div>
          </el-checkbox>
          <el-tag v-if="existingListIds.has(list.id)" size="small" type="success" effect="plain">
            已加入
          </el-tag>
          <span class="list-book-count">{{ list.books?.length || 0 }} 本</span>
        </label>
      </el-checkbox-group>
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="closeDialog">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="loading || loadFailed || bookLists.length === 0 || newListIds.length === 0"
        @click="submit"
      >
        {{ newListIds.length > 0 ? `加入 ${newListIds.length} 个书单` : '请选择新书单' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/utils/api'
import { message } from '@/utils/message'
import { useBookStore, type Book } from '@/stores/book'

const { displayBookName } = useBookNameDisplay()

interface BookListItem {
  id: number
  name: string
  description?: string
  books?: Array<{ id: number }>
}

const props = defineProps<{
  visible: boolean
  book: Book | null
  bookIds?: number[]
}>()

const emit = defineEmits<{
  close: []
  added: []
}>()

const bookStore = useBookStore()
const targetBookIds = computed(() => props.book ? [props.book.id] : [...new Set(props.bookIds || [])])
const router = useRouter()
const loading = ref(false)
const loadFailed = ref(false)
const submitting = ref(false)
const bookLists = ref<BookListItem[]>([])
const selectedListIds = ref<number[]>([])
const existingListIds = ref(new Set<number>())

const newListIds = computed(() =>
  selectedListIds.value.filter(id => !existingListIds.value.has(id))
)

const loadBookLists = async () => {
  if (targetBookIds.value.length === 0) return
  loading.value = true
  loadFailed.value = false
  bookLists.value = []
  selectedListIds.value = []
  existingListIds.value = new Set()
  try {
    const { data } = await api.get('/api/booklists')
    bookLists.value = data
    existingListIds.value = new Set(
      bookLists.value
        .filter(list => {
          const existing = new Set(list.books?.map(book => book.id) || [])
          return targetBookIds.value.every(id => existing.has(id))
        })
        .map(list => list.id)
    )
    selectedListIds.value = Array.from(existingListIds.value)
  } catch {
    loadFailed.value = true
    message.error('加载书单失败')
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (
    submitting.value || loading.value || loadFailed.value ||
    targetBookIds.value.length === 0 || newListIds.value.length === 0
  ) return
  if (!props.book && newListIds.value.length > 20) {
    message.warning('每次最多加入 20 个书单，请减少选择')
    return
  }
  submitting.value = true
  try {
    if (props.book) {
      await Promise.all(
        newListIds.value.map(listId =>
          api.post(`/api/booklists/${listId}/books/${props.book!.id}`)
        )
      )
    } else {
      await bookStore.addMembershipBatch(targetBookIds.value, 'BOOK_LIST', [...newListIds.value])
    }
    message.success(`已加入 ${newListIds.value.length} 个书单`)
    emit('added')
    closeDialog()
  } catch (error: any) {
    message.error(error.response?.data?.message || '加入书单失败')
  } finally {
    submitting.value = false
  }
}

const closeDialog = () => {
  selectedListIds.value = []
  existingListIds.value = new Set()
  emit('close')
}

const goToCreateList = () => {
  closeDialog()
  void router.push('/shelf')
}
</script>

<style scoped>
.book-list-selector {
  min-height: 160px;
}

.book-list-options {
  display: grid;
  gap: 10px;
}

.book-list-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}

.book-list-option:hover,
.book-list-option.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.book-list-option :deep(.el-checkbox) {
  min-width: 0;
  flex: 1;
  height: auto;
  align-items: center;
}

.book-list-option :deep(.el-checkbox__label) {
  min-width: 0;
}

.list-summary {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.list-summary strong,
.list-summary span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-summary span,
.list-book-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.list-book-count {
  flex: 0 0 auto;
}
</style>
