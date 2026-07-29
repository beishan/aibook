<template>
  <div class="tag-management-view">
    <div class="page-header">
      <div>
        <h1 class="page-title">书籍标签</h1>
        <p class="page-subtitle">使用标签灵活标记题材、来源、用途或阅读偏好</p>
      </div>
      <button class="btn btn-primary" @click="openCreate">新增标签</button>
    </div>

    <div class="summary-grid">
      <div class="summary-card glass">
        <span class="summary-value">{{ tagStore.tags.length }}</span>
        <span class="summary-label">标签总数</span>
      </div>
      <div class="summary-card glass">
        <span class="summary-value">{{ usedTagCount }}</span>
        <span class="summary-label">使用中的标签</span>
      </div>
      <div class="summary-card glass">
        <span class="summary-value">{{ taggedBookCount }}</span>
        <span class="summary-label">标签使用次数</span>
      </div>
    </div>

    <div class="tag-card glass">
      <div class="tag-card-header">
        <div>
          <h2>标签列表</h2>
          <p>删除标签只会解除书籍关联，不会删除书籍或原始文件。</p>
        </div>
      </div>

      <div v-if="tagStore.loading" class="loading">
        <div class="loading-spinner"></div>
        <p>正在加载标签...</p>
      </div>

      <div v-else-if="tagStore.tags.length === 0" class="empty">
        <div class="empty-icon">🏷️</div>
        <p>暂无标签，可创建“待读”“精品”“系列”等自定义标签</p>
        <button class="btn btn-primary" @click="openCreate">创建第一个标签</button>
      </div>

      <div v-else class="tag-list">
        <div v-for="tag in tagStore.tags" :key="tag.id" class="tag-row">
          <div class="tag-main">
            <span
              class="tag-preview"
              :style="tagStyle(tag.color)"
            >
              {{ tag.name }}
            </span>
          </div>
          <span class="tag-count">{{ tag.bookCount || 0 }} 本</span>
          <div class="tag-actions">
            <button class="btn btn-text" @click="openEdit(tag)">编辑</button>
            <button class="btn btn-text btn-danger" @click="removeTag(tag)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="dialogVisible" class="dialog-overlay" @click.self="closeDialog">
          <div class="dialog tag-dialog">
            <div class="dialog-header">
              <span>{{ editingId ? '编辑标签' : '新增标签' }}</span>
              <button class="dialog-close" @click="closeDialog">✕</button>
            </div>
            <div class="dialog-body">
              <div class="form-group">
                <label class="form-label">标签名称</label>
                <input
                  v-model.trim="form.name"
                  class="input"
                  maxlength="30"
                  placeholder="例如：精品、待读、系列"
                  @keyup.enter="saveTag"
                />
              </div>
              <div class="form-group">
                <label class="form-label">标签颜色</label>
                <div class="color-field">
                  <input v-model="form.color" type="color" class="color-picker" />
                  <input
                    v-model.trim="form.color"
                    class="input color-value"
                    maxlength="7"
                    placeholder="#64748B"
                  />
                  <span class="tag-preview" :style="tagStyle(form.color)">
                    {{ form.name || '标签预览' }}
                  </span>
                </div>
              </div>
            </div>
            <div class="dialog-footer">
              <button class="btn" @click="closeDialog">取消</button>
              <button class="btn btn-primary" :disabled="saving || !form.name" @click="saveTag">
                {{ saving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { confirm, message } from '@/utils/message'
import { useTagStore, type Tag } from '@/stores/tag'

const tagStore = useTagStore()
const dialogVisible = ref(false)
const editingId = ref<number>()
const saving = ref(false)
const form = reactive({ name: '', color: '#64748B' })

const usedTagCount = computed(() =>
  tagStore.tags.filter(tag => (tag.bookCount || 0) > 0).length
)
const taggedBookCount = computed(() =>
  tagStore.tags.reduce((sum, tag) => sum + (tag.bookCount || 0), 0)
)

const tagStyle = (color?: string) => {
  const safeColor = /^#[0-9a-fA-F]{6}$/.test(color || '') ? color! : '#64748B'
  return {
    color: safeColor,
    borderColor: `${safeColor}88`,
    backgroundColor: `${safeColor}16`,
  }
}

const resetForm = () => {
  editingId.value = undefined
  form.name = ''
  form.color = '#64748B'
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (tag: Tag) => {
  editingId.value = tag.id
  form.name = tag.name
  form.color = tag.color || '#64748B'
  dialogVisible.value = true
}

const closeDialog = () => {
  dialogVisible.value = false
  resetForm()
}

const saveTag = async () => {
  if (!form.name.trim()) return
  if (!/^#[0-9a-fA-F]{6}$/.test(form.color)) {
    message.warning('请输入正确的颜色值，例如 #64748B')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await tagStore.updateTag(editingId.value, form.name, form.color)
    } else {
      await tagStore.createTag(form.name, form.color)
    }
    message.success('标签已保存')
    closeDialog()
  } catch (error: any) {
    message.error(error.response?.data?.message || '标签保存失败，名称可能已存在')
  } finally {
    saving.value = false
  }
}

const removeTag = async (tag: Tag) => {
  const accepted = await confirm(
    `确定删除“${tag.name}”吗？该标签会从 ${tag.bookCount || 0} 本书中移除。`
  )
  if (!accepted) return
  try {
    await tagStore.deleteTag(tag.id)
    message.success('标签已删除')
  } catch {
    message.error('标签删除失败')
  }
}

onMounted(() => tagStore.fetchTags())
</script>

<style scoped>
.tag-management-view {
  width: 100%;
}

.page-header,
.tag-card-header,
.tag-actions,
.color-field {
  display: flex;
  align-items: center;
}

.page-header,
.tag-card-header {
  justify-content: space-between;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 20px;
}

.summary-value {
  font-size: 28px;
  font-weight: 700;
}

.summary-label,
.tag-card-header p {
  color: var(--text-secondary);
  font-size: 13px;
}

.tag-card {
  padding: 20px;
}

.tag-card-header {
  margin-bottom: 14px;
}

.tag-card-header h2,
.tag-card-header p {
  margin: 0;
}

.tag-card-header p {
  margin-top: 5px;
}

.tag-list {
  border-top: 1px solid var(--border-color);
}

.tag-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 90px auto;
  align-items: center;
  min-height: 64px;
  border-bottom: 1px solid var(--border-color);
}

.tag-preview {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.tag-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.tag-actions {
  gap: 8px;
}

.tag-dialog {
  width: min(440px, calc(100vw - 32px));
}

.color-field {
  gap: 10px;
}

.color-picker {
  width: 44px;
  height: 38px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
}

.color-value {
  width: 118px;
}

@media (max-width: 640px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .tag-row {
    grid-template-columns: 1fr auto;
    gap: 8px;
    padding: 12px 0;
  }

  .tag-count {
    grid-column: 1;
  }

  .tag-actions {
    grid-column: 2;
    grid-row: 1 / span 2;
  }
}
</style>
