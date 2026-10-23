<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${source?.name || ''} · ${manual ? '手动刮削' : '试刮削'}`"
    class="scrape-workbench-dialog"
    width="1120px"
    top="5vh"
    style="max-width: calc(100vw - 24px)"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header="{ titleId }">
      <div class="trial-workbench-header">
        <span class="trial-brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M4 5.5c3-1 5.5-.5 8 1.2 2.5-1.7 5-2.2 8-1.2v14c-3-1-5.5-.5-8 1.2-2.5-1.7-5-2.2-8-1.2v-14Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
            <path d="M12 7v13M7 10h2M15 10h2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </span>
        <div class="trial-workbench-title">
          <span>元信息工作台</span>
          <h2 :id="titleId">
            {{ manual ? '手动刮削' : '试刮削' }}
            <span class="trial-source-pill">{{ source?.name }}</span>
          </h2>
        </div>
        <span class="trial-header-note">查询与预览不写入书库</span>
      </div>
    </template>
    <div v-if="book" class="trial-target-banner">
      <div class="trial-target-copy">
        <span class="trial-result-eyebrow">当前书籍</span>
        <strong>{{ displayBookName(book.title) }}</strong>
        <span>{{ book.author || '未知作者' }}</span>
      </div>
      <el-button type="primary" :disabled="running || confirming" :loading="running" @click="start">
        {{ snapshot ? '重新查询' : '开始查询' }}
      </el-button>
    </div>
    <p v-else class="trial-intro">选择书籍，查看来源查询过程，再核对结果。使用已保存配置，绕过缓存；默认补全空字段，也可选择覆盖已有内容。</p>
    <div class="trial-tab-scroll">
      <div
        ref="tabList"
        class="trial-tabs"
        :style="{ gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))` }"
        role="tablist"
        :aria-label="manual ? '手动刮削步骤' : '试刮削步骤'"
        @keydown="switchTabByKeyboard"
      >
        <span class="trial-tab-indicator" :style="tabIndicatorStyle" aria-hidden="true"></span>
        <button
          v-for="(tab, index) in tabs"
          :id="`trial-tab-${tab.value}`"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.value"
          :aria-controls="`trial-panel-${tab.value}`"
          :tabindex="activeTab === tab.value ? 0 : -1"
          @click="activeTab = tab.value"
        >
          <span class="trial-tab-number" aria-hidden="true">0{{ index + 1 }}</span>
          {{ tab.label }}
        </button>
      </div>
    </div>
    <section
      v-if="!book"
      v-show="activeTab === 'books'"
      id="trial-panel-books"
      class="trial-panel"
      role="tabpanel"
      aria-labelledby="trial-tab-books"
      tabindex="0"
    >
      <fieldset :disabled="running" class="trial-filters">
        <el-input v-model="filters.keyword" placeholder="搜索书名、作者、ISBN" clearable @keyup.enter="search" />
        <el-select :disabled="running" v-model="filters.sourceType" placeholder="全部来源" clearable>
          <el-option label="上传" value="UPLOAD" />
          <el-option label="目录扫描" value="DIRECTORY_SCAN" />
          <el-option label="网站采集" value="CRAWLER" />
        </el-select>
        <el-select :disabled="running" v-model="filters.format" placeholder="全部文件类型" clearable>
          <el-option v-for="format in formats" :key="format" :label="format.toUpperCase()" :value="format" />
        </el-select>
        <el-select :disabled="running" v-model="filters.categoryId" placeholder="全部分类" clearable filterable>
          <el-option v-for="category in categories" :key="category.id" :label="category.path || category.name" :value="category.id" />
        </el-select>
        <el-select :disabled="running" v-model="filters.tagId" placeholder="全部标签" clearable filterable>
          <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
        <el-date-picker
          :disabled="running"
          v-model="dates"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="入库开始日期"
          end-placeholder="入库结束日期"
        />
        <el-button :loading="loading" :disabled="running" @click="search">搜索 / 筛选</el-button>
      </fieldset>
      <p v-if="loadError" role="alert">{{ loadError }}</p>
      <div class="trial-browser-toolbar">
        <strong>选择书籍 · 共 {{ total }} 本</strong>
        <div
          class="trial-view-switch"
          :class="{ 'is-card': viewMode === 'card' }"
          role="radiogroup"
          aria-label="选书视图"
          @keydown="switchViewByKeyboard"
        >
          <span class="trial-view-indicator" aria-hidden="true"></span>
          <button
            v-for="option in viewOptions"
            :key="option.value"
            type="button"
            role="radio"
            :aria-checked="viewMode === option.value"
            :tabindex="viewMode === option.value ? 0 : -1"
            @click="setViewMode(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      <div v-loading="loading" class="trial-book-browser" :aria-busy="loading">
        <el-table
          v-if="viewMode === 'table'"
          :data="books"
          height="100%"
          row-key="id"
          :current-row-key="selected?.id"
          empty-text="没有符合条件的书籍"
          highlight-current-row
          @current-change="selectBook"
        >
          <el-table-column label="选择" width="65">
            <template #default="{ row }">
              <input
                type="radio"
                :checked="selected?.id === row.id"
                :disabled="running || confirming"
                :aria-label="`选择${displayBookName(row.title)}`"
                @change="selectBook(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="书名" min-width="180">
            <template #default="{ row }">{{ displayBookName(row.title) }}</template>
          </el-table-column>
          <el-table-column prop="author" label="作者" min-width="100" />
          <el-table-column prop="format" label="类型" width="80" />
          <el-table-column prop="categoryName" label="分类" min-width="100" />
          <el-table-column prop="createdAt" label="入库时间" min-width="165" />
        </el-table>
        <div v-else class="trial-card-scroll">
          <el-empty v-if="books.length === 0" description="没有符合条件的书籍" />
          <div v-else class="trial-card-grid">
            <button
              v-for="book in books"
              :key="book.id"
              type="button"
              class="trial-book-card"
              :class="{ 'is-selected': selected?.id === book.id }"
              :aria-pressed="selected?.id === book.id"
              :aria-label="`选择《${displayBookName(book.title)}》${book.author ? `，${book.author}` : ''}`"
              :disabled="running || loading"
              @click="selectBook(book)"
            >
              <span class="trial-card-cover">
                <img
                  v-if="book.coverUrl && shouldLoadBookCover(book.id) && !failedCovers.has(book.id)"
                  :src="getCoverThumbnailUrl(book.coverUrl)"
                  alt=""
                  loading="lazy"
                  @error="failedCovers.add(book.id)"
                />
                <span v-else class="trial-cover-placeholder">{{ displayBookName(book.title) }}</span>
                <span v-if="selected?.id === book.id" class="trial-card-check" aria-hidden="true">✓</span>
              </span>
              <span class="trial-card-title" :title="displayBookName(book.title)">{{ displayBookName(book.title) }}</span>
              <span class="trial-card-author">{{ book.author || '未知作者' }}</span>
              <span class="trial-card-meta">{{ book.format?.toUpperCase() || '未知类型' }} · {{ book.categoryName || '未分类' }}</span>
            </button>
          </div>
        </div>
      </div>
      <el-pagination
        :current-page="page + 1" :page-size="20" :total="total" layout="prev, pager, next, total"
        :disabled="running || loading" small @current-change="changePage"
      />
    </section>
    <section
      v-show="activeTab === 'process'"
      id="trial-panel-process"
      class="trial-panel"
      role="tabpanel"
      aria-labelledby="trial-tab-process"
      tabindex="0"
    >
      <el-empty v-if="!snapshot && !running && !pollError" :description="book ? '点击开始查询，在这里查看过程和备选' : '选择书籍并开始试刮削后，在这里查看过程'" />
      <template v-else>
        <div class="trial-stage-heading" aria-live="polite">
          <div>
            <span class="trial-result-eyebrow">查询进度</span>
            <h3>
              <span class="trial-status-dot" :class="{ 'is-running': running }" aria-hidden="true"></span>
              {{ statusLabel }}
            </h3>
            <p>{{ displayBookName(selected?.title) || '试刮削任务' }} · 已用 {{ elapsed }} 秒</p>
          </div>
          <el-button v-if="pollError && taskId" @click="resumePolling">重新连接进度</el-button>
          <el-button v-if="snapshot?.metadata" @click="activeTab = 'result'">查看刮削结果</el-button>
        </div>
        <el-progress :percentage="snapshot?.finished ? 100 : 50" :indeterminate="running" :duration="3" :show-text="false" />
        <div class="trial-process-grid" :class="{ 'has-candidates': snapshot?.candidates?.length }">
          <div class="trial-log-column">
            <div class="trial-log-heading">
              <strong>执行记录</strong>
              <span>{{ snapshot?.logs?.length || 0 }} 条日志</span>
            </div>
            <ol class="trial-logs" aria-label="执行记录">
              <li v-for="(entry, index) in snapshot?.logs || []" :key="index">
                <time>{{ new Date(entry.time).toLocaleTimeString() }}</time>
                <span>{{ entry.message }}</span>
              </li>
            </ol>
            <p v-if="pollError" role="alert">{{ pollError }}</p>
          </div>
          <section v-if="snapshot?.candidates?.length" class="trial-candidates">
            <div class="trial-section-title">
              <h4>选择备选元信息</h4>
              <span>{{ snapshot.candidates.length }} 个备选</span>
            </div>
            <p>请核对作者、出版年份和版次。排序不代表匹配已确认。</p>
            <div class="trial-candidate-list">
              <article
                v-for="(candidate, index) in snapshot?.candidates"
                :key="candidate.id"
                class="trial-candidate"
                :class="{ 'is-selected': snapshot?.selectedCandidateId === candidate.id }"
              >
                <span class="trial-candidate-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
                <div class="trial-candidate-copy">
                  <strong>{{ displayBookName(candidate.title) }}</strong>
                  <div class="trial-candidate-meta">
                    <span>{{ candidate.author || '作者待加载详情' }} · {{ candidate.publishDate || '出版年份待核对' }}</span>
                    <a :href="candidate.sourceUrl" target="_blank" rel="noopener noreferrer">查看豆瓣原书页</a>
                  </div>
                </div>
                <el-button
                  size="small"
                  :type="snapshot?.selectedCandidateId === candidate.id ? 'primary' : 'default'"
                  :plain="snapshot?.selectedCandidateId === candidate.id"
                  :disabled="running || confirming || snapshot?.applied"
                  @click="loadCandidate(candidate.id)"
                >
                  加载
                </el-button>
              </article>
            </div>
          </section>
        </div>
        <section v-if="snapshot?.diagnostics?.length" class="trial-diagnostics">
          <h4>{{ snapshot?.metadata ? '查询中遇到的问题（后续查询已取得元信息）' : '各查询步骤的诊断' }}</h4>
          <ol>
            <li v-for="(item, index) in snapshot?.diagnostics" :key="index">
              <strong>{{ item.stage }} · {{ item.label }}</strong>
              <span v-if="item.httpStatus"> · HTTP {{ item.httpStatus }}</span>
              <p>{{ item.detail }}</p>
              <p v-if="item.endpoint" class="trial-diagnostic-endpoint">请求端点：{{ item.endpoint }}</p>
              <p>建议：{{ item.suggestion }}</p>
            </li>
          </ol>
        </section>
        <p v-if="candidateError" role="alert">{{ candidateError }}</p>
      </template>
    </section>
    <section
      v-if="activeTab === 'result'"
      id="trial-panel-result"
      class="trial-panel"
      role="tabpanel"
      aria-labelledby="trial-tab-result"
      tabindex="0"
    >
      <div v-if="snapshot?.metadata" class="trial-result">
        <div class="trial-result-heading">
          <div>
            <span class="trial-result-eyebrow">{{ source?.name }} · 元信息预览</span>
            <h3>{{ displayBookName(formatValue(snapshot.metadata.title)) }}</h3>
            <p>{{ snapshot.applied ? '已保存到这本书的公共元信息' : '仅预览，尚未写入书籍' }}</p>
          </div>
          <el-tag :type="snapshot.applied ? 'success' : 'info'">{{ statusLabel }}</el-tag>
        </div>
        <div v-if="canConfirm" class="trial-overwrite-toolbar">
          <div>
            <strong>选择需要覆盖的字段</strong>
            <p>勾选项会替换已有内容，未勾选项只补全空值；简介等信息由所有版本共享。</p>
          </div>
          <div class="trial-overwrite-actions">
            <el-button text type="primary" :disabled="running || confirming" @click="selectAllOverwriteFields">全选可用字段</el-button>
            <el-button text :disabled="running || confirming || !overwriteFields.length" @click="clearOverwriteFields">清空选择</el-button>
          </div>
        </div>
        <div class="trial-result-layout">
          <aside class="trial-result-cover">
            <div class="trial-cover-label">
              <span>封面预览</span>
              <span v-if="previewCoverOverride !== null">临时切换</span>
            </div>
            <div class="trial-result-image">
              <img
                v-if="resultCover && resultCoverVisible && !resultCoverFailed"
                :src="getCoverThumbnailUrl(resultCover)"
                :alt="`${displayBookName(formatValue(snapshot.metadata.title))}的封面`"
                @error="resultCoverFailed = true"
              />
              <div v-else class="trial-cover-placeholder">
                <strong>{{ displayBookName(formatValue(snapshot.metadata.title)) }}</strong>
                <p>{{ resultCoverMessage }}</p>
                <el-button v-if="resultCoverFailed && resultCoverVisible" size="small" @click="resultCoverFailed = false">重试加载</el-button>
              </div>
            </div>
            <el-button
              :disabled="!resultCover"
              :aria-pressed="resultCoverVisible"
              @click="toggleResultCover"
            >{{ resultCoverVisible ? '关闭封面' : '显示封面' }}</el-button>
            <small>仅本次预览，不修改后续封面显示设置</small>
            <el-checkbox
              v-if="canConfirm || snapshot.applied"
              :model-value="overwriteFields.includes('coverUrl')"
              :disabled="!canConfirm || confirming || running || !hasFieldValue('coverUrl')"
              aria-label="覆盖已有封面"
              @change="setOverwriteField('coverUrl', Boolean($event))"
            >覆盖已有封面</el-checkbox>
          </aside>
          <el-form label-position="top" class="trial-metadata-form">
            <div class="trial-section-title trial-field-wide">
              <h4>书籍信息</h4>
              <span>来自 {{ source?.name }}</span>
            </div>
            <el-form-item
              v-for="(label, field) in summaryFields"
              :key="field"
              :class="{ 'is-overwrite': overwriteFields.includes(field) }"
            >
              <div class="trial-field-header">
                <span class="trial-field-label">
                  <span>{{ label }}</span>
                  <el-checkbox
                    v-if="canConfirm || snapshot.applied"
                    :model-value="overwriteFields.includes(field)"
                    :disabled="!canConfirm || confirming || running || !hasFieldValue(field)"
                    :aria-label="`覆盖已有${label}`"
                    @change="setOverwriteField(field, Boolean($event))"
                  >覆盖已有</el-checkbox>
                </span>
              </div>
              <el-input :model-value="formatValue(snapshot.metadata[field])" :aria-label="label" readonly />
            </el-form-item>
            <el-form-item class="trial-field-wide" :class="{ 'is-overwrite': overwriteFields.includes('tags') }">
              <div class="trial-field-header">
                <span class="trial-field-label">
                  <span>标签</span>
                  <el-checkbox
                    v-if="canConfirm || snapshot.applied"
                    :model-value="overwriteFields.includes('tags')"
                    :disabled="!canConfirm || confirming || running || !hasFieldValue('tags')"
                    aria-label="覆盖已有标签"
                    @change="setOverwriteField('tags', Boolean($event))"
                  >覆盖已有</el-checkbox>
                </span>
              </div>
              <div class="trial-result-tags">
                <el-tag v-for="tag in resultTags" :key="tag" effect="plain">{{ tag }}</el-tag>
                <span v-if="!resultTags.length" class="trial-muted">来源未提供标签</span>
              </div>
            </el-form-item>
            <el-form-item class="trial-field-wide" :class="{ 'is-overwrite': overwriteFields.includes('description') }">
              <div class="trial-field-header">
                <span class="trial-field-label">
                  <span>内容简介</span>
                  <el-checkbox
                    v-if="canConfirm || snapshot.applied"
                    :model-value="overwriteFields.includes('description')"
                    :disabled="!canConfirm || confirming || running || !hasFieldValue('description')"
                    aria-label="覆盖已有内容简介"
                    @change="setOverwriteField('description', Boolean($event))"
                  >覆盖已有</el-checkbox>
                </span>
              </div>
              <div v-if="manual && book?.versionId && hasFieldValue('description')" class="trial-description-target">
                <div class="trial-description-target-header">
                  <strong>简介保存位置</strong>
                  <span>默认保存到系列简介</span>
                </div>
                <div
                  class="trial-description-target-switch"
                  :class="`is-${descriptionTarget.toLowerCase()}`"
                  role="radiogroup"
                  aria-label="简介保存位置"
                  @keydown="switchDescriptionTargetByKeyboard"
                >
                  <span class="trial-description-target-indicator" aria-hidden="true"></span>
                  <button
                    v-for="option in descriptionTargetOptions"
                    :key="option.value"
                    type="button"
                    role="radio"
                    :aria-checked="descriptionTarget === option.value"
                    :tabindex="descriptionTarget === option.value ? 0 : -1"
                    :disabled="!canConfirm || confirming || running"
                    @click="descriptionTarget = option.value"
                  >{{ option.label }}</button>
                </div>
                <small>{{ descriptionTargetHint }}</small>
              </div>
              <el-input
                :model-value="formatValue(snapshot.metadata.description)"
                aria-label="内容简介"
                type="textarea"
                :autosize="{ minRows: 5, maxRows: 14 }"
                resize="none"
                readonly
              />
            </el-form-item>
          </el-form>
        </div>
        <div v-if="canConfirm" class="trial-result-confirm">
          <div>
            <strong>核对无误后，保存这份元信息</strong>
            <p>{{ overwriteFields.length ? `允许覆盖：${overwriteFieldLabels}；其余字段仅补全空值。` : '未选择覆盖字段，仅补全空值，保留已有内容。' }}</p>
          </div>
          <div class="trial-confirm-buttons">
            <el-button type="primary" :loading="confirming" :disabled="running" @click="confirmCandidate">
              {{ overwriteFields.length ? '保存并覆盖所选字段' : '确认保存（仅补全空值）' }}
            </el-button>
            <el-button v-if="snapshot.candidates?.length" :disabled="confirming" @click="activeTab = 'process'">返回备选选择</el-button>
          </div>
        </div>
        <el-alert
          v-if="snapshot.applied"
          type="success"
          :closable="false"
          :title="`已保存书籍 · 更新字段：${updatedFieldLabels}`"
          show-icon
        />
      </div>
      <div v-else-if="snapshot?.finished && snapshot.failure" class="trial-failure" role="status">
        <strong>{{ snapshot.failure.label }}</strong>
        <p>{{ snapshot.failure.detail }}</p>
        <p>建议：{{ snapshot.failure.suggestion }}</p>
        <el-button @click="activeTab = 'process'">查看详细过程</el-button>
      </div>
      <el-empty
        v-else
        :description="running ? '正在查询元信息，请在过程页查看进度' : snapshot?.candidates?.length ? '请先选择备选书籍并加载详情' : '暂无刮削结果'"
      >
        <el-button v-if="snapshot || running || pollError" @click="activeTab = 'process'">{{ snapshot?.candidates?.length ? '前往备选选择' : '查看刮削过程' }}</el-button>
      </el-empty>
      <p v-if="candidateError" role="alert">{{ candidateError }}</p>
    </section>
    <template #footer>
      <div class="trial-workbench-footer">
        <span v-if="!book && activeTab === 'books'">{{ selected ? `已选择：${displayBookName(selected.title)}` : '请选择一本书籍开始查询' }}</span>
        <span v-else>{{ running ? '关闭后服务器仍会完成查询，重新打开可查看' : snapshot?.applied ? '已保存到本书公共元信息' : '仅在确认保存后补全书籍' }}</span>
        <div class="trial-footer-actions">
          <el-button @click="emit('update:modelValue', false)">{{ manual ? '返回模式选择' : '关闭工作台' }}</el-button>
          <el-button
            v-if="!book && activeTab === 'books'"
            type="primary"
            :disabled="!selected || loading || running || confirming"
            :loading="running"
            @click="start"
          >{{ running ? '试刮削中' : '开始试刮削' }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import api from '@/utils/api'
import type { ScraperStatus } from '@/utils/config'
import { usePreferencesStore, type ScraperTrialViewMode } from '@/stores/preferences'
import { getCoverThumbnailUrl } from '@/utils/cover'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'

const { displayBookName } = useBookNameDisplay()

const props = defineProps<{ modelValue: boolean; source: ScraperStatus | null; book?: BookChoice | null; manual?: boolean }>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'running', value: boolean): void
  (event: 'saved'): void
}>()
interface BookChoice {
  id: number
  title: string
  author?: string
  versionId?: number
  displayName?: string
  format?: string
  categoryName?: string
  coverUrl?: string
}
interface Option {
  id: number
  name: string
  path?: string
}
interface ScrapeFailure {
  code: string
  label: string
  detail: string
  suggestion: string
  stage: string
  httpStatus: number | null
  endpoint: string
}
interface MetadataCandidate {
  id: string
  title: string
  author: string
  publishDate: string
  coverUrl: string
  sourceUrl: string
}
interface TrialSnapshot {
  status: string
  finished: boolean
  elapsedMs: number
  logs: { time: string; message: string }[]
  metadata: Record<string, unknown> | null
  failure?: ScrapeFailure | null
  diagnostics?: ScrapeFailure[]
  candidates?: MetadataCandidate[]
  selectedCandidateId?: string | null
  applied?: boolean
  updatedFields?: string[]
}
const preferences = usePreferencesStore()
const viewMode = computed(() => preferences.scraperTrialViewMode)
const viewOptions: { value: ScraperTrialViewMode; label: string }[] = [
  { value: 'table', label: '列表视图' },
  { value: 'card', label: '卡片视图' },
]
type TrialTab = 'books' | 'process' | 'result'
const activeTab = ref<TrialTab>(props.book ? 'process' : 'books')
const tabList = ref<HTMLElement | null>(null)
watch(activeTab, async () => {
  await nextTick()
  tabList.value?.querySelector<HTMLElement>('[aria-selected="true"]')?.scrollIntoView({
    block: 'nearest', inline: 'nearest', behavior: 'auto',
  })
})
const apiPrefix = computed(() => props.manual ? '/api/scraper/manual' : '/api/scraper/trial')
const tabs = computed(() => [
  ...(!props.book ? [{ value: 'books' as const, label: '书籍选择' }] : []),
  { value: 'process' as const, label: snapshot.value?.candidates?.length ? '刮削过程 & 备选选择' : '刮削过程' },
  { value: 'result' as const, label: '刮削结果' },
])
const tabIndicatorStyle = computed(() => ({
  width: `calc((100% - 8px) / ${tabs.value.length})`,
  transform: `translateX(${tabs.value.findIndex(tab => tab.value === activeTab.value) * 100}%)`,
}))
async function switchTabByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  const index = tabs.value.findIndex(tab => tab.value === activeTab.value)
  const target = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.value.length - 1
    : (index + (event.key === 'ArrowRight' ? 1 : tabs.value.length - 1)) % tabs.value.length
  activeTab.value = tabs.value[target].value
  await nextTick()
  group.querySelector<HTMLButtonElement>('[aria-selected="true"]')?.focus()
}
const failedCovers = reactive(new Set<number>())
function setViewMode(value: ScraperTrialViewMode) {
  preferences.setScraperTrialViewMode(value)
}
async function switchViewByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  const value = event.key === 'Home' ? 'table'
    : event.key === 'End' ? 'card'
      : viewMode.value === 'table' ? 'card' : 'table'
  setViewMode(value)
  await nextTick()
  group.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus()
}
const formats = ['epub', 'pdf', 'txt', 'mobi', 'azw3', 'fb2', 'cbz', 'cbr']
const fields = {
  title: '书名', author: '作者', isbn: 'ISBN', publisher: '出版社', publishDate: '出版日期',
  description: '简介', coverUrl: '封面地址', language: '语言', rating: '评分', tags: '标签',
}
const summaryFields = {
  title: '书名', author: '作者', isbn: 'ISBN', publisher: '出版社',
  publishDate: '出版日期', language: '语言', rating: '来源评分',
}
const resultCover = computed(() => typeof snapshot.value?.metadata?.coverUrl === 'string'
  ? snapshot.value.metadata.coverUrl : '')
