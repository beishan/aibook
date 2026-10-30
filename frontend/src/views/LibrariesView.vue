<template>
  <div class="personal-libraries">
    <header class="library-page-heading">
      <div>
        <p class="library-eyebrow">
          我的阅读空间
        </p>
        <h1>
          我的图书馆
        </h1>
        <p class="library-muted">
          为每一种热爱，留一间书房。
        </p>
      </div>
      <el-button type="primary" @click="createOpen = true">
        ＋ 创建图书馆
      </el-button>
    </header>
    <div class="library-toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索图书馆名称"
        aria-label="搜索图书馆名称"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button @click="search">
        搜索
      </el-button>
      <el-select v-model="sort" aria-label="图书馆排序" @change="search">
        <el-option value="manual" label="手工次序" />
        <el-option value="created" label="最近创建" />
      </el-select>
      <span class="library-muted">
        {{ total }} 座图书馆 · 仅自己可见
      </span>
    </div>
    <el-alert v-if="failed" type="error" title="加载失败，请重试" :closable="false">
      <el-button @click="load">
        重新加载
      </el-button>
    </el-alert>
    <div v-loading="loading" class="personal-library-grid">
      <article v-for="(library, index) in libraries" :key="library.id" class="personal-library-card">
        <button class="library-card-open" @click="router.push(`/libraries/${library.id}`)">
          <LibraryLandscape :library-id="library.id" :image-key="library.config.coverImage" :variant="library.config.cover" />
          <div class="library-card-copy">
            <small>
              私人图书馆
            </small>
            <h2>
              {{ library.name }}
            </h2>
            <p>
              {{ library.description || '在这里，慢慢收集自己的阅读世界。' }}
            </p>
            <footer>
              <span>
                {{ library.bookCount }} 本藏书
              </span>
              <span>
                进入图书馆 ↗
              </span>
            </footer>
          </div>
        </button>
        <div class="library-cover-actions">
          <el-button text :aria-label="`自定义${library.name}封面`" @click="coverLibrary = library; coverEditorOpen = true">自定义封面</el-button>
        </div>
        <div v-if="sort === 'manual' && !keyword" class="library-card-order">
          <el-button
            text
            :disabled="(index === 0 && page === 1) || savingOrder"
            :aria-label="`前移${library.name}`"
            @click="reorder(index, -1)"
          >
            ← 前移
          </el-button>
          <el-button
            text
            :disabled="(index === libraries.length - 1 && page * size >= total) || savingOrder"
            :aria-label="`后移${library.name}`"
            @click="reorder(index, 1)"
          >
            后移 →
          </el-button>
        </div>
      </article>
      <button class="library-create-card" @click="createOpen = true">
        <span>
          ＋
        </span>
        <h3>
          下一间书房，留给新的热爱
        </h3>
        <p>
          创建一个主题图书馆
        </p>
      </button>
    </div>
    <el-pagination
      v-if="total"
      v-model:current-page="page"
      :page-size="size"
      :page-sizes="[12,24,48,96]"
      :total="total"
      layout="total, sizes, prev, pager, next"
      class="library-pagination"
      @current-change="load"
      @size-change="changeSize"
    />
    <LibraryCoverEditor v-model="coverEditorOpen" :library="coverLibrary" @saved="load" />
    <el-dialog
      v-model="createOpen"
      title="创建图书馆"
      width="min(500px,94vw)"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-position="top" @submit.prevent="create">
        <el-form-item label="图书馆名称">
          <el-input v-model="name" maxlength="50" show-word-limit placeholder="例如：文学与远方" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="首页模板">
          <AudioSegmented v-model="createConfig.template" label="新馆首页模板"
            :options="[{ value:'simple', label:'简洁' }, { value:'shelf', label:'书架' }, { value:'editorial', label:'专题' }]" />
        </el-form-item>
        <el-form-item label="内置封面">
          <AudioSegmented v-model="createConfig.cover" label="新馆内置封面"
            :options="[{ value:'landscape', label:'山景' }, { value:'arches', label:'拱廊' }, { value:'lines', label:'线条' }]" />
        </el-form-item>
        <p class="library-muted">
          先建一座空馆，再从书库添加书籍；外观默认跟随网站主题。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">
          取消
        </el-button>
        <el-button type="primary" :loading="creating" :disabled="!name.trim()" @click="create">
          创建图书馆
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import LibraryLandscape from '@/components/library/LibraryLandscape.vue'
import LibraryCoverEditor from '@/components/library/LibraryCoverEditor.vue'
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import api from '@/utils/api';
import AudioSegmented from '@/components/audio/AudioSegmented.vue';
import { personalLibraries, defaultLibraryConfig, type PersonalLibrary } from '@/utils/personalLibraries';
import '@/styles/personal-libraries.css';
const router = useRouter();
const libraries = ref<PersonalLibrary[]>([]);
const createConfig = ref(defaultLibraryConfig());
const keyword = ref(''), sort = ref('manual'), name = ref(''), description = ref('');
const page = ref(1), size = ref(24), total = ref(0);
const coverEditorOpen = ref(false)
const coverLibrary = ref<PersonalLibrary | null>(null)
const loading = ref(false), failed = ref(false), creating = ref(false), createOpen = ref(false), savingOrder = ref(false);
let generation = 0;
async function load() {
    const request = ++generation;
    loading.value = true;
    failed.value = false;
    try {
        const result = await personalLibraries.list({ keyword: keyword.value, sort: sort.value, page: page.value - 1, size: size.value });
        if (request !== generation)
            return;
        total.value = result.totalElements;
        libraries.value = result.content;
        if (page.value > 1 && !libraries.value.length && total.value) {
            page.value = Math.ceil(total.value / size.value);
            await load();
        }
    }
    catch {
        failed.value = true;
    }
    finally {
        if (request === generation)
            loading.value = false;
    }
}
function search() { page.value = 1; void load(); }
async function changeSize(value: number) {
    await api.put('/api/libraries/preferences', { pageSize: value });
    size.value = value;
    search();
}
async function create() {
    if (!name.value.trim())
        return;
    creating.value = true;
    try {
        const library = await personalLibraries.create({ name: name.value.trim(), description: description.value, config: createConfig.value });
        createOpen.value = false;
        await router.push(`/libraries/${library.id}`);
    }
    finally {
        creating.value = false;
    }
}
async function reorder(index: number, direction: number) {
    savingOrder.value = true;
    try {
        let other = libraries.value[index + direction];
        if (!other) {
            const adjacent = await personalLibraries.list({ page: page.value - 1 + direction, size: size.value, sort: 'manual' });
            other = direction < 0 ? adjacent.content[adjacent.content.length - 1] : adjacent.content[0];
        }
        if (!other) return;
        await personalLibraries.order(libraries.value[index].id, 'libraries', libraries.value[index].id, other.id);
        await load();
    }
    finally {
        savingOrder.value = false;
    }
}
onMounted(async () => {
    try {
        size.value = (await api.get('/api/libraries/preferences')).data.pageSize;
    }
    finally {
        await load();
    }
});
</script>
