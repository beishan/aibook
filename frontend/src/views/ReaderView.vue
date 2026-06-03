<template>
  <div class="reader-view">
    <!-- 加载中 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 阅读器内容 -->
    <div v-else-if="book" class="reader-content">
      <!-- 阅读器头部 -->
      <header class="reader-header glass">
        <button class="back-btn" @click="goBack">
          <span>‹</span>
          <span>返回</span>
        </button>
        <div class="reader-title">{{ book.title }}</div>
        <div class="reader-actions">
          <button v-if="book.format === 'epub' || tocItems.length > 0" class="btn btn-icon" @click="toggleToc">
            <span>☰</span>
          </button>
          <button class="btn btn-icon" @click="showSettings = true">
            <span>⚙️</span>
          </button>
        </div>
      </header>

      <div class="reader-body-wrapper">
        <!-- 目录侧边栏 -->
        <Transition name="slide-left">
          <div v-if="showToc && (book.format === 'epub' || tocItems.length > 0)" class="toc-sidebar glass">
            <div class="toc-header">
              <span>📑 目录</span>
              <button class="btn btn-icon btn-small" @click="showToc = false">✕</button>
            </div>
            <div class="toc-list">
              <div
                v-for="(item, index) in tocItems"
                :key="index"
                class="toc-item"
                :class="{ active: item.href === currentTocHref }"
                @click="goToTocItem(item)"
              >
                {{ item.label }}
              </div>
            </div>
          </div>
        </Transition>

        <!-- 阅读器内容 -->
        <div class="reader-body" :style="readerStyle">
          <!-- EPUB 阅读器 -->
          <div v-if="book.format === 'epub'" ref="epubContainer" class="epub-container"></div>

          <!-- TXT / MD 阅读器 -->
          <div v-else-if="book.format === 'txt' || book.format === 'md'" class="reader-text">
            <template v-for="(paragraph, index) in content" :key="index">
              <div v-if="isChapterTitle(paragraph)" class="chapter-title" :id="'chapter-' + index">
                {{ paragraph }}
              </div>
              <p v-else>{{ paragraph }}</p>
            </template>
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
      <footer class="reader-footer glass" v-if="book.format !== 'epub'">
        <div class="progress-info">
          <span>进度：{{ progress }}%</span>
        </div>
        <div class="progress-bar-wrapper">
          <div class="progress" style="height: 6px;">
            <div class="progress-bar" :style="{ width: progress + '%' }"></div>
          </div>
          <input type="range" v-model="progress" min="0" max="100" class="progress-slider" />
        </div>
      </footer>

      <!-- EPUB 底部导航 -->
      <footer class="reader-footer glass epub-footer" v-else>
        <button class="btn" @click="prevPage" :disabled="!bookInstance">
          <span>‹</span>
          <span>上一页</span>
        </button>
        <span class="epub-location">{{ currentLocation || '' }}</span>
        <button class="btn" @click="nextPage" :disabled="!bookInstance">
          <span>下一页</span>
          <span>›</span>
        </button>
      </footer>

      <!-- 设置面板 -->
      <Transition name="slide-right">
        <div v-if="showSettings" class="settings-overlay" @click.self="showSettings = false">
          <div class="settings-panel glass">
            <div class="settings-header">
              <span>⚙️ 阅读设置</span>
              <button class="dialog-close" @click="showSettings = false">✕</button>
            </div>
            <div class="settings-body">
              <div class="form-group">
                <label class="form-label">字体</label>
                <select v-model="settings.fontFamily" class="select-input">
                  <option value="default">默认</option>
                  <option value="SimSun, serif">宋体</option>
                  <option value="SimHei, sans-serif">黑体</option>
                  <option value="KaiTi, serif">楷体</option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">字号：{{ settings.fontSize }}px</label>
                <input type="range" v-model="settings.fontSize" min="12" max="24" class="slider" />
              </div>
              <div class="form-group">
                <label class="form-label">行间距：{{ settings.lineHeight }}</label>
                <input type="range" v-model="settings.lineHeight" min="1" max="3" step="0.1" class="slider" />
              </div>
              <div class="form-group">
                <label class="form-label">背景色</label>
                <div class="color-options">
                  <button
                    class="color-btn"
                    :class="{ active: settings.backgroundColor === 'white' }"
                    @click="settings.backgroundColor = 'white'"
                  >
                    <span class="color-preview" style="background: white;"></span>
                    <span>白色</span>
                  </button>
                  <button
                    class="color-btn"
                    :class="{ active: settings.backgroundColor === '#f5f5dc' }"
                    @click="settings.backgroundColor = '#f5f5dc'"
                  >
                    <span class="color-preview" style="background: #f5f5dc;"></span>
                    <span>米色</span>
                  </button>
                  <button
                    class="color-btn"
                    :class="{ active: settings.backgroundColor === '#333' }"
                    @click="settings.backgroundColor = '#333'"
                  >
                    <span class="color-preview" style="background: #333;"></span>
                    <span>黑色</span>
                  </button>
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

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()

