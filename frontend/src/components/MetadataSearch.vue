<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="dialog-overlay" @click.self="handleClose">
        <div class="dialog">
          <div class="dialog-header">
            <span>🔍 搜索书籍元数据</span>
            <button class="dialog-close" @click="handleClose">✕</button>
          </div>

          <div class="dialog-body">
            <div class="search-form">
              <div class="form-group">
                <label class="form-label">ISBN</label>
                <input
                  v-model="searchForm.isbn"
                  type="text"
                  class="input"
                  placeholder="输入 ISBN 搜索"
                />
              </div>

              <div class="form-group">
                <label class="form-label">书名</label>
                <input
                  v-model="searchForm.title"
                  type="text"
                  class="input"
                  placeholder="输入书名搜索"
                />
              </div>

              <div class="form-group">
                <label class="form-label">作者 <span class="optional">（可选）</span></label>
                <input
                  v-model="searchForm.author"
                  type="text"
                  class="input"
                  placeholder="输入作者名搜索"
                />
              </div>

              <div class="form-actions">
                <button class="btn btn-primary" @click="handleSearch" :disabled="loading">
                  <span v-if="loading" class="loading-spinner-small"></span>
                  <span>{{ loading ? '搜索中...' : '搜索' }}</span>
                </button>
                <button class="btn" @click="handleReset">重置</button>
              </div>
            </div>

            <!-- 搜索结果 -->
            <div v-if="searchResults.length > 0 && selectedCandidate" class="search-result">
              <div class="divider">
                <span class="divider-text">搜索结果 · 请核对后应用</span>
              </div>

              <div class="candidate-list">
                <button
                  v-for="candidate in searchResults"
                  :key="candidate.source"
                  type="button"
                  class="candidate-option"
                  :class="{ selected: selectedCandidate === candidate }"
                  @click="selectedCandidate = candidate"
                >
                  {{ sourceName(candidate.source) }} · {{ candidate.title || '未知书名' }}
                </button>
              </div>

              <div class="result-content">
                <div class="result-info">
                  <div class="result-item">
                    <span class="result-label">书名</span>
                    <span class="result-value">{{ selectedCandidate.title }}</span>
                  </div>
                  <div class="result-item">
                    <span class="result-label">作者</span>
                    <span class="result-value">{{ selectedCandidate.author || '未知' }}</span>
                  </div>
                  <div class="result-item">
                    <span class="result-label">ISBN</span>
                    <span class="result-value">{{ selectedCandidate.isbn || '无' }}</span>
                  </div>
                  <div class="result-item">
                    <span class="result-label">出版社</span>
                    <span class="result-value">{{ selectedCandidate.publisher || '未知' }}</span>
                  </div>
                  <div class="result-item">
                    <span class="result-label">出版日期</span>
                    <span class="result-value">{{ selectedCandidate.publishDate || '未知' }}</span>
                  </div>
                  <div class="result-item">
                    <span class="result-label">页数</span>
                    <span class="result-value">{{ selectedCandidate.pageCount || '未知' }}</span>
                  </div>
                  <div class="result-item full">
                    <span class="result-label">简介</span>
                    <span class="result-value">{{ selectedCandidate.description || '暂无简介' }}</span>
                  </div>
                </div>

                <div v-if="selectedCandidate.coverUrl && shouldLoadBookCover()" class="cover-preview">
                  <img :src="selectedCandidate.coverUrl" alt="封面预览" />
                </div>
              </div>

              <div class="result-actions">
                <button class="btn btn-primary" @click="handleApply">
                  <span>✓</span>
                  <span>应用到书籍</span>
                </button>
                <button class="btn" @click="selectedCandidate = null">
                  <span>🔄</span>
                  <span>重新搜索</span>
                </button>
              </div>
            </div>

            <div v-else-if="searched && !loading && searchResults.length === 0" class="empty">
              <div class="empty-icon">🔍</div>
              <p>未找到相关书籍</p>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, reactive, watch } from 'vue'
import api from '@/utils/api'
import { message } from '@/utils/message'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'

const props = defineProps<{
  modelValue: boolean
  initialTitle?: string
  initialAuthor?: string
  initialIsbn?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'apply', metadata: any): void
}>()

const visible = computed(() => props.modelValue)
const loading = ref(false)
const searched = ref(false)
const searchResults = ref<any[]>([])
const selectedCandidate = ref<any>(null)

const searchForm = reactive({
  isbn: '',
  title: '',
  author: '',
})

watch(() => props.modelValue, visible => {
  if (!visible) return
  searchForm.title = props.initialTitle || ''
  searchForm.author = props.initialAuthor || ''
  searchForm.isbn = props.initialIsbn || ''
  searchResults.value = []
  selectedCandidate.value = null
  searched.value = false
})

const handleSearch = async () => {
  if (!searchForm.isbn && !searchForm.title) {
    message.warning('请输入 ISBN 或书名')
    return
  }

  loading.value = true
  searched.value = true
  searchResults.value = []
  selectedCandidate.value = null

  try {
    let response
    if (searchForm.isbn) {
      response = await api.get(`/api/metadata/candidates/isbn/${encodeURIComponent(searchForm.isbn)}`)
    } else {
      response = await api.get('/api/metadata/candidates/search', {
        params: {
          title: searchForm.title,
          author: searchForm.author || undefined,
        },
      })
    }
    searchResults.value = response.data
    selectedCandidate.value = searchResults.value[0] || null
    if (searchResults.value.length === 0) message.info('未找到相关书籍')
  } catch (error: any) {
    message.error('搜索失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.isbn = ''
  searchForm.title = ''
  searchForm.author = ''
  searchResults.value = []
  selectedCandidate.value = null
  searched.value = false
}

const handleApply = () => {
  if (selectedCandidate.value) {
    emit('apply', selectedCandidate.value)
    handleClose()
  }
}

const sourceName = (source: string) => source === 'douban' ? '豆瓣' : 'Open Library'

const handleClose = () => {
  emit('update:modelValue', false)
  handleReset()
}
</script>

<style scoped>
.search-form {
  margin-bottom: var(--spacing-lg);
}

.form-group {
  margin-bottom: var(--spacing-md);
}

.form-label {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
}

.optional {
  font-weight: 400;
  color: var(--text-tertiary);
}

.form-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.loading-spinner-small {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-right: var(--spacing-sm);
  vertical-align: middle;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.search-result {
  margin-top: var(--spacing-lg);
}

.candidate-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.candidate-option {
  max-width: 100%;
  padding: 8px 12px;
  overflow: hidden;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.candidate-option.selected {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.result-content {
  display: flex;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.result-info {
  flex: 1;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.result-item {
  display: flex;
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-light);
}

.result-item:last-child {
  border-bottom: none;
}

.result-item.full {
  flex-direction: column;
}

.result-label {
  width: 80px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.result-value {
  flex: 1;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.cover-preview {
  flex-shrink: 0;
}

.cover-preview img {
  max-width: 150px;
  max-height: 200px;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: var(--spacing-md);
}

.empty {
  text-align: center;
  color: var(--text-secondary);
  padding: var(--spacing-xl);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}
</style>
