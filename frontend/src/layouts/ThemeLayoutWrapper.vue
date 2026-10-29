<template>
  <component
    :is="currentLayoutComponent"
    class="app-layout"
    :class="{ 'app-layout--immersive': immersive, 'app-layout--audio': !!audioPlayer.book && route.name !== 'AudiobookPlayer' }"
  />
  <MobileNavigation />
  <AudioMiniPlayer />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import AudioMiniPlayer from '@/components/audio/AudioMiniPlayer.vue'
import MobileNavigation from '@/components/MobileNavigation.vue'
import { useAudioPlayerStore } from '@/stores/audioPlayer'
import { useRoute } from 'vue-router'

const DockLayout = defineAsyncComponent(() => import('./DockLayout.vue'))
const TopbarLayout = defineAsyncComponent(() => import('./TopbarLayout.vue'))
const SidebarLayout = defineAsyncComponent(() => import('./SidebarLayout.vue'))

const themeStore = useThemeStore()
const userStore = useUserStore()
const route = useRoute()
const audioPlayer = useAudioPlayerStore()
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

<style scoped>
.app-layout--audio :deep(.layout-main)::after {
  content: '';
  display: block;
  height: 120px;
  flex-shrink: 0;
}
</style>
