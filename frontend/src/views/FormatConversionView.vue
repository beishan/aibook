<template>
  <div class="conversion-page">
    <header class="page-heading">
      <div><span class="eyebrow">独立工具</span><h1>格式转换</h1><p>TXT → EPUB · 转换结果不会自动加入书库</p></div>
      <el-button @click="showHistory = !showHistory">{{ showHistory ? '返回转换' : '转换历史' }}</el-button>
    </header>

    <section v-if="showHistory" class="panel history-panel">
      <div class="section-title"><div><span class="step">历史</span><h2>转换历史</h2></div><el-button @click="loadHistory">刷新</el-button></div>
      <el-table :data="history" empty-text="暂无转换任务">
        <el-table-column prop="title" label="书籍" min-width="180" />
        <el-table-column label="格式" width="120"><template #default="{ row }">{{ row.sourceFormat.toUpperCase() }} → {{ row.targetFormat.toUpperCase() }}</template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="210"><template #default="{ row }"><el-button link type="primary" @click="openTask(row.id)">查看/继续</el-button><el-button v-if="row.status === 'SUCCESS'" link @click="download(row)">下载</el-button><el-button link type="danger" @click="removeTask(row.id)">删除</el-button></template></el-table-column>
      </el-table>
    </section>

    <template v-else>
      <section v-if="!task" class="panel source-picker">
        <div class="section-title"><div><span class="step">01</span><h2>选择源文件</h2></div><el-tag>第一期仅支持 TXT</el-tag></div>
        <el-tabs v-model="sourceMode" stretch>
          <el-tab-pane label="上传本地 TXT" name="upload">
            <el-upload drag :auto-upload="false" :limit="1" accept=".txt,text/plain" :on-change="onSourceSelected" :on-remove="() => selectedFile = null">
              <div class="upload-icon">⇄</div><div>拖放 TXT 到这里，或点击选择</div><small>文件仅用于转换，不会自动入库</small>
            </el-upload>
            <el-button class="primary-wide" type="primary" :loading="creating" :disabled="!selectedFile" @click="createFromUpload">分析 TXT</el-button>
          </el-tab-pane>
          <el-tab-pane label="从书库选择" name="library">
            <el-select v-model="selectedBookId" filterable placeholder="选择书籍" class="wide" @change="loadBookVersions"><el-option v-for="book in books" :key="book.id" :label="`${book.title} · ${book.author || '未知作者'}`" :value="book.id" /></el-select>
            <div v-if="bookVersions.length" class="source-versions"><label v-for="version in bookVersions" :key="version.id" :class="{ disabled: version.format !== 'txt' }"><el-radio v-model="selectedVersionId" :label="version.id" :disabled="version.format !== 'txt'"><span class="sr-only">选择 {{ version.displayName }}</span></el-radio><strong>{{ version.format.toUpperCase() }}</strong><span>{{ version.displayName }} · {{ formatSize(version.fileSize) }}</span></label></div>
            <el-button class="primary-wide" type="primary" :loading="creating" :disabled="!selectedVersionId" @click="createFromBook">分析所选版本</el-button>
          </el-tab-pane>
        </el-tabs>
      </section>

      <template v-else>
        <section class="panel source-card">
          <div class="section-title"><div><span class="step">01</span><h2>源书籍信息</h2></div><el-button link @click="resetTask">更换源文件</el-button></div>
          <div class="source-grid">
            <div class="cover-box" @click="coverInput?.click()"><img v-if="coverObjectUrl" :src="coverObjectUrl" alt="EPUB 封面" /><span v-else>{{ task.title?.charAt(0) || '书' }}</span><em>点击上传封面</em></div>
            <input ref="coverInput" hidden type="file" accept="image/jpeg,image/png,image/webp" @change="uploadCover" />
            <div class="source-main"><h3>{{ task.title }}</h3><p>{{ task.author || '未知作者' }}</p><div class="facts"><span><b>格式</b> TXT</span><span><b>大小</b> {{ formatSize(task.sourceSize) }}</span><span><b>编码</b> {{ task.encoding }}</span><span><b>字符</b> {{ formatNumber(task.characterCount) }}</span><span><b>章节</b> {{ task.chapters?.length || 0 }}</span><span><b>换行</b> {{ task.newlineFormat }}</span></div><div class="cover-actions"><el-button @click="coverInput?.click()">上传封面</el-button><el-button @click="openCoverLibrary">封面库</el-button><el-button :loading="randomizing" @click="randomCover">随机一个</el-button></div></div>
          </div>
        </section>

        <section class="panel">
          <div class="section-title"><div><span class="step">02</span><h2>书籍元信息</h2></div><small>修改仅影响本次 EPUB</small></div>
          <el-form label-position="top" class="metadata-grid"><el-form-item label="书籍名称（必填）"><el-input v-model="form.title" /></el-form-item><el-form-item label="作者"><el-input v-model="form.author" /></el-form-item><el-form-item label="ISBN"><el-input v-model="form.isbn" /></el-form-item><el-form-item label="出版社"><el-input v-model="form.publisher" /></el-form-item><el-form-item label="出版日期"><el-input v-model="form.publishDate" /></el-form-item><el-form-item label="语言"><el-input v-model="form.language" /></el-form-item><el-form-item label="分类"><el-input v-model="form.categoryName" /></el-form-item><el-form-item label="标签（逗号分隔）"><el-input v-model="tagInput" /></el-form-item><el-form-item label="系列名称"><el-input v-model="form.seriesName" /></el-form-item><el-form-item label="系列序号"><el-input v-model="form.seriesIndex" /></el-form-item><el-form-item label="简介" class="full"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item></el-form>
        </section>

        <section class="panel">
          <div class="section-title"><div><span class="step">03</span><h2>内容解析</h2></div><el-tag :type="task.anomalyCount ? 'warning' : 'success'">{{ task.anomalyCount || 0 }} 项疑似异常</el-tag></div>
          <div class="analysis-row"><div><b>{{ task.encoding }}</b><span>文件编码</span></div><div><b>{{ task.chapters?.length || 0 }}</b><span>检测章节</span></div><div><b>{{ formatNumber(task.characterCount) }}</b><span>正文字符</span></div><div><b>{{ task.newlineFormat }}</b><span>换行格式</span></div></div>
          <el-alert v-if="task.anomalyCount" title="检测到乱码特征或重复章节标题，请在转换前检查章节列表。" type="warning" :closable="false" show-icon />
          <div class="chapter-rule">
            <el-radio-group v-model="chapterMode" @change="handleChapterModeChange"><el-radio-button label="auto">自动识别</el-radio-button><el-radio-button label="custom">自定义规则</el-radio-button></el-radio-group>
            <template v-if="chapterMode === 'custom'"><el-input v-model="form.chapterPattern" placeholder="例如：^第.{1,10}[章节卷回].*$" /><el-button :loading="analyzingChapters" @click="reanalyzeChapters">重新识别</el-button></template>
          </div>
          <div class="chapter-toolbar"><h3>章节结构</h3><span>可修改标题或忽略章节</span></div>
          <div class="chapter-list"><div v-for="(chapter, index) in form.chapters" :key="chapter.index" class="chapter-row" :class="{ ignored: chapter.ignored }"><span>{{ String(index + 1).padStart(3, '0') }}</span><el-input v-model="chapter.title" :disabled="chapter.ignored" /><el-checkbox v-model="chapter.ignored">忽略</el-checkbox></div></div>
        </section>

        <section class="panel">
          <div class="section-title"><div><span class="step">04</span><h2>EPUB 参数</h2></div><el-tag>EPUB 3</el-tag></div>
          <div class="settings-grid"><el-form-item label="EPUB 文件名"><el-input v-model="form.outputFilename" /></el-form-item><el-form-item label="首行缩进"><el-select v-model="form.firstLineIndent"><el-option label="不缩进" value="0" /><el-option label="1em" value="1em" /><el-option label="2em" value="2em" /></el-select></el-form-item><el-form-item label="段落间距"><el-select v-model="form.paragraphSpacing"><el-option label="无" value="none" /><el-option label="小" value="small" /><el-option label="中" value="medium" /><el-option label="大" value="large" /></el-select></el-form-item><el-form-item label="行高"><el-select v-model="form.lineHeight"><el-option v-for="n in [1.4,1.5,1.6,1.8,2]" :key="n" :label="String(n)" :value="n" /></el-select></el-form-item></div>
          <div class="cleanup-options"><el-checkbox v-model="form.removeExtraBlankLines">清理多余空行</el-checkbox><el-checkbox v-model="form.trimLineEnd">清理行尾空格</el-checkbox><el-checkbox v-model="form.normalizeWidth">全角/半角规范化</el-checkbox></div>
          <div class="convert-actions"><el-button :loading="saving" @click="saveConfig">保存配置</el-button><el-button type="primary" size="large" :loading="converting" :disabled="converting" @click="startConversion">{{ task.status === 'SUCCESS' ? '重新转换' : '开始转换' }}</el-button></div>
        </section>

        <section v-if="['CONVERTING','SUCCESS','FAILED'].includes(task.status)" class="panel progress-panel">
          <div class="section-title"><div><span class="step">05</span><h2>转换进度</h2></div><strong>{{ task.progress }}%</strong></div><el-progress :percentage="task.progress" :status="task.status === 'FAILED' ? 'exception' : task.status === 'SUCCESS' ? 'success' : undefined" /><p>{{ task.stage }}</p><el-alert v-if="task.status === 'FAILED'" :title="task.errorMessage || '转换失败，请保留配置后重试'" type="error" :closable="false" show-icon />
        </section>

        <section v-if="task.status === 'SUCCESS'" class="panel preview-panel">
          <div class="section-title"><div><span class="step">06</span><h2>EPUB 预览</h2></div><div><el-button :disabled="preview.chapterIndex <= 0" @click="loadPreview(preview.chapterIndex - 1)">上一章</el-button><el-button :disabled="preview.chapterIndex >= preview.chapterCount - 1" @click="loadPreview(preview.chapterIndex + 1)">下一章</el-button></div></div>
          <div class="reader-preview" :class="{ night: previewNight }"><div class="preview-tools"><el-select :model-value="preview.chapterIndex" @change="loadPreview"><el-option v-for="(chapter, index) in activeChapters" :key="chapter.index" :label="chapter.title" :value="index" /></el-select><el-button @click="previewNight = !previewNight">{{ previewNight ? '日间' : '夜间' }}</el-button></div><div class="preview-book"><img v-if="coverObjectUrl" :src="coverObjectUrl" alt="封面" /><div><h1>{{ task.title }}</h1><p>{{ task.author || '未知作者' }}</p></div></div><article><h2>{{ preview.chapterTitle }}</h2><p v-for="(paragraph, index) in previewParagraphs" :key="index">{{ paragraph }}</p></article></div>
        </section>

        <section v-if="task.status === 'SUCCESS'" class="panel result-panel">
          <div class="result-summary"><span class="success-mark">✓</span><div><h2>转换成功</h2><p>{{ task.outputFilename }} · {{ formatSize(task.outputSize) }} · {{ activeChapters.length }} 章 · {{ formatElapsed(task.elapsedMillis) }}</p></div></div>
          <div class="result-actions"><el-button type="primary" @click="download(task)">下载 EPUB</el-button><el-button @click="startConversion">重新转换</el-button><el-button type="danger" plain @click="removeTask(task.id)">删除转换结果</el-button></div>
          <div class="library-actions"><el-button v-if="task.sourceBookId" type="success" size="large" @click="attachToBook(task.sourceBookId)">加入到当前书籍的新版本</el-button><template v-else><el-button type="success" size="large" @click="createBook">创建新书籍</el-button><el-button size="large" @click="showBookDialog = true">关联已有书籍</el-button></template></div>
        </section>
      </template>
    </template>

    <el-dialog v-model="showCoverDialog" title="从封面库选择" width="min(760px, 92vw)"><el-input v-model="coverSearch" clearable placeholder="搜索封面文件名" /><div class="cover-library"><button v-for="cover in filteredCovers" :key="cover.id" @click="chooseCover(cover.id)"><img :src="getCoverUrl(cover.url)" :alt="cover.originalFilename" /><span>{{ cover.originalFilename }}</span></button></div><el-empty v-if="!filteredCovers.length" description="封面库为空" /></el-dialog>
    <el-dialog v-model="showBookDialog" title="关联已有书籍" width="min(560px, 92vw)"><el-select v-model="attachBookId" filterable class="wide" placeholder="选择书籍"><el-option v-for="book in books" :key="book.id" :label="`${book.title} · ${book.author || '未知作者'}`" :value="book.id" /></el-select><template #footer><el-button @click="showBookDialog = false">取消</el-button><el-button type="primary" :disabled="!attachBookId" @click="attachToBook(attachBookId)">确认关联</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/utils/api'
