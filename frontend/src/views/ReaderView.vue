<template>
  <div class="reader-view" :class="{ 'fullscreen-mode': isFullscreen }">
    <!-- 加载中 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 阅读器内容 -->
    <div v-else-if="book" class="reader-content">
      <!-- 阅读器头部 -->
      <header class="reader-header glass" v-show="!isFullscreen">
        <button class="back-btn" @click="goBack">
          <span>‹</span>
          <span>返回</span>
        </button>
        <div class="reader-title">{{ book.title }}</div>
        <div class="reader-actions">
          <button
            v-if="book.format === 'epub' || tocItems.length > 0"
            class="btn btn-icon"
            :class="{ active: showToc }"
            @click="togglePanel('toc')"
            title="目录"
          >
            <span>☰</span>
          </button>
          <button
            class="btn btn-icon"
            :class="{ active: showBookmarks }"
            @click="togglePanel('bookmarks')"
            title="书签"
          >
            <span>📑</span>
          </button>
          <button
            class="btn btn-icon"
            :class="{ active: showHighlights }"
            @click="togglePanel('highlights')"
            title="高亮"
          >
            <span>🖍️</span>
          </button>
          <button class="btn btn-icon" @click="showSettings = true" title="设置">
            <span>⚙️</span>
          </button>
          <button class="btn btn-icon" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏'">
            <span>{{ isFullscreen ? '⊡' : '⊞' }}</span>
          </button>
        </div>
      </header>

      <div class="reader-body-wrapper">
        <!-- 左侧面板：目录/书签/高亮 -->
        <Transition name="slide-left">
          <div v-if="showSidePanel" class="side-panel glass">
            <!-- 面板标签页 -->
            <div class="panel-tabs">
              <button
                class="tab-btn"
                :class="{ active: activeTab === 'toc' }"
                @click="activeTab = 'toc'"
              >
                目录
              </button>
              <button
                class="tab-btn"
                :class="{ active: activeTab === 'bookmarks' }"
                @click="activeTab = 'bookmarks'"
              >
                书签
              </button>
              <button
                class="tab-btn"
                :class="{ active: activeTab === 'highlights' }"
                @click="activeTab = 'highlights'"
              >
                高亮
              </button>
              <button class="btn btn-icon btn-small close-panel" @click="closeAllPanels">✕</button>
            </div>

            <!-- 目录内容 -->
            <div v-if="activeTab === 'toc'" class="panel-content">
              <div class="toc-header">
                <span>📑 章节目录</span>
                <span class="tag">{{ tocItems.length }} 章</span>
              </div>
              <div class="toc-list">
                <div
                  v-for="(item, index) in tocItems"
                  :key="index"
                  class="toc-item"
                  :class="{ active: isCurrentTocItem(item) }"
                  @click="goToTocItem(item)"
                >
                  <span class="toc-index">{{ index + 1 }}</span>
                  <span class="toc-title">{{ item.label }}</span>
                </div>
              </div>
            </div>

            <!-- 书签内容 -->
            <div v-if="activeTab === 'bookmarks'" class="panel-content">
              <div class="bookmarks-header">
                <span>📑 我的书签</span>
                <button class="btn btn-primary btn-small" @click="handleAddBookmark">
                  + 添加书签
                </button>
              </div>
              <div v-if="bookmarks.length === 0" class="empty-panel">
                <div class="empty-icon">📑</div>
                <p>暂无书签</p>
                <p class="empty-hint">点击上方按钮添加当前阅读位置</p>
              </div>
              <div v-else class="bookmarks-list">
                <div
                  v-for="bookmark in bookmarks"
                  :key="bookmark.id"
                  class="bookmark-item"
                  @click="handleGotoBookmark(bookmark)"
                >
                  <div class="bookmark-icon">🔖</div>
                  <div class="bookmark-info">
                    <div class="bookmark-title">{{ bookmark.title || '书签' }}</div>
                    <div class="bookmark-meta">
                      <span>{{ bookmark.chapter || '未知章节' }}</span>
                      <span>·</span>
                      <span>{{ formatTime(bookmark.createdAt) }}</span>
                    </div>
                  </div>
                  <button class="btn btn-icon btn-small" @click.stop="handleDeleteBookmark(bookmark)">
                    <span>🗑️</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- 高亮内容 -->
            <div v-if="activeTab === 'highlights'" class="panel-content">
              <div class="highlights-header">
                <span>🖍️ 高亮与笔记</span>
                <span class="tag">{{ highlights.length }} 条</span>
              </div>
              <div v-if="highlights.length === 0" class="empty-panel">
                <div class="empty-icon">🖍️</div>
                <p>暂无高亮内容</p>
                <p class="empty-hint">选中文本后可添加高亮或笔记</p>
              </div>
              <div v-else class="highlights-list">
                <div
                  v-for="highlight in highlights"
                  :key="highlight.id"
                  class="highlight-item"
                  :style="{ borderLeftColor: highlight.color }"
                >
                  <div class="highlight-content">
                    <div class="highlight-text">"{{ highlight.text }}"</div>
                    <div v-if="highlight.note" class="highlight-note">
                      <span class="note-icon">✏️</span>
                      <span>{{ highlight.note }}</span>
                    </div>
                  </div>
                  <div class="highlight-meta">
                    <span>{{ highlight.chapter }}</span>
                    <span>·</span>
                    <span>{{ formatTime(highlight.createdAt) }}</span>
                  </div>
                  <div class="highlight-actions">
                    <button class="btn btn-text" @click="handleGotoHighlight(highlight)">定位</button>
                    <button class="btn btn-text btn-danger" @click="handleDeleteHighlight(highlight)">删除</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 阅读器内容 -->
        <div class="reader-body" :class="{ 'pagination-mode': settings.paginationMode }" :style="readerStyle" @scroll="handleScroll">
          <!-- EPUB 阅读器 -->
          <div v-if="book.format === 'epub'" ref="epubContainer" class="epub-container"></div>

          <!-- TXT / MD 阅读器 -->
          <div v-else-if="book.format === 'txt' || book.format === 'md'" class="reader-text">
            <template v-for="(paragraph, index) in currentPageContent" :key="index">
              <div v-if="isChapterTitle(paragraph)" class="chapter-title" :id="'chapter-' + index">
                {{ paragraph }}
              </div>
              <p v-else :id="'para-' + index">{{ paragraph }}</p>
            </template>
            <!-- 翻页模式提示 -->
            <div v-if="settings.paginationMode && totalPages > 1" class="pagination-hint">
              <span>← → 翻页 | 共 {{ totalPages }} 页</span>
            </div>
          </div>

          <!-- HTML 阅读器 -->
          <div v-else-if="book.format === 'html'" class="reader-html" v-html="htmlContent"></div>

          <!-- PDF 阅读器 -->
          <div v-else-if="book.format === 'pdf'" class="reader-pdf">
            <iframe :src="pdfUrl" class="pdf-frame"></iframe>
          </div>

          <!-- 不支持的格式 -->
          <div v-else class="reader-placeholder">
            <div class="placeholder-icon">📚</div>
            <p>{{ book.format.toUpperCase() }} 格式暂不支持在线阅读</p>
            <button class="btn btn-primary" @click="handleDownload">下载文件</button>
          </div>
        </div>
      </div>

      <!-- 阅读器底部 -->
      <footer class="reader-footer glass" v-show="!isFullscreen">
        <!-- TXT/MD 翻页模式 -->
        <template v-if="(book.format === 'txt' || book.format === 'md') && settings.paginationMode">
          <button class="btn" @click="prevTextPage" :disabled="currentPage === 0">
            <span>‹</span>
            <span>上一页</span>
          </button>
          <div class="pagination-info">
            <span class="chapter-info">{{ currentChapterName }}</span>
            <span class="page-info">{{ currentPage + 1 }} / {{ totalPages }}</span>
          </div>
          <button class="btn" @click="nextTextPage" :disabled="currentPage >= totalPages - 1">
            <span>下一页</span>
            <span>›</span>
          </button>
        </template>

        <!-- TXT/MD 滚动模式 / 其他格式 -->
        <template v-else-if="book.format !== 'epub'">
          <div class="footer-left">
            <span class="chapter-info">{{ currentChapterName }}</span>
          </div>
          <div class="footer-center">
            <div class="progress-bar-wrapper">
              <div class="progress-bar" :style="{ width: progress + '%' }"></div>
              <input type="range" v-model="progress" min="0" max="100" class="progress-slider" @input="handleProgressChange" />
            </div>
          </div>
          <div class="footer-right">
            <span class="progress-text">{{ progress }}%</span>
          </div>
        </template>

        <!-- EPUB格式 -->
        <template v-else>
          <button class="btn" @click="prevPage" :disabled="!bookInstance">
            <span>‹</span>
            <span>上一页</span>
          </button>
          <div class="epub-info">
            <span class="epub-location">{{ currentLocation || '' }}</span>
            <span class="epub-progress">{{ progress }}%</span>
          </div>
          <button class="btn" @click="nextPage" :disabled="!bookInstance">
            <span>下一页</span>
            <span>›</span>
          </button>
        </template>
      </footer>

      <!-- 全屏模式下的浮动控制条 -->
      <div v-if="isFullscreen" class="fullscreen-controls" @mouseenter="showFullscreenControls = true" @mouseleave="showFullscreenControls = false">
        <Transition name="fade">
          <div v-if="showFullscreenControls" class="floating-bar glass">
            <button class="btn btn-icon" @click="togglePanel('toc')" title="目录">
              <span>☰</span>
            </button>
            <button class="btn btn-icon" @click="showSettings = true" title="设置">
              <span>⚙️</span>
            </button>
            <span class="floating-progress">{{ progress }}%</span>
            <button class="btn btn-icon" @click="toggleFullscreen" title="退出全屏">
              <span>⊡</span>
            </button>
          </div>
        </Transition>
      </div>

      <!-- 设置面板 -->
      <Transition name="slide-right">
        <div v-if="showSettings" class="settings-overlay" @click.self="showSettings = false">
          <div class="settings-panel glass">
            <div class="settings-header">
              <span>⚙️ 阅读设置</span>
              <button class="dialog-close" @click="showSettings = false">✕</button>
            </div>
            <div class="settings-body">
              <!-- 字体设置 -->
              <div class="setting-section">
                <h4 class="section-title">字体设置</h4>
                <div class="form-group">
                  <label class="form-label">字体</label>
                  <div class="font-options">
                    <button
                      v-for="font in fontOptions"
                      :key="font.value"
                      class="font-btn"
                      :class="{ active: settings.fontFamily === font.value }"
                      :style="{ fontFamily: font.preview }"
                      @click="settings.fontFamily = font.value"
                    >
                      {{ font.label }}
                    </button>
                  </div>
                </div>
                <div class="form-group">
                  <label class="form-label">字号：{{ settings.fontSize }}px</label>
                  <div class="slider-wrapper">
                    <span class="slider-min">A</span>
                    <input type="range" v-model="settings.fontSize" min="12" max="28" class="slider" />
                    <span class="slider-max">A</span>
                  </div>
                </div>
                <div class="form-group">
                  <label class="form-label">行间距：{{ settings.lineHeight }}</label>
                  <input type="range" v-model="settings.lineHeight" min="1.2" max="2.5" step="0.1" class="slider" />
                </div>
                <div class="form-group">
                  <label class="form-label">段落间距：{{ settings.paragraphSpacing }}px</label>
                  <input type="range" v-model="settings.paragraphSpacing" min="0" max="40" step="2" class="slider" />
                </div>
              </div>

              <!-- 主题设置 -->
              <div class="setting-section">
                <h4 class="section-title">阅读主题</h4>
                <div class="theme-options">
                  <button
                    v-for="theme in themeOptions"
                    :key="theme.value"
                    class="theme-btn"
                    :class="{ active: settings.backgroundColor === theme.value }"
                    @click="settings.backgroundColor = theme.value"
                  >
                    <span class="theme-preview" :style="getThemePreviewStyle(theme)">
                      <span class="preview-title">字</span>
                      <span class="preview-line"></span>
                      <span class="preview-line short"></span>
                    </span>
                    <span class="theme-name">{{ theme.label }}</span>
                  </button>
                </div>
              </div>

              <!-- 阅读设置 -->
              <div class="setting-section">
                <h4 class="section-title">阅读偏好</h4>
                <div class="form-group">
                  <label class="form-label toggle-label">
                    <span>首行缩进</span>
                    <button class="toggle-switch" :class="{ on: settings.textIndent }" @click="settings.textIndent = !settings.textIndent">
                      <span class="toggle-knob"></span>
                    </button>
                  </label>
                </div>
                <div class="form-group">
                  <label class="form-label toggle-label">
                    <span>显示阅读进度</span>
                    <button class="toggle-switch" :class="{ on: settings.showProgress }" @click="settings.showProgress = !settings.showProgress">
                      <span class="toggle-knob"></span>
                    </button>
                  </label>
                </div>
                <div class="form-group" v-if="book?.format === 'txt' || book?.format === 'md'">
                  <label class="form-label toggle-label">
                    <span>翻页模式</span>
                    <button class="toggle-switch" :class="{ on: settings.paginationMode }" @click="settings.paginationMode = !settings.paginationMode">
                      <span class="toggle-knob"></span>
                    </button>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty glass">
      <div class="empty-icon">📚</div>
      <p>书籍不存在</p>
      <button class="btn btn-primary" @click="$router.back()">返回书库</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBookStore } from '@/stores/book'
