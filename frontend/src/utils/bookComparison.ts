export interface ComparisonCandidate {
  id: number
  title: string
  author?: string
  format: string
  sourceType?: string
  sourceSiteName?: string
  sourcePath?: string
  chapterCount?: number | null
  fileSize?: number | null
}

export interface BatchMergePlan {
  groupId: string
  title: string
  books: ComparisonCandidate[]
  targetBookId: number
}

export interface BatchMergeResult {
  groupId: string
  status: 'completed' | 'failed'
  message: string
}

export const sourceLabel = (book: ComparisonCandidate) => {
  if (book.sourceType === 'UPLOAD') return '上传'
  if (book.sourceType === 'DIRECTORY_SCAN') return '目录扫描'
  if (book.sourceType === 'CRAWLER') return book.sourceSiteName ? `网站采集 · ${book.sourceSiteName}` : '网站采集'
  return '未知来源'
}

export const sizeLabel = (bytes?: number | null) => {
  if (bytes == null || bytes < 0) return '大小未知'
  const units = ['B', 'KiB', 'MiB', 'GiB']
  let value = bytes
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index++
  }
  return `${value.toFixed(index ? 2 : 0)} ${units[index]}`
}

export const chapterCountLabel = (count?: number | null) => count == null ? '章节数待解析' : `${count} 章`

export const suggestedTarget = (books: ComparisonCandidate[]) => [...books].sort((left, right) =>
  (right.chapterCount ?? -1) - (left.chapterCount ?? -1)
  || (right.fileSize ?? -1) - (left.fileSize ?? -1)
  || left.id - right.id,
)[0]?.id ?? 0

export const validateBatchPlans = (plans: BatchMergePlan[]) => {
  if (!plans.length) throw new Error('请选择要合并的分组')
  const bookIds = new Set<number>()
  for (const plan of plans) {
    if (plan.books.length < 2 || !plan.books.some(book => book.id === plan.targetBookId)) {
      throw new Error('每组至少选择两本书，并选择组内要保留的书籍')
    }
    for (const book of plan.books) {
      if (bookIds.has(book.id)) throw new Error('同一本书不能出现在多个合并计划中')
      bookIds.add(book.id)
    }
  }
}

export async function executeBatchMerge(
  plans: BatchMergePlan[],
  loadVersions: (bookId: number) => Promise<{ id: number }[]>,
  merge: (targetId: number, sources: { bookId: number; versionIds: number[] }[]) => Promise<unknown>,
  onResult: (result: BatchMergeResult, completed: number) => void,
  shouldStop: () => boolean = () => false,
): Promise<BatchMergeResult[]> {
  validateBatchPlans(plans)
  const results: BatchMergeResult[] = []
  // 一组一笔现有合并事务，组间串行；失败组不影响已经成功的组。
  for (const plan of plans) {
    if (shouldStop()) break
    let stopAfterGroup = false
    let result: BatchMergeResult
    try {
      const sources: { bookId: number; versionIds: number[] }[] = []
      for (const book of plan.books.filter(book => book.id !== plan.targetBookId)) {
        const versions = await loadVersions(book.id)
        if (!versions.length) throw new Error(`《${book.title}》没有可合并的版本`)
        sources.push({ bookId: book.id, versionIds: versions.map(version => version.id) })
      }
      await merge(plan.targetBookId, sources)
      result = { groupId: plan.groupId, status: 'completed', message: '已合并' }
    } catch (cause: any) {
      stopAfterGroup = cause.response?.status === 401 || cause.response?.status === 403
      result = {
        groupId: plan.groupId,
        status: 'failed',
        message: cause.response?.data?.message || cause.message || '合并失败',
      }
    }
    results.push(result)
    onResult(result, results.length)
    if (stopAfterGroup) break
  }
  return results
}
