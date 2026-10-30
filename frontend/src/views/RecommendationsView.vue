<template>
  <div class="recommendations-view">
    <header class="page-heading">
      <div>
        <p class="eyebrow">YOUR DAILY READING</p>
        <h1>推荐阅读</h1>
        <p class="muted">{{ result?.date || recommendationDate() }} · 每日一份，为阅读留一点偶然</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" :disabled="busy || loading" @click="load">刷新结果</el-button>
        <el-button :disabled="busy || loading" @click="managerOpen = true">管理规则</el-button>
        <el-button type="primary" :disabled="busy || loading" @click="edit()">新增推荐组</el-button>
      </div>
    </header>
    <p class="muted">推荐按北京时间每日生成，同一天跨设备保持一致。刷新结果不会换书；各组独立抽取，允许跨组重复。</p>
    <div v-if="loadError" class="notice" role="alert">{{ loadError }} <el-button @click="load">重试</el-button>
    </div>
    <div v-if="!result && loading" class="empty" role="status">正在准备今日推荐…</div>
    <section v-else-if="result && !result.groups.length" class="card glass empty">
      <h2>从一组推荐开始</h2>
      <p>选择书籍范围，每天随机遇见几本书。已有规则关闭时，可在管理规则中重新启用。</p>
      <el-button type="primary" :disabled="busy || loading" @click="edit()">创建推荐组</el-button>
    </section>
    <section v-for="group in result?.groups || []" :key="group.rule.id" class="recommendation-group card glass">
      <header class="group-heading">
        <div>
          <h2>{{ group.rule.name }}</h2>
          <p class="muted">{{ summary(group.rule) }}</p>
          <small class="muted">今日 {{ group.books.length }} / {{ group.rule.filter.count }} 本 · 生成于 {{ formatTime(group.generatedAt) }}</small>
        </div>
        <div class="actions">
          <el-button :disabled="busy || loading" @click="edit(group.rule)">编辑规则</el-button>
          <el-button :loading="rerolling === group.rule.id" :disabled="busy || loading" @click="reroll(group)">换一批</el-button>
        </div>
      </header>
      <p v-if="group.invalidCount" class="notice">{{ group.invalidCount }} 本书已删除或不再可见，可点击“换一批”重新生成。</p>
      <p v-if="group.repeatedCount" class="notice">为补足数量，包含 {{ group.repeatedCount }} 本近期推荐，优先补入最久未推荐的书。</p>
      <p v-if="group.books.length < group.rule.filter.count && !group.invalidCount" class="notice">
        {{ group.candidateCount === 0 ? '当前没有符合规则的书籍，请调整筛选条件或导入书籍。' : group.rule.filter.strictAvoid ? '严格去重后可推荐书籍不足，已展示全部可用结果。' : '符合规则的书籍不足，已展示全部可用结果。' }}
      </p>
      <div class="book-grid">
        <article v-for="book in group.books" :key="book.id" class="recommended-book">
          <router-link :to="`/books/${book.id}`" class="cover" :aria-label="`查看《${displayBookName(book.title)}》详情`">
            <img v-if="book.coverUrl && shouldLoadBookCover(book.id)" :src="getCoverThumbnailUrl(book.coverUrl, 'card')" alt="" loading="lazy" />
            <span v-else class="cover-placeholder"><BookCoverPlaceholderText :title="displayBookName(book.title)" /></span>
          </router-link>
          <router-link :to="`/books/${book.id}`" class="book-title">{{ displayBookName(book.title) }}</router-link>
          <p class="muted author">{{ book.author || '未知作者' }}</p>
          <small class="muted">{{ statusLabels[book.readingStatus] || book.readingStatus }} · {{ book.format.toUpperCase() }}</small>
          <router-link :to="`/reader/${book.id}`" class="read-link">{{ book.readingStatus === 'READING' ? '继续阅读' : '开始阅读' }} →</router-link>
        </article>
      </div>
    </section>
    <el-drawer v-model="managerOpen" title="管理推荐规则" size="min(600px, 100vw)" append-to-body>
      <p class="muted">规则按当前账户保存，最多 30 组；排序值越小越靠前。关闭后保留历史结果。</p>
      <el-button type="primary" :disabled="busy || loading" @click="edit()">新增推荐组</el-button>
      <p v-if="!rules.length" class="empty">还没有推荐规则</p>
      <article v-for="rule in rules" :key="rule.id" class="rule-row">
        <div>
          <strong>{{ rule.name }}</strong>
          <p class="muted">{{ rule.enabled ? '已启用' : '已暂停' }} · 排序 {{ rule.sortOrder }} · {{ summary(rule) }}</p>
        </div>
        <div class="actions">
          <el-button :disabled="busy || loading" @click="toggle(rule)">{{ rule.enabled ? '暂停' : '启用' }}</el-button>
          <el-button :disabled="busy || loading" @click="edit(rule)">编辑</el-button>
          <el-button :disabled="busy || loading" @click="edit(rule, true)">复制</el-button>
          <el-button type="danger" plain :disabled="busy || loading" @click="remove(rule)">删除</el-button>
        </div>
      </article>
    </el-drawer>
    <el-dialog class="recommendation-editor" top="5vh" v-model="editorOpen" :title="draft.id ? '编辑推荐规则' : '新增推荐规则'" width="min(760px, 94vw)" append-to-body :close-on-click-modal="false">
      <p class="muted">不同条件之间为“并且”，同一项多选为“或者”；留空表示不限。</p>
      <el-form label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="推荐组名称">
            <el-input v-model="draft.name" maxlength="80" show-word-limit />
          </el-form-item>
          <el-form-item label="展示排序">
            <el-input-number v-model="draft.sortOrder" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="每天推荐数量">
            <el-input-number v-model="draft.filter.count" :min="1" :max="30" />
          </el-form-item>
          <el-form-item label="避开本组最近多少天的推荐">
            <el-input-number v-model="draft.filter.avoidDays" :min="0" :max="365" />
          </el-form-item>
          <el-form-item v-for="field in idFields" :key="field.key" :label="field.label">
            <el-select v-model="draft.filter[field.key]" multiple filterable clearable :placeholder="`不限${field.label}`">
              <el-option v-for="option in options[field.options]" :key="option.id" :label="option.name" :value="option.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="阅读状态">
            <el-select v-model="draft.filter.statuses" multiple clearable placeholder="不限阅读状态">
              <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="文件格式">
            <el-select v-model="draft.filter.formats" multiple clearable placeholder="不限文件格式">
              <el-option v-for="format in formats" :key="format" :label="format.toUpperCase()" :value="format" />
            </el-select>
          </el-form-item>
          <el-form-item label="入库来源">
            <el-select v-model="draft.filter.sources" multiple clearable placeholder="不限入库来源">
              <el-option v-for="(label, value) in sourceLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item v-for="field in booleanFields" :key="field.key" :label="field.label">
            <el-select v-model="draft.filter[field.key]" clearable placeholder="不限" @clear="draft.filter[field.key] = null">
              <el-option label="是" :value="true" />
              <el-option label="否" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item v-for="field in dayFields" :key="field.key" :label="field.label">
            <el-input-number :model-value="draft.filter[field.key]" :min="1" :max="36500" placeholder="不限，可清空" @update:model-value="draft.filter[field.key] = $event ?? null" />
          </el-form-item>
        </div>
        <div class="checkboxes">
          <el-checkbox v-model="draft.enabled">启用该组</el-checkbox>
          <el-checkbox v-model="draft.filter.includeSubcategories">包含子分类</el-checkbox>
          <el-checkbox v-model="draft.filter.matchAllTags">包含全部选中的标签</el-checkbox>
          <el-checkbox v-model="draft.filter.strictAvoid">严格去重，数量不足时不补入近期推荐</el-checkbox>
        </div>
        <p class="muted">去重天数为 0 时仍避开今天已推荐的书；非严格模式不足时按最久未推荐优先补足，不放宽筛选条件。</p>
        <p class="notice">修改筛选、数量或去重条件并保存，将重新生成该组今日推荐；仅改名称、排序或启停不会换书。</p>
        <div class="actions">
          <el-button :loading="previewing" :disabled="busy || loading" @click="preview">预览候选数量</el-button>
          <span v-if="previewCount !== null" role="status">符合筛选条件 {{ previewCount }} 本（未扣除近期推荐）</span>
        </div>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="editorOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="loading" @click="save">保存规则</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import BookCoverPlaceholderText from '@/components/BookCoverPlaceholderText.vue'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { confirm, message } from '@/utils/message'
