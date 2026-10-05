<template>
  <section v-loading="loading" class="nodes-panel">
    <div class="toolbar">
      <el-input v-model="search" placeholder="搜索全部节点名称或协议" clearable />
      <el-button @click="load">刷新节点</el-button>
      <el-button type="primary" @click="newGroup">新增备用节点组</el-button>
    </div>
    <el-alert
      title="备用节点组保存一组指定节点。请选择 Mihomo 中已有的 Selector 受控组，并填写对应代理入口；多个执行器独立切换时需使用不同受控组。"
      type="info"
      :closable="false"
    />
    <div class="group-list">
      <article v-for="group in groups" :key="group.id" class="group-card">
        <div>
          <strong>{{ group.name }}</strong>
          <small>{{ group.controlGroup }} · {{ group.nodes.length }} 个节点 · {{ group.proxyUrl }}</small>
        </div>
        <el-button size="small" @click="editGroup(group)">编辑</el-button>
        <el-button size="small" type="danger" plain @click="removeGroup(group)">删除</el-button>
      </article>
      <el-empty v-if="!groups.length && !editing" description="尚未创建备用节点组" :image-size="48" />
    </div>
    <el-form v-if="editing" label-position="top" class="group-editor">
      <div class="form-grid">
        <el-form-item label="备用节点组名称">
          <el-input v-model="draft.name" maxlength="100" placeholder="例如：香港与日本备用组" />
        </el-form-item>
        <el-form-item label="Mihomo 受控 Selector 组">
          <el-select v-model="draft.controlGroup" filterable placeholder="选择已有受控组">
            <el-option v-for="group in catalog.groups" :key="group.name" :value="group.name" :label="group.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="此节点组对应的 HTTP 代理入口">
          <el-input v-model="draft.proxyUrl" placeholder="http://mihomo:7895" />
        </el-form-item>
      </div>
      <p class="hint">下表展示全部具体节点，只有所选受控组内的节点可勾选。下方排列决定顺序轮换次序。</p>
      <div v-for="(name, index) in draft.nodes" :key="name" class="selected-row">
        <span>{{ index + 1 }} · {{ name }}</span>
        <el-button size="small" :disabled="index === 0" @click="move(index, -1)">上移</el-button>
        <el-button size="small" :disabled="index === draft.nodes.length - 1" @click="move(index, 1)">下移</el-button>
        <el-button size="small" @click="toggle(name)">移除</el-button>
      </div>
      <div class="editor-actions">
        <el-button @click="editing = false">取消编辑</el-button>
        <el-button type="primary" :loading="saving" @click="saveGroup">保存备用节点组</el-button>
      </div>
    </el-form>
    <el-table :data="visibleNodes" max-height="420" empty-text="控制器尚未返回具体节点">
      <el-table-column v-if="editing" label="选择" width="65">
        <template #default="{ row }">
          <el-checkbox
            :model-value="draft.nodes.includes(row.name)"
            :disabled="!members.includes(row.name)"
            :aria-label="`选择 ${row.name}`"
            @change="toggle(row.name)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="全部具体节点" min-width="220" />
      <el-table-column prop="type" label="协议" width="95" />
      <el-table-column label="检测结果" width="155">
        <template #default="{ row }">
          {{ row.alive === false ? '检测失败' : row.delay != null ? `${row.delay} ms` : '未检测' }}
          <small v-if="row.checkedAt">{{ new Date(row.checkedAt).toLocaleString() }}</small>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="85">
        <template #default="{ row }">
          <el-button size="small" :disabled="!!testing" :loading="testing === row.name" @click="testNode(row.name)">检测</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { proxySettingsApi, type SystemProxyConfig, type MihomoNodeGroup, type MihomoNodeGroupPayload } from '@/utils/proxySettings'
import type { MihomoCatalog } from '@/utils/crawler'
import { confirm, message } from '@/utils/message'

