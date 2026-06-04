<template>
  <div class="layout-container" :class="{ collapsed: sidebarCollapsed }">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo" @click="router.push('/')">
          <span class="logo-icon">📚</span>
          <Transition name="fade">
            <span v-if="!sidebarCollapsed" class="logo-text">汗牛充栋</span>
          </Transition>
        </div>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActiveRoute(item.path) }"
          :title="item.title"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <Transition name="fade">
            <span v-if="!sidebarCollapsed" class="nav-text">{{ item.title }}</span>
          </Transition>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <span :class="{ 'rotated': sidebarCollapsed }">◀</span>
        </button>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-wrapper">
      <!-- 顶部栏 -->
      <header class="top-bar">
        <div class="top-bar-left">
          <!-- 移动端菜单按钮 -->
          <button class="mobile-menu-btn" @click="showMobileMenu = !showMobileMenu">
            ☰
          </button>
        </div>

        <div class="top-bar-right">
          <div class="search-box">
            <span class="search-icon">🔍</span>
            <input
              v-model="searchKeyword"
              type="text"
              class="search-input"
              placeholder="搜索书籍..."
              @keyup.enter="handleSearch"
            />
          </div>

          <div class="user-menu" @click="showDropdown = !showDropdown">
            <div class="user-avatar">
              {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
            </div>

            <Transition name="fade">
              <div v-if="showDropdown" class="dropdown-menu">
                <div class="dropdown-item" @click="handleLogout">
                  <span class="dropdown-icon">🚪</span>
                  <span>退出登录</span>
                </div>
              </div>
            </Transition>
          </div>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="layout-main">
        <router-view v-slot="{ Component }">
          <Transition name="fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </router-view>
      </main>
    </div>

    <!-- 移动端侧边栏遮罩 -->
    <Transition name="fade">
      <div
        v-if="showMobileMenu"
        class="mobile-overlay"
        @click="showMobileMenu = false"
      />
    </Transition>

    <!-- 移动端侧边栏 -->
    <Transition name="slide">
      <aside v-if="showMobileMenu" class="mobile-sidebar">
        <div class="mobile-sidebar-header">
          <div class="logo">
            <span class="logo-icon">📚</span>
            <span class="logo-text">汗牛充栋</span>
          </div>
          <button class="close-btn" @click="showMobileMenu = false">✕</button>
        </div>

        <nav class="mobile-nav">
          <router-link
            v-for="item in menuItems"
            :key="item.path"
            :to="item.path"
            class="mobile-nav-item"
            :class="{ active: isActiveRoute(item.path) }"
            @click="showMobileMenu = false"
          >
            <span class="nav-icon">{{ item.icon }}</span>
            <span class="nav-text">{{ item.title }}</span>
          </router-link>
        </nav>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirm } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const searchKeyword = ref('')
const showDropdown = ref(false)
const sidebarCollapsed = ref(false)
const showMobileMenu = ref(false)

const menuItems = [
  { path: '/', icon: '🏠', title: '首页' },
  { path: '/books', icon: '📚', title: '书库' },
  { path: '/shelf', icon: '📖', title: '书架' },
  { path: '/connections', icon: '🔗', title: '连接' },
  { path: '/settings', icon: '⚙️', title: '设置' },
]

const isActiveRoute = (path: string) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

const handleSearch = () => {
  if (searchKeyword.value.trim()) {
    router.push({ path: '/books', query: { search: searchKeyword.value } })
  }
}

const handleLogout = async () => {
  const result = await confirm('确定要退出登录吗？')
  if (result) {
    userStore.logout()
    router.push('/login')
  }
  showDropdown.value = false
}

const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.user-menu')) {
    showDropdown.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>

<style scoped>
.layout-container {
  display: flex;
  min-height: 100vh;
  background: var(--bg-page-gradient);
}

/* 侧边栏 */
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 240px;
  background: var(--nav-bg);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  z-index: 100;
  transition: width var(--transition-normal);
}

.layout-container.collapsed .sidebar {
  width: 64px;
}

.sidebar-header {
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  height: 60px;
  display: flex;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.logo-text {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--text-primary);
}

.sidebar-nav {
  flex: 1;
  padding: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  text-decoration: none;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
  transition: all var(--transition-fast);
  overflow: hidden;
  white-space: nowrap;
}

.nav-item:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.nav-icon {
  font-size: 18px;
  flex-shrink: 0;
  width: 24px;
  text-align: center;
}

.sidebar-footer {
  padding: var(--spacing-sm);
  border-top: 1px solid var(--border-color);
}

.collapse-btn {
  width: 100%;
  padding: 8px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  font-size: 12px;
}

.collapse-btn:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.collapse-btn .rotated {
  display: inline-block;
  transform: rotate(180deg);
}

/* 主内容区 */
.main-wrapper {
  flex: 1;
  margin-left: 240px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left var(--transition-normal);
}

.layout-container.collapsed .main-wrapper {
  margin-left: 64px;
}

/* 顶部栏 */
.top-bar {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  height: 60px;
  background: var(--surface-card);
  border-bottom: 1px solid var(--border-color);
}

.top-bar-left {
  display: flex;
  align-items: center;
}

.mobile-menu-btn {
  display: none;
  padding: 8px;
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
  color: var(--text-primary);
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

/* 搜索框 */
.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 12px;
  font-size: 14px;
  pointer-events: none;
}

.search-input {
  width: 200px;
  padding: 8px 14px 8px 36px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  background: var(--surface-card);
  transition: all var(--transition-normal);
  outline: none;
  color: var(--text-primary);
}

.search-input:focus {
  width: 260px;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-10);
}

.search-input::placeholder {
  color: var(--text-tertiary);
}

/* 用户菜单 */
.user-menu {
  position: relative;
  display: flex;
  align-items: center;
  padding: 4px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.user-menu:hover {
  background: var(--surface-hover);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: var(--font-size-base);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: var(--spacing-sm);
  min-width: 160px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 12px 16px;
  color: var(--danger);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.dropdown-item:hover {
  background: var(--surface-hover);
}

.dropdown-icon {
  font-size: 16px;
}

/* 内容区 */
.layout-main {
  flex: 1;
  padding: var(--spacing-lg);
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
}

/* 移动端遮罩 */
.mobile-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 150;
}

/* 移动端侧边栏 */
.mobile-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 280px;
  background: var(--nav-bg);
  z-index: 200;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-xl);
}

.mobile-sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  height: 60px;
}

.close-btn {
  padding: 8px;
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-secondary);
  border-radius: var(--radius-md);
}

.close-btn:hover {
  background: var(--surface-hover);
}

.mobile-nav {
  flex: 1;
  padding: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mobile-nav-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: 12px 16px;
  border-radius: var(--radius-md);
  text-decoration: none;
  color: var(--text-secondary);
  font-size: var(--font-size-base);
  font-weight: 500;
  transition: all var(--transition-fast);
}

.mobile-nav-item:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.mobile-nav-item.active {
  background: var(--primary-alpha-10);
  color: var(--primary);
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(-100%);
}

/* 响应式 */
@media (max-width: 768px) {
  .sidebar {
    display: none;
  }

  .main-wrapper {
    margin-left: 0;
  }

  .layout-container.collapsed .main-wrapper {
    margin-left: 0;
  }

  .mobile-menu-btn {
    display: block;
  }

  .search-input {
    width: 140px;
  }

  .search-input:focus {
    width: 180px;
  }

  .layout-main {
    padding: var(--spacing-md);
  }
}
</style>
