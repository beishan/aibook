<template>
  <div class="repair-config-view">
    <div class="page-header">
      <h1 class="page-title">🔧 内容修复配置</h1>
      <p class="page-subtitle">管理广告规则和修复模板</p>
    </div>

    <!-- 标签页 -->
    <div class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <span class="tab-icon-lg">{{ tab.icon }}</span>
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <!-- 广告规则 -->
    <div v-show="activeTab === 'rules'" class="tab-content">
      <div class="section-header">
        <h3>广告规则</h3>
        <button class="btn btn-primary" @click="showRuleDialog = true">+ 添加规则</button>
      </div>
      <div class="rules-list">
        <div
          v-for="rule in repairStore.rules"
          :key="rule.id"
          class="rule-item glass"
        >
          <div class="rule-info">
            <div class="rule-name">
              {{ rule.name }}
              <span v-if="rule.systemRule" class="tag tag-info">系统</span>
              <span v-if="rule.whitelist" class="tag tag-success">白名单</span>
              <span class="risk-tag" :class="rule.riskLevel.toLowerCase()">
                {{ rule.riskLevel === 'LOW' ? '低风险' : rule.riskLevel === 'MEDIUM' ? '中风险' : '高风险' }}
              </span>
            </div>
            <div class="rule-pattern">{{ rule.pattern }}</div>
            <div class="rule-meta">
              <span>类型: {{ getRuleTypeText(rule.type) }}</span>
              <span>范围: {{ getMatchScopeText(rule.matchScope) }}</span>
              <span>处理: {{ getActionText(rule.action) }}</span>
            </div>
          </div>
          <div class="rule-actions">
            <button
              class="btn btn-sm"
              @click="toggleRuleEnabled(rule)"
            >
              {{ rule.enabled ? '✅ 已启用' : '⬜ 已禁用' }}
            </button>
            <button
              v-if="!rule.systemRule"
              class="btn btn-sm"
              @click="editRule(rule)"
            >
              ✏️ 编辑
            </button>
            <button
              v-if="!rule.systemRule"
              class="btn btn-sm btn-danger"
              @click="handleDeleteRule(rule)"
            >
              🗑️ 删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 修复模板 -->
    <div v-show="activeTab === 'templates'" class="tab-content">
      <div class="section-header">
        <h3>修复模板</h3>
        <button class="btn btn-primary" @click="showTemplateDialog = true">+ 添加模板</button>
      </div>
      <div class="templates-list">
        <div
          v-for="template in repairStore.templates"
          :key="template.id"
          class="template-item glass"
        >
          <div class="template-info">
            <div class="template-name">
              {{ template.name }}
              <span v-if="template.systemTemplate" class="tag tag-info">系统</span>
              <span class="tag" :class="getModeClass(template.repairMode)">
                {{ getModeText(template.repairMode) }}
              </span>
            </div>
            <p v-if="template.description" class="template-desc">{{ template.description }}</p>
            <div class="template-config">
              <span>章节格式: {{ template.chapterFormat }}</span>
              <span>缩进: {{ getIndentText(template.indentStyle) }}</span>
              <span>空行: {{ template.blankLineCount }}</span>
              <span>标点统一: {{ template.punctuationNormalize ? '是' : '否' }}</span>
              <span>超短章节: {{ template.minChapterWords }}字</span>
              <span>超长章节: {{ template.maxChapterWords }}字</span>
            </div>
          </div>
          <div class="template-actions">
            <button
              v-if="!template.systemTemplate"
              class="btn btn-sm"
              @click="editTemplate(template)"
            >
              ✏️ 编辑
            </button>
            <button
              v-if="!template.systemTemplate"
              class="btn btn-sm btn-danger"
              @click="handleDeleteTemplate(template)"
            >
              🗑️ 删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 通用设置 -->
    <div v-show="activeTab === 'general'" class="tab-content">
      <div class="section-header">
        <h3>通用修复设置</h3>
      </div>
      <div class="general-settings glass">
        <div class="setting-item">
          <label class="setting-label">默认修复模式</label>
          <select v-model="generalSettings.defaultMode" class="setting-select">
            <option value="SAFE">安全修复 - 仅处理低风险问题</option>
            <option value="STANDARD">标准修复 - 安全修复 + 常见广告清理、章节统一</option>
            <option value="DEEP">深度修复 - 标准修复 + 模糊广告识别、章节粘连检测</option>
          </select>
        </div>
        <div class="setting-item">
          <label class="setting-label">默认章节输出格式</label>
          <input v-model="generalSettings.chapterFormat" type="text" class="setting-input" />
          <small class="setting-hint">占位符: {number} 编号, {number:3} 三位补零, {chineseNumber} 中文编号, {title} 标题</small>
        </div>
        <div class="setting-item">
          <label class="setting-label">段首缩进方式</label>
          <select v-model="generalSettings.indentStyle" class="setting-select">
            <option value="FULL_WIDTH_SPACE">两个全角空格（　　）</option>
            <option value="HALF_SPACE">两个普通空格</option>
            <option value="FOUR_SPACE">四个普通空格</option>
            <option value="NONE">不缩进</option>
            <option value="KEEP">保持原样</option>
          </select>
        </div>
        <div class="setting-row">
          <div class="setting-item">
            <label class="setting-label">段落间空行数量</label>
            <select v-model="generalSettings.blankLineCount" class="setting-select">
              <option :value="0">不保留空行</option>
              <option :value="1">保留 1 个空行</option>
              <option :value="2">保留 2 个空行</option>
              <option :value="-1">保持原样</option>
            </select>
          </div>
          <div class="setting-item">
            <label class="setting-label">自动接受置信度阈值</label>
            <input v-model.number="generalSettings.autoApplyThreshold" type="number" step="0.1" min="0" max="1" class="setting-input" />
            <small class="setting-hint">高于此置信度的问题可自动接受（0.0~1.0）</small>
          </div>
        </div>
        <div class="setting-row">
          <div class="setting-item">
            <label class="setting-label">超短章节字数阈值</label>
            <input v-model.number="generalSettings.minChapterWords" type="number" class="setting-input" />
            <small class="setting-hint">字数低于此值的章节将被标记为异常</small>
          </div>
          <div class="setting-item">
            <label class="setting-label">超长章节字数阈值</label>
            <input v-model.number="generalSettings.maxChapterWords" type="number" class="setting-input" />
            <small class="setting-hint">字数高于此值的章节可能存在粘连</small>
          </div>
        </div>
        <div class="setting-item">
          <label class="setting-checkbox-label">
            <input v-model="generalSettings.punctuationNormalize" type="checkbox" />
            标点统一（英文标点自动转中文标点）
          </label>
        </div>
        <div class="setting-actions">
          <button class="btn btn-primary" @click="handleSaveGeneralSettings">保存设置</button>
        </div>
      </div>
    </div>

    <!-- 规则编辑弹窗 -->
    <div v-if="showRuleDialog" class="modal" @click.self="showRuleDialog = false">
      <div class="modal-content glass">
        <div class="modal-header">
          <h3>{{ editingRule ? '编辑规则' : '添加规则' }}</h3>
          <button class="btn" @click="showRuleDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>规则名称</label>
            <input v-model="ruleForm.name" type="text" placeholder="如：网址广告" />
          </div>
          <div class="form-group">
            <label>问题类型</label>
            <select v-model="ruleForm.type">
              <option value="AD">广告</option>
              <option value="ENCODING">乱码</option>
              <option value="PARAGRAPH">段落</option>
              <option value="PUNCTUATION">标点</option>
            </select>
          </div>
          <div class="form-group">
            <label>匹配表达式（正则）</label>
            <textarea v-model="ruleForm.pattern" rows="3" placeholder="如：https?://[^\s]+" />
          </div>
          <div class="form-group">
            <label class="setting-checkbox-label">
              <input v-model="ruleForm.whitelist" type="checkbox" />
              作为广告白名单（匹配内容不参与广告检测）
            </label>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>匹配范围</label>
              <select v-model="ruleForm.matchScope">
                <option value="LINE">当前行</option>
                <option value="PARAGRAPH">当前段落</option>
                <option value="CONTENT">当前内容</option>
                <option value="CHAPTER_START">章节开头</option>
                <option value="CHAPTER_END">章节结尾</option>
              </select>
            </div>
            <div class="form-group">
              <label>处理方式</label>
              <select v-model="ruleForm.action">
                <option value="DELETE_LINE">删除整行</option>
                <option value="DELETE_MATCH">删除匹配内容</option>
                <option value="DELETE_PARAGRAPH">删除整个段落</option>
                <option value="MARK_ONLY">仅标记</option>
                <option value="REPLACE">替换为指定内容</option>
              </select>
            </div>
          </div>
          <div v-if="ruleForm.action === 'REPLACE'" class="form-group">
            <label>替换内容</label>
            <input v-model="ruleForm.replacement" type="text" placeholder="替换后的文本" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>风险等级</label>
              <select v-model="ruleForm.riskLevel">
                <option value="LOW">低风险</option>
                <option value="MEDIUM">中风险</option>
                <option value="HIGH">高风险</option>
              </select>
            </div>
            <div class="form-group">
              <label>作用范围</label>
              <select v-model="ruleForm.scope">
                <option value="ALL_BOOKS">所有书籍</option>
                <option v-if="ruleForm.bookId" value="CURRENT_BOOK">仅当前书籍</option>
              </select>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="showRuleDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveRule">保存</button>
        </div>
      </div>
    </div>

    <!-- 模板编辑弹窗 -->
    <div v-if="showTemplateDialog" class="modal" @click.self="showTemplateDialog = false">
      <div class="modal-content glass">
        <div class="modal-header">
          <h3>{{ editingTemplate ? '编辑模板' : '添加模板' }}</h3>
          <button class="btn" @click="showTemplateDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>模板名称</label>
            <input v-model="templateForm.name" type="text" placeholder="如：网络小说清理" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="templateForm.description" rows="2" placeholder="模板描述" />
          </div>
          <div class="form-group">
            <label>修复模式</label>
            <select v-model="templateForm.repairMode">
              <option value="SAFE">安全修复</option>
              <option value="STANDARD">标准修复</option>
              <option value="DEEP">深度修复</option>
            </select>
          </div>
          <div class="form-group">
            <label>章节输出格式</label>
            <input v-model="templateForm.chapterFormat" type="text" />
            <small>占位符: {number} 编号, {number:3} 三位补零, {chineseNumber} 中文编号, {title} 标题</small>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>段首缩进</label>
              <select v-model="templateForm.indentStyle">
                <option value="FULL_WIDTH_SPACE">两个全角空格</option>
                <option value="HALF_SPACE">两个普通空格</option>
                <option value="FOUR_SPACE">四个普通空格</option>
                <option value="NONE">不缩进</option>
                <option value="KEEP">保持原样</option>
              </select>
            </div>
            <div class="form-group">
              <label>空行数量</label>
              <select v-model="templateForm.blankLineCount">
                <option :value="0">不保留空行</option>
                <option :value="1">保留1个空行</option>
                <option :value="2">保留2个空行</option>
                <option :value="-1">保持原样</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>超短章节字数</label>
              <input v-model.number="templateForm.minChapterWords" type="number" />
            </div>
            <div class="form-group">
              <label>超长章节字数</label>
              <input v-model.number="templateForm.maxChapterWords" type="number" />
            </div>
          </div>
          <div class="form-group">
            <label>
              <input v-model="templateForm.punctuationNormalize" type="checkbox" />
              标点统一（英文标点转中文）
            </label>
          </div>
          <div class="form-group">
            <label>自动处理置信度阈值</label>
            <input v-model.number="templateForm.autoApplyThreshold" type="number" step="0.1" min="0" max="1" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="showTemplateDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveTemplate">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, confirm } from '@/utils/message'
