<template>
  <div class="shelf-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">我的书架</h1>
        <p class="page-subtitle">管理您的阅读收藏</p>
      </div>
      <div class="header-actions">
        <!-- 视图切换 -->
        <div class="view-toggle">
          <button
            class="view-btn"
            :class="{ active: viewMode === 'grid' }"
            @click="viewMode = 'grid'"
            title="网格视图"
          >
            <span>▦</span>
          </button>
          <button
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            @click="viewMode = 'list'"
            title="列表视图"
          >
            <span>☰</span>
          </button>
        </div>
        <!-- 卡片大小（仅网格模式显示） -->
        <div v-if="viewMode === 'grid'" class="card-size-selector">
          <button
            v-for="size in cardSizes"
            :key="size.value"
            class="size-btn"
            :class="{ active: cardSize === size.value }"
            @click="cardSize = size.value"
            :title="size.label"
          >
            {{ size.icon }}
          </button>
        </div>
        <button class="btn btn-primary" @click="showCreateListDialog = true">
          <span>➕</span>
          <span>创建书单</span>
        </button>
      </div>
    </div>

    <!-- 选项卡 -->
    <div class="tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="handleTabChange(tab.key)"
      >
        <span class="tab-icon">{{ tab.icon }}</span>
        <span>{{ tab.label }}</span>
      </div>
    </div>

    <!-- 书架 -->
    <div v-show="activeTab === 'shelf'" class="tab-content shelf-tab-content">
      <div class="shelf-tab-toolbar">
        <div>
          <h2>我的书架</h2>
          <p>共 {{ shelfOverview.totalBooks }} 本书，可按文件夹整理和手动排序</p>
        </div>
        <button class="btn btn-primary" @click="openCreateShelfGroup">
          <span>📁</span>
          <span>新建分组</span>
        </button>
      </div>

      <div v-if="shelfLoading" class="loading glass">
        <div class="loading-spinner"></div>
        <p>正在加载书架...</p>
      </div>
      <div v-else-if="shelfOverview.totalBooks === 0 && shelfOverview.groups.length === 0" class="empty glass">
        <div class="empty-icon">📚</div>
        <p>书架还是空的，可在书库或书籍详情页点击“加入书架”</p>
        <button class="btn btn-primary" @click="$router.push('/books')">去书库添加</button>
      </div>
      <template v-else>
        <div class="shelf-folder-grid">
          <button
            class="shelf-folder-card shelf-folder-all"
            :class="{ active: selectedShelfGroup === 'all' }"
            @click="selectedShelfGroup = 'all'"
          >
            <span class="shelf-folder-icon">📚</span>
            <strong>全部书籍</strong>
            <small>{{ shelfOverview.totalBooks }} 本</small>
          </button>
          <button
            class="shelf-folder-card shelf-folder-ungrouped"
            :class="{ active: selectedShelfGroup === 'ungrouped' }"
            @click="selectedShelfGroup = 'ungrouped'"
          >
            <span class="shelf-folder-icon">📖</span>
            <strong>未分组</strong>
            <small>{{ shelfOverview.ungroupedBooks.length }} 本</small>
          </button>
          <article
            v-for="(group, groupIndex) in shelfOverview.groups"
            :key="group.id"
            class="shelf-folder-card custom-folder-card"
            :class="{ active: selectedShelfGroup === group.id }"
            :style="{ '--folder-color': group.color }"
            role="button"
            tabindex="0"
            @click="selectedShelfGroup = group.id"
            @keyup.enter="selectedShelfGroup = group.id"
          >
            <div class="folder-card-main">
              <span class="shelf-folder-icon">{{ group.icon }}</span>
              <div class="folder-card-copy">
                <strong>{{ group.name }}</strong>
                <small>{{ group.books.length }} 本</small>
              </div>
            </div>
            <p>{{ group.description || '暂无分组描述' }}</p>
            <div class="folder-card-actions" @click.stop>
              <button :disabled="groupIndex === 0" title="分组前移" @click="moveShelfGroup(groupIndex, -1)">←</button>
              <button :disabled="groupIndex === shelfOverview.groups.length - 1" title="分组后移" @click="moveShelfGroup(groupIndex, 1)">→</button>
              <button title="编辑分组" @click="openEditShelfGroup(group)">✎</button>
              <button class="danger" title="删除分组" @click="deleteShelfGroup(group)">✕</button>
            </div>
          </article>
        </div>

        <div class="shelf-section-heading">
          <div>
            <h3>{{ selectedShelfTitle }}</h3>
            <p>{{ selectedShelfDescription }}</p>
          </div>
          <span>{{ displayedShelfBooks.length }} 本</span>
        </div>

        <div v-if="displayedShelfBooks.length === 0" class="empty glass compact-empty">
          <div class="empty-icon">📂</div>
          <p>这个分组中还没有书籍</p>
        </div>
        <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
          <div
            v-for="(book, bookIndex) in displayedShelfBooks"
            :key="book.id"
            :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
            @click="$router.push(`/books/${book.id}`)"
          >
            <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
              <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
              <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
              <div v-if="viewMode === 'grid'" class="book-cover-actions shelf-book-actions" @click.stop>
                <button
                  v-if="selectedShelfGroup !== 'all'"
                  class="action-btn"
                  :disabled="bookIndex === 0 || shelfActionBookId === book.id"
                  title="向前移动"
                  @click="moveShelfBookOrder(bookIndex, -1)"
                ><span class="action-icon">↑</span></button>
                <button
                  v-if="selectedShelfGroup !== 'all'"
                  class="action-btn"
                  :disabled="bookIndex === displayedShelfBooks.length - 1 || shelfActionBookId === book.id"
                  title="向后移动"
                  @click="moveShelfBookOrder(bookIndex, 1)"
                ><span class="action-icon">↓</span></button>
                <el-dropdown trigger="click" @command="command => moveBookToShelfGroup(book, command)">
                  <button class="action-btn" title="移动到分组" @click.stop>
                    <span class="action-icon">📁</span>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="ungrouped" :disabled="!book.shelfGroupId">未分组</el-dropdown-item>
                      <el-dropdown-item
                        v-for="group in shelfOverview.groups"
                        :key="group.id"
                        :command="String(group.id)"
                        :disabled="book.shelfGroupId === group.id"
                      >{{ group.icon }} {{ group.name }}</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <button
                  class="action-btn remove-reading-action"
                  :disabled="shelfActionBookId === book.id"
                  title="移出书架"
                  @click="removeShelfBook(book)"
                ><span class="action-icon">✕</span></button>
              </div>
            </div>
            <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
              <div class="book-title">{{ book.title }}</div>
              <div class="book-author">{{ book.author || '未知作者' }}</div>
              <div v-if="viewMode === 'list'" class="book-meta">
                <span class="book-format">{{ book.format?.toUpperCase() }}</span>
                <span>加入于 {{ formatShelfDate(book.shelfAddedAt) }}</span>
              </div>
            </div>
            <div v-if="viewMode === 'list'" class="shelf-list-actions" @click.stop>
              <button v-if="selectedShelfGroup !== 'all'" class="btn btn-text" :disabled="bookIndex === 0" @click="moveShelfBookOrder(bookIndex, -1)">上移</button>
              <button v-if="selectedShelfGroup !== 'all'" class="btn btn-text" :disabled="bookIndex === displayedShelfBooks.length - 1" @click="moveShelfBookOrder(bookIndex, 1)">下移</button>
              <el-dropdown trigger="click" @command="command => moveBookToShelfGroup(book, command)">
                <button class="btn btn-text" @click.stop>移动分组</button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="ungrouped" :disabled="!book.shelfGroupId">未分组</el-dropdown-item>
                    <el-dropdown-item v-for="group in shelfOverview.groups" :key="group.id" :command="String(group.id)" :disabled="book.shelfGroupId === group.id">
                      {{ group.icon }} {{ group.name }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <button class="btn btn-text remove-reading-list-action" @click="removeShelfBook(book)">移出书架</button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 收藏 -->
    <div v-show="activeTab === 'favorite'" class="tab-content">
      <div v-if="favoriteLoading" class="loading glass">
        <div class="loading-spinner"></div>
        <p>正在加载...</p>
      </div>
      <div v-else-if="favoriteBooks.length === 0" class="empty glass">
        <div class="empty-icon">⭐</div>
        <p>暂无收藏的书籍</p>
        <button class="btn btn-primary" @click="$router.push('/books')">去书库看看</button>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in favoriteBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 正在阅读 -->
    <div v-show="activeTab === 'reading'" class="tab-content">
      <div v-if="readingLoading" class="loading glass">
        <div class="loading-spinner"></div>
        <p>正在加载...</p>
      </div>
      <div v-else-if="readingBooks.length === 0" class="empty glass">
        <div class="empty-icon">📖</div>
        <p>暂无正在阅读的书籍</p>
        <button class="btn btn-primary" @click="$router.push('/books')">去书库看看</button>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in readingBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/reader/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
            <div v-if="viewMode === 'grid'" class="book-cover-actions" @click.stop>
              <button
                class="action-btn remove-reading-action"
                :disabled="removingReadingIds.has(book.id)"
                aria-label="移除正在阅读"
                title="移除正在阅读"
                @click.stop="removeFromReading(book)"
              >
                <span class="action-icon">{{ removingReadingIds.has(book.id) ? '…' : '✕' }}</span>
              </button>
            </div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
          <button
            v-if="viewMode === 'list'"
            class="btn btn-text remove-reading-list-action"
            :disabled="removingReadingIds.has(book.id)"
            @click.stop="removeFromReading(book)"
          >
            {{ removingReadingIds.has(book.id) ? '处理中...' : '移除正在阅读' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 已读完 -->
    <div v-show="activeTab === 'finished'" class="tab-content">
      <div v-if="finishedBooks.length === 0" class="empty glass">
        <div class="empty-icon">✅</div>
        <p>暂无已读完的书籍</p>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in finishedBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 想读 -->
    <div v-show="activeTab === 'wanted'" class="tab-content">
      <div v-if="wantedLoading" class="loading glass">
        <div class="loading-spinner"></div>
        <p>正在加载...</p>
      </div>
      <div v-else-if="wantedBooks.length === 0" class="empty glass">
        <div class="empty-icon">📝</div>
        <p>暂无想读的书籍</p>
        <button class="btn btn-primary" @click="$router.push('/books')">去书库看看</button>
      </div>
      <div v-else :class="[viewMode === 'grid' ? 'books-grid' : 'books-list', `card-${cardSize}`]">
        <div
          v-for="book in wantedBooks"
          :key="book.id"
          :class="viewMode === 'grid' ? 'book-card glass' : 'book-list-item glass'"
          @click="$router.push(`/books/${book.id}`)"
        >
          <div :class="viewMode === 'grid' ? 'book-cover' : 'book-list-cover'">
            <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
            <div v-else class="no-cover">{{ book.title.charAt(0) }}</div>
          </div>
          <div :class="viewMode === 'grid' ? 'book-info' : 'book-list-info'">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="viewMode === 'list'" class="book-meta">
              <span class="book-format">{{ book.format?.toUpperCase() }}</span>
              <span v-if="book.publisher">{{ book.publisher }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 我的书单 -->
    <div v-show="activeTab === 'lists'" class="tab-content">
      <div v-if="bookLists.length === 0" class="empty glass">
        <div class="empty-icon">📚</div>
        <p>暂无书单</p>
        <button class="btn btn-primary" @click="showCreateListDialog = true">创建书单</button>
      </div>
      <div v-else class="book-lists">
        <div
          v-for="list in bookLists"
          :key="list.id"
          class="list-card glass"
          @click="handleViewList(list)"
        >
          <div class="list-header">
            <div class="list-name">{{ list.name }}</div>
            <div class="list-count tag">{{ list.bookCount }} 本书</div>
          </div>
          <div class="list-description">{{ list.description || '暂无描述' }}</div>
          <div class="list-books">
            <div
              v-for="book in list.books?.slice(0, 4)"
              :key="book.id"
              class="list-book-cover"
            >
              <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="封面" />
              <div v-else class="no-cover-small">{{ book.title.charAt(0) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建书单对话框 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showCreateListDialog" class="dialog-overlay" @click.self="showCreateListDialog = false">
          <div class="dialog">
            <div class="dialog-header">
              <span>📚 创建书单</span>
              <button class="dialog-close" @click="showCreateListDialog = false">✕</button>
            </div>
            <div class="dialog-body">
              <div class="form-group">
                <label class="form-label">书单名称</label>
                <input v-model="newListForm.name" type="text" class="input" placeholder="请输入书单名称" />
              </div>
              <div class="form-group">
                <label class="form-label">描述 <span class="optional">（可选）</span></label>
                <textarea v-model="newListForm.description" class="textarea" placeholder="请输入书单描述"></textarea>
              </div>
            </div>
            <div class="dialog-footer">
              <button class="btn" @click="showCreateListDialog = false">取消</button>
              <button class="btn btn-primary" @click="handleCreateList">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <el-dialog
      v-model="showShelfGroupDialog"
      append-to-body
      :title="editingShelfGroup ? '编辑书架分组' : '新建书架分组'"
      width="min(520px, 92vw)"
    >
      <el-form label-position="top">
        <el-form-item label="分组名称" required>
          <el-input v-model="shelfGroupForm.name" maxlength="80" show-word-limit placeholder="例如：近期必读" />
        </el-form-item>
        <el-form-item label="分组描述">
          <el-input v-model="shelfGroupForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="介绍这个分组收录的内容" />
        </el-form-item>
        <div class="group-appearance-fields">
          <el-form-item label="分组图标">
            <el-select v-model="shelfGroupForm.icon">
              <el-option v-for="icon in shelfGroupIcons" :key="icon" :label="icon" :value="icon" />
            </el-select>
          </el-form-item>
          <el-form-item label="主题颜色">
            <el-color-picker v-model="shelfGroupForm.color" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <button class="btn" @click="showShelfGroupDialog = false">取消</button>
        <button class="btn btn-primary" :disabled="savingShelfGroup" @click="saveShelfGroup">
          {{ savingShelfGroup ? '保存中...' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message, confirm } from '@/utils/message'
import { useBookStore } from '@/stores/book'
import api from '@/utils/api'
import { getCoverUrl } from '@/utils/cover'
import type { Book } from '@/stores/book'

const router = useRouter()
const bookStore = useBookStore()

const STORAGE_KEY = 'shelf-settings'

const tabs = [
  { key: 'shelf', label: '书架', icon: '📚' },
  { key: 'favorite', label: '收藏', icon: '⭐' },
  { key: 'reading', label: '正在阅读', icon: '📖' },
  { key: 'finished', label: '已读完', icon: '✅' },
  { key: 'wanted', label: '想读', icon: '📝' },
  { key: 'lists', label: '我的书单', icon: '📚' },
]

const cardSizes = [
  { value: 'small', label: '小', icon: '▪' },
  { value: 'medium', label: '中', icon: '▫' },
  { value: 'large', label: '大', icon: '◻' },
]

interface ShelfGroup {
  id: number
  name: string
  description?: string
  icon: string
  color: string
  sortOrder: number
  books: Book[]
}

interface ShelfOverview {
  ungroupedBooks: Book[]
  groups: ShelfGroup[]
  totalBooks: number
}

const activeTab = ref('shelf')
const showCreateListDialog = ref(false)
const bookLists = ref<any[]>([])
const favoriteBooks = ref<Book[]>([])
const favoriteLoading = ref(false)
const wantedBooks = ref<Book[]>([])
const wantedLoading = ref(false)
const readingBooks = ref<Book[]>([])
const readingLoading = ref(false)
const removingReadingIds = ref(new Set<number>())
const shelfOverview = ref<ShelfOverview>({ ungroupedBooks: [], groups: [], totalBooks: 0 })
const shelfLoading = ref(false)
const selectedShelfGroup = ref<'all' | 'ungrouped' | number>('all')
const shelfActionBookId = ref<number | null>(null)
const showShelfGroupDialog = ref(false)
const savingShelfGroup = ref(false)
const editingShelfGroup = ref<ShelfGroup | null>(null)
const shelfGroupIcons = ['📁', '📚', '⭐', '❤️', '🎯', '💡', '🌙', '☕', '🧠', '🚀']
const shelfGroupForm = ref({ name: '', description: '', icon: '📁', color: '#4f8cff' })
const viewMode = ref<'grid' | 'list'>('grid')
const cardSize = ref<'small' | 'medium' | 'large'>('medium')

const newListForm = ref({
  name: '',
  description: '',
})

const finishedBooks = computed(() => bookStore.books.filter((b) => b.readingStatus === 'FINISHED'))
const displayedShelfBooks = computed(() => {
  if (selectedShelfGroup.value === 'ungrouped') return shelfOverview.value.ungroupedBooks
  if (typeof selectedShelfGroup.value === 'number') {
    return shelfOverview.value.groups.find(group => group.id === selectedShelfGroup.value)?.books || []
  }
  return [...shelfOverview.value.ungroupedBooks, ...shelfOverview.value.groups.flatMap(group => group.books)]
    .sort((left, right) => new Date(right.shelfAddedAt || 0).getTime() - new Date(left.shelfAddedAt || 0).getTime())
})
const selectedShelfTitle = computed(() => {
  if (selectedShelfGroup.value === 'all') return '全部书籍'
  if (selectedShelfGroup.value === 'ungrouped') return '未分组'
  return shelfOverview.value.groups.find(group => group.id === selectedShelfGroup.value)?.name || '书架分组'
})
const selectedShelfDescription = computed(() => {
  if (selectedShelfGroup.value === 'all') return '默认按照加入书架的时间倒序展示'
  if (selectedShelfGroup.value === 'ungrouped') return '尚未放入自定义分组的书籍，可使用上移、下移手动调整顺序'
  return shelfOverview.value.groups.find(group => group.id === selectedShelfGroup.value)?.description
    || '可使用上移、下移手动调整组内顺序'
})

// 加载设置
const loadSettings = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const settings = JSON.parse(saved)
      if (settings.viewMode) viewMode.value = settings.viewMode
      if (settings.cardSize) cardSize.value = settings.cardSize
    }
  } catch (e) { /* ignore */ }
}

// 保存设置
const saveSettings = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      viewMode: viewMode.value,
      cardSize: cardSize.value,
    }))
  } catch (e) { /* ignore */ }
}

