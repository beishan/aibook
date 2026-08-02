<template>
  <div class="repair-config-view">
    <div class="page-header">
      <h1 class="page-title">🔧 内容修复配置</h1>
      <p class="page-subtitle">管理检测记录、广告规则、修复模板和默认检测功能</p>
    </div>

    <!-- 标签页 -->
    <div class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="selectTab(tab.key)"
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
              <span class="risk-tag" :class="(rule.riskLevel || 'LOW').toLowerCase()">
                {{ getRiskLevelText(rule.riskLevel) }}
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
        <button class="btn btn-primary" @click="handleCreateTemplate">+ 添加模板</button>
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

    <!-- 检测结果记录 -->
    <div v-show="activeTab === 'records'" class="tab-content">
      <div class="section-header records-header">
        <div>
          <h3>检测结果记录</h3>
          <p class="section-note">扫描结果和处理状态会持续保存，可直接继续处理或按原配置重新检测。</p>
        </div>
        <button class="btn" :disabled="repairStore.loading" @click="loadRecords">刷新</button>
      </div>
      <div v-if="repairStore.loading && !repairStore.records.length" class="records-empty">正在加载检测记录...</div>
      <div v-else-if="!repairStore.records.length" class="records-empty">
        <strong>还没有已完成的检测记录</strong>
        <span>从书籍详情进入“内容修复”并完成一次扫描后，会显示在这里。</span>
      </div>
      <div v-else class="record-list">
        <article v-for="record in repairStore.records" :key="record.id" class="record-card glass">
          <div class="record-main">
            <div class="record-title-row">
              <h4>{{ record.bookTitle }}</h4>
              <span class="record-status" :class="record.status.toLowerCase()">{{ getTaskStatusText(record.status) }}</span>
              <span class="record-mode">{{ getModeText(record.repairMode) }}</span>
            </div>
            <div class="record-meta">检测于 {{ formatDate(record.createdAt) }} · 识别 {{ record.detectedChapterCount || 0 }} 章</div>
            <div class="record-progress">
              <div class="progress-track">
                <span :style="{ width: `${getProcessedPercent(record)}%` }"></span>
              </div>
              <div class="progress-labels">
                <span>已处理 {{ getProcessedCount(record) }}</span>
                <span>待处理 {{ record.pendingIssueCount }} · 共 {{ record.totalIssueCount }}</span>
              </div>
            </div>
            <div class="record-counts">
              <span>已接受 {{ record.acceptedIssueCount }}</span>
              <span>已拒绝 {{ record.rejectedIssueCount }}</span>
              <span>已忽略 {{ record.ignoredIssueCount }}</span>
              <span>已应用 {{ record.appliedIssueCount }}</span>
            </div>
          </div>
          <div class="record-actions">
            <button class="btn btn-primary" :disabled="deletingId === record.id" @click="continueRecord(record)">继续处理</button>
            <button class="btn" :disabled="rescanningId === record.id || deletingId === record.id" @click="rescanRecord(record)">
              {{ rescanningId === record.id ? '检测中...' : '重新检测' }}
            </button>
            <button
              class="btn btn-danger record-delete"
              :disabled="deletingId === record.id || rescanningId === record.id"
              :aria-label="`删除《${record.bookTitle}》的检测记录`"
              @click="deleteRecord(record)"
            >
              {{ deletingId === record.id ? '删除中...' : '删除' }}
            </button>
          </div>
        </article>
      </div>
      <div v-if="repairStore.recordsTotal > recordsPageSize" class="records-pagination">
        <button class="btn btn-sm" :disabled="recordsPage === 0" @click="changeRecordsPage(-1)">上一页</button>
        <span>第 {{ recordsPage + 1 }} / {{ Math.ceil(repairStore.recordsTotal / recordsPageSize) }} 页</span>
        <button class="btn btn-sm" :disabled="(recordsPage + 1) * recordsPageSize >= repairStore.recordsTotal" @click="changeRecordsPage(1)">下一页</button>
      </div>
    </div>

    <!-- 通用设置 -->
    <div v-show="activeTab === 'general'" class="tab-content">
      <div class="section-header">
        <div>
          <h3>修复功能配置</h3>
          <p class="section-note">控制扫描时实际启用的检测器和修复建议，新建任务时自动使用。</p>
        </div>
      </div>
      <div class="general-settings glass">
        <div class="preset-strip">
          <span>快速预设</span>
          <button type="button" @click="applyFeaturePreset(generalSettings, 'SAFE')">安全</button>
          <button type="button" @click="applyFeaturePreset(generalSettings, 'STANDARD')">标准</button>
          <button type="button" @click="applyFeaturePreset(generalSettings, 'DEEP')">深度</button>
        </div>
        <div class="feature-config-grid">
          <section v-for="group in featureGroups" :key="group.key" class="feature-card">
            <div class="feature-card-heading">
              <span class="feature-icon">{{ group.icon }}</span>
              <div><strong>{{ group.title }}</strong><small>{{ group.description }}</small></div>
            </div>
            <label v-for="item in group.items" :key="item.key" class="feature-switch">
              <input v-model="generalSettings[item.key]" type="checkbox" />
              <span class="switch-visual"></span>
              <span><b>{{ item.label }}</b><small>{{ item.hint }}</small></span>
            </label>
          </section>
        </div>
        <div class="subsection-title">输出格式与判定阈值</div>
        <div class="setting-row">
          <div class="setting-item">
            <label class="setting-label">源文件解码方式</label>
            <select v-model="generalSettings.preferredEncoding" class="setting-select">
              <option value="AUTO">自动检测（推荐）</option>
              <option value="UTF-8">UTF-8</option>
              <option value="GB18030">GB18030 / GBK</option>
              <option value="BIG5">Big5</option>
              <option value="UTF-16LE">UTF-16 LE</option>
              <option value="UTF-16BE">UTF-16 BE</option>
            </select>
            <small class="setting-hint">仅在自动检测结果错误时手动指定。</small>
          </div>
          <div class="setting-item">
            <label class="setting-label">不可恢复乱码</label>
            <select v-model="generalSettings.unrecoverableEncodingAction" class="setting-select">
              <option value="MARK">标记并人工确认</option>
              <option value="IGNORE">忽略不可恢复项</option>
            </select>
          </div>
        </div>
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
      <div class="modal-content glass template-editor">
        <div class="modal-header">
          <div>
            <h3>{{ editingTemplate ? '编辑修复模板' : '添加修复模板' }}</h3>
            <p>组合检测器、输出格式与风险阈值，创建可重复使用的修复方案。</p>
          </div>
          <button class="btn" @click="showTemplateDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <section class="template-section">
            <div class="template-section-heading">
              <span>01</span><div><strong>基本信息</strong><small>名称、用途和默认修复强度</small></div>
            </div>
            <div class="form-row">
              <div class="form-group form-grow-2">
                <label>模板名称</label>
                <input v-model="templateForm.name" type="text" placeholder="如：网络小说深度清理" />
              </div>
              <div class="form-group">
                <label>修复模式</label>
                <select v-model="templateForm.repairMode" @change="applyFeaturePreset(templateFeatures, templateForm.repairMode)">
                  <option value="SAFE">安全修复</option>
                  <option value="STANDARD">标准修复</option>
                  <option value="DEEP">深度修复</option>
                </select>
              </div>
            </div>
            <div class="form-group">
              <label>模板说明</label>
              <textarea v-model="templateForm.description" rows="2" placeholder="说明该模板适合处理什么类型的文本" />
            </div>
          </section>

          <section class="template-section">
            <div class="template-section-heading">
              <span>02</span>
              <div><strong>启用的修复功能</strong><small>按类别精确控制扫描内容</small></div>
              <em>{{ enabledTemplateFeatureCount }} 项已启用</em>
            </div>
            <div class="template-feature-groups">
              <div v-for="group in featureGroups" :key="group.key" class="template-feature-group">
                <div class="template-feature-group-title">
                  <span>{{ group.icon }}</span><strong>{{ group.title }}</strong>
                </div>
                <label v-for="item in group.items" :key="item.key" class="template-feature-option">
                  <input v-model="templateFeatures[item.key]" type="checkbox" />
                  <span class="template-check">✓</span>
                  <span>{{ item.label }}</span>
                </label>
              </div>
            </div>
            <p class="template-section-tip">选择修复模式会应用推荐开关，之后仍可逐项调整。</p>
          </section>

          <section class="template-section">
            <div class="template-section-heading">
              <span>03</span><div><strong>格式与判定参数</strong><small>控制编码、章节格式、段落样式和阈值</small></div>
            </div>
            <div class="template-parameter-grid">
              <div class="form-group">
                <label>源文件解码方式</label>
                <select v-model="templateAdvanced.preferredEncoding">
                  <option value="AUTO">自动检测</option><option value="UTF-8">UTF-8</option>
                  <option value="GB18030">GB18030 / GBK</option><option value="BIG5">Big5</option>
                  <option value="UTF-16LE">UTF-16 LE</option><option value="UTF-16BE">UTF-16 BE</option>
                </select>
              </div>
              <div class="form-group">
                <label>不可恢复乱码</label>
                <select v-model="templateAdvanced.unrecoverableEncodingAction">
                  <option value="MARK">标记并人工确认</option><option value="IGNORE">忽略</option>
                </select>
              </div>
              <div class="form-group parameter-wide">
                <label>章节输出格式</label>
                <input v-model="templateForm.chapterFormat" type="text" />
                <small>{number} 编号 · {number:3} 补零 · {chineseNumber} 中文编号 · {title} 标题</small>
              </div>
              <div class="form-group">
                <label>段首缩进</label>
                <select v-model="templateForm.indentStyle">
                  <option value="FULL_WIDTH_SPACE">两个全角空格</option><option value="HALF_SPACE">两个普通空格</option>
                  <option value="FOUR_SPACE">四个普通空格</option><option value="NONE">不缩进</option><option value="KEEP">保持原样</option>
                </select>
              </div>
              <div class="form-group">
                <label>段落间空行</label>
                <select v-model="templateForm.blankLineCount">
                  <option :value="0">不保留空行</option><option :value="1">保留 1 个空行</option>
                  <option :value="2">保留 2 个空行</option><option :value="-1">保持原样</option>
                </select>
              </div>
              <div class="form-group"><label>超短章节字数</label><input v-model.number="templateForm.minChapterWords" type="number" /></div>
              <div class="form-group"><label>超长章节字数</label><input v-model.number="templateForm.maxChapterWords" type="number" /></div>
              <div class="form-group parameter-wide">
                <label>自动接受置信度 <b>{{ formatConfidence(templateForm.autoApplyThreshold) }}</b></label>
                <input v-model.number="templateForm.autoApplyThreshold" class="confidence-range" type="range" step="0.1" min="0" max="1" />
              </div>
            </div>
          </section>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, confirm } from '@/utils/message'
