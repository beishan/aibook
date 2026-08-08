<template>
  <div class="card glass profile-card">
    <div class="profile-header">
      <div>
        <div class="panel-title">👤 个人设置</div>
        <p class="header-hint">完善您的个人资料和阅读偏好</p>
      </div>
    </div>

    <div v-if="loading" class="profile-state">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else class="profile-content">
      <section class="avatar-section">
        <div class="avatar-preview">
          <img v-if="userStore.avatarObjectUrl" :src="userStore.avatarObjectUrl" alt="当前头像" />
          <span v-else>{{ userInitial }}</span>
        </div>
        <div class="avatar-actions">
          <strong>个人头像</strong>
          <p>支持 JPG、PNG、WebP、GIF，最大 5MB；可拖动和缩放裁剪，裁剪后以 JPG 保存。</p>
          <div class="button-row">
            <input
              ref="avatarInput"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              hidden
              @change="handleAvatarSelected"
            />
            <button class="btn btn-primary" :disabled="uploading" @click="avatarInput?.click()">
              {{ uploading ? '上传中...' : '选择头像' }}
            </button>
            <button
              v-if="userStore.userInfo?.hasAvatar"
              class="btn btn-text btn-danger"
              :disabled="uploading"
              @click="removeAvatar"
            >
              移除头像
            </button>
          </div>
        </div>
      </section>

      <section class="profile-form">
        <div class="form-grid">
          <div class="form-group readonly-field">
            <label class="form-label">用户名</label>
            <input :value="userStore.userInfo?.username" class="input" disabled />
            <small>用户名需要由管理员在用户管理中修改。</small>
          </div>

          <div class="form-group readonly-field">
            <label class="form-label">邮箱</label>
            <input :value="userStore.userInfo?.email" class="input" disabled />
            <small>邮箱需要由管理员在用户管理中修改。</small>
          </div>

          <div class="form-group">
            <label class="form-label">昵称</label>
            <input
              v-model="profileForm.nickname"
              class="input"
              maxlength="50"
              placeholder="您希望显示的名字"
            />
            <small class="field-counter">{{ profileForm.nickname.length }}/50</small>
          </div>

          <div class="form-group">
            <label class="form-label">出生日期</label>
            <input
              v-model="profileForm.birthDate"
              class="input"
              type="date"
              :max="maxBirthDate"
            />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">心情</label>
          <input
            v-model="profileForm.mood"
            class="input"
            maxlength="100"
            placeholder="写下此刻的心情或个性签名"
          />
          <small class="field-counter">{{ profileForm.mood.length }}/100</small>
        </div>

        <div class="form-group">
          <label class="form-label">书籍喜好</label>
          <textarea
            v-model="profileForm.bookPreferences"
            class="input textarea"
            maxlength="1000"
            rows="4"
            placeholder="例如：历史、科幻、推理；喜欢的作者或近期想读的主题"
          ></textarea>
          <small class="field-counter">{{ profileForm.bookPreferences.length }}/1000</small>
        </div>

        <div class="form-group">
          <label class="form-label">备注</label>
          <textarea
            v-model="profileForm.notes"
            class="input textarea"
            maxlength="1000"
            rows="4"
            placeholder="记录与个人阅读相关的其他信息"
          ></textarea>
          <small class="field-counter">{{ profileForm.notes.length }}/1000</small>
        </div>

        <div class="form-actions">
          <button class="btn btn-primary" :disabled="saving" @click="saveProfile">
            {{ saving ? '保存中...' : '保存个人设置' }}
          </button>
        </div>
      </section>
    </div>

    <AvatarCropperDialog
      :visible="cropDialogVisible"
      :image-url="cropImageUrl"
      :original-name="cropOriginalName"
      @close="closeCropper"
      @confirm="uploadCroppedAvatar"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import api from '@/utils/api'