// 监听设置变化
watch([viewMode, cardSize], saveSettings)

const loadBooks = async () => {
  await bookStore.fetchBooks(0, 100, 'createdAt', 'desc')
}

const loadMarkedBooks = async (endpoint: 'favorites' | 'wanted') => {
  const books: Book[] = []
  let page = 0
  let totalPages = 1
  do {
    const response = await api.get(`/api/books/${endpoint}`, {
      params: { page, size: 100 },
    })
    books.push(...response.data.content)
    totalPages = response.data.totalPages
    page += 1
  } while (page < totalPages)
  return books
}

const loadFavoriteBooks = async () => {
  if (favoriteLoading.value) return
  favoriteLoading.value = true
  try {
    favoriteBooks.value = await loadMarkedBooks('favorites')
  } catch (error) {
    console.error('Failed to load favorite books:', error)
    message.error('收藏书籍加载失败')
  } finally {
    favoriteLoading.value = false
  }
}

const loadWantedBooks = async () => {
  if (wantedLoading.value) return
  wantedLoading.value = true
  try {
    wantedBooks.value = await loadMarkedBooks('wanted')
  } catch (error) {
    console.error('Failed to load wanted books:', error)
    message.error('想读书籍加载失败')
  } finally {
    wantedLoading.value = false
  }
}

