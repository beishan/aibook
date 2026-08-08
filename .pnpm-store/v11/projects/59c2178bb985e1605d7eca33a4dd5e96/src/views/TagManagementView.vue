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

      <div v-if="!tagStore.loading && tagStore.tags.length > 0" class="tag-list-tools">
        <el-input
          v-model="searchKeyword"
          clearable
          class="tag-search-input"
          placeholder="输入标签名称进行模糊查询"
          aria-label="查询标签"
        >
          <template #prefix>🔍</template>
        </el-input>
        <span class="query-result-count">
          {{ searchKeyword.trim() ? `找到 ${filteredTags.length} 个标签` : `共 ${filteredTags.length} 个标签` }}
        </span>
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

      <div v-else-if="filteredTags.length === 0" class="empty query-empty">
        <div class="empty-icon">🔍</div>
        <p>未找到名称中包含“{{ searchKeyword.trim() }}”的标签</p>
        <button class="btn" @click="searchKeyword = ''">清除查询</button>
      </div>

      <template v-else>
        <div class="tag-list">
          <div v-for="tag in pagedTags" :key="tag.id" class="tag-row">
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
        <div class="tag-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="pageSizeOptions"
            :total="filteredTags.length"
            layout="total, sizes, prev, pager, next"
          />
        </div>
      </template>
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
                <div class="color-palette" role="listbox" aria-label="常用标签颜色">
                  <button
                    v-for="color in presetColors"
                    :key="color.value"
                    type="button"
                    class="color-swatch"
                    :class="{ selected: isSelectedColor(color.value) }"
                    :style="{ backgroundColor: color.value }"
                    :title="`${color.name} ${color.value}`"
                    role="option"
                    :aria-label="color.name"
                    :aria-selected="isSelectedColor(color.value)"
                    @click="form.color = color.value"
                  >
                    <span v-if="isSelectedColor(color.value)" class="color-check">✓</span>
                  </button>
                </div>
                <div class="custom-color-label">自定义颜色</div>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { confirm, message } from '@/utils/message'
import { useTagStore, type Tag } from '@/stores/tag'

const tagStore = useTagStore()
const dialogVisible = ref(false)
const editingId = ref<number>()
const saving = ref(false)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const pageSizeOptions = [10, 20, 50]
const form = reactive({ name: '', color: '#64748B' })
const presetColors = [
  { name: '石板灰', value: '#64748B' },
  { name: '中性灰', value: '#737373' },
  { name: '红色', value: '#EF4444' },
  { name: '深红', value: '#DC2626' },
  { name: '橙色', value: '#F97316' },
  { name: '琥珀', value: '#F59E0B' },
  { name: '黄色', value: '#EAB308' },
  { name: '青柠', value: '#84CC16' },
  { name: '绿色', value: '#22C55E' },
  { name: '翠绿', value: '#10B981' },
  { name: '青色', value: '#14B8A6' },
  { name: '湖蓝', value: '#06B6D4' },
  { name: '天蓝', value: '#0EA5E9' },
  { name: '蓝色', value: '#3B82F6' },
  { name: '靛蓝', value: '#6366F1' },
  { name: '紫罗兰', value: '#8B5CF6' },
  { name: '紫色', value: '#A855F7' },
  { name: '品红', value: '#D946EF' },
  { name: '粉色', value: '#EC4899' },
  { name: '玫红', value: '#F43F5E' },
  { name: '棕色', value: '#A16207' },
  { name: '咖啡色', value: '#92400E' },
  { name: '深青', value: '#0F766E' },
  { name: '深蓝', value: '#1D4ED8' },
]

const isSelectedColor = (color: string) =>
  form.color.toUpperCase() === color.toUpperCase()

const usedTagCount = computed(() =>
  tagStore.tags.filter(tag => (tag.bookCount || 0) > 0).length
)
const taggedBookCount = computed(() =>
  tagStore.tags.reduce((sum, tag) => sum + (tag.bookCount || 0), 0)
)
const filteredTags = computed(() => {
  const keyword = searchKeyword.value.trim().toLocaleLowerCase()
  if (!keyword) return tagStore.tags
  return tagStore.tags.filter(tag =>
    tag.name.toLocaleLowerCase().includes(keyword)
  )
})
const pagedTags = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredTags.value.slice(start, start + pageSize.value)
})

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

watch(searchKeyword, () => {
  currentPage.value = 1
})

watch(
  [() => filteredTags.value.length, pageSize],
  () => {
    const totalPages = Math.max(1, Math.ceil(filteredTags.value.length / pageSize.value))
    if (currentPage.value > totalPages) currentPage.value = totalPages
  }
)

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

.tag-list-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.tag-search-input {
  width: min(420px, 100%);
}

.query-result-count {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: 13px;
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

.tag-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

.query-empty {
  padding-block: 40px;
}

.tag-dialog {
  width: min(440px, calc(100vw - 32px));
}

.color-field {
  gap: 10px;
}

.color-palette {
  display: grid;
  grid-template-columns: repeat(8, 32px);
  gap: 10px;
  margin-bottom: 14px;
}

.color-swatch {
  position: relative;
  display: grid;
  width: 32px;
  height: 32px;
  padding: 0;
  place-items: center;
  border: 2px solid transparent;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.12);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.color-swatch:hover {
  z-index: 1;
  transform: scale(1.12);
}

.color-swatch.selected {
  border-color: var(--surface-card);
  box-shadow:
    0 0 0 2px var(--primary),
    0 3px 8px rgba(15, 23, 42, 0.2);
}

.color-swatch:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}

.color-check {
  color: white;
  font-size: 15px;
  font-weight: 800;
  line-height: 1;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.65);
}

.custom-color-label {
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 12px;
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

  .color-palette {
    grid-template-columns: repeat(6, 32px);
  }

  .tag-list-tools {
    align-items: stretch;
    flex-direction: column;
  }

  .tag-search-input {
    width: 100%;
  }

  .tag-pagination {
    justify-content: flex-start;
    overflow-x: auto;
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