import { getCoverUrl } from '@/utils/cover'
import { message, confirm } from '@/utils/message'

const route = useRoute(); const router = useRouter()
const task = ref<any>(null); const history = ref<any[]>([]); const books = ref<any[]>([]); const bookVersions = ref<any[]>([])
const sourceMode = ref('upload'); const selectedFile = ref<File | null>(null); const selectedBookId = ref<number | null>(null); const selectedVersionId = ref<number | null>(null)
const creating = ref(false); const saving = ref(false); const converting = ref(false); const randomizing = ref(false); const analyzingChapters = ref(false); const showHistory = ref(false)
const coverInput = ref<HTMLInputElement | null>(null); const coverObjectUrl = ref(''); const showCoverDialog = ref(false); const covers = ref<any[]>([]); const coverSearch = ref('')
const showBookDialog = ref(false); const attachBookId = ref<number | null>(null); const tagInput = ref(''); const previewNight = ref(false)
const preview = reactive<any>({ chapterIndex: 0, chapterCount: 0, chapterTitle: '', content: '' })
const chapterMode = ref('auto')
const form = reactive<any>({ title: '', author: '', description: '', isbn: '', publisher: '', publishDate: '', language: 'zh-CN', categoryName: '', seriesName: '', seriesIndex: '', outputFilename: '', chapterPattern: '', epubVersion: '3', firstLineIndent: '2em', paragraphSpacing: 'small', lineHeight: 1.6, removeExtraBlankLines: true, trimLineEnd: true, normalizeWidth: false, chapters: [] })
const activeChapters = computed(() => form.chapters.filter((chapter: any) => !chapter.ignored))
const previewParagraphs = computed(() => String(preview.content || '').split(/\n+/).map(value => value.trim()).filter(Boolean))
const filteredCovers = computed(() => covers.value.filter(c => !coverSearch.value || c.originalFilename.toLowerCase().includes(coverSearch.value.toLowerCase())))

