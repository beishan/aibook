import type { CrawlerChapter } from './crawler'

export const CHAPTER_PROGRESS_STAGES = [
  { key: 'prepare', label: '准备', color: '#60a5fa' },
  { key: 'fixed', label: '固定等待', color: '#eab308' },
  { key: 'random', label: '随机等待', color: '#f97316' },
  { key: 'other', label: '其他等待', color: '#a78bfa' },
  { key: 'request', label: '请求', color: '#06b6d4' },
  { key: 'parse', label: '解析', color: '#14b8a6' },
  { key: 'save', label: '保存', color: '#22c55e' },
] as const

type ChapterProgressInput = Pick<CrawlerChapter, 'crawlStatus' | 'currentSubStep' | 'crawlFinishedAt'>

export function chapterStageProgress(chapter: ChapterProgressInput) {
  const step = chapter.currentSubStep?.trim() || ''
  const visible = Boolean(step) || chapter.crawlStatus === 'CRAWLING'
  const finished = Boolean(chapter.crawlFinishedAt) && chapter.crawlStatus !== 'CRAWLING'
  const failed = visible && (chapter.crawlStatus === 'FAILED' || chapter.crawlStatus === 'CONTENT_SUSPECTED'
    || step.includes('本章采集异常'))
  let index = -1
  if (/^准备/.test(step)) index = 0
  else if (/^(固定等待|请求间隔等待)/.test(step)) index = 1
  else if (/^随机等待/.test(step)) index = 2
  else if (/^(自适应等待|访问时段等待|其他等待|异常重试)/.test(step)) index = 3
  else if (/^请求章节/.test(step)) index = 4
  else if (/^解析/.test(step)) index = 5
  else if (/^保存/.test(step)) index = 6

  const completed = finished && chapter.crawlStatus === 'COMPLETED' && !failed
  const activeIndex = !finished && !failed ? index : -1
  const description = !visible ? '' : completed ? '本章采集完成'
    : failed ? (step || '本章采集失败') : (step || '等待阶段信息')
  return { visible, finished, completed, failed, index, activeIndex, description }
}

export function chapterStageRowStyle(chapter: ChapterProgressInput): Record<string, string> {
  const progress = chapterStageProgress(chapter)
  if (!progress.visible) return {}
  const width = 100 / CHAPTER_PROGRESS_STAGES.length
  const pending = 'var(--chapter-stage-pending, #d1d5db)'
  const stops = CHAPTER_PROGRESS_STAGES.flatMap((stage, index) => {
    const passed = progress.completed || index < progress.index
      || (progress.finished && !progress.failed && index === progress.index)
    const color = passed ? stage.color : progress.failed && index === progress.index ? '#ef4444' : pending
    const start = `${index * width}%`
    const end = index === CHAPTER_PROGRESS_STAGES.length - 1 ? '100%' : `calc(${(index + 1) * width}% - 2px)`
    return [`${color} ${start} ${end}`, `transparent ${end} ${(index + 1) * width}%`]
  })
  const active = CHAPTER_PROGRESS_STAGES[progress.activeIndex]
  return {
    '--chapter-stage-track': `linear-gradient(to right, ${stops.join(', ')})`,
    '--chapter-stage-left': `calc(var(--chapter-stage-scroll-left, 0px) + var(--chapter-stage-viewport-width, 100%) * ${Math.max(0, progress.activeIndex) / CHAPTER_PROGRESS_STAGES.length})`,
    '--chapter-stage-width': `calc(var(--chapter-stage-viewport-width, 100%) / ${CHAPTER_PROGRESS_STAGES.length} - 2px)`,
    '--chapter-stage-color': active?.color || 'transparent',
  }
}

/** 横向滚动时将七段轨道固定在表格可见区域，移动端也能看到全部阶段。 */
export function observeChapterProgressViewport(table: HTMLElement): () => void {
  const viewport = table.querySelector<HTMLElement>('.el-table__body-wrapper .el-scrollbar__wrap')
  if (!viewport) return () => {}
  const update = () => {
    if (viewport.clientWidth <= 0) return
    table.style.setProperty('--chapter-stage-viewport-width', `${viewport.clientWidth}px`)
    table.style.setProperty('--chapter-stage-scroll-left', `${viewport.scrollLeft}px`)
  }
  const observer = new ResizeObserver(update)
  observer.observe(viewport)
  viewport.addEventListener('scroll', update, { passive: true })
  update()
  return () => {
    observer.disconnect()
    viewport.removeEventListener('scroll', update)
    table.style.removeProperty('--chapter-stage-viewport-width')
    table.style.removeProperty('--chapter-stage-scroll-left')
  }
}