// 章节接口定义
interface Chapter {
  title: string
  index: number
}

const book = ref<any>(null)
const loading = ref(true)
const content = ref<string[]>([])
const htmlContent = ref('')
const progress = ref(0)
const showSettings = ref(false)
const showToc = ref(false)
const tocItems = ref<Chapter[]>([])
const currentTocHref = ref('')
const currentLocation = ref('')

// EPUB 相关
const epubContainer = ref<HTMLElement>()
let bookInstance: any = null
let rendition: any = null

const pdfUrl = computed(() => {
  if (!book.value) return ''
  const token = localStorage.getItem('token')
  return `/api/books/${book.value.id}/content?token=${token}`
})

const settings = ref({
  fontFamily: 'default',
  fontSize: 16,
  lineHeight: 1.8,
  backgroundColor: 'white',
})

const readerStyle = computed(() => ({
  fontFamily: settings.value.fontFamily === 'default' ? 'inherit' : settings.value.fontFamily,
  fontSize: `${settings.value.fontSize}px`,
  lineHeight: settings.value.lineHeight,
  backgroundColor: settings.value.backgroundColor,
  color: settings.value.backgroundColor === '#333' ? '#fff' : '#333',
}))

// 阅读进度保存相关
let saveTimer: ReturnType<typeof setTimeout> | null = null
let readingStartTime = 0
const savedCfi = ref<string | null>(null)

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    book.value = await bookStore.fetchBookById(id)

    // 加载已保存的阅读进度
    await loadSavedProgress(id)

    if (book.value.format === 'txt' || book.value.format === 'md') {
      await loadTextContent()
    } else if (book.value.format === 'html') {
      await loadHtmlContent()
    } else if (book.value.format === 'epub') {
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
 * 解析章节标题，生成目录
 */
const parseChapters = (paragraphs: string[]): Chapter[] => {
  const chapters: Chapter[] = []

  // 章节匹配正则表达式
  const patterns = [
    // 中文数字格式：第X章/回/节/卷/篇
    /^第[一二三四五六七八九十百千万零\d]+[章回节卷篇]/,
    // 英文格式：Chapter X
    /^Chapter\s+\d+/i,
    // 卷X格式
    /^卷[一二三四五六七八九十\d]+/,
    // 特殊章节：序章、楔子、尾声等
    /^(序章|楔子|尾声|后记|前言|引言)/,
  ]

  paragraphs.forEach((paragraph, index) => {
    const trimmed = paragraph.trim()
    // 检查是否匹配章节模式
    if (patterns.some(pattern => pattern.test(trimmed))) {
      chapters.push({
        title: trimmed,
        index
      })
    }
  })

  return chapters
}

/**
 * 判断是否为章节标题
 */
const isChapterTitle = (paragraph: string): boolean => {
  return tocItems.value.some(item => item.title === paragraph.trim())
}

const loadTextContent = async () => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/content`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const text = await response.text()
    content.value = text.split(/\n\n+/).filter(p => p.trim())

    // 解析章节并生成目录
    tocItems.value = parseChapters(content.value)

    // 等待 DOM 渲染后恢复滚动位置并监听滚动
    await nextTick()
    restoreScrollPosition()
    setupScrollListener()
  } catch (error) {
    console.error('Failed to load text content:', error)
    content.value = ['加载内容失败']
  }
}

/**
 * 恢复 TXT/MD 滚动位置
 */
const restoreScrollPosition = () => {
  if (progress.value > 0) {
    const readerBody = document.querySelector('.reader-body')
    if (readerBody) {
      const maxScroll = readerBody.scrollHeight - readerBody.clientHeight
      readerBody.scrollTop = maxScroll * (progress.value / 100)
    }
  }
}

/**
 * 监听 TXT/MD 滚动事件，保存进度
 */
const setupScrollListener = () => {
  const readerBody = document.querySelector('.reader-body')
  if (!readerBody) return

  readerBody.addEventListener('scroll', () => {
    const maxScroll = readerBody.scrollHeight - readerBody.clientHeight
    if (maxScroll > 0) {
      const currentProgress = Math.round((readerBody.scrollTop / maxScroll) * 100)
      if (Math.abs(currentProgress - progress.value) >= 1) {
        progress.value = currentProgress
        // 找到当前可见的章节作为 currentChapter
        const currentChapter = findCurrentChapter()
        saveProgress(currentProgress, currentChapter)
      }
    }
  })
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
          // 保存阅读进度，用 CFI 作为当前章节标记
          saveProgress(progress.value, location.start.cfi)
        }
      }
    })

    rendition.hooks.content.register((contents: any) => {
      applyThemeToContent(contents)
    })

    // 先生成 locations，再 display，否则首次 relocated 事件中无法计算进度
    await bookInstance.locations.generate(1024)
    console.log('[Reader] Locations generated, count:', bookInstance.locations.length())

    // 如果有保存的阅读位置，恢复到该位置
    if (savedCfi.value) {
      console.log('[Reader] Restoring to CFI:', savedCfi.value)
      try {
        await rendition.display(savedCfi.value)
        console.log('[Reader] Restored to saved position')
      } catch (e) {
        console.error('[Reader] Failed to restore CFI, falling back to start:', e)
        await rendition.display()
      }
    } else {
      console.log('[Reader] No saved CFI, starting from beginning')
      await rendition.display()
    }

  } catch (error) {
    console.error('Failed to init EPUB:', error)
  }
}

const flattenToc = (toc: any[], result: any[] = []): any[] => {
  for (const item of toc) {
    result.push({ label: item.label.trim(), href: item.href })
    if (item.subitems && item.subitems.length > 0) {
      flattenToc(item.subitems, result)
    }
  }
  return result
}

const toggleToc = () => {
  showToc.value = !showToc.value
}

const goToTocItem = (item: Chapter | any) => {
  if (book.value?.format === 'epub' && rendition) {
    // EPUB跳转
    rendition.display(item.href)
    currentTocHref.value = item.href
  } else if (book.value?.format === 'txt' || book.value?.format === 'md') {
    // TXT/MD跳转 - 滚动到对应章节
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

const applyThemeToContent = (contents: any) => {
  if (!contents || !contents.css) return

  const fontFamily = settings.value.fontFamily === 'default'
    ? 'serif'
    : settings.value.fontFamily.split(',')[0].trim()

  const textColor = settings.value.backgroundColor === '#333' ? '#fff' : '#333'

  contents.css('font-family', `${fontFamily}, serif`, true)
  contents.css('font-size', `${settings.value.fontSize}px`, true)
  contents.css('line-height', `${settings.value.lineHeight}`, true)
  contents.css('color', textColor, true)
  contents.css('background', settings.value.backgroundColor, true)

  try {
    const doc = contents.document
    if (doc) {
      const style = doc.createElement('style')
      style.textContent = `
        * {
          font-family: ${fontFamily}, serif !important;
          font-size: ${settings.value.fontSize}px !important;
          line-height: ${settings.value.lineHeight} !important;
          color: ${textColor} !important;
        }
        body {
          background: ${settings.value.backgroundColor} !important;
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

watch(() => settings.value, () => {
  applyEpubTheme()
}, { deep: true })

onMounted(loadBook)

onBeforeUnmount(() => {
  // 清除防抖定时器
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }

  const token = localStorage.getItem('token')

  // 离开页面时同步保存最终进度（keepalive 保证请求不被取消）
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

  // 保存阅读时长
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
})
</script>

<style scoped>
.reader-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
}

