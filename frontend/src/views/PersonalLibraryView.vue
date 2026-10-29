<template>
  <div
    class="personal-library"
    :class="[`template-${activeConfig.template}`, `background-${activeConfig.background}`]"
    :style="accentStyle"
  >
    <el-alert v-if="failed" title="图书馆不可用或加载失败" type="error" :closable="false">
      <el-button @click="refresh">
        重新加载
      </el-button>
      <el-button @click="router.push('/libraries')">
        返回图书馆列表
      </el-button>
    </el-alert>
    <div v-if="library" v-loading="busy">
      <header class="library-page-heading">
        <div>
          <p class="library-eyebrow">
            我的阅读空间 / 私人图书馆
          </p>
          <h1>
            {{ library.name }}
          </h1>
        </div>
        <div class="library-heading-actions">
          <el-button @click="router.push('/libraries')">
            所有图书馆
          </el-button>
          <el-select
            :model-value="id"
            filterable
            remote
            :remote-method="searchLibraries"
            aria-label="切换图书馆"
            style="width:160px"
            @change="switchLibrary"
          >
            <el-option v-for="item in switchOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button @click="navigate('settings')">
            设置
          </el-button>
          <el-button type="primary" @click="pickerOpen = true">
            ＋ 添加书籍
          </el-button>
        </div>
      </header>
      <AudioSegmented :model-value="activeTab" label="图书馆栏目" :options="tabOptions" @update:model-value="navigate" />
      <section v-if="activeTab === 'home'" aria-label="图书馆首页">
        <div class="library-hero">
          <div>
            <p class="library-eyebrow">
              属于自己的主题书房
            </p>
            <h2>
              {{ library.name }}
            </h2>
            <p v-if="activeConfig.showDescription">
              {{ library.description || '在这里，慢慢收集自己的阅读世界。' }}
            </p>
            <span v-if="activeConfig.showCount" class="hero-count">
              {{ library.bookCount }} 本藏书
            </span>
          </div>
          <div
            v-if="activeConfig.showCover"
            class="library-landscape"
            :class="`cover-${activeConfig.cover}`"
            aria-hidden="true"
          >
            <span />
          </div>
        </div>
        <section v-for="module in visibleModules" :key="module" class="library-section">
          <div class="library-section-heading">
            <div>
              <h2>
                {{ moduleLabels[module] }}
              </h2>
              <small>
                {{ module === 'continuing' ? '接着上次的位置，继续读下去。' : '从馆藏中，挑选值得停留的内容。' }}
              </small>
            </div>
            <el-button text @click="moduleMore(module)">
              查看全部 →
            </el-button>
          </div>
          <div v-if="module === 'lists'" class="library-list-grid">
            <button v-for="list in homeLists" :key="list.id" class="library-list-card" @click="openList(list)">
              <span class="list-mark" aria-hidden="true">
                ▤
              </span>
              <div>
                <h3>
                  {{ list.name }}
                </h3>
                <p>
                  {{ list.description || '一条自己的阅读路线' }}
                </p>
                <small>
                  {{ list.bookCount }} 本书
                </small>
              </div>
            </button>
          </div>
          <LibraryBookGrid v-else :books="homeBooks(module)" :size="activeConfig.coverSize" @open="openBook">
            <template #actions="{ book }">
              <el-button
                text
                :aria-label="book.favorite ? '取消本馆收藏' : '添加本馆收藏'"
                :aria-pressed="book.favorite"
                @click="patch(book, { favorite: !book.favorite })"
              >
                {{ book.favorite ? '♥' : '♡' }}
              </el-button>
            </template>
          </LibraryBookGrid>
        </section>
        <el-empty
          v-if="!visibleModules.length"
          :description="library.bookCount ? '首页暂未配置内容，可以进入书架浏览' : '让第一本书，走进这座馆'"
        >
          <el-button type="primary" @click="library.bookCount ? navigate('shelf') : pickerOpen = true">
            {{ library.bookCount ? '进入书架' : '从书库添加书籍' }}
          </el-button>
        </el-empty>
      </section>
      <section v-else-if="activeTab === 'shelf' || activeTab === 'favorites'" aria-label="馆藏书籍">
        <div class="library-section-heading" style="margin-top:28px">
          <div>
            <h2>
              {{ currentList?.name || (activeTab === 'favorites' ? '本馆收藏' : '馆内书架') }}
            </h2>
            <small>
              阅读进度跨馆共享；本馆收藏、分组与书单独立保存。
            </small>
          </div>
          <div class="library-heading-actions">
            <el-button v-if="currentList" @click="navigate('lists')">
              返回书单
            </el-button>
            <el-button v-if="currentList" @click="editCollection('lists', currentList)">
              编辑书单
            </el-button>
            <el-button v-else-if="activeTab === 'shelf'" @click="editCollection('groups')">
              新建分组
            </el-button>
          </div>
        </div>
        <div v-if="activeTab === 'shelf' && !currentList" class="library-groups">
          <AudioSegmented :model-value="group" label="馆内分组" :options="groupOptions" @update:model-value="setGroup" />
          <el-dropdown v-if="group && group !== '0'" trigger="click">
            <el-button aria-label="管理当前分组">
              管理分组
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="editCollection('groups', selectedGroup)">
                  编辑分组
                </el-dropdown-item>
                <el-dropdown-item @click="deleteCollection('groups', selectedGroup!)">
                  删除分组
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="library-toolbar">
          <el-input
            v-model="query"
            clearable
            aria-label="搜索本馆书名或作者"
            placeholder="搜索本馆书名、作者"
            @keyup.enter="applyFilters"
            @clear="applyFilters"
          />
          <el-button @click="applyFilters">
            搜索
          </el-button>
          <el-select v-model="status" placeholder="全部阅读状态" aria-label="阅读状态" @change="applyFilters">
            <el-option label="全部阅读状态" value="" />
            <el-option label="未读" value="UNREADING" />
            <el-option label="在读" value="READING" />
            <el-option label="已读" value="FINISHED" />
          </el-select>
          <el-select
            v-model="categoryId"
            clearable
            filterable
            placeholder="分类"
            aria-label="馆藏分类"
            @change="applyFilters"
          >
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="tagId" clearable filterable placeholder="标签" aria-label="馆藏标签" @change="applyFilters">
            <el-option v-for="item in tags" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="sort" aria-label="馆藏排序" @change="applyFilters">
            <el-option label="最近加入" value="added" />
            <el-option label="书名" value="title" />
            <el-option label="作者" value="author" />
            <el-option label="最近阅读" value="recent" />
            <el-option v-if="activeTab === 'favorites'" label="最近收藏" value="favorite" />
            <el-option label="手工次序" value="manual" />
          </el-select>
        </div>
        <div class="library-toolbar">
          <el-checkbox :model-value="books.length > 0 && selected.length === books.length" @change="toggleAll">
            选择当前页
          </el-checkbox>
          <span class="library-muted">
            已选 {{ selected.length }} 本
          </span>
          <div class="library-view-toggle">
            <AudioSegmented
              :model-value="library.config.layout"
              label="馆藏展示"
              :options="[{ value:'grid', label:'封面网格' }, { value:'list', label:'简洁列表' }]"
              @update:model-value="value => savePreference('layout', value)"
            />
          </div>
        </div>
        <div v-if="selected.length" class="library-batch">
          <el-button @click="batchFavorite(true)">
            本馆收藏
          </el-button>
          <el-button @click="batchFavorite(false)">
            取消收藏
          </el-button>
          <el-select :model-value="''" placeholder="移动到分组" aria-label="移动所选书籍到分组" @change="moveSelected">
            <el-option label="未分组" value="0" />
            <el-option v-for="item in library.groups" :key="item.id" :label="item.name" :value="String(item.id)" />
          </el-select>
          <el-select :model-value="''" placeholder="加入本馆书单" aria-label="加入本馆书单" @change="addSelectedToList">
            <el-option v-for="item in library.lists" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button v-if="currentList" @click="removeSelected(true)">
            从书单移除
          </el-button>
          <el-button type="danger" plain @click="removeSelected(false)">
            移出本馆
          </el-button>
        </div>
        <p v-if="sort === 'manual' && hasFilter" class="library-note">
          搜索或筛选期间不能手工排序，请清空筛选后操作。
        </p>
        <LibraryBookGrid
          :books="books"
          :layout="library.config.layout"
          :size="library.config.coverSize"
          selectable
          :selected="selected"
          @select="toggleSelect"
          @open="openBook"
        >
          <template #actions="{ book }">
            <el-button
              text
              :aria-label="book.favorite ? '取消本馆收藏' : '添加本馆收藏'"
              :aria-pressed="book.favorite"
              @click="patch(book, { favorite: !book.favorite })"
            >
              {{ book.favorite ? '♥' : '♡' }}
            </el-button>
            <el-dropdown trigger="click">
              <el-button text :aria-label="`${book.title}更多操作`">
                •••
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="patch(book, { featured: !book.featured })">
                    {{ book.featured ? '取消首页精选' : '设为首页精选' }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="sort === 'manual' && !hasFilter"
                    :disabled="books.indexOf(book) === 0 && page === 1"
                    @click="orderBook(book, -1)"
                  >
                    前移
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="sort === 'manual' && !hasFilter"
                    :disabled="books.indexOf(book) === books.length - 1 && page * library.config.pageSize >= total"
                    @click="orderBook(book, 1)"
                  >
                    后移
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="removeOne(book)">
                    移出本馆
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </LibraryBookGrid>
        <el-empty v-if="!busy && !books.length" :description="hasFilter ? '没有匹配的书籍，请调整筛选' : '这里还没有书籍'">
          <el-button @click="pickerOpen = true">
            从书库添加
          </el-button>
        </el-empty>
        <el-pagination
          v-model:current-page="page"
          :page-size="library.config.pageSize"
          :page-sizes="[12,24,48,96]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          class="library-pagination"
          @current-change="changePage"
          @size-change="value => savePreference('pageSize', value)"
        />
      </section>
      <section v-else-if="activeTab === 'lists'" class="library-section" aria-label="馆内书单">
        <div class="library-section-heading">
          <div>
            <h2>
              馆内书单
            </h2>
            <small>
              给阅读，一条慢慢展开的路径。
            </small>
          </div>
          <el-button type="primary" @click="editCollection('lists')">
            新建书单
          </el-button>
        </div>
        <div class="library-list-grid">
          <article v-for="(list,index) in library.lists" :key="list.id" class="library-list-card">
            <button class="library-list-open" @click="openList(list)">
              <span class="list-mark" aria-hidden="true">
                ▤
              </span>
              <div>
                <h3>
                  {{ list.name }}
                </h3>
                <p>
                  {{ list.description }}
                </p>
                <small>
                  {{ list.bookCount }} 本书{{ list.recommended ? ' · 首页推荐' : '' }}
                </small>
              </div>
            </button>
            <div class="library-heading-actions">
              <el-button text @click="editCollection('lists',list)">
                编辑
              </el-button>
              <el-button text :disabled="index === 0" @click="orderCollection('lists',index,-1)">
                上移
              </el-button>
              <el-button text :disabled="index === library.lists.length - 1" @click="orderCollection('lists',index,1)">
                下移
              </el-button>
              <el-button text type="danger" @click="deleteCollection('lists',list)">
                删除
              </el-button>
            </div>
          </article>
        </div>
        <el-empty v-if="!library.lists.length" description="把散落的书，串成自己的阅读路线" />
      </section>
      <section v-else-if="activeTab === 'settings'" class="library-section" aria-label="图书馆设置">
        <div class="library-section-heading">
          <div>
            <h2>
              布置这间书房
            </h2>
            <small>
              颜色和控件继承全站主题，馆配置只作用于馆内。
            </small>
          </div>
          <div class="library-heading-actions">
            <el-button @click="reloadDraft">
              重新加载
            </el-button>
            <el-button type="primary" :loading="saving" @click="saveSettings">
              保存设置
            </el-button>
          </div>
        </div>
        <el-alert v-if="conflict" title="其他设备已更新设置，当前草稿已保留。请复制需要的内容，再重新加载后修改。" type="warning" :closable="false" />
        <div class="library-settings-grid">
          <div>
            <el-form class="library-settings-panel" label-position="top">
              <h3>
                基本资料与外观
              </h3>
              <el-form-item label="名称">
                <el-input v-model="draft.name" maxlength="50" show-word-limit />
              </el-form-item>
              <el-form-item label="简介">
                <el-input v-model="draft.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
              </el-form-item>
              <el-form-item label="首页模板">
                <AudioSegmented
                  :model-value="draft.config.template"
                  label="首页模板"
                  :options="[{value:'simple',label:'简洁'},{value:'shelf',label:'书架'},{value:'editorial',label:'专题'}]"
                  @update:model-value="setTemplate"
                />
              </el-form-item>
              <el-form-item label="馆头点缀色">
                <el-select v-model="draft.config.accent">
                  <el-option label="跟随网站（推荐）" value="site" />
                  <el-option label="森林点缀" value="forest" />
                  <el-option label="雾蓝点缀" value="blue" />
                  <el-option label="暖棕点缀" value="brown" />
                </el-select>
              </el-form-item>
              <el-form-item label="馆头封面">
                <AudioSegmented
                  v-model="draft.config.cover"
                  label="馆头封面"
                  :options="[{value:'landscape',label:'山景'},{value:'arches',label:'拱廊'},{value:'lines',label:'线条'}]"
                />
              </el-form-item>
              <el-form-item label="背景">
                <AudioSegmented
                  v-model="draft.config.background"
                  label="馆内背景"
                  :options="[{value:'plain',label:'纯色'},{value:'texture',label:'轻纹理'}]"
                />
              </el-form-item>
              <el-form-item label="书架布局">
                <AudioSegmented
                  v-model="draft.config.layout"
                  label="书架布局"
                  :options="[{value:'grid',label:'网格'},{value:'list',label:'列表'}]"
                />
              </el-form-item>
              <el-form-item label="封面尺寸">
                <AudioSegmented
                  v-model="draft.config.coverSize"
                  label="封面尺寸"
                  :options="[{value:'small',label:'小'},{value:'medium',label:'中'},{value:'large',label:'大'}]"
                />
              </el-form-item>
              <div class="library-setting-switches">
                <el-checkbox v-model="draft.config.showDescription">
                  显示简介
                </el-checkbox>
                <el-checkbox v-model="draft.config.showCount">
                  显示馆藏数量
                </el-checkbox>
                <el-checkbox v-model="draft.config.showCover">
                  显示馆头封面
                </el-checkbox>
              </div>
            </el-form>
            <section class="library-settings-panel">
              <h3>
                首页模块
              </h3>
              <div v-for="(key,index) in moduleOrder" :key="key" class="library-module-row">
                <el-checkbox :model-value="draft.config.modules.includes(key)" @change="toggleModule(key)">
                  {{ moduleLabels[key] }}
                </el-checkbox>
                <div>
                  <el-button text :disabled="index === 0" :aria-label="`上移${moduleLabels[key]}`" @click="moveModule(index,-1)">
                    ↑
                  </el-button>
                  <el-button
                    text
                    :disabled="index === moduleOrder.length-1"
                    :aria-label="`下移${moduleLabels[key]}`"
                    @click="moveModule(index,1)"
                  >
                    ↓
                  </el-button>
                </div>
              </div>
              <el-form label-position="top">
                <el-form-item label="每个书籍模块数量">
                  <el-select v-model="draft.config.bookLimit">
                    <el-option v-for="n in [4,8,12]" :key="n" :label="`${n} 本`" :value="n" />
                  </el-select>
                </el-form-item>
                <el-form-item label="首页书单数量">
                  <el-select v-model="draft.config.listLimit">
                    <el-option v-for="n in [2,4,6]" :key="n" :label="`${n} 个`" :value="n" />
                  </el-select>
                </el-form-item>
              </el-form>
              <p class="library-note">
                从书架的“更多”设置精选；编辑书单可设为首页推荐。无数据模块在浏览时隐藏。
              </p>
            </section>
            <section class="library-settings-panel">
              <h3>
                分组排序
              </h3>
              <div v-for="(item,index) in library.groups" :key="item.id" class="library-module-row">
                <span>
                  {{ item.name }}
                </span>
                <div>
                  <el-button text :disabled="index === 0" @click="orderCollection('groups',index,-1)">
                    ↑
                  </el-button>
                  <el-button text :disabled="index === library.groups.length-1" @click="orderCollection('groups',index,1)">
                    ↓
                  </el-button>
                </div>
              </div>
            </section>
            <section class="library-settings-panel">
              <h3>
                精选排序
              </h3>
              <div v-for="(book,index) in featuredBooks" :key="book.id" class="library-module-row">
                <span>
                  {{ book.title }}
                </span>
                <div>
                  <el-button text :disabled="index === 0" @click="orderFeatured(index,-1)">
                    ↑
                  </el-button>
                  <el-button text :disabled="index === featuredBooks.length-1" @click="orderFeatured(index,1)">
                    ↓
                  </el-button>
                </div>
              </div>
            </section>
            <div class="library-section-heading">
              <el-button @click="resetAppearance">
                恢复默认外观
              </el-button>
              <el-button type="danger" plain @click="deleteLibrary">
                删除图书馆
              </el-button>
            </div>
          </div>
          <aside class="library-preview library-settings-panel" :class="`template-${draft.config.template}`">
            <div class="library-section-heading">
              <h3>
                首页预览
              </h3>
              <small>
                {{ dirty ? '尚未保存' : '已保存' }}
              </small>
            </div>
            <div class="library-hero">
              <div>
                <h2>
                  {{ draft.name || '图书馆名称' }}
                </h2>
                <p v-if="draft.config.showDescription">
                  {{ draft.description }}
                </p>
                <small v-if="draft.config.showCount">
                  {{ library.bookCount }} 本藏书
                </small>
              </div>
              <div
                v-if="draft.config.showCover"
                class="library-landscape"
                :class="`cover-${draft.config.cover}`"
                aria-hidden="true"
              >
                <span />
              </div>
            </div>
            <div v-for="key in draft.config.modules" :key="key" class="preview-module">
              <h3>
                {{ moduleLabels[key] }}
              </h3>
              <div class="preview-books" :class="{ 'preview-list': draft.config.layout === 'list' }">
                <span v-for="n in 3" :key="n" />
              </div>
            </div>
            <p class="library-muted">
              此处预览模块顺序和视觉结构，实际首页只显示有数据的模块。
            </p>
          </aside>
        </div>
      </section>
    </div>
    <el-skeleton v-else-if="busy" :rows="8" animated />
    <AddToLibraryDialog v-model="pickerOpen" :library-id="id" :list-id="listId" @added="refresh" />
    <el-dialog
      v-model="collectionOpen"
      :title="`${collectionDraft.id ? '编辑' : '新建'}${collectionKind === 'groups' ? '分组' : '书单'}`"
      width="min(500px,94vw)"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="collectionDraft.name" maxlength="50" show-word-limit />
        </el-form-item>
        <template v-if="collectionKind === 'lists'">
          <el-form-item label="简介">
            <el-input v-model="collectionDraft.description" type="textarea" maxlength="500" show-word-limit />
          </el-form-item>
          <el-checkbox v-model="collectionDraft.recommended">
            设为首页推荐（最多 6 个）
          </el-checkbox>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="collectionOpen = false">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" :disabled="!collectionDraft.name?.trim()" @click="saveCollection">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import AudioSegmented from '@/components/audio/AudioSegmented.vue';
import LibraryBookGrid from '@/components/library/LibraryBookGrid.vue';
import AddToLibraryDialog from '@/components/library/AddToLibraryDialog.vue';
import { personalLibraries, defaultLibraryConfig, moduleLabels, type PersonalLibrary, type LibraryBook, type LibraryCollection, type LibraryConfig } from '@/utils/personalLibraries';
import api from '@/utils/api';
import { message, confirm } from '@/utils/message';
import '@/styles/personal-libraries.css';
function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)); }
const route = useRoute(), router = useRouter();
const id = computed(() => Number(route.params.libraryId));
const listId = computed(() => route.params.listId ? Number(route.params.listId) : undefined);
const activeTab = computed(() => listId.value ? 'shelf' : String(route.meta.libraryTab || 'home'));
const tabOptions = [{ value: 'home', label: '首页' }, { value: 'shelf', label: '书架' }, { value: 'lists', label: '书单' }, { value: 'favorites', label: '收藏' }];
const library = ref<PersonalLibrary | null>(null);
const switchOptions = ref<PersonalLibrary[]>([]);
const books = ref<LibraryBook[]>([]), selected = ref<number[]>([]);
const dashboard = ref<Record<string, any[]>>({});
const categories = ref<{
    id: number;
    name: string;
}[]>([]), tags = ref<{
    id: number;
    name: string;
}[]>([]);
const busy = ref(false), failed = ref(false), saving = ref(false), pickerOpen = ref(false), conflict = ref(false);
const query = ref(''), status = ref(''), group = ref(''), sort = ref('added');
const categoryId = ref<number>(), tagId = ref<number>();
const page = ref(1), total = ref(0);
const pages = new Map<string, number>();
let generation = 0, bookGeneration = 0;
const draft = ref({ name: '', description: '', config: defaultLibraryConfig() });
const moduleOrder = ref(Object.keys(moduleLabels));
const activeConfig = computed(() => activeTab.value === 'settings' ? draft.value.config : (library.value?.config || defaultLibraryConfig()));
const currentList = computed(() => library.value?.lists.find(list => list.id === listId.value));
const selectedGroup = computed(() => library.value?.groups.find(item => String(item.id) === group.value));
const groupOptions = computed(() => [{ value: '', label: '全部书籍' }, { value: '0', label: '未分组' }, ...(library.value?.groups || []).map(g => ({ value: String(g.id), label: g.name }))]);
const hasFilter = computed(() => !!(query.value || status.value || categoryId.value || tagId.value));
const featuredBooks = computed(() => (dashboard.value.featured || []) as LibraryBook[]);
const homeLists = computed(() => (dashboard.value.lists || []).slice(0, activeConfig.value.listLimit) as LibraryCollection[]);
const visibleModules = computed(() => activeConfig.value.modules.filter(key => (dashboard.value[key] || []).length));
const dirty = computed(() => library.value && JSON.stringify(draft.value) !== JSON.stringify({ name: library.value.name, description: library.value.description, config: library.value.config }));
const accentStyle = computed(() => {
    const colors: Record<string, string> = { forest: 'var(--success)', blue: 'var(--info)', brown: 'var(--warning)' };
    return { '--library-accent': colors[activeConfig.value.accent] || 'var(--primary)' };
});
function homeBooks(key: string) { return (dashboard.value[key] || []).slice(0, activeConfig.value.bookLimit) as LibraryBook[]; }
function initializeDraft() {
    if (!library.value)
        return;
    draft.value = clone({ name: library.value.name, description: library.value.description, config: library.value.config });
    moduleOrder.value = [...draft.value.config.modules, ...Object.keys(moduleLabels).filter(key => !draft.value.config.modules.includes(key))];
    conflict.value = false;
}
async function searchLibraries(keyword = '') {
    const result = await personalLibraries.list({ keyword, size: 24 });
    switchOptions.value = [...new Map([...(library.value ? [library.value] : []), ...result.content].map(l => [l.id, l])).values()];
}
async function refresh() {
    const request = ++generation;
    const target = id.value;
    const preserveDraft = activeTab.value === 'settings' && library.value?.id === target && dirty.value;
    busy.value = true;
    failed.value = false;
    try {
        const [result, home] = await Promise.all([personalLibraries.get(target), personalLibraries.home(target)]);
        if (request !== generation || target !== id.value)
            return;
        result.config = { ...defaultLibraryConfig(), ...result.config };
        library.value = result;
        dashboard.value = home;
        if (!preserveDraft)
            initializeDraft();
        await searchLibraries();
        if (activeTab.value === 'shelf' || activeTab.value === 'favorites')
            await loadBooks();
    }
    catch (error: any) {
        if (request === generation) {
            failed.value = true;
            if (error.response?.status === 404) {
                library.value = null;
                message.warning('图书馆或馆内内容已不可用，已返回图书馆列表');
                void router.replace('/libraries');
            }
        }
    }
    finally {
        if (request === generation)
            busy.value = false;
    }
}
function writeQuery() {
    const next: Record<string, string> = {};
    if (query.value)
        next.q = query.value;
    if (status.value)
        next.status = status.value;
    if (group.value)
        next.group = group.value;
    if (categoryId.value)
        next.category = String(categoryId.value);
    if (tagId.value)
        next.tag = String(tagId.value);
    if (sort.value !== 'added')
        next.sort = sort.value;
    if (page.value > 1)
        next.page = String(page.value);
    void router.replace({ path: route.path, query: next });
}
async function loadBooks() {
    if (!library.value)
        return;
    const request = ++bookGeneration, target = id.value;
    const result = await personalLibraries.books(target, {
        keyword: query.value, groupId: group.value || undefined, listId: listId.value,
        favorites: activeTab.value === 'favorites', status: status.value || undefined,
        categoryId: categoryId.value, tagId: tagId.value, sort: sort.value,
        page: page.value - 1, size: library.value.config.pageSize,
    });
    if (request !== bookGeneration || target !== id.value)
        return;
    books.value = result.content;
    total.value = result.totalElements;
    selected.value = [];
    const last = Math.max(1, Math.ceil(result.totalElements / library.value.config.pageSize));
    if (page.value > last) {
        page.value = last;
        writeQuery();
        await loadBooks();
    }
}
async function applyFilters() { page.value = 1; writeQuery(); await loadBooks(); }
function setGroup(value: string) { group.value = value; void applyFilters(); }
function toggleSelect(book: number) { selected.value = selected.value.includes(book) ? selected.value.filter(item => item !== book) : [...selected.value, book]; }
function toggleAll() { selected.value = selected.value.length === books.value.length ? [] : books.value.map(book => book.id); }
function changePage() { writeQuery(); void loadBooks(); }
async function navigate(value: string) {
    pages.set(activeTab.value + (listId.value || ''), page.value);
    const part: Record<string, string> = { home: '', shelf: '/shelf', lists: '/booklists', favorites: '/favorites', settings: '/settings' };
    if (!(value in part))
        return;
    await router.push(`/libraries/${id.value}${part[value]}`);
}
function switchLibrary(value: number) { void router.push(`/libraries/${value}`); }
function openList(list: LibraryCollection) { void router.push(`/libraries/${id.value}/booklists/${list.id}`); }
function openBook(book: LibraryBook) { void router.push({ path: `/books/${book.id}`, query: { libraryId: String(id.value), libraryReturn: route.fullPath } }); }
function moduleMore(key: string) {
    if (key === 'lists')
        void navigate('lists');
    else
        void router.push({ path: `/libraries/${id.value}/shelf`, query: key === 'continuing' ? { status: 'READING' } : {} });
}
async function patch(book: LibraryBook, changes: Record<string, unknown>) {
    await personalLibraries.item(id.value, book.id, changes);
    await refresh();
}
async function batchFavorite(favorite: boolean) {
    await api.put(`/api/libraries/${id.value}/books`, { bookIds: selected.value, favorite });
    await refresh();
}
async function moveSelected(value: string) {
    await api.put(`/api/libraries/${id.value}/books`, { bookIds: selected.value, move: true, groupId: value === '0' ? null : Number(value) });
    await refresh();
}
async function addSelectedToList(value: number) {
    const results = await personalLibraries.add([id.value], selected.value, value);
    const failures = results.filter(item => item.status === 'failed');
    if (failures.length) message.warning(`${failures.length} 本书未能加入：${failures[0].reason}。请刷新后重试。`);
    else message.success('已加入本馆书单');
    await refresh();
}
async function removeSelected(fromList: boolean) {
    const text = fromList ? '只移除书单关系，书籍仍保留在本馆。' : '将同时清除本馆分组、书单、收藏和精选关联。原书、文件及阅读记录保留。';
    if (!await confirm(`确定处理所选 ${selected.value.length} 本书？\n${text}`))
        return;
    await personalLibraries.remove(id.value, selected.value, fromList ? listId.value : undefined);
    await refresh();
}
async function removeOne(book: LibraryBook) { selected.value = [book.id]; await removeSelected(false); }
async function orderBook(book: LibraryBook, direction: number) {
    const index = books.value.findIndex(item => item.id === book.id);
    if (hasFilter.value) return;
    let other = books.value[index + direction];
    if (!other && library.value) {
        const adjacent = await personalLibraries.books(id.value, {
            groupId: group.value || undefined, listId: listId.value,
            favorites: activeTab.value === 'favorites', sort: 'manual',
            page: page.value - 1 + direction, size: library.value.config.pageSize,
        });
        other = direction < 0 ? adjacent.content[adjacent.content.length - 1] : adjacent.content[0];
    }
    if (!other) return;
    await personalLibraries.order(id.value, listId.value ? 'list-books' : 'books', book.id, other.id, listId.value);
    await loadBooks();
}
async function orderFeatured(index: number, direction: number) {
    await personalLibraries.order(id.value, 'featured', featuredBooks.value[index].id, featuredBooks.value[index + direction].id);
    dashboard.value = await personalLibraries.home(id.value);
}
const collectionOpen = ref(false), collectionKind = ref('groups');
const collectionDraft = ref<Partial<LibraryCollection>>({ name: '' });
function editCollection(kind: string, value?: LibraryCollection) {
    collectionKind.value = kind;
    collectionDraft.value = value ? clone(value) : { name: '', description: '', recommended: false };
    collectionOpen.value = true;
}
async function saveCollection() {
    saving.value = true;
    try {
        await personalLibraries.collectionSave(id.value, collectionKind.value, collectionDraft.value);
        collectionOpen.value = false;
        await refresh();
    }
    finally {
        saving.value = false;
    }
}
async function deleteCollection(kind: string, value: LibraryCollection) {
    if (!await confirm(`删除「${value.name}」？${kind === 'groups' ? '成员将回到未分组。' : '书籍仍保留在本馆。'}`))
        return;
    await personalLibraries.collectionDelete(id.value, kind, value.id);
    if (kind === 'groups') {
        group.value = '';
        page.value = 1;
        writeQuery();
    }
    await refresh();
}
async function orderCollection(kind: string, index: number, direction: number) {
    const values = kind === 'groups' ? library.value!.groups : library.value!.lists;
    await personalLibraries.order(id.value, kind, values[index].id, values[index + direction].id);
    const updated = await personalLibraries.get(id.value);
    library.value!.groups = updated.groups;
    library.value!.lists = updated.lists;
}
async function savePreference(key: keyof LibraryConfig, value: any) {
    if (!library.value)
        return;
    try {
        const target = id.value;
        const updated = await personalLibraries.save(target, { ...library.value, config: { ...library.value.config, [key]: value } });
        if (target !== id.value) return;
        library.value = updated;
        if (key === 'pageSize') {
            pages.clear();
            page.value = 1;
            writeQuery();
        }
        await loadBooks();
        initializeDraft();
    }
    catch {
        message.warning('偏好未保存，请刷新并重试');
    }
}
async function saveSettings() {
    if (!library.value || !draft.value.name.trim())
        return;
    saving.value = true;
    try {
        const target = id.value;
        const updated = await personalLibraries.save(target, { ...draft.value, revision: library.value.revision });
        if (target !== id.value) return;
        library.value = updated;
        initializeDraft();
        message.success('图书馆设置已保存');
    }
    catch (error: any) {
        if (error.response?.status === 409)
            conflict.value = true;
    }
    finally {
        saving.value = false;
    }
}
async function reloadDraft() {
    if (dirty.value && !await confirm('重新加载会放弃当前未保存草稿，是否继续？'))
        return;
    library.value = await personalLibraries.get(id.value);
    initializeDraft();
}
async function setTemplate(value: string) {
    if (!await confirm('切换模板会调整首页模块顺序及书架展示方式，保留全部书籍和书单。是否继续？'))
        return;
    draft.value.config.template = value;
    draft.value.config.layout = value === 'editorial' ? 'list' : 'grid';
    moduleOrder.value = value === 'shelf' ? ['featured', 'recent', 'continuing', 'lists'] : value === 'editorial' ? ['lists', 'featured', 'continuing', 'recent'] : Object.keys(moduleLabels);
    draft.value.config.modules = [...moduleOrder.value];
}
function toggleModule(key: string) {
    const selected = new Set(draft.value.config.modules);
    if (selected.has(key))
        selected.delete(key);
    else
        selected.add(key);
    draft.value.config.modules = moduleOrder.value.filter(item => selected.has(item));
}
function moveModule(index: number, direction: number) {
    const next = moduleOrder.value[index + direction];
    moduleOrder.value[index + direction] = moduleOrder.value[index];
    moduleOrder.value[index] = next;
    const selected = new Set(draft.value.config.modules);
    draft.value.config.modules = moduleOrder.value.filter(item => selected.has(item));
}
async function resetAppearance() {
    if (await confirm('恢复默认外观与首页模块？全部馆藏、分组和书单保留；保存设置后生效。')) {
        draft.value.config = defaultLibraryConfig();
        moduleOrder.value = Object.keys(moduleLabels);
    }
}
async function deleteLibrary() {
    if (!await confirm(`删除「${library.value?.name}」？\n${library.value?.bookCount} 本馆藏关联、${library.value?.lists.length} 个书单及分组、收藏、配置将被删除，无法恢复。\n原书、文件及阅读记录均保留。`))
        return;
    await personalLibraries.delete(id.value);
    initializeDraft();
    library.value = null;
    await router.push('/libraries');
}
async function allowLeave() {
    return activeTab.value !== 'settings' || !dirty.value || await confirm('设置尚未保存。确定放弃草稿并离开？');
}
onBeforeRouteLeave(allowLeave);
onBeforeRouteUpdate(async (to, from) => {
    if (to.path !== from.path && !await allowLeave())
        return false;
});
function beforeUnload(event: BeforeUnloadEvent) { if (activeTab.value === 'settings' && dirty.value) {
    event.preventDefault();
    event.returnValue = '';
} }
function hydrateQuery() {
    query.value = String(route.query.q || '');
    status.value = String(route.query.status || '');
    group.value = String(route.query.group || '');
    categoryId.value = route.query.category ? Number(route.query.category) : undefined;
    tagId.value = route.query.tag ? Number(route.query.tag) : undefined;
    sort.value = String(route.query.sort || (activeTab.value === 'favorites' ? 'favorite' : 'added'));
    page.value = Math.max(1, Number(route.query.page) || pages.get(activeTab.value + (listId.value || '')) || 1);
}
watch(() => route.path, async () => {
    generation++;
    bookGeneration++;
    pickerOpen.value = false;
    selected.value = [];
    failed.value = false;
    hydrateQuery();
    await refresh();
    if (activeTab.value === 'settings')
        initializeDraft();
});
onMounted(async () => {
    hydrateQuery();
    window.addEventListener('beforeunload', beforeUnload);
    await refresh();
    const options = await Promise.allSettled([api.get('/api/categories'), api.get('/api/tags')]);
    if (options[0].status === 'fulfilled')
        categories.value = options[0].value.data;
    if (options[1].status === 'fulfilled')
        tags.value = options[1].value.data;
});
onBeforeUnmount(() => { generation++; bookGeneration++; window.removeEventListener('beforeunload', beforeUnload); });
</script>
<style scoped>
.library-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  padding: clamp(24px,4vw,42px);
  margin: 28px 0;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  background: var(--surface-card);
  overflow: hidden;
  border-top: 3px solid var(--library-accent, var(--primary));
}
.library-hero h2 {
  font-size: clamp(28px,3vw,38px);
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.library-hero p {
  color: var(--text-secondary);
  max-width: 540px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.library-hero .library-landscape {
  width: 240px;
  min-width: 120px;
  border-radius: var(--radius-md);
}
.hero-count {
  display: inline-block;
  color: var(--text-secondary);
  font-size: 12px;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 4px 12px;
}
.library-groups, .library-batch {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 20px 0;
}
.library-groups .el-button + .el-button, .library-batch .el-button + .el-button {
  margin-left: 0;
}
.library-batch {
  padding: 15px;
  border-radius: var(--radius-md);
  background: var(--surface-card);
  border: 1px solid var(--border-color);
}
.library-batch .el-select {
  width: 170px;
}
.library-view-toggle {
  margin-left: auto;
}
.library-list-grid {
  display: grid;
  grid-template-columns: repeat(2,minmax(0,1fr));
  gap: 20px;
}
.library-list-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 18px;
  background: var(--surface-card);
  color: var(--text-primary);
  text-align: left;
  padding: 24px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  font: inherit;
  cursor: pointer;
}
.library-list-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
  overflow-wrap: anywhere;
}
.library-list-card p, .library-list-card small {
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}
.library-list-open {
  display: flex;
  align-items: center;
  gap: 20px;
  color: inherit;
  text-align: left;
  border: 0;
  background: transparent;
  font: inherit;
  cursor: pointer;
}
.list-mark {
  font-size: 45px;
  color: var(--primary);
  margin-right: 15px;
}
.library-settings-grid {
  display: grid;
  grid-template-columns: minmax(0,1.1fr) minmax(0,1fr);
  gap: 28px;
  align-items: start;
}
.library-settings-panel {
  padding: 24px;
  background: var(--surface-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  margin-bottom: 24px;
}
.library-settings-panel h3 {
  margin: 0 0 22px;
}
.library-settings-panel .el-select {
  width: 100%;
}
.library-settings-panel .el-form-item :deep(.audio-segmented) {
  width: 100%;
}
.library-setting-switches {
  display: flex;
  flex-wrap: wrap;
}
.library-module-row {
  display: flex;
  gap: 15px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color-light);
}
.library-module-row > span {
  overflow-wrap: anywhere;
}
.library-module-row > div {
  flex-shrink: 0;
}
.library-preview {
  position: sticky;
  top: 20px;
}
.library-preview .library-hero {
  padding: 18px;
  flex-wrap: wrap;
}
.library-preview .library-hero h2 {
  font-size: 24px;
}
.library-preview .library-landscape {
  width: 100%;
  height: 100px;
}
.preview-module {
  margin: 25px 0;
}
.preview-books {
  display: grid;
  grid-template-columns: repeat(3,1fr);
  gap: 12px;
}
.preview-books span {
  height: 120px;
  background: var(--primary-alpha-15);
  border-radius: 4px;
}
.preview-books span:nth-child(2) {
  background: var(--primary-alpha-20);
}
.preview-books.preview-list {
  grid-template-columns: 1fr;
}
.preview-list span {
  height: 35px;
}
.template-shelf .library-hero {
  background: var(--primary-alpha-10);
}
.template-editorial .library-hero {
  border-radius: 0;
  border-right: 0;
  border-left: 0;
  border-bottom: 0;
  background: transparent;
}
.template-editorial .library-hero h2 {
  font-family: "Noto Serif SC", "Songti SC", serif;
}
.background-texture {
  background-image: repeating-linear-gradient(0deg, transparent 0 5px, var(--primary-alpha-10) 5px 6px);
}
@media (max-width: 900px) {
  .library-settings-grid {
    grid-template-columns: 1fr;
  }
  .library-preview {
    position: static;
  }
}
@media (max-width: 600px) {
  .library-hero {
    align-items: flex-start;
    flex-direction: column;
  }
  .library-hero .library-landscape {
    width: 100%;
    height: 110px;
  }
  .library-list-grid {
    grid-template-columns: 1fr;
  }
  .library-view-toggle {
    width: 100%;
    margin-left: 0;
  }
  .library-settings-panel {
    padding: 16px;
  }
  .library-groups {
    gap: 8px;
  }
}
</style>
