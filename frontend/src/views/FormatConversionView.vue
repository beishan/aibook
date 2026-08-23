<template>
  <div class="conversion-page">
    <header class="page-heading">
      <div><span class="eyebrow">独立工具</span><h1>格式转换</h1><p>TXT → EPUB · 转换结果不会自动加入书库</p></div>
      <el-button @click="showHistory = true">转换历史</el-button>
    </header>

    <el-tabs v-model="activeTab" class="conversion-tabs">
      <el-tab-pane name="select">
        <template #label><span class="conversion-tab-label"><el-icon><UploadFilled /></el-icon><span>选择/上传书籍</span><small>选择 TXT 来源</small></span></template>
        <div class="tab-content-shell">
          <section v-if="task" class="current-task-banner">
            <div><span class="task-state-dot"></span><strong>当前任务：{{ task.title }}</strong><small>{{ task.sourceFilename }} · {{ statusText(task.status) }}</small></div>
            <el-button type="primary" plain @click="activeTab = 'source'">继续编辑源信息</el-button>
          </section>
          <section class="panel source-picker">
            <div class="section-title"><div><span class="step">01</span><h2>{{ task ? '选择新的源文件' : '选择源文件' }}</h2></div><el-tag>第一期仅支持 TXT</el-tag></div>
            <div class="source-method-field">
              <label for="conversion-source-method">书籍来源方式</label>
              <el-select id="conversion-source-method" v-model="sourceMode" class="source-method-select" @change="handleSourceModeChange">
                <el-option label="上传本地书籍" value="upload" />
                <el-option label="从书库选择" value="library" />
              </el-select>
              <small>选择来源后，下方将展示对应的书籍选择方式。</small>
            </div>

            <div class="source-method-content">
              <div v-if="sourceMode === 'upload'" class="upload-source-panel">
                <el-upload drag :auto-upload="false" :limit="1" accept=".txt,text/plain" :on-change="onSourceSelected" :on-remove="() => selectedFile = null">
                  <div class="upload-icon">⇄</div><div>拖放 TXT 到这里，或点击选择</div><small>文件仅用于转换，不会自动入库</small>
                </el-upload>
                <el-button class="primary-wide" type="primary" :loading="creating" :disabled="!selectedFile" @click="createFromUpload">分析 TXT 并继续</el-button>
              </div>

              <div v-else class="library-source-panel">
                <div class="library-search-bar">
                  <el-input v-model="libraryKeyword" clearable placeholder="搜索书名、作者或 ISBN" :prefix-icon="Search" @keyup.enter="searchLibraryBooks" @clear="searchLibraryBooks" />
                  <el-button type="primary" :loading="libraryLoading" @click="searchLibraryBooks">搜索书库</el-button>
                </div>
                <div class="library-results-toolbar">
                  <span>书库展示方式</span>
                  <div class="library-view-switch" role="group" aria-label="选择书库展示方式">
                    <el-button :type="libraryViewMode === 'card' ? 'primary' : 'default'" :plain="libraryViewMode !== 'card'" circle aria-label="大卡片显示" title="大卡片显示" :aria-pressed="libraryViewMode === 'card'" @click="setLibraryViewMode('card')"><el-icon><Grid /></el-icon></el-button>
                    <el-button :type="libraryViewMode === 'compact' ? 'primary' : 'default'" :plain="libraryViewMode !== 'compact'" circle aria-label="小卡片显示" title="小卡片显示" :aria-pressed="libraryViewMode === 'compact'" @click="setLibraryViewMode('compact')"><el-icon><Tickets /></el-icon></el-button>
                    <el-button :type="libraryViewMode === 'list' ? 'primary' : 'default'" :plain="libraryViewMode !== 'list'" circle aria-label="列表显示" title="列表显示" :aria-pressed="libraryViewMode === 'list'" @click="setLibraryViewMode('list')"><el-icon><List /></el-icon></el-button>
                  </div>
                </div>
                <div v-loading="libraryLoading" class="library-results">
                  <div v-if="libraryBooks.length && libraryViewMode !== 'list'" class="library-book-grid" :class="{ 'is-compact': libraryViewMode === 'compact' }">
                    <article v-for="book in libraryBooks" :key="book.id" class="library-book-card" :class="{ selected: selectedBookId === book.id }">
                      <div class="library-book-cover">
                        <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" :alt="`${book.title}封面`" loading="lazy" decoding="async" />
                        <span v-else>{{ book.title?.charAt(0) || '书' }}</span>
                        <em>{{ (book.format || '未知').toUpperCase() }}</em>
                      </div>
                      <div class="library-book-info">
                        <h3 :title="book.title">{{ book.title }}</h3>
                        <p :title="book.author || '未知作者'">{{ book.author || '未知作者' }}</p>
                        <small>{{ book.categoryName || '未分类' }}<template v-if="book.fileSize"> · {{ formatSize(book.fileSize) }}</template></small>
                      </div>
                      <el-button type="primary" plain :size="libraryViewMode === 'compact' ? 'small' : 'default'" :loading="selectingBookId === book.id" :disabled="creating || selectingBookId !== null" @click="selectLibraryBook(book)">{{ libraryViewMode === 'compact' ? '选择' : '选择转换此书籍' }}</el-button>
                    </article>
                  </div>
                  <div v-else-if="libraryBooks.length" class="library-book-list">
                    <article v-for="book in libraryBooks" :key="book.id" class="library-book-list-row" :class="{ selected: selectedBookId === book.id }">
                      <div class="library-list-cover">
                        <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" :alt="`${book.title}封面`" loading="lazy" decoding="async" />
                        <span v-else>{{ book.title?.charAt(0) || '书' }}</span>
                      </div>
                      <div class="library-list-primary">
                        <h3 :title="book.title">{{ book.title }}</h3>
                        <p :title="book.author || '未知作者'">{{ book.author || '未知作者' }}</p>
                      </div>
                      <div class="library-list-meta"><span>{{ (book.format || '未知').toUpperCase() }}</span><span>{{ book.categoryName || '未分类' }}</span><span>{{ formatSize(book.fileSize) }}</span></div>
                      <el-button type="primary" plain size="small" :loading="selectingBookId === book.id" :disabled="creating || selectingBookId !== null" @click="selectLibraryBook(book)">选择</el-button>
                    </article>
                  </div>
                  <el-empty v-else :description="libraryKeyword ? '没有找到匹配的书籍' : '书库中暂无书籍'" />
                </div>
                <div v-if="libraryTotal > 0" class="library-pagination-box">
                  <el-pagination v-model:current-page="libraryPage" v-model:page-size="libraryPageSize" background layout="total, sizes, prev, pager, next, jumper" :page-sizes="libraryPageSizes" :total="libraryTotal" :pager-count="5" @current-change="loadLibraryBooks" @size-change="handleLibraryPageSizeChange" />
                </div>
              </div>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane name="source" :disabled="!task">
        <template #label><span class="conversion-tab-label"><el-icon><Document /></el-icon><span>书籍源信息</span><small>检查并配置</small></span></template>
        <div v-if="task" class="tab-content-shell">
          <section class="panel source-card">
            <div class="section-title"><div><span class="step">02</span><h2>源书籍信息</h2></div><el-button link @click="activeTab = 'select'">更换源文件</el-button></div>
            <div class="source-grid">
              <div class="cover-box" @click="coverInput?.click()"><img v-if="coverObjectUrl" :src="coverObjectUrl" alt="EPUB 封面" /><span v-else>{{ task.title?.charAt(0) || '书' }}</span><em>点击上传封面</em></div>
              <input ref="coverInput" hidden type="file" accept="image/jpeg,image/png,image/webp" @change="uploadCover" />
              <div class="source-main"><h3>{{ task.title }}</h3><p>{{ task.author || '未知作者' }}</p><div class="facts"><span><b>格式</b> TXT</span><span><b>大小</b> {{ formatSize(task.sourceSize) }}</span><span><b>编码</b> {{ task.encoding }}</span><span><b>字符</b> {{ formatNumber(task.characterCount) }}</span><span><b>章节</b> {{ task.chapters?.length || 0 }}</span><span><b>换行</b> {{ task.newlineFormat }}</span></div><div class="cover-actions"><el-button @click="coverInput?.click()">上传封面</el-button><el-button @click="openCoverLibrary">封面库</el-button><el-button :loading="randomizing" @click="randomCover">随机一个</el-button></div></div>
            </div>
          </section>

          <section class="panel">
            <div class="section-title"><div><span class="step">03</span><h2>书籍元信息</h2></div><small>修改仅影响本次 EPUB</small></div>
            <el-form label-position="top" class="metadata-grid"><el-form-item label="书籍名称（必填）"><el-input v-model="form.title" /></el-form-item><el-form-item label="作者"><el-input v-model="form.author" /></el-form-item><el-form-item label="ISBN"><el-input v-model="form.isbn" /></el-form-item><el-form-item label="出版社"><el-input v-model="form.publisher" /></el-form-item><el-form-item label="出版日期"><el-date-picker v-model="form.publishDate" class="conversion-date-picker" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择出版日期" /></el-form-item><el-form-item label="语言"><el-input v-model="form.language" /></el-form-item><el-form-item label="分类"><el-input v-model="form.categoryName" /></el-form-item><el-form-item label="标签（逗号分隔）"><el-input v-model="tagInput" /></el-form-item><el-form-item label="系列名称"><el-input v-model="form.seriesName" /></el-form-item><el-form-item label="系列序号"><el-input v-model="form.seriesIndex" /></el-form-item><el-form-item label="简介" class="full"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item></el-form>
          </section>

          <section class="panel">
            <div class="section-title"><div><span class="step">04</span><h2>内容解析</h2></div><el-tag :type="task.anomalyCount ? 'warning' : 'success'">{{ task.anomalyCount || 0 }} 项疑似异常</el-tag></div>
            <div class="analysis-row"><div><b>{{ task.encoding }}</b><span>文件编码</span></div><div><b>{{ task.chapters?.length || 0 }}</b><span>检测章节</span></div><div><b>{{ formatNumber(task.characterCount) }}</b><span>正文字符</span></div><div><b>{{ task.newlineFormat }}</b><span>换行格式</span></div></div>
            <el-alert v-if="task.anomalyCount" title="检测到乱码特征或重复章节标题，请在转换前检查章节列表。" type="warning" :closable="false" show-icon />
            <div class="chapter-rule"><el-radio-group v-model="chapterMode" @change="handleChapterModeChange"><el-radio-button label="auto">自动识别</el-radio-button><el-radio-button label="custom">自定义规则</el-radio-button></el-radio-group><template v-if="chapterMode === 'custom'"><el-input v-model="form.chapterPattern" placeholder="例如：^第.{1,10}[章节卷回].*$" /><el-button :loading="analyzingChapters" @click="reanalyzeChapters">重新识别</el-button></template></div>
            <div class="chapter-title-rules">
              <div class="chapter-title-rules__heading"><div><h3>章节标题规则</h3><p>批量删除标题中的文字，并统一章号和标题格式。</p></div><el-button link type="primary" @click="useStandardChapterRule">使用“正文 → 第255章”示例</el-button></div>
              <div class="chapter-title-rules__fields">
                <el-form-item label="删除内容（正则表达式）"><el-input v-model="form.chapterTitleRemovePattern" placeholder="例如：^正文\s*" clearable /></el-form-item>
                <el-form-item label="输出格式"><el-input v-model="form.chapterTitleFormat" placeholder="第{number}章 {title}" /></el-form-item>
              </div>
              <div class="chapter-title-rules__footer"><small>可用变量：{number} 阿拉伯章号、{title} 章节名、{original} 清理后的原标题</small><el-button type="primary" plain :loading="formattingChapters" @click="applyChapterTitleRules">应用到全部章节</el-button></div>
            </div>
            <div class="chapter-toolbar"><h3>章节结构</h3><span>应用规则后仍可逐章修改或忽略</span></div>
            <div class="chapter-list"><div v-for="(chapter, index) in form.chapters" :key="chapter.index" class="chapter-row" :class="{ ignored: chapter.ignored }"><span>{{ String(index + 1).padStart(3, '0') }}</span><el-input v-model="chapter.title" :disabled="chapter.ignored" /><el-checkbox v-model="chapter.ignored">忽略</el-checkbox></div></div>
          </section>

          <section class="panel">
            <div class="section-title"><div><span class="step">05</span><h2>EPUB 参数</h2></div><el-tag>EPUB 3</el-tag></div>
            <div class="settings-grid"><el-form-item label="EPUB 文件名"><el-input v-model="form.outputFilename" /></el-form-item><el-form-item label="首行缩进"><el-select v-model="form.firstLineIndent"><el-option label="不缩进" value="0" /><el-option label="1em" value="1em" /><el-option label="2em" value="2em" /></el-select></el-form-item><el-form-item label="段落间距"><el-select v-model="form.paragraphSpacing"><el-option label="无" value="none" /><el-option label="小" value="small" /><el-option label="中" value="medium" /><el-option label="大" value="large" /></el-select></el-form-item><el-form-item label="行高"><el-select v-model="form.lineHeight"><el-option v-for="n in [1.4,1.5,1.6,1.8,2]" :key="n" :label="String(n)" :value="n" /></el-select></el-form-item></div>
            <div class="cleanup-options"><el-checkbox v-model="form.removeExtraBlankLines">清理多余空行</el-checkbox><el-checkbox v-model="form.trimLineEnd">清理行尾空格</el-checkbox><el-checkbox v-model="form.normalizeWidth">全角/半角规范化</el-checkbox></div>
            <div class="convert-actions"><el-button :loading="saving" @click="saveConfig">保存配置</el-button><el-button type="primary" size="large" :loading="converting" :disabled="converting" @click="startConversion">{{ task.status === 'SUCCESS' ? '重新转换' : '开始转换' }}</el-button></div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane name="result" :disabled="!task">
        <template #label><span class="conversion-tab-label"><el-icon><CircleCheckFilled /></el-icon><span>转换结果</span><small>{{ task ? statusText(task.status) : '等待选择书籍' }}</small></span></template>
        <div v-if="task" class="tab-content-shell result-tab-shell">
          <section v-if="!['CONVERTING','SUCCESS','FAILED'].includes(task.status)" class="panel result-empty"><span class="result-empty-icon">⇄</span><h2>尚未开始转换</h2><p>源书籍已经分析完成，请检查信息与 EPUB 参数后开始转换。</p><el-button type="primary" @click="activeTab = 'source'">前往书籍源信息</el-button></section>
          <section v-else class="panel progress-panel"><div class="section-title"><div><span class="step">06</span><h2>转换进度</h2></div><strong>{{ task.progress }}%</strong></div><el-progress :percentage="task.progress" :status="task.status === 'FAILED' ? 'exception' : task.status === 'SUCCESS' ? 'success' : undefined" /><p>{{ task.stage }}</p><el-alert v-if="task.status === 'FAILED'" :title="task.errorMessage || '转换失败，请保留配置后重试'" type="error" :closable="false" show-icon /><div v-if="task.status === 'FAILED'" class="retry-action"><el-button type="primary" @click="activeTab = 'source'">检查配置并重新转换</el-button></div></section>
          <section v-if="task.status === 'SUCCESS'" class="panel result-panel"><div class="result-summary"><span class="success-mark">✓</span><div><h2>转换成功</h2><p>{{ task.outputFilename }} · {{ formatSize(task.outputSize) }} · {{ activeChapters.length }} 章 · {{ formatElapsed(task.elapsedMillis) }}</p></div></div><div class="result-actions"><el-button type="primary" @click="download(task)">下载 EPUB</el-button><el-button @click="activeTab = 'source'">修改并重新转换</el-button><el-button type="danger" plain @click="removeTask(task.id)">删除转换结果</el-button></div><div class="library-actions"><el-button v-if="task.sourceBookId" type="success" size="large" @click="attachToBook(task.sourceBookId)">加入到当前书籍的新版本</el-button><template v-else><el-button type="success" size="large" @click="createBook">创建新书籍</el-button><el-button size="large" @click="openAttachBookDialog">关联已有书籍</el-button></template></div></section>
          <section v-if="task.status === 'SUCCESS'" class="panel preview-panel"><div class="section-title"><div><span class="step">07</span><h2>EPUB 预览</h2></div><div><el-button :disabled="preview.chapterIndex <= 0" @click="loadPreview(preview.chapterIndex - 1)">上一章</el-button><el-button :disabled="preview.chapterIndex >= preview.chapterCount - 1" @click="loadPreview(preview.chapterIndex + 1)">下一章</el-button></div></div><div class="reader-preview" :class="{ night: previewNight }"><div class="preview-tools"><el-select :model-value="preview.chapterIndex" @change="loadPreview"><el-option v-for="(chapter, index) in activeChapters" :key="chapter.index" :label="chapter.title" :value="index" /></el-select><el-button @click="previewNight = !previewNight">{{ previewNight ? '日间' : '夜间' }}</el-button></div><div class="preview-book"><img v-if="coverObjectUrl" :src="coverObjectUrl" alt="封面" /><div><h1>{{ task.title }}</h1><p>{{ task.author || '未知作者' }}</p></div></div><article><h2>{{ preview.chapterTitle }}</h2><p v-for="(paragraph, index) in previewParagraphs" :key="index">{{ paragraph }}</p></article></div></section>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showHistory" title="转换历史" width="min(960px, 94vw)" class="conversion-history-dialog"><div class="history-actions"><span>关闭页面后也可以继续查看任务</span><el-button @click="loadHistory">刷新</el-button></div><el-table :data="history" empty-text="暂无转换任务"><el-table-column prop="title" label="书籍" min-width="180" /><el-table-column label="格式" width="120"><template #default="{ row }">{{ row.sourceFormat.toUpperCase() }} → {{ row.targetFormat.toUpperCase() }}</template></el-table-column><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column><el-table-column prop="createdAt" label="创建时间" min-width="170" /><el-table-column label="操作" width="210"><template #default="{ row }"><el-button link type="primary" @click="openTask(row.id)">查看/继续</el-button><el-button v-if="row.status === 'SUCCESS'" link @click="download(row)">下载</el-button><el-button link type="danger" @click="removeTask(row.id)">删除</el-button></template></el-table-column></el-table></el-dialog>
    <el-dialog v-model="showCoverDialog" title="从封面库选择" width="min(760px, 92vw)"><el-input v-model="coverSearch" clearable placeholder="搜索封面文件名" /><div class="cover-library"><button v-for="cover in filteredCovers" :key="cover.id" @click="chooseCover(cover.id)"><img :src="getCoverUrl(cover.url)" :alt="cover.originalFilename" /><span>{{ cover.originalFilename }}</span></button></div><el-empty v-if="!filteredCovers.length" description="封面库为空" /></el-dialog>
    <el-dialog v-model="showBookDialog" title="关联已有书籍" width="min(560px, 92vw)"><el-select v-model="attachBookId" filterable class="wide" placeholder="选择书籍"><el-option v-for="book in books" :key="book.id" :label="`${book.title} · ${book.author || '未知作者'}`" :value="book.id" /></el-select><template #footer><el-button @click="showBookDialog = false">取消</el-button><el-button type="primary" :disabled="!attachBookId" @click="attachToBook(attachBookId)">确认关联</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCheckFilled, Document, Grid, List, Search, Tickets, UploadFilled } from '@element-plus/icons-vue'
