<template>
  <div class="reader-view" :class="{ 'fullscreen-mode': isFullscreen }">
    <!-- 阅读器内容 -->
    <div v-if="book" class="reader-content">
      <div v-if="loading" class="reader-loading-overlay loading glass" role="status">
        <span class="loading-spinner" aria-hidden="true"></span>
        <p>正在打开书籍…</p>
      </div>
      <!-- 阅读器头部 -->
      <header
        v-show="!isFullscreen"
        class="reader-header"
        :style="readerHeaderStyle"
      >
        <button v-show="!showSidePanel" type="button" class="back-btn glass" @click="goBack">
          <span>‹</span>
          <span>返回</span>
        </button>
        <div class="reader-title glass">
          <div class="reader-title-main">
            <div class="reader-book-identity">
              <span class="reader-book-name" :title="book.title">{{ book.title }}</span>
              <span
                v-if="selectedVersion"
                class="reader-version-badge"
                :title="selectedVersion.displayName"
              >
                {{ selectedVersion.format.toUpperCase() }}
              </span>
            </div>
            <span
              v-if="performancePaginationMode"
              class="performance-mode-badge"
              title="长文本已自动使用分页渲染，避免一次创建过多页面节点"
            >
              性能模式
            </span>
            <div class="reader-title-meta">
              <span class="reader-current-chapter" :title="headerChapterName">
                {{ headerChapterName }}
              </span>
              <span v-if="settings.showProgress" class="reader-title-separator">·</span>
              <span v-if="settings.showProgress" class="reader-header-progress">{{ headerProgress }}%</span>
            </div>
          </div>
        </div>
        <div class="reader-actions glass" aria-label="阅读工具">
          <button
            class="btn btn-icon"
            :class="{ active: showSearch }"
            @click="togglePanel('search')"
            title="书内搜索（⌘/Ctrl + F）"
          >
            <span>⌕</span>
          </button>
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
              <div
                class="panel-tabs-nav"
                :style="panelTabStyle"
                role="tablist"
                aria-label="阅读辅助面板"
                @keydown="handlePanelTabKeydown"
              >
                <span class="panel-tab-indicator" aria-hidden="true"></span>
                <button
                  v-for="tab in panelTabs"
                  :key="tab.value"
                  class="tab-btn"
                  :class="{ active: activeTab === tab.value }"
                  type="button"
                  role="tab"
                  :aria-selected="activeTab === tab.value"
                  @click="activatePanelTab(tab.value)"
                >
                  {{ tab.label }}
                </button>
              </div>
              <button
                class="btn btn-icon btn-small close-panel"
                type="button"
                title="关闭侧栏"
                aria-label="关闭侧栏"
                @click="closeAllPanels"
              >
                ✕
              </button>
            </div>

            <!-- 书内搜索 -->
            <div v-if="activeTab === 'search'" class="panel-content search-panel">
              <form class="reader-search-form" role="search" @submit.prevent="runSearchNow">
                <label class="sr-only" for="reader-search-input">搜索书内内容</label>
                <input
                  id="reader-search-input"
                  ref="searchInput"
                  v-model="searchQuery"
                  type="search"
                  autocomplete="off"
                  placeholder="搜索书内内容"
                  @input="scheduleSearch"
                />
                <button type="submit" :disabled="searching || !searchQuery.trim()">搜索</button>
              </form>
              <div class="search-summary" aria-live="polite">
                <span v-if="searching">正在搜索…</span>
                <span v-else-if="searchQuery.trim() && searchCompleted">
                  {{ searchResults.length >= SEARCH_RESULT_LIMIT ? `显示前 ${SEARCH_RESULT_LIMIT} 条结果` : `${searchResults.length} 条结果` }}
                </span>
                <span v-else>输入关键词搜索当前书籍</span>
              </div>
              <div v-if="searchError" class="search-error" role="alert">{{ searchError }}</div>
              <div v-else-if="!searching && searchCompleted && searchResults.length === 0" class="empty-panel">
                <div class="empty-icon">⌕</div>
                <p>没有找到相关内容</p>
                <p class="empty-hint">试试更短或不同的关键词</p>
              </div>
              <div v-else class="search-results">
                <button
                  v-for="result in searchResults"
                  :key="result.id"
                  type="button"
                  class="search-result-item"
                  :class="{ active: activeSearchResultId === result.id }"
                  @click="goToSearchResult(result)"
                >
                  <span class="search-result-location">{{ result.locationLabel }}</span>
                  <span class="search-result-excerpt">{{ result.excerpt }}</span>
                </button>
              </div>
            </div>

            <!-- 目录内容 -->
            <div v-if="activeTab === 'toc'" class="panel-content">
              <div class="toc-header">
                <span>📑 章节目录</span>
                <span class="tag">{{ tocItems.length }} {{ book.format === 'pdf' ? '项' : '章' }}</span>
              </div>
              <div class="toc-list">
                <div
                  v-for="(item, index) in tocItems"
                  :key="index"
                  class="toc-item"
                  :class="{ active: isCurrentTocItem(item) }"
                  :style="item.level ? { paddingLeft: `${14 + item.level * 16}px` } : undefined"
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
                <button
                  class="btn btn-primary bookmark-add-btn"
                  type="button"
                  title="添加当前阅读位置为书签"
                  @click="handleAddBookmark"
                >
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
                    <button class="btn btn-text" @click="editHighlight(highlight)">批注</button>
                    <button class="btn btn-text btn-danger" @click="handleDeleteHighlight(highlight)">删除</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 阅读器内容 -->
        <div
          class="reader-body"
          :class="{ 'pagination-mode': isPaginationMode }"
          :style="readerStyle"
          @scroll="handleScroll"
          @mouseup="captureDocumentSelection"
          @touchend="captureDocumentSelection"
        >
          <!-- EPUB 阅读器 -->
          <div v-if="book.format === 'epub'" ref="epubContainer" class="epub-container"></div>

          <!-- TXT / MD 阅读器 -->
          <div v-else-if="book.format === 'txt' || book.format === 'md'" class="reader-text" :style="contentStyle">
            <template v-for="(paragraph, localIndex) in currentPageContent" :key="localIndex">
              <div v-if="isChapterTitle(paragraph)" class="chapter-title" :id="'chapter-' + getOriginalIndex(localIndex)" :data-reader-index="getOriginalIndex(localIndex)">
                {{ paragraph }}
              </div>
              <p v-else :id="'para-' + getOriginalIndex(localIndex)" :data-reader-index="getOriginalIndex(localIndex)">{{ paragraph }}</p>
            </template>
          </div>

          <!-- HTML 阅读器 -->
          <div v-else-if="book.format === 'html'" class="reader-html" v-html="htmlContent"></div>

          <!-- PDF 阅读器 -->
          <div v-else-if="book.format === 'pdf'" class="reader-pdf">
            <PdfReader
              ref="pdfReader"
              :source="pdfSource"
              :initial-page="pdfInitialPage"
              :display-mode="settings.screenMode === 'double' ? 'double' : 'single'"
              @page-change="handlePdfPageChange"
              @outline="handlePdfOutline"
              @error="handlePdfError"
            />
          </div>

          <!-- 不支持的格式 -->
          <div v-else class="reader-placeholder">
            <div class="placeholder-icon">📚</div>
            <p>{{ book.format.toUpperCase() }} 格式暂不支持在线阅读</p>
            <button class="btn btn-primary" @click="handleDownload">下载文件</button>
          </div>
        </div>
      </div>

      <!-- 正文两侧空白区域翻页 -->
      <button
        v-if="showPageNavigation"
        type="button"
        class="page-turn-zone page-turn-zone--previous"
        :style="pageTurnZoneStyle"
        :disabled="!canGoPrevious"
        aria-label="上一页"
        title="上一页"
        @click="turnPrevious"
      >
        <span class="page-turn-button" aria-hidden="true">‹</span>
      </button>
      <button
        v-if="showPageNavigation"
        type="button"
        class="page-turn-zone page-turn-zone--next"
        :style="pageTurnZoneStyle"
        :disabled="!canGoNext"
        aria-label="下一页"
        title="下一页"
        @click="turnNext"
      >
        <span class="page-turn-button" aria-hidden="true">›</span>
      </button>

      <!-- 仅保留页码的迷你阅读 Dock -->
      <div v-if="settings.showProgress" class="reader-progress-dock glass" role="status" aria-label="阅读进度">
        {{ pageProgressLabel }}
      </div>

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
            <div class="floating-reading-info">
              <span class="floating-book-title">{{ book.title }}</span>
              <span class="floating-progress">{{ headerChapterName }} · {{ headerProgress }}%</span>
            </div>
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
                      @click="selectReaderFont(font)"
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
                <div class="form-group">
                  <label class="form-label">内容宽度</label>
                  <div class="width-options">
                    <button
                      v-for="width in widthOptions"
                      :key="width.value"
                      class="width-btn"
                      :class="{ active: settings.contentWidth === width.value }"
                      @click="settings.contentWidth = width.value"
                    >
                      {{ width.label }}
                    </button>
                  </div>
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
                <div class="form-group reader-background-group">
                  <label class="form-label">背景壁纸</label>
                  <div class="reader-background-options">
                    <button
                      class="reader-background-btn"
                      :class="{ active: settings.backgroundImageId === 'none' }"
                      type="button"
                      @click="settings.backgroundImageId = 'none'"
                    >
                      <span class="reader-background-none">无</span>
                      <span>无背景</span>
                    </button>
                    <button
                      v-for="background in readerBackgrounds"
                      :key="background.id"
                      class="reader-background-btn"
                      :class="{ active: settings.backgroundImageId === background.id }"
                      type="button"
                      :title="background.name"
                      @click="settings.backgroundImageId = background.id"
                    >
                      <img :src="background.imageUrl" :alt="background.name" />
                      <span>{{ background.name }}</span>
                    </button>
                  </div>
                  <p v-if="readerBackgroundsLoading" class="reader-background-hint">正在加载自定义背景...</p>
                  <p v-else class="reader-background-hint">可在“设置 → 阅读背景”中上传或删除自定义壁纸。</p>
                </div>
              </div>

              <!-- 阅读设置 -->
              <div class="setting-section">
                <h4 class="section-title">阅读偏好</h4>
                <div class="form-group">
                  <label class="form-label">屏幕模式</label>
                  <div class="screen-mode-options">
                    <button
                      class="screen-mode-btn"
                      :class="{ active: settings.screenMode === 'single' }"
                      @click="settings.screenMode = 'single'"
                    >
                      <span class="screen-mode-icon">▣</span>
                      <span>一屏</span>
                    </button>
                    <button
                      class="screen-mode-btn"
                      :class="{ active: settings.screenMode === 'double' }"
                      @click="settings.screenMode = 'double'"
                    >
                      <span class="screen-mode-icon">▥</span>
                      <span>两屏</span>
                    </button>
                  </div>
                </div>
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
                    <button class="toggle-switch" :class="{ on: isPaginationMode }" @click="togglePaginationMode">
                      <span class="toggle-knob"></span>
                    </button>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>

      <div v-if="highlightEditor" class="highlight-editor-backdrop" @click.self="closeHighlightEditor">
        <section class="highlight-editor glass" role="dialog" aria-modal="true" aria-labelledby="highlight-editor-title">
          <header>
            <div>
              <strong id="highlight-editor-title">{{ highlightEditor.id ? '编辑高亮与批注' : '添加高亮与批注' }}</strong>
              <p>“{{ highlightEditor.text }}”</p>
            </div>
            <button class="dialog-close" type="button" aria-label="关闭" @click="closeHighlightEditor">✕</button>
          </header>
          <div class="highlight-color-options" aria-label="高亮颜色">
            <button
              v-for="color in highlightColors"
              :key="color"
              type="button"
              :class="{ active: highlightEditor.color === color }"
              :style="{ backgroundColor: color }"
              :aria-label="`选择高亮颜色 ${color}`"
              @click="highlightEditor.color = color"
            />
          </div>
          <textarea v-model="highlightEditor.note" rows="4" maxlength="2000" placeholder="添加批注（选填）" />
          <footer>
            <button class="btn btn-text" type="button" @click="closeHighlightEditor">取消</button>
            <button class="btn btn-primary" type="button" :disabled="savingHighlight" @click="saveHighlight">
              {{ savingHighlight ? '保存中…' : '保存' }}
            </button>
          </footer>
        </section>
      </div>
    </div>

    <div v-else-if="loading" class="loading glass" role="status">
      <span class="loading-spinner" aria-hidden="true"></span>
      <p>正在打开书籍…</p>
    </div>

    <div v-else class="empty glass">
      <div class="empty-icon">{{ loadError ? '⚠️' : '📚' }}</div>
      <p>{{ loadError || '书籍不存在' }}</p>
      <div class="empty-actions">
        <button v-if="loadError" class="btn btn-primary" type="button" @click="retryLoadBook">重新加载</button>
        <button class="btn btn-text" type="button" @click="$router.back()">返回书库</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBookStore } from '@/stores/book'
