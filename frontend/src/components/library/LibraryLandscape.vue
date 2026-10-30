<template>
  <div class="library-landscape" :class="[`cover-${variant || 'landscape'}`, { 'custom-library-cover': !!imageUrl }]" aria-hidden="true">
    <img v-if="imageUrl" :src="imageUrl" alt="" @error="clearImage" />
    <span v-else />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import api from '@/utils/api'
const props = defineProps<{ libraryId: number; imageKey?: string; variant?: string }>()
const imageUrl = ref('')
function clearImage() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}
watch(() => [props.libraryId, props.imageKey], async (_value, _old, onCleanup) => {
  let stale = false
  clearImage()
  onCleanup(() => { stale = true; clearImage() })
  if (!props.imageKey) return
  try {
    const { data } = await api.get(`/api/libraries/${props.libraryId}/cover`, {
      responseType: 'blob', headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    if (!stale) imageUrl.value = URL.createObjectURL(data)
  } catch {
    // A missing asset restores the built-in illustration without blocking the library.
  }
}, { immediate: true })
</script>

<style scoped>
.custom-library-cover::before,
.custom-library-cover::after {
  display: none;
}
.custom-library-cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