import { getCoverThumbnailUrl } from '@/utils/cover'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import {
  emptyRecommendationRule, recommendationApi, recommendationDate,
  type RecommendationRule, type RecommendationGroup, type RecommendationOptions, type TodayRecommendations,
} from '@/utils/recommendations'

const { displayBookName } = useBookNameDisplay()

const result = ref<TodayRecommendations | null>(null)
const rules = ref<RecommendationRule[]>([])
const options = ref<RecommendationOptions>({ categories: [], tags: [], authors: [], shelf_groups: [] })
const draft = ref(emptyRecommendationRule())
const managerOpen = ref(false)
const editorOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const rerolling = ref<number | null>(null)
const previewing = ref(false)
const previewCount = ref<number | null>(null)
const loadError = ref('')
const busy = computed(() => saving.value || rerolling.value !== null)
const statusLabels: Record<string, string> = { UNREADING: '未读', READING: '在读', FINISHED: '已读' }
const sourceLabels: Record<string, string> = { UPLOAD: '上传', DIRECTORY_SCAN: '目录扫描', CRAWLER: '采集入库' }
const formats = ['epub', 'txt', 'pdf', 'mobi', 'azw3', 'docx', 'html', 'md']
const idFields = [
  { key: 'categoryIds', label: '分类', options: 'categories' },
  { key: 'tagIds', label: '包含标签', options: 'tags' },
  { key: 'excludedTagIds', label: '排除标签', options: 'tags' },
  { key: 'authorIds', label: '作者', options: 'authors' },
  { key: 'shelfGroupIds', label: '书架分组', options: 'shelf_groups' },
] as const
const booleanFields = [
  { key: 'favorite', label: '已收藏' }, { key: 'wanted', label: '想读' }, { key: 'onShelf', label: '已上书架' },
] as const
const dayFields = [
  { key: 'addedWithinDays', label: '最近多少天内入库' },
  { key: 'addedBeforeDays', label: '入库已超过多少天' },
  { key: 'notReadDays', label: '最近多少天未阅读（含从未阅读）' },
] as const
let previewSequence = 0
let clock: number | undefined
let mounted = false