const loadShelf = async () => {
  if (shelfLoading.value) return
  shelfLoading.value = true
  try {
    const response = await api.get<ShelfOverview>('/api/shelf')
    shelfOverview.value = response.data
    if (typeof selectedShelfGroup.value === 'number'
      && !response.data.groups.some(group => group.id === selectedShelfGroup.value)) {
      selectedShelfGroup.value = 'all'
    }
  } catch (error) {
    console.error('Failed to load shelf:', error)
    message.error('书架加载失败')
  } finally {
    shelfLoading.value = false
  }
}

const formatShelfDate = (value?: string) => {
  if (!value) return '--'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return '--'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(date)
}

const openCreateShelfGroup = () => {
  editingShelfGroup.value = null
  shelfGroupForm.value = { name: '', description: '', icon: '📁', color: '#4f8cff' }
  showShelfGroupDialog.value = true
}

const openEditShelfGroup = (group: ShelfGroup) => {
  editingShelfGroup.value = group
  shelfGroupForm.value = {
    name: group.name,
    description: group.description || '',
    icon: group.icon,
    color: group.color,
  }
  showShelfGroupDialog.value = true
}

const saveShelfGroup = async () => {
  if (!shelfGroupForm.value.name.trim()) {
    message.warning('请输入分组名称')
    return
  }
  savingShelfGroup.value = true
  try {
    if (editingShelfGroup.value) {
      await api.put(`/api/shelf/groups/${editingShelfGroup.value.id}`, shelfGroupForm.value)
      message.success('分组已更新')
    } else {
      await api.post('/api/shelf/groups', shelfGroupForm.value)
      message.success('分组已创建')
    }
    showShelfGroupDialog.value = false
    await loadShelf()
  } catch (error: any) {
    message.error(error.response?.data?.message || '分组保存失败')
  } finally {
    savingShelfGroup.value = false
  }
}