const coverAllowed = computed(() => selected.value ? shouldLoadBookCover(selected.value.id) : false)
const resultCoverFailed = ref(false)
const previewCoverOverride = ref<boolean | null>(null)
const resultCoverVisible = computed(() => Boolean(resultCover.value)
  && (previewCoverOverride.value ?? coverAllowed.value))
const resultCoverMessage = computed(() => {
  if (!resultCover.value) return '来源未提供封面'
  if (!resultCoverVisible.value) {
    return previewCoverOverride.value === false ? '封面已临时关闭' : '封面已按隐私设置隐藏'
  }
  return resultCoverFailed.value ? '封面加载失败' : ''
})
function toggleResultCover() {
  if (!resultCover.value) return
  previewCoverOverride.value = !resultCoverVisible.value
  if (previewCoverOverride.value) resultCoverFailed.value = false
}
const resultTags = computed<string[]>(() => {
  const value = snapshot.value?.metadata?.tags
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && Boolean(item.trim()))
    : typeof value === 'string' && value.trim() ? [value] : []
})
const updatedFieldLabels = computed(() => snapshot.value?.updatedFields?.map(field =>
  fields[field as keyof typeof fields] || field).join('、') || '无（值未变化或已有内容被保留）')
const filters = reactive({
  keyword: '',
  sourceType: '',
  format: '',
  categoryId: undefined as number | undefined,
  tagId: undefined as number | undefined,
})
const dates = ref<string[] | null>(null)
const categories = ref<Option[]>([])
const tags = ref<Option[]>([])
const books = ref<BookChoice[]>([])
const selected = ref<BookChoice | null>(null)
const loading = ref(false)
const loadError = ref('')
const page = ref(0)
const total = ref(0)
const running = ref(false)
const confirming = ref(false)
watch(() => running.value || confirming.value, value => emit('running', value))
const snapshot = ref<TrialSnapshot | null>(null)
const pollError = ref('')
const taskId = ref('')
const candidateError = ref('')
const overwriteFields = ref<string[]>([])
type DescriptionTarget = 'SERIES' | 'VERSION' | 'BOTH'
const descriptionTarget = ref<DescriptionTarget>('SERIES')
const descriptionTargetOptions: { value: DescriptionTarget; label: string }[] = [
  { value: 'SERIES', label: '系列简介' },
  { value: 'VERSION', label: '当前版本简介' },
  { value: 'BOTH', label: '同时保存' },
]
const descriptionTargetHint = computed(() => {
  if (descriptionTarget.value === 'VERSION') {
    return `只保存到当前版本：${props.book?.displayName || '已选版本'}。`
  }
  if (descriptionTarget.value === 'BOTH') {
    return `分别保存到系列简介和当前版本：${props.book?.displayName || '已选版本'}。`
  }
  return '对这本书的所有版本生效。'
})
const overwriteFieldLabels = computed(() => overwriteFields.value
  .map(field => fields[field as keyof typeof fields]).join('、'))
