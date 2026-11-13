<template>
  <el-dialog
    :model-value="modelValue"
    :title="libraryId ? '从总书库添加书籍' : '添加到图书馆'"
    :width="libraryId ? 'min(1280px, 96vw)' : 'min(760px, 94vw)'"
    top="5vh"
    class="library-picker-dialog"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @close="close"
  >
    <p class="muted">
      只建立馆藏关联，不复制文件，不改变原有书架、收藏或阅读进度。
    </p>
    <template v-if="!libraryId">
      <el-select
        v-model="targets"
        multiple
        filterable
        remote
        :remote-method="loadTargets"
        placeholder="搜索并选择目标图书馆（最多 20 个）"
        style="width:100%"
        :multiple-limit="20"
      >
        <el-option v-for="item in libraries" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <p>
        已选择 {{ bookIds.length }} 本书
      </p>
      <el-button @click="createLibrary">
        新建图书馆并继续
      </el-button>
    </template>
    <template v-else>
      <el-alert v-if="listId" title="所选书籍将同时加入本馆及当前书单" type="info" :closable="false" />
      <el-form :disabled="saving" label-position="top" class="picker-filters" @submit.prevent="search">
        <el-form-item label="关键词" class="keyword-filter">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索书名、作者、ISBN"
            aria-label="搜索待添加书籍"
            @keyup.enter="search"
            @clear="search"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.format" clearable aria-label="类型筛选" placeholder="全部类型">
            <el-option v-for="item in formatOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="阅读状态">
          <el-select v-model="filters.status" clearable aria-label="阅读状态筛选" placeholder="全部状态">
            <el-option label="未读" value="UNREADING" />
            <el-option label="正在阅读" value="READING" />
            <el-option label="已读完" value="FINISHED" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.categoryId" clearable filterable aria-label="分类筛选" placeholder="全部分类">
            <el-option
              v-for="category in categoryStore.flatTree"
              :key="category.id"
              :label="category.path"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="filters.tagId" clearable filterable aria-label="标签筛选" placeholder="全部标签">
            <el-option v-for="tag in tagStore.tags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库来源">
          <el-select v-model="filters.source" clearable filterable aria-label="入库来源筛选" placeholder="全部来源">
            <el-option label="上传" value="UPLOAD" />
            <el-option label="目录扫描" value="DIRECTORY_SCAN" />
            <el-option label="爬取（全部网站）" value="CRAWLER" />
            <el-option v-for="site in sourceSites" :key="site" :label="`爬取 · ${site}`" :value="`site:${site}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库日期" class="date-filter">
          <el-date-picker
            v-model="addedRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            aria-label="入库日期范围"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="filters.sortBy" aria-label="排序字段">
            <el-option label="添加时间" value="createdAt" />
            <el-option label="书名" value="title" />
            <el-option label="作者" value="author" />
            <el-option label="最近更新" value="updatedAt" />
          </el-select>
        </el-form-item>
        <el-form-item label="顺序">
          <el-select v-model="filters.sortDir" aria-label="排序方向">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
        </el-form-item>
        <div class="picker-filter-actions">
          <el-checkbox v-model="filters.includeChildren">包含子分类</el-checkbox>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </el-form>
      <el-alert v-if="loadError" title="查询失败，请重新查询或重试" type="error" :closable="false" />
      <div class="picker-selection">
        <el-button :disabled="loading || saving || !hasSelectableOnPage" @click="selectCurrentPage">全选</el-button>
        <el-button :disabled="saving || chosen.length === 0" @click="chosen = []">清空已选</el-button>
        <span>已选 {{ chosen.length }} / 200 本，跨页保留选择；全选仅处理本页可添加书籍。</span>
      </div>
      <el-table v-loading="loading" :data="candidates" row-key="id" max-height="min(50vh, 520px)">
        <el-table-column label="选择" width="65">
          <template #default="{ row }">
            <el-checkbox
              :model-value="row.existing || chosen.includes(row.id)"
              :disabled="row.existing || loading || saving"
              :aria-label="`选择${row.title}`"
              @change="toggle(row.id)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="200" />
        <el-table-column prop="author" label="作者" min-width="100" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.format === 'structured' ? '在线章节' : row.format?.toUpperCase() || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="220">
          <template #default="{ row }">
            <div v-if="row.tags?.length || row.tagNames?.length" class="candidate-tags">
              <el-tag v-for="name in candidateTagNames(row)" :key="name" size="small">{{ name }}</el-tag>
            </div>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="章节数" width="100">
          <template #default="{ row }">{{ row.chapterCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            {{ row.existing ? '已添加' : '可添加' }}
          </template>
        </el-table-column>
      </el-table>
      <div class="picker-pages">
        <span>
          已选 {{ chosen.length }} 本，列表仅显示尚未加入本馆的书籍
        </span>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[...SHELF_PAGE_SIZE_OPTIONS]"
          :disabled="saving"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadBooks"
        />
      </div>
    </template>
    <div v-if="results.length" class="batch-results" role="status">
      <p>
        新增 {{ results.filter(r => r.status === 'added').length }} 条，已存在 {{ results.filter(r => r.status === 'existing').length }} 条，失败 {{ failures.length }} 条。
      </p>
      <p v-for="result in failures" :key="`${result.libraryId}-${result.bookId}`">
        书籍 #{{ result.bookId }}：{{ result.reason }}
      </p>
      <el-button v-if="failures.length" :loading="saving" @click="retry">
        仅重试失败项
      </el-button>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="close">
        {{ results.length ? '完成' : '取消' }}
      </el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submit">
        添加{{ libraryId ? ` ${chosen.length} 本` : `到 ${targets.length} 个馆` }}
      </el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessageBox } from 'element-plus';