const deleteShelfGroup = async (group: ShelfGroup) => {
  const approved = await confirm(
    `确定删除分组“${group.name}”吗？\n\n分组中的书籍会回到“未分组”，不会被移出书架。`,
    '删除书架分组',
  )
  if (!approved) return
  try {
    await api.delete(`/api/shelf/groups/${group.id}`)
    if (selectedShelfGroup.value === group.id) selectedShelfGroup.value = 'ungrouped'
    await loadShelf()
    message.success('分组已删除，书籍已移到未分组')
  } catch (error: any) {
    message.error(error.response?.data?.message || '分组删除失败')
  }
}

const moveShelfGroup = async (index: number, offset: number) => {
  const target = index + offset
  if (target < 0 || target >= shelfOverview.value.groups.length) return
  const previous = [...shelfOverview.value.groups]
  const reordered = [...previous]
  ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
  shelfOverview.value.groups = reordered
  try {
    await api.put('/api/shelf/groups/order', { groupIds: reordered.map(group => group.id) })
  } catch (error) {
    shelfOverview.value.groups = previous
    message.error('分组排序失败')
  }
}

const replaceSelectedShelfBooks = (books: Book[]) => {
  if (selectedShelfGroup.value === 'ungrouped') {
    shelfOverview.value.ungroupedBooks = books
    return
  }
  if (typeof selectedShelfGroup.value === 'number') {
    const group = shelfOverview.value.groups.find(item => item.id === selectedShelfGroup.value)
    if (group) group.books = books
  }
}