/* 阅读器头部 */
.reader-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--spacing-lg);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
  z-index: 10;
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
  gap: var(--spacing-sm);
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
}

/* 目录侧边栏 */
.toc-sidebar {
  width: 280px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-right: 1px solid rgba(255, 255, 255, 0.2);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.toc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.toc-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-sm) 0;
}

.toc-item {
  padding: var(--spacing-md) var(--spacing-lg);
  cursor: pointer;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  transition: all var(--transition-fast);
  border-left: 3px solid transparent;
}

.toc-item:hover {
  background: rgba(0, 122, 255, 0.1);
}

.toc-item.active {
  color: var(--primary);
  background: rgba(0, 122, 255, 0.1);
  border-left-color: var(--primary);
}

/* 阅读器内容区 */
.reader-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-xl);
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

.epub-container {
  width: 100%;
  height: 100%;
}

.reader-text {
  line-height: 1.8;
}

.reader-text p {
  margin-bottom: 1em;
  text-indent: 2em;
}

.chapter-title {
  font-size: 1.5em;
  font-weight: bold;
  text-align: center;
  margin: 2em 0 1em 0;
  padding: 0.5em 0;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
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
  padding: var(--spacing-md) var(--spacing-lg);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
}

.epub-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.epub-location {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.progress-info {
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
}

.progress-bar-wrapper {
  position: relative;
}

.progress-slider {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 6px;
  opacity: 0;
  cursor: pointer;
}

/* 设置面板 */
.settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  justify-content: flex-end;
  z-index: 1000;
}

.settings-panel {
  width: 320px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.1);
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
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

.select-input {
  width: 100%;
}

.slider {
  width: 100%;
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
  box-shadow: 0 2px 6px rgba(0, 122, 255, 0.3);
}

.color-options {
  display: flex;
  gap: var(--spacing-sm);
}

.color-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  background: white;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.color-btn:hover {
  border-color: var(--primary);
}

.color-btn.active {
  border-color: var(--primary);
  background: rgba(0, 122, 255, 0.1);
}

.color-preview {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
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
</style>
