<template>
  <div v-loading="loading" class="mihomo-panel">
    <el-alert
      title="请使用爬虫专用代理组与代理入口。切换共享组会影响其他应用；同一组只允许一个执行器管理。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-form label-position="top" class="connection-form" @submit.prevent>
      <el-form-item label="控制 API 地址">
        <el-input v-model="form.controllerUrl" placeholder="http://mihomo:9111" />
      </el-form-item>
      <el-form-item label="API 密钥">
        <el-input
          v-model="form.secret"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="status?.secretConfigured ? '已配置，留空保留原密钥' : '填写控制器密钥'"
        />
        <el-checkbox v-model="form.clearSecret">清除已保存密钥</el-checkbox>
      </el-form-item>
      <el-form-item label="爬虫 HTTP 代理地址">
        <el-input v-model="form.proxyUrl" placeholder="http://mihomo:7895" />
      </el-form-item>
      <el-form-item label="受控 Selector 代理组">
        <el-select v-model="form.groupName" filterable placeholder="连接后选择代理组">
          <el-option v-for="group in catalog.groups" :key="group.name" :label="group.name" :value="group.name" />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="toolbar">
      <el-button :loading="connecting" @click="connect">连接并刷新节点</el-button>
      <el-button :disabled="!canOperate || !form.nodes.length || switching" :loading="testing" @click="testSelected">检测已选节点</el-button>
      <el-input v-model="search" clearable placeholder="搜索节点名称或协议" aria-label="搜索节点" />
    </div>
    <p class="hint">按所选顺序轮换，第一个节点为首选。只使用勾选的具体节点；检测与手动切换需先保存配置。</p>
    <el-table :data="visibleNodes" max-height="300" class="node-table" empty-text="连接控制器并选择代理组后浏览节点">
      <el-table-column label="备用" width="65">
        <template #default="{ row }">
          <el-checkbox :model-value="form.nodes.includes(row.name)" :aria-label="`选择 ${row.name}`" @change="toggle(row.name)" />
        </template>
      </el-table-column>
      <el-table-column label="节点" min-width="230">
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag v-if="actualNode === row.name" size="small" type="success">当前</el-tag>
          <small v-if="status?.cooldowns[row.name] && new Date(status.cooldowns[row.name]).getTime() > Date.now()">
            冷却至 {{ time(status.cooldowns[row.name]) }}
          </small>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="协议" width="90" />
      <el-table-column label="检测" width="155">
        <template #default="{ row }">
          <span>{{ row.alive === false ? '检测失败' : row.delay != null ? `${row.delay} ms` : '未检测' }}</span>
          <small v-if="row.checkedAt">{{ time(row.checkedAt) }}</small>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="165">
        <template #default="{ row }">
          <el-button size="small" :disabled="!canOperate || testing || switching" @click="testNode(row.name)">检测</el-button>
          <el-button size="small" :disabled="!canOperate || !form.nodes.includes(row.name) || switching || testing" @click="switchNode(row.name)">切换</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="form.nodes.length" class="selected-nodes">
      <div v-for="(name, index) in form.nodes" :key="name" class="selected-node">
        <span>{{ index + 1 }} · {{ name }}</span>
        <el-button size="small" :disabled="index === 0" @click="move(index, -1)">上移</el-button>
        <el-button size="small" :disabled="index === form.nodes.length - 1" @click="move(index, 1)">下移</el-button>
        <el-button size="small" @click="toggle(name)">移除</el-button>
      </div>
    </div>
    <el-form label-position="top" class="strategy-form">
      <el-form-item label="网络故障自动切换">
        <el-switch v-model="form.failover" />
        <small>网站限制访问与正文解析失败不会触发节点切换。</small>
      </el-form-item>
      <el-form-item label="连续网络失败次数">
        <el-input-number v-model="form.failureThreshold" :min="1" :max="10" />
      </el-form-item>
      <el-form-item label="故障节点冷却（秒）">
        <el-input-number v-model="form.cooldownSeconds" :min="10" :max="604800" />
      </el-form-item>
      <el-form-item label="轮换顺序">
        <el-select v-model="form.randomOrder">
          <el-option label="按所选顺序循环" :value="false" />
          <el-option label="随机，不连续重复当前节点" :value="true" />
        </el-select>
      </el-form-item>
      <el-form-item label="按运行时间轮换（秒，0 关闭）">
        <el-input-number v-model="form.rotationSeconds" :min="0" :max="604800" :step="30" />
        <small>例如 300 秒；仅累计采集请求与限速等待时间，空闲和冻结时暂停。</small>
      </el-form-item>
      <el-form-item label="每成功采集多少章节轮换（0 关闭）">
        <el-input-number v-model="form.rotationChapters" :min="0" :max="1000000" />
      </el-form-item>
      <el-form-item label="每完成多少书籍任务轮换（0 关闭）">
        <el-input-number v-model="form.rotationTasks" :min="0" :max="1000000" />
      </el-form-item>
    </el-form>
    <p class="hint">任一条件满足时在下次请求前轮换，成功切换后重置三个计数。章节排除失败重试和 304；书籍任务排除目录、元数据及单章节任务。</p>
    <div v-if="status" class="runtime">
      <strong>实际节点：{{ actualNode || '尚未确认' }}</strong>
      <span>累计 {{ Math.floor(status.activeMillis / 1000) }} 秒 · {{ status.chapters }} 章 · {{ status.tasks }} 个书籍任务</span>
      <span v-if="status.retryAt">等待至 {{ time(status.retryAt) }}</span>
      <el-alert v-if="status.lastError" :title="status.lastError" type="warning" :closable="false" />
      <el-button size="small" @click="refresh">刷新运行状态</el-button>
    </div>
    <div class="footer-actions">
      <el-button type="primary" :loading="saving" @click="save">保存并启用 Mihomo 模式</el-button>
    </div>
    <details v-if="status?.events.length" class="history">
      <summary>最近切换记录（{{ status.events.length }}）</summary>
      <div v-for="(event, index) in status.events" :key="index" class="event-row">
        <small>{{ time(event.time) }}</small>
        <span>{{ event.from || '未确认' }} → {{ event.to || '未确认' }}</span>
        <span>{{ event.reason }} · {{ event.success ? '成功' : '失败' }}</span>
      </div>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { crawlerApi, type MihomoCatalog, type MihomoPolicyPayload, type MihomoPolicyView } from '@/utils/crawler'