const syncTask = (value: any) => { task.value = value; Object.assign(form, value.settings || {}, { title: value.title, author: value.author || '', description: value.description || '', isbn: value.isbn || '', publisher: value.publisher || '', publishDate: value.publishDate || '', language: value.language || 'zh-CN', categoryName: value.categoryName || '', seriesName: value.seriesName || '', seriesIndex: value.seriesIndex || '', outputFilename: value.outputFilename || `${value.title}.epub`, chapters: (value.chapters || []).map((c: any) => ({ ...c })) }); chapterMode.value = form.chapterPattern ? 'custom' : 'auto'; tagInput.value = (value.tags || []).join(', '); void refreshCover() }
const loadBooks = async () => { const { data } = await api.get('/api/books', { params: { page: 0, size: 1000, sortBy: 'title', sortDir: 'asc' } }); books.value = data.content || [] }
const loadHistory = async () => { history.value = (await api.get('/api/conversions')).data || [] }
const loadBookVersions = async () => { selectedVersionId.value = null; if (!selectedBookId.value) return; bookVersions.value = (await api.get(`/api/books/${selectedBookId.value}/versions`)).data || []; selectedVersionId.value = bookVersions.value.find((v: any) => v.format === 'txt')?.id || null }
const onSourceSelected = (upload: any) => { selectedFile.value = upload.raw }
const createFromUpload = async () => { if (!selectedFile.value) return; creating.value = true; try { const data = new FormData(); data.append('file', selectedFile.value); const response = await api.post('/api/conversions/upload', data); syncTask(response.data); await router.replace({ path: '/format-conversion', query: { taskId: response.data.id } }); await loadHistory() } catch (e: any) { message.error(e.response?.data?.message || 'TXT 分析失败') } finally { creating.value = false } }
const createFromBook = async () => { if (!selectedBookId.value || !selectedVersionId.value) return; creating.value = true; try { const { data } = await api.post('/api/conversions/from-book', { bookId: selectedBookId.value, versionId: selectedVersionId.value }); syncTask(data); await router.replace({ path: '/format-conversion', query: { taskId: data.id } }); await loadHistory() } catch (e: any) { message.error(e.response?.data?.message || '书籍分析失败') } finally { creating.value = false } }
const saveConfig = async (quiet = false) => { if (!form.title.trim()) { message.warning('书籍名称不能为空'); throw new Error('title') } saving.value = true; try { const payload = { ...form, title: form.title.trim(), tags: tagInput.value.split(/[,，]/).map(v => v.trim()).filter(Boolean) }; const { data } = await api.put(`/api/conversions/${task.value.id}`, payload); syncTask(data); if (!quiet) message.success('转换配置已保存'); return data } finally { saving.value = false } }
const startConversion = async () => { converting.value = true; try { await saveConfig(true); task.value.status = 'CONVERTING'; task.value.progress = 55; task.value.stage = '正在生成 EPUB 内容'; const { data } = await api.post(`/api/conversions/${task.value.id}/convert`); syncTask(data); if (data.status === 'SUCCESS') { message.success('EPUB 转换完成'); await loadPreview(0) } else message.error(data.errorMessage || '转换失败') } catch (e: any) { if (e.message !== 'title') message.error(e.response?.data?.message || '转换失败') } finally { converting.value = false; await loadHistory() } }
const uploadCover = async (event: Event) => { const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return; const body = new FormData(); body.append('file', file); try { syncTask((await api.post(`/api/conversions/${task.value.id}/cover`, body)).data); message.success('封面已更新') } catch (e: any) { message.error(e.response?.data?.message || '封面上传失败') } finally { input.value = '' } }
const refreshCover = async () => { if (coverObjectUrl.value) URL.revokeObjectURL(coverObjectUrl.value); coverObjectUrl.value = ''; if (!task.value?.coverUrl) return; try { const { data } = await api.get(task.value.coverUrl, { responseType: 'blob' }); coverObjectUrl.value = URL.createObjectURL(data) } catch { /* 无封面时显示占位 */ } }
const openCoverLibrary = async () => { covers.value = (await api.get('/api/random-book-covers')).data || []; showCoverDialog.value = true }
const chooseCover = async (id: number) => { syncTask((await api.post(`/api/conversions/${task.value.id}/cover/library/${id}`)).data); showCoverDialog.value = false; message.success('已选用封面库图片') }
const randomCover = async () => { randomizing.value = true; try { syncTask((await api.post(`/api/conversions/${task.value.id}/cover/random`)).data) } catch (e: any) { message.warning(e.response?.data?.message || '随机封面失败') } finally { randomizing.value = false } }
const reanalyzeChapters = async () => { if (!form.chapterPattern?.trim()) return message.warning('请输入章节识别正则表达式'); analyzingChapters.value = true; try { syncTask((await api.post(`/api/conversions/${task.value.id}/analyze-chapters`, { pattern: form.chapterPattern.trim() })).data); chapterMode.value = 'custom'; message.success('章节已重新识别') } catch (e: any) { message.error(e.response?.data?.message || '章节识别失败') } finally { analyzingChapters.value = false } }
const handleChapterModeChange = async (mode: string | number | boolean | undefined) => { if (mode !== 'auto' || !form.chapterPattern) return; analyzingChapters.value = true; try { syncTask((await api.post(`/api/conversions/${task.value.id}/analyze-chapters`, { pattern: '' })).data); chapterMode.value = 'auto'; message.success('已恢复自动章节识别') } catch (e: any) { message.error(e.response?.data?.message || '自动识别失败') } finally { analyzingChapters.value = false } }
const loadPreview = async (chapter = 0) => { Object.assign(preview, (await api.get(`/api/conversions/${task.value.id}/preview`, { params: { chapter } })).data) }
const download = async (row: any) => { const { data } = await api.get(`/api/conversions/${row.id}/download`, { responseType: 'blob' }); const url = URL.createObjectURL(data); const a = document.createElement('a'); a.href = url; a.download = row.outputFilename || `${row.title}.epub`; a.click(); URL.revokeObjectURL(url) }
const attachToBook = async (bookId: number) => { try { await api.post(`/api/conversions/${task.value.id}/attach/${bookId}`); showBookDialog.value = false; message.success('已加入书籍的新版本'); await router.push(`/books/${bookId}`) } catch (e: any) { message.error(e.response?.data?.message || '关联失败') } }
const createBook = async () => { try { const { data } = await api.post(`/api/conversions/${task.value.id}/create-book`); message.success('新书籍已创建'); await router.push(`/books/${data.id}`) } catch (e: any) { message.error(e.response?.data?.message || '新建书籍失败') } }
const openTask = async (id: number) => { syncTask((await api.get(`/api/conversions/${id}`)).data); showHistory.value = false; await router.replace({ path: '/format-conversion', query: { taskId: id } }); if (task.value.status === 'SUCCESS') await loadPreview(0) }
const removeTask = async (id: number) => { if (!await confirm('确定删除该转换任务及临时结果吗？')) return; await api.delete(`/api/conversions/${id}`); if (task.value?.id === id) resetTask(); await loadHistory(); message.success('转换任务已删除') }
const resetTask = () => { task.value = null; selectedFile.value = null; selectedBookId.value = null; selectedVersionId.value = null; void router.replace('/format-conversion') }
const formatSize = (bytes?: number) => { if (!bytes) return '0 B'; const units = ['B','KB','MB','GB']; let value = bytes; let i = 0; while (value >= 1024 && i < 3) { value /= 1024; i++ } return `${value.toFixed(i ? 2 : 0)} ${units[i]}` }
const formatNumber = (n?: number) => new Intl.NumberFormat('zh-CN').format(n || 0)
const formatElapsed = (ms?: number) => ms == null ? '-' : `${(ms / 1000).toFixed(1)} 秒`
const statusText = (s: string) => ({ CREATED:'已创建',ANALYZING:'正在分析',READY:'等待转换',CONVERTING:'正在转换',SUCCESS:'成功',FAILED:'失败',CANCELLED:'已取消' } as any)[s] || s
const statusType = (s: string) => s === 'SUCCESS' ? 'success' : s === 'FAILED' ? 'danger' : s === 'CONVERTING' ? 'warning' : 'info'