import { useRepairStore } from '@/stores/repair'
import type { RepairRule, RepairTemplate } from '@/utils/repair'

const repairStore = useRepairStore()
const activeTab = ref('rules')
const showRuleDialog = ref(false)
const showTemplateDialog = ref(false)
const editingRule = ref<RepairRule | null>(null)
const editingTemplate = ref<RepairTemplate | null>(null)

const generalSettings = reactive({
  defaultMode: 'STANDARD',
  chapterFormat: '第{number}章 {title}',
  indentStyle: 'FULL_WIDTH_SPACE',
  blankLineCount: 1,
  autoApplyThreshold: 0.8,
  minChapterWords: 100,
  maxChapterWords: 30000,
  punctuationNormalize: false,
})

const tabs = [
  { key: 'rules', label: '广告规则', icon: '📋' },
  { key: 'templates', label: '修复模板', icon: '⚙️' },
  { key: 'general', label: '通用设置', icon: '🎨' },
]

const ruleForm = reactive({
  name: '',
  type: 'AD',
  pattern: '',
  matchScope: 'LINE',
  action: 'DELETE_LINE',
  replacement: '',
  riskLevel: 'LOW',
  enabled: true,
  whitelist: false,
  scope: 'ALL_BOOKS',
  bookId: undefined as number | undefined,
  templateId: undefined as number | undefined,
})

