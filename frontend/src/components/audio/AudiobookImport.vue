<template>
  <el-dialog
    v-model="visible"
    title="导入有声书"
    width="min(960px, 95vw)"
    append-to-body
    :close-on-click-modal="false"
    @closed="stopPolling"
  >
    <p>选择文件或已配置的 NAS 目录，核对分组、顺序和演播版本后确认入库。原始文件不会被修改。</p>
    <AudioSegmented
      v-model="source"
      label="音频来源"
      :options="[
        { value: 'upload', label: '上传文件' },
        { value: 'scan', label: 'NAS 目录' },
      ]"
    />
    <div v-if="source === 'upload'" class="audio-import-source">
      <label
        >音频文件（MP3 / M4A / M4B）<input
          type="file"
          multiple
          accept=".mp3,.m4a,.m4b"
          :disabled="working"
          @change="upload"
      /></label>
      <small>单文件及单批受服务器上传限制；默认单批 500MB。更大的书籍请使用 NAS 扫描。</small>
    </div>
    <form v-else class="audio-import-source" @submit.prevent="scan">
      <label
        >扫描目录
        <select v-model="directoryId" required :disabled="working">
          <option :value="null" disabled>请选择已配置的目录</option>
          <option v-for="directory in directories" :key="directory.id" :value="directory.id">
            {{ directory.path }}
          </option>
        </select>
      </label>
      <label>此目录的定时扫描策略（默认仍为电子书）</label>
      <AudioSegmented
        v-model="scanContentType"
        label="扫描内容类型"
        :options="[
          { value: 'EBOOK', label: '电子书' },
          { value: 'AUDIO', label: '有声书' },
          { value: 'MIXED', label: '混合' },
        ]"
      />
      <button
        type="button"
        class="btn"
        :disabled="!directoryId || working"
        @click="saveDirectoryType"
      >
        保存扫描策略
      </button>
      <small>有声书定时任务仅生成预览，按账户原有扫描时间执行；仍需手动确认分组入库。</small>
      <label
        >目录内子文件夹（可留空）<input
          v-model="relativePath"
          class="input"
          placeholder="例如 三体/Disc1"
          :disabled="working"
      /></label>
      <button class="btn btn-primary" :disabled="working || !directoryId">探测音频</button>
      <small>目录在系统设置中配置；每次最多 5000 个文件。扫描仅生成预览，不自动入库。</small>
    </form>
    <details v-if="history.length" class="audio-import-source">
      <summary>最近的导入任务（关闭窗口后可从这里恢复预览）</summary>
      <button
        v-for="item in history"
        :key="item.id"
        class="btn"
        :disabled="working"
        @click="resume(item)"
      >
        {{ item.state }} · {{ item.createdAt }}
      </button>
    </details>
    <p v-if="task" role="status">{{ taskLabel }} · 已探测 {{ candidates.length }} 个文件</p>
    <p v-if="failure" role="alert" class="audio-error">{{ failure }}</p>
    <template v-if="task?.state === 'READY'">
      <div class="audio-import-form">
        <label
          >音轨分组
          <select v-model="selectedGroup" @change="selectGroup">
            <option v-for="group in groups" :key="group" :value="group">
              {{ group || '当前目录' }}
            </option>
            <option v-if="groups.length > 1" value="__all__">明确合并全部目录为一个演播版本</option>
          </select>
        </label>
        <label>作品名称<input v-model="info.title" class="input" maxlength="200" /></label>
        <label>作者<input v-model="info.author" class="input" maxlength="200" /></label>
        <label>演播版本名称<input v-model="info.name" class="input" maxlength="200" /></label>
        <label>演播者<input v-model="info.narrator" class="input" maxlength="200" /></label>
        <label>语言<input v-model="info.language" class="input" maxlength="80" /></label>
        <label
          >关联现有作品（可选）
          <el-select
            v-model="info.bookId"
            filterable
            remote
            clearable
            :remote-method="searchBooks"
            placeholder="输入书名搜索；留空则新建"
          >
            <el-option
              v-for="item in bookOptions"
              :key="item.id"
              :value="item.id"
              :label="`${item.title} · ${item.author || '未知作者'}`"
            />
          </el-select>
        </label>
        <label
          >简介<textarea v-model="info.description" class="input" rows="2" maxlength="20000" />
        </label>
      </div>
      <p>
        已选 {{ selected.length }} /
        {{ candidates.length }}
        个文件；失败文件不会被自动导入。顺序按下表，支持逐项调整。
      </p>
      <div class="audio-import-table">
        <table>
          <thead>
            <tr>
              <th>导入</th>
              <th>文件 / 时长</th>
              <th>状态</th>
              <th>顺序</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in shown" :key="item.path">
              <td>
                <input
                  v-model="selected"
                  type="checkbox"
                  :value="item.path"
                  :disabled="!!item.error"
                  :aria-label="`选择 ${item.title}`"
                />
              </td>
              <td>
                <strong>{{ item.title }}</strong
                ><small>{{ item.group || '当前目录' }} · {{ audioTime(item.durationMs) }}</small>
              </td>
              <td>
                <span v-if="item.error" class="audio-error">{{ item.error }}</span
                ><span v-else>就绪</span>
              </td>
              <td>
                <button
                  class="btn"
                  :disabled="index + tablePage * 100 === 0"
                  aria-label="音轨上移"
                  @click="move(index + tablePage * 100, -1)"
                >
                  ↑
                </button>
                <button
                  class="btn"
                  :disabled="index + tablePage * 100 >= candidates.length - 1"
                  aria-label="音轨下移"
                  @click="move(index + tablePage * 100, 1)"
                >
                  ↓
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="audio-import-pagination">
        <button class="btn" :disabled="tablePage === 0" @click="tablePage--">上一页</button>
        <span>{{ tablePage + 1 }} / {{ Math.max(1, Math.ceil(candidates.length / 100)) }}</span>
        <button
          class="btn"
          :disabled="(tablePage + 1) * 100 >= candidates.length"
          @click="tablePage++"
        >
          下一页
        </button>
      </div>
    </template>
    <template #footer>
      <button v-if="working || task?.state === 'READY'" class="btn" @click="cancel">
        取消任务
      </button>
      <button class="btn" @click="visible = false">关闭</button>
      <button
        class="btn btn-primary"
        :disabled="
          task?.state !== 'READY' ||
          !selected.length ||
          !info.title.trim() ||
          !info.name.trim() ||
          confirming
        "
        @click="confirm"
      >
        {{ confirming ? '正在导入…' : `确认导入 ${selected.length} 个文件` }}
      </button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import api from '@/utils/api'