import { useThemeStore } from '@/stores/theme'
import { usePreferencesStore } from '@/stores/preferences'
import { useFontStore } from '@/stores/font'
import PdfReader from '@/components/reader/PdfReader.vue'
import api from '@/utils/api'
import { message, confirm } from '@/utils/message'
import { formatChinaDateTime } from '@/utils/dateTime'
import {
  BUILT_IN_READER_BACKGROUNDS,
  toReaderBackgroundOption,
  type ReaderBackgroundDto,
  type ReaderBackgroundOption,
} from '@/utils/readerBackground'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()
const themeStore = useThemeStore()
const preferencesStore = usePreferencesStore()
const fontStore = useFontStore()
const customReaderBackgrounds = ref<ReaderBackgroundOption[]>([])
const readerBackgroundsLoading = ref(false)
const readerBackgrounds = computed(() => [
  ...BUILT_IN_READER_BACKGROUNDS,
  ...customReaderBackgrounds.value,
])

// 章节接口定义
interface Chapter {
  title: string
  index: number
  startIndex?: number
  endIndex?: number
  label?: string
  href?: string
  page?: number
  level?: number
}

interface Bookmark {
  id: number
  title: string
  excerpt?: string
  chapter?: string
  chapterIndex?: number
  page?: number
  cfi?: string
  scrollPosition?: number
  createdAt: string
}

interface Highlight {
  id: number
  cfiRange: string
  text: string
  note?: string
  color: string
  chapter: string
  createdAt: string
}

interface ReaderLocator {
  format: string
  cfi?: string
  href?: string
  textIndex?: number
  htmlOffset?: number
  pdfPage?: number
  pdfTotalPages?: number
  chapterTitle?: string
  chapterProgress: number
  totalProgress: number
  excerpt?: string
}

interface HighlightEditorState {
  id?: number
  cfiRange: string
  text: string
  chapter: string
  color: string
  note: string
}

type PanelTab = 'search' | 'toc' | 'bookmarks' | 'highlights'

interface ReaderSearchResult {
  id: string
  excerpt: string
  locationLabel: string
  paragraphIndex?: number
  startOffset?: number
  endOffset?: number
  htmlStart?: number
  htmlEnd?: number
  cfi?: string
  page?: number
}

interface BookVersion {
  id: number
  displayName: string
  format: string
  fileSize?: number
  primaryVersion: boolean
  chapterCount?: number
}

const book = ref<any>(null)
const versions = ref<BookVersion[]>([])
const selectedVersionId = ref<number | null>(null)
const selectedVersion = computed(() =>
  versions.value.find(version => version.id === selectedVersionId.value) || null,
)
const loading = ref(true)
const loadError = ref('')
const content = ref<string[]>([])
const htmlContent = ref('')
const progress = ref(0)
const showSettings = ref(false)
const showSearch = ref(false)
const showToc = ref(false)
const showBookmarks = ref(false)
const showHighlights = ref(false)
const activeTab = ref<PanelTab>('toc')
const panelTabs: Array<{ value: PanelTab; label: string }> = [
  { value: 'search', label: '搜索' },
  { value: 'toc', label: '目录' },
  { value: 'bookmarks', label: '书签' },
  { value: 'highlights', label: '高亮' },
]
const panelTabStyle = computed(() => ({
  '--panel-tab-index': String(Math.max(0, panelTabs.findIndex(tab => tab.value === activeTab.value))),
  '--panel-tab-count': String(panelTabs.length),
}))
const tocItems = ref<Chapter[]>([])
const currentTocHref = ref('')
const currentLocation = ref('')
const currentChapterName = ref('')
const isFullscreen = ref(false)
const showFullscreenControls = ref(false)
const headerChapterName = computed(() => currentChapterName.value.trim() || '正文')
const headerProgress = computed(() => {
  const value = Number(progress.value)
  return Number.isFinite(value) ? Math.min(100, Math.max(0, Math.round(value))) : 0
})

// 翻页模式相关
const currentPage = ref(0)
const totalPages = ref(0)
const scrollCurrentPage = ref(1)
const scrollTotalPages = ref(1)
const performancePaginationMode = ref(false)

// 书签和高亮数据
const bookmarks = ref<Bookmark[]>([])
const highlights = ref<Highlight[]>([])
const highlightEditor = ref<HighlightEditorState | null>(null)
const savingHighlight = ref(false)
const highlightColors = ['#ffe066', '#a9e8b3', '#9ed0ff', '#ffb3c6', '#d0bfff']
const savedLocator = ref<ReaderLocator | null>(null)

const SEARCH_RESULT_LIMIT = 200
const searchInput = ref<HTMLInputElement>()
const searchQuery = ref('')
const searchResults = ref<ReaderSearchResult[]>([])
const searching = ref(false)
const searchCompleted = ref(false)
const searchError = ref('')
const activeSearchResultId = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null
let searchSequence = 0

// EPUB 相关
const epubContainer = ref<HTMLElement>()
let bookInstance: any = null
let rendition: any = null
const epubKeyboardDocuments = new Set<Document>()

const pdfReader = ref<InstanceType<typeof PdfReader>>()
const pdfSource = ref<{ url: string; httpHeaders: Record<string, string> } | null>(null)
const pdfCurrentPage = ref(1)
const pdfTotalPages = ref(0)
const pdfInitialPage = computed(() => savedLocator.value?.format === 'pdf'
  ? Math.max(1, savedLocator.value.pdfPage || 1)
  : 1)

