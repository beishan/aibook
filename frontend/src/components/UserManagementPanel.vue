<template>
  <div class="card glass user-management-card">
    <div class="user-header">
      <div>
        <div class="panel-title">👥 用户管理</div>
        <p class="header-hint">管理系统用户、角色和登录状态</p>
      </div>
      <button class="btn btn-primary" @click="openCreateDialog">新增用户</button>
    </div>

    <div class="user-toolbar">
      <input
        v-model.trim="keyword"
        class="input search-input"
        placeholder="搜索用户名、昵称或邮箱"
        @keyup.enter="handleSearch"
      />
      <button class="btn" :disabled="loading" @click="handleSearch">搜索</button>
      <button v-if="keyword" class="btn btn-text" @click="clearSearch">清除</button>
    </div>

    <div v-if="loading && users.length === 0" class="user-state">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="users.length === 0" class="user-state">
      <div class="empty-icon">👤</div>
      <p>{{ keyword ? '没有找到匹配用户' : '暂无用户' }}</p>
    </div>

    <div v-else class="user-table-wrap">
      <table class="user-table">
        <thead>
          <tr>
            <th>用户</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th>创建时间</th>
            <th class="actions-heading">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>
              <div class="user-identity">
                <span class="user-avatar">{{ user.username.charAt(0).toUpperCase() }}</span>
                <div>
                  <div class="username-row">
                    <strong>{{ user.username }}</strong>
                    <span v-if="isCurrentUser(user)" class="current-badge">当前账号</span>
                  </div>
                  <small>{{ user.nickname || '未设置昵称' }}</small>
                </div>
              </div>
            </td>
            <td>{{ user.email }}</td>
            <td>
              <span class="tag" :class="user.role === 'ADMIN' ? 'tag-primary' : 'tag-info'">
                {{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}
              </span>
            </td>
            <td>
              <span class="tag" :class="user.enabled ? 'tag-success' : 'tag-disabled'">
                {{ user.enabled ? '已启用' : '已禁用' }}
              </span>
            </td>
            <td class="time-cell">{{ formatChinaDateTime(user.createdAt) }}</td>
            <td>
              <div class="row-actions">
                <button class="btn btn-text" @click="openEditDialog(user)">修改</button>
                <button class="btn btn-text" @click="openPasswordDialog(user)">重置密码</button>
                <button
                  class="btn btn-text btn-danger"
                  :disabled="isCurrentUser(user)"
                  @click="handleDelete(user)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > 0" class="user-pagination">
      <span>共 {{ total }} 个用户</span>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="sizes, prev, pager, next"
        @current-change="page => loadUsers(page - 1)"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="showUserDialog"
      :title="editingUser ? '修改用户' : '新增用户'"
      width="min(520px, 92vw)"
      class="user-editor-dialog"
      top="5vh"
      append-to-body
      destroy-on-close
    >
      <div class="dialog-form">
        <label class="form-label">用户名</label>
        <input
          v-model.trim="userForm.username"
          class="input"
          maxlength="20"
          :disabled="editingCurrentUser"
          placeholder="3-20 个字符"
        />

        <label class="form-label">昵称</label>
        <input v-model.trim="userForm.nickname" class="input" maxlength="50" placeholder="可选" />

        <label class="form-label">邮箱</label>
        <input v-model.trim="userForm.email" class="input" type="email" placeholder="name@example.com" />

        <template v-if="!editingUser">
          <label class="form-label">初始密码</label>
          <input
            v-model="userForm.password"
            class="input"
            type="password"
            maxlength="40"
            autocomplete="new-password"
            placeholder="6-40 个字符"
          />
        </template>

        <label class="form-label">角色</label>
        <el-select
          v-model="userForm.role"
          class="role-select"
          :disabled="editingCurrentUser"
        >
          <el-option label="普通用户" value="USER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>

        <label class="enabled-row">
          <span>
            <strong>启用账号</strong>
            <small>禁用后该用户将无法登录系统</small>
          </span>
          <input v-model="userForm.enabled" type="checkbox" :disabled="editingCurrentUser" />
        </label>
      </div>
      <template #footer>
        <button class="btn" @click="showUserDialog = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveUser">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showPasswordDialog"
      title="重置密码"
      width="min(440px, 92vw)"
      append-to-body
      destroy-on-close
    >
      <p class="password-hint">为用户“{{ passwordTarget?.username }}”设置新的登录密码。</p>
      <div class="dialog-form password-form">
        <label class="form-label">新密码</label>
        <input
          v-model="passwordForm.password"
          class="input"
          type="password"
          maxlength="40"
          autocomplete="new-password"
          placeholder="6-40 个字符"
        />
        <label class="form-label">确认新密码</label>
        <input
          v-model="passwordForm.confirmPassword"
          class="input"
          type="password"
          maxlength="40"
          autocomplete="new-password"
          @keyup.enter="resetPassword"
        />
      </div>
      <template #footer>
        <button class="btn" @click="showPasswordDialog = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="resetPassword">
          {{ saving ? '提交中...' : '确认重置' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/utils/api'
import { formatChinaDateTime } from '@/utils/dateTime'
import { confirm, message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

interface ManagedUser {
  id: number
  username: string
  email: string
  nickname?: string
  role: 'USER' | 'ADMIN'
  enabled: boolean
  createdAt: string
  updatedAt?: string
}

const userStore = useUserStore()
const users = ref<ManagedUser[]>([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showUserDialog = ref(false)
const showPasswordDialog = ref(false)
const editingUser = ref<ManagedUser | null>(null)
const passwordTarget = ref<ManagedUser | null>(null)

const emptyUserForm = () => ({
  username: '',
  nickname: '',
  email: '',
  password: '',
  role: 'USER' as 'USER' | 'ADMIN',
  enabled: true,
})
const userForm = reactive(emptyUserForm())
const passwordForm = reactive({ password: '', confirmPassword: '' })
const editingCurrentUser = computed(() =>
  editingUser.value?.id === userStore.userInfo?.id
)

const isCurrentUser = (user: ManagedUser) => user.id === userStore.userInfo?.id

const loadUsers = async (page = currentPage.value - 1) => {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/users', {
      params: { keyword: keyword.value, page, size: pageSize.value },
    })
    users.value = data.content || []
    total.value = data.totalElements || 0
    currentPage.value = (data.number || 0) + 1
  } catch (error: any) {
    message.error(error.response?.data?.message || '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  void loadUsers(0)
}

const clearSearch = () => {
  keyword.value = ''
  handleSearch()
}

const handleSizeChange = () => {
  currentPage.value = 1
  void loadUsers(0)
}

const resetUserForm = () => Object.assign(userForm, emptyUserForm())

const openCreateDialog = () => {
  editingUser.value = null
  resetUserForm()
  showUserDialog.value = true
}

const openEditDialog = (user: ManagedUser) => {
  editingUser.value = user
  Object.assign(userForm, {
    username: user.username,
    nickname: user.nickname || '',
    email: user.email,
    password: '',
    role: user.role,
    enabled: user.enabled,
  })
  showUserDialog.value = true
}

const validateUserForm = () => {
  if (userForm.username.length < 3 || userForm.username.length > 20) {
    message.warning('用户名长度必须在 3-20 个字符之间')
    return false
  }
  if (!/^\S+@\S+\.\S+$/.test(userForm.email)) {
    message.warning('请输入正确的邮箱地址')
    return false
  }
  if (!editingUser.value && (userForm.password.length < 6 || userForm.password.length > 40)) {
    message.warning('密码长度必须在 6-40 个字符之间')
    return false
  }
  return true
}

const saveUser = async () => {
  if (!validateUserForm()) return
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      username: userForm.username,
      nickname: userForm.nickname || null,
      email: userForm.email,
      role: userForm.role,
      enabled: userForm.enabled,
    }
    if (!editingUser.value) payload.password = userForm.password

    if (editingUser.value) {
      await api.put(`/api/admin/users/${editingUser.value.id}`, payload)
      if (editingCurrentUser.value) await userStore.hydrate(true)
      message.success('用户信息已更新')
    } else {
      await api.post('/api/admin/users', payload)
      message.success('用户已创建')
    }
    showUserDialog.value = false
    await loadUsers(currentPage.value - 1)
  } catch (error: any) {
    message.error(error.response?.data?.message || '用户保存失败')
  } finally {
    saving.value = false
  }
}

const openPasswordDialog = (user: ManagedUser) => {
  passwordTarget.value = user
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
  showPasswordDialog.value = true
}

const resetPassword = async () => {
  if (!passwordTarget.value) return
  if (passwordForm.password.length < 6 || passwordForm.password.length > 40) {
    message.warning('密码长度必须在 6-40 个字符之间')
    return
  }
  if (passwordForm.password !== passwordForm.confirmPassword) {
    message.warning('两次输入的密码不一致')
    return
  }
  saving.value = true
  try {
    await api.put(`/api/admin/users/${passwordTarget.value.id}/password`, {
      password: passwordForm.password,
    })
    showPasswordDialog.value = false
    message.success('密码已重置')
  } catch (error: any) {
    message.error(error.response?.data?.message || '密码重置失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (user: ManagedUser) => {
  const accepted = await confirm(
    `确定删除用户“${user.username}”吗？该用户的书库、书单、阅读记录等数据库数据将被清理，原始书籍文件会保留。`
  )
  if (!accepted) return
  try {
    await api.delete(`/api/admin/users/${user.id}`)
    message.success('用户已删除')
    const lastPage = Math.max(1, Math.ceil((total.value - 1) / pageSize.value))
    await loadUsers(Math.min(currentPage.value, lastPage) - 1)
  } catch (error: any) {
    message.error(error.response?.data?.message || '用户删除失败')
  }
}

onMounted(() => void loadUsers())
</script>

<style scoped>
.user-management-card {
  display: flex;
  min-height: 620px;
  flex-direction: column;
  overflow: hidden;
}

.user-header,
.user-toolbar,
.user-pagination,
.user-identity,
.username-row,
.row-actions,
.enabled-row {
  display: flex;
  align-items: center;
}

.user-header {
  justify-content: space-between;
  gap: var(--spacing-md);
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

.user-toolbar {
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.search-input {
  width: min(360px, 100%);
}

.user-state {
  flex: 1;
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.user-table-wrap {
  flex: 1;
  overflow-x: auto;
}

.user-table {
  width: 100%;
  min-width: 940px;
  border-collapse: collapse;
}

.user-table th,
.user-table td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color-light);
  text-align: left;
  vertical-align: middle;
}

.user-table th {
  color: var(--text-secondary);
  background: var(--primary-alpha-10);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.user-identity {
  gap: 10px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-weight: 700;
}

.username-row {
  gap: 6px;
}

.user-identity small,
.time-cell {
  color: var(--text-secondary);
  white-space: nowrap;
}

.current-badge {
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-size: 11px;
}

.tag-primary {
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.tag-disabled {
  background: rgba(148, 163, 184, 0.16);
  color: var(--text-secondary);
}

.actions-heading {
  text-align: right !important;
}

.row-actions {
  justify-content: flex-end;
  gap: 4px;
  white-space: nowrap;
}

.row-actions .btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.user-pagination {
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  color: var(--text-secondary);
}

.dialog-form {
  display: grid;
  gap: 10px;
}

:global(.user-editor-dialog) {
  display: flex;
  max-height: 90vh;
  flex-direction: column;
  overflow: hidden;
}

:global(.user-editor-dialog .el-dialog__body) {
  overflow-y: auto;
}

.dialog-form .form-label:not(:first-child) {
  margin-top: 6px;
}

.role-select {
  width: 100%;
}

.enabled-row {
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-top: 10px;
  padding: 12px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
}

.enabled-row span {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.enabled-row small,
.password-hint {
  color: var(--text-secondary);
}

.password-hint {
  margin: 0 0 var(--spacing-md);
}

@media (max-width: 640px) {
  .user-header,
  .user-pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .user-toolbar {
    flex-wrap: wrap;
  }

  .search-input {
    flex: 1 0 100%;
  }
}
</style>
