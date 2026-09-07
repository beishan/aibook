<template>
  <section class="proxy-settings-shell card glass">
    <header class="proxy-hero">
      <div class="proxy-hero-mark" aria-hidden="true">⌁</div>
      <div>
        <p class="proxy-kicker">{{ crawler ? 'CRAWLER ROUTING' : 'NETWORK ROUTING' }}</p>
        <h2>{{ crawler ? '爬虫设置' : '系统代理配置' }}</h2>
        <p>{{ crawler ? '集中管理爬取请求策略、请求身份与代理路由。' : '维护可被系统功能复用的代理资源；允许多条配置同时启用。' }}</p>
      </div>
      <el-button v-if="!crawler" type="primary" @click="openCreate">＋ 新增代理</el-button>
    </header>

    <div v-if="crawler" class="crawler-subtabs-scroll">
      <div class="crawler-subtabs" role="tablist" aria-label="爬虫子配置">
        <span class="crawler-subtab-indicator" :style="crawlerSubtabIndicatorStyle" aria-hidden="true"></span>
        <button
          v-for="(tab, index) in crawlerSubtabs"
          :id="`crawler-settings-tab-${tab.key}`"
          :key="tab.key"
          type="button"
          role="tab"
          class="crawler-subtab"
          :class="{ active: activeCrawlerSubtab === tab.key }"
          :aria-selected="activeCrawlerSubtab === tab.key"
          :aria-controls="`crawler-settings-panel-${tab.key}`"
          :tabindex="activeCrawlerSubtab === tab.key ? 0 : -1"
          @click="activeCrawlerSubtab = tab.key"
          @keydown="handleCrawlerSubtabKeydown(index, $event)"
        ><strong>{{ tab.label }}</strong><small>{{ tab.description }}</small></button>
      </div>
    </div>

    <div v-if="crawler && activeCrawlerSubtab === 'proxy'" class="routing-note">
      <span class="routing-note-icon">i</span>
      <div><strong>引用状态实时联动</strong><p>引用的系统代理被停用或删除后，此处会立即变为不可用且不会参与请求。</p></div>
    </div>

    <section
      v-if="crawler && activeCrawlerSubtab === 'request'"
      id="crawler-settings-panel-request"
      v-loading="settingsLoading"
      class="crawler-request-card"
      role="tabpanel"
      aria-labelledby="crawler-settings-tab-request"
    >
      <header class="request-card-header">
        <div><p class="proxy-kicker">REQUEST POLICY</p><h3>请求与失败策略</h3><span>以下配置对全部书籍爬虫任务生效。</span></div>
        <el-button type="primary" :loading="settingsSaving" @click="saveCrawlerSettings">保存请求设置</el-button>
      </header>
      <el-form label-position="top" class="crawler-request-form">
        <div class="request-number-grid">
          <el-form-item label="单次请求超时（ms）"><el-input-number v-model="requestSettings.timeoutMillis" :min="1000" :max="120000" :step="1000" controls-position="right" /></el-form-item>
          <el-form-item label="单次请求失败重试"><el-input-number v-model="requestSettings.retryCount" :min="0" :max="8" controls-position="right" /></el-form-item>
          <el-form-item label="任务连续请求失败上限"><el-input-number v-model="requestSettings.maxConsecutiveFailures" :min="1" :max="100" controls-position="right" /><small>达到上限后停止整项任务；任一请求成功后重新计数。</small></el-form-item>
        </div>
      </el-form>
    </section>

    <section
      v-if="crawler && activeCrawlerSubtab === 'identity'"
      id="crawler-settings-panel-identity"
      v-loading="settingsLoading"
      class="crawler-request-card"
      role="tabpanel"
      aria-labelledby="crawler-settings-tab-identity"
    >
      <header class="request-card-header">
        <div><p class="proxy-kicker">REQUEST IDENTITY</p><h3>请求身份与 Header</h3><span>以下身份信息会应用到全部书籍爬虫请求。</span></div>
        <el-button type="primary" :loading="settingsSaving" @click="saveCrawlerSettings">保存身份设置</el-button>
      </header>
      <el-form label-position="top" class="crawler-request-form">
        <el-form-item label="User-Agent"><el-input v-model="requestSettings.userAgent" maxlength="500" placeholder="留空时使用合规的 AiBookCrawler 标识" /></el-form-item>
        <el-form-item label="Cookie"><el-input v-model="requestSettings.cookie" type="password" show-password autocomplete="off" maxlength="20000" placeholder="选填，对所有采集网站发送" /></el-form-item>
        <el-form-item label="自定义 Header JSON"><el-input v-model="requestSettings.headersJson" type="textarea" :rows="4" maxlength="20000" placeholder='{"Referer":"https://example.com/"}' /><small>必须是字符串键值对 JSON；Host、Content-Length、Connection、Authorization 和 Cookie 不会从这里覆盖。</small></el-form-item>
      </el-form>
    </section>

    <div v-if="crawler && activeCrawlerSubtab === 'proxy'" class="proxy-section-title">
      <div><p class="proxy-kicker">PROXY ROUTING</p><h3>代理配置</h3><span>拖动条目调整顺序，越靠上越先使用</span></div>
      <el-button type="primary" @click="openCreate">＋ 新增代理</el-button>
    </div>

    <el-table v-if="!crawler || activeCrawlerSubtab === 'proxy'" :id="crawler ? 'crawler-settings-panel-proxy' : undefined" v-loading="loading || reorderSaving" :data="rows" row-key="id" class="proxy-table" empty-text="暂无代理配置" :role="crawler ? 'tabpanel' : undefined" :aria-labelledby="crawler ? 'crawler-settings-tab-proxy' : undefined">
      <el-table-column label="排序" width="64" align="center">
        <template #default="{ row }">
          <button
            type="button"
            class="proxy-drag-handle"
            :class="{ dragging: draggingId === row.id }"
            :draggable="!reorderSaving"
            :aria-label="`拖动排序：${row.name}`"
            title="拖动调整优先级；也可使用上下方向键"
            @dragstart="startProxyDrag(row, $event)"
            @dragenter.prevent="moveDraggedProxy(row)"
            @dragover.prevent
            @dragend="finishProxyDrag"
            @keydown.up.prevent="moveProxyByKeyboard(row, -1)"
            @keydown.down.prevent="moveProxyByKeyboard(row, 1)"
          >⠿</button>
        </template>
      </el-table-column>
      <el-table-column label="代理" min-width="210">
        <template #default="{ row }">
          <div class="proxy-identity" @dragenter.prevent="moveDraggedProxy(row)"><strong>{{ row.name || '引用已失效' }}</strong><small>{{ row.url || '系统代理已被删除' }}</small></div>
        </template>
      </el-table-column>
      <el-table-column v-if="crawler" label="来源" width="130">
        <template #default="{ row }"><span class="source-chip" :class="row.sourceType.toLowerCase()">{{ row.sourceType === 'SYSTEM' ? '系统引用' : '爬虫独立' }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <span class="health-chip" :class="effective(row) ? 'online' : 'offline'"><i></i>{{ statusText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" align="right">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" :disabled="crawler && row.sourceType === 'SYSTEM' && !row.sourceAvailable" @change="toggle(row, Boolean($event))" />
          <el-button text @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <footer v-if="!crawler || activeCrawlerSubtab === 'proxy'" class="proxy-footer"><span>拖动条目调整顺序，越靠上优先级越高</span><span>{{ enabledCount }} 条当前有效</span></footer>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑代理' : '新增代理'"
      width="min(520px, 92vw)"
      top="6vh"
      class="proxy-config-dialog"
      append-to-body
      destroy-on-close
    >
      <div v-if="crawler" class="source-segment" :class="form.sourceType.toLowerCase()">
        <span class="source-indicator"></span>
        <button type="button" :class="{ active: form.sourceType === 'CUSTOM' }" @click="form.sourceType = 'CUSTOM'">独立代理</button>
        <button type="button" :class="{ active: form.sourceType === 'SYSTEM' }" @click="form.sourceType = 'SYSTEM'">引用系统代理</button>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="proxy-form">
        <el-form-item v-if="!crawler || form.sourceType === 'CUSTOM'" label="代理名称" prop="name"><el-input v-model="form.name" placeholder="例如：家庭出口 A" /></el-form-item>
        <el-form-item v-if="!crawler || form.sourceType === 'CUSTOM'" label="代理地址" prop="url"><el-input v-model="form.url" placeholder="http://127.0.0.1:7890" /></el-form-item>
        <el-form-item v-if="crawler && form.sourceType === 'SYSTEM'" label="系统代理" prop="systemProxyId">
          <el-select v-model="form.systemProxyId" placeholder="选择已有系统代理" style="width:100%">
            <el-option v-for="proxy in systemOptions" :key="proxy.id" :value="proxy.id" :label="proxy.name" :disabled="!proxy.enabled"><span>{{ proxy.name }}</span><span class="option-url">{{ proxy.enabled ? proxy.url : '已停用' }}</span></el-option>
          </el-select>
        </el-form-item>
        <div class="form-pair single">
          <el-form-item label="启用"><el-switch v-model="form.enabled" active-text="参与路由" inactive-text="停用" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存配置</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { confirm, message } from '@/utils/message'
