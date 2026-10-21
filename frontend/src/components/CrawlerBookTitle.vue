<template>
  <span :title="title" tabindex="0">{{ maskCrawlerBookTitle(title, preferences.crawlerHideBookNames) }}</span>
</template>

<script setup lang="ts">
import { usePreferencesStore } from '@/stores/preferences'
import { maskCrawlerBookTitle } from '@/utils/crawlerBookTitle'

defineProps<{ title: string }>()
const preferences = usePreferencesStore()
</script>
