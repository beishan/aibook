export interface CrawlerImportTarget {
  id: number
  bookName: string
}

export interface CrawlerImportFailure<T> {
  book: T
  reason: string
}

export interface CrawlerImportJob {
  id: string
  status: 'RUNNING' | 'COMPLETED'
  total: number
  completed: number
  results: Array<{ crawlerBookId: number; bookId: number | null; success: boolean; reason?: string }>
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

/** 整批只提交一次；后续请求只查询进度，查询中断后可以恢复同一任务。 */
export async function runCrawlerImports<T extends CrawlerImportTarget>(
  books: T[],
  options: {
    submit: (ids: number[]) => Promise<CrawlerImportJob>
    progress: (jobId: string) => Promise<CrawlerImportJob>
    onProgress: (completed: number, total: number) => void
    onImported?: (book: T, libraryId: number) => void
    resumeJobId?: string
    waitForPoll?: () => Promise<void>
  },
): Promise<{ succeeded: T[]; failures: CrawlerImportFailure<T>[]; pendingJobId?: string }> {
  const targets = [...new Map(books.map(book => [book.id, book])).values()]
  const results = new Map<number, CrawlerImportJob['results'][number]>()
  let jobId = options.resumeJobId
  if (!targets.length) return { succeeded: [], failures: [] }
  options.onProgress(0, targets.length)
  let errorReason = '服务端未返回入库结果，请刷新状态后重试'
  let pendingJobId: string | undefined
  try {
    let job = jobId
      ? await options.progress(jobId)
      : await options.submit(targets.map(book => book.id))
    jobId = job.id
    while (true) {
      for (const item of job.results) {
        const target = targets.find(book => book.id === item.crawlerBookId)
        if (!target || results.has(item.crawlerBookId)) continue
        results.set(item.crawlerBookId, item)
        if (item.success && item.bookId != null) options.onImported?.(target, item.bookId)
      }
      options.onProgress(job.completed, job.total)
      if (job.status === 'COMPLETED') break
      await (options.waitForPoll?.() ?? new Promise(resolve => setTimeout(resolve, 1000)))
      job = await options.progress(jobId)
    }
  } catch (error) {
    errorReason = crawlerImportErrorReason(error)
    // 查询失败不重新提交仍可能正在执行的入库任务。
    if ((error as { response?: { status?: number } })?.response?.status !== 404) {
      pendingJobId = jobId
    }
  }
  return {
    succeeded: targets.filter(book => results.get(book.id)?.success),
    failures: targets.filter(book => !results.get(book.id)?.success).map(book => ({
      book,
      reason: results.get(book.id)?.reason || errorReason,
    })),
    pendingJobId,
  }
}
