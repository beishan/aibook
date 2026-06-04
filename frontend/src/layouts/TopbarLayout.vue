<template>
  <div class="layout-container">
    <!-- 顶部导航栏 -->
    <header class="layout-header">
      <div class="header-content">
        <div class="header-left">
          <div class="logo" @click="router.push('/')">
            <span class="logo-icon">📚</span>
            <span class="logo-text">汗牛充栋</span>
          </div>
        </div>

        <nav class="header-nav">
          <router-link
            v-for="item in menuItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActiveRoute(item.path) }"
          >
            <span class="nav-icon">{{ item.icon }}</span>
            <span class="nav-text">{{ item.title }}</span>
          </router-link>
        </nav>

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
              <div v-if="showDropdown" class="dropdown-menu">
                <div class="dropdown-item" @click="handleLogout">
                  <span class="dropdown-icon">🚪</span>
                  <span>退出登录</span>
                </div>
              </div>
            </Transition>
          </div>
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
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-page-gradient);
}

/* 顶部导航栏 */
.layout-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--nav-bg);
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--spacing-lg);
  height: 64px;
}

.header-left {
  display: flex;
  align-items: center;
  flex-shrink: 0;
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
  color: var(--text-primary);
}

/* 水平导航 */
.header-nav {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-xl);
  text-decoration: none;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
  transition: all var(--transition-fast);
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
  font-size: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-shrink: 0;
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
  border-radius: var(--radius-xl);
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
  gap: var(--spacing-sm);
  padding: 6px 12px 6px 6px;
  border-radius: var(--radius-xl);
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

.username {
  font-weight: 500;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: var(--spacing-sm);
  min-width: 160px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-color);
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
  margin: 0 auto;
  width: 100%;
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
  .header-content {
    padding: 0 var(--spacing-md);
    height: 56px;
  }

  .header-nav {
    display: none;
  }

  .search-input {
    width: 140px;
  }

  .search-input:focus {
    width: 180px;
  }

  .username {
    display: none;
  }

  .layout-main {
    padding: var(--spacing-md);
    padding-bottom: 80px;
  }

  /* 移动端底部导航 */
  .layout-container::after {
    content: '';
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: 60px;
    background: var(--nav-bg);
    border-top: 1px solid var(--border-color);
    z-index: 100;
  }
}
</style>