import { useThemeStore } from '@/stores/theme'
import api from '@/utils/api'
import { message, confirm } from '@/utils/message'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()
const themeStore = useThemeStore()

// 章节接口定义
interface Chapter {
  title: string
  index: number
  startIndex?: number
  endIndex?: number
  label?: string
  href?: string
}

interface Bookmark {
  id: number
  title: string
  chapter?: string
  page?: number
  cfi?: string
  scrollPosition?: number
  createdAt: string
}

interface Highlight {
  id: number
  text: string
  note?: string
  color: string
  chapter: string
  startOffset: number
  endOffset: number
  createdAt: string
}

const book = ref<any>(null)
const loading = ref(true)
const content = ref<string[]>([])
const htmlContent = ref('')
const progress = ref(0)
const showSettings = ref(false)
const showToc = ref(false)
const showBookmarks = ref(false)
const showHighlights = ref(false)
const activeTab = ref('toc')
const tocItems = ref<Chapter[]>([])
const currentTocHref = ref('')
const currentLocation = ref('')
const currentChapterName = ref('')
const isFullscreen = ref(false)
const showFullscreenControls = ref(false)

// 翻页模式相关
const currentPage = ref(0)
const totalPages = ref(0)

// 书签和高亮数据
const bookmarks = ref<Bookmark[]>([])
const highlights = ref<Highlight[]>([])

