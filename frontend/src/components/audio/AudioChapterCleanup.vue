<template>
  <el-dialog v-model="visible" title="批量清理章节名" width="min(760px, 94vw)" append-to-body
    :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <p>按原文删除章节名称中的文字。保存会结束当前播放会话，不修改音轨标题或原始文件。</p>
    <label class="cleanup-input">
      要删除的文字（按原文匹配）
      <input v-model="cleanText" class="input" :disabled="saving" placeholder="例如：广告文字" />
    </label>
    <p v-if="cleanText" role="status">匹配 {{ preview.length }} 个章节</p>
    <p v-if="hasEmpty" class="cleanup-error" role="alert">存在清理后为空的章节名称，请调整删除文字。</p>
    <div v-if="preview.length" class="cleanup-preview">
      <div v-for="item in preview.slice(0, 3)" :key="item.id" class="cleanup-example">
        <span>{{ item.before }}</span>
        <span aria-hidden="true">↓</span>
        <strong>{{ item.after || '清理后为空' }}</strong>
      </div>
      <small v-if="preview.length > 3">仅展示前 3 条，共 {{ preview.length }} 条。</small>
    </div>
    <p v-if="error" class="cleanup-error" role="alert">{{ error }}</p>
    <template #footer>
      <button class="btn" :disabled="saving" @click="visible = false">取消</button>
      <button class="btn btn-primary" :disabled="saving || !preview.length || hasEmpty" @click="save">
        {{ saving ? '处理中…' : '确认清理' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import api from '@/utils/api'
import { type Audiobook } from '@/utils/audiobook'
import { previewChapterCleanup } from '@/utils/audioChapterCleanup'
import { useAudioPlayerStore } from '@/stores/audioPlayer'

const props = defineProps<{ book: Audiobook }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ saved: [] }>()
const cleanText = ref('')
const saving = ref(false)
const error = ref('')
const preview = computed(() =>
  previewChapterCleanup(props.book.chapters.map((chapter) => chapter.title), cleanText.value)
    .map((item) => ({ ...item, id: props.book.chapters[item.index].id })),
)
const hasEmpty = computed(() => preview.value.some((item) => !item.after))

watch(visible, (open) => {
  if (open) {
    cleanText.value = ''
    error.value = ''
  }
})

async function save() {
  if (saving.value || !preview.value.length || hasEmpty.value) return
  saving.value = true
  error.value = ''
  try {
    const player = useAudioPlayerStore()
    if (player.book?.id === props.book.id) await player.close()
    await api.put(`/api/audiobooks/${props.book.id}/chapter-titles`, {
      contentRevision: props.book.contentRevision,
      changes: preview.value.map(({ id, before, after }) => ({ id, before, after })),
    })
    visible.value = false
    emit('saved')
  } catch (cause: any) {
    error.value = cause.response?.data?.message || '清理失败，请关闭窗口并重新加载后预览。'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.cleanup-input {
  display: grid;
  gap: 8px;
}
.cleanup-preview {
  display: grid;
  gap: 8px;
  max-height: 240px;
  overflow: auto;
}
.cleanup-example {
  display: grid;
  gap: 4px;
  padding: 10px;
  border-radius: 10px;
  background: var(--bg-secondary);
  overflow-wrap: anywhere;
}
.cleanup-error {
  color: var(--danger, #c0392b);
}
</style>
