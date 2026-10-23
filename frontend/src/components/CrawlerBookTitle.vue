<template>
  <span :title="title" tabindex="0">{{ displayCrawlerBookName(title) }}</span>
</template>

<script setup lang="ts">
import { useCrawlerBookNameDisplay } from '@/composables/useCrawlerBookNameDisplay'

defineProps<{ title: string }>()
const { displayCrawlerBookName } = useCrawlerBookNameDisplay()
</script>
