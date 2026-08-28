<template>
  <div class="repair-config-view">
    <header class="page-header">
      <div class="page-heading">
        <span class="page-heading-icon" aria-hidden="true">修</span>
        <div>
          <span class="page-eyebrow">内容质量工具</span>
          <h1 class="page-title">内容修复配置</h1>
          <p class="page-subtitle">管理检测记录、广告规则、修复模板和默认检测功能</p>
        </div>
      </div>
      <div class="page-capabilities" aria-label="内容修复能力">
        <span>编码修复</span>
        <span>章节整理</span>
        <span>内容清理</span>
      </div>
    </header>

    <!-- 标签页 -->
    <div class="repair-tabs-scroll">
      <div
        class="tabs"
        role="tablist"
        aria-label="内容修复配置导航"
        :style="{
          '--repair-tab-count': tabs.length,
          '--repair-tab-index': activeTabIndex,
        }"
      >
        <span class="tab-slider" aria-hidden="true"></span>
        <button
          v-for="(tab, index) in tabs"
          :id="`repair-tab-${tab.key}`"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          type="button"
          role="tab"
          :aria-controls="`repair-panel-${tab.key}`"
          :aria-selected="activeTab === tab.key"
          :tabindex="activeTab === tab.key ? 0 : -1"
          @click="selectTab(tab.key)"
          @keydown="handleTabKeydown($event, index)"
        >
          <span class="tab-icon-lg" aria-hidden="true">{{ tab.icon }}</span>
          <span class="tab-copy">
            <strong>{{ tab.label }}</strong>
            <small>{{ tab.description }}</small>
          </span>
        </button>
      </div>
    </div>

    <!-- 广告规则 -->
    <div id="repair-panel-rules" v-show="activeTab === 'rules'" class="tab-content" role="tabpanel" aria-labelledby="repair-tab-rules">
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
    <div id="repair-panel-templates" v-show="activeTab === 'templates'" class="tab-content" role="tabpanel" aria-labelledby="repair-tab-templates">
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
    <div id="repair-panel-records" v-show="activeTab === 'records'" class="tab-content" role="tabpanel" aria-labelledby="repair-tab-records">
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
    <div id="repair-panel-general" v-show="activeTab === 'general'" class="tab-content" role="tabpanel" aria-labelledby="repair-tab-general">
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
            <el-select v-model="generalSettings.preferredEncoding" class="setting-select">
              <el-option label="自动检测（推荐）" value="AUTO" />
              <el-option label="UTF-8" value="UTF-8" />
              <el-option label="GB18030 / GBK" value="GB18030" />
              <el-option label="Big5" value="BIG5" />
              <el-option label="UTF-16 LE" value="UTF-16LE" />
              <el-option label="UTF-16 BE" value="UTF-16BE" />
            </el-select>
            <small class="setting-hint">仅在自动检测结果错误时手动指定。</small>
          </div>
          <div class="setting-item">
            <label class="setting-label">不可恢复乱码</label>
            <el-select v-model="generalSettings.unrecoverableEncodingAction" class="setting-select">
              <el-option label="标记并人工确认" value="MARK" />
              <el-option label="忽略不可恢复项" value="IGNORE" />
            </el-select>
          </div>
        </div>
        <div class="setting-item">
          <label class="setting-label">默认修复模式</label>
          <el-select v-model="generalSettings.defaultMode" class="setting-select">
            <el-option label="安全修复 - 仅处理低风险问题" value="SAFE" />
            <el-option label="标准修复 - 安全修复 + 常见广告清理、章节统一" value="STANDARD" />
            <el-option label="深度修复 - 标准修复 + 模糊广告识别、章节粘连检测" value="DEEP" />
          </el-select>
        </div>
        <div class="setting-item">
          <label class="setting-label">默认章节输出格式</label>
          <input v-model="generalSettings.chapterFormat" type="text" class="setting-input" />
          <small class="setting-hint">占位符: {number} 编号, {number:3} 三位补零, {chineseNumber} 中文编号, {title} 标题</small>
        </div>
        <div class="setting-item">
          <label class="setting-label">段首缩进方式</label>
          <el-select v-model="generalSettings.indentStyle" class="setting-select">
            <el-option label="两个全角空格（　　）" value="FULL_WIDTH_SPACE" />
            <el-option label="两个普通空格" value="HALF_SPACE" />
            <el-option label="四个普通空格" value="FOUR_SPACE" />
            <el-option label="不缩进" value="NONE" />
            <el-option label="保持原样" value="KEEP" />
          </el-select>
        </div>
        <div class="setting-row">
          <div class="setting-item">
            <label class="setting-label">段落间空行数量</label>
            <el-select v-model="generalSettings.blankLineCount" class="setting-select">
              <el-option label="不保留空行" :value="0" />
              <el-option label="保留 1 个空行" :value="1" />
              <el-option label="保留 2 个空行" :value="2" />
              <el-option label="保持原样" :value="-1" />
            </el-select>
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
            <el-select v-model="ruleForm.type">
              <el-option label="广告" value="AD" />
              <el-option label="乱码" value="ENCODING" />
              <el-option label="段落" value="PARAGRAPH" />
              <el-option label="标点" value="PUNCTUATION" />
            </el-select>
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
              <el-select v-model="ruleForm.matchScope">
                <el-option label="当前行" value="LINE" />
                <el-option label="当前段落" value="PARAGRAPH" />
                <el-option label="当前内容" value="CONTENT" />
                <el-option label="章节开头" value="CHAPTER_START" />
                <el-option label="章节结尾" value="CHAPTER_END" />
              </el-select>
            </div>
            <div class="form-group">
              <label>处理方式</label>
              <el-select v-model="ruleForm.action">
                <el-option label="删除整行" value="DELETE_LINE" />
                <el-option label="删除匹配内容" value="DELETE_MATCH" />
                <el-option label="删除整个段落" value="DELETE_PARAGRAPH" />
                <el-option label="仅标记" value="MARK_ONLY" />
                <el-option label="替换为指定内容" value="REPLACE" />
              </el-select>
            </div>
          </div>
          <div v-if="ruleForm.action === 'REPLACE'" class="form-group">
            <label>替换内容</label>
            <input v-model="ruleForm.replacement" type="text" placeholder="替换后的文本" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>风险等级</label>
              <el-select v-model="ruleForm.riskLevel">
                <el-option label="低风险" value="LOW" />
                <el-option label="中风险" value="MEDIUM" />
                <el-option label="高风险" value="HIGH" />
              </el-select>
            </div>
            <div class="form-group">
              <label>作用范围</label>
              <el-select v-model="ruleForm.scope">
                <el-option label="所有书籍" value="ALL_BOOKS" />
                <el-option v-if="ruleForm.bookId" label="仅当前书籍" value="CURRENT_BOOK" />
              </el-select>
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
                <el-select v-model="templateForm.repairMode" @change="applyFeaturePreset(templateFeatures, templateForm.repairMode)">
                  <el-option label="安全修复" value="SAFE" />
                  <el-option label="标准修复" value="STANDARD" />
                  <el-option label="深度修复" value="DEEP" />
                </el-select>
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
                <el-select v-model="templateAdvanced.preferredEncoding">
                  <el-option label="自动检测" value="AUTO" />
                  <el-option label="UTF-8" value="UTF-8" />
                  <el-option label="GB18030 / GBK" value="GB18030" />
                  <el-option label="Big5" value="BIG5" />
                  <el-option label="UTF-16 LE" value="UTF-16LE" />
                  <el-option label="UTF-16 BE" value="UTF-16BE" />
                </el-select>
              </div>
              <div class="form-group">
                <label>不可恢复乱码</label>
                <el-select v-model="templateAdvanced.unrecoverableEncodingAction">
                  <el-option label="标记并人工确认" value="MARK" />
                  <el-option label="忽略" value="IGNORE" />
                </el-select>
              </div>
              <div class="form-group parameter-wide">
                <label>章节输出格式</label>
                <input v-model="templateForm.chapterFormat" type="text" />
                <small>{number} 编号 · {number:3} 补零 · {chineseNumber} 中文编号 · {title} 标题</small>
              </div>
              <div class="form-group">
                <label>段首缩进</label>
                <el-select v-model="templateForm.indentStyle">
                  <el-option label="两个全角空格" value="FULL_WIDTH_SPACE" />
                  <el-option label="两个普通空格" value="HALF_SPACE" />
                  <el-option label="四个普通空格" value="FOUR_SPACE" />
                  <el-option label="不缩进" value="NONE" />
                  <el-option label="保持原样" value="KEEP" />
                </el-select>
              </div>
              <div class="form-group">
                <label>段落间空行</label>
                <el-select v-model="templateForm.blankLineCount">
                  <el-option label="不保留空行" :value="0" />
                  <el-option label="保留 1 个空行" :value="1" />
                  <el-option label="保留 2 个空行" :value="2" />
                  <el-option label="保持原样" :value="-1" />
                </el-select>
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
  { key: 'rules', label: '广告规则', description: '管理匹配与清理规则', icon: '📋' },
  { key: 'templates', label: '修复模板', description: '复用常用修复方案', icon: '⚙️' },
  { key: 'general', label: '修复功能', description: '设置默认检测能力', icon: '🧰' },
  { key: 'records', label: '检测结果记录', description: '继续处理历史任务', icon: '🗂️' },
]
const activeTabIndex = computed(() =>
  Math.max(0, tabs.findIndex(tab => tab.key === activeTab.value))
)

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

