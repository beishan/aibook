<template>
  <div class="connections-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">客户端连接</h1>
        <p class="page-subtitle">配置第三方阅读器连接到汗牛充栋书库</p>
      </div>
    </div>

    <!-- 连接卡片 -->
    <div class="connections-grid">
      <!-- OPDS 连接 -->
      <div class="connection-card glass">
        <div class="card-header">
          <span class="card-icon">📚</span>
          <span class="card-title">OPDS 电子书目录</span>
        </div>

        <div class="connection-info">
          <p class="description">
            OPDS 协议允许阅读器浏览和下载书库中的电子书。支持大部分开源阅读器。
          </p>

          <div class="url-box">
            <label class="form-label">OPDS 地址</label>
            <div class="url-row">
              <input :value="opdsUrl" readonly class="input url-input" />
              <button class="btn" @click="copyUrl(opdsUrl)">
                <span>📋</span>
              </button>
              <button class="btn btn-primary" @click="testConnection('opds')">测试</button>
            </div>
          </div>

          <div class="supported-clients">
            <label class="form-label">支持的客户端</label>
            <div class="client-tags">
              <span class="tag tag-primary">KOReader</span>
              <span class="tag tag-primary">Moon+ Reader</span>
              <span class="tag tag-primary">Librera</span>
              <span class="tag tag-primary">CoolReader</span>
              <span class="tag tag-primary">FBReader</span>
            </div>
          </div>

          <div class="url-box">
            <label class="form-label">OPDS 2.0 (JSON) 地址</label>
            <div class="url-row">
              <input :value="opds2Url" readonly class="input url-input" />
              <button class="btn" @click="copyUrl(opds2Url)">
                <span>📋</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- WebDAV 连接 -->
      <div class="connection-card glass">
        <div class="card-header">
          <span class="card-icon">📁</span>
          <span class="card-title">WebDAV 文件同步</span>
        </div>

        <div class="connection-info">
          <p class="description">
            WebDAV 协议支持文件浏览、下载和进度同步。主要用于 KOReader 的阅读进度同步。
          </p>

          <div class="url-box">
            <label class="form-label">WebDAV 地址</label>
            <div class="url-row">
              <input :value="webdavUrl" readonly class="input url-input" />
              <button class="btn" @click="copyUrl(webdavUrl)">
                <span>📋</span>
              </button>
              <button class="btn btn-primary" @click="testConnection('webdav')">测试</button>
            </div>
          </div>

          <div class="supported-clients">
            <label class="form-label">支持的客户端</label>
            <div class="client-tags">
              <span class="tag tag-primary">KOReader (进度同步)</span>
              <span class="tag tag-primary">Cyberduck</span>
              <span class="tag tag-primary">文件管理器</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- KOReader 设置指南 -->
    <div class="guide-card glass">
      <div class="card-header">
        <span class="card-icon">📖</span>
        <span class="card-title">KOReader 设置指南</span>
      </div>

      <div class="steps">
        <div class="step" v-for="(step, index) in steps" :key="index">
          <div class="step-icon">{{ index + 1 }}</div>
          <div class="step-content">
            <div class="step-title">{{ step.title }}</div>
            <div class="step-description">{{ step.description }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 测试结果对话框 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showTestResult" class="dialog-overlay" @click.self="showTestResult = false">
          <div class="dialog">
            <div class="dialog-header">
              <span>🔗 连接测试</span>
              <button class="dialog-close" @click="showTestResult = false">✕</button>
            </div>
            <div class="dialog-body">
              <div v-if="testResult" class="test-result" :class="testResult.success ? 'success' : 'error'">
                <div class="result-icon">{{ testResult.success ? '✅' : '❌' }}</div>
                <div class="result-title">{{ testResult.success ? '连接成功' : '连接失败' }}</div>
                <div class="result-message">{{ testResult.message }}</div>
              </div>
            </div>
            <div class="dialog-footer">
              <button class="btn" @click="showTestResult = false">关闭</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message } from '@/utils/message'
import api from '@/utils/api'

const showTestResult = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const serverUrl = computed(() => window.location.origin)

const opdsUrl = computed(() => `${serverUrl.value}/opds`)
const opds2Url = computed(() => `${serverUrl.value}/opds/v2`)
const webdavUrl = computed(() => `${serverUrl.value}/webdav`)

const steps = computed(() => [
  { title: '添加 OPDS 书库', description: '打开 KOReader → 左上角菜单 → 云存储 → 添加 OPDS 目录' },
  { title: '输入服务器地址', description: `输入 OPDS 地址: ${opdsUrl.value}` },
  { title: '输入用户名和密码', description: '使用汗牛充栋的账号密码登录' },
  { title: '浏览和下载书籍', description: '成功连接后即可浏览书库、下载和阅读书籍' },
  { title: '配置进度同步', description: '在 KOReader 设置中启用 WebDAV 进度同步，输入 WebDAV 地址' },
])

const copyUrl = async (url: string) => {
  try {
    await navigator.clipboard.writeText(url)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

const testConnection = async (type: 'opds' | 'webdav') => {
  try {
    const url = type === 'opds' ? '/opds' : '/webdav'
    const response = await api.get(url, {
      headers: type === 'opds'
        ? { 'Accept': 'application/atom+xml;profile=opds-catalog' }
        : {}
    })

    if (response.status === 200) {
      testResult.value = {
        success: true,
        message: `${type === 'opds' ? 'OPDS' : 'WebDAV'} 服务运行正常`
      }
    } else {
      testResult.value = {
        success: false,
        message: `服务器返回状态码: ${response.status}`
      }
    }
  } catch (error: any) {
    testResult.value = {
      success: false,
      message: error.response?.data?.message || '无法连接到服务器'
    }
  }
  showTestResult.value = true
}
</script>

<style scoped>
.connections-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

/* 页面头部 */
.page-header {
  margin-bottom: var(--spacing-xl);
}

.page-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: white;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  margin-bottom: var(--spacing-sm);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: rgba(255, 255, 255, 0.8);
}

/* 连接网格 */
.connections-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

/* 连接卡片 */
.connection-card,
.guide-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  font-weight: 600;
  font-size: var(--font-size-lg);
}

.card-icon {
  font-size: 24px;
}

.card-title {
  flex: 1;
}

.connection-info {
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.description {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  margin: 0;
}

.url-box {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.url-row {
  display: flex;
  gap: var(--spacing-sm);
}

.url-input {
  flex: 1;
  font-family: 'SF Mono', monospace;
  font-size: var(--font-size-sm);
  background: var(--bg-secondary);
}

.supported-clients {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.client-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

/* 步骤指南 */
.steps {
  padding: var(--spacing-lg);
}

.step {
  display: flex;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg) 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.step:last-child {
  border-bottom: none;
}

.step-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  background: var(--primary-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

.step-content {
  flex: 1;
}

.step-title {
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-size-base);
}

.step-description {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: 1.5;
}

/* 测试结果 */
.test-result {
  text-align: center;
  padding: var(--spacing-lg);
}

.result-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-md);
}

.result-title {
  font-size: var(--font-size-xl);
  font-weight: 600;
  margin-bottom: var(--spacing-sm);
}

.result-message {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.test-result.success .result-title {
  color: var(--success);
}

.test-result.error .result-title {
  color: var(--danger);
}

/* 响应式 */
@media (max-width: 768px) {
  .connections-grid {
    grid-template-columns: 1fr;
  }
}
</style>
