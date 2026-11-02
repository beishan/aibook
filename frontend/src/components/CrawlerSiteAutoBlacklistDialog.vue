<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${site?.siteName || '网站'} · 自动黑名单配置`"
    width="min(600px, 94vw)"
    append-to-body
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="loadFailed" type="error" :closable="false" title="配置加载失败，请重新加载。">
      <el-button text @click="load">重新加载</el-button>
    </el-alert>
    <el-form v-loading="loading" label-position="top" :disabled="loading || saving || loadFailed">
      <el-form-item label="自动加入黑名单">
        <el-switch v-model="form.enabled" aria-label="启用自动黑名单" />
      </el-form-item>
      <p>任意关键字或标签命中即加入黑名单。扫描继续处理其他书籍；书籍任务命中后停止，并记录具体原因。</p>
      <el-form-item label="关键字（书名、作者或简介包含任意一项）">
        <el-select
          v-model="form.keywords" multiple filterable allow-create default-first-option
          :multiple-limit="100" placeholder="输入关键字后按回车，可添加多个" style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="标签（完整匹配任意一项）">
        <el-select
          v-model="form.tags" multiple filterable allow-create default-first-option
          :multiple-limit="100" placeholder="输入标签后按回车，可添加多个" style="width: 100%"
        />
      </el-form-item>
      <p>忽略大小写，每种最多100项，每项最多200字。保存后在后续扫描和任务启动时生效，不立即处理历史书籍。启用规则会按需读取详情页，增加扫描请求量；停用不会移出已有黑名单。</p>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading || loadFailed" @click="save">保存配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { crawlerApi, type CrawlerSite, type CrawlerSiteAutoBlacklist } from '@/utils/crawler'

const props = defineProps<{ modelValue: boolean; site?: CrawlerSite }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const form = reactive<CrawlerSiteAutoBlacklist>({ enabled: false, keywords: [], tags: [] })
const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
let requestId = 0

watch(() => [props.modelValue, props.site?.id], () => {
  if (props.modelValue && props.site) void load()
  else requestId++
})

async function load() {
  if (!props.site) return
  const request = ++requestId
  loading.value = true
  loadFailed.value = false
  Object.assign(form, { enabled: false, keywords: [], tags: [] })
  try {
    const value = await crawlerApi.siteAutoBlacklist(props.site.id)
    if (request === requestId) Object.assign(form, value)
  } catch {
    if (request === requestId) loadFailed.value = true
  } finally {
    if (request === requestId) loading.value = false
  }
}

async function save() {
  if (!props.site || saving.value || loading.value || loadFailed.value) return
  if ([...form.keywords, ...form.tags].some(value => value.length > 200)) {
    ElMessage.warning('每个关键字或标签最多200字')
    return
  }
  saving.value = true
  try {
    await crawlerApi.setSiteAutoBlacklist(props.site.id, form)
    ElMessage.success('自动黑名单配置已保存')
    emit('update:modelValue', false)
  } catch {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
p {
  color: var(--el-text-color-secondary);
  line-height: 1.7;
  margin: 0 0 18px;
}
</style>