import api from '@/utils/api';
import { personalLibraries, type PersonalLibrary, type BatchResult } from '@/utils/personalLibraries';
import { message } from '@/utils/message';
import { useCategoryStore } from '@/stores/category';
import { useTagStore } from '@/stores/tag';
import { SHELF_PAGE_SIZE_OPTIONS, usePreferencesStore } from '@/stores/preferences';
import type { Book } from '@/stores/book';

const categoryStore = useCategoryStore();
const tagStore = useTagStore();
const preferencesStore = usePreferencesStore();
const pageSize = computed({
    get: () => preferencesStore.libraryPickerPageSize,
    set: (value: number) => { void preferencesStore.setLibraryPickerPageSize(value); },
});
const formatOptions = [
    { value: 'epub', label: 'EPUB' },
    { value: 'structured', label: '在线章节' },
    { value: 'pdf', label: 'PDF' },
    { value: 'txt', label: 'TXT' },
    { value: 'mobi', label: 'MOBI' },
    { value: 'azw', label: 'AZW' },
    { value: 'azw3', label: 'AZW3' },
    { value: 'docx', label: 'DOCX' },
    { value: 'html', label: 'HTML' },
    { value: 'md', label: 'Markdown' },
    { value: 'cbz', label: 'CBZ' },
    { value: 'cbr', label: 'CBR' },
];
const defaultFilters = () => ({
    format: '', status: '', categoryId: undefined as number | undefined,
    tagId: undefined as number | undefined, source: '', includeChildren: true,
    sortBy: 'createdAt', sortDir: 'desc',
});
const filters = reactive(defaultFilters());
const addedRange = ref<string[] | null>(null);
const sourceSites = ref<string[]>([]);
const loadError = ref(false);