const props = defineProps<{ queueId:number; executorId:number }>()
const emit = defineEmits<{ saved:[] }>()
const loading = ref(false)
const connecting = ref(false)
const saving = ref(false)
const testing = ref(false)
const switching = ref(false)
const saved = ref(false)
const search = ref('')
const status = ref<MihomoPolicyView|null>(null)
const catalog = ref<MihomoCatalog>({ groups:[], nodes:[] })
const form = ref<MihomoPolicyPayload>({
  controllerUrl:'', secret:'', clearSecret:false, proxyUrl:'', groupName:'', nodes:[],
  failover:true, failureThreshold:2, cooldownSeconds:300, rotationSeconds:0,
  rotationChapters:0, rotationTasks:0, randomOrder:false,
})
const canOperate = computed(() => saved.value && status.value
  && form.value.controllerUrl.replace(/\/$/, '') === status.value.controllerUrl
  && form.value.proxyUrl.replace(/\/$/, '') === status.value.proxyUrl
  && form.value.groupName === status.value.groupName
  && JSON.stringify(form.value.nodes) === JSON.stringify(status.value.nodes)
  && !form.value.secret && !form.value.clearSecret)
const actualNode = computed(() => (status.value?.groupName === form.value.groupName ? status.value?.currentNode : null)
  || catalog.value.groups.find(group => group.name === form.value.groupName)?.currentNode)
const visibleNodes = computed(() => {
  const members = catalog.value.groups.find(group => group.name === form.value.groupName)?.nodes || []
  const query = search.value.trim().toLocaleLowerCase()
  return catalog.value.nodes.filter(node => members.includes(node.name)
    && `${node.name} ${node.type}`.toLocaleLowerCase().includes(query))
})
const time = (value:string) => new Date(value).toLocaleString()
const error = (e:any) => ElMessage.error(e.response?.data?.message || e.message || '操作失败')

