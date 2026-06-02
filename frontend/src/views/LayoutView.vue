<template>
  <div class="layout-container">
    <!-- 顶部工具栏 -->
    <header class="layout-header glass">
      <div class="header-left">
        <div class="logo">
          <span class="logo-icon">📚</span>
          <span class="logo-text">汗牛充栋</span>
        </div>
      </div>

      <div class="header-right">
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
          <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>

          <Transition name="fade">
            <div v-if="showDropdown" class="dropdown-menu glass">
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

    <!-- 底部 Dock 导航栏 -->
    <nav class="dock-nav glass">
      <div class="dock-container">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="dock-item"
          :class="{ active: isActiveRoute(item.path) }"
        >
          <span class="dock-icon">{{ item.icon }}</span>
          <span class="dock-tooltip">{{ item.title }}</span>
        </router-link>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirm } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const searchKeyword = ref('')
const showDropdown = ref(false)

const menuItems = [
  { path: '/', icon: '🏠', title: '首页' },
  { path: '/books', icon: '📚', title: '书库' },
  { path: '/shelf', icon: '📖', title: '书架' },
  { path: '/connections', icon: '🔗', title: '连接' },
  { path: '/settings', icon: '⚙️', title: '设置' },
]

const isActiveRoute = (path: string) => {
  if (path === '/') {
    return route.path === '/'
  }
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

// 点击外部关闭下拉菜单
const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.user-menu')) {
    showDropdown.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

/* 顶部工具栏 */
.layout-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  height: 60px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
}

.logo-icon {
  font-size: 28px;
}

.logo-text {
  font-size: var(--font-size-xl);
  font-weight: 700;
  background: linear-gradient(135deg, #007AFF 0%, #5AC8FA 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 右侧区域 */
.header-right {
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
  left: 14px;
  font-size: 14px;
  pointer-events: none;
}

.search-input {
  width: 240px;
  padding: 10px 16px 10px 40px;
  border: none;
  border-radius: var(--radius-full);
  font-size: var(--font-size-sm);
  background: var(--bg-secondary);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: all var(--transition-normal);
  outline: none;
}

.search-input:focus {
  width: 320px;
  background: var(--bg-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.2);
}

.search-input::placeholder {
  color: var(--text-tertiary);
}

/* 用户菜单 */
.user-menu {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 6px 12px 6px 6px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.user-menu:hover {
  background: rgba(255, 255, 255, 0.5);
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

.username {
  font-weight: 500;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

/* 下拉菜单 */
.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: var(--spacing-sm);
  min-width: 160px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: var(--radius-lg);
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
  background: rgba(255, 59, 48, 0.1);
}

.dropdown-icon {
  font-size: 16px;
}

/* 内容区 */
.layout-main {
  flex: 1;
  padding: var(--spacing-lg);
  padding-bottom: 100px; /* 为底部 Dock 留出空间 */
  overflow-y: auto;
}

/* 底部 Dock 导航栏 */
.dock-nav {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 28px;
  padding: 8px 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12),
              0 0 0 1px rgba(255, 255, 255, 0.1) inset;
}

.dock-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dock-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  text-decoration: none;
  color: var(--text-secondary);
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  cursor: pointer;
}

.dock-item:hover {
  transform: translateY(-8px) scale(1.15);
  background: rgba(0, 122, 255, 0.15);
}

.dock-item.active {
  transform: translateY(-8px) scale(1.15);
  background: var(--primary-gradient);
  color: white;
  box-shadow: 0 8px 20px rgba(0, 122, 255, 0.4);
}

.dock-icon {
  font-size: 28px;
  transition: transform 0.2s ease;
}

.dock-item:hover .dock-icon {
  transform: scale(1.1);
}

/* 工具提示 */
.dock-tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  padding: 6px 12px;
  background: rgba(0, 0, 0, 0.8);
  color: white;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s ease;
  margin-bottom: 12px;
  pointer-events: none;
}

.dock-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 6px solid transparent;
  border-top-color: rgba(0, 0, 0, 0.8);
}

.dock-item:hover .dock-tooltip {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(-4px);
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 响应式 */
@media (max-width: 768px) {
  .layout-header {
    padding: 0 var(--spacing-md);
  }

  .search-input {
    width: 160px;
  }

  .search-input:focus {
    width: 200px;
  }

  .username {
    display: none;
  }

  .dock-nav {
    bottom: 16px;
    padding: 6px 12px;
  }

  .dock-item {
    width: 48px;
    height: 48px;
    border-radius: 12px;
  }

  .dock-icon {
    font-size: 24px;
  }

  .layout-main {
    padding-bottom: 90px;
  }
}

@media (max-width: 480px) {
  .dock-item {
    width: 44px;
    height: 44px;
  }

  .dock-icon {
    font-size: 20px;
  }
}
</style>
