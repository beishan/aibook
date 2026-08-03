<template>
  <div class="category-view">
    <div class="page-header">
      <div>
        <h1 class="page-title">书籍分类</h1>
        <p class="page-subtitle">管理分类层级、常见分类和书籍归属</p>
      </div>
      <div class="header-actions">
        <button class="btn" :disabled="categoryStore.loading" @click="restorePresets">
          恢复常见分类
        </button>
        <button class="btn btn-primary" @click="openCreate()">新增分类</button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card glass">
        <span class="summary-value">{{ categoryStore.flatTree.length }}</span>
        <span class="summary-label">分类总数</span>
      </div>
      <div class="summary-card glass">
        <span class="summary-value">{{ rootCount }}</span>
        <span class="summary-label">一级分类</span>
      </div>
      <div class="summary-card glass">
        <span class="summary-value">{{ categorizedBookCount }}</span>
        <span class="summary-label">已分类书籍</span>
      </div>
    </div>

    <div class="category-card glass">
      <div class="category-card-header">
        <div>
          <h2>分类树</h2>
          <p>点击大类可展开或折叠；删除分类只会解除书籍分类，不会删除书籍文件。</p>
        </div>
        <button
          v-if="collapsibleRoots.length"
          class="btn btn-text collapse-all-button"
          type="button"
          @click="toggleAllRoots"
        >
          {{ allRootsCollapsed ? '全部展开' : '全部折叠' }}
        </button>
      </div>

      <div v-if="categoryStore.loading" class="loading">
        <div class="loading-spinner"></div>
        <p>正在加载分类...</p>
      </div>

      <div v-else-if="categoryStore.flatTree.length === 0" class="empty">
        <div class="empty-icon">🗂️</div>
        <p>暂无分类</p>
        <button class="btn btn-primary" @click="restorePresets">初始化常见分类</button>
      </div>

      <div v-else class="category-list">
        <div
          v-for="category in visibleCategories"
          :key="category.id"
          class="category-row"
          :class="{
            disabled: !category.enabled,
            'root-category': category.depth === 0,
            collapsed: isRootCollapsed(category),
          }"
        >
          <div
            class="category-main"
            :class="{ 'category-main-collapsible': isCollapsibleRoot(category) }"
            :style="{ paddingLeft: `${category.depth * 28 + 8}px` }"
            :role="isCollapsibleRoot(category) ? 'button' : undefined"
            :tabindex="isCollapsibleRoot(category) ? 0 : undefined"
            :aria-expanded="isCollapsibleRoot(category) ? !isRootCollapsed(category) : undefined"
            @click="toggleRoot(category)"
            @keydown.enter.prevent="toggleRoot(category)"
            @keydown.space.prevent="toggleRoot(category)"
          >
            <span v-if="category.depth > 0" class="tree-branch">└</span>
            <span
              v-else-if="isCollapsibleRoot(category)"
              class="collapse-chevron"
              aria-hidden="true"
            >
              ›
            </span>
            <span v-else class="collapse-chevron-placeholder" aria-hidden="true"></span>
            <span class="category-icon">{{ category.depth === 0 ? '📚' : '📖' }}</span>
            <div class="category-copy">
              <div class="category-name">
                {{ category.name }}
                <span v-if="category.builtIn" class="tag tag-info">预置</span>
                <span v-if="!category.enabled" class="tag">已停用</span>
                <span
                  v-if="isCollapsibleRoot(category)"
                  class="child-count"
                >
                  {{ descendantCount(category) }} 个子类
                </span>
              </div>
              <div class="category-description">
                {{ category.description || category.path }}
              </div>
            </div>
          </div>
          <div class="category-count">{{ category.bookCount || 0 }} 本</div>
          <div class="category-actions">
            <button class="btn btn-text" @click="openCreate(category.id)">新增子分类</button>
            <button class="btn btn-text" @click="openEdit(category)">编辑</button>
            <button class="btn btn-text" @click="toggleEnabled(category)">
              {{ category.enabled ? '停用' : '启用' }}
            </button>
            <button class="btn btn-text btn-danger" @click="removeCategory(category)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="dialogVisible" class="dialog-overlay" @click.self="closeDialog">
          <div class="dialog category-dialog">
            <div class="dialog-header">
              <span>{{ editingId ? '编辑分类' : '新增分类' }}</span>
              <button class="dialog-close" @click="closeDialog">✕</button>
            </div>
            <div class="dialog-body">
              <div class="form-group">
                <label class="form-label">分类名称</label>
                <input
                  v-model.trim="form.name"
                  class="input"
                  maxlength="50"
                  placeholder="例如：玄幻、修真"
                />
              </div>
              <div class="form-group">
                <label class="form-label">父分类</label>
                <select v-model="form.parentId" class="select-input full-width">
                  <option :value="undefined">作为一级分类</option>
                  <option
                    v-for="category in availableParents"
                    :key="category.id"
                    :value="category.id"
                  >
                    {{ `${'　'.repeat(category.depth)}${category.name}` }}
                  </option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">分类说明</label>
                <textarea
                  v-model.trim="form.description"
                  class="input textarea"
                  maxlength="255"
                  placeholder="可选"
                ></textarea>
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label class="form-label">排序</label>
                  <input v-model.number="form.sortOrder" type="number" min="0" class="input" />
                </div>
                <label class="enabled-field">
                  <input v-model="form.enabled" type="checkbox" />
                  <span>启用分类</span>
                </label>
              </div>
            </div>
            <div class="dialog-footer">
              <button class="btn" @click="closeDialog">取消</button>
              <button class="btn btn-primary" :disabled="saving || !form.name" @click="saveCategory">
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
import {
  useCategoryStore,
  type Category,
  type CategoryPayload,
} from '@/stores/category'