const withVersion = (url: string) => {
  if (!selectedVersionId.value) return url
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}versionId=${selectedVersionId.value}`
}

// 字体选项
const builtInFontOptions = [
  { value: 'default', label: '默认', preview: 'inherit' },
  { value: 'SimSun, serif', label: '宋体', preview: 'SimSun, serif' },
  { value: 'SimHei, sans-serif', label: '黑体', preview: 'SimHei, sans-serif' },
  { value: 'KaiTi, serif', label: '楷体', preview: 'KaiTi, serif' },
  { value: 'FangSong, serif', label: '仿宋', preview: 'FangSong, serif' },
]

const managedFontValue = (id: number) => `managed:${id}`

const managedFontId = (value: string) => {
  const match = /^managed:(\d+)$/.exec(value)
  return match ? Number(match[1]) : null
}

const resolveReaderFontFamily = (value: string) => {
  const id = managedFontId(value)
  if (id != null) return `${fontStore.cssFamily(id)}, serif`
  return value === 'default' ? 'inherit' : value
}

const fontOptions = computed(() => [
  ...builtInFontOptions,
  ...fontStore.availableFonts.map(font => ({
    value: managedFontValue(font.id),
    label: font.displayName,
    preview: fontStore.cssFamily(font.id),
    fontId: font.id,
  })),
])

const selectReaderFont = async (font: { value: string; fontId?: number }) => {
  try {
    if (font.fontId != null) {
      await fontStore.loadFont(font.fontId)
      preferencesStore.setReaderFontId(font.fontId)
    } else {
      preferencesStore.setReaderFontId(null)
    }
    settings.value.fontFamily = font.value
  } catch {
    // 浏览器不支持的字体会被自动标记不可用，不打断阅读。
  }
}

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
    case 'macos26': return { bg: '#eef5fb', text: '#172235' }
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
  backgroundImageId: 'none',
  textIndent: true,
  showProgress: true,
  paginationMode: false, // 翻页模式
  contentWidth: 'medium', // 内容宽度: narrow, medium, wide
  screenMode: 'single', // 屏幕模式: single(一屏), double(两屏)
})

const isPaginationMode = computed(
  () => settings.value.paginationMode || performancePaginationMode.value
)

const epubPageNumbers = computed(() => {
  const match = currentLocation.value.match(/(\d+)\s*\/\s*(\d+)/)
  if (!match) return null
  return { current: Number(match[1]), total: Number(match[2]) }
})

const pageProgressLabel = computed(() => {
  if (book.value?.format === 'pdf') {
    return `${pdfCurrentPage.value}/${Math.max(pdfTotalPages.value, 1)}`
  }
  if ((book.value?.format === 'txt' || book.value?.format === 'md') && isPaginationMode.value) {
    return `${Math.min(currentPage.value + 1, Math.max(totalPages.value, 1))}/${Math.max(totalPages.value, 1)}`
  }
  if (book.value?.format === 'epub' && epubPageNumbers.value) {
    return `${epubPageNumbers.value.current}/${epubPageNumbers.value.total}`
  }
  return `${scrollCurrentPage.value}/${scrollTotalPages.value}`
})

const LARGE_TEXT_PARAGRAPH_THRESHOLD = 1200
const LARGE_TEXT_FILE_SIZE_THRESHOLD = 2 * 1024 * 1024
const MAX_PARAGRAPH_LENGTH = 4000

// 内容宽度选项
const widthOptions = [
  { value: 'narrow', label: '窄', maxWidth: '700px' },
  { value: 'medium', label: '中', maxWidth: '800px' },
  { value: 'wide', label: '宽', maxWidth: '1000px' },
  { value: 'wider', label: '更宽', maxWidth: '1200px' },
  { value: 'full', label: '全屏', maxWidth: '100%' },
]

// 获取实际背景色和文字色（处理 'auto' 跟随主题）
const getResolvedColors = (bg: string) => {
  if (bg === 'auto') {
    return { bg: autoThemeColors.value.bg, text: autoThemeColors.value.text }
  }
  return { bg, text: ['#2d2d2d', '#1a1a2e'].includes(bg) ? '#eee' : '#333' }
}

const selectedReaderBackground = computed(() =>
  readerBackgrounds.value.find(background => background.id === settings.value.backgroundImageId)
)

const loadReaderBackgrounds = async () => {
  readerBackgroundsLoading.value = true
  try {
    const { data } = await api.get<ReaderBackgroundDto[]>('/api/reader-backgrounds')
    customReaderBackgrounds.value = data.map(toReaderBackgroundOption)
    if (settings.value.backgroundImageId !== 'none' && !selectedReaderBackground.value) {
      settings.value.backgroundImageId = 'none'
    }
  } catch (error) {
    console.error('Failed to load reader backgrounds:', error)
  } finally {
    readerBackgroundsLoading.value = false
  }
}

const readerStyle = computed(() => {
  const colors = getResolvedColors(settings.value.backgroundColor)
  const widthOption = widthOptions.find(w => w.value === settings.value.contentWidth) || widthOptions[1]
  const isDoubleScreen = settings.value.screenMode === 'double'
  return {
    fontFamily: resolveReaderFontFamily(settings.value.fontFamily),
    fontSize: `${settings.value.fontSize}px`,
    lineHeight: settings.value.lineHeight,
    backgroundColor: colors.bg,
    backgroundImage: selectedReaderBackground.value
      ? `url(${JSON.stringify(selectedReaderBackground.value.imageUrl)})`
      : 'none',
    backgroundPosition: 'center',
    backgroundRepeat: 'no-repeat',
    backgroundSize: 'cover',
    color: colors.text,
    // 两屏模式下不限制宽度，让两栏均匀分布
    maxWidth: isDoubleScreen ? '100%' : widthOption.maxWidth,
  }
})

const pageTurnZoneStyle = computed(() => ({
  '--reader-content-width': readerStyle.value.maxWidth,
}))

const readerHeaderStyle = computed(() => ({
  '--reader-content-width': readerStyle.value.maxWidth,
}))

// 两屏模式下的内容样式（仅用于 TXT/MD）
const contentStyle = computed(() => {
  const isDoubleScreen = settings.value.screenMode === 'double'
  const isTxtOrMd = book.value?.format === 'txt' || book.value?.format === 'md'
  if (!isTxtOrMd) return {}
  return {
    columnCount: isDoubleScreen ? 2 : 1,
    columnGap: isDoubleScreen ? '40px' : '0',
    columnRule: isDoubleScreen ? '1px solid var(--border-color-light)' : 'none',
    height: isDoubleScreen ? '100%' : 'auto',
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
  if (!isPaginationMode.value || book.value?.format === 'epub') {
    return content.value
  }
  const pageSize = calculatePageSize()
  const start = currentPage.value * pageSize
  const end = start + pageSize
  return content.value.slice(start, end)
})

// 获取当前页中某个本地索引对应原始 content 数组的索引
const getOriginalIndex = (localIndex: number): number => {
  if (!isPaginationMode.value || book.value?.format === 'epub') {
    return localIndex
  }
  const pageSize = calculatePageSize()
  return currentPage.value * pageSize + localIndex
}

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
  if (isPaginationMode.value && content.value.length > 0) {
    const pageSize = calculatePageSize()
    totalPages.value = Math.ceil(content.value.length / pageSize)
  } else {
    totalPages.value = 0
  }
}

// 检查内容是否溢出，如果溢出则减少每页内容
const checkAndAdjustPageSize = () => {
  if (!isPaginationMode.value) return

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
    const pageSize = calculatePageSize()
    const currentContentIndex = page * pageSize
    const chapter = [...tocItems.value]
      .reverse()
      .find(item => item.index <= currentContentIndex)
    if (chapter) currentChapterName.value = chapter.title
    progress.value = totalPages.value <= 1
      ? 100
      : Math.round((page / (totalPages.value - 1)) * 100)
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
  // 如果设置面板打开，不处理按键
  if (showSettings.value) return

  if ((e.metaKey || e.ctrlKey) && e.key.toLocaleLowerCase() === 'f') {
    e.preventDefault()
    openSearchPanel()
    return
  }

  if (e.key === 'Escape' && showSidePanel.value) {
    closeAllPanels()
    return
  }

  const target = e.target as HTMLElement | null
  if (
    target?.closest('input, textarea, select, button, [contenteditable="true"]')
  ) {
    return
  }

  // EPUB 格式
  if (book.value?.format === 'epub' && rendition) {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault()
      prevPage()
    } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown' || e.key === 'PageDown' || e.key === ' ') {
      e.preventDefault()
      nextPage()
    }
    return
  }

  if (book.value?.format === 'pdf') {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault()
      pdfReader.value?.previous()
    } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown' || e.key === 'PageDown' || e.key === ' ') {
      e.preventDefault()
      pdfReader.value?.next()
    } else if (e.key === 'Home') {
      e.preventDefault()
      void pdfReader.value?.goToPage(1)
    } else if (e.key === 'End') {
      e.preventDefault()
      void pdfReader.value?.goToPage(pdfTotalPages.value)
    }
    return
  }

  // TXT/MD 翻页模式
  if (isPaginationMode.value) {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault()
      prevTextPage()
    } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown' || e.key === 'PageDown' || e.key === ' ') {
      e.preventDefault()
      nextTextPage()
    }
    return
  }

  // TXT/MD 滚动模式
  const readerBody = document.querySelector('.reader-body')
  if (!readerBody) return

  const scrollStep = 200
  const pageScroll = readerBody.clientHeight * 0.8

  if (e.key === 'ArrowUp') {
    e.preventDefault()
    readerBody.scrollTop -= scrollStep
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    readerBody.scrollTop += scrollStep
  } else if (e.key === 'PageUp') {
    e.preventDefault()
    readerBody.scrollTop -= pageScroll
  } else if (e.key === 'PageDown' || e.key === ' ') {
    e.preventDefault()
    readerBody.scrollTop += pageScroll
  } else if (e.key === 'Home') {
    e.preventDefault()
    readerBody.scrollTop = 0
  } else if (e.key === 'End') {
    e.preventDefault()
    readerBody.scrollTop = readerBody.scrollHeight
  }
}

const bindEpubKeyboard = (contents: any) => {
  const document = contents?.document as Document | undefined
  if (!document || epubKeyboardDocuments.has(document)) return

  document.addEventListener('keydown', handleKeydown, true)
  epubKeyboardDocuments.add(document)
}

const unbindEpubKeyboard = (contents: any) => {
  const document = contents?.document as Document | undefined
  if (!document || !epubKeyboardDocuments.has(document)) return

  document.removeEventListener('keydown', handleKeydown, true)
  epubKeyboardDocuments.delete(document)
}

const clearEpubKeyboardBindings = () => {
  epubKeyboardDocuments.forEach(document => {
    document.removeEventListener('keydown', handleKeydown, true)
  })
  epubKeyboardDocuments.clear()
}

// 面板显示状态
const showSidePanel = computed(() =>
  showSearch.value || showToc.value || showBookmarks.value || showHighlights.value,
)
const supportsPageTurning = computed(() =>
  ['epub', 'txt', 'md', 'html', 'pdf'].includes(book.value?.format || '')
)
const showPageNavigation = computed(() =>
  supportsPageTurning.value && !showSidePanel.value && !showSettings.value
)
const canGoPrevious = computed(() => {
  if (book.value?.format === 'epub') return true
  if (book.value?.format === 'pdf') return pdfCurrentPage.value > 1
  if ((book.value?.format === 'txt' || book.value?.format === 'md') && isPaginationMode.value) {
    return currentPage.value > 0
  }
  return scrollCurrentPage.value > 1
})
const canGoNext = computed(() => {
  if (book.value?.format === 'epub') return true
  if (book.value?.format === 'pdf') return pdfCurrentPage.value < pdfTotalPages.value
  if ((book.value?.format === 'txt' || book.value?.format === 'md') && isPaginationMode.value) {
    return currentPage.value < totalPages.value - 1
  }
  return scrollCurrentPage.value < scrollTotalPages.value
})

// 阅读进度保存相关
let saveTimer: ReturnType<typeof setTimeout> | null = null
let readingHeartbeatTimer: ReturnType<typeof setInterval> | null = null
let activeReadingStartedAt = 0
let accumulatedReadingMillis = 0
const readingSessionId = typeof crypto.randomUUID === 'function'
  ? crypto.randomUUID()
  : `reader-${Date.now()}-${Math.random().toString(16).slice(2)}`
const savedCfi = ref<string | null>(null)

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    loadError.value = ''
    // 先获取书籍信息
    book.value = await bookStore.fetchBookById(id)
    const versionsResponse = await api.get(`/api/books/${id}/versions`)
    versions.value = versionsResponse.data || []
    const requestedVersionId = Number(route.query.versionId)
    const requestedVersion = versions.value.find(
      version => version.id === requestedVersionId,
    )
    const version = requestedVersion
      || versions.value.find(item => item.primaryVersion)
      || versions.value[0]
    if (!version) {
      throw new Error('书籍没有可阅读版本')
    }
    selectedVersionId.value = version.id
    book.value = {
      ...book.value,
      format: version.format,
      fileSize: version.fileSize,
      chapterCount: version.chapterCount,
    }

    // 阅读进度会影响 EPUB 首次定位，先发起请求但不阻塞书籍下载。
    const progressPromise = loadSavedProgress(id)
    const accessoryPromises = [
      progressPromise,
      loadBookmarks(id),
      loadHighlights(id),
    ]

    // 根据格式加载内容
    if (book.value.format === 'txt' || book.value.format === 'md') {
      accessoryPromises.push(loadTextContent())
    } else if (book.value.format === 'html') {
      accessoryPromises.push(loadHtmlContent())
    } else if (book.value.format === 'pdf') {
      accessoryPromises.push(progressPromise.then(loadPdfContent))
    }

    // EPUB 文件、解析模块和附属数据并行加载，减少进入阅读页后的空白等待。
    if (book.value.format === 'epub') {
      await nextTick()
      await initEpub(progressPromise)
      await Promise.all(accessoryPromises)
    } else {
      await Promise.all(accessoryPromises)
    }
    await nextTick()
    renderAllHighlights()

    resumeReadingClock()
    if (!readingHeartbeatTimer) {
      readingHeartbeatTimer = window.setInterval(() => void saveReadingTime(), 30_000)
    }
    void api.post(withVersion(`/api/books/${id}/open`)).catch(error => {
      console.error('Failed to record book open:', error)
    })
  } catch (error) {
    console.error('Failed to load book:', error)
    loadError.value = error instanceof Error ? error.message : '书籍加载失败，请稍后重试'
    book.value = null
  } finally {
    loading.value = false
    await nextTick()
    await applyRequestedChapter()
  }
}

const retryLoadBook = async () => {
  loading.value = true
  loadError.value = ''
  content.value = []
  htmlContent.value = ''
  pdfSource.value = null
  pdfCurrentPage.value = 1
  pdfTotalPages.value = 0
  tocItems.value = []
  searchSequence += 1
  searchResults.value = []
  searchCompleted.value = false
  searchError.value = ''
  clearCurrentSearchHighlight()
  if (bookInstance) {
    clearEpubKeyboardBindings()
    bookInstance.destroy()
    bookInstance = null
    rendition = null
  }
  await loadBook()
}

const applyRequestedChapter = async () => {
  if (!book.value) return
  const chapterTitle = typeof route.query.chapterTitle === 'string'
    ? route.query.chapterTitle
    : ''

  if ((book.value.format === 'txt' || book.value.format === 'md') && chapterTitle) {
    const chapter = tocItems.value.find(item => item.title === chapterTitle)
    if (chapter) await jumpToTextChapter(chapter, 'auto')
  }
}

/**
 * 加载已保存的阅读进度
 */
const loadSavedProgress = async (bookId: number) => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(
      withVersion(`/api/reading-progress/book/${bookId}`),
      {
      headers: { Authorization: `Bearer ${token}` }
      },
    )
    if (response.ok) {
      const data = await response.json()
      console.log('[Reader] Loaded progress:', data)
      if (data.totalProgress > 0) {
        progress.value = data.totalProgress
      }
      if (data.currentChapterTitle) {
        currentChapterName.value = data.currentChapterTitle
      } else if (data.currentChapter && !data.currentChapter.startsWith('epubcfi(')) {
        currentChapterName.value = data.currentChapter
      }
      if (data.locator) {
        try {
          savedLocator.value = JSON.parse(data.locator) as ReaderLocator
        } catch {
          savedLocator.value = null
        }
      }
      if (book.value?.format === 'epub' && data.currentChapter) {
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
    await nextTick()
    renderAllHighlights()
  } catch (error) {
    console.error('Failed to load highlights:', error)
  }
}

const closeHighlightEditor = () => {
  highlightEditor.value = null
  window.getSelection()?.removeAllRanges()
}

const openHighlightEditor = (value: Omit<HighlightEditorState, 'note' | 'color'>) => {
  if (!value.text.trim()) return
  highlightEditor.value = { ...value, color: '#ffe066', note: '' }
}

const editHighlight = (highlight: Highlight) => {
  highlightEditor.value = {
    id: highlight.id,
    cfiRange: highlight.cfiRange,
    text: highlight.text,
    chapter: highlight.chapter || '',
    color: highlight.color || '#ffe066',
    note: highlight.note || '',
  }
}

const saveHighlight = async () => {
  if (!book.value || !highlightEditor.value) return
  savingHighlight.value = true
  try {
    const editor = highlightEditor.value
    const payload = {
      cfiRange: editor.cfiRange,
      text: editor.text,
      color: editor.color,
      chapter: editor.chapter,
      note: editor.note,
    }
    const response = editor.id
      ? await api.put(`/api/books/${book.value.id}/highlights/${editor.id}`, payload)
      : await api.post(`/api/books/${book.value.id}/highlights`, payload)
    const saved = response.data as Highlight
    const existingIndex = highlights.value.findIndex(item => item.id === saved.id)
    if (existingIndex >= 0) highlights.value.splice(existingIndex, 1, saved)
    else highlights.value.unshift(saved)
    highlightEditor.value = null
    await nextTick()
    renderAllHighlights()
    message.success(editor.id ? '批注已更新' : '高亮已保存')
  } catch {
    message.error('高亮保存失败')
  } finally {
    savingHighlight.value = false
  }
}

const closestReaderBlock = (node: Node | null) => {
  const element = node?.nodeType === Node.ELEMENT_NODE
    ? node as Element
    : node?.parentElement
  return element?.closest<HTMLElement>('[data-reader-index]') || null
}

const captureDocumentSelection = () => {
  if (highlightEditor.value || book.value?.format === 'epub') return
  window.setTimeout(() => {
    const selection = window.getSelection()
    if (!selection || selection.isCollapsed || !selection.rangeCount) return
    const text = selection.toString().replace(/\s+/g, ' ').trim().slice(0, 4000)
    if (!text) return
    const range = selection.getRangeAt(0)
    const reader = document.querySelector<HTMLElement>('.reader-body')
    if (!reader || !reader.contains(range.commonAncestorContainer)) return
    if (book.value?.format === 'txt' || book.value?.format === 'md') {
      const startBlock = closestReaderBlock(range.startContainer)
      const endBlock = closestReaderBlock(range.endContainer)
      if (!startBlock || !endBlock) return
      const startIndex = Number(startBlock.dataset.readerIndex)
      const endIndex = Number(endBlock.dataset.readerIndex)
      openHighlightEditor({
        cfiRange: `text:${startIndex}:${range.startOffset}:${endIndex}:${range.endOffset}`,
        text,
        chapter: currentChapterName.value,
      })
      return
    }
    if (book.value?.format === 'html') {
      const htmlRoot = document.querySelector<HTMLElement>('.reader-html')
      if (!htmlRoot || !htmlRoot.contains(range.commonAncestorContainer)) return
      const beforeStart = document.createRange()
      beforeStart.selectNodeContents(htmlRoot)
      beforeStart.setEnd(range.startContainer, range.startOffset)
      const beforeEnd = document.createRange()
      beforeEnd.selectNodeContents(htmlRoot)
      beforeEnd.setEnd(range.endContainer, range.endOffset)
      openHighlightEditor({
        cfiRange: `html:${beforeStart.toString().length}:${beforeEnd.toString().length}`,
        text,
        chapter: currentChapterName.value || '正文',
      })
    }
  }, 0)
}

const textRangeFromLocation = (location: string): Range | null => {
  const match = /^text:(\d+):(\d+):(\d+):(\d+)$/.exec(location)
  if (!match) return null
  const start = document.querySelector<HTMLElement>(`[data-reader-index="${match[1]}"]`)
  const end = document.querySelector<HTMLElement>(`[data-reader-index="${match[3]}"]`)
  const startNode = start?.firstChild
  const endNode = end?.firstChild
  if (!startNode || !endNode) return null
  const range = document.createRange()
  range.setStart(startNode, Math.min(Number(match[2]), startNode.textContent?.length || 0))
  range.setEnd(endNode, Math.min(Number(match[4]), endNode.textContent?.length || 0))
  return range
}

const htmlRangeFromLocation = (location: string): Range | null => {
  const match = /^html:(\d+):(\d+)$/.exec(location)
  const root = document.querySelector<HTMLElement>('.reader-html')
  if (!match || !root) return null
  const offsets = [Number(match[1]), Number(match[2])]
  const points: Array<{ node: Node; offset: number } | null> = [null, null]
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let consumed = 0
  let node: Node | null
  while ((node = walker.nextNode())) {
    const length = node.textContent?.length || 0
    offsets.forEach((offset, index) => {
      if (!points[index] && offset <= consumed + length) {
        points[index] = { node: node!, offset: Math.max(0, offset - consumed) }
      }
    })
    consumed += length
  }
  if (!points[0] || !points[1]) return null
  const range = document.createRange()
  range.setStart(points[0].node, points[0].offset)
  range.setEnd(points[1].node, points[1].offset)
  return range
}

const highlightStyleId = 'reader-highlight-rules'
const textHighlightNames = new Set<string>()

const clearDocumentHighlights = () => {
  const registry = (CSS as any).highlights
  textHighlightNames.forEach(name => registry?.delete(name))
  textHighlightNames.clear()
  document.getElementById(highlightStyleId)?.remove()
}

const renderDocumentHighlights = () => {
  clearDocumentHighlights()
  const registry = (CSS as any).highlights
  const HighlightConstructor = (window as any).Highlight
  if (!registry || !HighlightConstructor) return
  const rules: string[] = []
  highlights.value.forEach(highlight => {
    const range = highlight.cfiRange.startsWith('text:')
      ? textRangeFromLocation(highlight.cfiRange)
      : highlight.cfiRange.startsWith('html:')
        ? htmlRangeFromLocation(highlight.cfiRange)
        : null
    if (!range) return
    const name = `reader-highlight-${highlight.id}`
    registry.set(name, new HighlightConstructor(range))
    textHighlightNames.add(name)
    rules.push(`::highlight(${name}){background-color:${highlight.color || '#ffe066'};color:inherit}`)
  })
  if (rules.length) {
    const style = document.createElement('style')
    style.id = highlightStyleId
    style.textContent = rules.join('\n')
    document.head.appendChild(style)
  }
}

const renderEpubHighlights = () => {
  if (!rendition?.annotations) return
  highlights.value.filter(item => item.cfiRange?.startsWith('epubcfi(')).forEach(highlight => {
    try {
      rendition.annotations.remove(highlight.cfiRange, 'highlight')
      rendition.annotations.highlight(
        highlight.cfiRange,
        { highlightId: highlight.id },
        () => editHighlight(highlight),
        `reader-epub-highlight-${highlight.id}`,
        { fill: highlight.color || '#ffe066', 'fill-opacity': '0.42', 'mix-blend-mode': 'multiply' },
      )
    } catch (error) {
      console.warn('[Reader] Failed to render highlight:', highlight.id, error)
    }
  })
}

const renderAllHighlights = () => {
  if (book.value?.format === 'epub') renderEpubHighlights()
  else renderDocumentHighlights()
}

const normalizeBookmarkExcerpt = (value?: string | null) =>
  (value || '').replace(/\s+/g, ' ').trim().slice(0, 160)

const getCurrentBookmarkExcerpt = () => {
  if (book.value?.format === 'epub' && rendition) {
    const epubText = rendition.getContents()
      .map((contents: any) => contents?.document?.body?.innerText || '')
      .find((text: string) => text.trim())
    return normalizeBookmarkExcerpt(epubText)
  }
  if (book.value?.format === 'pdf') return `PDF 第 ${pdfCurrentPage.value} 页`

  const readerBody = document.querySelector<HTMLElement>('.reader-body')
  if (!readerBody) return ''
  const bodyRect = readerBody.getBoundingClientRect()
  const contentElements = Array.from(
    readerBody.querySelectorAll<HTMLElement>('.reader-text > *, .reader-html > *'),
  )
  const visibleElement = contentElements.find(element => {
    const rect = element.getBoundingClientRect()
    return rect.bottom > bodyRect.top + 12 && rect.top < bodyRect.bottom - 12
  })
  return normalizeBookmarkExcerpt(visibleElement?.textContent || readerBody.textContent)
}

const getCurrentBookmarkChapterIndex = () => {
  const chapterName = currentChapterName.value.trim()
  const index = tocItems.value.findIndex(item => {
    if (book.value?.format === 'epub' && currentTocHref.value && item.href) {
      return item.href === currentTocHref.value
    }
    return chapterName && item.title.trim() === chapterName
  })
  return index >= 0 ? index + 1 : undefined
}

/**
 * 添加书签
 */
const handleAddBookmark = async () => {
  if (!book.value) return

  try {
    const bookmarkData: any = {
      title: currentChapterName.value || '书签',
      excerpt: getCurrentBookmarkExcerpt(),
      chapter: currentChapterName.value,
      chapterIndex: getCurrentBookmarkChapterIndex(),
    }

    if (book.value.format === 'epub' && rendition) {
      const location = rendition.currentLocation()
      if (location?.start?.cfi) {
        bookmarkData.cfi = location.start.cfi
      }
    } else if (book.value.format === 'pdf') {
      bookmarkData.page = pdfCurrentPage.value
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
  } else if (book.value?.format === 'pdf' && bookmark.page) {
    await pdfReader.value?.goToPage(bookmark.page)
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
const handleGotoHighlight = async (highlight: Highlight) => {
  closeAllPanels()
  if (book.value?.format === 'epub' && rendition && highlight.cfiRange.startsWith('epubcfi(')) {
    await rendition.display(highlight.cfiRange)
    return
  }
  const textMatch = /^text:(\d+):/.exec(highlight.cfiRange)
  if (textMatch) {
    const textIndex = Number(textMatch[1])
    if (isPaginationMode.value) {
      currentPage.value = Math.floor(textIndex / calculatePageSize())
      await nextTick()
      renderDocumentHighlights()
    }
    document.querySelector<HTMLElement>(`[data-reader-index="${textIndex}"]`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  const range = htmlRangeFromLocation(highlight.cfiRange)
  const element = range?.startContainer.parentElement
  element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

/**
 * 删除高亮
 */
const handleDeleteHighlight = async (highlight: Highlight) => {
  const result = await confirm('确定要删除这个高亮吗？')
  if (result) {
    try {
      await api.delete(`/api/books/${book.value.id}/highlights/${highlight.id}`)
      if (book.value?.format === 'epub' && rendition?.annotations) {
        rendition.annotations.remove(highlight.cfiRange, 'highlight')
      }
      highlights.value = highlights.value.filter(h => h.id !== highlight.id)
      renderAllHighlights()
      message.success('高亮已删除')
    } catch (error) {
      message.error('删除失败')
    }
  }
}

/**
 * 保存阅读进度（防抖）
 */
const saveProgress = (
  totalProgress: number,
  currentChapter?: string,
  locator: ReaderLocator | null = createCurrentLocator(totalProgress),
) => {
  if (!book.value) return

  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    try {
      const token = localStorage.getItem('token')
      await fetch(withVersion(`/api/reading-progress/book/${book.value.id}`), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          currentChapter: currentChapter || '',
          currentChapterTitle: currentChapterName.value || '',
          chapterProgress: locator?.chapterProgress || 0,
          totalProgress: Math.round(totalProgress),
          locator: locator ? JSON.stringify(locator) : null,
        })
      })
    } catch (error) {
      console.error('Failed to save reading progress:', error)
    }
  }, 1000)
}

const chapterAtTextIndex = (textIndex: number) => {
  const ordered = tocItems.value.filter(item => Number.isFinite(item.index))
  let chapterIndex = -1
  for (let index = 0; index < ordered.length; index += 1) {
    if (ordered[index].index <= textIndex) chapterIndex = index
    else break
  }
  const chapter = chapterIndex >= 0 ? ordered[chapterIndex] : undefined
  const next = chapterIndex >= 0 ? ordered[chapterIndex + 1] : undefined
  return { chapter, next }
}

const textChapterProgress = (textIndex: number) => {
  const { chapter, next } = chapterAtTextIndex(textIndex)
  if (!chapter) return 0
  const end = next?.index ?? content.value.length
  if (end <= chapter.index) return 0
  return Math.max(0, Math.min(100, Math.round(
    ((textIndex - chapter.index) / (end - chapter.index)) * 100,
  )))
}

const visibleTextIndex = () => {
  if (isPaginationMode.value) return currentPage.value * calculatePageSize()
  const readerBody = document.querySelector<HTMLElement>('.reader-body')
  if (!readerBody) return 0
  const bodyRect = readerBody.getBoundingClientRect()
  const visible = Array.from(readerBody.querySelectorAll<HTMLElement>('[data-reader-index]'))
    .find(element => element.getBoundingClientRect().bottom > bodyRect.top + 12)
  return Number(visible?.dataset.readerIndex || 0)
}

const createCurrentLocator = (totalProgress = progress.value): ReaderLocator | null => {
  if (!book.value) return null
  if (book.value.format === 'epub') {
    const location = rendition?.currentLocation?.()
    const displayed = location?.start?.displayed
    const chapterProgress = displayed?.total > 0
      ? Math.round(((displayed.page - 1) / Math.max(1, displayed.total - 1)) * 100)
      : 0
    return {
      format: 'epub',
      cfi: location?.start?.cfi || savedCfi.value || undefined,
      href: location?.start?.href || currentTocHref.value || undefined,
      chapterTitle: currentChapterName.value || undefined,
      chapterProgress,
      totalProgress: Math.round(totalProgress),
    }
  }
  if (book.value.format === 'html') {
    const readerBody = document.querySelector<HTMLElement>('.reader-body')
    const htmlRoot = document.querySelector<HTMLElement>('.reader-html')
    const bodyRect = readerBody?.getBoundingClientRect()
    const visibleElement = bodyRect
      ? Array.from(htmlRoot?.querySelectorAll<HTMLElement>('*') || [])
        .find(element => element.children.length === 0
          && element.getBoundingClientRect().bottom > bodyRect.top + 12)
      : undefined
    let htmlOffset = 0
    if (htmlRoot && visibleElement) {
      const before = document.createRange()
      before.selectNodeContents(htmlRoot)
      before.setEndBefore(visibleElement)
      htmlOffset = before.toString().length
    }
    return {
      format: 'html',
      htmlOffset,
      chapterTitle: currentChapterName.value || '正文',
      chapterProgress: Math.round(totalProgress),
      totalProgress: Math.round(totalProgress),
      excerpt: normalizeBookmarkExcerpt(visibleElement?.textContent),
    }
  }
  if (book.value.format === 'pdf') {
    return {
      format: 'pdf',
      pdfPage: pdfCurrentPage.value,
      pdfTotalPages: pdfTotalPages.value,
      chapterTitle: `第 ${pdfCurrentPage.value} 页`,
      chapterProgress: Math.round(totalProgress),
      totalProgress: Math.round(totalProgress),
    }
  }
  const textIndex = visibleTextIndex()
  const paragraph = content.value[textIndex] || ''
  return {
    format: book.value.format,
    textIndex,
    chapterTitle: currentChapterName.value || chapterAtTextIndex(textIndex).chapter?.title,
    chapterProgress: textChapterProgress(textIndex),
    totalProgress: Math.round(totalProgress),
    excerpt: paragraph.replace(/\s+/g, ' ').trim().slice(0, 160) || undefined,
  }
}

/**
 * 保存阅读时长
 */
const resumeReadingClock = () => {
  if (!activeReadingStartedAt && !document.hidden) activeReadingStartedAt = Date.now()
}

const pauseReadingClock = () => {
  if (!activeReadingStartedAt) return
  accumulatedReadingMillis += Date.now() - activeReadingStartedAt
  activeReadingStartedAt = 0
}

const currentReadingSeconds = () => Math.floor((accumulatedReadingMillis
  + (activeReadingStartedAt ? Date.now() - activeReadingStartedAt : 0)) / 1000)

const saveReadingTime = async (keepalive = false) => {
  if (!book.value) return
  const elapsedSeconds = currentReadingSeconds()
  if (elapsedSeconds < 5) return
  try {
    const token = localStorage.getItem('token')
    await fetch(withVersion(`/api/reading-progress/book/${book.value.id}/time`), {
      method: 'PUT',
      keepalive,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        seconds: elapsedSeconds,
        sessionId: readingSessionId,
      })
    })
  } catch (error) {
    console.error('Failed to save reading time:', error)
  }
}

const handleVisibilityChange = () => {
  if (document.hidden) {
    pauseReadingClock()
    void saveReadingTime(true)
  } else {
    resumeReadingClock()
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
  const paragraphs = splitTextIntoParagraphs(processedText)
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

const splitTextIntoParagraphs = (text: string): string[] =>
  text
    .split(/\n\n+/)
    .flatMap(paragraph => {
      const trimmed = paragraph.trim()
      if (!trimmed) return []
      if (trimmed.length <= MAX_PARAGRAPH_LENGTH) return [trimmed]
      const chunks: string[] = []
      for (let start = 0; start < trimmed.length; start += MAX_PARAGRAPH_LENGTH) {
        chunks.push(trimmed.slice(start, start + MAX_PARAGRAPH_LENGTH))
      }
      return chunks
    })

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
  if (book.value?.format === 'pdf') return item.page === pdfCurrentPage.value
  return item.title === currentChapterName.value
}

const loadTextContent = async () => {
  try {
    const token = localStorage.getItem('token')

    const response = await fetch(withVersion(
      `/api/books/${book.value.id}/content-processed`,
    ), {
      headers: { Authorization: `Bearer ${token}` }
    })

    if (response.ok) {
      const data = await response.json()
      content.value = splitTextIntoParagraphs(data.text)
      enableLargeTextPerformanceMode()

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
      const rawResponse = await fetch(
        withVersion(`/api/books/${book.value.id}/content`),
        {
        headers: { Authorization: `Bearer ${token}` }
        },
      )
      const text = await rawResponse.text()
      content.value = splitTextIntoParagraphs(text)
      enableLargeTextPerformanceMode()
      tocItems.value = parseChapters(content.value)
    }

    await nextTick()
    updateTotalPages()
    restoreScrollPosition()
  } catch (error) {
    console.error('Failed to load text content:', error)
    throw new Error('文本内容加载失败')
  }
}

const enableLargeTextPerformanceMode = () => {
  if (settings.value.paginationMode) {
    performancePaginationMode.value = false
    return
  }
  performancePaginationMode.value =
    content.value.length >= LARGE_TEXT_PARAGRAPH_THRESHOLD
    || Number(book.value?.fileSize || 0) >= LARGE_TEXT_FILE_SIZE_THRESHOLD
  if (performancePaginationMode.value) {
    currentPage.value = 0
  }
}

const togglePaginationMode = () => {
  if (performancePaginationMode.value && !settings.value.paginationMode) {
    performancePaginationMode.value = false
    updateTotalPages()
    return
  }
  settings.value.paginationMode = !settings.value.paginationMode
  if (settings.value.paginationMode) {
    performancePaginationMode.value = false
  }
}

/**
 * 恢复滚动位置
 */
const restoreScrollPosition = () => {
  const locator = savedLocator.value
  const locatorIndex = locator && locator.format === book.value?.format
    ? locator.textIndex
    : undefined
  if (locatorIndex !== undefined) {
    const safeIndex = Math.max(0, Math.min(locatorIndex, Math.max(0, content.value.length - 1)))
    if (isPaginationMode.value && totalPages.value > 0) {
      currentPage.value = Math.min(totalPages.value - 1, Math.floor(safeIndex / calculatePageSize()))
    } else {
      nextTick(() => document.querySelector<HTMLElement>(`[data-reader-index="${safeIndex}"]`)
        ?.scrollIntoView({ block: 'start' }))
    }
    return
  }
  if (progress.value > 0) {
    if (isPaginationMode.value && totalPages.value > 0) {
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

  const readerBody = document.querySelector<HTMLElement>('.reader-body')
  if (!readerBody) return

  const viewportHeight = Math.max(readerBody.clientHeight, 1)
  scrollTotalPages.value = Math.max(1, Math.ceil(readerBody.scrollHeight / viewportHeight))

  const maxScroll = readerBody.scrollHeight - readerBody.clientHeight
  scrollCurrentPage.value = maxScroll <= 0
    ? 1
    : Math.min(
      scrollTotalPages.value,
      Math.round((readerBody.scrollTop / maxScroll) * (scrollTotalPages.value - 1)) + 1,
    )
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

const loadHtmlContent = async () => {
  try {
    const response = await api.get(withVersion(`/api/books/${book.value.id}/content-sanitized`))
    htmlContent.value = response.data?.html || ''
    await nextTick()
    if (savedLocator.value?.format === 'html'
        && savedLocator.value.htmlOffset !== undefined) {
      const range = htmlRangeFromLocation(
        `html:${savedLocator.value.htmlOffset}:${savedLocator.value.htmlOffset}`,
      )
      range?.startContainer.parentElement?.scrollIntoView({ block: 'start' })
    } else {
      restoreScrollPosition()
    }
  } catch (error) {
    console.error('Failed to load HTML content:', error)
    throw new Error('HTML 内容加载失败')
  }
}

const loadPdfContent = async () => {
  if (!book.value) return
  const token = localStorage.getItem('token')
  pdfSource.value = {
    url: withVersion(`/api/books/${book.value.id}/content`),
    httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  }
}

const handlePdfPageChange = (value: { page: number; total: number; progress: number }) => {
  pdfCurrentPage.value = value.page
  pdfTotalPages.value = value.total
  progress.value = value.progress
  currentChapterName.value = `第 ${value.page} 页`
  saveProgress(value.progress, currentChapterName.value, {
    format: 'pdf',
    pdfPage: value.page,
    pdfTotalPages: value.total,
    chapterTitle: currentChapterName.value,
    chapterProgress: value.progress,
    totalProgress: value.progress,
  })
}

const handlePdfOutline = (items: Array<{ title: string; page: number; level: number }>) => {
  tocItems.value = items.map(item => ({
    title: item.title,
    label: item.title,
    index: item.page - 1,
    page: item.page,
    level: item.level,
  }))
}

const handlePdfError = (error: string) => {
  console.error('[Reader] PDF reader error:', error)
}

const initEpub = async (progressReady: Promise<void> = Promise.resolve()) => {
  try {
    const token = localStorage.getItem('token')
    const contentVersion = encodeURIComponent(
      `${book.value.fileSize || 0}-${book.value.updatedAt || ''}`,
    )
    const contentUrl = withVersion(
      `/api/books/${book.value.id}/content?v=${contentVersion}`,
    )

    // epub.js 代码块和书籍二进制并行获取，浏览器可复用后端的版本化缓存。
    const [epubModule, arrayBuffer] = await Promise.all([
      import('epubjs'),
      fetch(contentUrl, {
        headers: { Authorization: `Bearer ${token}` },
        cache: 'default',
      }).then(async response => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`)
        return response.arrayBuffer()
      }),
    ])
    const ePub = epubModule.default

    bookInstance = ePub(arrayBuffer)

    // 解析 EPUB 与读取用户进度并行；仅在决定首个展示位置前等待进度。
    await Promise.all([bookInstance.ready, progressReady])

    // 部分 EPUB 未在 manifest 中完整声明图片，epub.js 不会自动替换这些路径。
    // 在章节序列化前直接从归档创建 Blob URL，同时兼容 SVG xlink:href。
    bookInstance.spine.hooks.content.register(resolveEpubChapterImages)

    const navigation = bookInstance.navigation
    if (navigation && navigation.toc) {
      tocItems.value = flattenToc(navigation.toc)
    }

    // 根据屏幕模式设置 spread
    const spreadMode = settings.value.screenMode === 'double' ? 'always' : 'none'

    rendition = bookInstance.renderTo(epubContainer.value!, {
      width: '100%',
      height: '100%',
      spread: spreadMode,
      allowScriptedContent: false,
    })

    rendition.on('relocated', (location: any) => {
      if (location && location.start) {
        const currentTocItem = findEpubTocItem(location.start.href)
        if (currentTocItem) {
          currentChapterName.value = currentTocItem.title
          currentTocHref.value = currentTocItem.href || ''
        }
        savedCfi.value = location.start.cfi
        currentLocation.value = location.start.displayed
          ? `${location.start.displayed.page} / ${location.start.displayed.total}`
          : ''
        if (bookInstance.locations && bookInstance.locations.length()) {
          const percentage = bookInstance.locations.percentageFromCfi(location.start.cfi)
          progress.value = Math.round(percentage * 100)
          saveProgress(progress.value, location.start.cfi, createCurrentLocator(progress.value))
        }
      }
    })

    rendition.on('selected', (cfiRange: string, contents: any) => {
      const text = contents?.window?.getSelection?.()?.toString?.()
        ?.replace(/\s+/g, ' ').trim().slice(0, 4000) || ''
      if (!text) return
      openHighlightEditor({
        cfiRange,
        text,
        chapter: currentChapterName.value,
      })
      contents?.window?.getSelection?.()?.removeAllRanges?.()
    })

    rendition.hooks.content.register((contents: any) => {
      applyThemeToContent(contents)
      bindEpubKeyboard(contents)
    })
    rendition.hooks.unloaded.register((view: any) => {
      unbindEpubKeyboard(view?.contents)
    })

    // 先显示内容，让用户立即看到书
    const requestedChapterHref = typeof route.query.chapterHref === 'string'
      ? route.query.chapterHref
      : ''
    if (requestedChapterHref) {
      await rendition.display(requestedChapterHref)
      currentTocHref.value = requestedChapterHref
    } else if (savedLocator.value?.format === 'epub' && savedLocator.value.cfi) {
      try {
        await rendition.display(savedLocator.value.cfi)
      } catch (e) {
        console.error('[Reader] Failed to restore locator CFI, falling back:', e)
        await rendition.display(savedCfi.value || undefined)
      }
    } else if (savedCfi.value) {
      try {
        await rendition.display(savedCfi.value)
      } catch (e) {
        console.error('[Reader] Failed to restore CFI, falling back to start:', e)
        await rendition.display()
      }
    } else {
      await rendition.display()
    }
    renderEpubHighlights()

    // 后台生成位置数据（不阻塞显示）
    bookInstance.locations.generate(1024).then(() => {
      console.log('[Reader] Locations generated')
      // 生成完成后更新一次进度
      if (rendition) {
        const location = rendition.currentLocation()
        if (location?.start?.cfi && bookInstance.locations.length()) {
          const percentage = bookInstance.locations.percentageFromCfi(location.start.cfi)
          progress.value = Math.round(percentage * 100)
        }
      }
    })

  } catch (error) {
    console.error('Failed to init EPUB:', error)
    throw new Error('EPUB 解析或渲染失败')
  }
}

