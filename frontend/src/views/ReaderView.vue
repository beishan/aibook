<template>
  <div class="reader-view">
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="book" class="reader-content">
      <!-- 阅读器头部 -->
      <div class="reader-header">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="reader-title">{{ book.title }}</div>
        <div class="reader-actions">
          <el-button v-if="book.format === 'epub'" @click="toggleToc">
            <el-icon><List /></el-icon>
          </el-button>
          <el-button @click="showSettings = true">
            <el-icon><Setting /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="reader-body-wrapper">
        <!-- EPUB 目录侧边栏 -->
        <div v-if="showToc && book.format === 'epub'" class="toc-sidebar">
          <div class="toc-header">目录</div>
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

        <!-- 阅读器内容 -->
        <div class="reader-body" :style="readerStyle">
          <!-- EPUB 阅读器 -->
          <div v-if="book.format === 'epub'" ref="epubContainer" class="epub-container"></div>

          <!-- TXT / MD 阅读器 -->
          <div v-else-if="book.format === 'txt' || book.format === 'md'" class="reader-text">
            <p v-for="(paragraph, index) in content" :key="index">{{ paragraph }}</p>
          </div>

          <!-- HTML 阅读器 -->
          <div v-else-if="book.format === 'html'" class="reader-html" v-html="htmlContent"></div>

          <!-- PDF 阅读器 -->
          <div v-else-if="book.format === 'pdf'" class="reader-pdf">
            <iframe :src="pdfUrl" class="pdf-frame"></iframe>
          </div>

          <!-- 不支持的格式 -->
          <div v-else class="reader-placeholder">
            <el-empty :description="`${book.format.toUpperCase()} 格式暂不支持在线阅读`">
              <el-button type="primary" @click="handleDownload">下载文件</el-button>
            </el-empty>
          </div>
        </div>
      </div>

      <!-- 阅读器底部 -->
      <div class="reader-footer" v-if="book.format !== 'epub'">
        <div class="progress-info">
          <span>进度：{{ progress }}%</span>
        </div>
        <el-slider v-model="progress" :max="100" :step="1" />
      </div>

      <!-- EPUB 底部导航 -->
      <div class="reader-footer epub-footer" v-else>
        <el-button @click="prevPage" :disabled="!bookInstance">上一页</el-button>
        <span class="epub-location">{{ currentLocation || '' }}</span>
        <el-button @click="nextPage" :disabled="!bookInstance">下一页</el-button>
      </div>

      <!-- 设置面板 -->
      <el-drawer v-model="showSettings" title="阅读设置" direction="rtl" size="300px">
        <el-form label-position="top">
          <el-form-item label="字体">
            <el-select v-model="settings.fontFamily" style="width: 100%">
              <el-option label="默认" value="default" />
              <el-option label="宋体" value="SimSun, serif" />
              <el-option label="黑体" value="SimHei, sans-serif" />
              <el-option label="楷体" value="KaiTi, serif" />
            </el-select>
          </el-form-item>

          <el-form-item label="字号">
            <el-slider v-model="settings.fontSize" :min="12" :max="24" :step="1" show-input />
          </el-form-item>

          <el-form-item label="行间距">
            <el-slider v-model="settings.lineHeight" :min="1" :max="3" :step="0.1" show-input />
          </el-form-item>

          <el-form-item label="背景色">
            <el-radio-group v-model="settings.backgroundColor">
              <el-radio-button value="white">白色</el-radio-button>
              <el-radio-button value="#f5f5dc">米色</el-radio-button>
              <el-radio-button value="#333">黑色</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </el-drawer>
    </div>

    <div v-else class="empty-state">
      <el-empty description="书籍不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Setting, List } from '@element-plus/icons-vue'
import { useBookStore } from '@/stores/book'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()

const book = ref<any>(null)
const loading = ref(true)
const content = ref<string[]>([])
const htmlContent = ref('')
const progress = ref(0)
const showSettings = ref(false)
const showToc = ref(false)
const tocItems = ref<any[]>([])
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

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    book.value = await bookStore.fetchBookById(id)

    if (book.value.format === 'txt' || book.value.format === 'md') {
      await loadTextContent()
    } else if (book.value.format === 'html') {
      await loadHtmlContent()
    } else if (book.value.format === 'epub') {
      await nextTick()
      initEpub()
    }
  } catch (error) {
    console.error('Failed to load book:', error)
  } finally {
    loading.value = false
  }
}