const moveShelfBookOrder = async (index: number, offset: number) => {
  if (selectedShelfGroup.value === 'all') return
  const target = index + offset
  const current = [...displayedShelfBooks.value]
  if (target < 0 || target >= current.length) return
  const previous = [...current]
  ;[current[index], current[target]] = [current[target], current[index]]
  replaceSelectedShelfBooks(current)
  shelfActionBookId.value = current[target].id
  try {
    await api.put('/api/shelf/books/order', {
      groupId: selectedShelfGroup.value === 'ungrouped' ? null : selectedShelfGroup.value,
      bookIds: current.map(book => book.id),
    })
  } catch (error) {
    replaceSelectedShelfBooks(previous)
    message.error('书籍排序失败')
  } finally {
    shelfActionBookId.value = null
  }
}

const moveBookToShelfGroup = async (book: Book, command: string | number | object) => {
  const value = String(command)
  const groupId = value === 'ungrouped' ? null : Number(value)
  shelfActionBookId.value = book.id
  try {
    await api.put(`/api/shelf/books/${book.id}/group`, { groupId })
    await loadShelf()
    message.success('书籍分组已更新')
  } catch (error) {
    message.error('移动书籍失败')
  } finally {
    shelfActionBookId.value = null
  }
}

const removeShelfBook = async (book: Book) => {
  shelfActionBookId.value = book.id
  try {
    await bookStore.removeFromShelf(book.id)
    await loadShelf()
    message.success(`已将《${book.title}》移出书架`)
  } catch (error) {
    message.error('移出书架失败')
  } finally {
    shelfActionBookId.value = null
  }
}