const props = withDefaults(defineProps<{
    modelValue: boolean;
    bookIds?: number[];
    libraryId?: number;
    listId?: number;
}>(), { bookIds: () => [] });
const emit = defineEmits<{
    'update:modelValue': [
        value: boolean
    ];
    added: [
    ];
}>();
const libraries = ref<PersonalLibrary[]>([]);
const targets = ref<number[]>([]);
type Candidate = Pick<Book, 'id' | 'title' | 'author' | 'format' | 'tags' | 'tagNames' | 'chapterCount'> & { existing?: boolean };
const candidates = ref<Candidate[]>([]);
const chosen = ref<number[]>([]);
const keyword = ref('');
const page = ref(1), total = ref(0), loading = ref(false), saving = ref(false);
const results = ref<BatchResult[]>([]);
const failures = computed(() => results.value.filter(r => r.status === 'failed'));
const canSubmit = computed(() => props.libraryId ? chosen.value.length > 0 : props.bookIds.length > 0 && targets.value.length > 0);
let generation = 0;
async function loadTargets(query = '') {
    const response = await personalLibraries.list({ keyword: query, size: 24 });
    const selected = libraries.value.filter(lib => targets.value.includes(lib.id));
    libraries.value = [...new Map([...selected, ...response.content].map(lib => [lib.id, lib])).values()];
}
function candidateTagNames(book: Candidate) {
    return [...new Set([...(book.tags || []).map(tag => tag.name), ...(book.tagNames || [])])];
}
const hasSelectableOnPage = computed(() => candidates.value.some(book => !book.existing && !chosen.value.includes(book.id)));

async function loadFilterOptions() {
    const results = await Promise.allSettled([
        categoryStore.fetchTree(),
        tagStore.fetchTags(),
        api.get<string[]>('/api/books/source-sites').then(({ data }) => { sourceSites.value = data; }),
    ]);
    if (props.modelValue && results.some(result => result.status === 'rejected')) {
        message.warning('部分筛选选项加载失败，请重新打开弹窗重试');
    }
}