import { useRepairStore } from '@/stores/repair'
import type { RepairRule, RepairTask, RepairTemplate } from '@/utils/repair'

const repairStore = useRepairStore()
const router = useRouter()
const activeTab = ref('rules')
const showRuleDialog = ref(false)
const showTemplateDialog = ref(false)
const editingRule = ref<RepairRule | null>(null)
const editingTemplate = ref<RepairTemplate | null>(null)
const recordsPage = ref(0)
const recordsPageSize = 10
const rescanningId = ref<number | null>(null)
const deletingId = ref<number | null>(null)

const generalSettings = reactive<Record<string, any>>({
  defaultMode: 'STANDARD',
  chapterFormat: '第{number}章 {title}',
  indentStyle: 'FULL_WIDTH_SPACE',
  blankLineCount: 1,
  autoApplyThreshold: 0.8,
  minChapterWords: 100,
  maxChapterWords: 30000,
  punctuationNormalize: false,
  preferredEncoding: 'AUTO',
  unrecoverableEncodingAction: 'MARK',
  encodingRepair: true,
  invisibleCharCleanup: true,
  adDetection: true,
  chapterDetection: true,
  chapterNormalize: true,
  chapterNumberCheck: true,
  chapterAdhesionDetection: false,
  lineEndingNormalize: true,
  blankLineCleanup: true,
  brokenLineMerge: true,
  indentNormalize: true,
  duplicateChapterDetection: true,
  similarChapterDetection: false,
  duplicateParagraphDetection: false,
})

