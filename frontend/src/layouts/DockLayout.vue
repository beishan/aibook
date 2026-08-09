<template>
  <div class="layout-container" :style="dockStyle">
    <!-- 顶部工具栏 -->
    <header class="layout-header glass">
      <div class="header-left">
        <div class="logo" @click="router.push('/')">
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
            <img v-if="userStore.avatarObjectUrl" :src="userStore.avatarObjectUrl" alt="用户头像" />
            <template v-else>
              {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
            </template>
          </div>
          <span class="username">
            {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '用户' }}
          </span>

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
    <nav class="dock-nav" aria-label="主导航">
      <div
        ref="dockContainerRef"
        class="dock-container"
        @pointermove="handleDockPointerMove"
        @pointerleave="resetDockMagnification"
      >
        <router-link
          v-for="(item, index) in menuItems"
          :key="item.path"
          :to="item.path"
          class="dock-item"
          :class="{ active: isActiveRoute(item.path), separator: item.path === '/settings' }"
          :style="dockItemStyle(index)"
          :aria-label="item.title"
        >
          <span class="dock-icon-tile">
            <el-icon class="dock-icon" aria-hidden="true"><component :is="item.icon" /></el-icon>
          </span>
          <span class="dock-active-dot" aria-hidden="true"></span>
          <span class="dock-tooltip">{{ item.title }}</span>
        </router-link>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Collection, HomeFilled, Reading, Setting, Tools } from '@element-plus/icons-vue'
import { confirm } from '@/utils/message'
import { useUserStore } from '@/stores/user'
import { usePreferencesStore } from '@/stores/preferences'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const preferencesStore = usePreferencesStore()

const searchKeyword = ref('')
const showDropdown = ref(false)
const dockContainerRef = ref<HTMLElement | null>(null)
const dockScales = ref<number[]>([])

const menuItems = [
  { path: '/', icon: HomeFilled, title: '首页' },
  { path: '/books', icon: Collection, title: '书库' },
  { path: '/shelf', icon: Reading, title: '书架' },
  { path: '/text-repair', icon: Tools, title: '内容修复' },
  { path: '/settings', icon: Setting, title: '设置' },
]

const dockStyle = computed(() => ({
  '--dock-size': `${preferencesStore.dockSize}px`,
  '--dock-opacity': String(preferencesStore.dockOpacity / 100),
  '--dock-blur': `${preferencesStore.dockBlur}px`,
}))

const dockItemStyle = (index: number) => {
  const scale = dockScales.value[index] || 1
  const lift = (scale - 1) * preferencesStore.dockSize * 0.72
  return {
    '--dock-item-scale': String(scale),
    '--dock-item-lift': `${lift}px`,
  }
}

const resetDockMagnification = () => {
  dockScales.value = menuItems.map(() => 1)
}

const handleDockPointerMove = (event: PointerEvent) => {
  if (event.pointerType === 'touch' || !dockContainerRef.value) return
  const maxScale = preferencesStore.dockMagnification / 100
  const influenceRadius = preferencesStore.dockSize * 1.75
  const items = dockContainerRef.value.querySelectorAll<HTMLElement>('.dock-item')
  dockScales.value = Array.from(items, item => {
    const rect = item.getBoundingClientRect()
    const distance = Math.abs(event.clientX - (rect.left + rect.width / 2))
    const proximity = Math.max(0, 1 - distance / influenceRadius)
    return 1 + (maxScale - 1) * proximity * proximity
  })
}

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

onMounted(() => {
  resetDockMagnification()
  document.addEventListener('click', handleClickOutside)
})
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-page-gradient);
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  height: 60px;
  background: var(--nav-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: var(--glass-border);
  box-shadow: var(--glass-shadow);
  color: var(--nav-text-primary, var(--text-primary));
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
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

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
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  transition: all var(--transition-normal);
  outline: none;
  color: var(--text-primary);
}

.search-input:focus {
  width: 320px;
  background: var(--surface-elevated);
  box-shadow: 0 0 0 3px var(--primary-alpha-20);
}

