export type ReaderEngineKind = 'epubjs' | 'readium'

export interface ReaderEngineLocator {
  href?: string
  title?: string
  progression?: number
  totalProgression?: number
  position?: number
  epubCfi?: string
  raw?: unknown
}

export interface ReaderEngineSettings {
  fontFamily: string
  fontSize: number
  lineHeight: number
  paragraphSpacing: number
  backgroundColor: string
  textColor: string
  paragraphIndent: boolean
  columnCount: 1 | 2
}

export interface ReaderEngineCapabilities {
  search: boolean
  highlights: boolean
  textSelection: boolean
}

export interface ReaderEngine {
  readonly kind: ReaderEngineKind
  readonly capabilities: ReaderEngineCapabilities
  next(): Promise<boolean>
  previous(): Promise<boolean>
  goTo(locator: ReaderEngineLocator): Promise<boolean>
  getCurrentLocator(): ReaderEngineLocator | null
  applySettings(settings: ReaderEngineSettings): Promise<void>
  destroy(): Promise<void>
}

export interface ReaderEngineEvents {
  onPositionChanged(locator: ReaderEngineLocator): void
  onError(error: Error): void
  onTextSelected?(selection: { text: string; locator?: ReaderEngineLocator }): void
}