import api from '@/utils/api'
import { getCoverUrl } from '@/utils/cover'
import { message, confirm } from '@/utils/message'

const route = useRoute(); const router = useRouter()
const task = ref<any>(null); const history = ref<any[]>([]); const books = ref<any[]>([]); const bookVersions = ref<any[]>([])
const activeTab = ref<'select' | 'source' | 'result'>('select')
const sourceMode = ref('library'); const selectedFile = ref<File | null>(null); const selectedBookId = ref<number | null>(null); const selectedVersionId = ref<number | null>(null)
type LibraryViewMode = 'card' | 'compact' | 'list'
const libraryViewModeStorageKey = 'aibook-conversion-library-view-mode'
const libraryPageSizeStorageKey = 'aibook-conversion-library-page-size'
const libraryPageSizes = [8, 16, 32, 64] as const
const isLibraryViewMode = (value: string | null): value is LibraryViewMode => value === 'card' || value === 'compact' || value === 'list'
const readLibraryPageSize = () => { try { const value = Number(localStorage.getItem(libraryPageSizeStorageKey)); return libraryPageSizes.includes(value as typeof libraryPageSizes[number]) ? value : libraryPageSizes[0] } catch { return libraryPageSizes[0] } }
const libraryViewMode = ref<LibraryViewMode>('card')
const libraryBooks = ref<any[]>([]); const libraryKeyword = ref(''); const libraryPage = ref(1); const libraryPageSize = ref(readLibraryPageSize()); const libraryTotal = ref(0); const libraryLoading = ref(false); const libraryLoaded = ref(false); const selectingBookId = ref<number | null>(null)
const creating = ref(false); const saving = ref(false); const converting = ref(false); const randomizing = ref(false); const analyzingChapters = ref(false); const formattingChapters = ref(false); const showHistory = ref(false)
const coverInput = ref<HTMLInputElement | null>(null); const coverObjectUrl = ref(''); const showCoverDialog = ref(false); const covers = ref<any[]>([]); const coverSearch = ref('')
const showBookDialog = ref(false); const attachBookId = ref<number | null>(null); const tagInput = ref(''); const previewNight = ref(false)
const preview = reactive<any>({ chapterIndex: 0, chapterCount: 0, chapterTitle: '', content: '' })
const chapterMode = ref('auto')
const form = reactive<any>({ title: '', author: '', description: '', isbn: '', publisher: '', publishDate: '', language: 'zh-CN', categoryName: '', seriesName: '', seriesIndex: '', outputFilename: '', chapterPattern: '', chapterTitleRemovePattern: '', chapterTitleFormat: '{original}', epubVersion: '3', firstLineIndent: '2em', paragraphSpacing: 'small', lineHeight: 1.6, removeExtraBlankLines: true, trimLineEnd: true, normalizeWidth: false, chapters: [] })
const activeChapters = computed(() => form.chapters.filter((chapter: any) => !chapter.ignored))
const previewParagraphs = computed(() => String(preview.content || '').split(/\n+/).map(value => value.trim()).filter(Boolean))
const filteredCovers = computed(() => covers.value.filter(c => !coverSearch.value || c.originalFilename.toLowerCase().includes(coverSearch.value.toLowerCase())))