import { audioTime } from '@/utils/audiobook'
import AudioSegmented from './AudioSegmented.vue'
const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ editionId?: number; bookId?: number }>()
const emit = defineEmits<{ imported: [editionId: number] }>()
interface Candidate {
  path: string
  title: string
  group: string
  durationMs: number
  error?: string
}
const source = ref('upload')
const directoryId = ref<number | null>(null)
const relativePath = ref('')
const scanContentType = ref('EBOOK')
watch(directoryId, (id) => {
  scanContentType.value = directories.value.find((item) => item.id === id)?.contentType || 'EBOOK'
})
async function saveDirectoryType() {
  const { data } = await api.put(`/api/scan-directories/${directoryId.value}/content-type`, {
    contentType: scanContentType.value,
  })
  const directory = directories.value.find((item) => item.id === directoryId.value)
  if (directory) directory.contentType = data.contentType
}
const directories = ref<Array<{ id: number; path: string; contentType?: string }>>([])
const history = ref<any[]>([])
const task = ref<any>(null)
const candidates = ref<Candidate[]>([])
const selected = ref<string[]>([])
const selectedGroup = ref('')
const failure = ref('')
const confirming = ref(false)
const uploading = ref(false)
const tablePage = ref(0)
const info = ref({
  title: '',
  author: '',
  name: '完整版',
  narrator: '',
  language: '中文',
  description: '',
  bookId: props.bookId as number | undefined,
})
const bookOptions = ref<Array<{ id: number; title: string; author: string }>>([])
let pollTimer: ReturnType<typeof setTimeout> | undefined
let pollGeneration = 0
const groups = computed(() => [...new Set(candidates.value.map((item) => item.group))])
const working = computed(() => uploading.value || task.value?.state === 'SCANNING')
const shown = computed(() =>
  candidates.value.slice(tablePage.value * 100, (tablePage.value + 1) * 100)
)
const taskLabel = computed(
  () =>
    ({
      SCANNING: '正在探测',
      READY: '预览就绪',
      FAILED: '探测失败',
      CANCELLED: '已取消',
      INTERRUPTED: '任务被重启中断',
    })[task.value?.state as string] || task.value?.state
)
watch(visible, async (value) => {
  if (value) {
    directories.value = (await api.get('/api/scan-directories')).data.filter(
      (item: any) => item.enabled
    )
    history.value = (await api.get('/api/audiobooks/imports')).data.filter((item: any) =>
      ['READY', 'SCANNING', 'FAILED', 'INTERRUPTED'].includes(item.state)
    )
    if (task.value?.state === 'SCANNING') poll()
  }
})
function stopPolling() {
  pollGeneration++
  clearTimeout(pollTimer)
}
async function poll() {
  stopPolling()
  const generation = pollGeneration
  try {
    const { data } = await api.get(`/api/audiobooks/imports/${task.value.id}`)
    if (generation !== pollGeneration) return
    task.value = data
    candidates.value = data.candidates
    if (data.state === 'SCANNING') pollTimer = setTimeout(poll, 1500)
    else if (data.state === 'READY') {
      selectedGroup.value = groups.value[0] || ''
      selectGroup()
      if (!info.value.title)
        info.value.title = candidates.value[0]?.group.split('/').filter(Boolean).pop() || '新有声书'
    } else failure.value = data.error || '任务未完成，可重新探测'
  } catch (error: any) {
    failure.value = error.response?.data?.message || '无法读取探测任务，请关闭后重新打开'
  }
}
async function resume(item: any) {
  task.value = item
  await poll()
}
function selectGroup() {
  selected.value = candidates.value
    .filter(
      (item) =>
        !item.error && (selectedGroup.value === '__all__' || item.group === selectedGroup.value)
    )
    .map((item) => item.path)
}
async function upload(event: Event) {
  const files = [...((event.target as HTMLInputElement).files || [])]
  if (!files.length) return
  const body = new FormData()
  files.forEach((file) => body.append('files', file))
  uploading.value = true
  failure.value = ''
  try {
    task.value = (await api.post('/api/audiobooks/imports/upload', body, { timeout: 0 })).data
    await poll()
  } finally {
    uploading.value = false
  }
}
async function scan() {
  failure.value = ''
  task.value = (
    await api.post('/api/audiobooks/imports/scan', {
      directoryId: directoryId.value,
      relativePath: relativePath.value,
    })
  ).data
  await poll()
}
async function cancel() {
  if (task.value) await api.delete(`/api/audiobooks/imports/${task.value.id}`)
  stopPolling()
  task.value = null
  candidates.value = []
  selected.value = []
}
async function searchBooks(keyword: string) {
  bookOptions.value = (await api.get('/api/books', { params: { keyword, size: 30 } })).data.content
}
function move(index: number, offset: number) {
  const next = [...candidates.value]
  ;[next[index], next[index + offset]] = [next[index + offset], next[index]]
  candidates.value = next
}
async function confirm() {
  confirming.value = true
  try {
    const paths = candidates.value
      .filter((item) => selected.value.includes(item.path))
      .map((item) => item.path)
    const { data } = await api.post('/api/audiobooks/imports/confirm', {
      taskId: task.value.id,
      paths,
      edition: info.value,
      editionId: props.editionId,
    })
    task.value = null
    candidates.value = []
    selected.value = []
    visible.value = false
    emit('imported', data.editionId)
  } finally {
    confirming.value = false
  }
}
onBeforeUnmount(stopPolling)
</script>
<style scoped>
p {
  margin-bottom: 16px;
  color: var(--text-secondary);
}
.audio-import-source {
  display: grid;
  gap: 12px;
  margin: 20px 0;
}
.audio-import-source label,
.audio-import-form label {
  display: grid;
  gap: 8px;
}
.audio-import-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin: 18px 0;
}
.audio-import-table {
  overflow: auto;
  max-height: 360px;
}
table {
  width: 100%;
  text-align: left;
  border-collapse: collapse;
}
th,
td {
  padding: 10px;
  border-bottom: 1px solid var(--border-color-light);
}
td small {
  display: block;
  color: var(--text-secondary);
  margin-top: 5px;
  overflow-wrap: anywhere;
}
select {
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-primary);
  background: var(--bg-surface);
}
.audio-import-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 12px;
}
.audio-error {
  color: var(--danger);
}
@media (max-width: 600px) {
  .audio-import-form {
    grid-template-columns: 1fr;
  }
}
</style>
