/**
 * 封面图片工具函数
 */

/**
 * 获取封面图片 URL
 * - 相对路径（covers/xxx.jpg）-> /api/covers/xxx.jpg
 * - 外部 URL（http/https）-> 通过代理访问，解决防盗链问题
 */
export function getCoverUrl(coverUrl: string | null | undefined): string {
  if (!coverUrl) return ''

  // 相对路径，转换为 API 路径
  if (coverUrl.startsWith('covers/')) {
    const filename = coverUrl.replace('covers/', '')
    return `/api/covers/${filename}`
  }

  // 完整 URL，通过代理访问（解决豆瓣等防盗链问题）
  if (coverUrl.startsWith('http://') || coverUrl.startsWith('https://')) {
    return `/api/covers/proxy?url=${encodeURIComponent(coverUrl)}`
  }

  // 其他相对路径
  if (coverUrl.startsWith('/')) {
    return coverUrl
  }

  return coverUrl
}
