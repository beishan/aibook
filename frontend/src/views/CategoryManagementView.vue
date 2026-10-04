<template>
  <div class="category-view">
    <div class="page-header">
      <div>
        <h1 class="page-title">书籍分类</h1>
        <p class="page-subtitle">管理分类层级、常见分类和书籍归属</p>
      </div>
      <div class="header-actions">
        <button class="btn" :disabled="categoryStore.loading || batchBusy" @click="restorePresets">
          恢复常见分类
        </button>
        <button class="btn btn-primary" :disabled="batchBusy" @click="openCreate()">新增分类</button>
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

    <div class="category-card glass" :aria-busy="batchBusy">
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

      <div v-if="categoryStore.flatTree.length" class="category-selection-bar" role="toolbar" aria-label="分类多选管理">
        <el-checkbox :model-value="allPageSelected" :indeterminate="somePageSelected && !allPageSelected"
          :disabled="batchBusy || categoryStore.loading" @change="selectPage(Boolean($event))">
          选择当前页
        </el-checkbox>
        <el-checkbox :model-value="allSelected" :indeterminate="selectedIds.size > 0 && !allSelected"
          :disabled="batchBusy || categoryStore.loading" @change="selectAll(Boolean($event))">
          全选全部分类
        </el-checkbox>
        <span class="selection-count" aria-live="polite">{{ batchBusy ? '正在处理…' : `已选 ${selectedIds.size} 个` }}</span>
        <div class="category-batch-actions">
          <button class="btn btn-text" :disabled="batchBusy || !selectedIds.size" @click="selectedIds=new Set()">清空选择</button>
          <button class="btn" :disabled="batchBusy || !selectedIds.size" @click="runBatch('ENABLE')">批量启用</button>
          <button class="btn" :disabled="batchBusy || !selectedIds.size" @click="runBatch('DISABLE')">批量停用</button>
          <button class="btn btn-danger" :disabled="batchBusy || !selectedIds.size" @click="openBatchDelete">批量删除</button>
        </div>
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

      <div v-else class="category-list" :inert="batchBusy || undefined">
        <div
          v-for="category in pagedCategories"
          :key="category.id"
          class="category-row"
          :class="{
            disabled: !category.enabled,
            'root-category': category.depth === 0,
            collapsed: isRootCollapsed(category),
            selected: selectedIds.has(category.id),
          }"
        >
          <el-checkbox class="category-select" :model-value="selectedIds.has(category.id)"
            :aria-label="`选择分类 ${category.path}`" @change="selectCategory(category.id, Boolean($event))" />
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
                {{ category.depth > 0 ? [category.path, category.description].filter(Boolean).join(' · ') : category.description || category.path }}
              </div>
            </div>
          </div>
          <div class="category-count">{{ category.bookCount || 0 }} 本</div>
          <div class="category-actions">
            <button v-if="category.children?.length" class="btn btn-text" @click="selectBranch(category)">选中含子类</button>
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

    <div v-if="!categoryStore.loading && visibleCategories.length" class="category-pagination">
      <p>按展开后的分类分页，已选项跨页保留。</p>
      <el-pagination :current-page="currentPage" :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="visibleCategories.length" :disabled="batchBusy"
        layout="total, sizes, prev, pager, next, jumper"
        @update:current-page="currentPage=$event" @update:page-size="changePageSize" />
    </div>

    <el-dialog v-model="batchDeleteVisible" title="批量删除分类" width="min(560px, 94vw)" append-to-body
      :close-on-click-modal="!batchBusy" :close-on-press-escape="!batchBusy" :show-close="!batchBusy">
      <p>将删除 {{ selectedCategories.length }} 个分类，关联 {{ selectedBookCount }} 本在库书籍。书籍和文件会保留。</p>
      <div class="selected-category-preview">
        <el-tag v-for="category in selectedCategories" :key="category.id" size="small">{{ category.path }}</el-tag>
      </div>
      <el-form label-position="top" :disabled="batchBusy">
        <el-form-item label="关联书籍转移到">
          <el-select v-model="batchTransferTarget" clearable filterable class="full-width" placeholder="不选择则转为未分类">
            <el-option v-for="category in transferTargets" :key="category.id" :value="category.id" :label="category.path" />
          </el-select>
        </el-form-item>
      </el-form>
      <p>回收站书籍和扫描目录的默认分类也会转移。删除父分类需同时选中其全部子分类，删除后可通过“恢复常见分类”重新添加预置分类。</p>
      <template #footer>
        <el-button :disabled="batchBusy" @click="batchDeleteVisible=false">取消</el-button>
        <el-button type="danger" :loading="batchBusy" @click="runBatch('DELETE')">确认删除 {{ selectedIds.size }} 个分类</el-button>
      </template>
    </el-dialog>

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
                <el-select
                  :model-value="form.parentId ?? ''"
                  class="full-width"
                  filterable
                  @change="form.parentId = $event === '' ? undefined : Number($event)"
                >
                  <el-option label="作为一级分类" value="" />
                  <el-option
                    v-for="category in availableParents"
                    :key="category.id"
                    :value="category.id"
                    :label="`${'　'.repeat(category.depth)}${category.name}`"
                  />
                </el-select>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'