// EPUB 相关
const epubContainer = ref<HTMLElement>()
let bookInstance: any = null
let rendition: any = null

const pdfUrl = computed(() => {
  if (!book.value) return ''
  const token = localStorage.getItem('token')
  return `/api/books/${book.value.id}/content?token=${token}`
})

// 字体选项
const fontOptions = [
  { value: 'default', label: '默认', preview: 'inherit' },
  { value: 'SimSun, serif', label: '宋体', preview: 'SimSun, serif' },
  { value: 'SimHei, sans-serif', label: '黑体', preview: 'SimHei, sans-serif' },
  { value: 'KaiTi, serif', label: '楷体', preview: 'KaiTi, serif' },
  { value: 'FangSong, serif', label: '仿宋', preview: 'FangSong, serif' },
]

// 主题选项
const themeOptions = [
  { value: 'auto', label: '跟随主题', textColor: 'auto' },
  { value: '#ffffff', label: '白色', textColor: '#333' },
  { value: '#f5f5dc', label: '米色', textColor: '#333' },
  { value: '#e8f5e9', label: '护眼', textColor: '#333' },
  { value: '#fff8e1', label: '暖黄', textColor: '#333' },
  { value: '#2d2d2d', label: '暗黑', textColor: '#eee' },
  { value: '#1a1a2e', label: '深蓝', textColor: '#eee' },
]

// 自动跟随主题的颜色映射
const autoThemeColors = computed(() => {
  switch (themeStore.currentTheme) {
    case 'modern': return { bg: '#ffffff', text: '#1a1a1a' }
    case 'warm': return { bg: '#faf6f1', text: '#3d2b1f' }
    case 'natural':
    default: return { bg: '#f0f7f4', text: '#1a3a2a' }
  }
})

// 获取主题预览样式
const getThemePreviewStyle = (theme: any) => {
  if (theme.value === 'auto') {
    return { background: autoThemeColors.value.bg, color: autoThemeColors.value.text }
  }
  return { background: theme.value, color: theme.textColor }
}

const SETTINGS_STORAGE_KEY = 'ai-book-reader-settings'

const settings = ref({
  fontFamily: 'default',
  fontSize: 16,
  lineHeight: 1.8,
  paragraphSpacing: 16,
  backgroundColor: 'auto',
  textIndent: true,
  showProgress: true,
  paginationMode: false, // 翻页模式
})

// 获取实际背景色和文字色（处理 'auto' 跟随主题）
const getResolvedColors = (bg: string) => {
  if (bg === 'auto') {
    return { bg: autoThemeColors.value.bg, text: autoThemeColors.value.text }
  }
  return { bg, text: ['#2d2d2d', '#1a1a2e'].includes(bg) ? '#eee' : '#333' }
}

const readerStyle = computed(() => {
  const colors = getResolvedColors(settings.value.backgroundColor)
  return {
    fontFamily: settings.value.fontFamily === 'default' ? 'inherit' : settings.value.fontFamily,
    fontSize: `${settings.value.fontSize}px`,
    lineHeight: settings.value.lineHeight,
    backgroundColor: colors.bg,
    color: colors.text,
  }
})

