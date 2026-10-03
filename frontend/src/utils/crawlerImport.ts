export interface CrawlerImportTarget {
  id: number
  bookName: string
}

export interface CrawlerImportFailure<T> {
  book: T
  reason: string
}

export function crawlerImportErrorReason(error: unknown): string {
  const failure = error as {
    code?: string
    response?: { status?: number; data?: { message?: string } }
  }
  if (failure?.code === 'ECONNABORTED' || failure?.code === 'ETIMEDOUT') {
    return '请求超时，入库结果尚未确认，请刷新状态后重试'
  }
  if (failure?.response?.status === 401) return '登录已过期，请重新登录后重试'
  return failure?.response?.data?.message || '请求失败，入库结果尚未确认，请刷新状态后重试'
}

/** 逐本提交，全部以服务端正文校验为准；失败项保留供用户重试。 */
export async function runCrawlerImports<T extends CrawlerImportTarget>(
  books: T[],
  importOne: (book: T) => Promise<void>,
  onProgress: (completed: number, total: number) => void,
): Promise<{ succeeded: T[]; failures: CrawlerImportFailure<T>[] }> {
  const targets = [...new Map(books.map(book => [book.id, book])).values()]
  const succeeded: T[] = []
  const failures: CrawlerImportFailure<T>[] = []
  onProgress(0, targets.length)
  for (const [index, book] of targets.entries()) {
    try {
      await importOne(book)
      succeeded.push(book)
    } catch (error) {
      failures.push({ book, reason: crawlerImportErrorReason(error) })
      if ((error as { response?: { status?: number } })?.response?.status === 401) {
        failures.push(...targets.slice(index + 1).map(remaining => ({
          book: remaining,
          reason: '登录已过期，尚未提交入库',
        })))
        onProgress(targets.length, targets.length)
        break
      }
    }
    onProgress(index + 1, targets.length)
  }
  return { succeeded, failures }
}
