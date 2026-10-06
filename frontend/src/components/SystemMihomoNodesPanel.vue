<template>
  <section v-loading="loading" class="nodes-panel">
    <header class="nodes-panel-header">
      <div>
        <p class="eyebrow">MIHOMO ROUTING</p>
        <h3>{{ proxy.name }} · 节点与备用组</h3>
        <p class="nodes-panel-description">
          管理备用节点组，并查看控制器返回的全部具体节点。
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <div
      class="nodes-segmented-tabs"
      role="tablist"
      aria-label="Mihomo 节点管理分类"
      @keydown="handleTabKeydown"
    >
      <span
        class="nodes-tab-indicator"
        :style="{ transform: `translateX(${activeTabIndex * 100}%)` }"
        aria-hidden="true"
      />
      <button
        v-for="tab in tabs"
        :id="`mihomo-tab-${tab.key}`"
        :key="tab.key"
        type="button"
        role="tab"
        :aria-selected="activeTab === tab.key"
        :aria-controls="`mihomo-panel-${tab.key}`"
        :tabindex="activeTab === tab.key ? 0 : -1"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <span>{{ tab.label }}</span>
        <b>{{ tab.key === 'groups' ? groups.length : catalog.nodes.length }}</b>
      </button>
    </div>

    <section
      v-show="activeTab === 'groups'"
      id="mihomo-panel-groups"
      class="nodes-tab-panel"
      role="tabpanel"
      aria-labelledby="mihomo-tab-groups"
      tabindex="0"
    >
      <div class="tab-panel-heading">
        <div>
          <h4>节点组</h4>
          <p>为代理组配置轮换节点、Mihomo 受控组和代理入口。</p>
        </div>
        <el-button type="primary" @click="newGroup">新增节点组</el-button>
      </div>

      <el-alert
        title="节点组只能选择受控 Selector 组中的具体节点；左侧排列顺序用于节点轮换。"
        type="info"
        :closable="false"
        show-icon
      />

      <div v-if="groups.length" class="group-list">
        <article v-for="group in groups" :key="group.id" class="group-card">
          <div class="group-card-main">
            <span class="group-mark" aria-hidden="true">↻</span>
            <div class="group-card-content">
              <strong>{{ group.name }}</strong>
              <div class="group-card-details">
                <span><small>受控组</small><b>{{ group.controlGroup }}</b></span>
                <span><small>已选节点</small><b>{{ group.nodes.length }} 个</b></span>
                <span><small>代理入口</small><b>{{ group.proxyUrl }}</b></span>
              </div>
            </div>
          </div>
          <div class="group-card-actions">
            <el-button size="small" type="primary" plain @click="editGroup(group)">
              配置
            </el-button>
            <el-button size="small" type="danger" plain @click="removeGroup(group)">
              删除
            </el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="尚未创建节点组" :image-size="54">
        <el-button type="primary" @click="newGroup">新增节点组</el-button>
      </el-empty>
    </section>

    <section
      v-show="activeTab === 'nodes'"
      id="mihomo-panel-nodes"
      class="nodes-tab-panel"
      role="tabpanel"
      aria-labelledby="mihomo-tab-nodes"
      tabindex="0"
    >
      <div class="all-nodes-toolbar">
        <div>
          <h4>全部节点</h4>
          <p>查看控制器中的具体节点并测试连通性。</p>
        </div>
        <el-input
          v-model="nodeSearch"
          clearable
          class="node-search"
          placeholder="搜索节点名称或协议"
          aria-label="搜索全部节点"
        />
      </div>

      <el-table
        :data="visibleNodes"
        max-height="440"
        empty-text="控制器尚未返回具体节点"
        class="all-nodes-table"
      >
        <el-table-column prop="name" label="节点名称" min-width="220" />
        <el-table-column prop="type" label="协议" width="110" />
        <el-table-column label="检测结果" min-width="170">
          <template #default="{ row }">
            <div class="node-health-cell">
              <el-tag :type="nodeHealthType(row)" effect="light">
                {{ nodeHealthLabel(row) }}
              </el-tag>
              <small v-if="row.checkedAt">{{ formatTime(row.checkedAt) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="Boolean(testing)"
              :loading="testing === row.name"
              @click="testNode(row.name)"
            >
              检测
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="editorOpen"
      :title="editingId ? '配置节点组' : '新增节点组'"
      width="min(920px, 96vw)"
      append-to-body
      destroy-on-close
      class="node-group-editor-dialog"
    >
      <div class="group-editor" v-loading="saving">
        <el-form label-position="top" class="group-editor-form">
          <div class="form-grid">
            <el-form-item label="节点组名称">
              <el-input
                v-model="draft.name"
                maxlength="100"
                placeholder="例如：香港与日本备用组"
              />
            </el-form-item>
            <el-form-item label="Mihomo 受控组">
              <el-select
                :model-value="draft.controlGroup"
                filterable
                class="full-width"
                placeholder="选择已有 Selector 组"
                @change="changeControlGroup"
              >
                <el-option
                  v-for="group in selectorGroups"
                  :key="group.name"
                  :value="group.name"
                  :label="group.name"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="代理入口">
              <el-input v-model="draft.proxyUrl" placeholder="http://mihomo:7895" />
            </el-form-item>
          </div>
        </el-form>

        <div class="transfer-heading">
          <div>
            <h4>选择轮换节点</h4>
            <p>左侧是该组已选择的节点，右侧是所选受控组中的备选节点。</p>
          </div>
          <span>最多 100 个节点</span>
        </div>

        <div class="node-transfer">
          <section class="transfer-pane selected-pane" aria-label="已选择的节点">
            <header class="transfer-pane-header">
              <div>
                <strong>已选节点</strong>
                <small>顺序决定轮换次序</small>
              </div>
              <el-tag effect="plain">{{ draft.nodes.length }}</el-tag>
            </header>
            <div class="transfer-list">
              <article v-for="(name, index) in draft.nodes" :key="name" class="transfer-node">
                <el-checkbox
                  :model-value="selectedConfiguredNames.includes(name)"
                  :aria-label="`选择已选节点 ${name}`"
                  @change="checked => setConfiguredSelection(name, Boolean(checked))"
                />
                <span class="node-order">{{ index + 1 }}</span>
                <div class="transfer-node-info">
                  <strong>{{ name }}</strong>
                  <small>{{ nodeSummary(name) }}</small>
                </div>
                <div class="node-order-actions">
                  <el-button
                    text
                    size="small"
                    :disabled="index === 0"
                    :aria-label="`上移 ${name}`"
                    @click="move(index, -1)"
                  >
                    ↑
                  </el-button>
                  <el-button
                    text
                    size="small"
                    :disabled="index === draft.nodes.length - 1"
                    :aria-label="`下移 ${name}`"
                    @click="move(index, 1)"
                  >
                    ↓
                  </el-button>
                </div>
              </article>
              <el-empty
                v-if="!draft.nodes.length"
                :image-size="42"
                description="还没有选择节点"
              />
            </div>
          </section>

          <div class="transfer-controls" aria-label="移动节点">
            <el-button
              type="primary"
              plain
              :disabled="!selectedAvailableNames.length || draft.nodes.length >= 100"
              @click="addSelectedNodes"
            >
              ← 加入已选
            </el-button>
            <el-button
              plain
              :disabled="!selectedConfiguredNames.length"
              @click="removeSelectedNodes"
            >
              移出已选 →
            </el-button>
          </div>

          <section class="transfer-pane available-pane" aria-label="备选节点">
            <header class="transfer-pane-header">
              <div>
                <strong>备选节点</strong>
                <small>来自当前受控组</small>
              </div>
              <el-tag effect="plain">{{ availableNodes.length }}</el-tag>
            </header>
            <el-input
              v-model="candidateSearch"
              clearable
              size="small"
              placeholder="搜索备选节点"
              aria-label="搜索备选节点"
            />
            <div class="transfer-list">
              <article v-for="node in availableNodes" :key="node.name" class="transfer-node">
                <el-checkbox
                  :model-value="selectedAvailableNames.includes(node.name)"
                  :aria-label="`选择备选节点 ${node.name}`"
                  @change="checked => setAvailableSelection(node.name, Boolean(checked))"
                />
                <div class="transfer-node-info">
                  <strong>{{ node.name }}</strong>
                  <small>{{ node.type }} · {{ nodeHealthLabel(node) }}</small>
                </div>
              </article>
              <el-empty
                v-if="!availableNodes.length"
                :image-size="42"
                :description="draft.controlGroup ? '没有可添加的节点' : '请先选择受控组'"
              />
            </div>
          </section>
        </div>
      </div>

      <template #footer>
        <div class="editor-footer">
          <el-button :disabled="saving" @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="!canSave" @click="saveGroup">
            保存节点组
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  proxySettingsApi,
  type SystemProxyConfig,
  type MihomoNodeGroup,
  type MihomoNodeGroupPayload,
} from '@/utils/proxySettings'
import type { MihomoCatalog, MihomoNode } from '@/utils/crawler'
import { confirm, message } from '@/utils/message'

const props = defineProps<{ proxy: SystemProxyConfig }>()

type NodeTab = 'groups' | 'nodes'

const tabs: { key: NodeTab; label: string }[] = [
  { key: 'groups', label: '节点组' },
  { key: 'nodes', label: '全部节点' },
]

const loading = ref(false)
const saving = ref(false)
const testing = ref('')
const activeTab = ref<NodeTab>('groups')
const nodeSearch = ref('')
const candidateSearch = ref('')
const groups = ref<MihomoNodeGroup[]>([])
const catalog = ref<MihomoCatalog>({ groups: [], nodes: [] })
const editorOpen = ref(false)
const editingId = ref<number>()
const selectedConfiguredNames = ref<string[]>([])
const selectedAvailableNames = ref<string[]>([])
const draft = ref<MihomoNodeGroupPayload>({
  name: '',
  controlGroup: '',
  proxyUrl: props.proxy.url,
  nodes: [],
})

const activeTabIndex = computed(() => tabs.findIndex(tab => tab.key === activeTab.value))
const selectorGroups = computed(() => catalog.value.groups)
const controlGroupMembers = computed(() => new Set(
  catalog.value.groups.find(group => group.name === draft.value.controlGroup)?.nodes || [],
))
const availableNodes = computed(() => {
  const searchValue = candidateSearch.value.trim().toLocaleLowerCase()

  return catalog.value.nodes.filter(node => {
    if (!controlGroupMembers.value.has(node.name) || draft.value.nodes.includes(node.name)) {
      return false
    }

    return !searchValue || `${node.name} ${node.type}`.toLocaleLowerCase().includes(searchValue)
  })
})
const visibleNodes = computed(() => {
  const searchValue = nodeSearch.value.trim().toLocaleLowerCase()
  if (!searchValue) return catalog.value.nodes

  return catalog.value.nodes.filter(node =>
    `${node.name} ${node.type}`.toLocaleLowerCase().includes(searchValue),
  )
})
const canSave = computed(() => Boolean(
  draft.value.name.trim()
  && draft.value.controlGroup
  && draft.value.proxyUrl.trim()
  && draft.value.nodes.length > 0
  && draft.value.nodes.length <= 100,
))

const error = (cause: any) => message.error(cause.response?.data?.message || 'Mihomo 操作失败')

async function load() {
  loading.value = true
  try {
    const [nodes, savedGroups] = await Promise.all([
      proxySettingsApi.mihomoCatalog(props.proxy.id, {
        ...props.proxy,
        secret: '',
        clearSecret: false,
      }),
      proxySettingsApi.mihomoGroups(props.proxy.id),
    ])

    catalog.value = nodes
    groups.value = savedGroups
  } catch (cause) {
    error(cause)
  } finally {
    loading.value = false
  }
}

function newGroup() {
  editingId.value = undefined
  draft.value = {
    name: '',
    controlGroup: '',
    proxyUrl: props.proxy.url,
    nodes: [],
  }
  selectedConfiguredNames.value = []
  selectedAvailableNames.value = []
  candidateSearch.value = ''
  editorOpen.value = true
}

function editGroup(group: MihomoNodeGroup) {
  editingId.value = group.id
  draft.value = {
    name: group.name,
    controlGroup: group.controlGroup,
    proxyUrl: group.proxyUrl,
    nodes: [...group.nodes],
  }
  selectedConfiguredNames.value = []
  selectedAvailableNames.value = []
  candidateSearch.value = ''
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
  selectedConfiguredNames.value = []
  selectedAvailableNames.value = []
}

function changeControlGroup(value: string) {
  if (draft.value.controlGroup === value) return

  draft.value.controlGroup = value
  draft.value.nodes = []
  selectedConfiguredNames.value = []
  selectedAvailableNames.value = []
  candidateSearch.value = ''
}

function setSelection(selection: typeof selectedAvailableNames, name: string, checked: boolean) {
  if (checked && !selection.value.includes(name)) {
    selection.value = [...selection.value, name]
  } else if (!checked) {
    selection.value = selection.value.filter(item => item !== name)
  }
}

function setAvailableSelection(name: string, checked: boolean) {
  setSelection(selectedAvailableNames, name, checked)
}

function setConfiguredSelection(name: string, checked: boolean) {
  setSelection(selectedConfiguredNames, name, checked)
}

function addSelectedNodes() {
  const additions = selectedAvailableNames.value
    .filter(name => controlGroupMembers.value.has(name) && !draft.value.nodes.includes(name))
  draft.value.nodes = [...draft.value.nodes, ...additions].slice(0, 100)
  selectedAvailableNames.value = []
}

function removeSelectedNodes() {
  const removals = new Set(selectedConfiguredNames.value)
  draft.value.nodes = draft.value.nodes.filter(name => !removals.has(name))
  selectedConfiguredNames.value = []
}

function move(index: number, direction: number) {
  const nodes = [...draft.value.nodes]
  const target = index + direction
  if (target < 0 || target >= nodes.length) return

  const current = nodes[index]
  nodes[index] = nodes[target]
  nodes[target] = current
  draft.value.nodes = nodes
}

function nodeSummary(name: string) {
  const node = catalog.value.nodes.find(item => item.name === name)
  if (!node) return '当前控制器未返回此节点'
  return `${node.type} · ${nodeHealthLabel(node)}`
}

function nodeHealthLabel(node: MihomoNode) {
  if (node.alive === false) return '检测失败'
  if (node.delay != null) return `${node.delay} ms`
  return node.alive === true ? '可用' : '未检测'
}

function nodeHealthType(node: MihomoNode): 'success' | 'danger' | 'info' {
  if (node.alive === false) return 'danger'
  if (node.alive === true) return 'success'
  return 'info'
}

function formatTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

async function saveGroup() {
  if (!canSave.value || saving.value) return

  saving.value = true
  try {
    if (editingId.value) {
      await proxySettingsApi.updateMihomoGroup(props.proxy.id, editingId.value, draft.value)
    } else {
      await proxySettingsApi.createMihomoGroup(props.proxy.id, draft.value)
    }

    closeEditor()
    message.success('节点组已保存')
    await load()
  } catch (cause) {
    error(cause)
  } finally {
    saving.value = false
  }
}

async function removeGroup(group: MihomoNodeGroup) {
  if (!await confirm(`删除节点组“${group.name}”？`)) return

  try {
    await proxySettingsApi.deleteMihomoGroup(props.proxy.id, group.id)
    if (editingId.value === group.id) closeEditor()
    message.success('节点组已删除')
    await load()
  } catch (cause) {
    error(cause)
  }
}

async function testNode(name: string) {
  testing.value = name
  try {
    const result = await proxySettingsApi.mihomoDelay(props.proxy.id, name)
    const node = catalog.value.nodes.find(item => item.name === name)
    if (node) Object.assign(node, result)
  } catch (cause) {
    error(cause)
  } finally {
    testing.value = ''
  }
}

function handleTabKeydown(event: KeyboardEvent) {
  const current = activeTabIndex.value
  let next = current

  if (event.key === 'ArrowRight') next = (current + 1) % tabs.length
  else if (event.key === 'ArrowLeft') next = (current - 1 + tabs.length) % tabs.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = tabs.length - 1
  else return

  event.preventDefault()
  activeTab.value = tabs[next].key
  void nextTick(() => document.getElementById(`mihomo-tab-${tabs[next].key}`)?.focus())
}

onMounted(load)
</script>

<style scoped>
.nodes-panel {
  display: grid;
  min-width: 0;
  gap: 16px;
}

.nodes-panel-header,
.tab-panel-heading,
.all-nodes-toolbar,
.transfer-heading,
.transfer-pane-header,
.group-card,
.group-card-main,
.group-card-actions,
.transfer-node,
.transfer-node-info,
.editor-footer {
  display: flex;
  align-items: center;
}

.nodes-panel-header,
.tab-panel-heading,
.all-nodes-toolbar,
.transfer-heading {
  justify-content: space-between;
  gap: 16px;
}

.nodes-panel-header h3,
.tab-panel-heading h4,
.all-nodes-toolbar h4,
.transfer-heading h4 {
  margin: 0;
  color: var(--el-text-color-primary);
}

.nodes-panel-description,
.tab-panel-heading p,
.all-nodes-toolbar p,
.transfer-heading p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--el-color-primary);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.13em;
}