const loadReadingBooks = async () => {
  if (readingLoading.value) return
  readingLoading.value = true
  try {
    const books: Book[] = []
    let page = 0
    let totalPages = 1
    do {
      const response = await api.get('/api/books', {
        params: { page, size: 100, sortBy: 'updatedAt', sortDir: 'desc', status: 'READING' },
      })
      books.push(...response.data.content)
      totalPages = response.data.totalPages
      page += 1
    } while (page < totalPages)
    readingBooks.value = books
  } catch (error) {
    console.error('Failed to load reading books:', error)
    message.error('正在阅读书籍加载失败')
  } finally {
    readingLoading.value = false
  }
}

const removeFromReading = async (book: Book) => {
  if (removingReadingIds.value.has(book.id)) return
  removingReadingIds.value.add(book.id)
  try {
    await bookStore.updateReadingStatus(book.id, 'UNREADING')
    readingBooks.value = readingBooks.value.filter(item => item.id !== book.id)
    message.success(`已将《${book.title}》移出正在阅读`)
  } catch (error) {
    console.error('Failed to remove reading book:', error)
    message.error('移除正在阅读失败')
  } finally {
    removingReadingIds.value.delete(book.id)
  }
}

const loadBookLists = async () => {
  try {
    const response = await api.get('/api/booklists')
    bookLists.value = response.data
  } catch (error) {
    console.error('Failed to load book lists:', error)
  }
}

const handleTabChange = (tab: string) => {
  activeTab.value = tab
  if (tab === 'shelf') {
    loadShelf()
  } else if (tab === 'favorite') {
    loadFavoriteBooks()
  } else if (tab === 'wanted') {
    loadWantedBooks()
  } else if (tab === 'lists') {
    loadBookLists()
  } else if (tab === 'reading') {
    loadReadingBooks()
  }
}

const handleCreateList = async () => {
  if (!newListForm.value.name.trim()) {
    message.warning('请输入书单名称')
    return
  }

  try {
    await api.post('/api/booklists', newListForm.value)
    message.success('创建成功')
    showCreateListDialog.value = false
    newListForm.value = { name: '', description: '' }
    loadBookLists()
  } catch (error) {
    message.error('创建失败')
  }
}

const handleViewList = (list: any) => {
  router.push(`/booklists/${list.id}`)
}

