<template>
  <div class="settings-view">
    <div class="page-header">
      <h2>系统设置</h2>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 扫描目录 -->
      <el-tab-pane label="扫描目录" name="directories">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>监控目录配置</span>
              <el-button type="primary" @click="handleAddDirectory">添加目录</el-button>
            </div>
          </template>

          <el-table :data="directories" style="width: 100%">
            <el-table-column prop="path" label="目录路径" />
            <el-table-column prop="lastScanTime" label="上次扫描" width="180" />
            <el-table-column prop="bookCount" label="书籍数量" width="100" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleScanDirectory(row)">
                  立即扫描
                </el-button>
                <el-button type="danger" link @click="handleRemoveDirectory(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 定时任务 -->
      <el-tab-pane label="定时任务" name="scheduler">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>定时扫描配置</span>
            </div>
          </template>

          <el-form :model="schedulerConfig" label-width="120px">
            <el-form-item label="启用定时扫描">
              <el-switch v-model="schedulerConfig.enabled" />
            </el-form-item>

            <el-form-item label="扫描时间">
              <el-time-picker
                v-model="schedulerConfig.time"
                format="HH:mm"
                placeholder="选择扫描时间"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleSaveScheduler">保存配置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 书源管理 -->
      <el-tab-pane label="书源管理" name="sources">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>书源配置</span>
              <div>
                <el-button @click="handleImportSource">导入</el-button>
                <el-button type="primary" @click="handleAddSource">添加书源</el-button>
              </div>
            </div>
          </template>

          <el-table :data="bookSources" style="width: 100%">
            <el-table-column prop="name" label="书源名称" />
            <el-table-column prop="url" label="书源地址" />
            <el-table-column prop="enabled" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleEditSource(row)">
                  编辑
                </el-button>
                <el-button type="danger" link @click="handleDeleteSource(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 系统信息 -->
      <el-tab-pane label="系统信息" name="info">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="系统版本">1.0.0</el-descriptions-item>
            <el-descriptions-item label="运行状态">
              <el-tag type="success">正常运行</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="数据库状态">
              <el-tag type="success">已连接</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="存储空间">
              <el-progress :percentage="60" :format="(p) => `${p}% 已使用`" />
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('directories')

const directories = ref([
  {
    id: 1,
    path: '/books/fiction',
    lastScanTime: '2024-01-01 10:00:00',
    bookCount: 150,
  },
  {
    id: 2,
    path: '/books/tech',
    lastScanTime: '2024-01-01 10:00:00',
    bookCount: 80,
  },
])

const schedulerConfig = reactive({
  enabled: true,
  time: new Date(2024, 0, 1, 2, 0, 0),
})

const bookSources = ref([
  {
    id: 1,
    name: '示例书源',
    url: 'https://example.com',
    enabled: true,
  },
])

const handleAddDirectory = () => {
  ElMessage.info('添加目录功能开发中')
}

const handleScanDirectory = (row: any) => {
  ElMessage.info(`正在扫描 ${row.path}`)
}

const handleRemoveDirectory = async (row: any) => {
  await ElMessageBox.confirm(`确定要删除目录 ${row.path} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  ElMessage.success('删除成功')
}

const handleSaveScheduler = () => {
  ElMessage.success('配置已保存')
}

const handleImportSource = () => {
  ElMessage.info('导入书源功能开发中')
}

const handleAddSource = () => {
  ElMessage.info('添加书源功能开发中')
}

const handleEditSource = (row: any) => {
  ElMessage.info(`编辑书源 ${row.name}`)
}

const handleDeleteSource = async (row: any) => {
  await ElMessageBox.confirm(`确定要删除书源 ${row.name} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  ElMessage.success('删除成功')
}
</script>

<style scoped>
.settings-view {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: #333;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
