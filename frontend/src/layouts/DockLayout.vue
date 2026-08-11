<template>
  <div class="layout-container" :style="dockStyle">
    <!-- 内容区 -->
    <main class="layout-main" :class="{ 'layout-main--reader': isReaderRoute }">
      <router-view v-slot="{ Component }">
        <Transition name="fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </main>

    <!-- 底部 Dock 导航栏 -->
    <nav v-if="!isReaderRoute" class="dock-nav" aria-label="主导航">
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
          :class="{ active: isActiveRoute(item.path) }"
          :style="dockItemStyle(index)"
          :aria-label="item.title"
        >
          <span
            class="dock-icon-tile"
            :class="{ 'dock-icon-tile--custom': preferencesStore.dockIconStyle === 'custom' }"
          >
            <DockIcon
              class="dock-icon"
              :name="item.icon"
              :variant="preferencesStore.dockIconStyle"
              :custom-src="dockIconStore.iconUrls[item.icon]"
              aria-hidden="true"
            />
          </span>
          <span class="dock-active-dot" aria-hidden="true"></span>
          <span class="dock-tooltip">{{ item.title }}</span>
        </router-link>

        <div class="dock-trash-entry" :class="{ open: showTrashMenu }">
          <button
            type="button"
            class="dock-item dock-trash-item"
            :style="dockItemStyle(menuItems.length)"
            aria-haspopup="dialog"
            :aria-expanded="showTrashMenu"
            aria-label="回收站"
            @click.stop="toggleTrashMenu"
          >
            <span
              class="dock-icon-tile"
              :class="{ 'dock-icon-tile--custom': preferencesStore.dockIconStyle === 'custom' }"
            >
              <DockIcon
                class="dock-icon"
                :name="trashIconName"
                :variant="preferencesStore.dockIconStyle"
                :custom-src="trashCustomIconUrl"
                aria-hidden="true"
              />
            </span>
            <span v-if="bookStore.trashCount > 0" class="dock-trash-badge">
              {{ bookStore.trashCount > 99 ? '99+' : bookStore.trashCount }}
            </span>
            <span class="dock-tooltip">回收站</span>
          </button>

          <Transition name="dock-trash-window">
            <section
              v-if="showTrashMenu"
              class="dock-trash-window"
              role="dialog"
              aria-modal="false"
              aria-labelledby="dock-trash-title"
              @click.stop
              @pointermove.stop
            >
              <header class="dock-trash-header">
                <div>
                  <strong id="dock-trash-title">系统回收站</strong>
                  <small>恢复书籍，或从系统中永久清理记录</small>
                </div>
                <button type="button" class="dock-trash-close" aria-label="关闭回收站" @click="showTrashMenu = false">✕</button>
              </header>
              <div class="dock-trash-body">
                <RecycleBinPanel compact @changed="refreshTrashCount" />
              </div>
            </section>
          </Transition>
        </div>

        <div class="dock-user-entry" :class="{ open: showUserMenu }">
          <button
            type="button"
            class="dock-item dock-user-item"
            :style="dockItemStyle(menuItems.length + 1)"
            aria-haspopup="menu"
            :aria-expanded="showUserMenu"
            aria-label="用户菜单"
            @click.stop="toggleUserMenu"
          >
            <span class="dock-user-avatar">
              <img v-if="userStore.avatarObjectUrl" :src="userStore.avatarObjectUrl" alt="" />
              <span v-else>{{ userInitial }}</span>
            </span>
            <span class="dock-tooltip">{{ displayName }}</span>
          </button>

          <Transition name="dock-menu">
            <div
              v-if="showUserMenu"
              class="dock-user-menu"
              role="menu"
              @pointermove.stop
            >
              <div class="dock-user-summary">
                <span class="dock-menu-avatar">
                  <img v-if="userStore.avatarObjectUrl" :src="userStore.avatarObjectUrl" alt="" />
                  <span v-else>{{ userInitial }}</span>
                </span>
                <span class="dock-user-copy">
                  <strong>{{ displayName }}</strong>
                  <small>{{ userStore.userInfo?.email || '个人账号' }}</small>
                </span>
              </div>
              <button type="button" class="dock-menu-item" role="menuitem" @click="openProfile">
                <span>👤</span>
                <span>个人设置</span>
              </button>
              <button
                type="button"
                class="dock-menu-item danger"
                role="menuitem"
                @click="handleLogout"
              >
                <span>🚪</span>
                <span>退出登录</span>
              </button>
            </div>
          </Transition>
        </div>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DockIcon, { type DockIconName } from '@/components/DockIcon.vue'
