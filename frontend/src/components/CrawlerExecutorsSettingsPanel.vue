<template>
  <section v-loading="loading" class="executors-panel">
    <header class="panel-heading">
      <div>
        <h3>执行器配置</h3>
        <p>按队列管理执行器，配置普通代理或引用系统 Mihomo 备用节点组。</p>
      </div>
      <el-button type="primary" :disabled="!queueId || executors.length >= 16" @click="create">新增执行器</el-button>
    </header>
    <div class="queue-toolbar">
      <el-select v-model="queueId" filterable placeholder="选择采集队列" @change="loadExecutors">
        <el-option v-for="queue in queues" :key="queue.id" :value="queue.id" :label="queue.queueName || queue.siteName || `队列 ${queue.id}`" />
      </el-select>
      <el-button @click="load">刷新配置</el-button>
    </div>
    <el-table :data="executors" empty-text="请先选择采集队列">
      <el-table-column label="执行器" min-width="180">
        <template #default="{ row }">
          <strong>{{ row.name }}</strong>
          <el-tag v-if="row.defaultExecutor" size="small">默认</el-tag>
          <small>{{ row.description || '暂无描述' }}</small>
        </template>
      </el-table-column>
      <el-table-column label="代理方式" min-width="155">
        <template #default="{ row }">{{ row.proxyMode === 'MIHOMO' ? 'Mihomo 节点组' : row.proxyMode === 'SELECTED' ? '指定普通代理' : '默认代理' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="95">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已启用' : '已停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="edit(row)">配置</el-button>
          <el-button v-if="!row.defaultExecutor" size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editingId ? '配置执行器' : '新增执行器'" width="min(820px, 96vw)" append-to-body destroy-on-close>
      <div v-loading="editorLoading" class="executor-editor">
        <el-form label-position="top">
          <div class="form-grid">
            <el-form-item label="执行器名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
            <el-form-item label="启用"><el-switch v-model="form.enabled" /><small>停用后不接收新任务，已运行任务继续完成。</small></el-form-item>
          </div>
          <el-form-item label="描述"><el-input v-model="form.description" type="textarea" maxlength="500" /></el-form-item>
          <el-form-item label="代理方式">
            <el-select v-model="form.proxyMode">
              <el-option label="默认代理配置" value="DEFAULT" />
              <el-option label="普通代理 · 指定代理列表" value="SELECTED" />
              <el-option label="Mihomo · 系统代理与备用节点组" value="MIHOMO" />
            </el-select>
          </el-form-item>
          <template v-if="form.proxyMode !== 'MIHOMO'">
            <el-form-item label="代理选用顺序">
              <el-select v-model="form.selectionStrategy">
                <el-option label="按顺序" value="ORDERED" />
                <el-option label="随机" value="RANDOM" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.proxyMode === 'DEFAULT'" label="代理冷却（秒，留空使用默认值）">
              <el-input-number v-model="form.defaultProxyCooldownSeconds" :min="10" :max="604800" />
            </el-form-item>
            <template v-else>
              <el-form-item label="选择普通代理">
                <el-select v-model="selectedProxyIds" multiple filterable>
                  <el-option v-for="proxy in proxyOptions" :key="proxy.id" :value="proxy.id" :label="proxy.name" :disabled="!proxy.effectiveEnabled" />
                </el-select>
              </el-form-item>
              <div v-for="(proxy, index) in form.proxies" :key="proxy.proxyConfigId" class="proxy-row">
                <span>{{ proxyOptions.find(p => p.id === proxy.proxyConfigId)?.name || '已删除代理' }}</span>
                <el-input-number v-model="proxy.cooldownSeconds" :min="10" :max="604800" placeholder="冷却秒数" />
                <el-button size="small" :disabled="index === 0" @click="moveProxy(index, -1)">上移</el-button>
                <el-button size="small" :disabled="index === form.proxies.length - 1" @click="moveProxy(index, 1)">下移</el-button>
              </div>
            </template>
          </template>
          <template v-else>
            <el-alert v-if="policy && !policy.systemProxyId" title="此执行器使用旧版独立 Mihomo 配置，继续有效。迁移后在系统代理管理连接和节点组。" type="info" :closable="false" />
            <el-button v-if="policy && !policy.systemProxyId" :loading="saving" @click="migrate">迁移到系统代理配置</el-button>
            <div class="form-grid">
              <el-form-item label="Mihomo 系统代理">
                <el-select v-model="reference.systemProxyId" filterable placeholder="请先在系统代理配置新增 Mihomo 代理" @change="changeSystem">
                  <el-option v-for="proxy in systems" :key="proxy.id" :value="proxy.id" :label="proxy.name" :disabled="!proxy.enabled" />
                </el-select>
              </el-form-item>
              <el-form-item label="备用节点组">
                <el-select v-model="reference.nodeGroupId" filterable placeholder="选择已保存的节点组" @change="reference.manualNode = null">
                  <el-option v-for="group in groups" :key="group.id" :value="group.id" :label="`${group.name} · ${group.nodes.length} 个节点`" />
                </el-select>
              </el-form-item>
            </div>
            <div v-if="selectedGroup" class="group-preview">
              <strong>{{ selectedGroup.name }}</strong>
              <small>{{ selectedGroup.controlGroup }} · {{ selectedGroup.proxyUrl }}</small>
              <div><el-tag v-for="node in selectedGroup.nodes" :key="node" size="small">{{ node }}</el-tag></div>
            </div>
            <el-form-item label="节点切换方式">
              <el-select v-model="reference.switchingMode">
                <el-option label="手动指定节点" value="MANUAL" />
                <el-option label="仅在节点故障时自动切换" value="FAILOVER" />
                <el-option label="按运行时间轮换" value="TIME" />
                <el-option label="按成功章节数轮换" value="CHAPTER" />
                <el-option label="按成功书籍任务数轮换" value="TASK" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="reference.switchingMode === 'MANUAL'" label="手动指定节点">
              <el-select v-model="reference.manualNode" filterable>
                <el-option v-for="node in selectedGroup?.nodes || []" :key="node" :value="node" :label="node" />
              </el-select>
              <small>保存后采集请求固定使用此节点，失败时等待，不自动换到其他节点。</small>
            </el-form-item>
            <template v-else>
              <el-form-item v-if="reference.switchingMode === 'TIME'" label="轮换间隔（秒）">
                <el-input-number v-model="reference.rotationSeconds" :min="30" :max="604800" :step="30" />
                <small>300 秒为 5 分钟。仅累计请求及请求内限速等待，空闲、冻结时暂停。</small>
              </el-form-item>
              <el-form-item v-if="reference.switchingMode === 'CHAPTER'" label="每成功采集多少章节轮换">
                <el-input-number v-model="reference.rotationChapters" :min="1" :max="1000000" />
              </el-form-item>
              <el-form-item v-if="reference.switchingMode === 'TASK'" label="每成功完成多少书籍任务轮换">
                <el-input-number v-model="reference.rotationTasks" :min="1" :max="1000000" />
              </el-form-item>
              <div class="form-grid">
                <el-form-item label="节点选用顺序">
                  <el-select v-model="reference.randomOrder">
                    <el-option label="按节点组顺序循环" :value="false" />
                    <el-option label="随机，不立即重复" :value="true" />
                  </el-select>
                </el-form-item>
                <el-form-item label="节点故障自动切换">
                  <el-switch
                    :model-value="reference.switchingMode === 'FAILOVER' || reference.failover"
                    :disabled="reference.switchingMode === 'FAILOVER'"
                    @change="reference.failover = Boolean($event)"
                  />
                </el-form-item>
                <el-form-item label="连续网络失败次数"><el-input-number v-model="reference.failureThreshold" :min="1" :max="10" /></el-form-item>
                <el-form-item label="故障节点冷却（秒）"><el-input-number v-model="reference.cooldownSeconds" :min="10" :max="604800" /></el-form-item>
              </div>
            </template>
            <p class="hint">切换只使用所选节点组。网站限制访问进入冷却，正文失败不触发换节点；同一受控组只由一个执行器管理。</p>
          </template>
        </el-form>
        <div v-if="policy && form.proxyMode === 'MIHOMO'" class="runtime">
          <strong>最近确认节点：{{ policy.currentNode || '尚未确认' }}</strong>
          <small>{{ Math.floor(policy.activeMillis / 1000) }} 秒 · {{ policy.chapters }} 章 · {{ policy.tasks }} 个任务</small>
          <el-alert v-if="policy.lastError" :title="policy.lastError" type="warning" :closable="false" />
          <el-button size="small" @click="refreshPolicy">刷新状态</el-button>
          <details v-if="policy.events.length">
            <summary>最近切换记录</summary>
            <div v-for="(event, index) in policy.events" :key="index" class="event-row">
              <small>{{ new Date(event.time).toLocaleString() }} · {{ event.reason }} · {{ event.success ? '成功' : '失败' }}</small>
              {{ event.from || '未确认' }} → {{ event.to || '未确认' }}
            </div>
          </details>
        </div>
      </div>
      <template #footer>
        <el-button v-if="policy?.systemProxyId" type="warning" plain :disabled="saving" @click="unbind">解除 Mihomo 引用</el-button>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="editorLoading" @click="save">保存执行器配置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { crawlerApi, type CrawlerTaskQueue, type CrawlerQueueExecutor, type CrawlerQueueExecutorPayload, type CrawlerQueueProxyOption, type MihomoPolicyView, type MihomoReferencePayload } from '@/utils/crawler'
