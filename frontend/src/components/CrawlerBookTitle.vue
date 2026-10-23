<template>
  <span :title="preferences.hydrated && !preferences.hideBookNames ? title : displayedTitle" tabindex="0">{{ displayedTitle }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePreferencesStore } from '@/stores/preferences'
import { maskCrawlerBookTitle } from '@/utils/crawlerBookTitle'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'

const props = defineProps<{ title: string }>()
const preferences = usePreferencesStore()

const { displayBookName } = useBookNameDisplay()

const displayedTitle = computed(() => preferences.hideBookNames
  ? displayBookName(props.title)
  : displayBookName(maskCrawlerBookTitle(props.title, preferences.crawlerHideBookNames)))
</script>