watch(draft, () => { previewSequence++; previewCount.value = null }, { deep: true, flush: 'sync' })
const errorText = (error: any) => error.response?.data?.message || '操作失败，请重试'
const formatTime = (value: string) => value.replace('T', ' ').slice(0, 19)

function summary(rule: RecommendationRule) {
  const f = rule.filter
  const parts: string[] = []
  for (const field of idFields) {
    const ids = f[field.key]
    if (ids.length) parts.push(`${field.label}：${ids.map(id => options.value[field.options].find(o => o.id === id)?.name || `#${id}`).join('、')}`)
  }
  if (f.statuses.length) parts.push(f.statuses.map(s => statusLabels[s]).join(' / '))
  if (f.formats.length) parts.push(f.formats.join(' / ').toUpperCase())
  if (f.sources.length) parts.push(f.sources.map(s => sourceLabels[s]).join(' / '))
  for (const field of booleanFields) {
    if (f[field.key] !== null) parts.push(`${field.label}：${f[field.key] ? '是' : '否'}`)
  }
  for (const field of dayFields) {
    if (f[field.key]) parts.push(`${field.label} ${f[field.key]} 天`)
  }
  if (!parts.length) parts.push('全部书库')
  parts.push(`避开近 ${f.avoidDays} 天${f.strictAvoid ? '（严格）' : ''}`)
  return parts.join(' · ')
}

async function load() {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    // Serial requests keep rule metadata consistent with the generated results.
    const next = await recommendationApi.today()
    const nextRules = await recommendationApi.rules()
    const nextOptions = await recommendationApi.options()
    if (!mounted) return
    result.value = next
    rules.value = nextRules
    options.value = nextOptions
  } catch (error) {
    if (mounted) loadError.value = errorText(error)
  } finally {
    loading.value = false
  }
}

function edit(rule?: RecommendationRule, copy = false) {
  draft.value = rule ? JSON.parse(JSON.stringify(rule)) : emptyRecommendationRule()
  if (!rule) draft.value.sortOrder = Math.min(9999, Math.max(0, ...rules.value.map(r => r.sortOrder)) + 1)
  if (copy) {
    delete draft.value.id
    draft.value.name = `${draft.value.name.slice(0, 75)}（副本）`
  }
  previewCount.value = null
  editorOpen.value = true
}