const syncTask = (value: any) => { task.value = value; Object.assign(form, { chapterPattern: '', chapterTitleRemovePattern: '', chapterTitleFormat: '{original}' }, value.settings || {}, { title: value.title, author: value.author || '', description: value.description || '', isbn: value.isbn || '', publisher: value.publisher || '', publishDate: value.publishDate || '', language: value.language || 'zh-CN', categoryName: value.categoryName || '', seriesName: value.seriesName || '', seriesIndex: value.seriesIndex || '', outputFilename: value.outputFilename || `${value.title}.epub`, chapters: (value.chapters || []).map((c: any) => ({ ...c })) }); chapterMode.value = form.chapterPattern ? 'custom' : 'auto'; tagInput.value = (value.tags || []).join(', '); void refreshCover() }
const loadBooks = async () => { const { data } = await api.get('/api/books', { params: { page: 0, size: 1000, sortBy: 'title', sortDir: 'asc' } }); books.value = data.content || [] }
const openAttachBookDialog = async () => { if (!books.value.length) await loadBooks(); showBookDialog.value = true }
const loadLibraryBooks = async (page = libraryPage.value) => { libraryLoading.value = true; try { libraryPage.value = page; const keyword = libraryKeyword.value.trim(); const url = keyword ? '/api/books/search' : '/api/books'; const params = keyword ? { keyword, page: page - 1, size: libraryPageSize.value } : { page: page - 1, size: libraryPageSize.value, sortBy: 'title', sortDir: 'asc' }; const { data } = await api.get(url, { params }); libraryBooks.value = data.content || []; libraryTotal.value = data.totalElements || 0; libraryLoaded.value = true } catch (e: any) { libraryBooks.value = []; libraryTotal.value = 0; message.error(e.response?.data?.message || '书库加载失败') } finally { libraryLoading.value = false } }
const searchLibraryBooks = () => { void loadLibraryBooks(1) }
const setLibraryViewMode = (mode: LibraryViewMode) => { libraryViewMode.value = mode; try { localStorage.setItem(libraryViewModeStorageKey, mode) } catch { /* 存储不可用时仅保留本次选择 */ } }
const handleLibraryPageSizeChange = (size: number) => { if (!libraryPageSizes.includes(size as typeof libraryPageSizes[number])) return; try { localStorage.setItem(libraryPageSizeStorageKey, String(size)) } catch { /* 存储不可用时仅保留本次选择 */ } void loadLibraryBooks(1) }
const handleSourceModeChange = (mode: string | number | boolean | undefined) => { if (mode === 'library' && !libraryLoaded.value && !libraryLoading.value) void loadLibraryBooks(1) }
const loadHistory = async () => { history.value = (await api.get('/api/conversions')).data || [] }
const loadBookVersions = async (bookId = selectedBookId.value) => { selectedVersionId.value = null; if (!bookId) return null; const versions = (await api.get(`/api/books/${bookId}/versions`)).data || []; if (selectedBookId.value !== bookId) return null; bookVersions.value = versions; selectedVersionId.value = versions.find((v: any) => v.format === 'txt')?.id || null; return selectedVersionId.value }
const selectLibraryBook = async (book: any) => { const currentBookId = book.id; selectingBookId.value = currentBookId; selectedBookId.value = currentBookId; try { const versionId = await loadBookVersions(currentBookId); if (selectedBookId.value !== currentBookId) return; if (!versionId) { selectedBookId.value = null; selectedVersionId.value = null; message.warning(`《${book.title}》没有可用于转换的 TXT 文件版本`); return } await createFromBook() } catch (e: any) { if (selectedBookId.value === currentBookId) { selectedBookId.value = null; selectedVersionId.value = null; message.error(e.response?.data?.message || '读取书籍文件版本失败') } } finally { if (selectingBookId.value === currentBookId) selectingBookId.value = null } }
const onSourceSelected = (upload: any) => { selectedFile.value = upload.raw }
const createFromUpload = async () => { if (!selectedFile.value) return; creating.value = true; try { const data = new FormData(); data.append('file', selectedFile.value); const response = await api.post('/api/conversions/upload', data); syncTask(response.data); activeTab.value = 'source'; await router.replace({ path: '/format-conversion', query: { taskId: response.data.id } }); await loadHistory() } catch (e: any) { message.error(e.response?.data?.message || 'TXT 分析失败') } finally { creating.value = false } }
const createFromBook = async (loadHistoryAfter = true) => { if (!selectedBookId.value || !selectedVersionId.value) return; creating.value = true; try { const { data } = await api.post('/api/conversions/from-book', { bookId: selectedBookId.value, versionId: selectedVersionId.value }); syncTask(data); activeTab.value = 'source'; await router.replace({ path: '/format-conversion', query: { taskId: data.id } }); if (loadHistoryAfter) await loadHistory() } catch (e: any) { message.error(e.response?.data?.message || '书籍分析失败') } finally { creating.value = false } }
const saveConfig = async (quiet = false) => { if (!form.title.trim()) { message.warning('书籍名称不能为空'); throw new Error('title') } saving.value = true; try { const payload = { ...form, title: form.title.trim(), tags: tagInput.value.split(/[,，]/).map(v => v.trim()).filter(Boolean) }; const { data } = await api.put(`/api/conversions/${task.value.id}`, payload); syncTask(data); if (!quiet) message.success('转换配置已保存'); return data } finally { saving.value = false } }
const startConversion = async () => { converting.value = true; try { await saveConfig(true); task.value.status = 'CONVERTING'; task.value.progress = 55; task.value.stage = '正在生成 EPUB 内容'; activeTab.value = 'result'; const { data } = await api.post(`/api/conversions/${task.value.id}/convert`); syncTask(data); if (data.status === 'SUCCESS') { message.success('EPUB 转换完成'); await loadPreview(0) } else message.error(data.errorMessage || '转换失败') } catch (e: any) { if (e.message !== 'title') message.error(e.response?.data?.message || '转换失败') } finally { converting.value = false; await loadHistory() } }
const uploadCover = async (event: Event) => { const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return; const body = new FormData(); body.append('file', file); try { syncTask((await api.post(`/api/conversions/${task.value.id}/cover`, body)).data); message.success('封面已更新') } catch (e: any) { message.error(e.response?.data?.message || '封面上传失败') } finally { input.value = '' } }
const refreshCover = async () => { if (coverObjectUrl.value) URL.revokeObjectURL(coverObjectUrl.value); coverObjectUrl.value = ''; if (!task.value?.coverUrl) return; try { const { data } = await api.get(task.value.coverUrl, { responseType: 'blob' }); coverObjectUrl.value = URL.createObjectURL(data) } catch { /* 无封面时显示占位 */ } }
const openCoverLibrary = async () => { covers.value = (await api.get('/api/random-book-covers')).data || []; showCoverDialog.value = true }
const chooseCover = async (id: number) => { syncTask((await api.post(`/api/conversions/${task.value.id}/cover/library/${id}`)).data); showCoverDialog.value = false; message.success('已选用封面库图片') }
const randomCover = async () => { randomizing.value = true; try { syncTask((await api.post(`/api/conversions/${task.value.id}/cover/random`)).data) } catch (e: any) { message.warning(e.response?.data?.message || '随机封面失败') } finally { randomizing.value = false } }
const reanalyzeChapters = async () => { if (!form.chapterPattern?.trim()) return message.warning('请输入章节识别正则表达式'); analyzingChapters.value = true; try { syncTask((await api.post(`/api/conversions/${task.value.id}/analyze-chapters`, { pattern: form.chapterPattern.trim() })).data); chapterMode.value = 'custom'; message.success('章节已重新识别') } catch (e: any) { message.error(e.response?.data?.message || '章节识别失败') } finally { analyzingChapters.value = false } }
const handleChapterModeChange = async (mode: string | number | boolean | undefined) => { if (mode !== 'auto' || !form.chapterPattern) return; analyzingChapters.value = true; try { syncTask((await api.post(`/api/conversions/${task.value.id}/analyze-chapters`, { pattern: '' })).data); chapterMode.value = 'auto'; message.success('已恢复自动章节识别') } catch (e: any) { message.error(e.response?.data?.message || '自动识别失败') } finally { analyzingChapters.value = false } }
const useStandardChapterRule = () => { form.chapterTitleRemovePattern = '^正文\\s*'; form.chapterTitleFormat = '第{number}章 {title}' }
const applyChapterTitleRules = async () => { formattingChapters.value = true; try { const { data } = await api.post(`/api/conversions/${task.value.id}/format-chapters`, { chapterTitleRemovePattern: form.chapterTitleRemovePattern, chapterTitleFormat: form.chapterTitleFormat }); syncTask(data); message.success('章节标题规则已应用，可在下方继续微调') } catch (e: any) { message.error(e.response?.data?.message || '章节标题规则应用失败') } finally { formattingChapters.value = false } }
const loadPreview = async (chapter = 0) => { Object.assign(preview, (await api.get(`/api/conversions/${task.value.id}/preview`, { params: { chapter } })).data) }
const download = async (row: any) => { const { data } = await api.get(`/api/conversions/${row.id}/download`, { responseType: 'blob' }); const url = URL.createObjectURL(data); const a = document.createElement('a'); a.href = url; a.download = row.outputFilename || `${row.title}.epub`; a.click(); URL.revokeObjectURL(url) }
const attachToBook = async (bookId: number) => { try { await api.post(`/api/conversions/${task.value.id}/attach/${bookId}`); showBookDialog.value = false; message.success('已加入书籍的新版本'); await router.push(`/books/${bookId}`) } catch (e: any) { message.error(e.response?.data?.message || '关联失败') } }
const createBook = async () => { try { const { data } = await api.post(`/api/conversions/${task.value.id}/create-book`); message.success('新书籍已创建'); await router.push(`/books/${data.id}`) } catch (e: any) { message.error(e.response?.data?.message || '新建书籍失败') } }
const openTask = async (id: number) => { syncTask((await api.get(`/api/conversions/${id}`)).data); showHistory.value = false; activeTab.value = task.value.status === 'SUCCESS' || task.value.status === 'FAILED' ? 'result' : 'source'; await router.replace({ path: '/format-conversion', query: { taskId: id } }); if (task.value.status === 'SUCCESS') await loadPreview(0) }
const removeTask = async (id: number) => { if (!await confirm('确定删除该转换任务及临时结果吗？')) return; await api.delete(`/api/conversions/${id}`); if (task.value?.id === id) resetTask(); await loadHistory(); message.success('转换任务已删除') }
const resetTask = () => { task.value = null; selectedFile.value = null; selectedBookId.value = null; selectedVersionId.value = null; sourceMode.value = 'library'; activeTab.value = 'select'; if (!libraryLoaded.value && !libraryLoading.value) void loadLibraryBooks(1); void router.replace('/format-conversion') }
const formatSize = (bytes?: number) => { if (!bytes) return '0 B'; const units = ['B','KB','MB','GB']; let value = bytes; let i = 0; while (value >= 1024 && i < 3) { value /= 1024; i++ } return `${value.toFixed(i ? 2 : 0)} ${units[i]}` }
const formatNumber = (n?: number) => new Intl.NumberFormat('zh-CN').format(n || 0)
const formatElapsed = (ms?: number) => ms == null ? '-' : `${(ms / 1000).toFixed(1)} 秒`
const statusText = (s: string) => ({ CREATED:'已创建',ANALYZING:'正在分析',READY:'等待转换',CONVERTING:'正在转换',SUCCESS:'成功',FAILED:'失败',CANCELLED:'已取消' } as any)[s] || s
const statusType = (s: string) => s === 'SUCCESS' ? 'success' : s === 'FAILED' ? 'danger' : s === 'CONVERTING' ? 'warning' : 'info'

