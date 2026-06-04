// 消息提示工具 - 替代 ElMessage

interface MessageOptions {
  type?: 'success' | 'warning' | 'error' | 'info'
  duration?: number
}

let messageContainer: HTMLDivElement | null = null

const getContainer = () => {
  if (!messageContainer) {
    messageContainer = document.createElement('div')
    messageContainer.className = 'message-container'
    document.body.appendChild(messageContainer)
  }
  return messageContainer
}

export const showMessage = (text: string, options: MessageOptions = {}) => {
  const { type = 'info', duration = 3000 } = options

  const container = getContainer()

  const message = document.createElement('div')
  message.className = `message message-${type}`
  message.textContent = text

  container.appendChild(message)

  setTimeout(() => {
    message.remove()
  }, duration)
}

export const message = {
  success: (text: string) => showMessage(text, { type: 'success' }),
  warning: (text: string) => showMessage(text, { type: 'warning' }),
  error: (text: string) => showMessage(text, { type: 'error' }),
  info: (text: string) => showMessage(text, { type: 'info' }),
}

// 确认对话框 - 替代 ElMessageBox.confirm
export const confirm = (text: string, title = '提示'): Promise<boolean> => {
  return new Promise((resolve) => {
    const result = window.confirm(`${title}\n\n${text}`)
    resolve(result)
  })
}

export default message