import { confirm, message } from '@/utils/message'
import {
  useCategoryStore,
  type Category,
  type CategoryPayload,
} from '@/stores/category'

const categoryStore = useCategoryStore()
const preferencesStore = usePreferencesStore()
const { categoryPageSize:pageSize } = storeToRefs(preferencesStore)
const currentPage = ref(1)
const selectedIds = ref<Set<number>>(new Set())
const batchBusy = ref(false)
const batchDeleteVisible = ref(false)
const batchTransferTarget = ref<number>()
const selectedCategories = computed(() => categoryStore.flatTree.filter(category => selectedIds.value.has(category.id)))
const selectedBookCount = computed(() => selectedCategories.value.reduce((sum, category) => sum + (category.directBookCount || 0), 0))
const transferTargets = computed(() => categoryStore.flatTree.filter(category => !selectedIds.value.has(category.id)))
const allSelected = computed(() => categoryStore.flatTree.length > 0
  && categoryStore.flatTree.every(category => selectedIds.value.has(category.id)))

watch(() => categoryStore.flatTree.map(category => category.id), ids => {
  const available = new Set(ids)
  selectedIds.value = new Set([...selectedIds.value].filter(id => available.has(id)))
})

function selectCategory(id:number, selected:boolean) {
  const next = new Set(selectedIds.value)
  if (selected) next.add(id)
  else next.delete(id)
  selectedIds.value = next
}

function selectAll(selected:boolean) {
  selectedIds.value = new Set(selected ? categoryStore.flatTree.map(category => category.id) : [])
}

function selectBranch(category:Category) {
  const next = new Set(selectedIds.value)
  const visit = (node:Category) => {
    next.add(node.id)
    node.children?.forEach(visit)
  }
  visit(category)
  selectedIds.value = next
}

function openBatchDelete() {
  const incomplete = selectedCategories.value.find(category =>
    category.children?.some(child => !selectedIds.value.has(child.id)))
  if (incomplete) {
    return message.warning(`“${incomplete.name}”还有未选中的子分类，请使用“选中含子类”一并选中`)
  }
  batchTransferTarget.value = undefined
  batchDeleteVisible.value = true
}

async function runBatch(action:'ENABLE'|'DISABLE'|'DELETE') {
  if (batchBusy.value || categoryStore.loading || !selectedIds.value.size) return
  const ids = [...selectedIds.value]
  if (ids.length > 1000) return message.warning('每次最多处理 1000 个分类，请分批选择')
  batchBusy.value = true
  try {
    await categoryStore.batchCategories(ids, action, action === 'DELETE' ? batchTransferTarget.value : undefined)
    batchDeleteVisible.value = false
    selectedIds.value = new Set()
    message.success(`已${action === 'DELETE' ? '删除' : action === 'ENABLE' ? '启用' : '停用'} ${ids.length} 个分类`)
    try {
      await categoryStore.refresh()
    } catch {
      message.warning('操作已完成，但刷新分类失败，请重新打开分类管理')
    }
  } catch {
    // 请求失败由 API 层显示具体原因，保留选择和删除对话框以便处理后重试。
  } finally {
    batchBusy.value = false
  }
}
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

