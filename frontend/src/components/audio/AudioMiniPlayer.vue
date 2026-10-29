<template>
  <section
    v-if="player.book && route.name !== 'AudiobookPlayer'"
    class="audio-mini glass"
    aria-label="正在收听"
  >
    <div class="audio-mini-title">
      <router-link :to="`/audiobooks/${player.book.id}/listen`"
        ><strong>{{ displayBookName(player.book.title) }}</strong></router-link
      >
      <small>{{ player.chapter?.title || player.track?.title }}</small>
      <small v-if="player.error" role="alert">{{ player.error }}</small>
    </div>
    <button
      class="btn"
      aria-label="后退"
      @click="player.seek(player.positionMs - player.settings.backSeconds * 1000)"
    >
      ↶ {{ player.settings.backSeconds }}
    </button>
    <button
      class="btn btn-primary"
      :aria-label="player.playing ? '暂停' : '播放'"
      @click="player.toggle()"
    >
      {{ player.playing ? 'Ⅱ' : '▶' }}
    </button>
    <label class="audio-mini-seek"
      >播放位置
      <input
        type="range"
        min="0"
        :max="player.track?.durationMs || 1"
        :value="player.positionMs"
        @change="player.seek(Number(($event.target as HTMLInputElement).value))"
      />
    </label>
    <small
      >{{ audioTime(player.positionMs) }} / {{ audioTime(player.track?.durationMs || 0) }}</small
    >
    <router-link class="btn" :to="`/audiobooks/${player.book.id}/listen`">展开</router-link>
    <button class="btn" aria-label="停止并关闭播放器" @click="player.close()">✕</button>
  </section>
</template>
<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAudioPlayerStore } from '@/stores/audioPlayer'
import { audioTime } from '@/utils/audiobook'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
const player = useAudioPlayerStore()
const route = useRoute()
const { displayBookName } = useBookNameDisplay()
</script>
<style scoped>
.audio-mini {
  position: fixed;
  z-index: 2100;
  left: max(16px, 4vw);
  right: max(16px, 4vw);
  bottom: calc(var(--dock-size, 64px) + 48px + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-radius: 22px;
  background: var(--surface-elevated);
}
.audio-mini-title {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 5px;
}
.audio-mini-title strong,
.audio-mini-title small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.audio-mini-title a {
  color: var(--text-primary);
  text-decoration: none;
}
.audio-mini-seek {
  flex: 1;
  font-size: 0;
}
.audio-mini-seek input {
  width: 100%;
  accent-color: var(--primary);
}
small {
  color: var(--text-secondary);
}
@media (max-width: 768px) {
  .audio-mini {
    bottom: calc(76px + env(safe-area-inset-bottom));
    padding: 12px;
    gap: 8px;
  }
  .audio-mini-seek,
  .audio-mini > small,
  .audio-mini > button:first-of-type {
    display: none;
  }
}
</style>