const categoryStore = useCategoryStore()
const COLLAPSED_ROOTS_KEY = 'aibook.category.collapsed-roots'
const dialogVisible = ref(false)
const editingId = ref<number>()
const saving = ref(false)
const collapsedRootIds = ref<Set<number>>(new Set())
const form = reactive<CategoryPayload>({
  name: '',
  description: '',
  parentId: undefined,
  sortOrder: 0,
  enabled: true,
})

const rootCount = computed(() => categoryStore.categoryTree.length)
const categorizedBookCount = computed(() =>
  categoryStore.categoryTree.reduce((sum, category) => sum + (category.bookCount || 0), 0),
)
const availableParents = computed(() =>
  categoryStore.flatTree.filter((category) => category.id !== editingId.value && category.depth < 2),
)
const collapsibleRoots = computed(() =>
  categoryStore.categoryTree.filter((category) => category.children?.length),
)
const allRootsCollapsed = computed(() =>
  collapsibleRoots.value.length > 0
  && collapsibleRoots.value.every((category) => collapsedRootIds.value.has(category.id)),
)
const visibleCategories = computed(() => {
  const result: Array<Category & { depth: number; path: string }> = []
  const visit = (nodes: Category[], depth: number, parentPath: string) => {
    nodes.forEach((node) => {
      const path = parentPath ? `${parentPath} / ${node.name}` : node.name
      result.push({ ...node, depth, path })
      if (depth !== 0 || !collapsedRootIds.value.has(node.id)) {
        visit(node.children || [], depth + 1, path)
      }
    })
  }
  visit(categoryStore.categoryTree, 0, '')
  return result
})

const saveCollapsedRoots = () => {
  localStorage.setItem(COLLAPSED_ROOTS_KEY, JSON.stringify([...collapsedRootIds.value]))
}

const setCollapsedRoots = (ids: Iterable<number>) => {
  collapsedRootIds.value = new Set(ids)
  saveCollapsedRoots()
}

const loadCollapsedRoots = () => {
  try {
    const stored = JSON.parse(localStorage.getItem(COLLAPSED_ROOTS_KEY) || '[]')
    if (Array.isArray(stored)) {
      collapsedRootIds.value = new Set(stored.filter((id): id is number => Number.isInteger(id)))
    }
  } catch {
    collapsedRootIds.value = new Set()
  }
}

const isCollapsibleRoot = (category: Category & { depth?: number }) =>
  category.depth === 0 && Boolean(category.children?.length)

const isRootCollapsed = (category: Category & { depth?: number }) =>
  isCollapsibleRoot(category) && collapsedRootIds.value.has(category.id)

const toggleRoot = (category: Category & { depth?: number }) => {
  if (!isCollapsibleRoot(category)) return
  const next = new Set(collapsedRootIds.value)
  if (next.has(category.id)) next.delete(category.id)
  else next.add(category.id)
  setCollapsedRoots(next)
}

const toggleAllRoots = () => {
  setCollapsedRoots(allRootsCollapsed.value ? [] : collapsibleRoots.value.map(({ id }) => id))
}

const descendantCount = (category: Category): number =>
  (category.children || []).reduce(
    (count, child) => count + 1 + descendantCount(child),
    0,
  )

const rootIdFor = (categoryId: number) => {
  const contains = (category: Category): boolean =>
    category.id === categoryId || (category.children || []).some(contains)
  return categoryStore.categoryTree.find(contains)?.id
}

const expandCategoryRoot = (categoryId?: number) => {
  if (!categoryId) return
  const rootId = rootIdFor(categoryId)
  if (!rootId || !collapsedRootIds.value.has(rootId)) return
  const next = new Set(collapsedRootIds.value)
  next.delete(rootId)
  setCollapsedRoots(next)
}

const resetForm = () => {
  editingId.value = undefined
  Object.assign(form, {
    name: '',
    description: '',
    parentId: undefined,
    sortOrder: 0,
    enabled: true,
  })
}