// 加载保存的阅读设置
const loadReaderSettings = () => {
  try {
    const saved = localStorage.getItem(SETTINGS_STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      Object.assign(settings.value, parsed)
    }
  } catch (e) { /* ignore */ }
}

// 保存阅读设置
const saveReaderSettings = () => {
  try {
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings.value))
  } catch (e) { /* ignore */ }
}

// 翻页模式相关计算
const currentPageContent = computed(() => {
  if (!settings.value.paginationMode || book.value?.format === 'epub') {
    return content.value
  }
  const pageSize = calculatePageSize()
  const start = currentPage.value * pageSize
  const end = start + pageSize
  return content.value.slice(start, end)
})

// 计算每页能显示多少段落（基于实际渲染高度）
const calculatePageSize = (): number => {
  const readerBody = document.querySelector('.reader-body')
  if (!readerBody) return 5

  // 可用高度 = 容器高度 - 上下padding(40px*2) - 提示区域(80px)
  const availableHeight = readerBody.clientHeight - 160
  const lineHeight = settings.value.fontSize * settings.value.lineHeight
  const paragraphSpacing = settings.value.paragraphSpacing

  // 保守估计每段高度：假设较长的段落会换行
  // 中文段落平均约50-80字，在手机宽度约30字/行，所以约2-3行
  const charsPerLine = Math.floor((readerBody.clientWidth - 120) / (settings.value.fontSize * 0.9))
  const avgLinesPerParagraph = Math.max(2, Math.ceil(60 / charsPerLine)) // 假设每段60字
  const estimatedParagraphHeight = lineHeight * avgLinesPerParagraph + paragraphSpacing

  return Math.max(1, Math.floor(availableHeight / estimatedParagraphHeight))
}

const updateTotalPages = () => {
  if (settings.value.paginationMode && content.value.length > 0) {
    const pageSize = calculatePageSize()
    totalPages.value = Math.ceil(content.value.length / pageSize)
  } else {
    totalPages.value = 0
  }
}

// 检查内容是否溢出，如果溢出则减少每页内容
const checkAndAdjustPageSize = () => {
  if (!settings.value.paginationMode) return

  const readerBody = document.querySelector('.reader-body')
  if (!readerBody) return

  // 检查是否溢出
  if (readerBody.scrollHeight > readerBody.clientHeight + 10) {
    // 内容溢出，重新计算更小的页大小
    const newPageSize = calculatePageSize()
    if (newPageSize < currentPageContent.value.length) {
      // 强制更新页大小
      updateTotalPages()
    }
  }
}

const goToPage = (page: number) => {
  if (page >= 0 && page < totalPages.value) {
    currentPage.value = page
    progress.value = Math.round((page / (totalPages.value - 1)) * 100)
    saveProgress(progress.value, currentChapterName.value)
    // 滚动到顶部
    const readerBody = document.querySelector('.reader-body')
    if (readerBody) {
      readerBody.scrollTop = 0
    }
  }
}

const prevTextPage = () => {
  goToPage(currentPage.value - 1)
}

const nextTextPage = () => {
  goToPage(currentPage.value + 1)
}

// 键盘翻页
const handleKeydown = (e: KeyboardEvent) => {
  if (!settings.value.paginationMode) return
  if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
    e.preventDefault()
    prevTextPage()
  } else if (e.key === 'ArrowRight' || e.key === 'PageDown') {
    e.preventDefault()
    nextTextPage()
  }
}

// 面板显示状态
const showSidePanel = computed(() => showToc.value || showBookmarks.value || showHighlights.value)

// 阅读进度保存相关
let saveTimer: ReturnType<typeof setTimeout> | null = null
let readingStartTime = 0
const savedCfi = ref<string | null>(null)
const savedScrollPosition = ref<number | null>(null)

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    // 先获取书籍信息
    book.value = await bookStore.fetchBookById(id)

    // 并行加载进度、书签、高亮和内容
    const promises = [
      loadSavedProgress(id),
      loadBookmarks(id),
      loadHighlights(id)
    ]

    // 根据格式加载内容
    if (book.value.format === 'txt' || book.value.format === 'md') {
      promises.push(loadTextContent())
    } else if (book.value.format === 'html') {
      promises.push(loadHtmlContent())
    }

    // 等待所有请求完成
    await Promise.all(promises)

    // EPUB 需要在 DOM 更新后初始化
    if (book.value.format === 'epub') {
      await nextTick()
      initEpub()
    }

    // 记录开始阅读时间
    readingStartTime = Date.now()
  } catch (error) {
    console.error('Failed to load book:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 加载已保存的阅读进度
 */
const loadSavedProgress = async (bookId: number) => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/reading-progress/book/${bookId}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (response.ok) {
      const data = await response.json()
      console.log('[Reader] Loaded progress:', data)
      if (data.totalProgress > 0) {
        progress.value = data.totalProgress
      }
      if (data.currentChapter) {
        savedCfi.value = data.currentChapter
        console.log('[Reader] Saved CFI:', savedCfi.value)
      }
    }
  } catch (error) {
    console.error('Failed to load reading progress:', error)
  }
}

/**
 * 加载书签
 */
const loadBookmarks = async (bookId: number) => {
  try {
    const response = await api.get(`/api/books/${bookId}/bookmarks`)
    bookmarks.value = response.data || []
  } catch (error) {
    console.error('Failed to load bookmarks:', error)
  }
}

/**
 * 加载高亮
 */
const loadHighlights = async (bookId: number) => {
  try {
    const response = await api.get(`/api/books/${bookId}/highlights`)
    highlights.value = response.data || []
  } catch (error) {
    console.error('Failed to load highlights:', error)
  }
}

/**
 * 添加书签
 */
const handleAddBookmark = async () => {
  if (!book.value) return

  try {
    const bookmarkData: any = {
      title: currentChapterName.value || '书签',
      chapter: currentChapterName.value,
    }

    if (book.value.format === 'epub' && rendition) {
      const location = rendition.currentLocation()
      if (location?.start?.cfi) {
        bookmarkData.cfi = location.start.cfi
      }
    } else {
      const readerBody = document.querySelector('.reader-body')
      if (readerBody) {
        bookmarkData.scrollPosition = readerBody.scrollTop
      }
    }

    const response = await api.post(`/api/books/${book.value.id}/bookmarks`, bookmarkData)
    bookmarks.value.unshift(response.data)
    message.success('书签添加成功')
  } catch (error) {
    message.error('书签添加失败')
  }
}