const tabs = [
  { key: 'rules', label: '广告规则', icon: '📋' },
  { key: 'templates', label: '修复模板', icon: '⚙️' },
  { key: 'general', label: '修复功能', icon: '🧰' },
  { key: 'records', label: '检测结果记录', icon: '🗂️' },
]

const featureGroups = [
  {
    key: 'encoding', title: '编码与乱码', icon: '译',
    description: '识别错误编码、乱码特征与隐藏控制字符',
    items: [
      { key: 'encodingRepair', label: '乱码检测与候选修复', hint: '识别 UTF-8、GBK 等错误解码特征' },
      { key: 'invisibleCharCleanup', label: '清理不可见字符', hint: '处理 BOM、零宽字符和异常控制符' },
    ],
  },
  {
    key: 'chapter', title: '章节标题', icon: '章',
    description: '识别章节结构并规范标题与编号',
    items: [
      { key: 'chapterDetection', label: '章节标题识别', hint: '识别中文数字、阿拉伯数字及特殊章节' },
      { key: 'chapterNormalize', label: '章节标题规范化', hint: '按下方章节格式生成修改建议' },
      { key: 'chapterNumberCheck', label: '章节编号与字数检查', hint: '检查缺失、重复、乱序及过短过长章节' },
      { key: 'chapterAdhesionDetection', label: '章节粘连检测', hint: '检测标题与正文或相邻章节粘连，风险较高' },
    ],
  },
  {
    key: 'paragraph', title: '段落与标点', icon: '¶',
    description: '整理换行、空行、缩进和中文标点',
    items: [
      { key: 'lineEndingNormalize', label: '统一换行符', hint: '将 CRLF、CR 统一为 LF' },
      { key: 'blankLineCleanup', label: '清理多余空行', hint: '按下方空行数量压缩连续空行' },
      { key: 'brokenLineMerge', label: '错误换行合并', hint: '合并被错误拆开的连续句子，需人工确认' },
      { key: 'indentNormalize', label: '段首缩进统一', hint: '章节标题不缩进，正文使用指定缩进' },
      { key: 'punctuationNormalize', label: '标点与异常空格整理', hint: '转换常见英文标点并清理重复标点' },
    ],
  },
  {
    key: 'content', title: '广告与重复内容', icon: '净',
    description: '结合规则、章节结构和相似度发现冗余内容',
    items: [
      { key: 'adDetection', label: '广告与推广信息检测', hint: '使用系统规则、自定义规则和白名单' },
      { key: 'duplicateChapterDetection', label: '完全重复章节', hint: '比较章节正文，标记完全一致的章节' },
      { key: 'similarChapterDetection', label: '近似重复章节', hint: '相似度检测，仅提供高风险建议' },
      { key: 'duplicateParagraphDetection', label: '重复段落检测', hint: '检测全书范围内重复出现的长段落' },
    ],
  },
]