const templateForm = reactive({
  name: '',
  description: '',
  repairMode: 'STANDARD' as 'SAFE' | 'STANDARD' | 'DEEP',
  chapterFormat: '第{number}章 {title}',
  indentStyle: 'FULL_WIDTH_SPACE',
  blankLineCount: 1,
  punctuationNormalize: false,
  traditionalSimplified: 'NONE',
  minChapterWords: 100,
  maxChapterWords: 30000,
  autoApplyThreshold: 0.8,
})

onMounted(async () => {
  // 加载保存的通用设置
  const saved = localStorage.getItem('textRepairSettings')
  if (saved) {
    try {
      Object.assign(generalSettings, JSON.parse(saved))
    } catch {
      // 忽略解析错误
    }
  }
  await Promise.all([repairStore.loadRules(), repairStore.loadTemplates()])
})

function editRule(rule: RepairRule) {
  editingRule.value = rule
  Object.assign(ruleForm, {
    name: rule.name,
    type: rule.type,
    pattern: rule.pattern,
    matchScope: rule.matchScope,
    action: rule.action,
    replacement: rule.replacement || '',
    riskLevel: rule.riskLevel,
    enabled: rule.enabled,
    whitelist: rule.whitelist,
    scope: rule.scope,
    bookId: rule.bookId,
    templateId: rule.templateId,
  })
  showRuleDialog.value = true
}