onMounted(async () => { try { const savedViewMode = localStorage.getItem(libraryViewModeStorageKey); if (isLibraryViewMode(savedViewMode)) libraryViewMode.value = savedViewMode } catch { /* 存储不可用时使用大卡片默认值 */ } const taskId = Number(route.query.taskId); if (taskId) { await openTask(taskId); return } const bookId = Number(route.query.bookId), versionId = Number(route.query.versionId); if (bookId && versionId) { selectedBookId.value = bookId; selectedVersionId.value = versionId; await createFromBook(false); return } await Promise.all([loadHistory(), loadLibraryBooks(1)]) })
onBeforeUnmount(() => { if (coverObjectUrl.value) URL.revokeObjectURL(coverObjectUrl.value) })
</script>

<style scoped>
.sr-only{position:absolute;width:1px;height:1px;padding:0;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.conversion-page{max-width:1120px;margin:0 auto;padding:28px 0 70px}.page-heading,.section-title,.source-grid,.convert-actions,.result-summary,.result-actions,.library-actions,.chapter-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:22px}.page-heading h1{margin:3px 0;font-size:32px}.page-heading p,.section-title small,.chapter-toolbar span{margin:0;color:var(--text-secondary)}.eyebrow,.step{color:var(--primary);font-size:12px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}.step{padding:4px 7px;border-radius:7px;background:var(--primary-alpha-10)}.panel{margin-bottom:18px;padding:24px;border:1px solid color-mix(in srgb,var(--primary) 14%,var(--border-color));border-radius:22px;background:var(--surface-elevated);box-shadow:var(--shadow-md),inset 0 1px 0 color-mix(in srgb,var(--surface-elevated) 88%,white)}.section-title{margin-bottom:22px}.section-title h2{margin:3px 0 0;font-size:20px}.wide,.primary-wide{width:100%}.primary-wide{margin-top:18px}.upload-icon{margin:8px;font-size:42px;color:var(--primary)}.source-grid{justify-content:flex-start;align-items:flex-start}.cover-box{position:relative;display:grid;width:150px;aspect-ratio:2/3;flex:0 0 auto;place-items:center;overflow:hidden;border-radius:14px;background:linear-gradient(145deg,var(--primary-alpha-10),var(--surface-hover));color:var(--primary);font-size:54px;cursor:pointer}.cover-box img{width:100%;height:100%;object-fit:cover}.cover-box em{position:absolute;inset:auto 0 0;padding:8px;background:#0009;color:#fff;font-size:12px;font-style:normal;text-align:center}.source-main{flex:1}.source-main h3{margin:4px 0;font-size:27px}.source-main p{color:var(--text-secondary)}.facts,.analysis-row{display:grid;grid-template-columns:repeat(3,minmax(100px,1fr));gap:10px}.facts span,.analysis-row div{display:flex;padding:12px;flex-direction:column;border:1px solid color-mix(in srgb,var(--primary) 8%,var(--border-color-light));border-radius:12px;background:var(--surface-hover)}.facts b,.analysis-row span{color:var(--text-secondary);font-size:12px}.analysis-row b{font-size:20px}.cover-actions{display:flex;gap:8px;margin-top:16px}.metadata-grid,.settings-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.metadata-grid .full{grid-column:1/-1}.chapter-toolbar{margin:22px 0 10px}.chapter-list{max-height:390px;overflow:auto;border:1px solid var(--border-color);border-radius:12px;background:var(--surface-hover)}.chapter-row{display:grid;grid-template-columns:50px 1fr 70px;align-items:center;gap:10px;padding:9px 12px;border-bottom:1px solid var(--border-color)}.chapter-row:last-child{border:0}.chapter-row.ignored{opacity:.55}.cleanup-options{display:flex;gap:24px;flex-wrap:wrap}.convert-actions{justify-content:flex-end;margin-top:22px}.progress-panel p{text-align:center;color:var(--text-secondary)}.reader-preview{overflow:hidden;border:1px solid var(--border-color);border-radius:16px;background:#f8f1df;color:#332b22}.reader-preview.night{background:#1f2428;color:#d7dadd}.preview-tools{display:flex;justify-content:space-between;padding:10px;border-bottom:1px solid #8884}.reader-preview article{max-height:540px;padding:32px 8%;overflow:auto}.reader-preview article h2{text-align:center}.reader-preview article p{text-indent:2em;line-height:1.8}.success-mark{display:grid;width:52px;height:52px;place-items:center;border-radius:50%;background:#e7f7ed;color:#2d9b58;font-size:28px}.result-summary{justify-content:flex-start}.result-summary h2{margin:0}.result-summary p{margin:5px 0;color:var(--text-secondary)}.result-actions,.library-actions{justify-content:flex-start;margin-top:20px}.library-actions{padding-top:20px;border-top:1px solid var(--border-color)}.cover-library{display:grid;grid-template-columns:repeat(auto-fill,minmax(115px,1fr));gap:12px;margin-top:16px;max-height:55vh;overflow:auto}.cover-library button{padding:0;overflow:hidden;border:2px solid transparent;border-radius:12px;background:var(--surface-hover);cursor:pointer}.cover-library button:hover{border-color:var(--primary)}.cover-library img{width:100%;aspect-ratio:2/3;object-fit:cover}.cover-library span{display:block;padding:7px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-panel{overflow:auto}@media(max-width:720px){.conversion-page{padding:18px 0}.page-heading,.source-grid{align-items:stretch;flex-direction:column}.panel{padding:18px;border-radius:16px}.source-grid .cover-box{align-self:center}.metadata-grid,.settings-grid,.facts,.analysis-row{grid-template-columns:1fr}.result-actions,.library-actions{align-items:stretch;flex-direction:column}.result-actions :deep(button),.library-actions :deep(button){margin-left:0}.chapter-row{grid-template-columns:38px 1fr}.chapter-row :deep(.el-checkbox){grid-column:2}}
.chapter-rule{display:flex;align-items:center;gap:10px;margin-top:18px}.chapter-rule :deep(.el-input){flex:1}.preview-book{display:flex;align-items:center;justify-content:center;gap:18px;padding:28px 8% 0;text-align:left}.preview-book img{width:72px;aspect-ratio:2/3;object-fit:cover;border-radius:6px}.preview-book h1,.preview-book p{margin:4px}@media(max-width:720px){.chapter-rule{align-items:stretch;flex-direction:column}}
.chapter-title-rules{margin-top:18px;padding:18px;border:1px solid var(--primary-alpha-15);border-radius:14px;background:var(--primary-alpha-10)}.chapter-title-rules__heading,.chapter-title-rules__footer{display:flex;align-items:center;justify-content:space-between;gap:16px}.chapter-title-rules h3,.chapter-title-rules p{margin:0}.chapter-title-rules p,.chapter-title-rules small{color:var(--text-secondary)}.chapter-title-rules__fields{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:16px}.chapter-title-rules__fields :deep(.el-form-item){margin-bottom:12px}.chapter-title-rules__footer small{line-height:1.6}@media(max-width:720px){.chapter-title-rules__heading,.chapter-title-rules__footer{align-items:stretch;flex-direction:column}.chapter-title-rules__fields{grid-template-columns:1fr}}

.conversion-tabs {
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--primary) 18%, var(--border-color));
  border-radius: 24px;
  background: var(--surface-card);
  box-shadow: var(--shadow-xl);
}