const pagedCategories = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return visibleCategories.value.slice(start, start + pageSize.value)
})
const allPageSelected = computed(() => pagedCategories.value.length > 0
  && pagedCategories.value.every(category => selectedIds.value.has(category.id)))
const somePageSelected = computed(() => pagedCategories.value.some(category => selectedIds.value.has(category.id)))

function selectPage(selected:boolean) {
  const next = new Set(selectedIds.value)
  pagedCategories.value.forEach(category => {
    if (selected) next.add(category.id)
    else next.delete(category.id)
  })
  selectedIds.value = next
}

function changePageSize(value:number) {
  preferencesStore.setCategoryPageSize(value)
  currentPage.value = 1
}

watch([() => visibleCategories.value.length, pageSize], () => {
  const lastPage = Math.max(1, Math.ceil(visibleCategories.value.length / pageSize.value))
  currentPage.value = Math.min(currentPage.value, lastPage)
})

const saveCollapsedRoots = () => {
  localStorage.setItem(COLLAPSED_ROOTS_KEY, JSON.stringify([...collapsedRootIds.value]))
}

const setCollapsedRoots = (ids: Iterable<number>) => {
  collapsedRootIds.value = new Set(ids)
  saveCollapsedRoots()
}

const loadCollapsedRoots = () => {
  const savedValue = localStorage.getItem(COLLAPSED_ROOTS_KEY)
  if (savedValue === null) return false
  try {
    const stored = JSON.parse(savedValue)
    if (Array.isArray(stored)) {
      collapsedRootIds.value = new Set(stored.filter((id): id is number => Number.isInteger(id)))
      return true
    }
  } catch {
    collapsedRootIds.value = new Set()
  }
  return false
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

onMounted(async () => {
  await preferencesStore.hydrate()
  const hasSavedCollapseState = loadCollapsedRoots()
  await categoryStore.refresh()
  if (!hasSavedCollapseState) {
    setCollapsedRoots(collapsibleRoots.value.map(({ id }) => id))
  }
})
</script>

<style scoped>
.category-view {
  width: 100%;
}

.category-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 18px 0;
  overflow-x: auto;
}

.category-pagination p {
  color: var(--text-secondary);
  font-size: 12px;
}

.page-header {
  margin-bottom: 28px;
}

.page-title {
  margin: 0 0 8px;
  line-height: 1.2;
}

.page-subtitle {
  margin: 0 0 20px;
  line-height: 1.6;
}

.header-actions,
.category-actions,
.form-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-actions {
  gap: 12px;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 24px;
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
  grid-template-columns: 36px minmax(260px, 1fr) 90px auto;
  align-items: center;
  min-height: 68px;
  border-bottom: 1px solid var(--border-color);
  transition: background-color 160ms ease, opacity 160ms ease;
}

.category-selection-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 8px;
  border-top: 1px solid var(--border-color);
  background: var(--surface-card);
}

.selection-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.category-batch-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.category-select {
  justify-self: center;
}

.category-row.selected {
  background: color-mix(in srgb, var(--primary) 8%, transparent);
}

.selected-category-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 180px;
  overflow-y: auto;
  margin: 16px 0;
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

.category-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
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
  .page-header {
    margin-bottom: 22px;
  }

  .page-subtitle {
    margin-bottom: 16px;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .category-row {
    grid-template-columns: 32px minmax(0, 1fr) auto;
    gap: 8px;
    padding: 12px 0;
  }

  .category-card-header {
    align-items: flex-start;
  }

  .category-count {
    grid-column: 3;
    grid-row: 1;
  }

  .category-actions {
    grid-column: 1 / -1;
    flex-wrap: wrap;
    padding-left: 8px;
    justify-content: flex-start;
  }


  .child-count {
    display: none;
  }
}
</style>
