/** Small account preference, never a filesystem path or arbitrary image URL. */
export async function prepareDesktopIcon(file: File): Promise<string> {
  if (file.size > 2 * 1024 * 1024) throw new Error('请选择不超过 2 MiB 的 PNG 图片')
  const bytes = new Uint8Array(await file.arrayBuffer())
  const signature = [137, 80, 78, 71, 13, 10, 26, 10]
  if (bytes.length < 33 || !signature.every((byte, index) => bytes[index] === byte)) {
    throw new Error('请选择有效的 PNG 图片')
  }
  const header = new DataView(bytes.buffer)
  if (header.getUint32(16) > 4096 || header.getUint32(20) > 4096) {
    throw new Error('原图长宽不能超过 4096 像素')
  }
  const image = await createImageBitmap(new Blob([bytes], { type: 'image/png' }))
  try {
    for (const size of [256, 128, 64]) {
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size
      const context = canvas.getContext('2d')
      if (!context) throw new Error('当前环境无法处理图标')
      const scale = size / Math.max(image.width, image.height)
      const width = image.width * scale
      const height = image.height * scale
      context.drawImage(image, (size - width) / 2, (size - height) / 2, width, height)
      const result = canvas.toDataURL('image/png')
      if (result.length <= 43714) return result
    }
    throw new Error('图标压缩后仍超过 32 KiB，请选择更简单的图片')
  } finally {
    image.close()
  }
}