const resolveEpubChapterImages = async (document: Document, section: any) => {
  if (!bookInstance?.archive || !document || !section?.url) return

  const imageReferences: Array<{
    element: Element
    attribute: 'src' | 'href' | 'xlink:href'
  }> = []

  document.querySelectorAll('img[src], input[type="image"][src]').forEach(element => {
    imageReferences.push({ element, attribute: 'src' })
  })
  document.querySelectorAll('svg image').forEach(element => {
    if (element.hasAttribute('href')) {
      imageReferences.push({ element, attribute: 'href' })
    } else if (element.hasAttribute('xlink:href')) {
      imageReferences.push({ element, attribute: 'xlink:href' })
    }
  })

  await Promise.all(imageReferences.map(async ({ element, attribute }) => {
    const originalUrl = element.getAttribute(attribute)
    if (
      !originalUrl
      || /^(?:data:|blob:|https?:|\/\/|#)/i.test(originalUrl)
    ) {
      return
    }

    try {
      const cleanUrl = originalUrl
        .split('#', 1)[0]
        .split('?', 1)[0]
        .replace(/\\/g, '/')
      const sectionUrl = new URL(section.url, 'https://epub.local/')
      const archivePath = new URL(cleanUrl, sectionUrl).pathname
      const blobUrl = await bookInstance.archive.createUrl(archivePath)

      if (attribute === 'xlink:href') {
        element.setAttributeNS(
          'http://www.w3.org/1999/xlink',
          'xlink:href',
          blobUrl,
        )
      } else {
        element.setAttribute(attribute, blobUrl)
      }
    } catch (error) {
      console.warn('[Reader] EPUB image could not be resolved:', originalUrl, error)
    }
  }))
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

const normalizeEpubHref = (href?: string): string => {
  if (!href) return ''
  const path = href.split('#', 1)[0].replace(/^\/+/, '')
  try {
    return decodeURIComponent(path)
  } catch {
    return path
  }
}

const findEpubTocItem = (href?: string): Chapter | undefined => {
  const currentHref = normalizeEpubHref(href)
  if (!currentHref) return undefined

  return tocItems.value.find(item => {
    const tocHref = normalizeEpubHref(item.href)
    if (!tocHref) return false
    return tocHref === currentHref
      || tocHref.endsWith(`/${currentHref}`)
      || currentHref.endsWith(`/${tocHref}`)
  })
}

const excerptAround = (text: string, index: number, length: number) => {
  const start = Math.max(0, index - 36)
  const end = Math.min(text.length, index + length + 56)
  return `${start > 0 ? '…' : ''}${text.slice(start, end).replace(/\s+/g, ' ')}${end < text.length ? '…' : ''}`
}

const chapterForParagraph = (paragraphIndex: number) => [...tocItems.value]
  .reverse()
  .find(item => item.index <= paragraphIndex)?.title || '正文'

const searchPlainText = (query: string) => {
  const normalizedQuery = query.toLocaleLowerCase()
  const results: ReaderSearchResult[] = []
  content.value.some((paragraph, paragraphIndex) => {
    const normalizedText = paragraph.toLocaleLowerCase()
    let fromIndex = 0
    while (results.length < SEARCH_RESULT_LIMIT) {
      const index = normalizedText.indexOf(normalizedQuery, fromIndex)
      if (index < 0) break
      results.push({
        id: `text-${paragraphIndex}-${index}`,
        excerpt: excerptAround(paragraph, index, query.length),
        locationLabel: chapterForParagraph(paragraphIndex),
        paragraphIndex,
        startOffset: index,
        endOffset: index + query.length,
      })
      fromIndex = index + Math.max(1, normalizedQuery.length)
    }
    return results.length >= SEARCH_RESULT_LIMIT
  })
  return results
}

const searchHtml = (query: string) => {
  const text = document.querySelector<HTMLElement>('.reader-html')?.textContent || ''
  const normalizedText = text.toLocaleLowerCase()
  const normalizedQuery = query.toLocaleLowerCase()
  const results: ReaderSearchResult[] = []
  let fromIndex = 0
  while (results.length < SEARCH_RESULT_LIMIT) {
    const index = normalizedText.indexOf(normalizedQuery, fromIndex)
    if (index < 0) break
    results.push({
      id: `html-${index}`,
      excerpt: excerptAround(text, index, query.length),
      locationLabel: '正文',
      htmlStart: index,
      htmlEnd: index + query.length,
    })
    fromIndex = index + Math.max(1, normalizedQuery.length)
  }
  return results
}

const searchEpub = async (query: string, sequence: number) => {
  if (!bookInstance?.spine) return []
  const results: ReaderSearchResult[] = []
  const sections = bookInstance.spine.spineItems || []
  for (const section of sections) {
    if (results.length >= SEARCH_RESULT_LIMIT || sequence !== searchSequence) break
    try {
      await section.load(bookInstance.load.bind(bookInstance))
      const matches = section.find(query) || []
      const chapter = findEpubTocItem(section.href)?.title || section.href || '正文'
      for (const match of matches) {
        if (results.length >= SEARCH_RESULT_LIMIT) break
        results.push({
          id: `epub-${results.length}-${match.cfi}`,
          excerpt: match.excerpt || query,
          locationLabel: chapter,
          cfi: match.cfi,
        })
      }
    } finally {
      section.unload?.()
    }
  }
  return results
}

const runSearchNow = async () => {
  const query = searchQuery.value.trim()
  const sequence = ++searchSequence
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  searchError.value = ''
  activeSearchResultId.value = ''
  clearCurrentSearchHighlight()
  pdfReader.value?.cancelSearch()
  if (!query || !book.value) {
    searchResults.value = []
    searchCompleted.value = false
    searching.value = false
    return
  }
  searching.value = true
  searchCompleted.value = false
  try {
    let results: ReaderSearchResult[] = []
    if (book.value.format === 'txt' || book.value.format === 'md') {
      results = searchPlainText(query)
    } else if (book.value.format === 'html') {
      results = searchHtml(query)
    } else if (book.value.format === 'epub') {
      results = await searchEpub(query, sequence)
    } else if (book.value.format === 'pdf') {
      const pdfResults = await pdfReader.value?.search(query, SEARCH_RESULT_LIMIT) || []
      results = pdfResults.map((result, index) => ({
        id: `pdf-${result.page}-${index}`,
        excerpt: result.excerpt,
        locationLabel: `第 ${result.page} 页`,
        page: result.page,
      }))
    }
    if (sequence === searchSequence) searchResults.value = results
  } catch (error) {
    console.error('[Reader] In-book search failed:', error)
    if (sequence === searchSequence) searchError.value = '书内搜索失败，请稍后重试'
  } finally {
    if (sequence === searchSequence) {
      searching.value = false
      searchCompleted.value = true
    }
  }
}

const scheduleSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => void runSearchNow(), 280)
}