const flatFeatureItems = featureGroups.flatMap(group => group.items)
const templateFeatures = reactive<Record<string, boolean>>({})
const enabledTemplateFeatureCount = computed(() =>
  flatFeatureItems.filter(item => templateFeatures[item.key]).length
)
const templateAdvanced = reactive({
  preferredEncoding: 'AUTO',
  unrecoverableEncodingAction: 'MARK',
})

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

applyFeaturePreset(templateFeatures, 'STANDARD')

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

async function selectTab(tab: string) {
  activeTab.value = tab
  if (tab === 'records' && !repairStore.records.length) await loadRecords()
}

async function loadRecords() {
  try {
    await repairStore.loadRecords(recordsPage.value, recordsPageSize)
  } catch {
    message.error('加载检测记录失败')
  }
}

async function changeRecordsPage(offset: number) {
  recordsPage.value += offset
  await loadRecords()
}

function continueRecord(record: RepairTask) {
  router.push({ name: 'TextRepair', params: { id: record.bookId }, query: { taskId: record.id } })
}

async function rescanRecord(record: RepairTask) {
  const confirmed = await confirm('将按该记录的书籍版本和修复配置重新检测，原记录会继续保留。确认继续？')
  if (!confirmed) return
  try {
    rescanningId.value = record.id
    const newRecord = await repairStore.rescanTask(record.id)
    message.success(`重新检测完成，发现 ${newRecord.totalIssueCount} 个问题`)
    router.push({ name: 'TextRepair', params: { id: newRecord.bookId }, query: { taskId: newRecord.id } })
  } catch {
    message.error('重新检测失败')
  } finally {
    rescanningId.value = null
  }
}

