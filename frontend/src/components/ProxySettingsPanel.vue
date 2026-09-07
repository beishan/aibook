<template>
  <section class="proxy-settings-shell card glass">
    <header class="proxy-hero">
      <div class="proxy-hero-mark" aria-hidden="true">⌁</div>
      <div>
        <p class="proxy-kicker">{{ crawler ? 'CRAWLER ROUTING' : 'NETWORK ROUTING' }}</p>
        <h2>{{ crawler ? '爬虫代理配置' : '系统代理配置' }}</h2>
        <p>{{ crawler ? '组合独立代理与系统代理引用，按较小的优先级数字依次尝试。' : '维护可被系统功能复用的代理资源；允许多条配置同时启用。' }}</p>
      </div>
      <el-button type="primary" @click="openCreate">＋ 新增代理</el-button>
    </header>

    <div v-if="crawler" class="routing-note">
      <span class="routing-note-icon">i</span>
      <div><strong>引用状态实时联动</strong><p>引用的系统代理被停用或删除后，此处会立即变为不可用且不会参与请求。</p></div>
    </div>

    <el-table v-loading="loading" :data="rows" class="proxy-table" empty-text="暂无代理配置">
      <el-table-column label="优先级" width="92" sortable prop="priority">
        <template #default="{ row }"><span class="priority-orb">{{ row.priority }}</span></template>
      </el-table-column>
      <el-table-column label="代理" min-width="210">
        <template #default="{ row }">
          <div class="proxy-identity"><strong>{{ row.name || '引用已失效' }}</strong><small>{{ row.url || '系统代理已被删除' }}</small></div>
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

    <footer class="proxy-footer"><span>优先级数字越小越先使用</span><span>{{ enabledCount }} 条当前有效</span></footer>

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
            <el-option v-for="proxy in systemOptions" :key="proxy.id" :value="proxy.id" :label="`${proxy.name} · P${proxy.priority}`" :disabled="!proxy.enabled"><span>{{ proxy.name }}</span><span class="option-url">{{ proxy.enabled ? proxy.url : '已停用' }}</span></el-option>
          </el-select>
        </el-form-item>
        <div class="form-pair">
          <el-form-item label="优先级" prop="priority"><el-input-number v-model="form.priority" :min="1" :max="9999" controls-position="right" /></el-form-item>
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
import { proxySettingsApi, type CrawlerProxyConfig, type SystemProxyConfig } from '@/utils/proxySettings'

const props = defineProps<{ scope: 'system' | 'crawler' }>()
const crawler = computed(() => props.scope === 'crawler')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const rows = ref<Array<SystemProxyConfig | CrawlerProxyConfig>>([])
const systemOptions = ref<SystemProxyConfig[]>([])
const formRef = ref<FormInstance>()
const form = reactive({ sourceType: 'CUSTOM' as 'CUSTOM' | 'SYSTEM', name: '', url: '', systemProxyId: undefined as number | undefined, enabled: true, priority: 100 })
const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请输入代理名称', trigger: ['blur', 'change'] }],
  url: [{ required: true, whitespace: true, message: '请输入代理地址', trigger: ['blur', 'change'] }],
  systemProxyId: [{ required: true, message: '请选择系统代理', trigger: 'change' }],
  priority: [{ required: true, message: '请设置优先级', trigger: 'change' }],
}
const enabledCount = computed(() => rows.value.filter(effective).length)
const effective = (row: any) => crawler.value ? row.effectiveEnabled : row.enabled
const statusText = (row: any) => crawler.value && row.sourceType === 'SYSTEM' && !row.sourceAvailable ? '来源不可用' : effective(row) ? '生效中' : '已停用'

async function load() {
  loading.value = true
  try {
    if (crawler.value) {
      const [crawlerRows, systems] = await Promise.all([proxySettingsApi.crawlerList(), proxySettingsApi.systemList()])
      rows.value = crawlerRows
      systemOptions.value = systems
    } else rows.value = await proxySettingsApi.systemList()
  } catch (error: any) { message.error(error.response?.data?.message || '代理配置加载失败') }
  finally { loading.value = false }
}
function reset() { editingId.value=undefined; Object.assign(form,{sourceType:'CUSTOM',name:'',url:'',systemProxyId:undefined,enabled:true,priority:100}) }
function openCreate() { reset(); dialogVisible.value=true }
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
onMounted(load)
</script>

