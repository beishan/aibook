<template>
  <span class="highlight-text">
    <template v-for="(segment, index) in segments" :key="index">
      <mark v-if="segment.hit" class="search-hit">{{ segment.text }}</mark>
      <template v-else>{{ segment.text }}</template>
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { highlightSegments } from '@/utils/searchHighlight'

const props = defineProps<{
  /** 需要渲染并可能高亮的原始文本 */
  text?: string
  /** 搜索关键词；为空时原样渲染 */
  keyword?: string
}>()

const segments = computed(() => {
  const text = props.text ?? ''
  const keyword = (props.keyword ?? '').trim()
  if (!keyword) {
    return [{ text, hit: false }]
  }
  return highlightSegments(text, keyword)
})
</script>

<style scoped>
.search-hit {
  /* 使用主题 warning 色的轻量底色强调，不改变文字颜色与字号 */
  background: color-mix(in srgb, var(--warning, #e68a00) 24%, transparent);
  color: inherit;
  font-weight: 600;
  border-radius: 2px;
  padding: 0 1px;
}
</style>