.conversion-tabs > :deep(.el-tabs__header) {
  margin: 0;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
  background: color-mix(in srgb, var(--primary) 6%, var(--surface-elevated));
}

.conversion-tabs > :deep(.el-tabs__header .el-tabs__nav-wrap::after),
.conversion-tabs > :deep(.el-tabs__header .el-tabs__active-bar) {
  display: none;
}

.conversion-tabs > :deep(.el-tabs__header .el-tabs__nav) {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.conversion-tabs > :deep(.el-tabs__header .el-tabs__item) {
  height: 68px;
  padding: 0 18px !important;
  border: 1px solid transparent;
  border-radius: 15px;
  color: var(--text-secondary);
  transition: color .18s ease, border-color .18s ease, background .18s ease;
}

.conversion-tabs > :deep(.el-tabs__header .el-tabs__item:hover:not(.is-disabled)) {
  border-color: var(--primary-alpha-15);
  background: var(--surface-hover);
  color: var(--text-primary);
}

.conversion-tabs > :deep(.el-tabs__header .el-tabs__item.is-active) {
  border-color: var(--primary-alpha-20);
  background: var(--surface-elevated);
  color: var(--primary);
}

.conversion-tabs > :deep(.el-tabs__header .el-tabs__item.is-disabled) {
  opacity: .42;
}

.conversion-tabs > :deep(.el-tabs__content) {
  overflow: visible;
}

.conversion-tab-label {
  display: grid;
  width: 100%;
  grid-template-columns: 30px 1fr;
  grid-template-rows: auto auto;
  column-gap: 10px;
  line-height: 1.2;
  text-align: left;
}

.conversion-tab-label .el-icon {
  width: 30px;
  height: 30px;
  grid-row: 1 / 3;
  align-self: center;
  border-radius: 9px;
  background: var(--primary-alpha-10);
  font-size: 17px;
}

.conversion-tab-label > span:not(.el-icon) {
  align-self: end;
  color: inherit;
  font-weight: 700;
}

.conversion-tab-label small {
  align-self: start;
  margin-top: 3px;
  color: var(--text-tertiary, var(--text-secondary));
  font-size: 11px;
}

.tab-content-shell {
  min-height: 480px;
  padding: 24px;
  background: color-mix(in srgb, var(--primary) 5%, var(--bg-page));
  animation: conversion-pane-in .2s ease-out both;
}

.tab-content-shell .panel:last-child {
  margin-bottom: 0;
}

.current-task-banner,
.current-task-banner > div,
.history-actions {
  display: flex;
  align-items: center;
}

.current-task-banner {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid var(--primary-alpha-15);
  border-radius: 14px;
  background: var(--primary-alpha-10);
}

.current-task-banner > div {
  min-width: 0;
  gap: 10px;
}

.current-task-banner small {
  overflow: hidden;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-state-dot {
  width: 9px;
  height: 9px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 0 5px var(--primary-alpha-10);
}

.source-method-field {
  display: grid;
  grid-template-columns: minmax(140px, 180px) minmax(240px, 360px) 1fr;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-hover);
}

.source-method-field label {
  font-weight: 700;
}

.source-method-field small {
  color: var(--text-secondary);
  line-height: 1.5;
}

.source-method-select {
  width: 100%;
}

.conversion-date-picker {
  width: 100%;
}

.source-method-content {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--border-color);
}

