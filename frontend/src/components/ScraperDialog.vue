<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="dialog-overlay" @click.self="close">
        <div class="dialog scraper-dialog">
          <div class="dialog-header">
            <span>🔍 元数据刮削</span>
            <button class="dialog-close" @click="close">✕</button>
          </div>
          <div class="dialog-body">
            <!-- 加载中 -->
            <div v-if="loading" class="scrape-loading">
              <div class="loading-spinner"></div>
              <p>正在刮削元数据，请稍候...</p>
            </div>

            <!-- 结果 -->
            <div v-else-if="result" class="scrape-result">
              <div class="result-status" :class="result.success ? 'success' : 'error'">
                <span class="status-icon">{{ result.success ? '✅' : '❌' }}</span>
                <span class="status-text">{{ result.message }}</span>
              </div>

              <!-- 单本书籍结果 -->
              <div v-if="result.book" class="book-result">
                <div class="result-item">
                  <span class="result-label">书名：</span>
                  <span class="result-value">{{ result.book.title }}</span>
                </div>
                <div class="result-item">
                  <span class="result-label">作者：</span>
                  <span class="result-value">{{ result.book.author || '未知' }}</span>
                </div>
                <div v-if="result.book.isbn" class="result-item">
                  <span class="result-label">ISBN：</span>
                  <span class="result-value">{{ result.book.isbn }}</span>
                </div>
                <div v-if="result.book.publisher" class="result-item">
                  <span class="result-label">出版社：</span>
                  <span class="result-value">{{ result.book.publisher }}</span>
                </div>
                <div v-if="result.book.coverUrl" class="result-item">
                  <span class="result-label">封面：</span>
                  <span class="result-value success-text">已获取</span>
                </div>
              </div>

              <!-- 批量结果 -->
              <div v-if="result.results && result.results.length > 0" class="batch-results">
                <h4 class="results-title">刮削详情</h4>
                <div class="results-list">
                  <div
                    v-for="item in result.results"
                    :key="item.bookId"
                    class="result-item-row"
                    :class="item.success ? 'success' : 'error'"
                  >
                    <span class="item-status">{{ item.success ? '✅' : '❌' }}</span>
                    <span class="item-title">{{ item.title }}</span>
                    <span v-if="item.updatedFields && item.updatedFields.length > 0" class="item-fields">
                      更新: {{ item.updatedFields.join(', ') }}
                    </span>
                    <span v-if="item.error" class="item-error">{{ item.error }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 错误 -->
            <div v-else-if="error" class="scrape-error">
              <p>{{ error }}</p>
            </div>
          </div>
          <div class="dialog-footer">
            <button class="btn" @click="close">关闭</button>
            <button v-if="result?.success" class="btn btn-primary" @click="handleRefresh">
              刷新页面
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ScrapeResponse } from '@/utils/scraper'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'refresh'): void
}>()

const loading = ref(false)
const result = ref<ScrapeResponse | null>(null)
const error = ref<string | null>(null)

const startScrape = async (scrapeFn: () => Promise<ScrapeResponse>) => {
  loading.value = true
  result.value = null
  error.value = null

  try {
    result.value = await scrapeFn()
  } catch (e: any) {
    error.value = e.response?.data?.message || '刮削失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const close = () => {
  emit('close')
}

const handleRefresh = () => {
  emit('refresh')
  close()
}

defineExpose({
  startScrape
})
</script>

<style scoped>
.scraper-dialog {
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
}

.scrape-loading {
  text-align: center;
  padding: var(--spacing-xl);
}

.loading-spinner {
  display: inline-block;
  width: 40px;
  height: 40px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-md);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.scrape-loading p {
  color: var(--text-secondary);
}

.scrape-result {
  padding: var(--spacing-md) 0;
}

.result-status {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  margin-bottom: var(--spacing-lg);
}

.result-status.success {
  background: rgba(52, 199, 89, 0.1);
  border: 1px solid rgba(52, 199, 89, 0.3);
}

.result-status.error {
  background: rgba(255, 59, 48, 0.1);
  border: 1px solid rgba(255, 59, 48, 0.3);
}

.status-icon {
  font-size: 20px;
}

.status-text {
  font-weight: 500;
  color: var(--text-primary);
}

.book-result {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
}

.result-item {
  display: flex;
  padding: var(--spacing-sm) 0;
  border-bottom: 1px solid var(--border-light);
}

.result-item:last-child {
  border-bottom: none;
}

.result-label {
  width: 80px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.result-value {
  flex: 1;
  color: var(--text-primary);
}

.success-text {
  color: var(--success);
}

.batch-results {
  margin-top: var(--spacing-lg);
}

.results-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-md);
}

.results-list {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  overflow: hidden;
  max-height: 300px;
  overflow-y: auto;
}

.result-item-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--border-light);
}

.result-item-row:last-child {
  border-bottom: none;
}

.result-item-row.success {
  background: rgba(52, 199, 89, 0.05);
}

.result-item-row.error {
  background: rgba(255, 59, 48, 0.05);
}

.item-status {
  font-size: 16px;
}

.item-title {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-fields {
  font-size: var(--font-size-xs);
  color: var(--success);
}

.item-error {
  font-size: var(--font-size-xs);
  color: var(--danger);
}

.scrape-error {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--danger);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}
</style>
