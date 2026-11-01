import chapterTitleCss from './chapterTitle.css?inline'

export const CHAPTER_TITLE_STYLES = ['original', 'compact', 'body', 'divider'] as const
export type ChapterTitleStyle = (typeof CHAPTER_TITLE_STYLES)[number]

export interface QuickChapterTitleOptions {
  style: ChapterTitleStyle
  fontSize: number | null
  bodyFontSize: number
}

export function chapterTitleFontSize(options: QuickChapterTitleOptions): number {
  if (options.fontSize != null) return options.fontSize
  if (options.style === 'body') return options.bodyFontSize
  if (options.style === 'compact') return 18
  if (options.style === 'divider') return 16
  return Math.min(40, Math.round(options.bodyFontSize * 1.6))
}

export function applyQuickChapterTitle(doc: Document, options: QuickChapterTitleOptions | null) {
  const root = doc.documentElement
  const previous = doc.getElementById('aibook-quick-chapter-title-style')
  if (!options) {
    root.removeAttribute('data-quick-chapter-title')
    root.removeAttribute('data-quick-chapter-title-custom-size')
    root.style.removeProperty('--quick-chapter-title-size')
    previous?.remove()
    return
  }
  root.setAttribute('data-quick-chapter-title', options.style)
  root.toggleAttribute('data-quick-chapter-title-custom-size', options.fontSize != null)
  root.style.setProperty('--quick-chapter-title-size', `${chapterTitleFontSize(options)}px`)
  if (!previous) {
    const style = doc.createElement('style')
    style.id = 'aibook-quick-chapter-title-style'
    style.textContent = chapterTitleCss
    doc.head.appendChild(style)
  }
}
