import type {
  ReaderEngine,
  ReaderEngineLocator,
  ReaderEngineSettings,
} from './ReaderEngine'

/** Compatibility adapter while the existing epub.js feature set is migrated incrementally. */
export class EpubJsReaderEngine implements ReaderEngine {
  readonly kind = 'epubjs' as const
  readonly capabilities = { search: true, highlights: true, textSelection: true }

  constructor(
    private readonly book: any,
    private readonly rendition: any,
    private readonly applyTheme: () => void,
  ) {}

  async next() {
    await this.rendition.next()
    return true
  }

  async previous() {
    await this.rendition.prev()
    return true
  }

  async goTo(locator: ReaderEngineLocator) {
    const target = locator.epubCfi || locator.href
    if (!target) return false
    await this.rendition.display(target)
    return true
  }

  getCurrentLocator(): ReaderEngineLocator | null {
    const location = this.rendition.currentLocation?.()
    if (!location?.start) return null
    const cfi = location.start.cfi
    const totalProgression = cfi && this.book.locations?.length?.()
      ? this.book.locations.percentageFromCfi(cfi)
      : undefined
    return {
      href: location.start.href,
      title: location.start.href,
      epubCfi: cfi,
      totalProgression,
      raw: location,
    }
  }

  async applySettings(settings: ReaderEngineSettings) {
    this.rendition.spread(settings.columnCount === 2 ? 'always' : 'none')
    this.applyTheme()
  }

  async destroy() {
    this.book.destroy()
  }
}