.library-search-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.library-results-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 14px;
  color: var(--text-secondary);
  font-size: 13px;
}

.library-view-switch {
  display: inline-flex;
  gap: 6px;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-hover);
}

.library-view-switch :deep(.el-button) {
  width: 32px;
  height: 32px;
  margin: 0;
  transition: color .18s ease, border-color .18s ease, background .18s ease, transform .18s ease;
}

.library-view-switch :deep(.el-button:focus-visible) {
  outline: 3px solid var(--primary-alpha-30, var(--primary));
  outline-offset: 2px;
}

.library-results {
  min-height: 300px;
  margin-top: 18px;
}

.library-book-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.library-book-grid.is-compact {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.library-book-card {
  display: flex;
  min-width: 0;
  padding: 12px;
  flex-direction: column;
  border: 1px solid color-mix(in srgb, var(--primary) 10%, var(--border-color));
  border-radius: 15px;
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
  transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease;
}

.library-book-card:hover,
.library-book-card.selected {
  border-color: var(--primary-alpha-30, var(--primary));
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.library-book-cover {
  position: relative;
  display: grid;
  width: 100%;
  aspect-ratio: 2 / 3;
  place-items: center;
  overflow: hidden;
  border-radius: 10px;
  background: linear-gradient(145deg, var(--primary-alpha-10), var(--surface-hover));
  color: var(--primary);
  font-size: 44px;
  font-weight: 700;
}

.library-book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.library-book-cover em {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 3px 7px;
  border-radius: 6px;
  background: rgba(20, 24, 32, .72);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  letter-spacing: .04em;
}

.library-book-info {
  min-width: 0;
  padding: 12px 2px;
  flex: 1;
}

.library-book-info h3,
.library-book-info p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.library-book-info h3 {
  margin: 0 0 5px;
  color: var(--text-primary);
  font-size: 15px;
}

.library-book-info p {
  margin: 0 0 7px;
  color: var(--text-secondary);
  font-size: 13px;
}

.library-book-info small {
  color: var(--text-tertiary, var(--text-secondary));
  font-size: 11px;
}

.library-book-card :deep(.el-button) {
  width: 100%;
  margin-left: 0;
}

.library-book-grid.is-compact .library-book-card {
  padding: 8px;
  border-radius: 11px;
}

.library-book-grid.is-compact .library-book-cover {
  border-radius: 7px;
  font-size: 30px;
}

.library-book-grid.is-compact .library-book-cover em {
  right: 5px;
  bottom: 5px;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 8px;
}

.library-book-grid.is-compact .library-book-info {
  padding: 8px 1px;
}

.library-book-grid.is-compact .library-book-info h3 {
  margin-bottom: 3px;
  font-size: 13px;
}

.library-book-grid.is-compact .library-book-info p {
  margin-bottom: 4px;
  font-size: 11px;
}

.library-book-grid.is-compact .library-book-info small {
  font-size: 10px;
}

.library-book-grid.is-compact .library-book-card :deep(.el-button) {
  min-height: 28px;
  padding: 5px 8px;
  border-radius: 7px;
  font-size: 11px;
}

.library-book-list-row {
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--primary) 10%, var(--border-color));
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
  transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease;
}

