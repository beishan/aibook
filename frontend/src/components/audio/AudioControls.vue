<template>
  <div class="audio-controls">
    <label class="audio-seek">
      <span class="sr-only">音轨播放位置</span>
      <input
        type="range"
        min="0"
        :max="player.track?.durationMs || 1"
        :value="player.positionMs"
        @change="seek"
      />
    </label>
    <div class="audio-times">
      <span>{{ audioTime(player.positionMs) }}</span
      ><span>{{ audioTime(player.track?.durationMs || 0) }}</span>
    </div>
    <div class="audio-transport">
      <button
        class="btn"
        :aria-label="`后退${player.settings.backSeconds}秒`"
        @click="player.seek(player.positionMs - player.settings.backSeconds * 1000)"
      >
        ↶ {{ player.settings.backSeconds }}
      </button>
      <button
        class="audio-play btn btn-primary"
        :aria-label="player.playing ? '暂停' : '播放'"
        @click="player.toggle()"
      >
        {{ player.playing ? 'Ⅱ' : '▶' }}
      </button>
      <button
        class="btn"
        :aria-label="`前进${player.settings.forwardSeconds}秒`"
        @click="player.seek(player.positionMs + player.settings.forwardSeconds * 1000)"
      >
        {{ player.settings.forwardSeconds }} ↷
      </button>
    </div>
    <div v-if="!compact" class="audio-tools">
      <label
        >倍速
        <select aria-label="播放倍速" :value="player.settings.speed" @change="speed">
          <option
            v-for="value in [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3]"
            :key="value"
            :value="value"
          >
            {{ value }}×
          </option>
        </select>
      </label>
      <details>
        <summary class="btn">
          ☾
          {{
            player.sleepAtChapterEnd
              ? '本章结束停止'
              : player.sleepDeadline
                ? `${Math.ceil(player.sleepRemaining / 60)} 分钟后停止`
                : '睡眠定时'
          }}
        </summary>
        <div class="audio-sleep glass">
          <button
            v-for="minutes in [15, 30, 60]"
            :key="minutes"
            class="btn"
            @click="player.sleep(minutes)"
          >
            {{ minutes }} 分钟
          </button>
          <button class="btn" :disabled="!player.chapter" @click="player.sleep('chapter')">
            本章结束
          </button>
          <button class="btn" @click="player.sleep(null)">关闭定时</button>
        </div>
      </details>
      <button class="btn" @click="bookmark">添加书签</button>
    </div>
    <p v-if="player.buffering" role="status">正在缓冲…</p>
    <p v-if="player.error" class="audio-error" role="alert">
      {{ player.error }}
    </p>
  </div>
</template>
<script setup lang="ts">
import { useAudioPlayerStore } from '@/stores/audioPlayer'
import { audioTime } from '@/utils/audiobook'
import { ElMessageBox } from 'element-plus'
defineProps<{ compact?: boolean }>()
const player = useAudioPlayerStore()
function seek(event: Event) {
  player.seek(Number((event.target as HTMLInputElement).value))
}
async function speed(event: Event) {
  await player.saveSettings({
    ...player.settings,
    speed: Number((event.target as HTMLSelectElement).value),
  })
}
async function bookmark() {
  try {
    const { value } = await ElMessageBox.prompt('为当前音频位置添加书签', '添加书签', {
      inputValue: `${player.chapter?.title || player.track?.title || '书签'} ${audioTime(player.positionMs)}`,
      inputValidator: (value) => (!!value?.trim() && value.length <= 200) || '请输入 1—200 字标题',
    })
    await player.addBookmark(value.trim())
  } catch {
    /* Cancel or request error already displayed. */
  }
}
</script>
<style scoped>
.audio-seek input {
  width: 100%;
  accent-color: var(--primary);
  cursor: pointer;
}
.audio-times {
  display: flex;
  justify-content: space-between;
  color: var(--text-secondary);
  margin-top: 6px;
  font-variant-numeric: tabular-nums;
}
.audio-transport,
.audio-tools {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  flex-wrap: wrap;
}
.audio-play {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  font-size: 24px;
}
.audio-tools label {
  display: flex;
  align-items: center;
  gap: 8px;
}
select {
  background: var(--bg-surface);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px;
}
details {
  position: relative;
}
summary {
  list-style: none;
  cursor: pointer;
}
.audio-sleep {
  position: absolute;
  bottom: 110%;
  left: 0;
  z-index: 30;
  padding: 10px;
  display: grid;
  gap: 6px;
  width: 180px;
  border-radius: 16px;
  background: var(--surface-elevated);
}
.audio-error {
  color: var(--danger);
  margin-top: 12px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  clip: rect(0, 0, 0, 0);
  overflow: hidden;
}
</style>
