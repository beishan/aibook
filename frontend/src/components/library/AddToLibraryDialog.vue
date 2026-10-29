<template>
  <el-dialog
    :model-value="modelValue"
    :title="libraryId ? '从总书库添加书籍' : '添加到图书馆'"
    width="min(760px, 94vw)"
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
      <div class="picker-search">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索书名、作者"
          aria-label="搜索待添加书籍"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button @click="search">
          搜索
        </el-button>
      </div>
      <el-table v-loading="loading" :data="candidates" row-key="id" max-height="360">
        <el-table-column label="选择" width="65">
          <template #default="{ row }">
            <el-checkbox
              :model-value="row.existing || chosen.includes(row.id)"
              :disabled="row.existing"
              :aria-label="`选择${row.title}`"
              @change="toggle(row.id)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="200" />
        <el-table-column prop="author" label="作者" min-width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            {{ row.existing ? '已添加' : '可添加' }}
          </template>
        </el-table-column>
      </el-table>
      <div class="picker-pages">
        <span>
          已选 {{ chosen.length }} 本，已有馆藏会自动跳过
        </span>
        <el-pagination
          v-model:current-page="page"
          :page-size="24"
          :total="total"
          layout="prev, next"
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
import { computed, ref, watch } from 'vue';
import { ElMessageBox } from 'element-plus';
import api from '@/utils/api';
import { personalLibraries, type PersonalLibrary, type BatchResult } from '@/utils/personalLibraries';
import { message } from '@/utils/message';
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
const candidates = ref<{
    id: number;
    title: string;
    author: string;
    existing?: boolean;
}[]>([]);
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
async function loadBooks() {
    const request = ++generation;
    loading.value = true;
    try {
        const { data } = await api.get('/api/books', { params: { keyword: keyword.value, page: page.value - 1, size: 24 } });
        if (request !== generation)
            return;
        const ids = data.content.map((book: {
            id: number;
        }) => book.id);
        const existing = ids.length ? (await api.get<number[]>(`/api/libraries/${props.libraryId}/membership`, {
            params: { bookIds: ids.join(','), listId: props.listId },
        })).data : [];
        if (request !== generation)
            return;
        candidates.value = data.content.map((book: {
            id: number;
        }) => ({ ...book, existing: existing.includes(book.id) }));
        total.value = data.totalElements;
    }
    finally {
        if (request === generation)
            loading.value = false;
    }
}
function search() { page.value = 1; void loadBooks(); }
function toggle(id: number) {
    if (chosen.value.includes(id))
        chosen.value = chosen.value.filter(item => item !== id);
    else if (chosen.value.length < 200)
        chosen.value.push(id);
    else
        message.warning('每次最多添加 200 本');
}
async function submit() {
    saving.value = true;
    try {
        results.value = await personalLibraries.add(props.libraryId ? [props.libraryId] : targets.value, props.libraryId ? chosen.value : props.bookIds, props.listId);
        emit('added');
        if (!failures.value.length) {
            chosen.value = [];
            targets.value = [];
        }
    }
    finally {
        saving.value = false;
    }
}
async function retry() {
    saving.value = true;
    try {
        const retried: BatchResult[] = [];
        for (const result of failures.value)
            retried.push(...await personalLibraries.add([result.libraryId], [result.bookId], props.listId));
        results.value = [...results.value.filter(r => r.status !== 'failed'), ...retried];
        emit('added');
    }
    finally {
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
function close() { if (!saving.value)
    emit('update:modelValue', false); }
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
    if (props.libraryId)
        await loadBooks();
    else
        await loadTargets();
});
</script>
<style scoped>
.muted {
  color: var(--text-secondary);
  margin-bottom: 20px;
}
.picker-search {
  display: flex;
  gap: 10px;
  margin: 18px 0;
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