const loadTextContent = async () => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/content`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const text = await response.text()
    content.value = text.split(/\n\n+/).filter(p => p.trim())
  } catch (error) {
    console.error('Failed to load text content:', error)
    content.value = ['加载内容失败']
  }
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

    // 先用 fetch 拿到 ArrayBuffer，再传给 epubjs（避免 iframe 跨域认证问题）
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/content`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const arrayBuffer = await response.arrayBuffer()

    bookInstance = ePub(arrayBuffer)

    await bookInstance.ready

    // 获取目录
    const navigation = bookInstance.navigation
    if (navigation && navigation.toc) {
      tocItems.value = flattenToc(navigation.toc)
    }

    // 渲染到容器
    rendition = bookInstance.renderTo(epubContainer.value!, {
      width: '100%',
      height: '100%',
      spread: 'none',
    })

    rendition.display()

    // 监听位置变化
    rendition.on('relocated', (location: any) => {
      if (location && location.start) {
        currentLocation.value = location.start.displayed
          ? `${location.start.displayed.page} / ${location.start.displayed.total}`
          : ''
        // 更新进度
        if (bookInstance.locations && bookInstance.locations.length()) {
          const percentage = bookInstance.locations.percentageFromCfi(location.start.cfi)
          progress.value = Math.round(percentage * 100)
        }
      }
    })

    // 生成 locations 用于进度计算
    await bookInstance.locations.generate(1024)

    // 注册默认主题并应用当前设置
    applyEpubTheme()

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

const goToTocItem = (item: any) => {
  if (rendition) {
    rendition.display(item.href)
    currentTocHref.value = item.href
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
  const token = localStorage.getItem('token')
  const link = document.createElement('a')
  link.href = `/api/books/${book.value.id}/content`
  link.setAttribute('download', `${book.value.title}.${book.value.format}`)
  // For auth, we use a workaround: open in new tab
  window.open(`/api/books/${book.value.id}/content`, '_blank')
}

// 应用 EPUB 主题样式
const applyEpubTheme = () => {
  if (!rendition) return

  const fontFamily = settings.value.fontFamily === 'default'
    ? 'serif'
    : settings.value.fontFamily.split(',')[0].trim()

  rendition.themes.register('custom', {
    'body': {
      'font-family': `${fontFamily}, serif !important`,
      'font-size': `${settings.value.fontSize}px !important`,
      'line-height': `${settings.value.lineHeight} !important`,
      'color': settings.value.backgroundColor === '#333' ? '#fff !important' : '#333 !important',
      'background': `${settings.value.backgroundColor} !important`,
    },
    'p': {
      'font-family': `${fontFamily}, serif !important`,
      'font-size': `${settings.value.fontSize}px !important`,
      'line-height': `${settings.value.lineHeight} !important`,
    }
  })
  rendition.themes.select('custom')
}

// 监听设置变化，更新 EPUB 样式
watch(() => settings.value, () => {
  applyEpubTheme()
}, { deep: true })

onMounted(loadBook)

onBeforeUnmount(() => {
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
  background: #f5f5f5;
}

.loading-state,
.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.reader-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.reader-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: white;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}

.reader-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.reader-body-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.toc-sidebar {
  width: 250px;
  background: white;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.toc-header {
  padding: 12px 16px;
  font-weight: 500;
  border-bottom: 1px solid #eee;
}

.toc-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.toc-item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
  color: #333;
  transition: background 0.2s;
}

.toc-item:hover {
  background: #f5f7fa;
}

.toc-item.active {
  color: #409eff;
  background: #ecf5ff;
}

.reader-body {
  flex: 1;
  overflow-y: auto;
  padding: 40px;
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
  align-items: center;
  justify-content: center;
  height: 100%;
}

.reader-footer {
  padding: 10px 20px;
  background: white;
  border-top: 1px solid #eee;
  flex-shrink: 0;
}

.epub-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.epub-location {
  font-size: 14px;
  color: #666;
}

.progress-info {
  text-align: center;
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}
</style>