.search-input::placeholder {
  color: var(--text-tertiary);
}

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
  overflow: hidden;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.username {
  font-weight: 500;
  color: var(--nav-text-primary, var(--text-primary));
  font-size: var(--font-size-sm);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: var(--spacing-sm);
  min-width: 160px;
  background: var(--surface-elevated);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
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

.layout-main {
  flex: 1;
  padding: var(--spacing-lg);
  padding-bottom: calc(var(--dock-size, 58px) + 58px);
  overflow-y: auto;
}

/* Dock 导航 */
.dock-nav {
  position: fixed;
  bottom: 18px;
  left: 50%;
  z-index: 1000;
  padding: 7px 10px 9px;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: calc(var(--dock-size) * 0.44);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.42), transparent 44%),
    rgba(244, 252, 248, var(--dock-opacity));
  box-shadow:
    0 22px 52px rgba(20, 73, 50, 0.2),
    0 5px 14px rgba(20, 73, 50, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.96),
    inset 0 -1px 0 rgba(69, 126, 100, 0.12);
  transform: translateX(-50%);
  backdrop-filter: blur(var(--dock-blur)) saturate(190%) contrast(102%);
  -webkit-backdrop-filter: blur(var(--dock-blur)) saturate(190%) contrast(102%);
  isolation: isolate;
}

.dock-nav::before {
  content: '';
  position: absolute;
  inset: 1px 8% auto;
  z-index: -1;
  height: 45%;
  border-radius: inherit;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.56), rgba(255, 255, 255, 0));
  pointer-events: none;
}

.dock-nav::after {
  content: '';
  position: absolute;
  inset: 6px;
  z-index: -2;
  border-radius: calc(var(--dock-size) * 0.34);
  box-shadow: inset 0 0 18px rgba(255, 255, 255, 0.2);
  pointer-events: none;
}

.dock-container {
  display: flex;
  align-items: flex-end;
  gap: 6px;
}

.dock-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--dock-size);
  height: var(--dock-size);
  margin-top: 0;
  text-decoration: none;
  color: var(--text-secondary);
  transform: translateY(calc(-1 * var(--dock-item-lift, 0px))) scale(var(--dock-item-scale, 1));
  transform-origin: bottom center;
  transition: transform 90ms cubic-bezier(0.2, 0.8, 0.2, 1);
  cursor: pointer;
  will-change: transform;
}

.dock-icon-tile {
  position: relative;
  display: grid;
  width: calc(var(--dock-size) - 4px);
  height: calc(var(--dock-size) - 4px);
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 26%;
  background:
    radial-gradient(circle at 25% 16%, rgba(255, 255, 255, 0.96), transparent 36%),
    linear-gradient(145deg, rgba(251, 255, 253, 0.86), rgba(190, 227, 209, 0.72));
  box-shadow:
    0 7px 16px rgba(22, 83, 56, 0.17),
    inset 0 1px 0 rgba(255, 255, 255, 0.96),
    inset 0 -1px 2px rgba(59, 123, 92, 0.1);
  transition: filter 180ms ease, box-shadow 180ms ease;
}

.dock-icon-tile::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(120deg, rgba(255, 255, 255, 0.28), transparent 42%);
  pointer-events: none;
}

