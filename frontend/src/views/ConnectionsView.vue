<template>
  <div class="connections-view">
    <h2>客户端连接</h2>
    <p class="subtitle">配置第三方阅读器连接到汗牛充栋书库</p>

    <el-row :gutter="20">
      <!-- OPDS 连接 -->
      <el-col :span="12">
        <el-card class="connection-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>OPDS 电子书目录</span>
            </div>
          </template>

          <div class="connection-info">
            <p class="description">
              OPDS 协议允许阅读器浏览和下载书库中的电子书。支持大部分开源阅读器。
            </p>

            <div class="url-box">
              <label>OPDS 地址</label>
              <div class="url-row">
                <el-input
                  :model-value="opdsUrl"
                  readonly
                  class="url-input"
                />
                <el-button type="primary" @click="copyUrl(opdsUrl)">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
                <el-button @click="testConnection('opds')">
                  测试
                </el-button>
              </div>
            </div>

            <div class="supported-clients">
              <label>支持的客户端</label>
              <div class="client-tags">
                <el-tag>KOReader</el-tag>
                <el-tag>Moon+ Reader</el-tag>
                <el-tag>Librera</el-tag>
                <el-tag>CoolReader</el-tag>
                <el-tag>FBReader</el-tag>
              </div>
            </div>

            <el-collapse>
              <el-collapse-item title="OPDS 2.0 (JSON)">
                <div class="url-box">
                  <label>OPDS 2.0 地址</label>
                  <div class="url-row">
                    <el-input
                      :model-value="opds2Url"
                      readonly
                      class="url-input"
                    />
                    <el-button type="primary" @click="copyUrl(opds2Url)">
                      <el-icon><CopyDocument /></el-icon>
                    </el-button>
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-card>
      </el-col>

      <!-- WebDAV 连接 -->
      <el-col :span="12">
        <el-card class="connection-card">
          <template #header>
            <div class="card-header">
              <el-icon><Folder /></el-icon>
              <span>WebDAV 文件同步</span>
            </div>
          </template>

          <div class="connection-info">
            <p class="description">
              WebDAV 协议支持文件浏览、下载和进度同步。主要用于 KOReader 的阅读进度同步。
            </p>

            <div class="url-box">
              <label>WebDAV 地址</label>
              <div class="url-row">
                <el-input
                  :model-value="webdavUrl"
                  readonly
                  class="url-input"
                />
                <el-button type="primary" @click="copyUrl(webdavUrl)">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
                <el-button @click="testConnection('webdav')">
                  测试
                </el-button>
              </div>
            </div>

            <div class="supported-clients">
              <label>支持的客户端</label>
              <div class="client-tags">
                <el-tag>KOReader (进度同步)</el-tag>
                <el-tag>Cyberduck</el-tag>
                <el-tag>文件管理器</el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- KOReader 设置指南 -->
    <el-card class="guide-card" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><InfoFilled /></el-icon>
          <span>KOReader 设置指南</span>
        </div>
      </template>

      <el-steps :active="1" direction="vertical" :space="60">
        <el-step title="添加 OPDS 书库" description="打开 KOReader → 左上角菜单 → 云存储 → 添加 OPDS 目录">
          <template #icon><el-icon><Edit /></el-icon></template>
        </el-step>
        <el-step title="输入服务器地址" :description="'输入 OPDS 地址: ' + opdsUrl">
          <template #icon><el-icon><Link /></el-icon></template>
        </el-step>
        <el-step title="输入用户名和密码" description="使用汗牛充栋的账号密码登录">
          <template #icon><el-icon><User /></el-icon></template>
        </el-step>
        <el-step title="浏览和下载书籍" description="成功连接后即可浏览书库、下载和阅读书籍">
          <template #icon><el-icon><Reading /></el-icon></template>
        </el-step>
        <el-step title="配置进度同步" description="在 KOReader 设置中启用 WebDAV 进度同步，输入 WebDAV 地址">
          <template #icon><el-icon><Refresh /></el-icon></template>
        </el-step>
      </el-steps>
    </el-card>

    <!-- 测试结果对话框 -->
    <el-dialog v-model="showTestResult" title="连接测试" width="400px">
      <div v-if="testResult" class="test-result">
        <el-result
          :icon="testResult.success ? 'success' : 'error'"
          :title="testResult.success ? '连接成功' : '连接失败'"
          :sub-title="testResult.message"
        />
      </div>
      <template #footer>
        <el-button @click="showTestResult = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Document,
  Folder,
  InfoFilled,
  CopyDocument,
  Edit,
  Link,
  User,
  Reading,
  Refresh,
} from '@element-plus/icons-vue'
import api from '@/utils/api'

const showTestResult = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

// 构建服务器地址
const serverUrl = computed(() => {
  return window.location.origin
})

const opdsUrl = computed(() => `${serverUrl.value}/opds`)
const opds2Url = computed(() => `${serverUrl.value}/opds/v2`)
const webdavUrl = computed(() => `${serverUrl.value}/webdav`)

const copyUrl = async (url: string) => {
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
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
}

.connections-view h2 {
  margin-bottom: 8px;
  font-size: 24px;
  font-weight: 600;
}

.subtitle {
  color: #666;
  margin-bottom: 24px;
}

.connection-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.connection-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.description {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}

.url-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.url-box label {
  font-size: 13px;
  color: #909399;
  font-weight: 500;
}

.url-row {
  display: flex;
  gap: 8px;
}

.url-input {
  flex: 1;
}

.url-input :deep(.el-input__inner) {
  font-family: monospace;
  font-size: 13px;
}

.supported-clients {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.supported-clients label {
  font-size: 13px;
  color: #909399;
  font-weight: 500;
}

.client-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.guide-card :deep(.el-step__title) {
  font-size: 15px;
}

.guide-card :deep(.el-step__description) {
  font-size: 13px;
}

.test-result {
  padding: 20px 0;
}
</style>