const searchHighlightName = 'reader-search-current'
const searchHighlightStyleId = 'reader-search-current-style'
let activeEpubSearchCfi = ''

const clearCurrentSearchHighlight = () => {
  ;(CSS as any).highlights?.delete(searchHighlightName)
  document.getElementById(searchHighlightStyleId)?.remove()
  if (activeEpubSearchCfi && rendition?.annotations) {
    try {
      rendition.annotations.remove(activeEpubSearchCfi, 'underline')
    } catch { /* EPUB 章节卸载后无需处理 */ }
  }
  activeEpubSearchCfi = ''
}

const revealDocumentSearchRange = (range: Range | null) => {
  if (!range) return
  range.startContainer.parentElement?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  const registry = (CSS as any).highlights
  const HighlightConstructor = (window as any).Highlight
  if (!registry || !HighlightConstructor) return
  registry.set(searchHighlightName, new HighlightConstructor(range))
  const style = document.createElement('style')
  style.id = searchHighlightStyleId
  style.textContent = `::highlight(${searchHighlightName}){background:#ff9f43;color:inherit}`
  document.head.appendChild(style)
}

const goToSearchResult = async (result: ReaderSearchResult) => {
  activeSearchResultId.value = result.id
  clearCurrentSearchHighlight()
  if ((book.value?.format === 'txt' || book.value?.format === 'md')
      && result.paragraphIndex !== undefined) {
    if (isPaginationMode.value) {
      goToPage(Math.floor(result.paragraphIndex / calculatePageSize()))
      await nextTick()
    }
    const range = textRangeFromLocation(
      `text:${result.paragraphIndex}:${result.startOffset || 0}:${result.paragraphIndex}:${result.endOffset || 0}`,
    )
    revealDocumentSearchRange(range)
  } else if (book.value?.format === 'html'
      && result.htmlStart !== undefined && result.htmlEnd !== undefined) {
    revealDocumentSearchRange(htmlRangeFromLocation(`html:${result.htmlStart}:${result.htmlEnd}`))
  } else if (book.value?.format === 'epub' && result.cfi && rendition) {
    await rendition.display(result.cfi)
    activeEpubSearchCfi = result.cfi
    rendition.annotations.underline(
      result.cfi,
      {},
      undefined,
      'reader-epub-search-result',
      { stroke: '#ff9f43', 'stroke-width': '2px' },
    )
  } else if (book.value?.format === 'pdf' && result.page) {
    await pdfReader.value?.goToPage(result.page)
  }
}