onMounted(async () => { await Promise.all([loadBooks(), loadHistory()]); const taskId = Number(route.query.taskId); if (taskId) await openTask(taskId); else { const bookId = Number(route.query.bookId), versionId = Number(route.query.versionId); if (bookId && versionId) { selectedBookId.value = bookId; selectedVersionId.value = versionId; await createFromBook() } } })
onBeforeUnmount(() => { if (coverObjectUrl.value) URL.revokeObjectURL(coverObjectUrl.value) })
</script>

<style scoped>
.sr-only{position:absolute;width:1px;height:1px;padding:0;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.conversion-page{max-width:1120px;margin:0 auto;padding:28px 0 70px}.page-heading,.section-title,.source-grid,.convert-actions,.result-summary,.result-actions,.library-actions,.chapter-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:22px}.page-heading h1{margin:3px 0;font-size:32px}.page-heading p,.section-title small,.chapter-toolbar span{margin:0;color:var(--text-secondary)}.eyebrow,.step{color:var(--primary);font-size:12px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}.panel{margin-bottom:18px;padding:24px;border:1px solid var(--border-color);border-radius:22px;background:var(--card-bg);box-shadow:var(--shadow-sm)}.section-title{margin-bottom:22px}.section-title h2{margin:3px 0 0;font-size:20px}.wide,.primary-wide{width:100%}.primary-wide{margin-top:18px}.upload-icon{margin:8px;font-size:42px;color:var(--primary)}.source-versions{display:grid;gap:10px;margin-top:16px}.source-versions label{display:grid;grid-template-columns:auto 80px 1fr;align-items:center;padding:13px;border:1px solid var(--border-color);border-radius:12px}.source-versions label.disabled{opacity:.5}.source-grid{justify-content:flex-start;align-items:flex-start}.cover-box{position:relative;display:grid;width:150px;aspect-ratio:2/3;flex:0 0 auto;place-items:center;overflow:hidden;border-radius:14px;background:linear-gradient(145deg,var(--primary-alpha-10),var(--bg-secondary));color:var(--primary);font-size:54px;cursor:pointer}.cover-box img{width:100%;height:100%;object-fit:cover}.cover-box em{position:absolute;inset:auto 0 0;padding:8px;background:#0009;color:#fff;font-size:12px;font-style:normal;text-align:center}.source-main{flex:1}.source-main h3{margin:4px 0;font-size:27px}.source-main p{color:var(--text-secondary)}.facts,.analysis-row{display:grid;grid-template-columns:repeat(3,minmax(100px,1fr));gap:10px}.facts span,.analysis-row div{display:flex;padding:12px;flex-direction:column;border-radius:12px;background:var(--bg-secondary)}.facts b,.analysis-row span{color:var(--text-secondary);font-size:12px}.analysis-row b{font-size:20px}.cover-actions{display:flex;gap:8px;margin-top:16px}.metadata-grid,.settings-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.metadata-grid .full{grid-column:1/-1}.chapter-toolbar{margin:22px 0 10px}.chapter-list{max-height:390px;overflow:auto;border:1px solid var(--border-color);border-radius:12px}.chapter-row{display:grid;grid-template-columns:50px 1fr 70px;align-items:center;gap:10px;padding:9px 12px;border-bottom:1px solid var(--border-color)}.chapter-row:last-child{border:0}.chapter-row.ignored{opacity:.55}.cleanup-options{display:flex;gap:24px;flex-wrap:wrap}.convert-actions{justify-content:flex-end;margin-top:22px}.progress-panel p{text-align:center;color:var(--text-secondary)}.reader-preview{overflow:hidden;border:1px solid var(--border-color);border-radius:16px;background:#f8f1df;color:#332b22}.reader-preview.night{background:#1f2428;color:#d7dadd}.preview-tools{display:flex;justify-content:space-between;padding:10px;border-bottom:1px solid #8884}.reader-preview article{max-height:540px;padding:32px 8%;overflow:auto}.reader-preview article h2{text-align:center}.reader-preview article p{text-indent:2em;line-height:1.8}.success-mark{display:grid;width:52px;height:52px;place-items:center;border-radius:50%;background:#e7f7ed;color:#2d9b58;font-size:28px}.result-summary{justify-content:flex-start}.result-summary h2{margin:0}.result-summary p{margin:5px 0;color:var(--text-secondary)}.result-actions,.library-actions{justify-content:flex-start;margin-top:20px}.library-actions{padding-top:20px;border-top:1px solid var(--border-color)}.cover-library{display:grid;grid-template-columns:repeat(auto-fill,minmax(115px,1fr));gap:12px;margin-top:16px;max-height:55vh;overflow:auto}.cover-library button{padding:0;overflow:hidden;border:2px solid transparent;border-radius:12px;background:var(--bg-secondary);cursor:pointer}.cover-library button:hover{border-color:var(--primary)}.cover-library img{width:100%;aspect-ratio:2/3;object-fit:cover}.cover-library span{display:block;padding:7px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-panel{overflow:auto}@media(max-width:720px){.conversion-page{padding:18px 0}.page-heading,.source-grid{align-items:stretch;flex-direction:column}.panel{padding:18px;border-radius:16px}.source-grid .cover-box{align-self:center}.metadata-grid,.settings-grid,.facts,.analysis-row{grid-template-columns:1fr}.result-actions,.library-actions{align-items:stretch;flex-direction:column}.result-actions :deep(button),.library-actions :deep(button){margin-left:0}.chapter-row{grid-template-columns:38px 1fr}.chapter-row :deep(.el-checkbox){grid-column:2}}
.chapter-rule{display:flex;align-items:center;gap:10px;margin-top:18px}.chapter-rule :deep(.el-input){flex:1}.preview-book{display:flex;align-items:center;justify-content:center;gap:18px;padding:28px 8% 0;text-align:left}.preview-book img{width:72px;aspect-ratio:2/3;object-fit:cover;border-radius:6px}.preview-book h1,.preview-book p{margin:4px}@media(max-width:720px){.chapter-rule{align-items:stretch;flex-direction:column}}
</style>