function hasFieldValue(field: string) {
  const value = snapshot.value?.metadata?.[field]
  if (Array.isArray(value)) return value.some(item => typeof item === 'string' && item.trim())
  if (typeof value === 'number') return Number.isFinite(value)
  return typeof value === 'string' && Boolean(value.trim())
}
function setOverwriteField(field: string, checked: boolean) {
  if (!canConfirm.value || confirming.value || running.value
      || !Object.prototype.hasOwnProperty.call(fields, field) || !hasFieldValue(field)) return
  overwriteFields.value = checked
    ? [...new Set([...overwriteFields.value, field])]
    : overwriteFields.value.filter(value => value !== field)
}
function selectAllOverwriteFields() {
  if (!canConfirm.value || confirming.value || running.value) return
  overwriteFields.value = Object.keys(fields).filter(hasFieldValue)
}
function clearOverwriteFields() {
  if (confirming.value || running.value) return
  overwriteFields.value = []
}
const canConfirm = computed(() => snapshot.value?.status === 'REVIEW_REQUIRED'
  || props.manual && snapshot.value?.status === 'MATCHED' && Boolean(snapshot.value.metadata))
watch([taskId, () => snapshot.value?.selectedCandidateId, () => selected.value?.id, () => props.source?.configKey], () => {
  overwriteFields.value = []
})
watch([taskId, () => props.book?.id, () => props.book?.versionId, () => props.source?.configKey], () => {
  descriptionTarget.value = 'SERIES'
})
async function switchDescriptionTargetByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)
      || !canConfirm.value || confirming.value || running.value) return
  event.preventDefault()
  const currentIndex = descriptionTargetOptions.findIndex(
    option => option.value === descriptionTarget.value,
  )
  const index = event.key === 'Home' ? 0
    : event.key === 'End' ? descriptionTargetOptions.length - 1
      : event.key === 'ArrowRight'
        ? (currentIndex + 1) % descriptionTargetOptions.length
        : (currentIndex - 1 + descriptionTargetOptions.length) % descriptionTargetOptions.length
  const group = event.currentTarget as HTMLElement
  descriptionTarget.value = descriptionTargetOptions[index].value
  await nextTick()
  group.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus()
}
let savedTask = ''
watch([resultCover, () => snapshot.value?.selectedCandidateId, () => selected.value?.id, taskId], () => {
  resultCoverFailed.value = false
  previewCoverOverride.value = null
})
let timer: ReturnType<typeof setTimeout> | undefined
let generation = 0
let searchGeneration = 0
let disposed = false
const elapsed = computed(() => ((snapshot.value?.elapsedMs || 0) / 1000).toFixed(1))
const statusLabels: Record<string, string> = {
  RUNNING: '执行中',
  MATCHED: '取得元信息',
  NO_RESULT: '未取得元信息',
  UNSUPPORTED: '不支持 / 配置缺失',
  FAILED: '失败',
  AWAITING_SELECTION: '待选择候选',
  REVIEW_REQUIRED: '待人工确认',
  APPLIED: '已确认保存',
}
const statusLabel = computed(() => pollError.value
  ? '连接异常'
  : statusLabels[snapshot.value?.status || 'RUNNING'])