async function deleteRecord(record: RepairTask) {
  const confirmed = await confirm(
    `确认删除《${record.bookTitle}》在 ${formatDate(record.createdAt)} 生成的检测记录？\n\n该记录的检测问题和处理状态将一并删除，书籍文件不会受到影响。`,
    '删除检测记录',
  )
  if (!confirmed) return
  try {
    deletingId.value = record.id
    const isLastRecordOnPage = repairStore.records.length === 1
    await repairStore.removeTask(record.id)
    if (isLastRecordOnPage && recordsPage.value > 0) recordsPage.value -= 1
    await loadRecords()
    message.success('检测记录已删除')
  } catch {
    message.error('删除检测记录失败')
  } finally {
    deletingId.value = null
  }
}

function getProcessedCount(record: RepairTask) {
  return Math.max(0, record.totalIssueCount - record.pendingIssueCount)
}

function getProcessedPercent(record: RepairTask) {
  if (!record.totalIssueCount) return 100
  return Math.round(getProcessedCount(record) / record.totalIssueCount * 100)
}

function getTaskStatusText(status: string) {
  return status === 'COMPLETED' ? '已完成修复' : '待处理'
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

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
  applyFeaturePreset(templateFeatures, template.repairMode)
  if (template.enabledItemsJson) {
    try {
      const options = JSON.parse(template.enabledItemsJson)
      Object.assign(templateFeatures, options)
      templateAdvanced.preferredEncoding = options.preferredEncoding || 'AUTO'
      templateAdvanced.unrecoverableEncodingAction =
        options.unrecoverableEncodingAction || 'MARK'
    } catch {
      // 旧模板配置损坏时使用模式默认值。
    }
  }
  showTemplateDialog.value = true
}

