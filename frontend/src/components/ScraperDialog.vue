<template>
  <el-dialog
    v-model="dialogVisible"
    title="刮削书籍元数据"
    width="500px"
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="scraper-content">
      <div v-if="loading" class="scraping-status">
        <el-icon class="is-loading" :size="48">
          <Loading />
        </el-icon>
        <p>正在从豆瓣/京东读书/Open Library 获取书籍信息...</p>
        <p class="tip">请稍候，这可能需要几秒钟</p>
      </div>
      <div v-else-if="result" class="scraping-result">
        <el-result
          :icon="result.success ? 'success' : 'warning'"
          :title="result.success ? '已匹配元信息' : '没有匹配结果'"
          :sub-title="result.message"
        />
        <p v-if="result.sources?.length" class="scrape-sources">
          来源：{{ result.sources.join('、') }}
        </p>
        <p v-if="result.updatedFields?.length" class="scrape-fields">
          更新字段：{{ result.updatedFields.join('、') }}
        </p>
        <p v-else-if="result.success" class="scrape-fields">
          现有字段已保留，没有需要更新的字段。
        </p>
      </div>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button v-if="!loading && !result" type="primary" @click="handleScrape">
          开始刮削
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { ScrapeResponse } from '@/utils/scraper'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'refresh'): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: () => emit('close')
})

const loading = ref(false)
const result = ref<ScrapeResponse | null>(null)
let scrapeCallback: (() => Promise<ScrapeResponse>) | null = null

const handleClose = () => {
  loading.value = false
  result.value = null
  scrapeCallback = null
  emit('close')
}

const handleScrape = async () => {
  if (!scrapeCallback) return

  loading.value = true
  try {
    result.value = await scrapeCallback()
    if (result.value?.success) {
      emit('refresh')
    }
  } catch (error: any) {
    result.value = {
      success: false,
      message: error.message || '刮削失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

const startScrape = async (callback: () => Promise<ScrapeResponse>) => {
  scrapeCallback = callback
  await handleScrape()
}

defineExpose({
  startScrape
})
</script>

<style scoped>
.scraper-content {
  min-height: 200px;
}

.scraping-status {
  text-align: center;
  padding: 40px 0;
}

.scraping-status .is-loading {
  color: var(--el-color-primary);
  margin-bottom: 16px;
}

.scraping-status p {
  margin: 8px 0;
  color: var(--el-text-color-regular);
}

.scraping-status .tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.scraping-result {
  width: 100%;
}

.scrape-sources,
.scrape-fields {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