/**
 * 跳转到书签位置
 */
const handleGotoBookmark = async (bookmark: Bookmark) => {
  if (book.value?.format === 'epub' && bookmark.cfi && rendition) {
    await rendition.display(bookmark.cfi)
  } else if (bookmark.scrollPosition !== undefined) {
    const readerBody = document.querySelector('.reader-body')
    if (readerBody) {
      readerBody.scrollTop = bookmark.scrollPosition
    }
  }
}

/**
 * 删除书签
 */
const handleDeleteBookmark = async (bookmark: Bookmark) => {
  const result = await confirm('确定要删除这个书签吗？')
  if (result) {
    try {
      await api.delete(`/api/books/${book.value.id}/bookmarks/${bookmark.id}`)
      bookmarks.value = bookmarks.value.filter(b => b.id !== bookmark.id)
      message.success('书签已删除')
    } catch (error) {
      message.error('删除失败')
    }
  }
}

/**
 * 跳转到高亮位置
 */
const handleGotoHighlight = (highlight: Highlight) => {
  // TODO: 实现高亮定位
  message.info('高亮定位功能开发中')
}

/**
 * 删除高亮
 */
const handleDeleteHighlight = async (highlight: Highlight) => {
  const result = await confirm('确定要删除这个高亮吗？')
  if (result) {
    try {
      await api.delete(`/api/books/${book.value.id}/highlights/${highlight.id}`)
      highlights.value = highlights.value.filter(h => h.id !== highlight.id)
      message.success('高亮已删除')
    } catch (error) {
      message.error('删除失败')
    }
  }
}

/**
 * 保存阅读进度（防抖）
 */