<style scoped>
.proxy-settings-shell{overflow:hidden;padding:0}.proxy-hero{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:18px;padding:28px 30px;border-bottom:1px solid var(--border-color-light);background:radial-gradient(circle at 82% -30%,var(--primary-alpha-10),transparent 48%),var(--surface-card)}.proxy-hero-mark{display:grid;width:54px;height:54px;place-items:center;border:1px solid var(--primary-alpha-20);border-radius:18px;background:var(--primary-alpha-10);color:var(--primary);font-size:32px}.proxy-kicker{margin:0 0 3px;color:var(--primary);font-size:10px;font-weight:800;letter-spacing:.17em}.proxy-hero h2{margin:0;font-family:'Iowan Old Style','Songti SC',serif;font-size:25px}.proxy-hero p:last-child{margin:5px 0 0;color:var(--text-secondary);font-size:13px}.routing-note{display:flex;gap:12px;margin:18px 24px 0;padding:14px 16px;border:1px solid var(--primary-alpha-20);border-radius:14px;background:var(--primary-alpha-10)}.routing-note-icon{display:grid;flex:0 0 22px;height:22px;place-items:center;border-radius:50%;background:var(--primary);color:white;font-family:serif;font-weight:700}.routing-note p{margin:2px 0 0;color:var(--text-secondary);font-size:12px}.proxy-table{margin-top:14px}.priority-orb{display:inline-grid;min-width:38px;height:30px;padding:0 8px;place-items:center;border-radius:10px;background:var(--primary-alpha-10);color:var(--primary);font-weight:800}.proxy-identity{display:grid;gap:4px}.proxy-identity small{overflow:hidden;color:var(--text-secondary);font-family:'SFMono-Regular',Consolas,monospace;text-overflow:ellipsis}.source-chip,.health-chip{display:inline-flex;align-items:center;gap:6px;padding:4px 8px;border-radius:999px;background:var(--surface-elevated);font-size:12px}.source-chip.system{color:var(--primary)}.health-chip i{width:7px;height:7px;border-radius:50%;background:currentColor}.health-chip.online{color:var(--success-color,#2f9e68)}.health-chip.offline{color:var(--text-tertiary)}.proxy-footer{display:flex;justify-content:space-between;padding:14px 25px 20px;color:var(--text-secondary);font-size:12px}.source-segment{position:relative;display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;padding:4px;border:1px solid var(--border-color);border-radius:13px;background:var(--surface-elevated);isolation:isolate}.source-segment button{z-index:1;padding:9px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.source-segment button.active{color:var(--primary);font-weight:700}.source-indicator{position:absolute;top:4px;bottom:4px;left:4px;width:calc(50% - 4px);border:1px solid var(--border-color-light);border-radius:9px;background:var(--surface-card);box-shadow:var(--shadow-sm);transition:transform .24s ease}.source-segment.system .source-indicator{transform:translateX(100%)}.form-pair{display:grid;grid-template-columns:1fr 1fr;gap:18px}.option-url{float:right;margin-left:24px;color:var(--text-secondary);font-size:11px}@media(max-width:680px){.proxy-hero{grid-template-columns:auto 1fr;padding:22px}.proxy-hero .el-button{grid-column:1/-1}.form-pair{grid-template-columns:1fr}}
@media(prefers-reduced-motion:reduce){.source-indicator{transition:none}}
:global(.proxy-config-dialog){display:flex;max-height:88vh;flex-direction:column;margin-bottom:0}
:global(.proxy-config-dialog .el-dialog__header),:global(.proxy-config-dialog .el-dialog__footer){flex:0 0 auto}
:global(.proxy-config-dialog .el-dialog__body){min-height:0;overflow-y:auto;overscroll-behavior:contain}
</style>
