<template>
  <div class="reader-view">
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="book" class="reader-content">
      <!-- 阅读器头部 -->
      <div class="reader-header">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="reader-title">{{ book.title }}</div>
        <div class="reader-actions">
          <el-button @click="showSettings = true">
            <el-icon><Setting /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 阅读器内容 -->
      <div class="reader-body" :style="readerStyle">
        <div class="reader-text" v-if="book.format === 'txt' || book.format === 'md'">
          <p v-for="(paragraph, index) in content" :key="index">{{ paragraph }}</p>
        </div>

        <div class="reader-html" v-else-if="book.format === 'html'" v-html="htmlContent">
        </div>

        <div class="reader-placeholder" v-else>
          <el-empty :description="`${book.format.toUpperCase()} 格式暂不支持在线阅读`">
            <el-button type="primary" @click="handleDownload">下载文件</el-button>
          </el-empty>
        </div>
      </div>

      <!-- 阅读器底部 -->
      <div class="reader-footer">
        <div class="progress-info">
          <span>进度：{{ progress }}%</span>
        </div>
        <el-slider v-model="progress" :max="100" :step="1" />
      </div>

      <!-- 设置面板 -->
      <el-drawer v-model="showSettings" title="阅读设置" direction="rtl" size="300px">
        <el-form label-position="top">
          <el-form-item label="字体">
            <el-select v-model="settings.fontFamily" style="width: 100%">
              <el-option label="默认" value="default" />
              <el-option label="宋体" value="SimSun, serif" />
              <el-option label="黑体" value="SimHei, sans-serif" />
              <el-option label="楷体" value="KaiTi, serif" />
            </el-select>
          </el-form-item>

          <el-form-item label="字号">
            <el-slider v-model="settings.fontSize" :min="12" :max="24" :step="1" show-input />
          </el-form-item>

          <el-form-item label="行间距">
            <el-slider v-model="settings.lineHeight" :min="1" :max="3" :step="0.1" show-input />
          </el-form-item>

          <el-form-item label="背景色">
            <el-radio-group v-model="settings.backgroundColor">
              <el-radio-button value="white">白色</el-radio-button>
              <el-radio-button value="#f5f5dc">米色</el-radio-button>
              <el-radio-button value="#333">黑色</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </el-drawer>
    </div>

    <div v-else class="empty-state">
      <el-empty description="书籍不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Setting } from '@element-plus/icons-vue'
import { useBookStore } from '@/stores/book'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()

const book = ref<any>(null)
const loading = ref(true)
const content = ref<string[]>([])
const htmlContent = ref('')
const progress = ref(0)
const showSettings = ref(false)

const settings = ref({
  fontFamily: 'default',
  fontSize: 16,
  lineHeight: 1.8,
  backgroundColor: 'white',
})

const readerStyle = computed(() => ({
  fontFamily: settings.value.fontFamily === 'default' ? 'inherit' : settings.value.fontFamily,
  fontSize: `${settings.value.fontSize}px`,
  lineHeight: settings.value.lineHeight,
  backgroundColor: settings.value.backgroundColor,
  color: settings.value.backgroundColor === '#333' ? '#fff' : '#333',
}))

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    book.value = await bookStore.fetchBookById(id)

    // TODO: 从后端加载书籍内容
    // 这里先用示例内容
    if (book.value.format === 'txt' || book.value.format === 'md') {
      content.value = [
        '这是一本示例书籍的内容。',
        '在实际使用中，这里会显示从后端加载的书籍内容。',
        '支持多种格式的在线阅读。',
      ]
    }
  } catch (error) {
    console.error('Failed to load book:', error)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

const handleDownload = () => {
  // TODO: 实现下载功能
}

onMounted(loadBook)
</script>

<style scoped>
.reader-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.loading-state,
.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.reader-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.reader-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: white;
  border-bottom: 1px solid #eee;
}

.reader-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.reader-body {
  flex: 1;
  overflow-y: auto;
  padding: 40px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

.reader-text {
  line-height: 1.8;
}

.reader-text p {
  margin-bottom: 1em;
  text-indent: 2em;
}

.reader-html {
  line-height: 1.8;
}

.reader-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.reader-footer {
  padding: 10px 20px;
  background: white;
  border-top: 1px solid #eee;
}

.progress-info {
  text-align: center;
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}
</style>
