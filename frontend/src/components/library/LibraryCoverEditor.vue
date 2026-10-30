<template>
  <el-dialog
    :model-value="modelValue"
    title="自定义图书馆封面"
    width="min(560px, 94vw)"
    append-to-body
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="value => !saving && emit('update:modelValue', value)"
  >
    <template v-if="library">
      <img v-if="preview" class="library-cover-upload-preview" :src="preview" alt="待上传封面预览" />
      <LibraryLandscape v-else :library-id="library.id" :image-key="library.config.coverImage" :variant="library.config.cover" />
      <p class="library-cover-hint">PNG / JPEG，最大 5MB，边长不超过 6000 像素、总像素不超过 1600 万。图片居中裁剪用于列表卡片和馆头，服务端保存为最长边 1600 像素的 JPEG。</p>
      <label class="library-cover-file">
        选择封面图片
        <input :disabled="saving" type="file" accept="image/png,image/jpeg" @change="choose" />
      </label>
      <p v-if="file">{{ file.name }}</p>
    </template>
    <template #footer>
      <el-button v-if="library?.config.coverImage" :disabled="saving" @click="restore">恢复内置封面</el-button>
      <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!file" @click="upload">上传并应用</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import api from '@/utils/api'
import { message } from '@/utils/message'
import type { PersonalLibrary } from '@/utils/personalLibraries'
import LibraryLandscape from './LibraryLandscape.vue'
const props = defineProps<{ modelValue: boolean; library: PersonalLibrary | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const file = ref<File>()
const preview = ref('')
const saving = ref(false)
function clear() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
  file.value = undefined
}
watch(() => [props.modelValue, props.library?.id], (_value, _old, onCleanup) => {
  clear()
  onCleanup(clear)
}, { immediate: true })
function choose(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0]
  input.value = ''
  if (!selected) return
  if (!['image/png', 'image/jpeg'].includes(selected.type) || selected.size > 5 * 1024 * 1024) {
    message.error('请选择不超过 5MB 的 PNG 或 JPEG 图片')
    return
  }
  clear()
  file.value = selected
  preview.value = URL.createObjectURL(selected)
}
async function upload() {
  if (!file.value || !props.library || saving.value) return
  const data = new FormData()
  data.append('file', file.value)
  await apply(() => api.post(`/api/libraries/${props.library!.id}/cover`, data, {
    params: { revision: props.library!.revision },
  }))
}
async function restore() {
  if (!props.library || saving.value) return
  await apply(() => api.delete(`/api/libraries/${props.library!.id}/cover`, {
    params: { revision: props.library!.revision },
  }))
}
async function apply(action: () => Promise<unknown>) {
  saving.value = true
  try {
    await action()
    message.success('图书馆封面已保存')
    emit('update:modelValue', false)
    emit('saved')
  } catch (error: any) {
    message.error(error.response?.status === 409 ? '图书馆已更新，请刷新后重新打开封面编辑' : error.response?.data?.message || '封面保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.library-cover-upload-preview {
  display: block;
  width: 100%;
  height: 200px;
  object-fit: cover;
  border-radius: 12px;
}
.library-cover-hint {
  margin: 16px 0;
  color: var(--text-secondary);
  line-height: 1.6;
}
.library-cover-file {
  display: grid;
  gap: 10px;
}
</style>