import { confirm } from '@/utils/message'
import { useUserStore } from '@/stores/user'
import { usePreferencesStore } from '@/stores/preferences'
import { useDockIconStore } from '@/stores/dockIcons'
import { useBookStore } from '@/stores/book'
import RecycleBinPanel from '@/components/RecycleBinPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const preferencesStore = usePreferencesStore()
const dockIconStore = useDockIconStore()
const bookStore = useBookStore()

const isReaderRoute = computed(() => route.name === 'Reader')

const showUserMenu = ref(false)
const showTrashMenu = ref(false)
const dockContainerRef = ref<HTMLElement | null>(null)
const dockScales = ref<number[]>([])

const menuItems: Array<{ path: string; icon: DockIconName; title: string }> = [
  { path: '/', icon: 'home', title: '首页' },
  { path: '/books', icon: 'library', title: '书库' },
  { path: '/shelf', icon: 'shelf', title: '书架' },
  { path: '/text-repair', icon: 'repair', title: '内容修复' },
  { path: '/settings', icon: 'settings', title: '设置' },
]

const dockStyle = computed(() => ({
  '--dock-size': `${preferencesStore.dockSize}px`,
  '--dock-opacity': String(preferencesStore.dockOpacity / 100),
  '--dock-blur': `${preferencesStore.dockBlur}px`,
}))

const userInitial = computed(
  () => userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U'
)

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户'
)

const trashIconName = computed<DockIconName>(() =>
  bookStore.trashCount > 0 ? 'trashFull' : 'trashEmpty'
)

const trashCustomIconUrl = computed(() =>
  dockIconStore.iconUrls[trashIconName.value] || dockIconStore.iconUrls.trash
)

const dockItemStyle = (index: number) => {
  const scale = dockScales.value[index] || 1
  const lift = (scale - 1) * preferencesStore.dockSize * 0.72
  return {
    '--dock-item-scale': String(scale),
    '--dock-item-lift': `${lift}px`,
  }
}

const resetDockMagnification = () => {
  dockScales.value = Array.from({ length: menuItems.length + 2 }, () => 1)
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

const openProfile = () => {
  showUserMenu.value = false
  void router.push({ path: '/settings', query: { tab: 'profile' } })
}

const refreshTrashCount = () => {
  void bookStore.fetchTrashCount().catch(() => undefined)
}

const toggleTrashMenu = () => {
  showTrashMenu.value = !showTrashMenu.value
  showUserMenu.value = false
  if (showTrashMenu.value) refreshTrashCount()
}

const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
  showTrashMenu.value = false
}

const handleLogout = async () => {
  const result = await confirm('确定要退出登录吗？')
  if (result) {
    userStore.logout()
    router.push('/login')
  }
  showUserMenu.value = false
}

const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.dock-user-entry')) {
    showUserMenu.value = false
  }
  if (!target.closest('.dock-trash-entry')) {
    showTrashMenu.value = false
  }
}

const handleEscape = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    showUserMenu.value = false
    showTrashMenu.value = false
  }
}

onMounted(() => {
  resetDockMagnification()
  void dockIconStore.hydrate()
  refreshTrashCount()
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleEscape)
})
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleEscape)
})
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-page-gradient);
}

.layout-main {
  flex: 1;
  padding: var(--spacing-lg);
  padding-bottom: calc(var(--dock-size, 58px) + 58px);
  overflow-y: auto;
}