onMounted(() => {
  loadSettings()
  loadBooks()
  loadShelf()
  loadFavoriteBooks()
  loadWantedBooks()
  loadReadingBooks()
})
</script>

<style scoped>
.shelf-view {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-xl);
}

.page-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: var(--text-on-page-bg);
  text-shadow: var(--text-on-page-bg-shadow);
  margin-bottom: var(--spacing-sm);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--text-on-page-bg-secondary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

/* 视图切换 */
.view-toggle {
  display: flex;
  background: var(--surface-card);
  border: var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.view-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 16px;
}

.view-btn:hover {
  background: var(--bg-secondary);
}

.view-btn.active {
  background: var(--primary);
  color: white;
}

/* 卡片大小选择器 */
.card-size-selector {
  display: flex;
  background: var(--surface-card);
  border: var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.size-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 12px;
}

.size-btn:hover {
  background: var(--bg-secondary);
}

.size-btn.active {
  background: var(--primary);
  color: white;
}

.shelf-tab-content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.shelf-tab-toolbar,
.shelf-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.shelf-tab-toolbar h2,
.shelf-section-heading h3 {
  margin: 0 0 4px;
  color: var(--text-on-page-bg);
}

.shelf-tab-toolbar p,
.shelf-section-heading p {
  margin: 0;
  color: var(--text-on-page-bg-secondary);
  font-size: var(--font-size-sm);
}

.shelf-section-heading > span {
  flex: 0 0 auto;
  padding: 5px 10px;
  color: var(--text-on-page-bg-secondary);
  background: var(--surface-card);
  border-radius: 999px;
  font-size: var(--font-size-sm);
}

.shelf-folder-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: var(--spacing-md);
}

.shelf-folder-card {
  position: relative;
  min-height: 132px;
  padding: var(--spacing-md);
  overflow: hidden;
  color: var(--text-primary);
  text-align: left;
  background: var(--surface-card);
  border: 2px solid transparent;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

button.shelf-folder-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  font: inherit;
}

.shelf-folder-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-3px);
}

.shelf-folder-card.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-20);
}

.shelf-folder-all {
  background: linear-gradient(135deg, color-mix(in srgb, var(--primary) 18%, var(--surface-card)), var(--surface-card));
}

.shelf-folder-ungrouped {
  background: linear-gradient(135deg, color-mix(in srgb, var(--warning) 16%, var(--surface-card)), var(--surface-card));
}

.custom-folder-card {
  background: linear-gradient(135deg, color-mix(in srgb, var(--folder-color) 24%, var(--surface-card)), var(--surface-card));
}

.shelf-folder-icon {
  font-size: 30px;
  line-height: 1;
}

.shelf-folder-card strong {
  margin-top: 10px;
  overflow: hidden;
  font-size: var(--font-size-base);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shelf-folder-card small {
  margin-top: 3px;
  color: var(--text-secondary);
}

.folder-card-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.folder-card-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.folder-card-copy strong {
  margin-top: 0;
}

.custom-folder-card > p {
  display: -webkit-box;
  min-height: 34px;
  margin: 12px 0 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.folder-card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.custom-folder-card:hover .folder-card-actions,
.custom-folder-card:focus-within .folder-card-actions {
  opacity: 1;
}

.folder-card-actions button {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-primary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-color);
  border-radius: 50%;
  cursor: pointer;
  place-items: center;
}

.folder-card-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.folder-card-actions button.danger {
  color: var(--danger);
}

.compact-empty {
  padding-block: var(--spacing-lg);
}

.compact-empty .empty-icon {
  font-size: 42px;
}

.shelf-list-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
}

.group-appearance-fields {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(120px, 1fr);
  gap: var(--spacing-lg);
}