import { proxySettingsApi, type CrawlerProxyConfig, type CrawlerRequestSettings, type SystemProxyConfig } from '@/utils/proxySettings'

const props = defineProps<{ scope: 'system' | 'crawler' }>()
const crawler = computed(() => props.scope === 'crawler')
type CrawlerSubtab = 'request' | 'identity' | 'proxy'
const crawlerSubtabs: Array<{ key: CrawlerSubtab; label: string; description: string }> = [
  { key: 'request', label: '请求策略', description: '超时、重试与停止条件' },
  { key: 'identity', label: '请求身份', description: 'User-Agent、Cookie 与 Header' },
  { key: 'proxy', label: '代理配置', description: '系统引用与独立代理' },
]
const activeCrawlerSubtab = ref<CrawlerSubtab>('request')
const crawlerSubtabIndicatorStyle = computed(() => ({
  transform: `translateX(${crawlerSubtabs.findIndex(tab => tab.key === activeCrawlerSubtab.value) * 100}%)`,
}))
const loading = ref(false)
const saving = ref(false)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const reorderSaving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const draggingId = ref<number>()
const dragStartOrder = ref<number[]>([])
type ProxyRow = SystemProxyConfig | CrawlerProxyConfig
const rows = ref<ProxyRow[]>([])
const systemOptions = ref<SystemProxyConfig[]>([])
const formRef = ref<FormInstance>()
const form = reactive({ sourceType: 'CUSTOM' as 'CUSTOM' | 'SYSTEM', name: '', url: '', systemProxyId: undefined as number | undefined, enabled: true, priority: 100 })
const requestSettings = reactive<CrawlerRequestSettings>({timeoutMillis:15000,retryCount:2,maxConsecutiveFailures:5,userAgent:'',cookie:'',headersJson:'{}'})
const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请输入代理名称', trigger: ['blur', 'change'] }],
  url: [{ required: true, whitespace: true, message: '请输入代理地址', trigger: ['blur', 'change'] }],
  systemProxyId: [{ required: true, message: '请选择系统代理', trigger: 'change' }],
}
const enabledCount = computed(() => rows.value.filter(effective).length)
const effective = (row: any) => crawler.value ? row.effectiveEnabled : row.enabled
const statusText = (row: any) => crawler.value && row.sourceType === 'SYSTEM' && !row.sourceAvailable ? '来源不可用' : effective(row) ? '生效中' : '已停用'