const formatValue = (value: unknown) => Array.isArray(value) ? value.join('、') : String(value ?? '—')

function resetCandidateReview() {
  overwriteFields.value = []
  if (!snapshot.value) return
  clearTimeout(timer)
  generation++
  snapshot.value = null
  taskId.value = ''
  candidateError.value = ''
}
function selectBook(book: BookChoice | null) {
  if (!book || running.value || loading.value || confirming.value) return
  if (selected.value?.id !== book.id) resetCandidateReview()
  selected.value = book
}
async function loadBooks() {
  if (running.value || confirming.value) return
  resetCandidateReview()
  const request = ++searchGeneration
  loading.value = true
  loadError.value = ''
  selected.value = null
  try {
    const { data } = await api.get('/api/books', { params: {
      ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value != null)),
      page: page.value, size: 20, includeChildren: true,
      addedFrom: dates.value?.[0], addedTo: dates.value?.[1],
    } })
    if (request !== searchGeneration || disposed) return
    failedCovers.clear()
    books.value = data.content
    total.value = data.totalElements
  } catch {
    if (request !== searchGeneration || disposed) return
    books.value = []
    total.value = 0
    loadError.value = '书籍加载失败，请重新搜索。'
  } finally {
    if (request === searchGeneration) loading.value = false
  }
}
function search() {
  page.value = 0
  void loadBooks()
}
function changePage(value: number) {
  page.value = value - 1
  void loadBooks()
}
async function poll(request: number) {
  try {
    const { data } = await api.get<TrialSnapshot>(`${apiPrefix.value}/${taskId.value}`)
    if (request !== generation || disposed) return
    const previous = snapshot.value
    snapshot.value = data
    if (data.finished && (!previous?.finished || previous.status !== data.status)) {
      activeTab.value = data.candidates?.length && !data.metadata ? 'process' : 'result'
    }
    pollError.value = ''
    if (data.applied && savedTask !== taskId.value) {
      savedTask = taskId.value
      emit('saved')
    }
    running.value = !data.finished
    if (!data.finished) timer = setTimeout(() => void poll(request), 1000)
  } catch (error: any) {
    if (request !== generation || disposed) return
    if (error.response?.status === 404) {
      running.value = false
      taskId.value = ''
      pollError.value = '试刮削不存在或已过期，请重新开始。'
      return
    }
    pollError.value = '进度连接中断，任务可能仍在运行。点击重新连接进度；重启服务器或超过 30 分钟后任务会过期。'
  }
}
function resumePolling() {
  clearTimeout(timer)
  void poll(++generation)
}
async function loadCandidate(candidateId: string) {
  if (running.value || confirming.value || !taskId.value) return
  running.value = true
  activeTab.value = 'process'
  overwriteFields.value = []
  candidateError.value = ''
  try {
    const { data } = await api.post<TrialSnapshot>(`${apiPrefix.value}/${taskId.value}/candidates/${candidateId}`)
    if (disposed) return
    snapshot.value = data
    if (data.finished && data.metadata) activeTab.value = 'result'
    running.value = !data.finished
    resumePolling()
  } catch (error: any) {
    running.value = false
    candidateError.value = error.response?.data?.message || '候选详情加载失败，请重试。'
  }
}
async function confirmCandidate() {
  if (confirming.value || running.value || !canConfirm.value
      || !props.manual && !snapshot.value?.selectedCandidateId) return
  confirming.value = true
  candidateError.value = ''
  const request = generation
  try {
    const url = props.manual ? `${apiPrefix.value}/${taskId.value}/confirm`
      : `${apiPrefix.value}/${taskId.value}/candidates/${snapshot.value?.selectedCandidateId}/confirm`
    await api.post(url, {
      overwriteFields: [...overwriteFields.value],
      descriptionTarget: descriptionTarget.value,
      versionId: descriptionTarget.value === 'SERIES' ? undefined : props.book?.versionId,
    })
    if (disposed || request !== generation) return
    await poll(request)
  } catch (error: any) {
    if (!disposed && request === generation) {
      candidateError.value = error.response?.data?.message || '确认补全请求未完成，正在核对服务器状态。'
      await poll(request)
    }
  } finally {
    confirming.value = false
  }
}
async function start() {
  if (!selected.value || !props.source || running.value || confirming.value) return
  running.value = true
  activeTab.value = 'process'
  snapshot.value = null
  overwriteFields.value = []
  pollError.value = ''
  candidateError.value = ''
  try {
    const { data } = await api.post(`${apiPrefix.value}/${props.source.configKey}/books/${selected.value.id}`)
    taskId.value = data.taskId
    if (!disposed) resumePolling()
  } catch {
    running.value = false
    pollError.value = '试刮削启动失败，请检查权限或稍后重试。'
  }
}
watch(() => props.modelValue, async (open) => {
  previewCoverOverride.value = null
  if (!open) {
    overwriteFields.value = []
    return
  }
  if (props.book) {
    selected.value = props.book
    if (taskId.value && running.value) resumePolling()
    return
  }
  void preferences.hydrate()
  if (taskId.value && running.value) resumePolling()
  if (!running.value && !confirming.value && !snapshot.value) search()
  const results = await Promise.allSettled([api.get('/api/categories'), api.get('/api/tags')])
  if (disposed) return
  if (results[0].status === 'fulfilled') categories.value = results[0].value.data
  if (results[1].status === 'fulfilled') tags.value = results[1].value.data
  if (results.some(result => result.status === 'rejected')) loadError.value = '分类或标签加载失败，请关闭弹窗后重试。'
})
watch(() => props.source?.configKey, () => {
  if (running.value || confirming.value) return
  snapshot.value = null
  taskId.value = ''
  pollError.value = ''
  activeTab.value = props.book ? 'process' : 'books'
  selected.value = props.book || null
})
watch(() => props.book?.id, () => {
  if (running.value || confirming.value) return
  resetCandidateReview()
  selected.value = props.book || null
  activeTab.value = props.book ? 'process' : 'books'
})
onBeforeUnmount(() => {
  disposed = true
  generation++
  searchGeneration++
  clearTimeout(timer)
})
</script>