const activatePanelTab = (panel: PanelTab) => {
  activeTab.value = panel
  showSearch.value = panel === 'search'
  showToc.value = panel === 'toc'
  showBookmarks.value = panel === 'bookmarks'
  showHighlights.value = panel === 'highlights'
  if (panel === 'search') nextTick(() => searchInput.value?.focus())
}

const handlePanelTabKeydown = (event: KeyboardEvent) => {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const currentIndex = panelTabs.findIndex(tab => tab.value === activeTab.value)
  const targetIndex = event.key === 'Home'
    ? 0
    : event.key === 'End'
      ? panelTabs.length - 1
      : (currentIndex + (event.key === 'ArrowRight' ? 1 : -1) + panelTabs.length) % panelTabs.length
  activatePanelTab(panelTabs[targetIndex].value)
  nextTick(() => {
    document.querySelectorAll<HTMLButtonElement>('.panel-tabs-nav .tab-btn')[targetIndex]?.focus()
  })
}

const openSearchPanel = () => activatePanelTab('search')

const togglePanel = (panel: PanelTab) => {
  const isOpen = (panel === 'search' && showSearch.value)
    || (panel === 'toc' && showToc.value)
    || (panel === 'bookmarks' && showBookmarks.value)
    || (panel === 'highlights' && showHighlights.value)
  if (isOpen) {
    closeAllPanels()
  } else {
    activatePanelTab(panel)
  }
}

