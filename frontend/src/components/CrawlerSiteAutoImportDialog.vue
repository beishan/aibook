<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${site?.siteName || '网站'} · 自动入库配置`"
    width="min(860px, 96vw)"
    append-to-body
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="site-auto-import-editor">
      <el-alert v-if="loadFailed" type="error" :closable="false" title="配置加载失败，请重试后再编辑。">
        <el-button text type="primary" @click="load">重新加载</el-button>
      </el-alert>
      <el-form label-position="top" :disabled="loading || saving || loadFailed">
        <div class="automation-heading">
          <div>
            <strong>按规则加入私人书库</strong>
            <p>本站书籍正文采集成功后，命中任意一条规则即可自动入库。</p>
          </div>
          <el-switch v-model="form.enabled" aria-label="开启网站规则自动入库" active-text="启用" />
        </div>
        <el-form-item label="入库格式（可多选）">
          <el-checkbox-group v-model="form.formats" class="import-formats">
            <el-checkbox label="STRUCTURED">结构化章节</el-checkbox>
            <el-checkbox label="TXT">TXT</el-checkbox>
            <el-checkbox label="EPUB">EPUB</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <div class="rules-heading">
          <div>
            <h3>匹配规则</h3>
            <p>规则之间为「或」，同一规则内为「且」。</p>
          </div>
          <el-button :disabled="form.rules.length >= 50" @click="addRule">添加规则</el-button>
        </div>
        <div v-if="!form.rules.length" class="rules-empty">
          <strong>从第一条规则开始</strong>
          <span>例如：书名、作者或简介包含「神话」「传说」之一，且章节数大于 10。</span>
          <el-button type="primary" plain @click="addRule">添加第一条规则</el-button>
        </div>
        <template v-for="(rule, ruleIndex) in form.rules" :key="ruleKeys[ruleIndex]">
          <div v-if="ruleIndex" class="rule-or"><span>或 · 命中以下规则</span></div>
          <section class="import-rule" :aria-label="`规则 ${ruleIndex + 1}`">
            <header>
              <span class="rule-number">{{ String(ruleIndex + 1).padStart(2, '0') }}</span>
              <el-input v-model="rule.name" maxlength="100" :aria-label="`规则 ${ruleIndex + 1} 名称`" placeholder="规则名称" />
              <el-button text type="danger" :aria-label="`删除规则 ${ruleIndex + 1}`" @click="removeRule(ruleIndex)">删除规则</el-button>
            </header>
            <p class="rule-condition-hint">同时满足以下全部条件</p>
            <div v-for="(expression, expressionIndex) in rule.expressions"
              :key="expressionKeys[ruleIndex][expressionIndex]" class="expression-row">
              <span class="expression-and">{{ expressionIndex ? '且' : '当' }}</span>
              <el-select v-model="expression.type" class="expression-type"
                :aria-label="`规则 ${ruleIndex + 1} 条件 ${expressionIndex + 1} 类型`"
                @change="changeType(expression)">
                <el-option label="关键词包含" value="KEYWORD" />
                <el-option label="书籍标签命中" value="TAG" />
                <el-option label="书籍章节数" value="CHAPTER_COUNT" />
              </el-select>
              <div v-if="expression.type === 'CHAPTER_COUNT'" class="chapter-condition">
                <el-select v-model="expression.comparison" aria-label="章节数比较方式">
                  <el-option v-for="option in comparisons" :key="option.value"
                    :label="option.label" :value="option.value" />
                </el-select>
                <el-input-number :model-value="expression.chapterCount ?? 0"
                  :min="0" :max="2147483647" :precision="0" controls-position="right"
                  aria-label="章节数阈值" @update:model-value="expression.chapterCount = $event ?? null" />
              </div>
              <el-select v-else v-model="expression.values" multiple filterable allow-create
                default-first-option :reserve-keyword="false" :multiple-limit="50"
                class="expression-values" :aria-label="expression.type === 'TAG' ? '命中标签' : '包含关键词'"
                :placeholder="expression.type === 'TAG' ? '输入标签后按回车，可添加多个' : '输入关键词后按回车，可添加多个'">
                <el-option v-for="value in expression.values" :key="value" :label="value" :value="value" />
              </el-select>
              <el-button text type="danger" :aria-label="`删除条件 ${expressionIndex + 1}`"
                @click="removeExpression(ruleIndex, expressionIndex)">移除</el-button>
            </div>
            <el-button text type="primary" :disabled="rule.expressions.length >= 20"
              @click="addExpression(ruleIndex)">＋ 添加子表达式</el-button>
          </section>
        </template>
        <p class="matching-help">
          关键词：书名、作者、简介中包含任一关键词即可，不区分英文大小写。标签：任一标签精确命中。
          章节数使用已采集的目录总章节数。
        </p>
        <el-alert type="info" :closable="false"
          title="保存后应用于后续采集成功的书籍，不会立即处理历史书籍。单本书已开启自动入库时，优先使用单本设置与格式；暂停、部分失败的书籍等待续采成功后判断。" />
      </el-form>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading || loadFailed" @click="save">保存配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  crawlerApi,
  type CrawlerSite,
  type CrawlerSiteAutoImport,
  type CrawlerAutoImportExpression,
} from '@/utils/crawler'
import { message } from '@/utils/message'

const props = defineProps<{ modelValue: boolean; site?: CrawlerSite }>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()
const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
const form = reactive<CrawlerSiteAutoImport>({ enabled: false, formats: ['STRUCTURED'], rules: [] })
const ruleKeys = ref<number[]>([])
const expressionKeys = ref<number[][]>([])
let nextKey = 0
let requestId = 0
const comparisons = [
  { value: 'GT', label: '大于 >' },
  { value: 'GTE', label: '大于等于 ≥' },
  { value: 'EQ', label: '等于 =' },
  { value: 'NE', label: '不等于 ≠' },
  { value: 'LT', label: '小于 <' },
  { value: 'LTE', label: '小于等于 ≤' },
]

watch(() => [props.modelValue, props.site?.id], () => {
  ++requestId
  if (props.modelValue && props.site) void load()
}, { immediate: true })

async function load() {
  const siteId = props.site?.id
  if (!siteId) return
  const currentRequest = ++requestId
  loading.value = true
  loadFailed.value = false
  Object.assign(form, { enabled: false, formats: ['STRUCTURED'], rules: [] })
  ruleKeys.value = []
  expressionKeys.value = []
  try {
    const configuration = await crawlerApi.siteAutoImport(siteId)
    if (currentRequest !== requestId) return
    Object.assign(form, configuration)
    ruleKeys.value = form.rules.map(() => ++nextKey)
    expressionKeys.value = form.rules.map(rule => rule.expressions.map(() => ++nextKey))
  } catch {
    if (currentRequest === requestId) loadFailed.value = true
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

function newExpression(): CrawlerAutoImportExpression {
  return { type: 'KEYWORD', values: [], comparison: null, chapterCount: null }
}

function changeType(expression: CrawlerAutoImportExpression) {
  expression.values = []
  expression.comparison = expression.type === 'CHAPTER_COUNT' ? 'GT' : null
  expression.chapterCount = expression.type === 'CHAPTER_COUNT' ? 0 : null
}

function addRule() {
  form.rules.push({ name: `规则 ${form.rules.length + 1}`, expressions: [newExpression()] })
  ruleKeys.value.push(++nextKey)
  expressionKeys.value.push([++nextKey])
}

function removeRule(index: number) {
  form.rules.splice(index, 1)
  ruleKeys.value.splice(index, 1)
  expressionKeys.value.splice(index, 1)
}

function addExpression(ruleIndex: number) {
  form.rules[ruleIndex].expressions.push(newExpression())
  expressionKeys.value[ruleIndex].push(++nextKey)
}

function removeExpression(ruleIndex: number, index: number) {
  form.rules[ruleIndex].expressions.splice(index, 1)
  expressionKeys.value[ruleIndex].splice(index, 1)
}

async function save() {
  if (!props.site || loading.value || loadFailed.value || saving.value) return
  if (!form.formats.length) return message.warning('请至少选择一种入库格式')
  if (form.enabled && !form.rules.length) return message.warning('开启自动入库时请至少添加一条规则')
  for (const rule of form.rules) {
    if (!rule.name.trim()) return message.warning('请填写规则名称')
    if (!rule.expressions.length) return message.warning(`“${rule.name}”至少需要一个子表达式`)
    for (const expression of rule.expressions) {
      if (expression.type === 'CHAPTER_COUNT') {
        if (!expression.comparison || !Number.isInteger(expression.chapterCount)
            || expression.chapterCount! < 0) return message.warning('请填写有效的章节数比较条件')
      } else if (!expression.values.length || expression.values.some(value => !value.trim())) {
        return message.warning('关键词或标签条件至少需要填写一项')
      } else if (expression.values.some(value => value.trim().length > 200)) {
        return message.warning('每个关键词或标签不能超过 200 字')
      }
    }
  }
  saving.value = true
  try {
    await crawlerApi.setSiteAutoImport(props.site.id, form)
    message.success('网站自动入库配置已保存')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.site-auto-import-editor {
  min-height: 210px;
}
.automation-heading, .rules-heading, .import-rule header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.automation-heading {
  margin-bottom: 22px;
  padding: 18px;
  border: 1px solid var(--border-color-light);
  border-radius: 14px;
  background: var(--surface-elevated);
}
.automation-heading strong {
  font-size: 16px;
}
.automation-heading p, .rules-heading p, .matching-help, .rule-condition-hint {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.rules-heading {
  margin: 20px 0 14px;
}
.rules-heading h3 {
  margin: 0;
  font-size: 16px;
}
.import-formats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 22px;
}
.import-formats :deep(.el-checkbox) {
  margin-right: 0;
}
.import-rule {
  padding: 18px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
}
.import-rule header {
  gap: 10px;
}
.import-rule header .el-input {
  flex: 1;
  min-width: 0;
}
.rule-number {
  color: var(--primary);
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.rule-condition-hint {
  margin: 12px 0;
}
.expression-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 12px 0;
}
.expression-and {
  flex: 0 0 18px;
  padding-top: 7px;
  color: var(--text-tertiary);
  font-size: 12px;
}
.expression-type {
  flex: 0 0 150px;
}
.expression-values, .chapter-condition {
  flex: 1;
  min-width: 0;
}
.chapter-condition {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.chapter-condition .el-input-number {
  width: 100%;
}
.rule-or {
  display: flex;
  justify-content: center;
  margin: 12px 0;
}
.rule-or span {
  padding: 3px 12px;
  border-radius: 20px;
  color: var(--primary);
  background: var(--primary-alpha-10);
  font-size: 12px;
}
.rules-empty {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 28px 20px;
  border: 1px dashed var(--border-color);
  border-radius: 14px;
  text-align: center;
}
.rules-empty span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.matching-help {
  margin: 18px 0 12px;
}
@media (max-width: 640px) {
  .automation-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .import-rule {
    padding: 12px;
  }
  .expression-row {
    display: grid;
    grid-template-columns: 18px minmax(0, 1fr) auto;
  }
  .expression-type {
    grid-column: 2;
  }
  .expression-row > .el-button {
    grid-column: 3;
    grid-row: 1;
  }
  .expression-values, .chapter-condition {
    grid-column: 2 / 4;
  }
}
</style>
