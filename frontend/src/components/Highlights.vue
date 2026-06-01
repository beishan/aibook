<template>
  <div class="highlights">
    <div class="highlights-header">
      <span>高亮与批注</span>
      <el-tag size="small">{{ highlights.length }} 条</el-tag>
    </div>

    <div v-if="highlights.length === 0" class="empty-highlights">
      <el-empty description="暂无高亮和批注" :image-size="60" />
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
            <el-icon><Edit /></el-icon>
            {{ highlight.note }}
          </div>
        </div>
        <div class="highlight-meta">
          <span class="highlight-chapter">{{ highlight.chapter }}</span>
          <span class="highlight-time">{{ formatTime(highlight.createdAt) }}</span>
        </div>
        <div class="highlight-actions">
          <el-button type="primary" link @click="handleGoto(highlight)">
            定位
          </el-button>
          <el-button type="danger" link @click="handleDelete(highlight)">
            删除
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  await ElMessageBox.confirm('确定要删除这个高亮吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })

  try {
    await api.delete(`/api/books/${props.bookId}/highlights/${highlight.id}`)
    highlights.value = highlights.value.filter((h) => h.id !== highlight.id)
    ElMessage.success('删除成功')
  } catch (error) {
    ElMessage.error('删除失败')
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
  padding: 10px;
}

.highlights-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  font-weight: 500;
}

.empty-highlights {
  padding: 20px 0;
}

.highlights-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.highlight-item {
  padding: 12px;
  border-radius: 6px;
  background: #f5f7fa;
  border-left: 4px solid;
}

.highlight-content {
  margin-bottom: 8px;
}

.highlight-text {
  font-size: 14px;
  color: #333;
  font-style: italic;
  line-height: 1.6;
}

.highlight-note {
  margin-top: 8px;
  font-size: 13px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 4px;
}

.highlight-meta {
  display: flex;
  gap: 15px;
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.highlight-actions {
  display: flex;
  gap: 10px;
}
</style>