async function loadBooks() {
    const request = ++generation;
    loading.value = true;
    loadError.value = false;
    candidates.value = [];
    try {
        const source = filters.source || '';
        const { data } = await api.get('/api/books', {
            params: {
                excludeLibraryId: props.libraryId,
                keyword: keyword.value.trim() || undefined,
                format: filters.format || undefined,
                status: filters.status || undefined,
                categoryId: filters.categoryId || undefined,
                includeChildren: filters.includeChildren,
                tagId: filters.tagId || undefined,
                sourceType: source.startsWith('site:') ? 'CRAWLER' : source || undefined,
                sourceSiteName: source.startsWith('site:') ? source.slice(5) : undefined,
                addedFrom: addedRange.value?.[0],
                addedTo: addedRange.value?.[1],
                sortBy: filters.sortBy,
                sortDir: filters.sortDir,
                page: page.value - 1,
                size: pageSize.value,
            },
        });
        if (request !== generation) return;
        const lastPage = Math.max(1, Math.ceil(data.totalElements / pageSize.value));
        if (page.value > lastPage) {
            page.value = lastPage;
            await loadBooks();
            return;
        }
        const ids = data.content.map((book: Candidate) => book.id);
        const existing = ids.length ? (await api.get<number[]>(`/api/libraries/${props.libraryId}/membership`, {
            params: { bookIds: ids.join(',') },
        })).data : [];
        if (request !== generation) return;
        candidates.value = data.content.filter((book: Candidate) => !existing.includes(book.id));
        total.value = data.totalElements;
    } catch (error) {
        if (request !== generation) return;
        candidates.value = [];
        total.value = 0;
        loadError.value = true;
        message.error('加载待添加书籍失败，请重试');
    } finally {
        if (request === generation) loading.value = false;
    }
}
function search() {
    if (saving.value) return;
    page.value = 1;
    void loadBooks();
}
function resetFilters() {
    keyword.value = '';
    addedRange.value = null;
    Object.assign(filters, defaultFilters());
    search();
}
function selectCurrentPage() {
    if (loading.value || saving.value) return;
    const selected = new Set(chosen.value);
    const eligible = candidates.value.filter(book => !book.existing && !selected.has(book.id));
    const remaining = Math.max(0, 200 - selected.size);
    eligible.slice(0, remaining).forEach(book => selected.add(book.id));
    chosen.value = [...selected];
    if (eligible.length > remaining) message.warning('每次最多添加 200 本，已选至上限');
}
function toggle(id: number) {
    if (loading.value || saving.value || candidates.value.find(book => book.id === id)?.existing) return;
    if (chosen.value.includes(id)) {
        chosen.value = chosen.value.filter(item => item !== id);
    } else if (chosen.value.length < 200) {
        chosen.value.push(id);
    } else {
        message.warning('每次最多添加 200 本');
    }
}
async function submit() {
    if (saving.value || !canSubmit.value) return;
    saving.value = true;
    try {
        results.value = await personalLibraries.add(props.libraryId ? [props.libraryId] : targets.value, props.libraryId ? chosen.value : props.bookIds, props.listId);
        emit('added');
        if (!failures.value.length) {
            chosen.value = [];
            targets.value = [];
        }
        if (props.libraryId) await loadBooks();
    } catch {
        message.error('添加书籍失败，请重试');
    } finally {
        saving.value = false;
    }
}
async function retry() {
    if (saving.value) return;
    saving.value = true;
    try {
        const retried: BatchResult[] = [];
        for (const result of failures.value)
            retried.push(...await personalLibraries.add([result.libraryId], [result.bookId], props.listId));
        results.value = [...results.value.filter(r => r.status !== 'failed'), ...retried];
        emit('added');
        if (props.libraryId) await loadBooks();
    } catch {
        message.error('重试失败，请稍后重试');
    } finally {
        saving.value = false;
    }
}
async function createLibrary() {
    try {
        const { value } = await ElMessageBox.prompt('图书馆名称（1–50 字）', '创建图书馆', {
            inputValidator: value => !!value?.trim() && value.trim().length <= 50 || '请输入 1–50 字名称',
        });
        const library = await personalLibraries.create({ name: value.trim() });
        libraries.value.push(library);
        targets.value.push(library.id);
    }
    catch (error) {
        if (error !== 'cancel' && error !== 'close')
            console.error(error);
    }
}
function close() {
    if (!saving.value) emit('update:modelValue', false);
}
watch(pageSize, () => {
    if (props.modelValue && props.libraryId) search();
});
watch(() => props.modelValue, async (visible) => {
    if (!visible) {
        generation++;
        return;
    }
    results.value = [];
    chosen.value = [];
    targets.value = [];
    keyword.value = '';
    page.value = 1;
    candidates.value = [];
    total.value = 0;
    loadError.value = false;
    Object.assign(filters, defaultFilters());
    addedRange.value = null;
    if (props.libraryId) {
        const libraryId = props.libraryId;
        await preferencesStore.hydrate();
        if (!props.modelValue || props.libraryId !== libraryId) return;
        void loadFilterOptions();
        await loadBooks();
    } else {
        await loadTargets();
    }
});
</script>
<style scoped>
.muted {
  color: var(--text-secondary);
  margin-bottom: 20px;
}
.library-picker-dialog {
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}
.library-picker-dialog :deep(.el-dialog__body) {
  overflow-y: auto;
  min-height: 0;
}
.picker-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 14px;
  margin-top: 18px;
}
.picker-filters :deep(.el-select),
.picker-filters :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}
.keyword-filter,
.date-filter {
  grid-column: span 2;
}
.picker-filter-actions,
.picker-selection {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.picker-filter-actions {
  grid-column: 1 / -1;
}
.picker-selection span {
  color: var(--text-secondary);
}
.candidate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
@media (max-width: 700px) {
  .picker-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .picker-pages {
    overflow-x: auto;
  }
}
.picker-pages {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 15px;
}
.batch-results {
  padding: 12px;
  margin-top: 20px;
  background: var(--primary-alpha-10);
  border-radius: var(--radius-sm);
  overflow-wrap: anywhere;
  max-height: 180px;
  overflow: auto;
}
</style>
