<template>
  <section class="card glass badge-settings">
    <h2>书架卡片标签</h2>
    <p>来源类型按书籍当前格式显示在封面左上角，作者显示在左下角；列表视图保留原有文字。设置保存到当前账户。</p>
    <div class="badge-settings-body">
      <div class="badge-fields">
        <label v-for="type in badgeTypes" :key="type.key">
          <span>{{ type.label }}</span>
          <input v-model="draft.abbreviations[type.key]" class="input" :aria-label="`${type.label}缩写`" />
          <small>默认：{{ type.defaultValue }}，1–4 个字符</small>
        </label>
      </div>
      <div class="badge-preview-column">
        <label>
          预览类型
          <select v-model="previewType" class="input" aria-label="预览类型">
            <option v-for="type in badgeTypes" :key="type.key" :value="type.key">{{ type.label }}</option>
          </select>
        </label>
        <div class="badge-preview" aria-label="卡片标签预览">
          <span>书籍封面</span>
          <BookCardBadges :format="previewType" author="示例作者" :preview="draft" />
        </div>
        <label class="transparency-field">
          <span>标签背景透明度：{{ draft.transparency }}%</span>
          <input v-model.number="draft.transparency" type="range" min="0" max="100" step="1" />
          <small>0% 为不透明，100% 为全透明；文字保持清晰。</small>
        </label>
      </div>
    </div>
    <p v-if="validationError" role="alert">{{ validationError }}</p>
    <div class="badge-settings-actions">
      <button class="btn" :disabled="saving" @click="Object.assign(draft, defaultBadgeSettings())">恢复默认</button>
      <button class="btn btn-primary" :disabled="saving || !!validationError" @click="save">
        {{ saving ? '保存中…' : '保存标签设置' }}
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import BookCardBadges from '@/components/BookCardBadges.vue'
import {
  badgeTypes, bookCardBadges, defaultBadgeSettings, saveBookCardBadges,
} from '@/utils/bookCardBadges'
import { message } from '@/utils/message'
const draft = reactive(defaultBadgeSettings())
watch(bookCardBadges, value => {
  draft.abbreviations = { ...value.abbreviations }
  draft.transparency = value.transparency
}, { immediate: true })
const saving = ref(false)
const previewType = ref('structured')
const validationError = computed(() => badgeTypes.some(type => {
  const value = draft.abbreviations[type.key]?.trim() || ''
  return !value || [...value].length > 4 || /[\u0000-\u001f\u007f]/.test(value)
}) ? '每个类型的缩写须填写 1–4 个字符。' : '')
async function save() {
  if (validationError.value || saving.value) return
  saving.value = true
  try {
    await saveBookCardBadges({
      abbreviations: Object.fromEntries(Object.entries(draft.abbreviations).map(([key, value]) => [key, value.trim()])),
      transparency: draft.transparency,
    })
    message.success('卡片标签设置已保存到账户')
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存卡片标签设置失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.badge-settings {
  margin-top: 24px;
  padding: 24px;
}
.badge-settings p,
.badge-settings small {
  color: var(--text-secondary);
}
.badge-settings-body {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 20px;
}
.badge-fields {
  flex: 1 1 320px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
}
.badge-fields label,
.transparency-field {
  display: grid;
  gap: 8px;
}
.badge-preview-column {
  flex: 0 1 240px;
  display: grid;
  gap: 16px;
}
.badge-preview {
  position: relative;
  width: 160px;
  height: 220px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #bccbbc, #697d8a);
  color: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.badge-settings-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}
</style>