function handleTabKeydown(event: KeyboardEvent, index: number) {
  let nextIndex: number | null = null
  if (event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length
  if (event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length
  if (event.key === 'Home') nextIndex = 0
  if (event.key === 'End') nextIndex = tabs.length - 1
  if (nextIndex === null) return

  event.preventDefault()
  void selectTab(tabs[nextIndex].key)
  const tabButtons = (event.currentTarget as HTMLButtonElement)
    .parentElement?.querySelectorAll<HTMLButtonElement>('.tab-btn')
  tabButtons?.[nextIndex]?.focus()
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
    if (!await confirm(`确认删除规则"${rule.name}"？`)) return
    await repairStore.removeRule(rule.id)
    message.success('规则已删除')
  } catch (error) {
    if (error !== 'cancel') message.error('删除失败')
  }
}

async function handleDeleteTemplate(template: RepairTemplate) {
  try {
    if (!await confirm(`确认删除模板"${template.name}"？`)) return
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
  position: relative;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 28px 0 70px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.page-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 15px;
}

.page-heading-icon {
  display: grid;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 20%, transparent);
  border-radius: 15px;
  background: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 13%, var(--surface-card, transparent));
  color: var(--primary, var(--accent-color, #409eff));
  font-size: 21px;
  font-weight: 800;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .7);
}

.page-eyebrow {
  color: var(--primary, var(--accent-color, #409eff));
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}

.page-title {
  margin: 3px 0 0;
  color: var(--text-primary);
  font-size: 25px;
  font-weight: 750;
}

.page-subtitle {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.page-capabilities {
  display: flex;
  justify-content: flex-end;
  gap: 7px;
  flex-wrap: wrap;
}

.page-capabilities span {
  padding: 6px 10px;
  border: 1px solid color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 15%, var(--border-color));
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 7%, var(--surface-card, transparent));
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
}

.repair-tabs-scroll {
  margin-bottom: 20px;
  overflow-x: auto;
  scrollbar-width: none;
}

.repair-tabs-scroll::-webkit-scrollbar {
  display: none;
}

.tabs {
  --repair-tab-count: 4;
  --repair-tab-index: 0;
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(var(--repair-tab-count), minmax(0, 1fr));
  gap: 0;
  min-width: 720px;
  margin-bottom: 0;
  padding: 8px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 16%, var(--border-color));
  border-radius: 18px;
  background: color-mix(in srgb, var(--surface-card, var(--glass-bg)) 92%, var(--primary, #409eff) 3%);
  box-shadow: var(--shadow-sm);
}

.tab-slider {
  position: absolute;
  z-index: -1;
  top: 8px;
  bottom: 8px;
  left: 8px;
  width: calc((100% - 16px) / var(--repair-tab-count));
  border: 1px solid color-mix(in srgb, white 84%, var(--border-color));
  border-radius: 13px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .92), rgba(225, 239, 255, .7)),
    color-mix(in srgb, var(--primary, #409eff) 5%, transparent);
  box-shadow:
    0 8px 22px rgba(45, 65, 98, .15),
    inset 0 1px 0 rgba(255, 255, 255, 1),
    inset 0 -1px 0 rgba(90, 116, 153, .12);
  transform: translateX(calc(var(--repair-tab-index) * 100%));
  transition: transform 360ms cubic-bezier(.22, 1, .36, 1);
}

.tab-slider::after {
  position: absolute;
  inset: 1px 12% auto;
  height: 44%;
  border-radius: inherit;
  background: linear-gradient(rgba(255, 255, 255, .52), transparent);
  content: '';
  pointer-events: none;
}

.tab-btn {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  min-height: 64px;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  font: inherit;
  color: var(--text-secondary);
  text-align: left;
  touch-action: manipulation;
  transition: color .2s ease, background .2s ease, border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}

.tab-btn:hover:not(.active) {
  color: var(--text-primary);
  background: color-mix(in srgb, var(--surface-hover, var(--glass-hover)) 64%, transparent);
}

.tab-btn.active {
  border-color: transparent;
  background: transparent;
  box-shadow: none;
  color: var(--primary, var(--accent-color, #409eff));
}

.tab-btn:focus-visible {
  outline: 2px solid var(--primary, var(--accent-color, #409eff));
  outline-offset: -2px;
}

.tab-icon-lg {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 12%, var(--border-color));
  border-radius: 10px;
  background: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 8%, var(--surface-card, transparent));
  font-size: 18px;
  transition: background .2s ease, border-color .2s ease, transform .2s ease;
}

.tab-btn.active .tab-icon-lg {
  border-color: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 26%, transparent);
  background: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 14%, var(--surface-card, transparent));
  transform: scale(1.04);
}

.tab-copy {
  min-width: 0;
}

.tab-copy strong,
.tab-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-copy strong {
  color: inherit;
  font-size: 14px;
  font-weight: 700;
}

.tab-copy small {
  margin-top: 3px;
  color: var(--text-tertiary, var(--text-secondary));
  font-size: 11px;
}

:global(html[data-theme="modern"] .repair-config-view .tabs) {
  border-color: #cfd6e2;
  border-radius: 13px;
  background: #f3f5f8;
  box-shadow: 0 8px 22px rgba(15, 23, 42, .08);
}

:global(html[data-theme="modern"] .repair-config-view .tab-btn) {
  border-radius: 8px;
}

:global(html[data-theme="modern"] .repair-config-view .tab-btn.active) {
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

:global(html[data-theme="modern"] .repair-config-view .tab-slider) {
  border-color: color-mix(in srgb, var(--primary) 18%, #d4d9e2);
  background: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .09), inset 0 1px 0 #fff;
}

:global(html[data-theme="warm"] .repair-config-view .tabs) {
  border-color: color-mix(in srgb, var(--primary) 25%, var(--border-color));
  border-radius: 15px;
  background: color-mix(in srgb, #fffaf1 92%, var(--primary) 8%);
  box-shadow: 0 10px 26px rgba(89, 57, 35, .11);
}

:global(html[data-theme="warm"] .repair-config-view .tab-btn) {
  border-radius: 9px;
}

:global(html[data-theme="warm"] .repair-config-view .tab-btn.active) {
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

:global(html[data-theme="warm"] .repair-config-view .tab-slider) {
  border-color: color-mix(in srgb, var(--primary) 32%, #e4d5c3);
  background: #fffdf8;
  box-shadow: 0 5px 14px rgba(89, 57, 35, .12), inset 0 1px 0 rgba(255, 255, 255, .9);
}

:global(html[data-theme="natural"] .repair-config-view .tabs) {
  border-color: color-mix(in srgb, var(--primary) 26%, rgba(255, 255, 255, .78));
  background: rgba(247, 253, 249, .7);
  box-shadow: 0 14px 34px rgba(35, 83, 62, .14), inset 0 1px 0 rgba(255, 255, 255, .88);
  backdrop-filter: blur(20px) saturate(145%);
  -webkit-backdrop-filter: blur(20px) saturate(145%);
}

:global(html[data-theme="natural"] .repair-config-view .tab-btn.active) {
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

:global(html[data-theme="natural"] .repair-config-view .tab-slider) {
  border-color: rgba(255, 255, 255, .88);
  background: color-mix(in srgb, rgba(255, 255, 255, .86) 84%, var(--primary) 16%);
  box-shadow: 0 8px 20px rgba(35, 83, 62, .14), inset 0 1px 0 rgba(255, 255, 255, .95);
}

:global(html[data-theme="macos26"] .repair-config-view .tabs) {
  border-color: rgba(255, 255, 255, .82);
  border-radius: 22px;
  background: linear-gradient(145deg, rgba(255, 255, 255, .48), rgba(218, 234, 252, .28));
  box-shadow:
    0 18px 46px rgba(48, 78, 118, .18),
    inset 0 1px 0 rgba(255, 255, 255, .96),
    inset 0 -1px 0 rgba(118, 151, 193, .12);
  backdrop-filter: blur(32px) saturate(185%) contrast(103%);
  -webkit-backdrop-filter: blur(32px) saturate(185%) contrast(103%);
}

:global(html[data-theme="macos26"] .repair-config-view .tabs::before) {
  position: absolute;
  z-index: -1;
  top: 2px;
  right: 12%;
  left: 12%;
  height: 42%;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, .5), rgba(255, 255, 255, 0));
  content: '';
  pointer-events: none;
}

:global(html[data-theme="macos26"] .repair-config-view .tab-btn) {
  min-height: 66px;
  border-radius: 15px;
  text-shadow: 0 1px 0 rgba(255, 255, 255, .62);
}

:global(html[data-theme="macos26"] .repair-config-view .tab-btn:hover:not(.active)) {
  border-color: rgba(255, 255, 255, .66);
  background: rgba(255, 255, 255, .3);
}

:global(html[data-theme="macos26"] .repair-config-view .tab-btn.active) {
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

:global(html[data-theme="macos26"] .repair-config-view .tab-slider) {
  border-color: rgba(255, 255, 255, .94);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .82), rgba(231, 242, 255, .58)),
    color-mix(in srgb, var(--primary) 7%, transparent);
  box-shadow:
    0 10px 26px rgba(46, 79, 124, .18),
    inset 0 1px 0 rgba(255, 255, 255, 1),
    inset 0 -1px 0 rgba(87, 128, 184, .13);
}

:global(html[data-theme="macos26"] .repair-config-view .tab-icon-lg) {
  border-color: rgba(255, 255, 255, .72);
  border-radius: 12px;
  background: rgba(255, 255, 255, .34);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .86);
}

:global(html[data-theme="macos26"] .repair-config-view .tab-btn.active .tab-icon-lg) {
  border-color: rgba(255, 255, 255, .96);
  background: color-mix(in srgb, rgba(255, 255, 255, .72) 84%, var(--primary) 16%);
  box-shadow: 0 5px 14px rgba(49, 84, 132, .14), inset 0 1px 0 #fff;
}

.tab-content {
  min-height: 420px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  animation: repair-content-in .22s ease-out both;
}

@keyframes repair-content-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color);
}

.section-header h3 {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
}

.section-header h3::before {
  width: 4px;
  height: 18px;
  border-radius: 999px;
  background: var(--primary, var(--accent-color, #409eff));
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 9%, transparent);
  content: '';
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
  gap: 18px;
  margin-bottom: 10px;
  padding: 17px 18px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: color-mix(in srgb, var(--surface-elevated, var(--surface-card)) 94%, transparent);
  box-shadow: var(--shadow-sm);
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.rule-item:hover,
.template-item:hover {
  border-color: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 25%, var(--border-color));
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.rule-info,
.template-info {
  min-width: 0;
  flex: 1;
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
  margin-bottom: 9px;
  padding: 7px 9px;
  border: 1px solid color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 8%, var(--border-color));
  border-radius: 7px;
  background: var(--code-bg);
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

.rule-meta span,
.template-config span {
  padding: 4px 7px;
  border-radius: 6px;
  background: var(--surface-hover, var(--glass-hover));
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

.rule-actions .btn,
.template-actions .btn {
  min-width: 88px;
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
  background: rgba(15, 23, 42, .48);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
  padding: 20px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.modal-content {
  width: 100%;
  max-width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 14%, var(--border-color));
  border-radius: 18px;
  background: var(--surface-card, var(--glass-bg));
  box-shadow: 0 26px 72px rgba(15, 23, 42, .28);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 17px 19px;
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
  padding: 14px 19px;
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
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface-elevated, var(--surface-card)) 94%, transparent);
  box-shadow: var(--shadow-sm);
}

.form-group .el-select {
  width: 100%;
}

.preset-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
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

.preset-strip button:focus-visible,
.feature-switch input:focus-visible + .switch-visual {
  outline: 2px solid var(--primary, var(--accent-color, #409eff));
  outline-offset: 2px;
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
  border-radius: 13px;
  background: color-mix(in srgb, var(--input-bg, var(--glass-bg)) 92%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface-card, white) 75%, transparent);
  transition: border-color .18s ease, transform .18s ease;
}

.feature-card:hover {
  border-color: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 22%, var(--border-color));
  transform: translateY(-1px);
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
}

.setting-input {
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

.setting-row .setting-select,
.setting-row .setting-input {
  max-width: none;
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
  border-radius: 14px;
  background: color-mix(in srgb, var(--surface-elevated, var(--surface-card)) 94%, transparent);
  box-shadow: var(--shadow-sm);
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.record-card:hover {
  border-color: color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 23%, var(--border-color));
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
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
.progress-track span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--primary, var(--accent-color, #409eff)), color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 62%, #fff)); }
.progress-labels,
.record-counts { display: flex; justify-content: space-between; gap: 12px; margin-top: 6px; font-size: 12px; color: var(--text-secondary); }
.record-counts { justify-content: flex-start; flex-wrap: wrap; }
.record-actions { display: flex; gap: 8px; flex-shrink: 0; }
.record-delete { margin-left: 2px; }
.records-empty { display: grid; place-items: center; gap: 6px; min-height: 220px; padding: 32px; color: var(--text-secondary); border: 1px dashed color-mix(in srgb, var(--primary, var(--accent-color, #409eff)) 24%, var(--border-color)); border-radius: 14px; background: var(--surface-hover, var(--glass-hover)); }
.records-empty strong { color: var(--text-primary); }
.records-pagination { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 18px; color: var(--text-secondary); font-size: 13px; }

:global(html[data-theme="modern"] .repair-config-view .page-heading-icon) {
  border-radius: 9px;
  background: var(--primary-alpha-10);
  box-shadow: none;
}

:global(html[data-theme="modern"] .repair-config-view .rule-item),
:global(html[data-theme="modern"] .repair-config-view .template-item),
:global(html[data-theme="modern"] .repair-config-view .record-card),
:global(html[data-theme="modern"] .repair-config-view .general-settings) {
  border-color: #dde2ea;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 5px 15px rgba(15, 23, 42, .06);
}

:global(html[data-theme="warm"] .repair-config-view .page-heading-icon) {
  border-radius: 10px 10px 10px 4px;
  background: color-mix(in srgb, var(--primary) 12%, #fff9ef);
}

:global(html[data-theme="warm"] .repair-config-view .rule-item),
:global(html[data-theme="warm"] .repair-config-view .template-item),
:global(html[data-theme="warm"] .repair-config-view .record-card),
:global(html[data-theme="warm"] .repair-config-view .general-settings),
:global(html[data-theme="warm"] .repair-config-view .feature-card) {
  border-color: color-mix(in srgb, var(--primary) 18%, #e8ddcf);
  background: #fffaf3;
  box-shadow: 0 6px 17px rgba(89, 57, 35, .07);
}

:global(html[data-theme="natural"] .repair-config-view .rule-item),
:global(html[data-theme="natural"] .repair-config-view .template-item),
:global(html[data-theme="natural"] .repair-config-view .record-card),
:global(html[data-theme="natural"] .repair-config-view .general-settings),
:global(html[data-theme="natural"] .repair-config-view .feature-card) {
  border-color: color-mix(in srgb, var(--primary) 18%, rgba(255, 255, 255, .86));
  background: rgba(255, 255, 255, .76);
  box-shadow: 0 8px 22px rgba(35, 83, 62, .09), inset 0 1px 0 rgba(255, 255, 255, .9);
}

:global(html[data-theme="macos26"] .repair-config-view .page-heading),
:global(html[data-theme="macos26"] .repair-config-view .page-capabilities) {
  position: relative;
  z-index: 1;
}

:global(html[data-theme="macos26"] .repair-config-view .page-heading-icon) {
  border-color: rgba(255, 255, 255, .94);
  border-radius: 17px;
  background: linear-gradient(145deg, rgba(255, 255, 255, .8), rgba(220, 237, 255, .55));
  box-shadow: 0 9px 22px rgba(48, 84, 132, .16), inset 0 1px 0 #fff;
}

:global(html[data-theme="macos26"] .repair-config-view .page-capabilities span) {
  border-color: rgba(255, 255, 255, .88);
  background: rgba(255, 255, 255, .4);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .9);
}

:global(html[data-theme="macos26"] .repair-config-view .rule-item),
:global(html[data-theme="macos26"] .repair-config-view .template-item),
:global(html[data-theme="macos26"] .repair-config-view .record-card),
:global(html[data-theme="macos26"] .repair-config-view .general-settings),
:global(html[data-theme="macos26"] .repair-config-view .feature-card) {
  border-color: rgba(255, 255, 255, .9);
  border-radius: 17px;
  background: linear-gradient(145deg, rgba(255, 255, 255, .72), rgba(235, 245, 255, .47));
  box-shadow: 0 12px 30px rgba(49, 81, 124, .13), inset 0 1px 0 rgba(255, 255, 255, .98);
  backdrop-filter: blur(22px) saturate(165%);
  -webkit-backdrop-filter: blur(22px) saturate(165%);
}

:global(html[data-theme="macos26"] .repair-config-view .modal-content) {
  border-color: rgba(255, 255, 255, .92);
  border-radius: 22px;
  background: rgba(244, 250, 255, .78);
  box-shadow: 0 30px 90px rgba(29, 52, 84, .3), inset 0 1px 0 #fff;
  backdrop-filter: blur(36px) saturate(185%);
  -webkit-backdrop-filter: blur(36px) saturate(185%);
}

@media (max-width: 760px) {
  .repair-config-view {
    padding: 18px 0 70px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .page-heading-icon {
    width: 44px;
    height: 44px;
    flex-basis: 44px;
    border-radius: 13px;
  }

  .page-title {
    font-size: 21px;
  }

  .page-capabilities {
    justify-content: flex-start;
  }

  .tab-content {
    min-height: 360px;
  }

  .tabs {
    min-width: 660px;
  }

  .tab-btn {
    width: 100%;
    min-width: 0;
    min-height: 58px;
    padding: 8px 10px;
  }

  .tab-icon-lg {
    width: 32px;
    height: 32px;
    flex-basis: 32px;
    font-size: 16px;
  }

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

  .rule-item,
  .template-item {
    align-items: stretch;
    flex-direction: column;
  }

  .rule-actions,
  .template-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .rule-actions .btn,
  .template-actions .btn {
    flex: 1;
  }

  .record-card { align-items: stretch; flex-direction: column; gap: 14px; }
  .record-actions .btn { flex: 1; }
}

@media (max-width: 420px) {
  .page-heading {
    gap: 11px;
  }

  .page-heading-icon {
    width: 40px;
    height: 40px;
    flex-basis: 40px;
  }

  .page-capabilities span {
    padding: 5px 8px;
  }

  .tabs { min-width: 600px; }

  .tab-btn {
    min-height: 50px;
    gap: 7px;
    padding: 7px 8px;
  }

  .tab-icon-lg {
    width: 28px;
    height: 28px;
    flex-basis: 28px;
    font-size: 14px;
  }

  .tab-copy strong {
    font-size: 13px;
  }

  .tab-copy small {
    display: none;
  }

}

@media (min-width: 761px) and (max-width: 920px) {
  .feature-config-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tab-slider {
    transition: none;
  }

  .tab-content {
    animation: none;
  }
}
</style>
