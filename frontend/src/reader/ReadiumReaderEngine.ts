import type { Locator } from '@readium/shared'
import type {
  ReaderEngine,
  ReaderEngineEvents,
  ReaderEngineLocator,
  ReaderEngineSettings,
} from './ReaderEngine'

export interface ReadiumEngineSource {
  manifestUrl: string
  token: string | null
  container: HTMLElement
  initialLocator?: ReaderEngineLocator | null
  settings: ReaderEngineSettings
}

export class ReadiumReaderEngine implements ReaderEngine {
  readonly kind = 'readium' as const
  readonly capabilities = { search: false, highlights: false, textSelection: true }

  private navigator: any = null
  private publication: any = null
  private currentLocator: ReaderEngineLocator | null = null

  private constructor(
    private readonly events: ReaderEngineEvents,
  ) {}

  static async create(source: ReadiumEngineSource, events: ReaderEngineEvents) {
    const engine = new ReadiumReaderEngine(events)
    await engine.load(source)
    return engine
  }

  private async load(source: ReadiumEngineSource) {
    const [{ EpubNavigator }, { HttpFetcher, Manifest, Publication, Locator }] =
      await Promise.all([import('@readium/navigator'), import('@readium/shared')])

    const authenticatedFetch = (input: RequestInfo | URL, init: RequestInit = {}) => {
      const headers = new Headers(init.headers)
      if (source.token) headers.set('Authorization', `Bearer ${source.token}`)
      return fetch(input, { ...init, headers, credentials: 'same-origin' })
    }
    const response = await authenticatedFetch(source.manifestUrl)
    if (!response.ok) throw new Error(`Readium Manifest 加载失败（HTTP ${response.status}）`)
    const json = await response.json()
    const manifest = Manifest.deserialize(json)
    if (!manifest) throw new Error('Readium Manifest 格式无效')
    const selfLink = manifest.linkWithRel('self')
    manifest.setSelfLink(new URL(
      selfLink?.href || source.manifestUrl,
      window.location.origin,
    ).href)
    const fetcher = new HttpFetcher(authenticatedFetch, manifest.baseURL)
    this.publication = new Publication({ manifest, fetcher })

    let initialPosition: Locator | undefined
    if (source.initialLocator?.raw) {
      initialPosition = Locator.deserialize(source.initialLocator.raw)
    } else if (source.initialLocator?.href) {
      const link = this.publication.linkWithHref(source.initialLocator.href)
        || this.publication.readingOrder?.items?.find((item: any) =>
          item.href.endsWith(source.initialLocator?.href || '')
          || source.initialLocator?.href?.endsWith(item.href))
      initialPosition = link?.locator
      if (initialPosition && source.initialLocator.totalProgression != null) {
        initialPosition = initialPosition.copyWithLocations({
          totalProgression: source.initialLocator.totalProgression,
        })
      }
    }

    const listeners = {
      frameLoaded: () => undefined,
      positionChanged: (locator: Locator) => {
        this.currentLocator = this.fromReadiumLocator(locator)
        this.events.onPositionChanged(this.currentLocator)
      },
      timelineItemChanged: () => undefined,
      tap: () => false,
      click: () => false,
      zoom: () => undefined,
      miscPointer: () => undefined,
      scroll: () => undefined,
      customEvent: () => undefined,
      handleLocator: () => false,
      textSelected: (selection: any) => {
        const text = selection?.cleanText || selection?.text || ''
        if (text && this.events.onTextSelected) {
          this.events.onTextSelected({ text, locator: this.currentLocator || undefined })
        }
      },
      contentProtection: () => undefined,
      contextMenu: () => undefined,
      peripheral: () => undefined,
    }
    this.navigator = new EpubNavigator(
      source.container,
      this.publication,
      listeners,
      undefined,
      initialPosition,
      {
        preferences: this.toPreferences(source.settings),
        defaults: this.toPreferences(source.settings),
      },
    )
    await this.navigator.load()
  }

  async next() {
    return new Promise<boolean>(resolve => this.navigator.goForward(false, resolve))
  }

  async previous() {
    return new Promise<boolean>(resolve => this.navigator.goBackward(false, resolve))
  }

  async goTo(locator: ReaderEngineLocator) {
    const { Locator } = await import('@readium/shared')
    let target: Locator | undefined
    if (locator.raw) target = Locator.deserialize(locator.raw)
    if (!target && locator.href) {
      const link = this.publication.linkWithHref(locator.href)
        || this.publication.readingOrder?.items?.find((item: any) =>
          item.href.endsWith(locator.href) || locator.href?.endsWith(item.href))
      target = link?.locator
      if (target && locator.progression != null) {
        target = target.copyWithLocations({ progression: locator.progression })
      }
    }
    if (!target) return false
    return new Promise<boolean>(resolve => this.navigator.go(target, false, resolve))
  }

  getCurrentLocator() {
    return this.currentLocator
  }

  async applySettings(settings: ReaderEngineSettings) {
    const { EpubPreferences } = await import('@readium/navigator')
    await this.navigator.submitPreferences(new EpubPreferences(this.toPreferences(settings)))
  }

  async destroy() {
    if (this.navigator) await this.navigator.destroy()
    this.navigator = null
    this.publication = null
  }

  private toPreferences(settings: ReaderEngineSettings) {
    return {
      backgroundColor: settings.backgroundColor,
      textColor: settings.textColor,
      fontFamily: settings.fontFamily === 'inherit' ? null : settings.fontFamily,
      fontSize: settings.fontSize / 16,
      lineHeight: settings.lineHeight,
      paragraphSpacing: settings.paragraphSpacing / 16,
      paragraphIndent: settings.paragraphIndent ? 2 : 0,
      columnCount: settings.columnCount,
      scroll: false,
    }
  }

  private fromReadiumLocator(locator: Locator): ReaderEngineLocator {
    return {
      href: locator.href,
      title: locator.title,
      progression: locator.locations.progression,
      totalProgression: locator.locations.totalProgression,
      position: locator.locations.position,
      raw: locator.serialize(),
    }
  }
}