function handleCreateTemplate() {
  editingTemplate.value = null
  Object.assign(templateForm, {
    name: '', description: '', repairMode: 'STANDARD',
    chapterFormat: '第{number}章 {title}', indentStyle: 'FULL_WIDTH_SPACE',
    blankLineCount: 1, punctuationNormalize: false,
    traditionalSimplified: 'NONE', minChapterWords: 100,
    maxChapterWords: 30000, autoApplyThreshold: 0.8,
  })
  applyFeaturePreset(templateFeatures, 'STANDARD')
  Object.assign(templateAdvanced, {
    preferredEncoding: 'AUTO',
    unrecoverableEncodingAction: 'MARK',
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
    const payload = {
      ...templateForm,
      punctuationNormalize: Boolean(templateFeatures.punctuationNormalize),
      enabledItemsJson: JSON.stringify({
        ...templateFeatures,
        ...templateAdvanced,
      }),
    }
    if (editingTemplate.value) {
      await repairStore.editTemplate(editingTemplate.value.id, payload)
      message.success('模板已更新')
    } else {
      await repairStore.addTemplate(payload)
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

function applyFeaturePreset(target: Record<string, any>, mode: string) {
  const standard = mode !== 'SAFE'
  const deep = mode === 'DEEP'
  Object.assign(target, {
    encodingRepair: true,
    invisibleCharCleanup: true,
    adDetection: true,
    chapterDetection: true,
    chapterNormalize: standard,
    chapterNumberCheck: standard,
    chapterAdhesionDetection: deep,
    lineEndingNormalize: true,
    blankLineCleanup: true,
    brokenLineMerge: standard,
    indentNormalize: standard,
    punctuationNormalize: deep,
    duplicateChapterDetection: standard,
    similarChapterDetection: deep,
    duplicateParagraphDetection: deep,
  })
  if ('defaultMode' in target) target.defaultMode = mode
}

function getRiskLevelText(level?: string) {
  return { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }[level || 'LOW'] || '低风险'
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

function formatConfidence(value: number | null | undefined) {
  return Number.isFinite(Number(value)) ? Number(value).toFixed(1) : '0.8'
}

function handleSaveGeneralSettings() {
  // 保存到 localStorage 作为全局默认设置
  localStorage.setItem('textRepairSettings', JSON.stringify(generalSettings))
  message.success('设置已保存')
}
</script>

<style scoped>
.repair-config-view {
  max-width: 1120px;
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

.section-note {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
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

.template-editor {
  max-width: 860px;
  max-height: calc(100vh - 32px);
  overflow: hidden;
  background: var(--surface-card, var(--glass-bg));
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.24);
}

.template-editor .modal-header {
  padding: 18px 22px;
  background: var(--surface-card, var(--glass-bg));
}

.template-editor .modal-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
}

.template-editor .modal-header p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.template-editor .modal-body {
  padding: 18px;
  background: color-mix(in srgb, var(--input-bg, #f5f7fa) 72%, transparent);
}

.template-editor .modal-footer {
  padding: 13px 20px;
  background: var(--surface-card, var(--glass-bg));
}

.template-section {
  margin-bottom: 14px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: var(--surface-card, var(--glass-bg));
}

.template-section:last-child { margin-bottom: 0; }

.template-section-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.template-section-heading > span {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 7px;
  background: color-mix(in srgb, var(--accent-color, #409eff) 14%, transparent);
  color: var(--accent-color, #409eff);
  font-size: 11px;
  font-weight: 700;
}

.template-section-heading strong,
.template-section-heading small { display: block; }

.template-section-heading strong {
  color: var(--text-primary);
  font-size: 14px;
}

.template-section-heading small {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 11px;
}

.template-section-heading em {
  margin-left: auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--info-bg, #ecf5ff);
  color: var(--accent-color, #409eff);
  font-size: 11px;
  font-style: normal;
}

.form-grow-2 { flex: 2 !important; }

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

.preset-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: color-mix(in srgb, var(--glass-bg) 82%, var(--accent-color, #409eff) 8%);
  color: var(--text-secondary);
  font-size: 12px;
}

.preset-strip button {
  padding: 4px 11px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--input-bg, var(--glass-bg));
  color: var(--text-primary);
  cursor: pointer;
}

.preset-strip button:hover {
  border-color: var(--accent-color, #409eff);
  color: var(--accent-color, #409eff);
}

.feature-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
  margin-bottom: 24px;
}

.feature-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: color-mix(in srgb, var(--input-bg, var(--glass-bg)) 92%, transparent);
}

.feature-card-heading {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 13px;
  padding-bottom: 11px;
  border-bottom: 1px dashed var(--border-color);
}

.feature-card-heading strong,
.feature-card-heading small,
.feature-switch b,
.feature-switch small {
  display: block;
}

.feature-card-heading small,
.feature-switch small {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.feature-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 7px;
  background: var(--accent-color, #409eff);
  color: white;
  font-size: 13px;
  font-weight: 700;
}

.feature-switch {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 9px;
  align-items: start;
  padding: 8px 0;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 13px;
}

.feature-switch > span:last-child {
  min-width: 0;
  overflow-wrap: anywhere;
}

.feature-switch b {
  line-height: 1.35;
}

.feature-switch input {
  position: absolute;
  opacity: 0;
}

.switch-visual {
  position: relative;
  width: 32px;
  height: 18px;
  margin-top: 1px;
  border-radius: 999px;
  background: var(--border-color);
  transition: background 0.18s ease;
}

.switch-visual::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  transition: transform 0.18s ease;
}

.feature-switch input:checked + .switch-visual {
  background: var(--accent-color, #409eff);
}

.feature-switch input:checked + .switch-visual::after {
  transform: translateX(14px);
}

.subsection-title {
  margin: 4px 0 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
}

.template-feature-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.template-feature-group {
  min-width: 0;
  padding: 11px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--input-bg, var(--glass-bg));
}

.template-feature-group-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 7px;
  padding-bottom: 7px;
  border-bottom: 1px dashed var(--border-color);
  color: var(--text-primary);
  font-size: 12px;
}

.template-feature-group-title > span {
  display: grid;
  place-items: center;
  width: 21px;
  height: 21px;
  border-radius: 5px;
  background: var(--accent-color, #409eff);
  color: white;
  font-size: 10px;
}

.template-feature-option {
  display: flex !important;
  align-items: center;
  gap: 8px;
  padding: 5px 3px;
  margin: 0 !important;
  color: var(--text-primary) !important;
  font-size: 12px !important;
  cursor: pointer;
}

.template-feature-option input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.template-check {
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  flex: 0 0 17px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--surface-card, white);
  color: transparent;
  font-size: 11px;
  transition: 0.16s ease;
}

.template-feature-option input:checked + .template-check {
  border-color: var(--accent-color, #409eff);
  background: var(--accent-color, #409eff);
  color: white;
}

.template-section-tip {
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 11px;
}

.template-parameter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.parameter-wide { grid-column: 1 / -1; }

.confidence-range {
  height: 28px;
  padding: 0 !important;
  accent-color: var(--accent-color, #409eff);
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

.records-header { align-items: flex-start; }

.record-list { display: grid; gap: 12px; }

.record-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 18px 20px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
}

.record-main { flex: 1; min-width: 0; }

.record-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.record-title-row h4 { margin: 0; font-size: 17px; color: var(--text-primary); }
.record-status,
.record-mode {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--accent-bg);
  color: var(--accent-color);
}
.record-status.completed { background: rgba(34, 197, 94, .12); color: #16a34a; }
.record-mode { background: rgba(100, 116, 139, .1); color: var(--text-secondary); }
.record-meta { margin-top: 6px; color: var(--text-secondary); font-size: 12px; }
.record-progress { max-width: 620px; margin-top: 14px; }
.progress-track { height: 7px; overflow: hidden; border-radius: 99px; background: var(--border-color); }
.progress-track span { display: block; height: 100%; border-radius: inherit; background: var(--accent-color); }
.progress-labels,
.record-counts { display: flex; justify-content: space-between; gap: 12px; margin-top: 6px; font-size: 12px; color: var(--text-secondary); }
.record-counts { justify-content: flex-start; flex-wrap: wrap; }
.record-actions { display: flex; gap: 8px; flex-shrink: 0; }
.record-delete { margin-left: 2px; }
.records-empty { display: grid; place-items: center; gap: 6px; min-height: 220px; padding: 32px; color: var(--text-secondary); border: 1px dashed var(--border-color); border-radius: 10px; }
.records-empty strong { color: var(--text-primary); }
.records-pagination { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 18px; color: var(--text-secondary); font-size: 13px; }

@media (max-width: 760px) {
  .feature-config-grid,
  .template-feature-groups,
  .template-parameter-grid {
    grid-template-columns: 1fr;
  }

  .parameter-wide { grid-column: auto; }

  .setting-row,
  .form-row {
    display: block;
  }

  .general-settings {
    padding: 16px;
  }

  .modal:has(.template-editor) { padding: 8px; }

  .template-editor {
    max-height: calc(100vh - 16px);
    border-radius: 10px;
  }

  .template-editor .modal-header,
  .template-editor .modal-footer { padding: 13px 14px; }

  .template-editor .modal-body { padding: 10px; }

  .template-section { padding: 12px; }

  .record-card { align-items: stretch; flex-direction: column; gap: 14px; }
  .record-actions .btn { flex: 1; }
}

@media (min-width: 761px) and (max-width: 920px) {
  .feature-config-grid {
    grid-template-columns: 1fr;
  }
}
</style>