.library-list-cover {
  display: grid;
  place-items: center;
  overflow: hidden;
  background: linear-gradient(145deg, var(--primary-alpha-10), var(--surface-hover));
  color: var(--primary);
  font-weight: 700;
}

.library-list-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.library-list-primary {
  min-width: 0;
}

.library-list-primary h3,
.library-list-primary p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.library-list-primary h3 {
  margin: 0 0 5px;
  color: var(--text-primary);
  font-size: 14px;
}

.library-list-primary p {
  margin: 0 0 7px;
  color: var(--text-secondary);
  font-size: 12px;
}

.library-list-meta {
  display: flex;
  min-width: 0;
  gap: 6px;
  color: var(--text-tertiary, var(--text-secondary));
  font-size: 11px;
}

.library-list-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.library-list-meta span + span::before {
  margin-right: 6px;
  color: var(--border-color);
  content: '·';
}

.library-book-list {
  display: grid;
  gap: 10px;
}

.library-book-list-row {
  display: grid;
  grid-template-columns: 42px minmax(150px, 1fr) minmax(210px, auto) auto;
  align-items: center;
  gap: 14px;
  padding: 9px 12px;
  border-radius: 13px;
}

.library-list-cover {
  width: 42px;
  aspect-ratio: 2 / 3;
  border-radius: 6px;
  font-size: 17px;
}

.library-list-primary p {
  margin-bottom: 0;
}

.library-list-meta {
  justify-content: flex-end;
}

