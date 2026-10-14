<template>
  <el-dialog
    :model-value="visible"
    title="批量合并书籍"
    width="min(900px, calc(100vw - 24px))"
    append-to-body
    top="4vh"
    class="book-batch-merge-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!merging"
    :show-close="!merging"
    @open="prepare"
    @close="handleClose"
  >
    <div class="dialog-scroll-area">
      <p>按组分别合并。每组保留一本书，其余书籍的全部版本、进度、书签、批注和书单关联迁入该书；原始文件不会删除。</p>
      <p>默认建议保留章节较多的书籍；请核对并调整每组目标，章节数和大小不能保证内容完整。</p>
      <p>批量执行期间请保持浏览器开启。关闭浏览器会中断尚未开始的组，已提交的组仍可能完成；重新扫描可核对剩余书籍。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div class="batch-summary" role="status">
        {{ plans.length }} 组 · {{ sourceCount }} 本源书
        <span v-if="finished || merging"> · 已处理 {{ processed }} 组 · 成功 {{ successCount }} 组 · 失败 {{ failureCount }} 组<template v-if="finished"> · 未执行 {{ plans.length - processed }} 组</template></span>
      </div>
      <p v-if="stopRequested && merging">正在停止：当前组完成后不再执行后续分组。</p>
      <el-progress v-if="merging || finished" :percentage="progress" :status="finished && failureCount ? 'warning' : undefined" />

      <div class="batch-plans">
        <section v-for="plan in visiblePlans" :key="plan.groupId" class="batch-plan">
          <header>
            <strong>{{ plan.title }}</strong>
            <el-tag v-if="results[plan.groupId]" :type="results[plan.groupId].status === 'completed' ? 'success' : 'danger'">
              {{ results[plan.groupId].status === 'completed' ? '已合并' : '失败' }}
            </el-tag>
          </header>
          <label>
            保留书籍
            <el-select v-model="plan.targetBookId" :disabled="merging || finished" aria-label="选择本组保留的书籍">
              <el-option v-for="book in plan.books" :key="book.id" :value="book.id" :label="`${book.title} · ${chapterCountLabel(book.chapterCount)} · #${book.id}`" />
            </el-select>
          </label>
          <div class="batch-source-list">
            <p v-for="book in plan.books" :key="book.id">
              <strong>{{ book.id === plan.targetBookId ? '保留' : '迁入' }} · {{ book.title }}</strong>
              <span>{{ sourceLabel(book) }} · {{ chapterCountLabel(book.chapterCount) }} · {{ sizeLabel(book.fileSize) }}</span>
            </p>
          </div>
          <p v-if="results[plan.groupId]?.status === 'failed'" class="batch-error">{{ results[plan.groupId].message }}</p>
        </section>
      </div>

      <div v-if="plans.length > pageSize" class="batch-pagination">
        <el-button :disabled="page === 0" @click="page--">上一页</el-button>
        <span>{{ page + 1 }} / {{ Math.ceil(plans.length / pageSize) }}</span>
        <el-button :disabled="(page + 1) * pageSize >= plans.length" @click="page++">下一页</el-button>
      </div>
    </div>
    <template #footer>
      <el-button v-if="merging" :disabled="stopRequested" @click="stopRequested = true">停止后续合并</el-button>
      <el-button :disabled="merging" @click="handleClose">{{ finished ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!finished" type="primary" :loading="merging" :disabled="!plans.length || !!error" @click="submit">
        确认批量合并 {{ plans.length }} 组
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import api from '@/utils/api'
import { confirm } from '@/utils/message'
import {
  chapterCountLabel, executeBatchMerge, sizeLabel, sourceLabel, validateBatchPlans,
  type BatchMergePlan, type BatchMergeResult,
} from '@/utils/bookComparison'

const props = defineProps<{ visible: boolean; groups: BatchMergePlan[] }>()
const emit = defineEmits<{ close: []; complete: [results: BatchMergeResult[]] }>()
const plans = ref<BatchMergePlan[]>([])
const results = ref<Record<string, BatchMergeResult>>({})
const merging = ref(false)
const finished = ref(false)
const stopRequested = ref(false)
const error = ref('')
const processed = ref(0)
const page = ref(0)
const pageSize = 20
const visiblePlans = computed(() => plans.value.slice(page.value * pageSize, (page.value + 1) * pageSize))
const sourceCount = computed(() => plans.value.reduce((count, plan) => count + plan.books.length - 1, 0))
const successCount = computed(() => Object.values(results.value).filter(result => result.status === 'completed').length)
const failureCount = computed(() => processed.value - successCount.value)
const progress = computed(() => plans.value.length ? Math.round(processed.value / plans.value.length * 100) : 0)

const prepare = () => {
  plans.value = props.groups.map(plan => ({ ...plan, books: [...plan.books] }))
  results.value = {}
  error.value = ''
  processed.value = 0
  page.value = 0
  finished.value = false
  stopRequested.value = false
  try {
    validateBatchPlans(plans.value)
  } catch (cause: any) {
    error.value = cause.message
  }
}

const submit = async () => {
  if (merging.value) return
  const approved = await confirm(
    `确定按上述目标分别合并 ${plans.value.length} 组、${sourceCount.value} 本源书吗？\n\n每组只保留目标书籍，其余书籍的全部版本和关联数据迁入目标书籍。失败组会保留并显示原因，已成功的组不会回滚。`,
    '确认批量合并',
  )
  if (!approved) return
  merging.value = true
  try {
    const completed = await executeBatchMerge(
      plans.value,
      async id => (await api.get<{ id: number }[]>(`/api/books/${id}/versions`, {
        headers: { 'X-Suppress-Error-Toast': 'true' },
      })).data,
      (id, sources) => api.post(`/api/books/${id}/versions/import`, {
        sourceHandling: 'MERGE', sources,
      }, { headers: { 'X-Suppress-Error-Toast': 'true' } }),
      (result, count) => {
        results.value[result.groupId] = result
        processed.value = count
      },
      () => stopRequested.value,
    )
    finished.value = true
    emit('complete', completed)
  } catch (cause: any) {
    error.value = cause.message || '批量合并失败'
  } finally {
    merging.value = false
  }
}

const handleClose = () => {
  if (!merging.value) emit('close')
}

onUnmounted(() => { stopRequested.value = true })
</script>

<style scoped>
.dialog-scroll-area {
  max-height: calc(100dvh - 200px);
  overflow-y: auto;
  padding-right: 2px;
}

.batch-summary {
  margin: 16px 0;
  font-weight: 600;
  line-height: 1.7;
}

.batch-plans {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.batch-plan {
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  min-width: 0;
}

.batch-plan header,
.batch-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.batch-plan label {
  display: grid;
  gap: 8px;
  margin: 12px 0;
}

.batch-source-list {
  max-height: 240px;
  overflow-y: auto;
}

.batch-source-list p {
  display: grid;
  gap: 4px;
  overflow-wrap: anywhere;
}

.batch-source-list span {
  color: var(--text-secondary);
  font-size: 12px;
}

.batch-error {
  color: var(--danger);
  overflow-wrap: anywhere;
}

.batch-pagination {
  justify-content: center;
  margin-top: 16px;
}
</style>
