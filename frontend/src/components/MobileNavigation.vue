<template>
  <nav
    v-if="!immersive"
    class="phone-navigation"
    :style="dockStyle"
    aria-label="手机 Dock 主导航"
  >
    <div class="phone-dock-items">
      <router-link
        v-for="item in primaryItems"
        :key="item.path"
        :to="item.path"
        :class="{ active: isActive(item.path) }"
        :aria-current="isActive(item.path) ? 'page' : undefined"
        :aria-label="item.title"
      >
        <span class="phone-dock-icon" aria-hidden="true">
          <DockIcon
            :name="item.icon"
            :variant="preferencesStore.dockIconStyle"
            :custom-src="dockIconStore.iconUrls[item.icon]"
          />
        </span>
        <span class="phone-dock-label">{{ item.title }}</span>
        <span class="phone-dock-dot" aria-hidden="true"></span>
      </router-link>
    </div>
    <button
      type="button"
      class="phone-dock-more"
      :class="{ active: moreActive }"
      aria-label="更多功能"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="open = true"
    >
      <el-icon aria-hidden="true"><Grid /></el-icon>
      <span>更多</span>
    </button>
  </nav>

  <el-drawer
    v-model="open"
    title="全部功能"
    direction="btt"
    size="auto"
    class="phone-navigation-sheet"
    append-to-body
  >
    <div class="phone-navigation-grid">
      <router-link
        v-for="item in allItems"
        :key="item.path"
        :to="item.path"
        :class="{ active: isActive(item.path) }"
        :aria-current="isActive(item.path) ? 'page' : undefined"
        @click="open = false"
      >
        <span class="phone-sheet-icon" aria-hidden="true">
          <DockIcon
            :name="item.icon"
            :variant="preferencesStore.dockIconStyle"
            :custom-src="dockIconStore.iconUrls[item.icon]"
          />
        </span>
        <span>{{ item.title }}</span>
      </router-link>
    </div>
    <div class="phone-account-actions">
      <router-link to="/settings?tab=profile" @click="open = false">个人设置</router-link>
      <router-link to="/settings?tab=trash" @click="open = false">回收站</router-link>
      <button type="button" @click="logout">退出登录</button>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Grid } from '@element-plus/icons-vue'
import DockIcon from '@/components/DockIcon.vue'
import { usePreferencesStore } from '@/stores/preferences'
import { useDockIconStore } from '@/stores/dockIcons'
import { useUserStore } from '@/stores/user'
import { confirm } from '@/utils/message'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const preferencesStore = usePreferencesStore()
const dockIconStore = useDockIconStore()
const open = ref(false)
const immersive = computed(() => ['Reader', 'CrawlerTrialReader', 'RewriteEditor'].includes(String(route.name)))
const menuRoutes: Record<string, string> = {
  home: '/',
  library: '/books',
  rewrite: '/rewrite',
  shelf: '/shelf',
  repair: '/text-repair',
  conversion: '/format-conversion',
  crawler: '/crawler',
  statistics: '/statistics',
  settings: '/settings',
}
const allItems = computed(() => [...preferencesStore.dockNavigationItems]
  .sort((a, b) => a.order - b.order)
  .map(item => ({ path: menuRoutes[item.key], icon: item.icon, title: item.title, enabled: item.enabled })))
const primaryItems = computed(() => allItems.value.filter(item => item.enabled))
const dockStyle = computed(() => ({
  '--phone-dock-icon-size': `${Math.min(44, preferencesStore.dockSize)}px`,
  '--phone-dock-opacity': String(preferencesStore.dockOpacity / 100),
  '--phone-dock-blur': `${preferencesStore.dockBlur}px`,
}))
const isActive = (path: string) => path === '/' ? route.path === '/' : route.path.startsWith(path)
const moreActive = computed(() => !primaryItems.value.some(item => isActive(item.path)))
watch(() => route.fullPath, () => { open.value = false })

const logout = async () => {
  if (!await confirm('确定要退出登录吗？')) return
  open.value = false
  userStore.logout()
  await router.push('/login')
}
</script>

<style scoped>
.phone-navigation {
  display: none;
}

@media (max-width: 768px), (max-width: 1024px) and (max-height: 500px) and (pointer: coarse) {
  .phone-navigation {
    position: fixed;
    z-index: 110;
    inset: auto max(10px, env(safe-area-inset-right))
      calc(8px + env(safe-area-inset-bottom)) max(10px, env(safe-area-inset-left));
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 7px;
    border: 1px solid var(--border-color-light);
    border-radius: 24px;
    background: color-mix(in srgb, var(--surface-card) calc(var(--phone-dock-opacity) * 100%), transparent);
    backdrop-filter: blur(var(--phone-dock-blur));
    -webkit-backdrop-filter: blur(var(--phone-dock-blur));
  }

  .phone-dock-items {
    display: flex;
    flex: 1 1 auto;
    min-width: 0;
    gap: 4px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .phone-dock-items::-webkit-scrollbar {
    display: none;
  }

  .phone-dock-items > a,
  .phone-dock-more {
    position: relative;
    display: grid;
    flex: 0 0 64px;
    width: 64px;
    min-height: 68px;
    align-content: center;
    justify-items: center;
    gap: 4px;
    padding: 4px;
    border: 0;
    border-radius: 17px;
    background: transparent;
    color: var(--text-secondary);
    font: inherit;
    font-size: 11px;
    text-decoration: none;
    cursor: pointer;
  }

  .phone-dock-icon {
    width: var(--phone-dock-icon-size);
    height: var(--phone-dock-icon-size);
  }

  .phone-dock-label {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .phone-dock-more {
    flex-basis: 48px;
    width: 48px;
    border-left: 1px solid var(--border-color-light);
  }

  .phone-dock-more .el-icon {
    height: var(--phone-dock-icon-size);
    font-size: 28px;
  }

  .phone-dock-items > .active,
  .phone-dock-more.active {
    background: var(--primary-alpha-10);
    color: var(--primary);
    font-weight: 700;
  }

  .phone-dock-dot {
    position: absolute;
    bottom: 1px;
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--primary);
    opacity: 0;
  }

  .phone-dock-items > .active .phone-dock-dot {
    opacity: 1;
  }

  .phone-dock-items > a:active,
  .phone-dock-more:active {
    background: var(--primary-alpha-10);
  }
}

.phone-navigation-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.phone-navigation-grid a {
  display: grid;
  min-height: 76px;
  place-content: center;
  justify-items: center;
  gap: 10px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  color: var(--text-primary);
  font-size: 13px;
  text-decoration: none;
}

.phone-sheet-icon {
  width: 40px;
  height: 40px;
  color: var(--primary);
}

.phone-navigation-grid .active {
  background: var(--primary-alpha-10);
  border-color: var(--primary);
}

.phone-account-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.phone-account-actions a,
.phone-account-actions button {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.phone-navigation a:focus-visible,
.phone-navigation button:focus-visible,
.phone-navigation-grid a:focus-visible,
.phone-account-actions :focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
</style>