import { proxySettingsApi, type SystemProxyConfig, type MihomoNodeGroup } from '@/utils/proxySettings'
import { confirm, message } from '@/utils/message'

const route = useRoute()
const queues = ref<CrawlerTaskQueue[]>([])
const queueId = ref<number>()
const executors = ref<CrawlerQueueExecutor[]>([])
const systems = ref<SystemProxyConfig[]>([])
const proxyOptions = ref<CrawlerQueueProxyOption[]>([])
const groups = ref<MihomoNodeGroup[]>([])
const loading = ref(false)
const editorLoading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const editingId = ref<number>()
const policy = ref<MihomoPolicyView|null>(null)
const emptyForm = ():CrawlerQueueExecutorPayload => ({
  name:'', description:'', enabled:true, proxyMode:'DEFAULT', selectionStrategy:'ORDERED',
  defaultProxyCooldownSeconds:null, proxies:[],
})
const emptyReference = ():MihomoReferencePayload => ({
  systemProxyId:null, nodeGroupId:null, switchingMode:'MANUAL', manualNode:null,
  failover:true, failureThreshold:2, cooldownSeconds:300, rotationSeconds:300,
  rotationChapters:50, rotationTasks:1, randomOrder:false,
})
const form = ref(emptyForm())
const reference = ref(emptyReference())
const selectedGroup = computed(() => groups.value.find(g => g.id === reference.value.nodeGroupId))
const selectedProxyIds = computed({
  get: () => form.value.proxies.map(p => p.proxyConfigId),
  set: (ids:number[]) => {
    const current = new Map(form.value.proxies.map(p => [p.proxyConfigId, p]))
    form.value.proxies = ids.map(id => current.get(id) || { proxyConfigId:id, cooldownSeconds:null })
  },
})
const error = (e:any) => message.error(e.response?.data?.message || '执行器配置操作失败')