function editTemplate(template: RepairTemplate) {
  editingTemplate.value = template
  Object.assign(templateForm, {
    name: template.name,
    description: template.description || '',
    repairMode: template.repairMode,
    chapterFormat: template.chapterFormat,
    indentStyle: template.indentStyle,
    blankLineCount: template.blankLineCount,
    punctuationNormalize: template.punctuationNormalize,
    traditionalSimplified: template.traditionalSimplified,
    minChapterWords: template.minChapterWords,
    maxChapterWords: template.maxChapterWords,
    autoApplyThreshold: template.autoApplyThreshold,
  })
  showTemplateDialog.value = true
}

async function handleSaveRule() {
  if (!ruleForm.name.trim() || !ruleForm.pattern.trim()) {
    message.warning('请填写规则名称和匹配表达式')
    return
  }
  try {
    if (editingRule.value) {
      await repairStore.editRule(editingRule.value.id, { ...ruleForm })
      message.success('规则已更新')
    } else {
      await repairStore.addRule({ ...ruleForm })
      message.success('规则已添加')
    }
    showRuleDialog.value = false
    editingRule.value = null
  } catch {
    message.error('保存失败')
  }
}

async function handleSaveTemplate() {
  if (!templateForm.name.trim()) {
    message.warning('请填写模板名称')
    return
  }
  try {
    if (editingTemplate.value) {
      await repairStore.editTemplate(editingTemplate.value.id, { ...templateForm })
      message.success('模板已更新')
    } else {
      await repairStore.addTemplate({ ...templateForm })
      message.success('模板已添加')
    }
    showTemplateDialog.value = false
    editingTemplate.value = null
  } catch {
    message.error('保存失败')
  }
}

async function handleDeleteRule(rule: RepairRule) {
  try {
    await confirm(`确认删除规则"${rule.name}"？`)
    await repairStore.removeRule(rule.id)
    message.success('规则已删除')
  } catch (error) {
    if (error !== 'cancel') message.error('删除失败')
  }
}

