<template>
  <div class="file-upload">
    <div
      class="upload-area"
      :class="{ dragging: isDragging }"
      @dragover.prevent="isDragging = true"
      @dragleave="isDragging = false"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <input
        ref="fileInput"
        type="file"
        multiple
        accept=".txt,.epub,.mobi,.azw3,.pdf,.docx,.doc,.html,.htm,.cbz,.cbr,.md"
        style="display: none"
        @change="handleFileChange"
      />
      <div class="upload-icon">📤</div>
      <div class="upload-text">
        将文件拖到此处，或<em>点击上传</em>
      </div>
      <div class="upload-tip">
        支持 TXT、EPUB、MOBI、AZW3、PDF、DOCX、HTML、CBZ、MD 格式
      </div>
    </div>

    <!-- 上传进度 -->
    <div v-if="uploading" class="upload-progress">
      <div class="progress" style="height: 8px;">
        <div class="progress-bar" :style="{ width: progress + '%' }"></div>
      </div>
      <span class="progress-text">{{ progress }}%</span>
    </div>

    <!-- 文件列表 -->
    <div v-if="fileList.length > 0" class="file-list">
      <div v-for="(file, index) in fileList" :key="index" class="file-item">
        <span class="file-icon">📄</span>
        <span class="file-name">{{ file.name }}</span>
        <span class="file-size">{{ formatSize(file.size) }}</span>
        <button class="btn btn-icon btn-small" @click="removeFile(index)">
          <span>✕</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message } from '@/utils/message'

const props = defineProps<{
  modelValue?: any[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any[]): void
  (e: 'success', response: any): void
}>()

const fileInput = ref<HTMLInputElement>()
const fileList = ref<any[]>(props.modelValue || [])
const uploading = ref(false)
const progress = ref(0)
const isDragging = ref(false)

const uploadUrl = '/api/books/upload'

const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files) {
    processFiles(Array.from(target.files))
  }
  isDragging.value = false
}

const handleDrop = (event: DragEvent) => {
  isDragging.value = false
  if (event.dataTransfer?.files) {
    processFiles(Array.from(event.dataTransfer.files))
  }
}

const processFiles = async (files: File[]) => {
  const maxSize = 500 * 1024 * 1024 // 500MB

  for (const file of files) {
    if (file.size > maxSize) {
      message.error(`${file.name} 文件大小不能超过 500MB`)
      continue
    }

    fileList.value.push(file)
  }

  await uploadFiles()
}

const uploadFiles = async () => {
  if (fileList.value.length === 0) return

  uploading.value = true
  progress.value = 0

  try {
    const token = localStorage.getItem('token')
    const formData = new FormData()

    fileList.value.forEach((file) => {
      formData.append('files', file)
    })

    const xhr = new XMLHttpRequest()

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        progress.value = Math.round((e.loaded / e.total) * 100)
      }
    }

    xhr.onload = () => {
      if (xhr.status === 200) {
        const response = JSON.parse(xhr.responseText)
        message.success('上传成功')
        emit('success', response)
        fileList.value = []
      } else {
        message.error('上传失败')
      }
      uploading.value = false
    }

    xhr.onerror = () => {
      message.error('上传失败')
      uploading.value = false
    }

    xhr.open('POST', uploadUrl)
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.send(formData)
  } catch (error) {
    message.error('上传失败')
    uploading.value = false
  }
}

const removeFile = (index: number) => {
  fileList.value.splice(index, 1)
}

const formatSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
.file-upload {
  width: 100%;
}

.upload-area {
  border: 2px dashed rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  text-align: center;
  cursor: pointer;
  transition: all var(--transition-normal);
  background: var(--bg-secondary);
}

.upload-area:hover,
.upload-area.dragging {
  border-color: var(--primary);
  background: rgba(0, 122, 255, 0.1);
}

.upload-icon {
  font-size: 56px;
  margin-bottom: var(--spacing-md);
}

.upload-text {
  color: var(--text-primary);
  font-size: var(--font-size-base);
}

.upload-text em {
  color: var(--primary);
  font-style: normal;
  font-weight: 500;
}

.upload-tip {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  margin-top: var(--spacing-sm);
}

.upload-progress {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-lg);
}

.progress-text {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  min-width: 40px;
  text-align: right;
}

.file-list {
  margin-top: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.file-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  gap: var(--spacing-sm);
}

.file-icon {
  font-size: 20px;
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
}

.file-size {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

.btn-icon {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: var(--radius-full);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-xs);
}

.btn-icon:hover {
  background: rgba(255, 59, 48, 0.1);
  color: var(--danger);
}
</style>