.dock-item:nth-child(1) .dock-icon-tile {
  background: radial-gradient(circle at 24% 14%, #ffffff, transparent 38%),
    linear-gradient(145deg, rgba(229, 249, 239, 0.94), rgba(137, 205, 171, 0.78));
}

.dock-item:nth-child(1) .dock-icon {
  color: #176b4b;
}

.dock-item:nth-child(2) .dock-icon-tile {
  background: radial-gradient(circle at 24% 14%, #ffffff, transparent 38%),
    linear-gradient(145deg, rgba(244, 250, 221, 0.95), rgba(178, 218, 151, 0.8));
}

.dock-item:nth-child(2) .dock-icon {
  color: #52712e;
}

.dock-item:nth-child(3) .dock-icon-tile {
  background: radial-gradient(circle at 24% 14%, #ffffff, transparent 38%),
    linear-gradient(145deg, rgba(224, 248, 244, 0.95), rgba(120, 197, 185, 0.78));
}

.dock-item:nth-child(3) .dock-icon {
  color: #176d68;
}

.dock-item:nth-child(4) .dock-icon-tile {
  background: radial-gradient(circle at 24% 14%, #ffffff, transparent 38%),
    linear-gradient(145deg, rgba(246, 242, 226, 0.95), rgba(211, 187, 143, 0.76));
}

.dock-item:nth-child(4) .dock-icon {
  color: #765629;
}

.dock-item:nth-child(5) .dock-icon-tile {
  background: radial-gradient(circle at 24% 14%, #ffffff, transparent 38%),
    linear-gradient(145deg, rgba(235, 241, 247, 0.95), rgba(165, 187, 199, 0.78));
}

.dock-item:nth-child(5) .dock-icon {
  color: #405c69;
}

.dock-item:hover .dock-icon-tile,
.dock-item.active .dock-icon-tile {
  filter: saturate(112%) brightness(1.025);
  box-shadow:
    0 9px 22px rgba(18, 76, 50, 0.23),
    inset 0 1px 0 white,
    inset 0 -1px 2px rgba(59, 123, 92, 0.1);
}

.dock-icon {
  position: relative;
  z-index: 1;
  width: calc(var(--dock-size) * 0.46);
  height: calc(var(--dock-size) * 0.46);
  font-size: calc(var(--dock-size) * 0.46);
  line-height: 1;
  filter: drop-shadow(0 1px 1px rgba(17, 61, 43, 0.12));
  transition: color 180ms ease, transform 180ms ease;
}

.dock-icon :deep(svg) {
  width: 100%;
  height: 100%;
  shape-rendering: geometricPrecision;
}

.dock-item.active .dock-icon {
  color: var(--primary-dark);
}

.dock-item:hover .dock-icon {
  transform: translateY(-1px) scale(1.04);
}

.dock-active-dot {
  position: absolute;
  bottom: -6px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: transparent;
  box-shadow: none;
}

.dock-item.active .dock-active-dot {
  background: var(--primary-dark);
  box-shadow: 0 0 5px var(--primary-alpha-30);
}

.dock-item.separator {
  margin-left: 13px;
}

.dock-item.separator::before {
  content: '';
  position: absolute;
  top: 9%;
  right: calc(100% + 9px);
  width: 1px;
  height: 72%;
  background: rgba(64, 110, 89, 0.22);
  box-shadow: 1px 0 rgba(255, 255, 255, 0.54);
}

.dock-tooltip {
  position: absolute;
  bottom: calc(100% + 12px);
  left: 50%;
  transform: translateX(-50%);
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  background: rgba(28, 57, 44, 0.82);
  color: white;
  font-size: 12px;
  font-weight: 500;
  border-radius: 9px;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s ease;
  box-shadow: 0 8px 22px rgba(15, 52, 36, 0.18);
  backdrop-filter: blur(12px);
  pointer-events: none;
}

.dock-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 6px solid transparent;
  border-top-color: rgba(28, 57, 44, 0.82);
}

.dock-item:hover .dock-tooltip {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(-4px);
}

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
    bottom: 12px;
    padding: 6px 8px 8px;
  }

  .dock-item {
    width: min(var(--dock-size), 52px);
    height: min(var(--dock-size), 52px);
    margin-top: 0;
    transform: none;
  }

  .dock-icon-tile {
    width: calc(min(var(--dock-size), 52px) - 4px);
    height: calc(min(var(--dock-size), 52px) - 4px);
  }

  .layout-main {
    padding-bottom: 90px;
  }
}

@media (max-width: 480px) {
  .dock-item {
    width: min(var(--dock-size), 46px);
    height: min(var(--dock-size), 46px);
  }

  .dock-icon-tile {
    width: calc(min(var(--dock-size), 46px) - 4px);
    height: calc(min(var(--dock-size), 46px) - 4px);
  }

  .dock-item.separator {
    margin-left: 8px;
  }

  .dock-item.separator::before {
    right: calc(100% + 6px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .dock-item,
  .dock-icon,
  .dock-tooltip {
    transition: none;
  }
}
</style>
