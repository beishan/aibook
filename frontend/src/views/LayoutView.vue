<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo" @click="isCollapse = !isCollapse">
        <span v-if="!isCollapse">汗牛充栋</span>
        <span v-else>汗</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :router="true"
        class="aside-menu"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-menu-item index="/books">
          <el-icon><Document /></el-icon>
          <template #title>书库</template>
        </el-menu-item>

        <el-menu-item index="/shelf">
          <el-icon><Collection /></el-icon>
          <template #title>书架</template>
        </el-menu-item>

        <el-menu-item index="/connections">
          <el-icon><Connection /></el-icon>
          <template #title>连接</template>
        </el-menu-item>

        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>设置</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>

        <div class="header-center">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索书籍..."
            prefix-icon="Search"
            clearable
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="32" icon="User" />
              <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  HomeFilled,
  Document,
  Collection,
  Setting,
  Fold,
  Expand,
  Connection,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const searchKeyword = ref('')

const activeMenu = computed(() => route.path)

const handleSearch = () => {
  if (searchKeyword.value.trim()) {
    router.push({ path: '/books', query: { search: searchKeyword.value } })
  }
}

const handleLogout = async () => {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
  cursor: pointer;
  background-color: #263445;
}

.aside-menu {
  border-right: none;
  background-color: #304156;
}

.aside-menu:not(.el-menu--collapse) {
  width: 220px;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: white;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #666;
}

.collapse-btn:hover {
  color: #409eff;
}

.header-center {
  flex: 1;
  max-width: 500px;
  margin: 0 20px;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.username {
  margin-left: 8px;
  color: #666;
}

.layout-main {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>