async function handleDeleteTemplate(template: RepairTemplate) {
  try {
    await confirm(`确认删除模板"${template.name}"？`)
    await repairStore.removeTemplate(template.id)
    message.success('模板已删除')
  } catch (error) {
    if (error !== 'cancel') message.error('删除失败')
  }
}

async function toggleRuleEnabled(rule: RepairRule) {
  await repairStore.editRule(rule.id, { enabled: !rule.enabled })
}

function getRuleTypeText(type: string) {
  const map: Record<string, string> = { AD: '广告', ENCODING: '乱码', PARAGRAPH: '段落', PUNCTUATION: '标点' }
  return map[type] || type
}

function getMatchScopeText(scope: string) {
  const map: Record<string, string> = {
    LINE: '当前行', PARAGRAPH: '当前段落', CONTENT: '当前内容',
    CHAPTER_START: '章节开头', CHAPTER_END: '章节结尾',
  }
  return map[scope] || scope
}

function getActionText(action: string) {
  const map: Record<string, string> = {
    DELETE_LINE: '删除整行', DELETE_MATCH: '删除匹配',
    DELETE_PARAGRAPH: '删除段落', MARK_ONLY: '仅标记', REPLACE: '替换',
  }
  return map[action] || action
}

function getModeText(mode: string) {
  return { SAFE: '安全', STANDARD: '标准', DEEP: '深度' }[mode] || mode
}

function getModeClass(mode: string) {
  return { SAFE: 'tag-success', STANDARD: 'tag-info', DEEP: 'tag-warning' }[mode] || 'tag-info'
}

function getIndentText(style: string) {
  return {
    FULL_WIDTH_SPACE: '全角空格', HALF_SPACE: '半角空格',
    FOUR_SPACE: '四空格', NONE: '不缩进', KEEP: '保持原样',
  }[style] || style
}

function handleSaveGeneralSettings() {
  // 保存到 localStorage 作为全局默认设置
  localStorage.setItem('textRepairSettings', JSON.stringify(generalSettings))
  message.success('设置已保存')
}
</script>

<style scoped>
.repair-config-view {
  max-width: 900px;
  margin: 0 auto;
  padding: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 4px 0 0 0;
}

.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 2px solid var(--border-color);
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.tab-btn:hover {
  color: var(--text-primary);
  background: var(--glass-bg);
}

.tab-btn.active {
  color: var(--accent-color, var(--primary, #409eff));
  border-bottom-color: var(--accent-color, var(--primary, #409eff));
  font-weight: 600;
}

.tab-icon-lg {
  font-size: 18px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}

.rule-item,
.template-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px;
  border-radius: 10px;
  margin-bottom: 8px;
  gap: 12px;
}

.rule-name,
.template-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.rule-pattern {
  font-family: monospace;
  font-size: 12px;
  color: var(--text-secondary);
  padding: 4px 8px;
  background: var(--code-bg);
  border-radius: 4px;
  margin-bottom: 6px;
  word-break: break-all;
}

.rule-meta,
.template-config {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-secondary);
}

.template-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 4px 0 8px;
}

.rule-actions,
.template-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}

.risk-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
}
.risk-tag.low { background: var(--info-bg); }
.risk-tag.medium { background: var(--warning-bg); }
.risk-tag.high { background: var(--danger-bg); }

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

.btn-danger {
  background: var(--danger-color);
  color: white;
  border-color: var(--danger-color);
}

/* 弹窗 */
.modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-content {
  width: 100%;
  max-width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid var(--border-color);
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--input-bg);
  color: var(--text-primary);
  font-size: 14px;
}

.form-group small {
  display: block;
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-group {
  flex: 1;
}

/* 通用设置 */
.general-settings {
  padding: 24px;
  border-radius: 10px;
}

.setting-item {
  margin-bottom: 16px;
}

.setting-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.setting-select,
.setting-input {
  width: 100%;
  max-width: 400px;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--input-bg, var(--glass-bg));
  color: var(--text-primary);
  font-size: 14px;
}

.setting-hint {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.setting-checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-primary);
}

.setting-row {
  display: flex;
  gap: 16px;
}

.setting-row .setting-item {
  flex: 1;
}

.setting-actions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}
</style>
