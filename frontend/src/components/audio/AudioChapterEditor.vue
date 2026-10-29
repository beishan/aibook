<template>
  <el-dialog
    v-model="visible"
    title="编辑有声章节"
    width="min(900px, 95vw)"
    append-to-body
    :close-on-click-modal="false"
  >
    <p>章节可包含多个音轨中的片段。保存会更新内容修订号并结束当前播放会话，原音频不变。</p>
    <div v-for="(chapter, index) in draft" :key="index" class="chapter-edit-item">
      <label
        >章节名称<input v-model="chapter.title" class="input" maxlength="500" required
      /></label>
      <div class="chapter-edit-actions">
        <button class="btn" :disabled="index === 0" @click="move(index, -1)">上移</button>
        <button class="btn" :disabled="index === draft.length - 1" @click="move(index, 1)">
          下移
        </button>
        <button class="btn" @click="draft.splice(index, 1)">删除章节</button>
      </div>
      <div v-for="(segment, part) in chapter.segments" :key="part" class="chapter-edit-segment">
        <label
          >音轨<select v-model="segment.trackId">
            <option v-for="track in book.tracks" :key="track.id" :value="track.id">
              {{ track.title }}
            </option>
          </select></label
        >
        <label
          >起点（秒）<input
            v-model.number="segment.startSeconds"
            type="number"
            min="0"
            step="0.001"
        /></label>
        <label
          >终点（秒）<input
            v-model.number="segment.endSeconds"
            type="number"
            min="0.001"
            step="0.001"
        /></label>
        <button
          class="btn"
          :disabled="chapter.segments.length === 1"
          @click="chapter.segments.splice(part, 1)"
        >
          删除片段
        </button>
      </div>
      <button class="btn" @click="chapter.segments.push(newSegment())">添加跨音轨片段</button>
    </div>
    <button
      class="btn"
      @click="
        draft.push({
          title: `章节 ${draft.length + 1}`,
          segments: [newSegment()],
        })
      "
    >
      添加章节
    </button>
    <p v-if="error" role="alert">{{ error }}</p>
    <template #footer
      ><button class="btn" @click="visible = false">取消</button
      ><button class="btn btn-primary" :disabled="saving" @click="save">保存章节</button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import api from '@/utils/api'
import { type Audiobook } from '@/utils/audiobook'
import { useAudioPlayerStore } from '@/stores/audioPlayer'
const props = defineProps<{ book: Audiobook }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ saved: [] }>()
const draft = ref<
  Array<{
    title: string
    segments: Array<{
      trackId: number
      startSeconds: number
      endSeconds: number
    }>
  }>
>([])
const saving = ref(false),
  error = ref('')
watch(visible, (open) => {
  if (!open) return
  error.value = ''
  draft.value = props.book.chapters.map((chapter) => ({
    title: chapter.title,
    segments: chapter.segments.map((segment) => ({
      trackId: segment.trackId,
      startSeconds: segment.startMs / 1000,
      endSeconds: segment.endMs / 1000,
    })),
  }))
})
function newSegment() {
  const track = props.book.tracks[0]
  return {
    trackId: track.id,
    startSeconds: 0,
    endSeconds: track.durationMs / 1000,
  }
}
function move(index: number, offset: number) {
  const items = [...draft.value]
  ;[items[index], items[index + offset]] = [items[index + offset], items[index]]
  draft.value = items
}
async function save() {
  const chapters = draft.value.map((chapter) => ({
    title: chapter.title.trim(),
    segments: chapter.segments.map((segment) => ({
      trackId: segment.trackId,
      startMs: Math.round(segment.startSeconds * 1000),
      endMs: Math.round(segment.endSeconds * 1000),
    })),
  }))
  if (
    chapters.some(
      (chapter) =>
        !chapter.title ||
        chapter.segments.some(
          (segment) =>
            !Number.isFinite(segment.startMs) ||
            !Number.isFinite(segment.endMs) ||
            segment.startMs < 0 ||
            segment.startMs >= segment.endMs ||
            segment.endMs >
              (props.book.tracks.find((track) => track.id === segment.trackId)?.durationMs || 0)
        )
    )
  ) {
    error.value = '请填写章节名称，并核对片段起止时间未超出音轨。'
    return
  }
  saving.value = true
  try {
    const player = useAudioPlayerStore()
    if (player.book?.id === props.book.id) await player.close()
    await api.put(`/api/audiobooks/${props.book.id}/chapters`, chapters)
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
<style scoped>
p {
  margin-bottom: 16px;
  color: var(--text-secondary);
  line-height: 1.6;
}
.chapter-edit-item {
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  margin-bottom: 16px;
}
label {
  display: grid;
  gap: 8px;
  min-width: 0;
}
.chapter-edit-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 12px 0;
}
.chapter-edit-segment {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr auto;
  gap: 10px;
  align-items: end;
  margin-bottom: 12px;
}
select,
input {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-surface);
  color: var(--text-primary);
}
@media (max-width: 650px) {
  .chapter-edit-segment {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
