<template>
  <el-container class="modern-layout" :class="{ collapsed: sidebarCollapsed }">
    <el-aside class="modern-sidebar" :width="sidebarWidth">
      <el-button text class="brand-button" @click="router.push('/')">
        <el-icon :size="24"><Reading /></el-icon>
        <span v-if="!sidebarCollapsed" class="brand-name">汗牛充栋</span>
      </el-button>

      <el-menu
        class="desktop-menu"
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        @select="handleMenuSelect"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <el-button
          text
          class="collapse-button"
          :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <el-icon>
            <Expand v-if="sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
          <span v-if="!sidebarCollapsed">收起侧边栏</span>
        </el-button>
      </div>
    </el-aside>

    <el-container class="modern-workspace">
      <el-header class="modern-header">
        <el-button
          text
          class="mobile-menu-button"
          aria-label="打开导航菜单"
          @click="showMobileMenu = true"
        >
          <el-icon :size="20"><Menu /></el-icon>
        </el-button>

        <div class="header-actions">
          <el-input
            v-model="searchKeyword"
            class="global-search"
            clearable
            placeholder="搜索书籍"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-dropdown trigger="click" @command="handleUserCommand">
            <el-button text circle class="user-button" aria-label="用户菜单">
              <el-avatar :size="34" class="user-avatar">
                {{ userInitial }}
              </el-avatar>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" :icon="SwitchButton">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="modern-main">
        <div class="content-shell" :class="{ 'library-content-shell': route.path === '/books' }">
          <router-view v-slot="{ Component }">
            <Transition name="fade" mode="out-in">
              <component :is="Component" />
            </Transition>
          </router-view>
        </div>
      </el-main>
    </el-container>

    <el-drawer
      v-model="showMobileMenu"
      direction="ltr"
      size="280px"
      :with-header="false"
      class="modern-mobile-drawer"
    >
      <div class="mobile-brand">
        <div class="mobile-brand-title">
          <el-icon :size="24"><Reading /></el-icon>
          <span>汗牛充栋</span>
        </div>
        <el-button
          text
          circle
          aria-label="关闭导航菜单"
          @click="showMobileMenu = false"
        >
          <el-icon><Close /></el-icon>
        </el-button>
      </div>

      <el-menu
        class="mobile-menu"
        :default-active="activeMenu"
        @select="handleMenuSelect"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Close,
  Collection,
  Expand,
  Fold,
  FolderOpened,
  House,
  Menu,
  PriceTag,
  Reading,
  Search,
  Setting,
  SwitchButton,
  Tools,
} from '@element-plus/icons-vue'
import { confirm } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const searchKeyword = ref('')
const sidebarCollapsed = ref(false)
const showMobileMenu = ref(false)

const menuItems = [
  { path: '/', icon: House, title: '首页' },
  { path: '/books', icon: Collection, title: '书库' },
  { path: '/shelf', icon: Reading, title: '书架' },
  { path: '/categories', icon: FolderOpened, title: '分类' },
  { path: '/tags', icon: PriceTag, title: '标签' },
  { path: '/settings/text-repair', icon: Tools, title: '内容修复' },
  { path: '/settings', icon: Setting, title: '设置' },
]

const sidebarWidth = computed(() => (sidebarCollapsed.value ? '64px' : '224px'))

const activeMenu = computed(() => {
  const matched = menuItems.find(item =>
    item.path === '/' ? route.path === '/' : route.path.startsWith(item.path)
  )
  return matched?.path || ''
})

const userInitial = computed(
  () => userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U'
)

const handleMenuSelect = (path: string) => {
  showMobileMenu.value = false
  if (route.path !== path) {
    router.push(path)
  }
}

const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  if (keyword) {
    router.push({ path: '/books', query: { search: keyword } })
  }
}

const handleUserCommand = (command: string) => {
  if (command === 'logout') {
    void handleLogout()
  }
}

