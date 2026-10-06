<template>
  <component
    :is="currentLayoutComponent"
    class="app-layout"
    :class="{ 'app-layout--immersive': immersive }"
  />
  <MobileNavigation />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import MobileNavigation from '@/components/MobileNavigation.vue'
import { useRoute } from 'vue-router'

const DockLayout = defineAsyncComponent(() => import('./DockLayout.vue'))
const TopbarLayout = defineAsyncComponent(() => import('./TopbarLayout.vue'))
const SidebarLayout = defineAsyncComponent(() => import('./SidebarLayout.vue'))

const themeStore = useThemeStore()
const userStore = useUserStore()
const route = useRoute()
const immersive = computed(() => ['Reader', 'CrawlerTrialReader', 'RewriteEditor'].includes(String(route.name)))

onMounted(() => void userStore.hydrate())

const layoutMap = {
  dock: DockLayout,
  topbar: TopbarLayout,
  sidebar: SidebarLayout,
}

const currentLayoutComponent = computed(() => {
  return layoutMap[themeStore.currentLayout] || DockLayout
})
</script>