import AvatarCropperDialog from '@/components/AvatarCropperDialog.vue'
import { confirm, message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const cropDialogVisible = ref(false)
const cropImageUrl = ref('')
const cropOriginalName = ref('')
const profileForm = reactive({
  nickname: '',
  mood: '',
  birthDate: '',
  bookPreferences: '',
  notes: '',
})

const userInitial = computed(() =>
  userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U'
)
const yesterday = new Date()
yesterday.setDate(yesterday.getDate() - 1)
const maxBirthDate = [
  yesterday.getFullYear(),
  String(yesterday.getMonth() + 1).padStart(2, '0'),
  String(yesterday.getDate()).padStart(2, '0'),
].join('-')

const applyProfile = () => {
  const profile = userStore.userInfo
  profileForm.nickname = profile?.nickname || ''
  profileForm.mood = profile?.mood || ''
  profileForm.birthDate = profile?.birthDate || ''
  profileForm.bookPreferences = profile?.bookPreferences || ''
  profileForm.notes = profile?.notes || ''
}

const loadProfile = async () => {
  loading.value = true
  try {
    await userStore.hydrate()
    applyProfile()
  } catch (error: any) {
    message.error(error.response?.data?.message || '个人资料加载失败')
  } finally {
    loading.value = false
  }
}

const saveProfile = async () => {
  saving.value = true
  try {
    await api.put('/api/user/profile', {
      nickname: profileForm.nickname || null,
      mood: profileForm.mood || null,
      birthDate: profileForm.birthDate || null,
      bookPreferences: profileForm.bookPreferences || null,
      notes: profileForm.notes || null,
    })
    await userStore.hydrate(true)
    applyProfile()
    message.success('个人设置已保存')
  } catch (error: any) {
    message.error(error.response?.data?.message || '个人设置保存失败')
  } finally {
    saving.value = false
  }
}

const closeCropper = () => {
  cropDialogVisible.value = false
  if (cropImageUrl.value.startsWith('blob:')) URL.revokeObjectURL(cropImageUrl.value)
  cropImageUrl.value = ''
  cropOriginalName.value = ''
}

const handleAvatarSelected = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    message.warning('头像图片不能超过 5MB')
    input.value = ''
    return
  }
  const supportedType = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)
  const supportedExtension = /\.(jpe?g|png|webp|gif)$/i.test(file.name)
  if (!supportedType && !supportedExtension) {
    message.warning('仅支持 JPG、PNG、WebP 或 GIF 图片')
    input.value = ''
    return
  }

  closeCropper()
  cropImageUrl.value = URL.createObjectURL(file)
  cropOriginalName.value = file.name
  cropDialogVisible.value = true
  input.value = ''
}

const uploadCroppedAvatar = async (file: File) => {
  closeCropper()
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    await api.post('/api/user/profile/avatar', formData)
    await userStore.hydrate(true)
    message.success('头像已更新')
  } catch (error: any) {
    message.error(error.response?.data?.message || '头像上传失败')
  } finally {
    uploading.value = false
  }
}

const removeAvatar = async () => {
  const accepted = await confirm('确定移除当前头像吗？')
  if (!accepted) return
  uploading.value = true
  try {
    await api.delete('/api/user/profile/avatar')
    await userStore.hydrate(true)
    message.success('头像已移除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '头像移除失败')
  } finally {
    uploading.value = false
  }
}

onMounted(() => void loadProfile())
onBeforeUnmount(closeCropper)
</script>

<style scoped>
.profile-card {
  overflow: hidden;
}

.profile-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.panel-title {
  color: var(--text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.header-hint {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.profile-state {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.profile-content {
  padding: var(--spacing-lg);
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  padding-bottom: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.avatar-preview {
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  overflow: hidden;
  border: 3px solid var(--surface-card);
  border-radius: 50%;
  background: var(--primary-gradient);
  box-shadow: var(--shadow-md);
  color: white;
  font-size: 34px;
  font-weight: 700;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions p {
  margin: 5px 0 12px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.button-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.profile-form {
  display: grid;
  gap: var(--spacing-lg);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-lg);
}

.form-group {
  position: relative;
  margin: 0;
}

.form-label {
  display: block;
  margin-bottom: var(--spacing-sm);
}

.textarea {
  min-height: 104px;
  resize: vertical;
  line-height: 1.6;
}

.field-counter {
  display: block;
  margin-top: 5px;
  color: var(--text-tertiary);
  text-align: right;
}

.readonly-field small {
  display: block;
  margin-top: 5px;
  color: var(--text-tertiary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .profile-content {
    padding: var(--spacing-md);
  }

  .avatar-section {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .button-row {
    flex-wrap: wrap;
  }
}
</style>