.nodes-segmented-tabs {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 4px;
  border: 1px solid var(--el-border-color);
  border-radius: 13px;
  background: var(--el-fill-color-light);
  isolation: isolate;
}

.nodes-tab-indicator {
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 4px;
  z-index: 0;
  width: calc((100% - 8px) / 2);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
  transition: transform 240ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.nodes-segmented-tabs button {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 7px 12px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font: inherit;
  cursor: pointer;
}

.nodes-segmented-tabs button.active {
  color: var(--el-color-primary);
  font-weight: 700;
}

.nodes-segmented-tabs button b {
  min-width: 22px;
  padding: 2px 6px;
  border-radius: 99px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 10px;
  font-weight: 650;
}

.nodes-segmented-tabs button:focus-visible,
.nodes-tab-panel:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.nodes-tab-panel {
  display: grid;
  min-width: 0;
  gap: 14px;
}

.group-list {
  display: grid;
  gap: 10px;
}

.group-card {
  min-width: 0;
  justify-content: space-between;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 13px;
  background: var(--el-bg-color);
}

.group-card-main {
  min-width: 0;
  align-items: flex-start;
  gap: 12px;
}

.group-mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--el-color-primary) 20%, transparent);
  border-radius: 11px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 21px;
}

.group-card-content {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.group-card-content > strong {
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.group-card-details {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 18px;
}

.group-card-details span {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.group-card-details small,
.group-card-details b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-card-details small,
.node-health-cell small,
.transfer-pane-header small,
.transfer-node-info small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.5;
}

.group-card-details b {
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-weight: 550;
}

.group-card-actions {
  flex: 0 0 auto;
  gap: 7px;
}

.all-nodes-toolbar {
  align-items: flex-end;
}

.node-search {
  width: min(330px, 48%);
}

.all-nodes-table {
  width: 100%;
}

.node-health-cell {
  display: grid;
  justify-items: start;
  gap: 4px;
}

.transfer-heading {
  align-items: flex-end;
  margin-top: 2px;
}

.transfer-heading > span {
  flex: 0 0 auto;
  padding: 5px 9px;
  border-radius: 99px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.full-width {
  width: 100%;
}

.node-transfer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: stretch;
  gap: 12px;
}

.transfer-pane {
  display: grid;
  min-width: 0;
  grid-template-rows: auto 1fr;
  gap: 9px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 13px;
  background: var(--el-fill-color-lighter);
}

.available-pane {
  grid-template-rows: auto auto minmax(0, 1fr);
}

.transfer-pane-header {
  justify-content: space-between;
  gap: 8px;
}

.transfer-pane-header > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.transfer-pane-header strong {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.transfer-list {
  min-width: 0;
  max-height: min(42vh, 360px);
  min-height: 180px;
  overflow: auto;
  overscroll-behavior: contain;
  padding: 3px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.transfer-node {
  min-width: 0;
  gap: 8px;
  padding: 8px 7px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.transfer-node:last-of-type {
  border-bottom: 0;
}

.transfer-node-info {
  flex: 1;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.transfer-node-info strong {
  max-width: 100%;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transfer-node-info small {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-order {
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  place-items: center;
  border-radius: 7px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.node-order-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 1px;
}

.node-order-actions :deep(.el-button) {
  min-width: 25px;
  height: 26px;
  padding: 0 5px;
}

.transfer-controls {
  display: grid;
  align-content: center;
  gap: 8px;
}

.transfer-controls :deep(.el-button) {
  margin: 0;
}

.editor-footer {
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 720px) {
  .node-transfer {
    grid-template-columns: minmax(0, 1fr);
  }

  .transfer-list {
    max-height: 260px;
  }

  .transfer-controls {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .transfer-controls :deep(.el-button) {
    width: 100%;
    margin: 0;
  }
}

@media (max-width: 560px) {
  .nodes-panel-header,
  .tab-panel-heading,
  .all-nodes-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .nodes-panel-header,
  .tab-panel-heading,
  .all-nodes-toolbar,
  .transfer-heading {
    gap: 10px;
  }

  .tab-panel-heading :deep(.el-button),
  .nodes-panel-header :deep(.el-button) {
    align-self: flex-start;
  }

  .node-search {
    width: 100%;
  }

  .group-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .group-card-main {
    width: 100%;
  }

  .group-card-details {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .group-card-details span:last-child {
    grid-column: 1 / -1;
  }

  .group-card-actions {
    align-self: flex-end;
  }

  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .transfer-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .nodes-tab-indicator {
    transition: none;
  }
}
</style>