function handleCrawlerSubtabKeydown(index: number, event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const nextIndex = event.key === 'Home'
    ? 0
    : event.key === 'End'
      ? crawlerSubtabs.length - 1
      : (index + (event.key === 'ArrowRight' ? 1 : -1) + crawlerSubtabs.length) % crawlerSubtabs.length
  activeCrawlerSubtab.value = crawlerSubtabs[nextIndex].key
  requestAnimationFrame(() => document.getElementById(`crawler-settings-tab-${activeCrawlerSubtab.value}`)?.focus())
}

async function load() {
  loading.value = true
  if (crawler.value) settingsLoading.value = true
  try {
    if (crawler.value) {
      const [crawlerRows, systems, settings] = await Promise.all([proxySettingsApi.crawlerList(), proxySettingsApi.systemList(), proxySettingsApi.crawlerSettings()])
      rows.value = crawlerRows
      systemOptions.value = systems
      Object.assign(requestSettings,settings)
    } else rows.value = await proxySettingsApi.systemList()
  } catch (error: any) { message.error(error.response?.data?.message || '代理配置加载失败') }
  finally { loading.value = false; settingsLoading.value = false }
}
async function saveCrawlerSettings(){
  try{JSON.parse(requestSettings.headersJson||'{}')}catch{message.error('自定义 Header 必须是有效 JSON');return}
  settingsSaving.value=true
  try{Object.assign(requestSettings,await proxySettingsApi.updateCrawlerSettings({...requestSettings}));message.success('爬虫请求设置已保存')}
  catch(error:any){message.error(error.response?.data?.message||'爬虫请求设置保存失败')}
  finally{settingsSaving.value=false}
}
function reset() { editingId.value=undefined; Object.assign(form,{sourceType:'CUSTOM',name:'',url:'',systemProxyId:undefined,enabled:true,priority:100}) }
function openCreate() { reset(); form.priority=Math.min(9999,Math.max(0,...rows.value.map(row=>row.priority))+1); dialogVisible.value=true }
function openEdit(row: any) { editingId.value=row.id; Object.assign(form,{sourceType:row.sourceType||'CUSTOM',name:row.name||'',url:row.url||'',systemProxyId:row.systemProxyId,enabled:row.enabled,priority:row.priority}); dialogVisible.value=true }
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value=true
  try {
    if (crawler.value) {
      const payload = form.sourceType === 'SYSTEM' ? {systemProxyId:form.systemProxyId,enabled:form.enabled,priority:form.priority} : {name:form.name,url:form.url,enabled:form.enabled,priority:form.priority}
      if (editingId.value) await proxySettingsApi.updateCrawler(editingId.value,payload); else await proxySettingsApi.createCrawler(payload)
    } else {
      const payload={name:form.name,url:form.url,enabled:form.enabled,priority:form.priority}
      if(editingId.value) await proxySettingsApi.updateSystem(editingId.value,payload); else await proxySettingsApi.createSystem(payload)
    }
    message.success('代理配置已保存'); dialogVisible.value=false; await load()
  } catch(error:any){message.error(error.response?.data?.message||'代理配置保存失败')} finally{saving.value=false}
}
async function toggle(row:any,enabled:boolean){try{if(crawler.value)await proxySettingsApi.updateCrawler(row.id,row.sourceType==='SYSTEM'?{systemProxyId:row.systemProxyId,enabled,priority:row.priority}:{name:row.name,url:row.url,enabled,priority:row.priority});else await proxySettingsApi.updateSystem(row.id,{name:row.name,url:row.url,enabled,priority:row.priority});await load()}catch(error:any){message.error(error.response?.data?.message||'状态更新失败')}}
async function remove(row:any){if(!await confirm(`确定删除代理“${row.name||'失效引用'}”吗？`))return;try{if(crawler.value)await proxySettingsApi.deleteCrawler(row.id);else await proxySettingsApi.deleteSystem(row.id);message.success('代理配置已删除');await load()}catch(error:any){message.error(error.response?.data?.message||'删除失败')}}
function proxyRows(){return rows.value}
function startProxyDrag(row:ProxyRow,event:DragEvent){
  if(reorderSaving.value){event.preventDefault();return}
  draggingId.value=row.id
  dragStartOrder.value=proxyRows().map(item=>item.id)
  if(event.dataTransfer){event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',String(row.id))}
}
function moveDraggedProxy(target:ProxyRow){
  const sourceIndex=proxyRows().findIndex(item=>item.id===draggingId.value)
  const targetIndex=proxyRows().findIndex(item=>item.id===target.id)
  if(sourceIndex<0||targetIndex<0||sourceIndex===targetIndex)return
  const next=[...proxyRows()]
  const [source]=next.splice(sourceIndex,1)
  next.splice(targetIndex,0,source)
  rows.value=next
}
async function persistProxyOrder(){
  reorderSaving.value=true
  try{
    const ids=proxyRows().map(row=>row.id)
    rows.value=crawler.value?await proxySettingsApi.reorderCrawler(ids):await proxySettingsApi.reorderSystem(ids)
    message.success('代理优先级已更新')
  }
  catch(error:any){message.error(error.response?.data?.message||'代理排序保存失败');await load()}
  finally{reorderSaving.value=false}
}
async function finishProxyDrag(){
  const changed=dragStartOrder.value.join(',')!==proxyRows().map(row=>row.id).join(',')
  draggingId.value=undefined
  dragStartOrder.value=[]
  if(changed)await persistProxyOrder()
}
async function moveProxyByKeyboard(row:ProxyRow,direction:-1|1){
  if(reorderSaving.value)return
  const index=proxyRows().findIndex(item=>item.id===row.id)
  const target=index+direction
  if(index<0||target<0||target>=rows.value.length)return
  const next=[...proxyRows()]
  ;[next[index],next[target]]=[next[target],next[index]]
  rows.value=next
  await persistProxyOrder()
}
onMounted(load)
</script>

<style scoped>
.crawler-subtabs-scroll{margin:18px 24px 0;overflow-x:auto;overscroll-behavior-inline:contain}.crawler-subtabs{position:relative;display:grid;min-width:510px;grid-template-columns:repeat(3,1fr);padding:4px;border:1px solid var(--border-color);border-radius:15px;background:var(--surface-elevated);isolation:isolate}.crawler-subtab-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:-1;width:calc((100% - 8px)/3);border:1px solid var(--border-color-light);border-radius:11px;background:var(--surface-card);box-shadow:var(--shadow-sm);transition:transform .26s cubic-bezier(.2,.8,.2,1)}.crawler-subtab{z-index:1;display:grid;gap:2px;padding:10px 14px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer;text-align:left}.crawler-subtab strong{font-size:13px}.crawler-subtab small{overflow:hidden;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.crawler-subtab.active{color:var(--primary)}.crawler-subtab:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:11px}
.proxy-settings-shell{overflow:hidden;padding:0}.proxy-hero{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:18px;padding:28px 30px;border-bottom:1px solid var(--border-color-light);background:radial-gradient(circle at 82% -30%,var(--primary-alpha-10),transparent 48%),var(--surface-card)}.proxy-hero-mark{display:grid;width:54px;height:54px;place-items:center;border:1px solid var(--primary-alpha-20);border-radius:18px;background:var(--primary-alpha-10);color:var(--primary);font-size:32px}.proxy-kicker{margin:0 0 3px;color:var(--primary);font-size:10px;font-weight:800;letter-spacing:.17em}.proxy-hero h2{margin:0;font-family:'Iowan Old Style','Songti SC',serif;font-size:25px}.proxy-hero p:last-child{margin:5px 0 0;color:var(--text-secondary);font-size:13px}.routing-note{display:flex;gap:12px;margin:18px 24px 0;padding:14px 16px;border:1px solid var(--primary-alpha-20);border-radius:14px;background:var(--primary-alpha-10)}.routing-note-icon{display:grid;flex:0 0 22px;height:22px;place-items:center;border-radius:50%;background:var(--primary);color:white;font-family:serif;font-weight:700}.routing-note p{margin:2px 0 0;color:var(--text-secondary);font-size:12px}.crawler-request-card{margin:18px 24px;padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:var(--surface-elevated)}.request-card-header,.proxy-section-title{display:flex;align-items:center;justify-content:space-between;gap:18px}.request-card-header h3,.proxy-section-title h3{margin:2px 0;font-size:18px}.request-card-header span,.proxy-section-title span,.crawler-request-form small{color:var(--text-secondary);font-size:12px}.crawler-request-form{margin-top:18px}.request-number-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.request-number-grid .el-input-number{width:100%}.proxy-section-title{margin:24px 25px 0}.proxy-table{margin-top:14px}.proxy-drag-handle{display:grid;width:34px;height:34px;margin:auto;place-items:center;border:1px solid transparent;border-radius:9px;background:transparent;color:var(--text-tertiary);cursor:grab;font-size:20px;line-height:1;transition:color .18s ease,background .18s ease,border-color .18s ease}.proxy-drag-handle:hover,.proxy-drag-handle:focus-visible{border-color:var(--primary-alpha-20);outline:none;background:var(--primary-alpha-10);color:var(--primary)}.proxy-drag-handle:active,.proxy-drag-handle.dragging{cursor:grabbing;opacity:.55}.priority-orb{display:inline-grid;min-width:38px;height:30px;padding:0 8px;place-items:center;border-radius:10px;background:var(--primary-alpha-10);color:var(--primary);font-weight:800}.proxy-identity{display:grid;gap:4px}.proxy-identity small{overflow:hidden;color:var(--text-secondary);font-family:'SFMono-Regular',Consolas,monospace;text-overflow:ellipsis}.source-chip,.health-chip{display:inline-flex;align-items:center;gap:6px;padding:4px 8px;border-radius:999px;background:var(--surface-elevated);font-size:12px}.source-chip.system{color:var(--primary)}.health-chip i{width:7px;height:7px;border-radius:50%;background:currentColor}.health-chip.online{color:var(--success-color,#2f9e68)}.health-chip.offline{color:var(--text-tertiary)}.proxy-footer{display:flex;justify-content:space-between;padding:14px 25px 20px;color:var(--text-secondary);font-size:12px}.source-segment{position:relative;display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;padding:4px;border:1px solid var(--border-color);border-radius:13px;background:var(--surface-elevated);isolation:isolate}.source-segment button{z-index:1;padding:9px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.source-segment button.active{color:var(--primary);font-weight:700}.source-indicator{position:absolute;top:4px;bottom:4px;left:4px;width:calc(50% - 4px);border:1px solid var(--border-color-light);border-radius:9px;background:var(--surface-card);box-shadow:var(--shadow-sm);transition:transform .24s ease}.source-segment.system .source-indicator{transform:translateX(100%)}.form-pair{display:grid;grid-template-columns:1fr 1fr;gap:18px}.option-url{float:right;margin-left:24px;color:var(--text-secondary);font-size:11px}@media(max-width:680px){.proxy-hero{grid-template-columns:auto 1fr;padding:22px}.proxy-hero .el-button{grid-column:1/-1}.form-pair,.request-number-grid{grid-template-columns:1fr}.request-card-header{align-items:flex-start;flex-direction:column}}
.form-pair.single{grid-template-columns:1fr}
@media(prefers-reduced-motion:reduce){.crawler-subtab-indicator,.source-indicator{transition:none}}
:global(.proxy-config-dialog){display:flex;max-height:88vh;flex-direction:column;margin-bottom:0}
:global(.proxy-config-dialog .el-dialog__header),:global(.proxy-config-dialog .el-dialog__footer){flex:0 0 auto}
:global(.proxy-config-dialog .el-dialog__body){min-height:0;overflow-y:auto;overscroll-behavior:contain}
</style>
