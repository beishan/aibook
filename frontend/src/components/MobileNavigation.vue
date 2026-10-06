<template>
  <nav v-if="!immersive" class="phone-navigation" aria-label="手机主导航">
    <router-link
      v-for="item in primaryItems"
      :key="item.path"
      :to="item.path"
      :class="{ active: isActive(item.path) }"
      :aria-current="isActive(item.path) ? 'page' : undefined"
    >
      <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
      <span>{{ item.title }}</span>
    </router-link>
    <button
      type="button"
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
        <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
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
import {
  Collection, Connection, DataAnalysis, EditPen, Grid, House,
  Reading, Setting, Switch, Tools,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { confirm } from '@/utils/message'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const open = ref(false)
const immersive = computed(() => ['Reader', 'CrawlerTrialReader', 'RewriteEditor'].includes(String(route.name)))
const primaryItems = [
  { path: '/', title: '首页', icon: House },
  { path: '/books', title: '书库', icon: Collection },
  { path: '/shelf', title: '书架', icon: Reading },
]
const allItems = [
  ...primaryItems,
  { path: '/rewrite', title: '书籍重写', icon: EditPen },
  { path: '/text-repair', title: '内容修复', icon: Tools },
  { path: '/format-conversion', title: '格式转换', icon: Switch },
  { path: '/crawler', title: '书籍采集', icon: Connection },
  { path: '/statistics', title: '阅读统计', icon: DataAnalysis },
  { path: '/settings', title: '系统设置', icon: Setting },
]
const isActive = (path: string) => path === '/' ? route.path === '/' : route.path.startsWith(path)
const moreActive = computed(() => !primaryItems.some(item => isActive(item.path)))
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
    inset: auto 0 0;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 4px;
    padding: 6px max(10px, env(safe-area-inset-right))
      calc(6px + env(safe-area-inset-bottom)) max(10px, env(safe-area-inset-left));
    border-top: 1px solid var(--border-color);
    background: color-mix(in srgb, var(--surface-card) 96%, transparent);
    backdrop-filter: blur(18px);
  }

  .phone-navigation > a,
  .phone-navigation > button {
    display: grid;
    min-width: 0;
    min-height: 50px;
    place-content: center;
    justify-items: center;
    gap: 4px;
    border: 0;
    border-radius: 12px;
    background: transparent;
    color: var(--text-secondary);
    font: inherit;
    font-size: 11px;
    text-decoration: none;
    cursor: pointer;
  }

  .phone-navigation .el-icon {
    font-size: 22px;
  }

  .phone-navigation > .active {
    background: var(--primary-alpha-10);
    color: var(--primary);
    font-weight: 700;
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

.phone-navigation-grid .el-icon {
  font-size: 24px;
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
