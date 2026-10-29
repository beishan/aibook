<template>
  <span
    class="book-card-badge badge-source"
    :style="badgeStyle"
    :title="type.label"
    :aria-label="`书籍类型：${type.label}`"
  >{{ settings.abbreviations[type.key] || type.defaultValue }}</span>
  <span
    class="book-card-badge badge-author"
    :style="badgeStyle"
    :title="author || '未知作者'"
  >{{ author || '未知作者' }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { badgeType, bookCardBadges, type BadgeSettings } from '@/utils/bookCardBadges'
const props = defineProps<{ format?: string; author?: string; preview?: BadgeSettings }>()
const settings = computed(() => props.preview || bookCardBadges)
const type = computed(() => badgeType(props.format))
const badgeStyle = computed(() => ({
  backgroundColor: `rgb(15 23 42 / ${1 - settings.value.transparency / 100})`,
}))
</script>

<style scoped>
.book-card-badge {
  position: absolute;
  z-index: 2;
  left: 8px;
  display: block;
  padding: 3px 7px;
  border-radius: 6px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: calc(100% - 16px);
  text-shadow: 0 1px 2px rgb(0 0 0 / 50%);
}
.badge-source {
  top: 8px;
  max-width: calc(100% - 52px);
}
.badge-author {
  bottom: 8px;
  max-width: calc(100% - 52px);
}
</style>