.layout-main.layout-main--reader {
  padding: 0;
  overflow: hidden;
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

.dock-user-entry {
  position: relative;
  display: flex;
  width: var(--dock-size);
  height: var(--dock-size);
  align-items: flex-end;
  justify-content: center;
  margin-left: 13px;
  flex: 0 0 var(--dock-size);
}

.dock-trash-entry {
  position: relative;
  display: flex;
  width: var(--dock-size);
  height: var(--dock-size);
  align-items: flex-end;
  justify-content: center;
  flex: 0 0 var(--dock-size);
}

.dock-trash-item {
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
}

.dock-trash-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  z-index: 3;
  display: grid;
  min-width: 19px;
  height: 19px;
  padding: 0 5px;
  place-items: center;
  border: 2px solid rgba(245, 252, 248, 0.94);
  border-radius: 999px;
  background: var(--danger);
  color: white;
  font-size: 10px;
  font-weight: 750;
  line-height: 1;
}

.dock-trash-window {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 30;
  width: min(900px, calc(100vw - 32px));
  max-height: min(680px, calc(100vh - 130px));
  overflow: visible;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 22px;
  background: color-mix(in srgb, var(--surface-elevated) 92%, transparent);
  box-shadow: 0 24px 62px rgba(17, 61, 43, 0.28);
  color: var(--text-primary);
  transform: translate(-50%, -50%);
  backdrop-filter: blur(26px) saturate(170%);
  -webkit-backdrop-filter: blur(26px) saturate(170%);
}

.dock-trash-window::after {
  display: none;
}

.dock-trash-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 17px 20px 14px;
  border-bottom: 1px solid var(--border-color-light);
}

.dock-trash-header div {
  display: grid;
  gap: 3px;
}

.dock-trash-header strong {
  font-size: 16px;
}

.dock-trash-header small {
  color: var(--text-tertiary);
  font-size: 11px;
}

.dock-trash-close {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--surface-hover);
  color: var(--text-secondary);
  cursor: pointer;
}

.dock-trash-body {
  max-height: min(606px, calc(100vh - 204px));
  padding: 16px;
  overflow: auto;
}

.dock-user-entry::before {
  position: absolute;
  top: 9%;
  right: calc(100% + 9px);
  width: 1px;
  height: 72%;
  background: rgba(64, 110, 89, 0.22);
  box-shadow: 1px 0 rgba(255, 255, 255, 0.54);
  content: '';
}

.dock-user-item {
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
}

.dock-user-avatar,
.dock-menu-avatar {
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.92);
  border-radius: 50%;
  background: var(--primary-gradient);
  color: #fff;
  font-weight: 700;
  box-shadow:
    0 7px 16px rgba(22, 83, 56, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.dock-user-avatar {
  width: calc(var(--dock-size) - 6px);
  height: calc(var(--dock-size) - 6px);
  font-size: calc(var(--dock-size) * 0.34);
}

.dock-user-avatar img,
.dock-menu-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dock-user-item:hover .dock-user-avatar,
.dock-user-entry.open .dock-user-avatar {
  box-shadow:
    0 9px 22px rgba(18, 76, 50, 0.28),
    0 0 0 3px var(--primary-alpha-20);
}

.dock-user-menu {
  position: absolute;
  right: -8px;
  bottom: calc(100% + 22px);
  z-index: 20;
  width: 230px;
  padding: 8px;
  overflow: visible;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 18px;
  background: color-mix(in srgb, var(--surface-elevated) 88%, transparent);
  box-shadow: 0 20px 46px rgba(17, 61, 43, 0.24);
  color: var(--text-primary);
  backdrop-filter: blur(22px) saturate(160%);
  -webkit-backdrop-filter: blur(22px) saturate(160%);
}

.dock-user-menu::after {
  position: absolute;
  right: calc(var(--dock-size) / 2 - 1px);
  bottom: -6px;
  width: 12px;
  height: 12px;
  border-right: 1px solid rgba(255, 255, 255, 0.72);
  border-bottom: 1px solid rgba(255, 255, 255, 0.72);
  background: color-mix(in srgb, var(--surface-elevated) 92%, transparent);
  content: '';
  transform: rotate(45deg);
}

.dock-user-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 12px;
  border-bottom: 1px solid var(--border-color-light);
  margin-bottom: 6px;
}