<style scoped>
:global(.scrape-workbench-dialog) {
  --el-color-primary: var(--primary);
  --el-color-primary-light-3: color-mix(in srgb, var(--primary) 70%, white);
  --el-color-primary-light-5: color-mix(in srgb, var(--primary) 50%, white);
  --el-color-primary-light-7: color-mix(in srgb, var(--primary) 30%, white);
  --el-color-primary-light-8: color-mix(in srgb, var(--primary) 20%, white);
  --el-color-primary-light-9: color-mix(in srgb, var(--primary) 10%, white);
  --el-color-primary-dark-2: var(--primary-dark);
  display: flex;
  flex-direction: column;
  height: auto;
  max-height: 90dvh;
  padding: 0;
  margin-bottom: 0;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 24px;
  background: var(--surface-elevated);
}
:global(.scrape-workbench-dialog .el-dialog__header) {
  padding: 24px 28px 18px;
  margin-right: 0;
}
:global(.scrape-workbench-dialog .el-dialog__body) {
  display: flex;
  flex: 0 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
  padding: 0 28px;
}
:global(.scrape-workbench-dialog .el-dialog__footer) {
  padding: 16px 28px;
  border-top: 1px solid var(--border-color);
  background: var(--surface-elevated);
}
.trial-workbench-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-right: 28px;
}
.trial-brand-mark {
  display: grid;
  flex: 0 0 44px;
  height: 44px;
  border: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);
  border-radius: 14px;
  background: color-mix(in srgb, var(--primary) 8%, transparent);
  color: var(--primary);
  place-items: center;
}
.trial-brand-mark svg {
  width: 25px;
  height: 25px;
}
.trial-workbench-title {
  flex: 1;
}
.trial-workbench-title > span {
  color: var(--text-secondary);
  font-size: 11px;
  letter-spacing: 2px;
}
.trial-workbench-title h2 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 5px 0 0;
  color: var(--text-primary);
  font-size: 22px;
  line-height: 1.4;
}
.trial-source-pill {
  padding: 3px 9px;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  color: var(--primary);
  font-size: 11px;
  font-weight: 500;
}
.trial-header-note {
  color: var(--text-tertiary);
  font-size: 12px;
}
.trial-intro {
  margin: 0 0 6px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
.trial-target-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: linear-gradient(110deg, color-mix(in srgb, var(--primary) 6%, var(--surface-card)), var(--surface-card));
}
.trial-target-copy {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 16px;
  min-width: 0;
}
.trial-target-copy > strong {
  color: var(--text-primary);
  font-size: 16px;
  overflow-wrap: anywhere;
}
.trial-target-copy > span:last-child {
  color: var(--text-secondary);
  font-size: 12px;
}
.trial-tab-scroll {
  flex-shrink: 0;
  overflow-x: auto;
  margin: 18px 0;
}
.trial-tabs {
  position: relative;
  isolation: isolate;
  display: grid;
  min-width: 480px;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: color-mix(in srgb, var(--primary) 5%, var(--surface-hover));
}
.trial-tab-indicator {
  position: absolute;
  z-index: -1;
  top: 4px;
  bottom: 4px;
  left: 4px;
  border: 1px solid color-mix(in srgb, var(--primary) 20%, var(--border-color));
  border-radius: 10px;
  background: var(--surface-elevated);
  box-sizing: border-box;
  transition: transform 220ms ease;
}
.trial-tabs button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 43px;
  padding: 8px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.trial-tab-number {
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  opacity: .6;
}
.trial-tabs button[aria-selected="true"] {
  color: var(--primary);
  font-weight: 600;
}
.trial-tabs button:focus-visible,
.trial-panel:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.trial-panel {
  flex: 0 0 auto;
  min-height: auto;
  overflow: visible;
  padding: 2px 4px 24px;
  overscroll-behavior: contain;
}
.trial-stage-heading,
.trial-result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin: 4px 0 22px;
}
.trial-stage-heading h3,
.trial-result-heading h3 {
  margin: 8px 0;
  color: var(--text-primary);
  font-size: 26px;
  font-weight: 650;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.trial-stage-heading p,
.trial-result-heading p,
.trial-muted {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
}
.trial-result-eyebrow {
  color: var(--primary);
  font-size: 11px;
  letter-spacing: 1px;
}
.trial-status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin: 0 10px 4px 0;
  border-radius: 50%;
  background: var(--primary);
}
.trial-status-dot.is-running {
  background: var(--warning);
}
.trial-overwrite-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 18px;
  margin-bottom: 20px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: color-mix(in srgb, var(--primary) 4%, var(--surface-card));
}
.trial-overwrite-toolbar strong {
  color: var(--text-primary);
  font-size: 13px;
}
.trial-overwrite-toolbar p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.7;
}
.trial-overwrite-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.trial-overwrite-actions .el-button + .el-button {
  margin-left: 0;
}
.trial-metadata-form :deep(.el-form-item__content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 7px;
  line-height: 1.5;
}
.trial-field-header {
  width: 100%;
}
.trial-field-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
  color: var(--text-secondary);
  font-size: 11px;
}
.trial-field-label :deep(.el-checkbox) {
  height: 20px;
}
.trial-field-label :deep(.el-checkbox__label) {
  padding-left: 5px;
  font-size: 11px;
}
.trial-metadata-form .is-overwrite :deep(.el-input__wrapper),
.trial-metadata-form .is-overwrite :deep(.el-textarea__inner) {
  border-color: color-mix(in srgb, var(--primary) 30%, transparent);
}
.trial-result-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  align-items: start;
  gap: 28px;
}
.trial-result-cover {
  position: sticky;
  top: 0;
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: linear-gradient(160deg, color-mix(in srgb, var(--primary) 6%, var(--surface-card)), var(--surface-card));
  color: var(--text-secondary);
  text-align: center;
}
.trial-cover-label {
  display: flex;
  justify-content: space-between;
  color: var(--text-secondary);
  font-size: 11px;
}
.trial-result-cover > small {
  font-size: 10px;
  line-height: 1.7;
}
.trial-result-image {
  display: grid;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 4px 10px 10px 4px;
  background: color-mix(in srgb, var(--primary) 7%, var(--surface-card));
  place-items: center;
}
.trial-result-image img {
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: contain;
}
.trial-metadata-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--surface-card);
}
.trial-metadata-form :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 16px;
}
.trial-metadata-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 7px;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.5;
}
.trial-metadata-form :deep(.el-input__wrapper),
.trial-metadata-form :deep(.el-textarea__inner) {
  border: 1px solid transparent;
  border-radius: 9px;
  background: color-mix(in srgb, var(--primary) 4%, var(--surface-hover));
  box-shadow: none;
  color: var(--text-primary);
}
.trial-metadata-form :deep(.el-input__wrapper:focus-within),
.trial-metadata-form :deep(.el-textarea__inner:focus) {
  border-color: var(--primary);
}
.trial-metadata-form :deep(.el-input__inner) {
  height: 36px;
  color: var(--text-primary);
  font-size: 13px;
}
.trial-metadata-form :deep(.el-textarea__inner) {
  padding: 12px;
  line-height: 1.9;
}
.trial-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.trial-section-title h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 15px;
}
.trial-section-title > span {
  color: var(--text-tertiary);
  font-size: 11px;
}
.trial-field-wide {
  grid-column: 1 / -1;
}
.trial-result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.trial-result-tags :deep(.el-tag) {
  border-color: color-mix(in srgb, var(--primary) 16%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--primary) 5%, transparent);
  color: var(--primary);
}
.trial-description-target {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  margin: 0 0 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-card);
}
.trial-description-target-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 12px;
}
.trial-description-target-header strong {
  color: var(--text-primary);
  font-size: 12px;
}
.trial-description-target-header > span,
.trial-description-target > small {
  color: var(--text-secondary);
  font-size: 11px;
}
.trial-description-target-switch {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  max-width: 420px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: var(--surface-hover);
}
.trial-description-target-indicator {
  position: absolute;
  z-index: -1;
  top: 3px;
  bottom: 3px;
  left: 3px;
  width: calc((100% - 6px) / 3);
  border: 1px solid color-mix(in srgb, var(--primary) 18%, var(--border-color));
  border-radius: 8px;
  background: var(--surface-card);
  transition: transform 200ms ease;
}
.trial-description-target-switch.is-version .trial-description-target-indicator {
  transform: translateX(100%);
}
.trial-description-target-switch.is-both .trial-description-target-indicator {
  transform: translateX(200%);
}
.trial-description-target-switch button {
  min-height: 34px;
  padding: 6px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.trial-description-target-switch button[aria-checked="true"] {
  color: var(--primary);
  font-weight: 600;
}
.trial-description-target-switch button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.trial-description-target-switch button:disabled {
  cursor: not-allowed;
  opacity: .6;
}
.trial-result-confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  margin: 24px 0 0;
  padding: 18px 20px;
  border: 1px solid color-mix(in srgb, var(--primary) 20%, var(--border-color));
  border-radius: 14px;
  background: color-mix(in srgb, var(--primary) 5%, var(--surface-card));
}
.trial-result-confirm strong {
  color: var(--text-primary);
  font-size: 13px;
}
.trial-result-confirm p {
  margin: 7px 0 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.7;
}
.trial-confirm-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.trial-result-confirm .el-button {
  max-width: 100%;
  height: auto;
  min-height: 36px;
  margin: 0;
  white-space: normal;
}
.trial-workbench-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.trial-footer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}
.trial-footer-actions .el-button + .el-button {
  margin-left: 0;
}
.trial-workbench-footer > span {
  color: var(--text-secondary);
  font-size: 11px;
}
.trial-browser-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin: 16px 0 12px;
}
.trial-view-switch {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(2, 112px);
  max-width: 100%;
  overflow-x: auto;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-hover);
}
.trial-view-indicator {
  position: absolute;
  z-index: -1;
  top: 4px;
  bottom: 4px;
  left: 4px;
  width: calc((100% - 8px) / 2);
  border-radius: 10px;
  background: var(--surface-card);
  transition: transform 220ms ease;
}
.trial-view-switch.is-card .trial-view-indicator {
  transform: translateX(100%);
}
.trial-view-switch button {
  min-height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.trial-view-switch button[aria-checked="true"] {
  color: var(--primary);
  font-weight: 600;
}
.trial-view-switch button:focus-visible,
.trial-book-card:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.trial-book-browser {
  height: clamp(360px, 55vh, 640px);
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.trial-card-scroll {
  height: 100%;
  overflow: auto;
  overscroll-behavior: contain;
}
.trial-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  align-items: start;
  gap: 14px;
  padding: 14px;
}
.trial-book-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 6px;
  padding: 10px;
  border: 2px solid transparent;
  border-radius: 12px;
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.trial-book-card:hover {
  background: var(--surface-hover);
}
.trial-book-card.is-selected {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 8%, var(--surface-card));
}
.trial-book-card:disabled {
  cursor: default;
}
.trial-card-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border-radius: 6px;
  background: var(--surface-hover);
}
.trial-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.trial-cover-placeholder {
  padding: 12px;
  overflow-wrap: anywhere;
  text-align: center;
  color: var(--text-secondary);
}
.trial-card-check {
  position: absolute;
  top: 6px;
  right: 6px;
  display: grid;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--primary);
  color: white;
  place-items: center;
}
.trial-card-title {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.trial-card-author,
.trial-card-meta {
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--text-secondary);
  font-size: 12px;
}