async function loadExecutors() {
  if (!queueId.value) return
  loading.value = true
  try {
    executors.value = await crawlerApi.queueExecutors(queueId.value)
  } catch (e) {
    error(e)
  } finally {
    loading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [availableQueues, availableSystems, proxies] = await Promise.all([
      crawlerApi.taskQueues(), proxySettingsApi.systemList(), crawlerApi.queueProxyOptions(),
    ])
    queues.value = availableQueues
    systems.value = availableSystems.filter(p => p.proxyType === 'MIHOMO')
    proxyOptions.value = proxies
    const requested = Number(route.query.queueId)
    if (!queueId.value) queueId.value = availableQueues.find(q => q.id === requested)?.id || availableQueues[0]?.id
    await loadExecutors()
  } catch (e) {
    error(e)
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  groups.value = reference.value.systemProxyId ? await proxySettingsApi.mihomoGroups(reference.value.systemProxyId) : []
}

async function changeSystem() {
  reference.value.nodeGroupId = null
  reference.value.manualNode = null
  try {
    await loadGroups()
  } catch (e) {
    error(e)
  }
}

function create() {
  editingId.value = undefined
  form.value = emptyForm()
  form.value.name = `执行器 ${executors.value.length + 1}`
  reference.value = emptyReference()
  groups.value = []
  policy.value = null
  dialog.value = true
}

function applyPolicy(value:MihomoPolicyView) {
  policy.value = value
  reference.value = {
    systemProxyId:value.systemProxyId, nodeGroupId:value.nodeGroupId,
    switchingMode:value.switchingMode || 'MANUAL', manualNode:value.manualNode || value.currentNode,
    failover:value.failover, failureThreshold:value.failureThreshold, cooldownSeconds:value.cooldownSeconds,
    rotationSeconds:value.rotationSeconds || 300, rotationChapters:value.rotationChapters || 50,
    rotationTasks:value.rotationTasks || 1, randomOrder:value.randomOrder,
  }
}

async function edit(item:CrawlerQueueExecutor) {
  editingId.value = item.id
  form.value = {
    name:item.name, description:item.description || '', enabled:item.enabled,
    proxyMode:item.proxyMode, selectionStrategy:item.selectionStrategy,
    defaultProxyCooldownSeconds:item.defaultProxyCooldownSeconds,
    proxies:item.proxies.map(p => ({ proxyConfigId:p.proxyConfigId, cooldownSeconds:p.cooldownSeconds })),
  }
  reference.value = emptyReference()
  groups.value = []
  policy.value = null
  dialog.value = true
  editorLoading.value = true
  try {
    const value = await crawlerApi.mihomoPolicy(queueId.value!, item.id)
    if (value) {
      applyPolicy(value)
      await loadGroups()
    }
  } catch (e) {
    error(e)
  } finally {
    editorLoading.value = false
  }
}

function moveProxy(index:number, direction:number) {
  const next = [...form.value.proxies]
  const target = index + direction
  ;[next[index], next[target]] = [next[target], next[index]]
  form.value.proxies = next
}

async function refreshPolicy() {
  if (!queueId.value || !editingId.value) return
  try {
    policy.value = await crawlerApi.mihomoPolicy(queueId.value, editingId.value)
  } catch (e) {
    error(e)
  }
}

async function migrate() {
  if (!queueId.value || !editingId.value) return
  saving.value = true
  try {
    applyPolicy(await crawlerApi.migrateMihomo(queueId.value, editingId.value))
    systems.value = (await proxySettingsApi.systemList()).filter(p => p.proxyType === 'MIHOMO')
    await loadGroups()
    message.success('已迁移，连接和备用组现在由系统代理管理')
  } catch (e) {
    error(e)
  } finally {
    saving.value = false
  }
}

async function save() {
  if (!queueId.value || !form.value.name.trim()) return message.error('请选择队列并填写执行器名称')
  if (form.value.proxyMode === 'SELECTED' && !form.value.proxies.length) return message.error('请至少选择一个普通代理')
  if (form.value.proxyMode === 'MIHOMO' && (!reference.value.systemProxyId || !reference.value.nodeGroupId)) return message.error('请选择 Mihomo 系统代理与备用节点组，旧配置可先迁移')
  saving.value = true
  try {
    const isMihomo = form.value.proxyMode === 'MIHOMO'
    const payload = { ...form.value, proxies:form.value.proxyMode === 'SELECTED' ? form.value.proxies : [] }
    // Save references before changing an existing executor's running mode.
    if (!editingId.value) {
      const created = await crawlerApi.createQueueExecutor(queueId.value, { ...payload, enabled:isMihomo ? false : form.value.enabled })
      editingId.value = created.id
    }
    if (isMihomo) applyPolicy(await crawlerApi.saveMihomoSelection(queueId.value, editingId.value, reference.value))
    await crawlerApi.updateQueueExecutor(queueId.value, editingId.value, payload)
    message.success('执行器配置已保存')
    dialog.value = false
  } catch (e) {
    error(e)
  } finally {
    saving.value = false
    await loadExecutors()
  }
}

async function unbind() {
  if (!queueId.value || !editingId.value
    || !await confirm('解除 Mihomo 引用后，此执行器改为默认代理。系统代理及备用节点组会保留。')) return
  saving.value = true
  try {
    await crawlerApi.unbindMihomo(queueId.value, editingId.value)
    policy.value = null
    reference.value = emptyReference()
    groups.value = []
    form.value.proxyMode = 'DEFAULT'
    await loadExecutors()
    message.success('已解除 Mihomo 引用')
  } catch (e) {
    error(e)
  } finally {
    saving.value = false
  }
}

async function remove(item:CrawlerQueueExecutor) {
  if (!queueId.value || !await confirm(`删除执行器“${item.name}”？`)) return
  try {
    await crawlerApi.deleteQueueExecutor(queueId.value, item.id)
    await loadExecutors()
  } catch (e) {
    error(e)
  }
}

onMounted(async () => {
  await load()
  const requested = executors.value.find(e => e.id === Number(route.query.executorId))
  if (requested) await edit(requested)
  else if (route.query.newExecutor === '1') create()
})
</script>

<style scoped>
.executors-panel {
  padding: 24px;
}
.panel-heading, .queue-toolbar, .proxy-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.panel-heading {
  justify-content: space-between;
  margin-bottom: 20px;
}
.panel-heading h3 {
  margin: 0;
}
.panel-heading p, small, .hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}
small {
  display: block;
}
.queue-toolbar {
  margin-bottom: 18px;
}
.queue-toolbar .el-select {
  width: min(420px, 100%);
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}
.group-preview, .runtime {
  display: grid;
  gap: 8px;
  padding: 16px;
  margin: 12px 0 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-light);
}
.group-preview .el-tag {
  margin: 4px;
}
.runtime > .el-button {
  justify-self: start;
}
.proxy-row {
  padding: 8px 0;
}
.proxy-row span {
  flex: 1;
}
.event-row {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .panel-heading, .queue-toolbar, .proxy-row {
    flex-wrap: wrap;
  }
}
</style>
