<template>
  <div class="tag-management-view">
    <div class="page-header">
      <div>
        <h1 class="page-title">书籍标签</h1>
        <p class="page-subtitle">使用标签灵活标记题材、来源、用途或阅读偏好</p>
      </div>
      <div class="header-actions">
        <button class="btn" @click="openPending">待加入标签</button>
        <button class="btn" @click="startLibraryScan">书库扫描新增</button>
        <button class="btn" @click="openManage">一键整理</button>
        <button class="btn btn-primary" @click="openCreate">新增标签</button>
      </div>
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
            <div class="tag-source">
              <span class="source-label">来源</span>
              <el-tag size="small" effect="plain">{{ sourceLabel(tag) }}</el-tag>
              <small v-if="tag.sourceType === 'SCRAPER' && tag.sourceDetail">
                {{ tag.sourceDetail }}
              </small>
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

    <el-dialog
      v-model="pendingVisible"
      title="待加入标签"
      width="min(760px, 94vw)"
      :close-on-click-modal="!pendingBusy"
      :close-on-press-escape="!pendingBusy"
      :show-close="!pendingBusy"
      destroy-on-close
    >
      <p>爬虫和刮削发现的新标签先在此保留，加入后可用于书籍标记。不会自动补加到历史书籍；删除后再次爬取仍可能重新出现。</p>
      <el-input v-model="pendingKeyword" placeholder="搜索待加入标签" clearable aria-label="搜索待加入标签" />
      <el-table v-loading="pendingBusy" :data="filteredPendingTags" height="360" row-key="id">
        <el-table-column label="选择" width="70">
          <template #default="{ row }">
            <el-checkbox v-model="pendingIds" :value="row.id" :label="row.id" :disabled="pendingBusy" :aria-label="'选择标签 ' + row.name">
              <span></span>
            </el-checkbox>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="标签" min-width="150" />
        <el-table-column prop="sourceDetail" label="来源" min-width="150" />
        <template #empty>暂无待加入标签</template>
      </el-table>
      <template #footer>
        <el-button :disabled="pendingBusy" @click="pendingVisible = false">关闭</el-button>
        <el-button :disabled="pendingBusy || !pendingIds.length" type="danger" @click="processPending('delete')">删除所选</el-button>
        <el-button :disabled="pendingBusy || !pendingIds.length" type="primary" @click="processPending('approve')">加入所选（{{ pendingIds.length }}）</el-button>
      </template>
    </el-dialog>

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

    <el-dialog
      v-model="scanDialogVisible"
      title="书库扫描新增"
      width="min(920px, 94vw)"
      :close-on-click-modal="!scanLoading"
      :close-on-press-escape="!scanLoading"
      :show-close="!scanLoading"
      destroy-on-close
    >
      <div class="scan-dialog-content">
        <p class="dialog-intro">
          扫描书库中所有书籍的标签，只列出标签库中尚不存在的标签。选择后确认即可添加。
        </p>
        <div v-if="scanLoading || scanError" class="scan-progress-card">
          <div class="scan-progress-heading">
            <strong>{{ scanError ? '扫描未完成' : '正在扫描书库' }}</strong>
            <span>{{ scanScannedBooks }} / {{ scanTotalBooks }} 本</span>
          </div>
          <el-progress :percentage="scanProgress" :status="scanError ? 'exception' : undefined" />
          <p v-if="scanError" class="scan-error">{{ scanError }}</p>
        </div>
        <template v-if="!scanLoading && scanComplete">
          <div v-if="scanCandidates.length" class="scan-transfer-wrap">
            <div class="transfer-heading">
              <strong>发现 {{ scanCandidates.length }} 个未入库标签</strong>
              <span>从左侧勾选并移到右侧，再确认添加</span>
            </div>
            <el-transfer
              v-model="selectedScannedTags"
              :data="scanCandidates"
              :titles="['待选择', '确认添加']"
              :button-texts="['移除', '添加']"
              filterable
              filter-placeholder="搜索标签"
              target-order="push"
            />
          </div>
          <el-empty v-else description="扫描完成，没有发现新的标签" />
        </template>
      </div>
      <template #footer>
        <el-button :disabled="scanLoading" @click="scanDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="scanLoading || selectedScannedTags.length === 0"
          :loading="addingScannedTags"
          @click="confirmAddScannedTags"
        >
          添加所选标签（{{ selectedScannedTags.length }}）
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="manageDialogVisible"
      title="一键整理标签"
      width="min(900px, 94vw)"
      class="tag-manage-dialog"
      :close-on-click-modal="!deletingManageTags"
      :close-on-press-escape="!deletingManageTags"
      :show-close="!deletingManageTags"
    >
      <div class="manage-tools">
        <el-input
          v-model="manageKeyword"
          :disabled="deletingManageTags"
          clearable
          placeholder="搜索标签名称"
          aria-label="搜索待整理标签"
        />
        <el-select
          v-model="manageSourceFilter"
          :disabled="deletingManageTags"
          clearable
          placeholder="全部来源"
          aria-label="筛选待整理标签来源"
        >
          <el-option
            v-for="source in sourceOptions"
            :key="source.value"
            :label="source.label"
            :value="source.value"
          />
        </el-select>
        <span class="manage-count">已选 {{ selectedManageTagIds.length }} 个</span>
      </div>
      <div class="manage-selection">
        <el-checkbox
          :model-value="allManagePageSelected"
          :indeterminate="someManagePageSelected && !allManagePageSelected"
          :disabled="deletingManageTags || !pagedManageTags.length"
          @change="toggleManagePage(Boolean($event))"
        >
          选中本页（{{ pagedManageTags.length }}）
        </el-checkbox>
        <span>翻页保留选择，切换筛选清空选择</span>
      </div>
      <div class="manage-table-wrap">
        <el-table :data="pagedManageTags" height="100%" row-key="id">
          <el-table-column label="选择" width="70" align="center">
            <template #default="{ row }">
              <el-checkbox
                :model-value="selectedManageTagIds.includes(row.id)"
                :disabled="deletingManageTags"
                :aria-label="`选择标签 ${row.name}`"
                @change="toggleManageTag(row.id, Boolean($event))"
              />
            </template>
          </el-table-column>
          <el-table-column label="标签" min-width="180">
            <template #default="{ row }">
              <span class="tag-preview" :style="tagStyle(row.color)">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" min-width="150">
            <template #default="{ row }">
              <div class="manage-source-cell">
                <el-tag size="small" effect="plain">{{ sourceLabel(row) }}</el-tag>
                <small v-if="row.sourceType === 'SCRAPER' && row.sourceDetail">
                  {{ row.sourceDetail }}
                </small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="使用书籍" prop="bookCount" width="110" align="right">
            <template #default="{ row }">{{ row.bookCount || 0 }} 本</template>
          </el-table-column>
        </el-table>
      </div>
      <div class="manage-pagination">
        <el-pagination
          v-model:current-page="managePage"
          :page-size="managePageSize"
          :total="filteredManageTags.length"
          :pager-count="5"
          :disabled="deletingManageTags"
          layout="total, prev, pager, next"
        />
      </div>
      <template #footer>
        <el-button :disabled="deletingManageTags" @click="manageDialogVisible = false">关闭</el-button>
        <el-button
          type="danger"
          :disabled="selectedManageTagIds.length === 0"
          :loading="deletingManageTags"
          @click="deleteSelectedTags"
        >
          批量删除（{{ selectedManageTagIds.length }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { confirm, message } from '@/utils/message'
import { useTagStore, type Tag } from '@/stores/tag'

const tagStore = useTagStore()
const dialogVisible = ref(false)
const pendingVisible = ref(false)
const pendingBusy = ref(false)
const pendingTags = ref<Tag[]>([])
const pendingIds = ref<number[]>([])
const pendingKeyword = ref('')
const filteredPendingTags = computed(() => pendingTags.value.filter(tag =>
  tag.name.toLocaleLowerCase().includes(pendingKeyword.value.trim().toLocaleLowerCase())
))

const openPending = async () => {
  pendingVisible.value = true
  pendingBusy.value = true
  pendingIds.value = []
  pendingTags.value = []
  pendingKeyword.value = ''
  try {
    pendingTags.value = await tagStore.fetchPendingTags()
  } catch {
    message.error('待加入标签加载失败，请关闭后重试')
  } finally {
    pendingBusy.value = false
  }
}

const processPending = async (action: 'approve' | 'delete') => {
  if (!pendingIds.value.length || pendingBusy.value) return
  if (action === 'delete' && !await confirm('确定删除所选待加入标签吗？再次爬取可能重新出现。')) return
  pendingBusy.value = true
  try {
    await tagStore.managePendingTags([...pendingIds.value], action)
    pendingIds.value = []
    pendingTags.value = await tagStore.fetchPendingTags()
    message.success(action === 'approve' ? '所选标签已加入标签库' : '所选待加入标签已删除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '处理失败，请关闭后重试')
  } finally {
    pendingBusy.value = false
  }
}

const editingId = ref<number>()
const saving = ref(false)
const searchKeyword = ref('')
const scanDialogVisible = ref(false)
const scanLoading = ref(false)
const scanComplete = ref(false)
const scanError = ref('')
const scanScannedBooks = ref(0)
const scanTotalBooks = ref(0)
const scanCandidates = ref<Array<{ key: string; label: string }>>([])
const selectedScannedTags = ref<string[]>([])
const addingScannedTags = ref(false)
const manageDialogVisible = ref(false)
const manageKeyword = ref('')
const manageSourceFilter = ref('')
const managePage = ref(1)
const managePageSize = 20
const selectedManageTagIds = ref<number[]>([])
const deletingManageTags = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const pageSizeOptions = [10, 20, 50]
const sourceOptions = [
  { value: 'SYSTEM_BUILTIN', label: '系统内置' },
  { value: 'MANUAL', label: '人工添加' },
  { value: 'LIBRARY_SCAN', label: '书籍扫描' },
  { value: 'SCRAPER', label: '刮削' },
]
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
const scanProgress = computed(() => scanTotalBooks.value === 0
  ? (scanComplete.value ? 100 : 0)
  : Math.min(100, Math.round(scanScannedBooks.value / scanTotalBooks.value * 100)))
const filteredManageTags = computed(() => {
  const keyword = manageKeyword.value.trim().toLocaleLowerCase()
  return tagStore.tags.filter(tag => {
    const matchesName = !keyword || tag.name.toLocaleLowerCase().includes(keyword)
    const matchesSource = !manageSourceFilter.value
      || (tag.sourceType || 'MANUAL') === manageSourceFilter.value
    return matchesName && matchesSource
  })
})

const pagedManageTags = computed(() => {
  const start = (managePage.value - 1) * managePageSize
  return filteredManageTags.value.slice(start, start + managePageSize)
})
const allManagePageSelected = computed(() => pagedManageTags.value.length > 0
  && pagedManageTags.value.every(tag => selectedManageTagIds.value.includes(tag.id)))
const someManagePageSelected = computed(() =>
  pagedManageTags.value.some(tag => selectedManageTagIds.value.includes(tag.id)))

const toggleManagePage = (checked: boolean) => {
  if (deletingManageTags.value) return
  const pageIds = new Set(pagedManageTags.value.map(tag => tag.id))
  selectedManageTagIds.value = checked
    ? [...new Set([...selectedManageTagIds.value, ...pageIds])]
    : selectedManageTagIds.value.filter(id => !pageIds.has(id))
}

const resetManageFilter = () => {
  managePage.value = 1
  selectedManageTagIds.value = []
}
const clampManagePage = () => {
  const lastPage = Math.max(1, Math.ceil(filteredManageTags.value.length / managePageSize))
  managePage.value = Math.min(managePage.value, lastPage)
}
watch([manageKeyword, manageSourceFilter], resetManageFilter)
watch(() => filteredManageTags.value.length, clampManagePage)

const tagStyle = (color?: string) => {
  const safeColor = /^#[0-9a-fA-F]{6}$/.test(color || '') ? color! : '#64748B'
  return {
    color: safeColor,
    borderColor: `${safeColor}88`,
    backgroundColor: `${safeColor}16`,
  }
}

const sourceLabel = (tag: Tag) => ({
  SYSTEM_BUILTIN: '系统内置',
  MANUAL: '人工添加',
  LIBRARY_SCAN: '书籍扫描',
  SCRAPER: '刮削',
}[tag.sourceType || 'MANUAL'] || '人工添加')

const startLibraryScan = async () => {
  scanDialogVisible.value = true
  scanLoading.value = true
  scanComplete.value = false
  scanError.value = ''
  scanScannedBooks.value = 0
  scanTotalBooks.value = 0
  scanCandidates.value = []
  selectedScannedTags.value = []

  try {
    await tagStore.fetchTags()
    const existingNames = new Set(tagStore.tags.map(tag => tag.name.toLocaleLowerCase()))
    const found = new Map<string, string>()
    let page = 0
    let complete = false
    while (!complete) {
      const result = await tagStore.scanLibraryPage(page, 100)
      scanScannedBooks.value = result.scannedBooks
      scanTotalBooks.value = result.totalBooks
      for (const name of result.tags) {
        const normalized = name.trim().toLocaleLowerCase()
        if (normalized && !existingNames.has(normalized)) found.set(normalized, name.trim())
      }
      complete = result.complete
      page += 1
    }
    scanCandidates.value = [...found.values()]
      .sort((a, b) => a.localeCompare(b, 'zh-CN'))
      .map(name => ({ key: name, label: name }))
    scanComplete.value = true
  } catch (error: any) {
    scanError.value = error.response?.data?.message || '书库扫描失败，请稍后重试。'
  } finally {
    scanLoading.value = false
  }
}

const confirmAddScannedTags = async () => {
  if (!selectedScannedTags.value.length) return
  addingScannedTags.value = true
  try {
    const added = await tagStore.addScannedTags(selectedScannedTags.value)
    message.success(`已添加 ${added.length} 个标签`)
    scanDialogVisible.value = false
  } catch (error: any) {
    message.error(error.response?.data?.message || '添加标签失败')
  } finally {
    addingScannedTags.value = false
  }
}

const openManage = () => {
  managePage.value = 1
  manageKeyword.value = ''
  manageSourceFilter.value = ''
  selectedManageTagIds.value = []
  manageDialogVisible.value = true
}

const toggleManageTag = (id: number, checked: boolean) => {
  if (deletingManageTags.value) return
  selectedManageTagIds.value = checked
    ? [...new Set([...selectedManageTagIds.value, id])]
    : selectedManageTagIds.value.filter(selectedId => selectedId !== id)
}

const deleteSelectedTags = async () => {
  const ids = [...selectedManageTagIds.value]
  if (!ids.length || deletingManageTags.value) return
  const accepted = await confirm(
    `确定删除选中的 ${ids.length} 个标签吗？标签将从关联书籍中移除。`,
  )
  if (!accepted) return
  deletingManageTags.value = true
  try {
    const result = await tagStore.deleteTags(ids)
    selectedManageTagIds.value = []
    message.success(`已删除 ${result.deletedCount} 个标签`)
  } catch (error: any) {
    message.error(error.response?.data?.message || '批量删除失败')
  } finally {
    deletingManageTags.value = false
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
:global(.el-dialog.tag-manage-dialog) {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
  height: calc(100dvh - 48px);
  max-height: calc(100dvh - 48px);
  margin: 24px auto;
}

:global(.tag-manage-dialog .el-dialog__header),
:global(.tag-manage-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}

:global(.tag-manage-dialog .el-dialog__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.manage-tools,
.manage-selection,
.manage-pagination {
  flex: 0 0 auto;
}

.manage-selection {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-bottom: 10px;
}

.manage-selection span {
  color: var(--text-secondary);
  font-size: 12px;
}

.manage-table-wrap {
  flex: 1;
  min-height: 0;
}

.manage-pagination {
  margin-top: 14px;
  overflow-x: auto;
}

@media (max-height: 540px) {
  :global(.tag-manage-dialog .el-dialog__body) {
    overflow-y: auto;
  }

  .manage-table-wrap {
    flex: 0 0 180px;
  }
}

@media (max-width: 640px) {
  :global(.el-dialog.tag-manage-dialog) {
    height: calc(100dvh - max(10px, env(safe-area-inset-top)) - max(10px, env(safe-area-inset-bottom)));
    max-height: calc(100dvh - max(10px, env(safe-area-inset-top)) - max(10px, env(safe-area-inset-bottom)));
  }
}

.tag-management-view {
  width: 100%;
}

.page-header,
.tag-card-header,
.tag-actions,
.color-field,
.header-actions {
  display: flex;
  align-items: center;
}

.page-header,
.tag-card-header {
  justify-content: space-between;
}

.header-actions {
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
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
  grid-template-columns: minmax(180px, 1fr) minmax(150px, 230px) 90px auto;
  align-items: center;
  min-height: 64px;
  border-bottom: 1px solid var(--border-color);
}

.tag-source,
.manage-source-cell {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.tag-source small,
.manage-source-cell small {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-label {
  color: var(--text-secondary);
  font-size: 12px;
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

.scan-dialog-content {
  display: grid;
  gap: 16px;
}

.dialog-intro,
.transfer-heading span {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.scan-progress-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
}

.scan-progress-heading,
.transfer-heading,
.manage-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.scan-progress-heading span,
.manage-count {
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.scan-error {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 13px;
}

.scan-transfer-wrap {
  display: grid;
  gap: 14px;
}

.transfer-heading {
  justify-content: flex-start;
}

.scan-transfer-wrap :deep(.el-transfer) {
  display: flex;
  justify-content: center;
  width: 100%;
}

.scan-transfer-wrap :deep(.el-transfer-panel) {
  width: min(360px, calc((100% - 80px) / 2));
}

.manage-tools {
  margin-bottom: 14px;
}

.manage-tools .el-input {
  flex: 1;
}

.manage-tools .el-select {
  width: 180px;
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

  .tag-source {
    grid-column: 1;
  }

  .tag-count {
    grid-column: 1;
  }

  .tag-actions {
    grid-column: 2;
    grid-row: 1 / span 3;
  }

  .manage-tools {
    align-items: stretch;
    flex-direction: column;
  }

  .manage-tools .el-select {
    width: 100%;
  }
}
</style>