.trial-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
  margin: 0 0 18px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-card);
}
.trial-filters :deep(.el-date-editor) {
  width: 100%;
}
.trial-process-grid {
  min-width: 0;
}
.trial-log-column,
.trial-candidates {
  min-width: 0;
}
.trial-log-column {
  display: flex;
  flex-direction: column;
}
@media (min-width: 960px) {
  .trial-process-grid.has-candidates {
    display: grid;
    grid-template-columns: minmax(0, .95fr) minmax(0, 1.25fr);
    align-items: stretch;
    gap: 24px;
  }
  .trial-process-grid.has-candidates .trial-candidates {
    padding-top: 24px;
  }
}
.trial-log-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24px 0 12px;
}
.trial-log-heading strong {
  color: var(--text-primary);
  font-size: 13px;
}
.trial-log-heading > span {
  color: var(--text-tertiary);
  font-size: 11px;
}
.trial-logs {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 18px;
  margin: 0 0 24px;
  border: 1px solid #29483c;
  border-radius: 14px;
  background: #142b22;
  color: #e0ece6;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  line-height: 1.9;
  list-style: none;
}
.trial-logs li {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 12px;
  padding: 8px 0;
  overflow-wrap: anywhere;
}
.trial-logs li + li {
  border-top: 1px solid #284236;
}
.trial-logs time {
  color: #9abca9;
  font-variant-numeric: tabular-nums;
}
.trial-candidate-list {
  display: grid;
  gap: 10px;
  margin: 14px 0;
}
.trial-candidate {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-card);
  transition: border-color 180ms ease;
}
.trial-candidate-index {
  color: var(--text-tertiary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.trial-candidate-copy {
  min-width: 0;
  overflow-wrap: anywhere;
}
.trial-candidate-copy > strong {
  display: block;
  color: var(--text-primary);
  font-size: 15px;
}
.trial-candidate-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}
.trial-candidates > p {
  margin: 0 0 16px;
  color: var(--text-secondary);
  font-size: 12px;
}
.trial-candidate.is-selected {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 5%, var(--surface-card));
}
.trial-candidate p {
  margin: 0;
  color: var(--text-secondary);
}
.trial-candidate a {
  color: var(--primary);
  font-size: 11px;
  text-decoration: none;
}
.trial-candidate a:hover {
  text-decoration: underline;
}
.trial-failure {
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-hover);
}
.trial-failure p,
.trial-diagnostics p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
}
.trial-diagnostics ol {
  display: grid;
  gap: 14px;
  padding-left: 24px;
}
.trial-diagnostic-endpoint {
  color: var(--text-secondary);
  font-size: 12px;
}
@media (max-height: 800px) {
  .trial-result-cover {
    position: static;
  }
}
@media (max-width: 640px) {
  .trial-result-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
  :global(.scrape-workbench-dialog) {
    max-height: 92dvh;
    margin-top: 4dvh !important;
    border-radius: 18px;
  }
  :global(.scrape-workbench-dialog .el-dialog__header) {
    padding: 18px 18px 14px;
  }
  :global(.scrape-workbench-dialog .el-dialog__body) {
    padding: 0 16px;
  }
  :global(.scrape-workbench-dialog .el-dialog__footer) {
    padding: 12px 18px;
  }
  .trial-header-note,
  .trial-brand-mark {
    display: none;
  }
  .trial-target-banner {
    padding: 12px;
  }
  .trial-target-copy {
    display: grid;
    gap: 4px;
  }
  .trial-workbench-title h2 {
    font-size: 19px;
  }
  .trial-tabs {
    min-width: 430px;
  }
  .trial-tab-number {
    display: none;
  }
  .trial-result-cover {
    position: static;
    width: min(220px, 100%);
    box-sizing: border-box;
    justify-self: center;
  }
  .trial-metadata-form {
    padding: 16px;
  }
  .trial-result-heading h3 {
    font-size: 22px;
  }
  .trial-candidate {
    grid-template-columns: 24px minmax(0, 1fr) auto;
    gap: 8px;
    padding: 14px;
  }
  .trial-candidate > .el-button {
    grid-column: 3;
    grid-row: 1;
    justify-self: end;
  }
  .trial-logs li {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;
  }
  .trial-metadata-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .trial-panel {
    min-height: 0;
  }
  .trial-filters {
    grid-template-columns: minmax(0, 1fr);
  }
  .trial-book-browser {
    height: clamp(300px, 52vh, 480px);
  }
  .trial-card-grid {
    grid-template-columns: repeat(auto-fill, minmax(115px, 1fr));
    gap: 8px;
    padding: 8px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .trial-tab-indicator,
  .trial-candidate,
  .trial-view-indicator,
  .trial-description-target-indicator {
    transition: none;
  }
  :deep(.el-progress-bar__inner) {
    animation: none !important;
    transition: none !important;
  }
}
</style>
