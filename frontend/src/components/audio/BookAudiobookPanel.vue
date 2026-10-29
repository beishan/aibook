<template>
  <section v-if="editions.length" class="book-audio-panel glass">
    <h2>有声版本</h2>
    <p>阅读和收听分别记录进度</p>
    <div class="book-audio-editions">
      <router-link
        v-for="edition in editions"
        :key="edition.id"
        class="btn"
        :to="`/audiobooks/${edition.id}`"
        >♫ {{ edition.name }} · {{ edition.narrator || '未填写演播者' }}</router-link
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import api from '@/utils/api'
const props = defineProps<{ bookId: number }>()
const editions = ref<Array<{ id: number; name: string; narrator: string }>>([])
watch(
  () => props.bookId,
  async (id) => {
    editions.value = []
    try {
      const { data } = await api.get(`/api/audiobooks/by-book/${id}`, {
        headers: { 'X-Suppress-Error-Toast': 'true' },
      })
      if (id === props.bookId) editions.value = data
    } catch {
      /* Existing ebook details remain usable if audio is unavailable. */
    }
  },
  { immediate: true }
)
</script>
<style scoped>
.book-audio-panel {
  border-radius: 24px;
  padding: 24px;
  margin: 20px 0;
}
p {
  color: var(--text-secondary);
  margin: 10px 0;
}
.book-audio-editions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