const closeAllPanels = () => {
  showSearch.value = false
  showToc.value = false
  showBookmarks.value = false
  showHighlights.value = false
  clearCurrentSearchHighlight()
}

const jumpToTextChapter = async (
  item: Chapter,
  behavior: ScrollBehavior = 'smooth',
) => {
  if (isPaginationMode.value) {
    const pageSize = calculatePageSize()
    currentPage.value = Math.floor(item.index / pageSize)
    updateTotalPages()
    await nextTick()
  }
  const element = document.getElementById('chapter-' + item.index)
  if (element) {
    element.scrollIntoView({
      behavior,
      block: 'start',
    })
  }
  currentChapterName.value = item.title
}

const goToTocItem = async (item: Chapter | any) => {
  if (book.value?.format === 'epub' && rendition) {
    currentChapterName.value = item.title
    currentTocHref.value = item.href
    await rendition.display(item.href)
  } else if (book.value?.format === 'txt' || book.value?.format === 'md') {
    await jumpToTextChapter(item)
  } else if (book.value?.format === 'pdf' && item.page) {
    await pdfReader.value?.goToPage(item.page)
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

const turnPrevious = () => {
  if (!canGoPrevious.value) return
  if (book.value?.format === 'epub') {
    prevPage()
    return
  }
  if (book.value?.format === 'pdf') {
    pdfReader.value?.previous()
    return
  }
  if ((book.value?.format === 'txt' || book.value?.format === 'md') && isPaginationMode.value) {
    prevTextPage()
    return
  }
  const readerBody = document.querySelector<HTMLElement>('.reader-body')
  readerBody?.scrollBy({ top: -readerBody.clientHeight * 0.9, behavior: 'smooth' })
}

const turnNext = () => {
  if (!canGoNext.value) return
  if (book.value?.format === 'epub') {
    nextPage()
    return
  }
  if (book.value?.format === 'pdf') {
    pdfReader.value?.next()
    return
  }
  if ((book.value?.format === 'txt' || book.value?.format === 'md') && isPaginationMode.value) {
    nextTextPage()
    return
  }
  const readerBody = document.querySelector<HTMLElement>('.reader-body')
  readerBody?.scrollBy({ top: readerBody.clientHeight * 0.9, behavior: 'smooth' })
}

const goBack = () => {
  router.back()
}

const handleDownload = async () => {
  if (!book.value) return
  try {
    const response = await api.get(withVersion(`/api/books/${book.value.id}/content`), {
      responseType: 'blob',
    })
    const downloadUrl = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = downloadUrl
    anchor.download = `${book.value.title || 'book'}.${book.value.format || 'bin'}`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 0)
  } catch (error) {
    console.error('Failed to download book:', error)
    message.error('下载失败，请稍后重试')
  }
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

  const selectedManagedFontId = managedFontId(settings.value.fontFamily)
  const fontFamily = selectedManagedFontId != null
    ? fontStore.cssFamily(selectedManagedFontId)
    : settings.value.fontFamily === 'default'
      ? 'serif'
      : settings.value.fontFamily.split(',')[0].trim()
  const loadedManagedFont = selectedManagedFontId == null
    ? undefined
    : fontStore.getLoadedFont(selectedManagedFontId)

  const colors = getResolvedColors(settings.value.backgroundColor)

  contents.css('font-family', `${fontFamily}, serif`, true)
  contents.css('font-size', `${settings.value.fontSize}px`, true)
  contents.css('line-height', `${settings.value.lineHeight}`, true)
  contents.css('color', colors.text, true)
  const backgroundUrl = selectedReaderBackground.value
    ? new URL(selectedReaderBackground.value.imageUrl, window.location.origin).href
    : ''
  const backgroundImage = backgroundUrl ? `url(${JSON.stringify(backgroundUrl)})` : 'none'

  contents.css('background-color', colors.bg, true)
  contents.css('background-image', backgroundImage, true)
  contents.css('background-position', 'center', true)
  contents.css('background-repeat', 'no-repeat', true)
  contents.css('background-size', 'cover', true)

  try {
    const doc = contents.document
    if (doc) {
      const style = doc.createElement('style')
      style.textContent = `
        ${loadedManagedFont ? `
        @font-face {
          font-family: ${fontFamily};
          src: url("${loadedManagedFont.objectUrl}");
          font-style: normal;
          font-weight: normal;
          font-display: swap;
        }` : ''}
        * {
          font-family: ${fontFamily}, serif !important;
          font-size: ${settings.value.fontSize}px !important;
          line-height: ${settings.value.lineHeight} !important;
          color: ${colors.text} !important;
        }
        body {
          min-height: 100vh !important;
          background-color: ${colors.bg} !important;
          background-image: ${backgroundImage} !important;
          background-position: center !important;
          background-repeat: no-repeat !important;
          background-size: cover !important;
        }
        img,
        svg {
          max-width: 100% !important;
          max-height: 100% !important;
          object-fit: contain !important;
        }
        img {
          height: auto !important;
        }
        figure {
          max-width: 100% !important;
          margin-left: auto !important;
          margin-right: auto !important;
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
  return formatChinaDateTime(timeStr)
}

watch(() => settings.value, () => {
  applyEpubTheme()
  saveReaderSettings()
  updateTotalPages()
}, { deep: true })

// 监听翻页模式变化
watch(isPaginationMode, (newVal) => {
  if (newVal) {
    updateTotalPages()
    // 切换到翻页模式，根据当前进度计算页码
    if (totalPages.value > 0) {
      currentPage.value = Math.floor((progress.value / 100) * (totalPages.value - 1))
    }
  }
})

// 监听屏幕模式变化（EPUB）
watch(() => settings.value.screenMode, (newVal) => {
  if (book.value?.format === 'epub' && rendition) {
    const spreadMode = newVal === 'double' ? 'always' : 'none'
    rendition.spread(spreadMode)
  }
})

// 监听内容变化
watch(content, () => {
  updateTotalPages()
})

// 监听当前页内容变化，检查是否溢出
watch(currentPageContent, () => {
  if (isPaginationMode.value) {
    nextTick(() => {
      checkAndAdjustPageSize()
      renderDocumentHighlights()
    })
  }
})

watch(htmlContent, () => nextTick(renderDocumentHighlights))

// 监听窗口大小变化
const handleResize = () => {
  if (isPaginationMode.value) {
    updateTotalPages()
    if (currentPage.value >= totalPages.value) {
      currentPage.value = Math.max(0, totalPages.value - 1)
    }
  }
  nextTick(() => {
    handleScroll()
    renderAllHighlights()
  })
}

const initializeReader = async () => {
  loadReaderSettings()
  try {
    await preferencesStore.hydrate()
    await Promise.all([fontStore.fetchFonts(), loadReaderBackgrounds()])
    const preferredFontId = preferencesStore.readerFontId
    if (preferredFontId != null) {
      await fontStore.loadFont(preferredFontId)
      settings.value.fontFamily = managedFontValue(preferredFontId)
    } else if (managedFontId(settings.value.fontFamily) != null) {
      settings.value.fontFamily = 'default'
    }
  } catch (error) {
    console.error('Failed to initialize reader font:', error)
    if (preferencesStore.readerFontId != null) {
      preferencesStore.setReaderFontId(null)
    }
    if (managedFontId(settings.value.fontFamily) != null) {
      settings.value.fontFamily = 'default'
    }
  }
  await loadBook()
  await nextTick()
  handleScroll()
}

onMounted(() => {
  void initializeReader()
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('resize', handleResize)

  // 禁用父容器的滚动，让 reader-body 自己处理滚动
  const layoutMain = document.querySelector<HTMLElement>('.layout-main')
  if (layoutMain) {
    layoutMain.style.overflow = 'hidden'
  }
})

onBeforeUnmount(() => {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }
  if (readingHeartbeatTimer) {
    clearInterval(readingHeartbeatTimer)
    readingHeartbeatTimer = null
  }
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  searchSequence += 1

  const token = localStorage.getItem('token')

  const finalLocator = createCurrentLocator(progress.value)
  if (book.value && token) {
    fetch(withVersion(`/api/reading-progress/book/${book.value.id}`), {
      method: 'POST',
      keepalive: true,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        currentChapter: book.value.format === 'epub'
          ? savedCfi.value || ''
          : currentChapterName.value || '',
        currentChapterTitle: currentChapterName.value || '',
        chapterProgress: finalLocator?.chapterProgress || 0,
        totalProgress: Math.round(progress.value),
        locator: finalLocator ? JSON.stringify(finalLocator) : null,
      })
    })
  }

  pauseReadingClock()
  void saveReadingTime(true)

  if (bookInstance) {
    clearEpubKeyboardBindings()
    bookInstance.destroy()
    bookInstance = null
    rendition = null
  }

  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('resize', handleResize)
  clearDocumentHighlights()
  clearCurrentSearchHighlight()
  pdfSource.value = null

  // 恢复父容器的滚动
  const layoutMain = document.querySelector<HTMLElement>('.layout-main')
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
  color: var(--text-primary);
  gap: var(--spacing-md);
}

.loading-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid var(--primary-alpha-20);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.reader-loading-overlay {
  position: absolute;
  inset: 0;
  z-index: 2300;
  background: var(--surface-elevated);
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

.empty-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.highlight-editor-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2400;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.3);
  backdrop-filter: blur(8px);
}

.highlight-editor {
  width: min(460px, 100%);
  padding: 20px;
  border: 1px solid var(--border-color-light);
  border-radius: 22px;
  background: var(--surface-elevated);
  box-shadow: var(--shadow-lg);
  color: var(--text-primary);
}

.highlight-editor header,
.highlight-editor footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.highlight-editor header p {
  display: -webkit-box;
  overflow: hidden;
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.highlight-color-options {
  display: flex;
  gap: 10px;
  margin: 18px 0;
}

.highlight-color-options button {
  width: 34px;
  height: 34px;
  border: 3px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}

.highlight-color-options button.active {
  border-color: var(--primary);
  outline: 2px solid var(--surface-elevated);
  box-shadow: 0 0 0 4px var(--primary-alpha-20);
}

.highlight-editor textarea {
  width: 100%;
  margin-bottom: 18px;
  padding: 12px 14px;
  resize: vertical;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  outline: none;
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
  line-height: 1.6;
}

.highlight-editor textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-10);
}

.highlight-editor footer {
  justify-content: flex-end;
}

/* 阅读器内容 */
.reader-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  position: relative;
}

/* 阅读器头部 */
.reader-header {
  position: absolute;
  top: 0;
  left: var(--spacing-md);
  right: var(--spacing-md);
  display: grid;
  grid-template-columns:
    minmax(112px, 1fr)
    min(var(--reader-content-width, 800px), calc(100vw - 360px))
    minmax(216px, 1fr);
  align-items: center;
  column-gap: var(--spacing-md);
  padding: 0;
  z-index: 100;
  pointer-events: none;
  min-height: 40px;
}

.back-btn {
  display: inline-flex;
  grid-column: 1;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 8px 16px;
  justify-self: start;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-full);
  background: var(--surface-card);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  color: var(--text-primary);
  font-size: var(--font-size-base);
  cursor: pointer;
  transition: all var(--transition-fast);
  pointer-events: auto;
}

.back-btn:hover {
  background: var(--bg-tertiary);
}

.reader-title {
  display: flex;
  grid-column: 2;
  align-items: center;
  justify-content: center;
  justify-self: stretch;
  color: var(--text-primary);
  overflow: hidden;
  padding: 7px var(--spacing-md);
  margin: 0;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color-light);
  background: var(--surface-card);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  pointer-events: auto;
  width: 100%;
  min-width: 0;
}

.reader-title-main {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  min-width: 0;
}

.reader-book-identity {
  display: flex;
  flex: 1 1 48%;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.reader-book-name {
  overflow: hidden;
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reader-version-badge {
  flex: 0 0 auto;
  padding: 2px 6px;
  border-radius: var(--radius-full);
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-size: 10px;
  white-space: nowrap;
}

.reader-title-meta {
  display: flex;
  flex: 0 1 48%;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  max-width: 48%;
  margin-left: auto;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.reader-current-chapter {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reader-title-separator {
  color: var(--text-tertiary);
}

.reader-header-progress {
  flex: 0 0 auto;
  color: var(--primary);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.performance-mode-badge {
  flex: 0 0 auto;
  padding: 2px 6px;
  border: 1px solid var(--primary-alpha-30);
  border-radius: 999px;
  color: var(--primary);
  font-size: 10px;
  font-weight: 500;
  line-height: 1.3;
  background: var(--primary-alpha-10);
}

.reader-actions {
  display: flex;
  grid-column: 3;
  justify-self: end;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-full);
  background: var(--surface-card);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  pointer-events: auto;
}

.btn-icon {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: var(--radius-full);
  border: 0;
  background: transparent;
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
  z-index: 120;
}

.panel-tabs {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--border-color-light);
  gap: 6px;
  padding: 8px;
  background: var(--surface-card);
}

.panel-tabs-nav {
  position: relative;
  display: grid;
  flex: 1;
  grid-template-columns: repeat(var(--panel-tab-count), minmax(0, 1fr));
  padding: 3px;
  border: 1px solid var(--border-color-light);
  border-radius: 12px;
  background: var(--bg-secondary);
}

.panel-tab-indicator {
  position: absolute;
  z-index: 0;
  top: 3px;
  bottom: 3px;
  left: 3px;
  width: calc((100% - 6px) / var(--panel-tab-count));
  border: 1px solid var(--border-color-light);
  border-radius: 9px;
  background: var(--surface-elevated);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);
  transform: translateX(calc(var(--panel-tab-index) * 100%));
  transition: transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.tab-btn {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
  padding: 7px 4px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: color var(--transition-fast);
}

.tab-btn:hover {
  color: var(--text-primary);
}

.tab-btn.active {
  color: var(--primary);
}

.tab-btn:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -1px;
}

.close-panel {
  flex: 0 0 32px;
  margin-left: auto;
  background: transparent;
  color: var(--text-secondary);
}

.close-panel:hover {
  color: var(--text-primary);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
}

.reader-search-form {
  display: flex;
  gap: 8px;
}

.reader-search-form input {
  min-width: 0;
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  outline: none;
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
}

.reader-search-form input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-10);
}

.reader-search-form button {
  min-width: 58px;
  border: 0;
  border-radius: 11px;
  background: var(--primary);
  color: white;
  cursor: pointer;
}

.reader-search-form button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.search-summary,
.search-error {
  padding: 10px 2px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.search-error {
  color: var(--danger);
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-result-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}

.search-result-item:hover,
.search-result-item.active {
  border-color: var(--primary-alpha-30);
  background: var(--primary-alpha-10);
}

.search-result-location {
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
}

.search-result-excerpt {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

@media (prefers-reduced-motion: reduce) {
  .panel-tab-indicator {
    transition: none;
  }
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

.bookmarks-header > span {
  min-width: 0;
  white-space: nowrap;
}

.bookmark-add-btn {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: auto;
  min-width: 92px;
  min-height: 34px;
  padding: 7px 12px;
  white-space: nowrap;
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
  padding: 72px 60px 40px;
  margin: 0 auto;
  width: 100%;
  scroll-behavior: smooth;
  min-height: 0;
}

.fullscreen-mode .reader-body {
  padding-top: 40px;
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

/* 两屏模式下的样式 */
.reader-text[column-count="2"] {
  min-height: auto;
  height: 100%;
  overflow: hidden;
}

.reader-text p {
  margin-bottom: v-bind('settings.paragraphSpacing + "px"');
  text-indent: v-bind('settings.textIndent ? "2em" : "0"');
  line-height: 1.9;
  font-size: 1.05em;
  color: inherit;
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
  color: inherit;
  border-bottom: 2px solid var(--border-color);
  letter-spacing: 0.1em;
}

.reader-html {
  line-height: 1.8;
}

.reader-pdf {
  width: 100%;
  height: 100%;
  min-height: 0;
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

/* 两侧翻页区域与按钮 */
.page-turn-zone {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  width: clamp(44px, calc((100vw - var(--reader-content-width, 100%)) / 2 + 52px), 240px);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.page-turn-zone--previous {
  left: 0;
}

.page-turn-zone--next {
  right: 0;
}

.page-turn-zone:disabled {
  cursor: default;
}

.page-turn-button {
  display: grid;
  width: 38px;
  height: 58px;
  place-items: center;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-full);
  background: var(--surface-card);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  font-size: 34px;
  font-weight: 300;
  line-height: 1;
  opacity: 0.58;
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  transition: opacity var(--transition-fast), transform var(--transition-fast), background var(--transition-fast);
}

.page-turn-zone:hover:not(:disabled) .page-turn-button,
.page-turn-zone:focus-visible .page-turn-button {
  background: var(--surface-elevated);
  opacity: 1;
  transform: scale(1.06);
}

.page-turn-zone:disabled .page-turn-button {
  opacity: 0.18;
}

.page-turn-zone:focus-visible {
  outline: none;
}

/* 迷你阅读进度 Dock */
.reader-progress-dock {
  position: absolute;
  bottom: 12px;
  left: 50%;
  z-index: 130;
  min-width: 46px;
  padding: 5px 12px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-full);
  background: var(--surface-card);
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.12);
  color: var(--text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 1;
  text-align: center;
  transform: translateX(-50%);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
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

.floating-reading-info {
  display: grid;
  min-width: 0;
  max-width: min(50vw, 480px);
  gap: 2px;
  text-align: center;
}

.floating-book-title,
.floating-progress {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.floating-book-title {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
}

/* 设置面板 */
.settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: transparent;
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

/* 宽度选项 */
.width-options {
  display: flex;
  gap: var(--spacing-sm);
}

.width-btn {
  flex: 1;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.width-btn:hover {
  border-color: var(--primary);
}

.width-btn.active {
  border-color: var(--primary);
  background: var(--primary-alpha-10);
}

/* 屏幕模式选项 */
.screen-mode-options {
  display: flex;
  gap: var(--spacing-sm);
}

.screen-mode-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.screen-mode-btn:hover {
  border-color: var(--primary);
}

.screen-mode-btn.active {
  border-color: var(--primary);
  background: var(--primary-alpha-10);
}

.screen-mode-icon {
  font-size: 18px;
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

.reader-background-group {
  margin-top: var(--spacing-lg);
}

.reader-background-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-sm);
}

.reader-background-btn {
  display: grid;
  min-width: 0;
  gap: 6px;
  padding: 6px;
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 11px;
  text-align: center;
  transition: border-color var(--transition-fast), background-color var(--transition-fast);
}

.reader-background-btn:hover,
.reader-background-btn.active {
  border-color: var(--primary);
}

.reader-background-btn.active {
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.reader-background-btn img,
.reader-background-none {
  display: grid;
  width: 100%;
  aspect-ratio: 16 / 10;
  place-items: center;
  border-radius: 7px;
  object-fit: cover;
}

.reader-background-none {
  background:
    linear-gradient(135deg, transparent 47%, var(--danger) 48%, var(--danger) 52%, transparent 53%),
    var(--bg-secondary);
  color: var(--text-tertiary);
  font-size: 13px;
}

.reader-background-btn > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reader-background-hint {
  margin: 8px 0 0;
  color: var(--text-tertiary);
  font-size: 11px;
  line-height: 1.5;
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
  .reader-header {
    top: 0;
    left: 8px;
    right: 8px;
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 8px;
  }

  .reader-title {
    grid-column: 1 / -1;
    grid-row: 2;
    justify-self: center;
    width: min(100%, 520px);
    margin: 0;
  }

  .reader-actions {
    grid-column: 2;
    grid-row: 1;
    justify-self: end;
  }

  .side-panel {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 120;
    width: 85%;
    max-width: 320px;
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.2);
  }

  .settings-panel {
    width: 100%;
  }

  .reader-body {
    padding: 104px 52px 40px;
  }

  .fullscreen-mode .reader-body {
    padding: var(--spacing-md) 52px 40px;
  }

  .page-turn-zone {
    width: 44px;
  }

  .page-turn-button {
    width: 32px;
    height: 48px;
    font-size: 28px;
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