const openCreate = (parentId?: number) => {
  resetForm()
  form.parentId = parentId
  dialogVisible.value = true
}

const openEdit = (category: Category) => {
  editingId.value = category.id
  Object.assign(form, {
    name: category.name,
    description: category.description || '',
    parentId: category.parentId,
    sortOrder: category.sortOrder,
    enabled: category.enabled,
  })
  dialogVisible.value = true
}

const closeDialog = () => {
  dialogVisible.value = false
  resetForm()
}

const saveCategory = async () => {
  if (!form.name.trim()) return
  const parentId = form.parentId
  saving.value = true
  try {
    if (editingId.value) {
      await categoryStore.updateCategory(editingId.value, { ...form })
    } else {
      await categoryStore.createCategory({ ...form })
    }
    expandCategoryRoot(parentId)
    message.success('分类已保存')
    closeDialog()
  } catch {
    message.error('保存失败，请检查分类名称和层级')
  } finally {
    saving.value = false
  }
}

const toggleEnabled = async (category: Category) => {
  try {
    await categoryStore.updateCategory(category.id, {
      name: category.name,
      description: category.description,
      parentId: category.parentId,
      sortOrder: category.sortOrder,
      enabled: !category.enabled,
    })
    message.success(category.enabled ? '分类已停用' : '分类已启用')
  } catch {
    message.error('操作失败')
  }
}

const removeCategory = async (category: Category) => {
  const accepted = await confirm(
    category.children?.length
      ? '该分类还有子分类，请先移动或删除子分类。'
      : `确定删除“${category.name}”吗？其中的书籍将变为未分类，原文件不会删除。`,
  )
  if (!accepted || category.children?.length) return
  try {
    await categoryStore.deleteCategory(category.id)
    message.success('分类已删除')
  } catch {
    message.error('删除失败，请先处理子分类')
  }
}

const restorePresets = async () => {
  try {
    await categoryStore.initializePresets()
    message.success('常见分类已补齐')
  } catch {
    message.error('初始化分类失败')
  }
}

onMounted(() => {
  loadCollapsedRoots()
  void categoryStore.refresh()
})
</script>

<style scoped>
.category-view {
  width: 100%;
}

.header-actions,
.category-actions,
.form-row {
  display: flex;
  align-items: center;
  gap: 10px;
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
.category-description,
.category-card-header p {
  color: var(--text-secondary);
  font-size: 13px;
}

.category-card {
  padding: 20px;
}

.category-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.category-card-header h2,
.category-card-header p {
  margin: 0;
}

.category-card-header p {
  margin-top: 5px;
}

.category-list {
  border-top: 1px solid var(--border-color);
}

.category-row {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 90px auto;
  align-items: center;
  min-height: 68px;
  border-bottom: 1px solid var(--border-color);
  transition: background-color 160ms ease, opacity 160ms ease;
}

.category-row.root-category {
  background: color-mix(in srgb, var(--surface-card) 55%, transparent);
}

.category-row.root-category:hover {
  background: var(--surface-hover);
}

.category-row.root-category.collapsed {
  border-bottom-color: color-mix(in srgb, var(--primary) 25%, var(--border-color));
}

.category-row.disabled {
  opacity: 0.6;
}

.category-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 68px;
  border-radius: var(--radius-sm);
}

.category-main-collapsible {
  cursor: pointer;
}

.category-main-collapsible:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -3px;
}

.category-copy {
  min-width: 0;
}

.category-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.tree-branch {
  color: var(--text-secondary);
}

.collapse-chevron,
.collapse-chevron-placeholder {
  width: 16px;
  flex: 0 0 16px;
}

.collapse-chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  font-size: 24px;
  font-weight: 700;
  transform: rotate(90deg);
  transition: transform 180ms ease;
}

.category-row.collapsed .collapse-chevron {
  transform: rotate(0deg);
}

.child-count {
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 500;
}

.collapse-all-button {
  flex: 0 0 auto;
}

.category-count {
  color: var(--text-secondary);
  text-align: right;
}

.category-dialog {
  max-width: 520px;
}

.full-width,
.textarea {
  width: 100%;
}

.textarea {
  min-height: 88px;
  resize: vertical;
}

.form-row > .form-group {
  flex: 1;
}

.enabled-field {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 22px;
}

@media (max-width: 800px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .category-row {
    grid-template-columns: 1fr auto;
    gap: 8px;
    padding: 12px 0;
  }

  .category-card-header {
    align-items: flex-start;
  }

  .category-count {
    grid-column: 2;
    grid-row: 1;
  }

  .category-actions {
    grid-column: 1 / -1;
    flex-wrap: wrap;
    padding-left: 8px;
  }


  .child-count {
    display: none;
  }
}
</style>
