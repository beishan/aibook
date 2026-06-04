<template>
  <component :is="currentLayoutComponent" />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useThemeStore } from '@/stores/theme'

const DockLayout = defineAsyncComponent(() => import('./DockLayout.vue'))
const TopbarLayout = defineAsyncComponent(() => import('./TopbarLayout.vue'))
const SidebarLayout = defineAsyncComponent(() => import('./SidebarLayout.vue'))

const themeStore = useThemeStore()

const layoutMap = {
  dock: DockLayout,
  topbar: TopbarLayout,
  sidebar: SidebarLayout,
}

const currentLayoutComponent = computed(() => {
  return layoutMap[themeStore.currentLayout] || DockLayout
})
</script>