const saveProgress = (totalProgress: number, currentChapter?: string) => {
  if (!book.value) return

  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    try {
      const token = localStorage.getItem('token')
      await fetch(`/api/reading-progress/book/${book.value.id}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          currentChapter: currentChapter || '',
          chapterProgress: 0,
          totalProgress: Math.round(totalProgress)
        })
      })
    } catch (error) {
      console.error('Failed to save reading progress:', error)
    }
  }, 1000)
}

/**
 * 保存阅读时长
 */
const saveReadingTime = async () => {
  if (!book.value || readingStartTime === 0) return

  const elapsedSeconds = Math.floor((Date.now() - readingStartTime) / 1000)
  if (elapsedSeconds < 5) return // 少于5秒不记录

  try {
    const token = localStorage.getItem('token')
    await fetch(`/api/reading-progress/book/${book.value.id}/time`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ seconds: elapsedSeconds })
    })
  } catch (error) {
    console.error('Failed to save reading time:', error)
  }
}

/**
 * 解析章节标题，生成目录（客户端降级方案）
 */
const parseChapters = (paragraphs: string[]): Chapter[] => {
  const chapters: Chapter[] = []

  // 章节匹配正则表达式
  const patterns = [
    /^第[一二三四五六七八九十百千万零\d]+[章回节卷篇]/,
    /^Chapter\s+\d+/i,
    /^卷[一二三四五六七八九十\d]+/,
    /^(序章|序幕|楔子|尾声|终章|后记|前言|引言|番外|附录)/,
    /^【[^】]{1,50}】/,
    /^#{1,3}\s+.{1,100}/,
  ]

  paragraphs.forEach((paragraph, index) => {
    const trimmed = paragraph.trim()
    if (patterns.some(pattern => pattern.test(trimmed))) {
      chapters.push({ title: trimmed, index, label: trimmed })
    }
  })

  return chapters
}

/**
 * 将后端章节信息映射到段落索引
 */
const mapChaptersToParagraphs = (
  backendChapters: { title: string; startIndex: number; endIndex: number }[],
  processedText: string
): Chapter[] => {
  const paragraphs = processedText.split(/\n\n+/)
  const result: Chapter[] = []

  for (const ch of backendChapters) {
    const titleTrimmed = ch.title.trim()
    const paraIndex = paragraphs.findIndex(p => p.trim() === titleTrimmed)
    if (paraIndex >= 0) {
      result.push({
        title: titleTrimmed,
        index: paraIndex,
        startIndex: ch.startIndex,
        endIndex: ch.endIndex,
        label: titleTrimmed
      })
    }
  }

  return result
}

/**
 * 判断是否为章节标题
 */
const isChapterTitle = (paragraph: string): boolean => {
  return tocItems.value.some(item => item.title === paragraph.trim())
}

/**
 * 判断是否为当前目录项
 */
const isCurrentTocItem = (item: Chapter): boolean => {
  if (book.value?.format === 'epub') {
    return item.href === currentTocHref.value
  }
  return item.title === currentChapterName.value
}

const loadTextContent = async () => {
  try {
    const token = localStorage.getItem('token')

    const response = await fetch(`/api/books/${book.value.id}/content-processed`, {
      headers: { Authorization: `Bearer ${token}` }
    })

    if (response.ok) {
      const data = await response.json()
      content.value = data.text.split(/\n\n+/).filter((p: string) => p.trim())

      if (data.chapterInfo && data.chapterInfo !== '[]') {
        try {
          const backendChapters = JSON.parse(data.chapterInfo)
          if (backendChapters.length > 0) {
            tocItems.value = mapChaptersToParagraphs(backendChapters, data.text)
          } else {
            tocItems.value = parseChapters(content.value)
          }
        } catch (e) {
          tocItems.value = parseChapters(content.value)
        }
      } else {
        tocItems.value = parseChapters(content.value)
      }
    } else {
      const rawResponse = await fetch(`/api/books/${book.value.id}/content`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      const text = await rawResponse.text()
      content.value = text.split(/\n\n+/).filter(p => p.trim())
      tocItems.value = parseChapters(content.value)
    }

    await nextTick()
    updateTotalPages()
    restoreScrollPosition()
  } catch (error) {
    console.error('Failed to load text content:', error)
    content.value = ['加载内容失败']
  }
}

/**
 * 恢复滚动位置
 */
const restoreScrollPosition = () => {
  if (progress.value > 0) {
    if (settings.value.paginationMode && totalPages.value > 0) {
      // 翻页模式下恢复到对应页码
      currentPage.value = Math.floor((progress.value / 100) * (totalPages.value - 1))
    } else {
      const readerBody = document.querySelector('.reader-body')
      if (readerBody) {
        const maxScroll = readerBody.scrollHeight - readerBody.clientHeight
        readerBody.scrollTop = maxScroll * (progress.value / 100)
      }
    }
  }
}

/**
 * 处理滚动事件
 */
const handleScroll = () => {
  if (book.value?.format === 'epub') return

  const readerBody = document.querySelector('.reader-body')
  if (!readerBody) return

  const maxScroll = readerBody.scrollHeight - readerBody.clientHeight
  if (maxScroll > 0) {
    const currentProgress = Math.round((readerBody.scrollTop / maxScroll) * 100)
    if (Math.abs(currentProgress - progress.value) >= 1) {
      progress.value = currentProgress
      currentChapterName.value = findCurrentChapter()
      saveProgress(currentProgress, currentChapterName.value)
    }
  }
}

/**
 * 找到当前可见的章节
 */
const findCurrentChapter = (): string => {
  const readerBody = document.querySelector('.reader-body')
  if (!readerBody) return ''

  const scrollTop = readerBody.scrollTop
  let currentChapter = ''

  for (const item of tocItems.value) {
    const element = document.getElementById('chapter-' + item.index)
    if (element && element.offsetTop <= scrollTop + 100) {
      currentChapter = item.title
    }
  }

  return currentChapter
}

/**
 * 处理进度条拖动
 */
const handleProgressChange = () => {
  const readerBody = document.querySelector('.reader-body')
  if (!readerBody) return

  const maxScroll = readerBody.scrollHeight - readerBody.clientHeight
  readerBody.scrollTop = maxScroll * (progress.value / 100)
}

const loadHtmlContent = async () => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/content`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    htmlContent.value = await response.text()
  } catch (error) {
    console.error('Failed to load HTML content:', error)
    htmlContent.value = '<p>加载内容失败</p>'
  }
}

const initEpub = async () => {
  try {
    const ePub = (await import('epubjs')).default

    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/content`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const arrayBuffer = await response.arrayBuffer()

    bookInstance = ePub(arrayBuffer)

    await bookInstance.ready

    const navigation = bookInstance.navigation
    if (navigation && navigation.toc) {
      tocItems.value = flattenToc(navigation.toc)
    }

    rendition = bookInstance.renderTo(epubContainer.value!, {
      width: '100%',
      height: '100%',
      spread: 'none',
      allowScriptedContent: true,
    })

    rendition.on('relocated', (location: any) => {
      if (location && location.start) {
        currentLocation.value = location.start.displayed
          ? `${location.start.displayed.page} / ${location.start.displayed.total}`
          : ''
        if (bookInstance.locations && bookInstance.locations.length()) {
          const percentage = bookInstance.locations.percentageFromCfi(location.start.cfi)
          progress.value = Math.round(percentage * 100)
          saveProgress(progress.value, location.start.cfi)
        }
      }
    })

    rendition.hooks.content.register((contents: any) => {
      applyThemeToContent(contents)
    })

    await bookInstance.locations.generate(1024)

    if (savedCfi.value) {
      try {
        await rendition.display(savedCfi.value)
      } catch (e) {
        console.error('[Reader] Failed to restore CFI, falling back to start:', e)
        await rendition.display()
      }
    } else {
      await rendition.display()
    }

  } catch (error) {
    console.error('Failed to init EPUB:', error)
  }
}

const flattenToc = (toc: any[], result: any[] = []): any[] => {
  for (const item of toc) {
    result.push({ label: item.label.trim(), href: item.href, title: item.label.trim() })
    if (item.subitems && item.subitems.length > 0) {
      flattenToc(item.subitems, result)
    }
  }
  return result
}

const togglePanel = (panel: 'toc' | 'bookmarks' | 'highlights') => {
  if (panel === 'toc') {
    showToc.value = !showToc.value
    showBookmarks.value = false
    showHighlights.value = false
    activeTab.value = 'toc'
  } else if (panel === 'bookmarks') {
    showBookmarks.value = !showBookmarks.value
    showToc.value = false
    showHighlights.value = false
    activeTab.value = 'bookmarks'
  } else if (panel === 'highlights') {
    showHighlights.value = !showHighlights.value
    showToc.value = false
    showBookmarks.value = false
    activeTab.value = 'highlights'
  }
}

const closeAllPanels = () => {
  showToc.value = false
  showBookmarks.value = false
  showHighlights.value = false
}

const goToTocItem = (item: Chapter | any) => {
  if (book.value?.format === 'epub' && rendition) {
    rendition.display(item.href)
    currentTocHref.value = item.href
  } else if (book.value?.format === 'txt' || book.value?.format === 'md') {
    const element = document.getElementById('chapter-' + item.index)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' })
    }
  }
}

const prevPage = () => {
  if (rendition) {
    rendition.prev()
  }
}

const nextPage = () => {
  if (rendition) {
    rendition.next()
  }
}

const goBack = () => {
  router.back()
}

const handleDownload = () => {
  if (!book.value) return
  window.open(`/api/books/${book.value.id}/content`, '_blank')
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

const applyThemeToContent = (contents: any) => {
  if (!contents || !contents.css) return

  const fontFamily = settings.value.fontFamily === 'default'
    ? 'serif'
    : settings.value.fontFamily.split(',')[0].trim()

  const colors = getResolvedColors(settings.value.backgroundColor)

  contents.css('font-family', `${fontFamily}, serif`, true)
  contents.css('font-size', `${settings.value.fontSize}px`, true)
  contents.css('line-height', `${settings.value.lineHeight}`, true)
  contents.css('color', colors.text, true)
  contents.css('background', colors.bg, true)

  try {
    const doc = contents.document
    if (doc) {
      const style = doc.createElement('style')
      style.textContent = `
        * {
          font-family: ${fontFamily}, serif !important;
          font-size: ${settings.value.fontSize}px !important;
          line-height: ${settings.value.lineHeight} !important;
          color: ${colors.text} !important;
        }
        body {
          background: ${colors.bg} !important;
        }
        p {
          margin-bottom: ${settings.value.paragraphSpacing}px !important;
          ${settings.value.textIndent ? 'text-indent: 2em !important;' : ''}
        }
      `
      doc.head.appendChild(style)
    }
  } catch (e) {
    // 忽略跨域错误
  }
}

const applyEpubTheme = () => {
  if (!rendition) return
  const contents = rendition.getContents()
  contents.forEach((c: any) => applyThemeToContent(c))
}

const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN')
}

watch(() => settings.value, () => {
  applyEpubTheme()
  saveReaderSettings()
  updateTotalPages()
}, { deep: true })

// 监听翻页模式变化
watch(() => settings.value.paginationMode, (newVal) => {
  if (newVal) {
    updateTotalPages()
    // 切换到翻页模式，根据当前进度计算页码
    if (totalPages.value > 0) {
      currentPage.value = Math.floor((progress.value / 100) * (totalPages.value - 1))
    }
  }
})

// 监听内容变化
watch(content, () => {
  updateTotalPages()
})

// 监听当前页内容变化，检查是否溢出
watch(currentPageContent, () => {
  if (settings.value.paginationMode) {
    nextTick(() => {
      checkAndAdjustPageSize()
    })
  }
})

// 监听窗口大小变化
const handleResize = () => {
  if (settings.value.paginationMode) {
    updateTotalPages()
    if (currentPage.value >= totalPages.value) {
      currentPage.value = Math.max(0, totalPages.value - 1)
    }
  }
}

onMounted(() => {
  loadReaderSettings()
  loadBook()
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', handleResize)

  // 禁用父容器的滚动，让 reader-body 自己处理滚动
  const layoutMain = document.querySelector('.layout-main')
  if (layoutMain) {
    layoutMain.style.overflow = 'hidden'
  }
})

onBeforeUnmount(() => {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }

  const token = localStorage.getItem('token')

  if (book.value && progress.value > 0 && token) {
    fetch(`/api/reading-progress/book/${book.value.id}`, {
      method: 'POST',
      keepalive: true,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        currentChapter: savedCfi.value || '',
        chapterProgress: 0,
        totalProgress: Math.round(progress.value)
      })
    })
  }

  if (book.value && readingStartTime > 0 && token) {
    const elapsedSeconds = Math.floor((Date.now() - readingStartTime) / 1000)
    if (elapsedSeconds >= 5) {
      fetch(`/api/reading-progress/book/${book.value.id}/time`, {
        method: 'PUT',
        keepalive: true,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ seconds: elapsedSeconds })
      })
    }
  }

  if (bookInstance) {
    bookInstance.destroy()
    bookInstance = null
    rendition = null
  }

  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleResize)

  // 恢复父容器的滚动
  const layoutMain = document.querySelector('.layout-main')
  if (layoutMain) {
    layoutMain.style.overflow = ''
  }
})
</script>

<style scoped>
.reader-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-page-gradient);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
}

.reader-view.fullscreen-mode {
  /* Already full screen with fixed positioning */
}

/* 加载中和空状态 */
.loading,
.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  gap: var(--spacing-md);
}

.loading-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-icon {
  font-size: 64px;
  opacity: 0.5;
}

/* 阅读器内容 */
.reader-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

/* 阅读器头部 */
.reader-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid var(--border-color-light);
  flex-shrink: 0;
  z-index: 100;
  min-height: 56px;
  position: sticky;
  top: 0;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: var(--font-size-base);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.back-btn:hover {
  background: var(--bg-tertiary);
}

.reader-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 var(--spacing-md);
}

.reader-actions {
  display: flex;
  gap: var(--spacing-xs);
}

.btn-icon {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.btn-icon:hover {
  background: var(--bg-tertiary);
}

.btn-icon.active {
  background: var(--primary-alpha-20);
  color: var(--primary);
}

.btn-small {
  width: 32px;
  height: 32px;
  font-size: var(--font-size-sm);
}

/* 阅读器主体 */
.reader-body-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
  min-height: 0;
}

/* 侧边面板 */
.side-panel {
  width: 320px;
  background: var(--surface-elevated);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-right: 1px solid var(--border-color-light);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 50;
}

.panel-tabs {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--border-color-light);
  padding: 0 var(--spacing-sm);
  background: var(--surface-card);
}

.tab-btn {
  flex: 1;
  padding: var(--spacing-md) var(--spacing-sm);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  border-bottom: 2px solid transparent;
}

.tab-btn:hover {
  color: var(--text-primary);
}

.tab-btn.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}

.close-panel {
  margin-left: auto;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
}

/* 目录样式 */
.toc-header,
.bookmarks-header,
.highlights-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
  font-weight: 600;
}

.toc-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.toc-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  transition: all var(--transition-fast);
  border-radius: var(--radius-md);
}

.toc-item:hover {
  background: var(--primary-alpha-10);
}

.toc-item.active {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.toc-index {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.toc-item.active .toc-index {
  background: var(--primary);
  color: white;
}

.toc-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 空面板状态 */
.empty-panel {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--text-secondary);
}

.empty-panel .empty-icon {
  font-size: 48px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

.empty-hint {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-top: var(--spacing-sm);
}

/* 书签列表 */
.bookmarks-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.bookmark-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.bookmark-item:hover {
  background: var(--primary-alpha-10);
}

.bookmark-icon {
  font-size: 20px;
}

.bookmark-info {
  flex: 1;
  min-width: 0;
}

.bookmark-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bookmark-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

/* 高亮列表 */
.highlights-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.highlight-item {
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  border-left: 4px solid;
}

.highlight-content {
  margin-bottom: var(--spacing-sm);
}

.highlight-text {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  font-style: italic;
  line-height: 1.6;
}

.highlight-note {
  margin-top: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-xs);
}

.note-icon {
  flex-shrink: 0;
}

.highlight-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-sm);
}

.highlight-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.btn-danger {
  color: var(--danger) !important;
}

.btn-danger:hover {
  background: rgba(255, 59, 48, 0.1) !important;
}

/* 阅读器内容区 */
.reader-body {
  flex: 1;
  overflow-y: auto;
  padding: 40px 60px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
  scroll-behavior: smooth;
  min-height: 0;
}

/* 翻页模式也允许滚动 */
.reader-body.pagination-mode {
  overflow-y: auto;
}

.epub-container {
  width: 100%;
  height: 100%;
}

.reader-text {
  min-height: 100%;
}

.reader-text p {
  margin-bottom: v-bind('settings.paragraphSpacing + "px"');
  text-indent: v-bind('settings.textIndent ? "2em" : "0"');
  line-height: 1.9;
  font-size: 1.05em;
  color: var(--text-primary);
}

.reader-text p::first-letter {
  font-size: 1.1em;
}

.chapter-title {
  font-size: 1.6em;
  font-weight: bold;
  text-align: center;
  margin: 2.5em 0 1.5em 0;
  padding: 0.8em 0;
  color: var(--text-primary);
  border-bottom: 2px solid var(--border-color);
  letter-spacing: 0.1em;
}

.pagination-hint {
  text-align: center;
  padding: 2em 0;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
  margin-top: 2em;
  border-top: 1px dashed var(--border-color-light);
}

.pagination-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  flex: 1;
}

.page-info {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

.reader-html {
  line-height: 1.8;
}

.reader-pdf {
  width: 100%;
  height: 100%;
}

.pdf-frame {
  width: 100%;
  height: 100%;
  border: none;
}

.reader-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: var(--spacing-lg);
  color: var(--text-secondary);
}

.placeholder-icon {
  font-size: 80px;
  opacity: 0.5;
}

/* 阅读器底部 */
.reader-footer {
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-top: 1px solid var(--border-color-light);
  flex-shrink: 0;
  min-height: 48px;
}

.footer-left {
  flex: 1;
  min-width: 0;
}

.chapter-info {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-center {
  flex: 2;
  padding: 0 var(--spacing-lg);
}

.footer-right {
  flex: 1;
  text-align: right;
}

.progress-text {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

.progress-bar-wrapper {
  position: relative;
  height: 4px;
  background: var(--bg-tertiary);
  border-radius: 2px;
  overflow: visible;
}

.progress-bar {
  height: 100%;
  background: var(--primary);
  border-radius: 2px;
  transition: width 0.1s linear;
}

.progress-slider {
  position: absolute;
  top: -8px;
  left: 0;
  width: 100%;
  height: 20px;
  opacity: 0;
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
}

.progress-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--primary);
  cursor: pointer;
  box-shadow: 0 2px 6px var(--primary-alpha-30);
}

.epub-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
}

.epub-location {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.epub-progress {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

/* 全屏模式控制 */
.fullscreen-controls {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  z-index: 100;
}

.floating-bar {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-full);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.floating-progress {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

/* 设置面板 */
.settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  justify-content: flex-end;
  z-index: 2000;
}

.settings-panel {
  width: 360px;
  background: var(--surface-elevated);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.2);
}

.settings-header {
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

.settings-body {
  padding: var(--spacing-lg);
  flex: 1;
  overflow-y: auto;
}

.setting-section {
  margin-bottom: var(--spacing-xl);
}

.section-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--spacing-md);
}

.form-group {
  margin-bottom: var(--spacing-md);
}

.form-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

/* 字体选项 */
.font-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.font-btn {
  flex: 1;
  min-width: calc(33.33% - var(--spacing-sm));
  padding: var(--spacing-sm) var(--spacing-md);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.font-btn:hover {
  border-color: var(--primary);
}

.font-btn.active {
  border-color: var(--primary);
  background: var(--primary-alpha-10);
}

/* 滑块样式 */
.slider-wrapper {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.slider-min,
.slider-max {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.slider {
  flex: 1;
  height: 6px;
  -webkit-appearance: none;
  background: var(--bg-tertiary);
  border-radius: 3px;
  outline: none;
}

.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--primary);
  cursor: pointer;
  box-shadow: 0 2px 6px var(--primary-alpha-30);
}

/* 开关样式 */
.toggle-label {
  cursor: pointer;
}

.toggle-switch {
  width: 44px;
  height: 24px;
  border-radius: 12px;
  border: none;
  background: var(--bg-tertiary);
  cursor: pointer;
  position: relative;
  transition: all var(--transition-fast);
  padding: 0;
}

.toggle-switch.on {
  background: var(--primary);
}

.toggle-knob {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: white;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: all var(--transition-fast);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.toggle-switch.on .toggle-knob {
  left: 22px;
}

/* 主题选项 */
.theme-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-sm);
}

.theme-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.theme-btn:first-child {
  grid-column: 1 / -1;
}

.theme-btn:hover {
  border-color: var(--primary);
}

.theme-btn.active {
  border-color: var(--primary);
  background: var(--primary-alpha-10);
}

.theme-preview {
  width: 100%;
  height: 60px;
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--border-color);
  transition: all var(--transition-fast);
}

.preview-title {
  font-weight: 600;
  font-size: 14px;
  line-height: 1;
}

.preview-line {
  width: 70%;
  height: 3px;
  border-radius: 2px;
  background: currentColor;
  opacity: 0.3;
}

.preview-line.short {
  width: 45%;
}

.theme-name {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

/* 动画 */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.3s ease;
}

.slide-left-enter-from,
.slide-left-leave-to {
  transform: translateX(-100%);
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.3s ease;
}

.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .side-panel {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    width: 85%;
    max-width: 320px;
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.2);
  }

  .settings-panel {
    width: 100%;
  }

  .reader-body {
    padding: var(--spacing-md);
  }

  .reader-actions {
    gap: 0;
  }

  .btn-icon {
    width: 36px;
    height: 36px;
  }
}
</style>