const handleLogout = async () => {
  const result = await confirm('确定要退出登录吗？')
  if (result) {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.modern-layout {
  min-height: 100vh;
  background: var(--bg-page);
}

.modern-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--nav-bg);
  border-right: 1px solid var(--border-color);
  transition: width var(--transition-normal);
}

.brand-button {
  justify-content: flex-start;
  width: calc(100% - 16px);
  height: 60px;
  margin: 0 8px;
  padding: 0 12px;
  overflow: hidden;
  color: var(--text-primary);
  border-radius: var(--radius-md);
}

.brand-button:hover {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.brand-name {
  margin-left: 10px;
  overflow: hidden;
  font-size: var(--font-size-lg);
  font-weight: 700;
  white-space: nowrap;
}

.desktop-menu,
.mobile-menu {
  flex: 1;
  border-right: none;
  background: transparent;
}

.desktop-menu:not(.el-menu--collapse) {
  width: 100%;
}

.desktop-menu :deep(.el-menu-item),
.mobile-menu :deep(.el-menu-item) {
  height: 44px;
  margin: 3px 8px;
  color: var(--text-secondary);
  border-radius: var(--radius-md);
}

.desktop-menu :deep(.el-menu-item:hover),
.mobile-menu :deep(.el-menu-item:hover) {
  color: var(--text-primary);
  background: var(--surface-hover);
}

.desktop-menu :deep(.el-menu-item.is-active),
.mobile-menu :deep(.el-menu-item.is-active) {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.desktop-menu.el-menu--collapse :deep(.el-menu-item) {
  justify-content: center;
  padding: 0;
}

.sidebar-footer {
  padding: 8px;
  border-top: 1px solid var(--border-color);
}

.collapse-button {
  justify-content: flex-start;
  width: 100%;
  color: var(--text-tertiary);
}

.collapse-button span {
  margin-left: 8px;
}

.modern-workspace {
  min-width: 0;
  min-height: 100vh;
  margin-left: 224px;
  transition: margin-left var(--transition-normal);
}

.collapsed .modern-workspace {
  margin-left: 64px;
}

.modern-header {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 var(--spacing-lg);
  background: var(--surface-card);
  border-bottom: 1px solid var(--border-color);
}

.mobile-menu-button {
  display: none;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.global-search {
  width: 220px;
  transition: width var(--transition-normal);
}

.global-search:focus-within {
  width: 280px;
}

.global-search :deep(.el-input__wrapper) {
  background: var(--bg-page);
  box-shadow: 0 0 0 1px var(--border-color) inset;
}

.global-search :deep(.el-input__wrapper.is-focus) {
  background: var(--surface-card);
  box-shadow: 0 0 0 1px var(--primary) inset;
}

.user-button {
  width: 42px;
  height: 42px;
}

.user-avatar {
  color: #ffffff;
  background: var(--primary);
}

.modern-main {
  padding: var(--spacing-lg);
  overflow: visible;
}

.content-shell {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.content-shell.library-content-shell {
  max-width: none;
}

.mobile-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 8px 0 12px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
}

.mobile-brand-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

:global(.modern-mobile-drawer .el-drawer__body) {
  display: flex;
  flex-direction: column;
  padding: 0;
  background: var(--nav-bg);
}

@media (max-width: 768px) {
  .modern-sidebar {
    display: none;
  }

  .modern-workspace,
  .collapsed .modern-workspace {
    margin-left: 0;
  }

  .mobile-menu-button {
    display: inline-flex;
  }

  .global-search {
    width: 160px;
  }

  .global-search:focus-within {
    width: 190px;
  }

  .modern-main {
    padding: var(--spacing-md);
  }
}

@media (max-width: 480px) {
  .modern-header {
    padding: 0 var(--spacing-sm);
  }

  .global-search {
    width: 128px;
  }

  .global-search:focus-within {
    width: 150px;
  }
}
</style>
