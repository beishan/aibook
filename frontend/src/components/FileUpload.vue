<template>
  <div class="file-upload">
    <el-upload
      ref="uploadRef"
      class="upload-area"
      drag
      :action="uploadUrl"
      :headers="uploadHeaders"
      :on-success="handleSuccess"
      :on-error="handleError"
      :before-upload="beforeUpload"
      :file-list="fileList"
      multiple
      accept=".txt,.epub,.mobi,.azw3,.pdf,.docx,.doc,.html,.htm,.cbz,.cbr,.md"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        将文件拖到此处，或<em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持 TXT、EPUB、MOBI、AZW3、PDF、DOCX、HTML、CBZ、MD 格式
        </div>
      </template>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue?: any[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any[]): void
  (e: 'success', response: any): void
}>()

const uploadRef = ref()
const fileList = ref<any[]>(props.modelValue || [])

const uploadUrl = '/api/books/upload'
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`,
}))

const beforeUpload = (file: File) => {
  const maxSize = 500 * 1024 * 1024 // 500MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 500MB')
    return false
  }
  return true
}

const handleSuccess = (response: any, file: any) => {
  ElMessage.success(`${file.name} 上传成功`)
  emit('success', response)
}

const handleError = (error: any, file: any) => {
  ElMessage.error(`${file.name} 上传失败`)
  console.error('Upload error:', error)
}
</script>

<style scoped>
.upload-area {
  width: 100%;
}

.el-upload__tip {
  color: #999;
  font-size: 12px;
}
</style>