const props = defineProps<{ proxy:SystemProxyConfig }>()
const loading = ref(false)
const saving = ref(false)
const testing = ref('')
const search = ref('')
const groups = ref<MihomoNodeGroup[]>([])
const catalog = ref<MihomoCatalog>({ groups:[], nodes:[] })
const editing = ref(false)
const editingId = ref<number>()
const draft = ref<MihomoNodeGroupPayload>({ name:'', controlGroup:'', proxyUrl:props.proxy.url, nodes:[] })
const members = computed(() => catalog.value.groups.find(g => g.name === draft.value.controlGroup)?.nodes || [])
const visibleNodes = computed(() => catalog.value.nodes.filter(node =>
  `${node.name} ${node.type}`.toLocaleLowerCase().includes(search.value.toLocaleLowerCase())))
const error = (e:any) => message.error(e.response?.data?.message || 'Mihomo 操作失败')

async function load() {
  loading.value = true
  try {
    const [nodes, savedGroups] = await Promise.all([
      proxySettingsApi.mihomoCatalog(props.proxy.id, { ...props.proxy, secret:'', clearSecret:false }),
      proxySettingsApi.mihomoGroups(props.proxy.id),
    ])
    catalog.value = nodes
    groups.value = savedGroups
  } catch (e) {
    error(e)
  } finally {
    loading.value = false
  }
}

function newGroup() {
  editingId.value = undefined
  draft.value = { name:'', controlGroup:'', proxyUrl:props.proxy.url, nodes:[] }
  editing.value = true
}

function editGroup(group:MihomoNodeGroup) {
  editingId.value = group.id
  draft.value = { name:group.name, controlGroup:group.controlGroup, proxyUrl:group.proxyUrl, nodes:[...group.nodes] }
  editing.value = true
}

function toggle(name:string) {
  draft.value.nodes = draft.value.nodes.includes(name)
    ? draft.value.nodes.filter(n => n !== name) : [...draft.value.nodes, name]
}

function move(index:number, direction:number) {
  const nodes = [...draft.value.nodes]
  const target = index + direction
  ;[nodes[index], nodes[target]] = [nodes[target], nodes[index]]
  draft.value.nodes = nodes
}

async function saveGroup() {
  saving.value = true
  try {
    if (editingId.value) await proxySettingsApi.updateMihomoGroup(props.proxy.id, editingId.value, draft.value)
    else await proxySettingsApi.createMihomoGroup(props.proxy.id, draft.value)
    editing.value = false
    message.success('备用节点组已保存')
    await load()
  } catch (e) {
    error(e)
  } finally {
    saving.value = false
  }
}

async function removeGroup(group:MihomoNodeGroup) {
  if (!await confirm(`删除备用节点组“${group.name}”？`)) return
  try {
    await proxySettingsApi.deleteMihomoGroup(props.proxy.id, group.id)
    if (editingId.value === group.id) editing.value = false
    await load()
  } catch (e) {
    error(e)
  }
}

async function testNode(name:string) {
  testing.value = name
  try {
    const result = await proxySettingsApi.mihomoDelay(props.proxy.id, name)
    const node = catalog.value.nodes.find(n => n.name === name)
    if (node) Object.assign(node, result)
  } catch (e) {
    error(e)
  } finally {
    testing.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.nodes-panel {
  display: grid;
  gap: 16px;
}
.toolbar, .group-card, .selected-row, .editor-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.toolbar .el-input, .group-card > div, .selected-row span {
  flex: 1;
  min-width: 0;
}
.group-card, .group-editor {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-light);
}
.group-list {
  display: grid;
  gap: 10px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.selected-row {
  padding: 8px 0;
}
.selected-row span {
  overflow-wrap: anywhere;
}
.editor-actions {
  justify-content: flex-end;
  margin-top: 12px;
}
small, .hint {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}
@media (max-width: 640px) {
  .toolbar, .group-card, .selected-row {
    flex-wrap: wrap;
  }
  .toolbar .el-input, .group-card > div, .selected-row span {
    flex-basis: 100%;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