/* 空状态 */
.empty {
  text-align: center;
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

/* 书籍网格 */
.books-grid {
  display: grid;
  gap: var(--spacing-md);
}

/* 小卡片 */
.books-grid.card-small {
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
}

.books-grid.card-small .book-cover {
  height: 160px;
}

.books-grid.card-small .no-cover {
  font-size: 36px;
}

.books-grid.card-small .book-info {
  padding: var(--spacing-xs) var(--spacing-sm);
}

.books-grid.card-small .book-title {
  font-size: var(--font-size-xs);
}

.books-grid.card-small .shelf-book-actions {
  gap: 3px;
  padding: 6px;
}

.books-grid.card-small .shelf-book-actions .action-btn {
  flex-basis: 27px;
  width: 27px;
  height: 27px;
  font-size: 12px;
}

.books-grid.card-small .book-author {
  font-size: 10px;
}

/* 中卡片（默认） */
.books-grid.card-medium {
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
}

.books-grid.card-medium .book-cover {
  height: 220px;
}

.books-grid.card-medium .no-cover {
  font-size: 48px;
}

/* 大卡片 */
.books-grid.card-large {
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
}

.books-grid.card-large .book-cover {
  height: 300px;
}

.books-grid.card-large .no-cover {
  font-size: 64px;
}

.books-grid.card-large .book-info {
  padding: var(--spacing-md) var(--spacing-lg);
}

.books-grid.card-large .book-title {
  font-size: var(--font-size-lg);
}

.books-grid.card-large .book-author {
  font-size: var(--font-size-base);
}

.book-card {
  position: relative;
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.book-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
}

.book-cover {
  height: 200px;
  position: relative;
  overflow: hidden;
}

.book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.book-cover-actions {
  position: absolute;
  z-index: 3;
  top: 0;
  right: 0;
  left: 0;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  padding: 9px 10px;
  background: rgba(15, 23, 42, 0.72);
  border-bottom: 1px solid rgba(255, 255, 255, 0.22);
  opacity: 0;
  transform: translateY(-105%);
  transition: transform 0.24s ease, opacity 0.2s ease;
}

.book-card:hover .book-cover-actions,
.book-card:focus-within .book-cover-actions {
  opacity: 1;
  transform: translateY(0);
}

.action-btn {
  position: relative;
  display: flex;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.96);
  font-size: 14px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.82);
  border-color: rgba(254, 202, 202, 0.9);
  transform: translateY(-1px);
}

.action-btn:disabled {
  cursor: wait;
  opacity: 0.55;
}

.action-icon {
  position: relative;
  z-index: 1;
}

.remove-reading-list-action {
  flex: 0 0 auto;
  color: var(--danger);
  white-space: nowrap;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  gap: var(--spacing-sm);
  color: var(--text-secondary);
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: shelf-spin 0.8s linear infinite;
}

@keyframes shelf-spin {
  to { transform: rotate(360deg); }
}

.no-cover {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 48px;
  font-weight: 600;
}

.book-info {
  padding: var(--spacing-sm) var(--spacing-md);
}

.book-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-author {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

/* 书籍列表 */
.books-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.book-list-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.book-list-item:hover {
  transform: translateX(4px);
  box-shadow: var(--shadow-md);
}

.book-list-cover {
  width: 50px;
  height: 70px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
}

.book-list-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.book-list-cover .no-cover {
  font-size: 24px;
}

.book-list-info {
  flex: 1;
  min-width: 0;
}

.book-list-info .book-title {
  font-size: var(--font-size-base);
  margin-bottom: var(--spacing-xs);
}

.book-list-info .book-author {
  font-size: var(--font-size-sm);
}

.book-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.book-format {
  background: var(--bg-secondary);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-weight: 500;
}

/* 书单网格 */
.book-lists {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--spacing-lg);
}

.list-card {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.list-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.list-name {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.list-count {
  font-size: var(--font-size-xs);
}

.list-description {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
  line-height: 1.5;
}

.list-books {
  display: flex;
  gap: var(--spacing-sm);
}

.list-book-cover {
  width: 50px;
  height: 70px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.list-book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-cover-small {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.optional {
  font-weight: 400;
  color: var(--text-tertiary);
}

/* 弹窗样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.dialog {
  width: 420px;
  max-width: 90vw;
  background: var(--surface-elevated);
  border-radius: var(--radius-xl);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  font-weight: 600;
  font-size: var(--font-size-lg);
}

.dialog-close {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  border: none;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.dialog-close:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.dialog-body {
  padding: var(--spacing-lg);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.form-label {
  display: block;
  margin-bottom: var(--spacing-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.input,
.textarea {
  width: 100%;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: var(--font-size-base);
  transition: all var(--transition-fast);
  box-sizing: border-box;
}

.input::placeholder,
.textarea::placeholder {
  color: var(--text-tertiary);
}

.input:focus,
.textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-20);
}

.textarea {
  min-height: 80px;
  resize: vertical;
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: var(--spacing-md);
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .tabs {
    flex-wrap: wrap;
    justify-content: center;
  }

  .tab-item {
    padding: 8px 12px;
    font-size: var(--font-size-xs);
  }

  .shelf-tab-toolbar,
  .shelf-section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .shelf-folder-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .folder-card-actions {
    opacity: 1;
  }

  .shelf-list-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .group-appearance-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

@media (hover: none), (pointer: coarse) {
  .book-cover-actions {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .book-cover-actions {
    transition: none;
  }
}
</style>
