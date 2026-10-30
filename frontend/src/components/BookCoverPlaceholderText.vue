<template>
  <span class="book-placeholder-text" :style="{ fontSize: scale }">{{ text }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { placeholderText, type PlaceholderSettings } from '@/utils/bookCoverPlaceholder'
// Callers pass their already-masked display name, including crawler-specific privacy rules.
const props = defineProps<{ title?: string | null; preview?: PlaceholderSettings }>()
const text = computed(() => placeholderText(props.title, props.preview))
const scale = computed(() => Array.from(text.value).length > 8 ? '0.4em'
  : Array.from(text.value).length > 4 ? '0.6em' : '1em')
</script>

<style scoped>
.book-placeholder-text {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  overflow: hidden;
  overflow-wrap: anywhere;
  white-space: normal;
  text-align: center;
  line-height: 1.35;
  max-width: 100%;
  max-height: 100%;
  min-width: 0;
  padding: 4px;
}
</style>
