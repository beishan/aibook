<template>
  <div class="highlights">
    <div class="highlights-header">
      <span>🖍️ 高亮与批注</span>
      <span class="tag tag-info">{{ highlights.length }} 条</span>
    </div>

    <div v-if="highlights.length === 0" class="empty">
      <div class="empty-icon">🖍️</div>
      <p>暂无高亮和批注</p>
    </div>

    <div v-else class="highlights-list">
      <div
        v-for="highlight in highlights"
        :key="highlight.id"
        class="highlight-item"
        :style="{ borderLeftColor: highlight.color }"
      >
        <div class="highlight-content">
          <div class="highlight-text">"{{ highlight.text }}"</div>
          <div v-if="highlight.note" class="highlight-note">
            <span class="note-icon">✏️</span>
            <span>{{ highlight.note }}</span>
          </div>
        </div>
        <div class="highlight-meta">
          <span class="highlight-chapter">{{ highlight.chapter }}</span>
          <span>·</span>
          <span class="highlight-time">{{ formatTime(highlight.createdAt) }}</span>
        </div>
        <div class="highlight-actions">
          <button class="btn btn-text" @click="handleGoto(highlight)">定位</button>
          <button class="btn btn-text btn-danger" @click="handleDelete(highlight)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, confirm } from '@/utils/message'
import api from '@/utils/api'

const props = defineProps<{
  bookId: number
}>()

const emit = defineEmits<{
  (e: 'goto-highlight', highlight: any): void
}>()

interface Highlight {
  id: number
  text: string
  note?: string
  color: string
  chapter: string
  startOffset: number
  endOffset: number
  createdAt: string
}

const highlights = ref<Highlight[]>([])

const loadHighlights = async () => {
  try {
    const response = await api.get(`/api/books/${props.bookId}/highlights`)
    highlights.value = response.data
  } catch (error) {
    console.error('Failed to load highlights:', error)
  }
}

const handleGoto = (highlight: Highlight) => {
  emit('goto-highlight', highlight)
}

const handleDelete = async (highlight: Highlight) => {
  const result = await confirm('确定要删除这个高亮吗？')
  if (result) {
    try {
      await api.delete(`/api/books/${props.bookId}/highlights/${highlight.id}`)
      highlights.value = highlights.value.filter((h) => h.id !== highlight.id)
      message.success('删除成功')
    } catch (error) {
      message.error('删除失败')
    }
  }
}

const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN')
}

onMounted(loadHighlights)
</script>

<style scoped>
.highlights {
  padding: var(--spacing-md);
}

.highlights-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
  font-weight: 600;
  font-size: var(--font-size-lg);
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

.highlights-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.highlight-item {
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  border-left: 4px solid;
}

.highlight-content {
  margin-bottom: var(--spacing-sm);
}

.highlight-text {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  font-style: italic;
  line-height: 1.6;
}

.highlight-note {
  margin-top: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-xs);
}

.note-icon {
  flex-shrink: 0;
}

.highlight-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-sm);
}

.highlight-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.btn-danger {
  color: var(--danger) !important;
}

.btn-danger:hover {
  background: rgba(255, 59, 48, 0.1) !important;
}
</style>