async function refresh() {
  try {
    status.value = await crawlerApi.mihomoPolicy(props.queueId, props.executorId)
    if (form.value.controllerUrl && form.value.groupName) await connect()
  } catch (e) {
    error(e)
  }
}

onMounted(async () => {
  loading.value = true
  await refresh()
  if (status.value) {
    const value = status.value
    form.value = {
      controllerUrl:value.controllerUrl, secret:'', clearSecret:false, proxyUrl:value.proxyUrl,
      groupName:value.groupName, nodes:[...value.nodes], failover:value.failover,
      failureThreshold:value.failureThreshold, cooldownSeconds:value.cooldownSeconds,
      rotationSeconds:value.rotationSeconds, rotationChapters:value.rotationChapters,
      rotationTasks:value.rotationTasks, randomOrder:value.randomOrder,
    }
    saved.value = true
    await connect()
  }
  loading.value = false
})

async function connect() {
  connecting.value = true
  try {
    catalog.value = await crawlerApi.mihomoCatalog(props.queueId, props.executorId, form.value)
    const current = catalog.value.groups.find(group => group.name === form.value.groupName)?.currentNode
    if (status.value && current) status.value.currentNode = current
  } catch (e) {
    error(e)
  } finally {
    connecting.value = false
  }
}

function toggle(name:string) {
  form.value.nodes = form.value.nodes.includes(name)
    ? form.value.nodes.filter(node => node !== name) : [...form.value.nodes, name]
}

function move(index:number, direction:number) {
  const next = [...form.value.nodes]
  const target = index + direction
  ;[next[index], next[target]] = [next[target], next[index]]
  form.value.nodes = next
}

async function save() {
  saving.value = true
  try {
    status.value = await crawlerApi.saveMihomo(props.queueId, props.executorId, form.value)
    form.value.controllerUrl = status.value.controllerUrl
    form.value.proxyUrl = status.value.proxyUrl
    form.value.secret = ''
    form.value.clearSecret = false
    saved.value = true
    emit('saved')
    ElMessage.success('已保存并启用 Mihomo 托管代理')
  } catch (e) { error(e) }
  finally { saving.value = false }
}

async function testNode(name:string) {
  testing.value = true
  try {
    const result = await crawlerApi.mihomoDelay(props.queueId, props.executorId, name)
    const node = catalog.value.nodes.find(node => node.name === name)
    if (node) Object.assign(node, result)
  } catch (e) { error(e) }
  finally { testing.value = false }
}

async function testSelected() {
  testing.value = true
  try {
    // Sequential checks bound load on the controller and selected nodes.
    for (const name of form.value.nodes) {
      const result = await crawlerApi.mihomoDelay(props.queueId, props.executorId, name)
      const node = catalog.value.nodes.find(node => node.name === name)
      if (node) Object.assign(node, result)
    }
  } catch (e) { error(e) }
  finally { testing.value = false }
}

async function switchNode(name:string) {
  switching.value = true
  try {
    status.value = await crawlerApi.mihomoSwitch(props.queueId, props.executorId, name)
    await connect()
    ElMessage.success('节点已切换')
  } catch (e) { error(e) }
  finally { switching.value = false }
}
</script>

<style scoped>
.mihomo-panel {
  display: grid;
  gap: 16px;
}
.connection-form, .strategy-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.toolbar .el-input {
  flex: 1;
  min-width: 180px;
}
.hint, small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}
small {
  display: block;
}
.hint {
  margin: 0;
}
.node-table .el-tag {
  margin-left: 8px;
}
.selected-nodes {
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}
.selected-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.selected-node span {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.runtime {
  display: grid;
  gap: 8px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 10px;
}
.runtime .el-button {
  justify-self: start;
}
.footer-actions {
  display: flex;
  justify-content: flex-end;
}
.history summary {
  cursor: pointer;
  padding: 10px 0;
}
.event-row {
  display: grid;
  gap: 4px;
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
@media (max-width: 640px) {
  .connection-form, .strategy-form {
    grid-template-columns: 1fr;
  }
  .selected-node {
    flex-wrap: wrap;
  }
  .selected-node span {
    flex-basis: 100%;
  }
}
</style>