async function preview() {
  if (previewing.value) return
  previewing.value = true
  const sequence = ++previewSequence
  try {
    const count = await recommendationApi.preview(draft.value)
    if (sequence === previewSequence) previewCount.value = count
  } catch (error) {
    if (sequence === previewSequence) message.error(errorText(error))
  } finally {
    previewing.value = false
  }
}

async function save() {
  if (busy.value || loading.value) return
  if (!draft.value.name.trim()) { message.error('请填写推荐组名称'); return }
  saving.value = true
  try {
    await recommendationApi.save(draft.value)
    editorOpen.value = false
    message.success('推荐规则已保存')
    await load()
  } catch (error) {
    message.error(errorText(error))
  } finally {
    saving.value = false
  }
}

async function toggle(rule: RecommendationRule) {
  if (busy.value || loading.value) return
  saving.value = true
  try {
    await recommendationApi.save({ ...rule, enabled: !rule.enabled })
    await load()
  } catch (error) { message.error(errorText(error)) }
  finally { saving.value = false }
}

async function remove(rule: RecommendationRule) {
  if (busy.value || loading.value) return
  saving.value = true
  try {
    if (!await confirm(`删除推荐组“${rule.name}”及该组推荐历史？书籍不会删除。`)) return
    await recommendationApi.delete(rule.id!)
    await load()
  } catch (error) { message.error(errorText(error)) }
  finally { saving.value = false }
}

async function reroll(group: RecommendationGroup) {
  if (busy.value || loading.value) return
  rerolling.value = group.rule.id!
  try {
    await recommendationApi.reroll(group.rule.id!)
    await load()
  } catch (error) { message.error(errorText(error)) }
  finally { rerolling.value = null }
}

function checkDate() {
  if (document.visibilityState === 'visible' && !busy.value && !editorOpen.value
      && result.value && result.value.date !== recommendationDate()) void load()
}
function visibilityChanged() {
  if (clock !== undefined) window.clearInterval(clock)
  clock = undefined
  if (document.visibilityState === 'visible') {
    checkDate()
    clock = window.setInterval(checkDate, 30000)
  }
}
onMounted(() => {
  mounted = true
  void load()
  document.addEventListener('visibilitychange', visibilityChanged)
  visibilityChanged()
})
onUnmounted(() => {
  mounted = false
  previewSequence++
  if (clock !== undefined) window.clearInterval(clock)
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>

<style scoped>
:global(.recommendation-editor) {
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}
:global(.recommendation-editor .el-dialog__body) {
  min-height: 0;
  overflow-y: auto;
}
.recommendations-view {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px;
}
.page-heading, .group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}
h1 {
  margin: 4px 0 12px;
  font-size: 30px;
}
h2 {
  margin: 0 0 8px;
  font-size: 21px;
}
.eyebrow {
  color: var(--primary);
  font-size: 11px;
  letter-spacing: .16em;
}
.muted {
  color: var(--text-secondary);
  line-height: 1.7;
}
p {
  margin: 8px 0;
}
.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.recommendation-group {
  margin-top: 24px;
  padding: 24px;
}
.group-heading > div:first-child {
  flex: 1 1 300px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.notice {
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--primary-alpha-10);
  color: var(--text-secondary);
  line-height: 1.7;
}
.empty {
  padding: 48px 20px;
  text-align: center;
}
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 28px 24px;
  margin-top: 24px;
}
.recommended-book {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.cover {
  text-decoration: none;
  width: 100%;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border-radius: 8px;
  background: var(--primary-alpha-10);
  display: grid;
  place-items: center;
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  font-size: 34px;
  color: var(--primary);
}
.book-title {
  margin-top: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-decoration: none;
  overflow-wrap: anywhere;
}
.author {
  margin: 5px 0;
  font-size: 13px;
}
.read-link {
  margin-top: 12px;
  color: var(--primary);
  text-decoration: none;
}
.rule-row {
  padding: 20px 0;
  border-bottom: 1px solid var(--border-color);
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
}
.form-grid :deep(.el-select), .form-grid :deep(.el-input-number) {
  width: 100%;
}
.checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
@media (max-width: 640px) {
  .recommendations-view {
    padding: 12px;
  }
  .recommendation-group {
    padding: 16px;
  }
  .book-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 16px;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
