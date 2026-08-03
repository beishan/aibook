<template>
  <component :is="currentLayoutComponent" />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'

const DockLayout = defineAsyncComponent(() => import('./DockLayout.vue'))
const TopbarLayout = defineAsyncComponent(() => import('./TopbarLayout.vue'))
const SidebarLayout = defineAsyncComponent(() => import('./SidebarLayout.vue'))

const themeStore = useThemeStore()
const userStore = useUserStore()

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