.dock-menu-avatar {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  font-size: 15px;
}

.dock-user-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
  text-align: left;
}

.dock-user-copy strong,
.dock-user-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dock-user-copy strong {
  font-size: 14px;
}

.dock-user-copy small {
  color: var(--text-tertiary);
  font-size: 11px;
}

.dock-menu-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.dock-menu-item:hover {
  background: var(--surface-hover);
}

.dock-menu-item.danger {
  color: var(--danger);
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

.dock-icon-tile--custom {
  border: 0;
  border-radius: 0;
  background: transparent !important;
  box-shadow: none;
}

.dock-icon-tile--custom::after {
  display: none;
}

.dock-icon.dock-glyph--custom {
  width: 100%;
  height: 100%;
  filter: none;
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

.dock-item:hover .dock-icon-tile--custom,
.dock-item.active .dock-icon-tile--custom {
  background: transparent !important;
  box-shadow: none;
  filter: none;
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

.dock-icon.dock-glyph--skeuomorphic {
  width: calc(var(--dock-size) * 0.66);
  height: calc(var(--dock-size) * 0.66);
  filter: none;
}

.dock-icon.dock-glyph--macos26 {
  width: calc(var(--dock-size) * 0.66);
  height: calc(var(--dock-size) * 0.66);
  filter: none;
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

.dock-user-entry.open .dock-tooltip {
  opacity: 0;
  visibility: hidden;
}

.dock-trash-entry.open .dock-tooltip {
  opacity: 0;
  visibility: hidden;
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

.dock-menu-enter-active,
.dock-menu-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
  transform-origin: bottom right;
}

.dock-menu-enter-from,
.dock-menu-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.97);
}

.dock-trash-window-enter-active,
.dock-trash-window-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
  transform-origin: center;
}

.dock-trash-window-enter-from,
.dock-trash-window-leave-to {
  opacity: 0;
  transform: translate(-50%, calc(-50% + 12px)) scale(0.97);
}

@media (max-width: 768px) {
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

  .dock-user-entry {
    width: min(var(--dock-size), 52px);
    height: min(var(--dock-size), 52px);
    flex-basis: min(var(--dock-size), 52px);
  }

  .dock-trash-entry {
    width: min(var(--dock-size), 52px);
    height: min(var(--dock-size), 52px);
    flex-basis: min(var(--dock-size), 52px);
  }

  .dock-user-avatar {
    width: calc(min(var(--dock-size), 52px) - 6px);
    height: calc(min(var(--dock-size), 52px) - 6px);
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
    width: min(var(--dock-size), 42px);
    height: min(var(--dock-size), 42px);
  }

  .dock-icon-tile {
    width: calc(min(var(--dock-size), 42px) - 4px);
    height: calc(min(var(--dock-size), 42px) - 4px);
  }

  .dock-user-entry {
    width: min(var(--dock-size), 42px);
    height: min(var(--dock-size), 42px);
    margin-left: 8px;
    flex-basis: min(var(--dock-size), 42px);
  }

  .dock-trash-entry {
    width: min(var(--dock-size), 42px);
    height: min(var(--dock-size), 42px);
    flex-basis: min(var(--dock-size), 42px);
  }

  .dock-trash-window {
    top: 50%;
    right: auto;
    bottom: auto;
    left: 50%;
    width: calc(100vw - 28px);
    max-height: calc(100vh - 92px);
    transform: translate(-50%, -50%);
  }

  .dock-trash-window::after {
    display: none;
  }

  .dock-user-entry::before {
    right: calc(100% + 6px);
  }

  .dock-user-avatar {
    width: calc(min(var(--dock-size), 42px) - 6px);
    height: calc(min(var(--dock-size), 42px) - 6px);
  }

  .dock-user-menu {
    right: -6px;
    width: min(230px, calc(100vw - 28px));
  }

}

@media (prefers-reduced-motion: reduce) {
  .dock-item,
  .dock-icon,
  .dock-tooltip,
  .dock-menu-item {
    transition: none;
  }
}
</style>
