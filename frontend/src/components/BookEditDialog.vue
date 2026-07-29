<template>
  <el-dialog
    v-model="dialogVisible"
    title="编辑书籍"
    width="680px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="resetForm"
  >
    <el-form label-position="top" class="book-edit-form">
      <div class="cover-editor">
        <div class="cover-preview">
          <img v-if="previewUrl" :src="previewUrl" alt="封面预览" />
          <div v-else class="cover-placeholder">{{ form.title.charAt(0) || '书' }}</div>
        </div>
        <div class="cover-upload">
          <el-button @click="coverInput?.click()">选择封面</el-button>
          <input
            ref="coverInput"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            hidden
            @change="handleCoverSelect"
          />
          <p>支持 JPG、PNG、WebP、GIF，最大 10MB</p>
          <p v-if="coverFile" class="selected-file">{{ coverFile.name }}</p>
        </div>
      </div>

      <div class="form-grid">
        <el-form-item label="书籍名称" required>
          <el-input v-model.trim="form.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model.trim="form.author" maxlength="255" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.categoryId" clearable filterable placeholder="未分类">
            <el-option
              v-for="category in categoryStore.flatTree"
              :key="category.id"
              :label="category.path"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="form.tagIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择标签"
          >
            <el-option
              v-for="tag in tagStore.tags"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            >
              <span class="tag-color" :style="{ backgroundColor: tag.color }"></span>
              {{ tag.name }}
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="出版社">
          <el-input v-model.trim="form.publisher" maxlength="255" />
        </el-form-item>
        <el-form-item label="ISBN">
          <el-input v-model.trim="form.isbn" maxlength="32" />
        </el-form-item>
        <el-form-item label="出版日期">
          <el-input v-model.trim="form.publishDate" placeholder="例如：2026-07-29" />
        </el-form-item>
        <el-form-item label="语言">
          <el-input v-model.trim="form.language" placeholder="例如：zh-CN" />
        </el-form-item>
      </div>

      <el-form-item label="书籍简介">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveBook">
        保存修改
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from '@/utils/message'
import { useBookStore, type Book } from '@/stores/book'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import { getCoverUrl } from '@/utils/cover'

const props = defineProps<{
  visible: boolean
  book: Book | null
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saved', book: Book): void
}>()

const bookStore = useBookStore()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()
const saving = ref(false)
const coverInput = ref<HTMLInputElement | null>(null)
const coverFile = ref<File | null>(null)
const localPreviewUrl = ref('')
const form = reactive({
  title: '',
  author: '',
  publisher: '',
  isbn: '',
  publishDate: '',
  language: '',
  description: '',
  categoryId: undefined as number | undefined,
  tagIds: [] as number[],
})

const dialogVisible = computed({
  get: () => props.visible,
  set: value => {
    if (!value) emit('close')
  },
})

const previewUrl = computed(() =>
  localPreviewUrl.value || getCoverUrl(props.book?.coverUrl)
)

const hydrateForm = () => {
  if (!props.book) return
  Object.assign(form, {
    title: props.book.title || '',
    author: props.book.author || '',
    publisher: props.book.publisher || '',
    isbn: props.book.isbn || '',
    publishDate: props.book.publishDate || '',
    language: props.book.language || '',
    description: props.book.description || '',
    categoryId: props.book.categoryId,
    tagIds: (props.book.tags || []).map(tag => tag.id),
  })
  void Promise.all([categoryStore.refresh(), tagStore.fetchTags()])
}

const clearLocalPreview = () => {
  if (localPreviewUrl.value) URL.revokeObjectURL(localPreviewUrl.value)
  localPreviewUrl.value = ''
}

const resetForm = () => {
  clearLocalPreview()
  coverFile.value = null
  if (coverInput.value) coverInput.value.value = ''
}

const handleCoverSelect = (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    message.warning('封面图片不能超过10MB')
    return
  }
  coverFile.value = file
  clearLocalPreview()
  localPreviewUrl.value = URL.createObjectURL(file)
}

const saveBook = async () => {
  if (!props.book || !form.title.trim()) {
    message.warning('书籍名称不能为空')
    return
  }

  saving.value = true
  try {
    let updatedBook = await bookStore.updateBookMetadata(props.book.id, {
      title: form.title.trim(),
      author: form.author.trim(),
      publisher: form.publisher.trim(),
      isbn: form.isbn.trim(),
      publishDate: form.publishDate.trim(),
      language: form.language.trim(),
      description: form.description.trim(),
    })
    updatedBook = await bookStore.updateBookCategory(props.book.id, form.categoryId)
    updatedBook = await bookStore.updateBookTags(props.book.id, form.tagIds)
    if (coverFile.value) {
      updatedBook = await bookStore.uploadBookCover(props.book.id, coverFile.value)
    }
    message.success('书籍信息已保存')
    emit('saved', updatedBook)
    emit('close')
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍信息保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => [props.visible, props.book?.id], ([visible]) => {
  if (visible) hydrateForm()
})
</script>

<style scoped>
.book-edit-form {
  max-height: min(68vh, 680px);
  padding-right: 4px;
  overflow-y: auto;
}

.cover-editor {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 20px;
  padding: 16px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
}

.cover-preview {
  width: 86px;
  height: 116px;
  flex: 0 0 auto;
  overflow: hidden;
  border-radius: 8px;
  background: var(--el-fill-color);
}

.cover-preview img,
.cover-placeholder {
  width: 100%;
  height: 100%;
}

.cover-preview img {
  object-fit: cover;
}

.cover-placeholder {
  display: grid;
  place-items: center;
  color: white;
  font-size: 30px;
  background: var(--primary-gradient);
}

.cover-upload p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.cover-upload .selected-file {
  color: var(--el-color-primary);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid :deep(.el-select) {
  width: 100%;
}

.tag-color {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 50%;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
