<template>
  <section class="card glass placeholder-settings">
    <h2>无图封面文字</h2>
    <p>所有页面的书籍无图占位共用此设置，封面被隐藏时也生效。书名仍遵循当前页面的隐藏规则。</p>
    <AudioSegmented
      v-model="draft.mode"
      label="无图封面文字显示方式"
      :options="[
        { value: 'PREFIX', label: '书名前 N 字' },
        { value: 'FULL', label: '完整书名' },
        { value: 'CUSTOM', label: '固定文字' },
      ]"
    />
    <label v-if="draft.mode === 'PREFIX'" class="placeholder-field">
      <span>显示字数 N（1–20）</span>
      <input v-model.number="draft.length" type="number" min="1" max="20" step="1" class="input" />
    </label>
    <label v-if="draft.mode === 'CUSTOM'" class="placeholder-field">
      <span>固定文字（1–40 个字符）</span>
      <input v-model="draft.text" class="input" />
    </label>
    <div class="placeholder-preview" aria-label="无图文字预览">
      <BookCoverPlaceholderText :title="displayBookName('汗牛充栋的阅读时光')" :preview="draft" />
    </div>
    <p>长文字会缩小并换行，空间不足时截断展示；默认显示书名前 2 字。预览使用示例书名。</p>
    <p v-if="!valid" role="alert">字数须为 1–20 的整数，固定文字须为 1–40 个字符。</p>
    <div class="placeholder-actions">
      <button class="btn" :disabled="saving" @click="Object.assign(draft, defaultPlaceholderSettings())">恢复默认</button>
      <button class="btn btn-primary" :disabled="saving || !valid" @click="save">{{ saving ? '保存中…' : '保存无图文字设置' }}</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import AudioSegmented from '@/components/audio/AudioSegmented.vue'
import BookCoverPlaceholderText from '@/components/BookCoverPlaceholderText.vue'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { bookCoverPlaceholder, defaultPlaceholderSettings, saveBookCoverPlaceholder } from '@/utils/bookCoverPlaceholder'
import { message } from '@/utils/message'
const { displayBookName } = useBookNameDisplay()
const draft = reactive(defaultPlaceholderSettings())
const saving = ref(false)
watch(bookCoverPlaceholder, value => Object.assign(draft, value), { immediate: true })
const valid = computed(() => Number.isInteger(draft.length) && draft.length >= 1 && draft.length <= 20
  && !!draft.text.trim() && Array.from(draft.text.trim()).length <= 40
  && !/[\u0000-\u001f\u007f]/.test(draft.text))
async function save() {
  if (!valid.value || saving.value) return
  saving.value = true
  try {
    await saveBookCoverPlaceholder({ ...draft, text: draft.text.trim() })
    message.success('无图文字设置已保存到账户')
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存无图文字设置失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.placeholder-settings {
  padding: 24px;
  margin-top: 24px;
  display: grid;
  gap: 18px;
}
.placeholder-settings p {
  color: var(--text-secondary);
}
.placeholder-settings .audio-segmented {
  width: fit-content;
  max-width: 100%;
}
.placeholder-field {
  display: grid;
  gap: 8px;
  max-width: 320px;
}
.placeholder-preview {
  width: 160px;
  height: 220px;
  display: grid;
  place-items: center;
  padding: 12px;
  border-radius: 12px;
  background: var(--primary-gradient);
  color: white;
  font-size: 32px;
  overflow: hidden;
}
.placeholder-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
