<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${source?.name || ''} · 代理配置`"
    width="760px"
    style="max-width: calc(100vw - 24px)"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p>配置对此来源的自动刮削和试刮削生效，保存在服务端，对所有账户生效。</p>
    <div v-loading="loading">
      <div class="proxy-mode-scroll">
        <div
          class="proxy-mode-switch"
          role="radiogroup"
          aria-label="刮削代理方式"
          @keydown="switchModeByKeyboard"
        >
          <span class="proxy-mode-indicator" :style="indicatorStyle" aria-hidden="true"></span>
          <button
            v-for="option in modes"
            :key="option.value"
            type="button"
            role="radio"
            :aria-checked="form.mode === option.value"
            :tabindex="form.mode === option.value ? 0 : -1"
            :disabled="loading || saving"
            @click="changeMode(option.value)"
          >{{ option.label }}</button>
        </div>
      </div>
      <el-form :disabled="loading || saving" label-position="top" class="proxy-form">
        <el-form-item v-if="form.mode !== 'NONE'" label="系统代理">
          <el-select v-model="form.systemProxyId" filterable placeholder="选择系统中已配置且启用的代理" @change="changeSystemProxy">
            <el-option v-for="proxy in availableProxies" :key="proxy.id" :value="proxy.id" :label="proxy.name" />
          </el-select>
          <small>代理连接和密钥在系统代理设置中管理，此处仅保存引用。</small>
        </el-form-item>
        <template v-if="form.mode === 'MIHOMO'">
          <el-form-item label="节点组">
            <el-select v-model="form.nodeGroupId" :loading="loadingGroups" filterable placeholder="选择当前账户已保存的节点组" @change="form.manualNode = null">
              <el-option v-for="group in groups" :key="group.id" :value="group.id" :label="group.name" />
            </el-select>
          </el-form-item>
          <div v-if="selectedGroup" class="proxy-group-info">
            <strong>{{ selectedGroup.controlGroup }}</strong>
            <p>代理入口：{{ selectedGroup.proxyUrl }}</p>
            <el-tag v-for="node in selectedGroup.nodes" :key="node" size="small">{{ node }}</el-tag>
          </div>
          <el-form-item label="节点切换方式">
            <el-select v-model="form.switchingMode">
              <el-option label="手动指定节点" value="MANUAL" />
              <el-option label="仅在节点网络故障时切换" value="FAILOVER" />
              <el-option label="按实际请求运行时间轮换" value="TIME" />
              <el-option label="按成功请求数轮换" value="REQUEST" />
              <el-option label="按成功来源查询次数轮换" value="TASK" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.switchingMode === 'MANUAL'" label="手动节点">
            <el-select v-model="form.manualNode" filterable placeholder="选择固定使用的节点">
              <el-option v-for="node in selectedGroup?.nodes || []" :key="node" :label="node" :value="node" />
            </el-select>
          </el-form-item>
          <template v-else>
            <el-form-item v-if="form.switchingMode === 'TIME'" label="轮换间隔（秒）">
              <el-input-number v-model="form.rotationSeconds" :min="30" :max="604800" :step="30" />
              <small>只累计 HTTP 请求耗时，不累计空闲或人工确认等待。</small>
            </el-form-item>
            <el-form-item v-if="form.switchingMode === 'REQUEST'" label="每成功请求多少次轮换">
              <el-input-number v-model="form.rotationRequests" :min="1" :max="1000000" />
            </el-form-item>
            <el-form-item v-if="form.switchingMode === 'TASK'" label="每成功来源查询多少次轮换">
              <el-input-number v-model="form.rotationTasks" :min="1" :max="1000000" />
              <small>一次自动来源刮削、候选搜索或候选详情读取成功记一次；保存书籍不计数。</small>
            </el-form-item>
            <el-form-item label="节点选用顺序">
              <el-select v-model="form.randomOrder">
                <el-option label="按节点组顺序循环" :value="false" />
                <el-option label="随机，不立即重复" :value="true" />
              </el-select>
            </el-form-item>
            <el-form-item label="网络故障自动切换">
              <el-switch
                :model-value="form.switchingMode === 'FAILOVER' || form.failover"
                :disabled="form.switchingMode === 'FAILOVER'"
                @change="form.failover = Boolean($event)"
              />
            </el-form-item>
          </template>
          <div class="proxy-thresholds">
            <el-form-item label="连续网络异常阈值">
              <el-input-number v-model="form.failureThreshold" :min="1" :max="10" />
            </el-form-item>
            <el-form-item label="异常节点冻结（秒）">
              <el-input-number v-model="form.cooldownSeconds" :min="10" :max="604800" />
            </el-form-item>
          </div>
          <p>每次失败记一次网络异常，达到阈值后冻结节点。手动模式或关闭故障切换时等待恢复；轮换在下一次请求前执行。状态计数在服务重启或配置更新后重置。</p>
          <p>同一受控 Selector 组只能绑定一个刮削来源或执行器。可在系统代理中创建独立节点组。</p>
        </template>
      </el-form>
      <p v-if="view" class="proxy-runtime">
        当前节点：{{ view.currentNode || '尚未请求' }} · 成功请求 {{ view.requests }} 次 · 来源查询 {{ view.tasks }} 次
        <br />{{ view.lastEvent }}
      </p>
      <p v-for="(until, node) in view?.cooldowns || {}" :key="node" class="proxy-runtime">
        节点 {{ node }} · {{ new Date(until).getTime() > Date.now() ? '冻结至' : '冻结期已结束' }} {{ new Date(until).toLocaleString() }}
      </p>
      <el-button v-if="view" :disabled="loading || saving" @click="refreshRuntime">刷新节点状态</el-button>
      <p v-if="error" role="alert">{{ error }}</p>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="loading || loadingGroups || !!loadError" :loading="saving" @click="save">保存代理配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import api from '@/utils/api'
import type { ScraperStatus } from '@/utils/config'
import { proxySettingsApi, type SystemProxyConfig, type MihomoNodeGroup } from '@/utils/proxySettings'
import { message } from '@/utils/message'

interface ProxySettings {
  mode: 'NONE' | 'FIXED' | 'MIHOMO'
  systemProxyId: number | null
  nodeGroupId: number | null
  switchingMode: 'MANUAL' | 'FAILOVER' | 'TIME' | 'REQUEST' | 'TASK'
  manualNode: string | null
  failover: boolean
  failureThreshold: number
  cooldownSeconds: number
  rotationSeconds: number
  rotationRequests: number
  rotationTasks: number
  randomOrder: boolean
}
interface ProxyView {
  settings: ProxySettings
  currentNode: string | null
  requests: number
  tasks: number
  lastEvent: string
  cooldowns: Record<string, string>
}
const props = defineProps<{ modelValue: boolean; source: ScraperStatus | null }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>()
const defaults = (): ProxySettings => ({
  mode: 'NONE', systemProxyId: null, nodeGroupId: null, switchingMode: 'MANUAL',
  manualNode: null, failover: true, failureThreshold: 2, cooldownSeconds: 300,
  rotationSeconds: 300, rotationRequests: 20, rotationTasks: 1, randomOrder: false,
})
const form = ref(defaults())
const proxies = ref<SystemProxyConfig[]>([])
const groups = ref<MihomoNodeGroup[]>([])
const view = ref<ProxyView | null>(null)
const loading = ref(false)
const loadingGroups = ref(false)
const saving = ref(false)
const error = ref('')
const loadError = ref(false)
let generation = 0
let groupGeneration = 0
const modes = [
  { value: 'NONE' as const, label: '无代理' },
  { value: 'FIXED' as const, label: '固定代理' },
  { value: 'MIHOMO' as const, label: 'Mihomo 代理' },
]
const indicatorStyle = computed(() => ({ transform: `translateX(${modes.findIndex(item => item.value === form.value.mode) * 100}%)` }))
const availableProxies = computed(() => proxies.value.filter(proxy => proxy.enabled
  && proxy.proxyType === (form.value.mode === 'MIHOMO' ? 'MIHOMO' : 'SIMPLE')))
const selectedGroup = computed(() => groups.value.find(group => group.id === form.value.nodeGroupId))
function changeMode(value: ProxySettings['mode']) {
  if (loading.value || saving.value) return
  if (value !== form.value.mode) {
    form.value.mode = value
    form.value.systemProxyId = null
    changeSystemProxy()
  }
}
async function switchModeByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || saving.value || loading.value) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  const index = modes.findIndex(item => item.value === form.value.mode)
  const target = event.key === 'Home' ? 0 : event.key === 'End' ? 2
    : (index + (event.key === 'ArrowRight' ? 1 : 2)) % 3
  changeMode(modes[target].value)
  await nextTick()
  group.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus()
}
async function loadGroups() {
  const request = ++groupGeneration
  groups.value = []
  loadingGroups.value = false
  if (form.value.mode !== 'MIHOMO' || !form.value.systemProxyId) return
  loadingGroups.value = true
  try {
    const data = await proxySettingsApi.mihomoGroups(form.value.systemProxyId)
    if (request === groupGeneration) groups.value = data
  } catch {
    if (request === groupGeneration) error.value = '节点组加载失败，请重新选择系统代理。'
  } finally {
    if (request === groupGeneration) loadingGroups.value = false
  }
}
function changeSystemProxy() {
  form.value.nodeGroupId = null
  form.value.manualNode = null
  void loadGroups()
}
watch(() => [props.modelValue, props.source?.configKey] as const, async ([open, key]) => {
  const request = ++generation
  groupGeneration++
  if (!open || !key) return
  loading.value = true
  loadError.value = false
  error.value = ''
  form.value = defaults()
  view.value = null
  try {
    const [settings, list] = await Promise.all([
      api.get<ProxyView>(`/api/scraper/proxy-settings/${key}`), proxySettingsApi.systemList(),
    ])
    if (request !== generation) return
    view.value = settings.data
    form.value = { ...defaults(), ...settings.data.settings }
    proxies.value = list
    await loadGroups()
  } catch {
    if (request !== generation) return
    loadError.value = true
    error.value = '代理配置加载失败，请关闭后重试。'
  } finally {
    if (request === generation) loading.value = false
  }
})
async function refreshRuntime() {
  if (!props.source || loading.value || saving.value) return
  try {
    const { data } = await api.get<ProxyView>(`/api/scraper/proxy-settings/${props.source.configKey}`)
    view.value = data
  } catch {
    error.value = '节点状态刷新失败，请稍后重试。'
  }
}
async function save() {
  if (!props.source || loading.value || saving.value || loadError.value) return
  if (form.value.mode !== 'NONE' && !form.value.systemProxyId) {
    error.value = '请选择系统代理。'
    return
  }
  if (form.value.mode === 'MIHOMO' && (!selectedGroup.value
      || form.value.switchingMode === 'MANUAL' && !form.value.manualNode)) {
    error.value = '请选择节点组，手动模式还需指定节点。'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const { data } = await api.put<ProxyView>(`/api/scraper/proxy-settings/${props.source.configKey}`, form.value)
    view.value = data
    message.success('此来源代理配置已保存')
  } catch (failure: any) {
    error.value = failure.response?.data?.message || '代理配置保存失败，请检查代理连接和节点组。'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.proxy-mode-scroll {
  overflow-x: auto;
  max-width: 100%;
  margin: 18px 0;
}
.proxy-mode-switch {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(3, minmax(110px, 1fr));
  min-width: 348px;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-hover);
}
.proxy-mode-indicator {
  position: absolute;
  z-index: -1;
  top: 4px;
  bottom: 4px;
  left: 4px;
  width: calc((100% - 8px) / 3);
  border-radius: 10px;
  background: var(--surface-card);
  transition: transform 220ms ease;
}
.proxy-mode-switch button {
  min-height: 38px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.proxy-mode-switch button[aria-checked="true"] {
  color: var(--primary);
  font-weight: 600;
}
.proxy-mode-switch button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.proxy-form :deep(.el-select) {
  width: 100%;
}
.proxy-form small {
  display: block;
  color: var(--text-secondary);
}
.proxy-group-info {
  padding: 12px;
  margin-bottom: 18px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
}
.proxy-group-info :deep(.el-tag) {
  margin: 4px;
}
.proxy-thresholds {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 16px;
}
.proxy-runtime {
  margin-top: 16px;
  color: var(--text-secondary);
}
@media (prefers-reduced-motion: reduce) {
  .proxy-mode-indicator {
    transition: none;
  }
}
</style>