.library-book-list-row:hover,
.library-book-list-row.selected {
  border-color: var(--primary-alpha-30, var(--primary));
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.library-pagination-box {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding: 14px;
  overflow-x: auto;
  border: 1px solid var(--border-color);
  border-radius: 13px;
  background: var(--surface-hover);
}

.result-empty {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.result-empty-icon {
  display: grid;
  width: 72px;
  height: 72px;
  place-items: center;
  border-radius: 22px;
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-size: 38px;
}

.result-empty h2 {
  margin: 18px 0 6px;
}

.result-empty p {
  max-width: 460px;
  margin: 0 0 20px;
  color: var(--text-secondary);
}

.retry-action {
  margin-top: 18px;
  text-align: center;
}

.history-actions {
  justify-content: space-between;
  margin-bottom: 14px;
  color: var(--text-secondary);
}

:global(html[data-theme="modern"]) .conversion-tabs {
  border-color: #cfd6e2;
  border-radius: 16px;
  background: var(--surface-elevated);
  box-shadow: 0 18px 42px rgba(15, 23, 42, .12);
}

:global(html[data-theme="modern"]) .tab-content-shell {
  background: #edf1f7;
}

:global(html[data-theme="modern"]) .panel {
  border-color: #d9dee8;
  background: #fff;
  box-shadow: 0 8px 22px rgba(15, 23, 42, .08);
}

:global(html[data-theme="modern"]) .library-book-card,
:global(html[data-theme="modern"]) .library-book-list-row {
  border-color: #d9dee8;
  background: #fff;
  box-shadow: 0 4px 14px rgba(15, 23, 42, .07);
}

:global(html[data-theme="modern"]) .conversion-tabs > :deep(.el-tabs__header .el-tabs__item.is-active) {
  background: var(--primary-alpha-10);
  box-shadow: inset 0 0 0 1px var(--primary-alpha-15);
}

:global(html[data-theme="warm"]) .conversion-tabs {
  border-color: color-mix(in srgb, var(--primary) 24%, var(--border-color));
  border-radius: 18px;
  background: var(--surface-elevated);
  box-shadow: 0 18px 42px rgba(89, 57, 35, .15);
}

:global(html[data-theme="warm"]) .conversion-tabs > :deep(.el-tabs__header) {
  background: color-mix(in srgb, var(--primary) 9%, var(--surface-elevated));
}

:global(html[data-theme="warm"]) .conversion-tabs > :deep(.el-tabs__header .el-tabs__item) {
  border-radius: 10px;
}

:global(html[data-theme="warm"]) .conversion-tabs > :deep(.el-tabs__header .el-tabs__item.is-active) {
  border-color: color-mix(in srgb, var(--primary) 30%, transparent);
  background: color-mix(in srgb, var(--primary) 12%, var(--surface-elevated));
}

:global(html[data-theme="warm"]) .tab-content-shell {
  background: color-mix(in srgb, var(--primary) 9%, var(--bg-page));
}

:global(html[data-theme="warm"]) .panel {
  border-color: color-mix(in srgb, var(--primary) 24%, var(--border-color));
  background: #fffdf9;
  box-shadow: 0 9px 24px rgba(89, 57, 35, .1);
}

:global(html[data-theme="warm"]) .library-book-card,
:global(html[data-theme="warm"]) .library-book-list-row {
  border-color: color-mix(in srgb, var(--primary) 22%, var(--border-color));
  border-radius: 11px;
  background: #fffdf9;
  box-shadow: 0 5px 16px rgba(89, 57, 35, .08);
}

:global(html[data-theme="natural"]) .conversion-tabs {
  border-color: color-mix(in srgb, var(--primary) 30%, rgba(255, 255, 255, .72));
  background: rgba(255, 255, 255, .78);
  box-shadow: 0 22px 54px rgba(35, 83, 62, .19);
  backdrop-filter: blur(22px) saturate(150%);
  -webkit-backdrop-filter: blur(22px) saturate(150%);
}

:global(html[data-theme="natural"]) .tab-content-shell {
  background: color-mix(in srgb, var(--primary) 10%, rgba(235, 247, 241, .88));
}

:global(html[data-theme="natural"]) .panel {
  border-color: color-mix(in srgb, var(--primary) 20%, rgba(255, 255, 255, .82));
  background: rgba(255, 255, 255, .9);
  box-shadow: 0 10px 28px rgba(35, 83, 62, .12);
}

:global(html[data-theme="natural"]) .library-book-card,
:global(html[data-theme="natural"]) .library-book-list-row {
  border-color: color-mix(in srgb, var(--primary) 20%, rgba(255, 255, 255, .82));
  background: rgba(255, 255, 255, .82);
  box-shadow: 0 8px 22px rgba(35, 83, 62, .1);
}

:global(html[data-theme="macos26"]) .conversion-tabs {
  border-color: rgba(255, 255, 255, .78);
  background: rgba(248, 252, 255, .62);
  box-shadow:
    0 24px 70px rgba(42, 71, 113, .2),
    inset 0 1px 0 rgba(255, 255, 255, .86);
  backdrop-filter: blur(34px) saturate(190%) contrast(104%);
  -webkit-backdrop-filter: blur(34px) saturate(190%) contrast(104%);
}

:global(html[data-theme="macos26"]) .conversion-tabs > :deep(.el-tabs__header) {
  border-color: rgba(255, 255, 255, .62);
  background: rgba(238, 246, 255, .3);
}

:global(html[data-theme="macos26"]) .tab-content-shell {
  background: rgba(202, 222, 245, .46);
}

:global(html[data-theme="macos26"]) .conversion-tabs > :deep(.el-tabs__header .el-tabs__item.is-active) {
  border-color: rgba(255, 255, 255, .9);
  background: rgba(255, 255, 255, .58);
  box-shadow:
    0 8px 24px rgba(54, 91, 141, .15),
    inset 0 1px 0 rgba(255, 255, 255, .92);
}

:global(html[data-theme="macos26"]) .panel {
  border-color: rgba(255, 255, 255, .9);
  background: rgba(255, 255, 255, .76);
  box-shadow:
    0 16px 40px rgba(50, 80, 120, .18),
    inset 0 1px 0 rgba(255, 255, 255, .96);
  backdrop-filter: blur(24px) saturate(165%);
  -webkit-backdrop-filter: blur(24px) saturate(165%);
}

:global(html[data-theme="macos26"]) .library-book-card,
:global(html[data-theme="macos26"]) .library-book-list-row {
  border-color: rgba(255, 255, 255, .88);
  background: rgba(255, 255, 255, .68);
  box-shadow: 0 10px 26px rgba(50, 80, 120, .14), inset 0 1px 0 rgba(255, 255, 255, .92);
  backdrop-filter: blur(18px) saturate(155%);
  -webkit-backdrop-filter: blur(18px) saturate(155%);
}

@keyframes conversion-pane-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 720px) {
  .conversion-tabs {
    border-radius: 18px;
  }

  .conversion-tabs > :deep(.el-tabs__header) {
    padding: 8px;
  }

  .conversion-tabs > :deep(.el-tabs__header .el-tabs__nav) {
    gap: 5px;
  }

  .conversion-tabs > :deep(.el-tabs__header .el-tabs__item) {
    height: 58px;
    padding: 0 7px !important;
  }

  .conversion-tab-label {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    text-align: center;
  }

  .conversion-tab-label .el-icon {
    width: 25px;
    height: 25px;
    flex: 0 0 auto;
    font-size: 14px;
  }

  .conversion-tab-label > span:not(.el-icon) {
    font-size: 12px;
  }

  .conversion-tab-label small {
    display: none;
  }

  .tab-content-shell {
    padding: 12px;
  }

  .current-task-banner,
  .current-task-banner > div {
    align-items: stretch;
    flex-direction: column;
  }

  .source-method-field {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .library-search-bar {
    grid-template-columns: 1fr;
  }

  .library-results-toolbar {
    justify-content: space-between;
  }

  .library-book-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .library-book-card {
    padding: 9px;
  }

  .library-book-grid.is-compact {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .library-book-list-row {
    grid-template-columns: 38px minmax(0, 1fr) auto;
    gap: 10px;
    padding: 8px;
  }

  .library-list-cover {
    width: 38px;
  }

  .library-book-list-row .library-list-meta {
    grid-column: 2 / -1;
    justify-content: flex-start;
    margin-top: -5px;
  }

  .library-pagination-box {
    justify-content: flex-start;
  }

  .library-pagination-box :deep(.el-pagination__jump),
  .library-pagination-box :deep(.el-pagination__total) {
    display: none;
  }
}

@media (min-width: 721px) and (max-width: 980px) {
  .library-book-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .library-book-grid.is-compact {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .library-book-grid.is-compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .tab-content-shell,
  .library-book-card,
  .library-book-list-row,
  .library-view-switch :deep(.el-button) {
    animation: none;
    transition: none;
  }
}
</style>
