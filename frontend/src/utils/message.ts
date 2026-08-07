import { h } from 'vue'
import { ElMessageBox, ElNotification } from 'element-plus'

type MessageType = 'success' | 'warning' | 'error' | 'info'

interface MessageOptions {
  type?: MessageType
  duration?: number
  title?: string
}

const notificationTitles: Record<MessageType, string> = {
  success: '操作成功',
  warning: '请注意',
  error: '操作失败',
  info: '系统提示',
}

export const showMessage = (text: string, options: MessageOptions = {}) => {
  const { type = 'info', duration = 3000, title = notificationTitles[type] } = options

  return ElNotification({
    title,
    message: text,
    type,
    duration,
    position: 'top-right',
    showClose: true,
    offset: 18,
  })
}

export const message = {
  success: (text: string) => showMessage(text, { type: 'success' }),
  warning: (text: string) => showMessage(text, { type: 'warning' }),
  error: (text: string) => showMessage(text, { type: 'error', duration: 4500 }),
  info: (text: string) => showMessage(text, { type: 'info' }),
}

export const confirm = async (text: string, title = '请确认'): Promise<boolean> => {
  try {
    await ElMessageBox.confirm(
      h('div', { style: { whiteSpace: 'pre-line', lineHeight: '1.6' } }, text),
      title,
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        closeOnClickModal: false,
        closeOnPressEscape: true,
        distinguishCancelAndClose: true,
        draggable: true,
      },
    )
    return true
  } catch {
    return false
  }
}

export default message
