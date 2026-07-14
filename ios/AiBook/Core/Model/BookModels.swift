import Foundation

// MARK: - BookFormat（与安卓 BookFormat 枚举完全对齐）

enum BookFormat: String, CaseIterable, Codable, Sendable {
    case epub
    case txt
    case pdf
    case markdown
    case html
    case htm

    var `extension`: String {
        switch self {
        case .epub: return "epub"
        case .txt: return "txt"
        case .pdf: return "pdf"
        case .markdown: return "md"
        case .html: return "html"
        case .htm: return "htm"
        }
    }

    var displayName: String {
        switch self {
        case .epub: return "EPUB"
        case .txt: return "TXT"
        case .pdf: return "PDF"
        case .markdown: return "Markdown"
        case .html, .htm: return "HTML"
        }
    }

    static func fromFileName(_ fileName: String) -> BookFormat? {
        let ext = (fileName as NSString).pathExtension.lowercased().trimmingCharacters(in: .whitespaces)
        guard !ext.isEmpty else { return nil }
        return BookFormat.allCases.first { $0.extension == ext }
    }
}

// MARK: - ReadingStatus（与安卓 ReadingStatus 枚举对齐）

enum ReadingStatus: String, Codable, Sendable {
    case unread
    case reading
    case finished
    case wanted
}

// MARK: - LocalBook（与安卓 LocalBook data class 对齐）

struct LocalBook: Identifiable, Sendable {
    let id: String
    var title: String
    var author: String?
    var format: BookFormat
    var uri: String
    var sha256: String?
    var coverUri: String?
    var folderId: String?
    var status: ReadingStatus
    var favorite: Bool
    var shelved: Bool
    var visibleInStore: Bool
    var importedAt: Date
    var lastReadAt: Date?
    var progress: ReadingProgress
}

// MARK: - ShelfFolder（与安卓 ShelfFolder 对齐）

struct ShelfFolder: Identifiable, Sendable {
    let id: String
    var name: String
    var createdAtEpochMillis: Int64
}

// MARK: - ShelfFolderSelection（与安卓 sealed interface 对齐）

enum ShelfFolderSelection: Equatable, Sendable {
    case all
    case unfiled
    case folder(folderId: String)
}

// MARK: - ShelfFolderCatalog（与安卓 object 对齐）

enum ShelfFolderCatalog {
    static func filterBooks(_ books: [LocalBook], selection: ShelfFolderSelection) -> [LocalBook] {
        switch selection {
        case .all:
            return books
        case .unfiled:
            return books.filter { $0.folderId == nil }
        case .folder(let folderId):
            return books.filter { $0.folderId == folderId }
        }
    }

    static func folderCounts(_ books: [LocalBook]) -> [String: Int] {
        var counts: [String: Int] = [:]
        for book in books {
            if let folderId = book.folderId {
                counts[folderId, default: 0] += 1
            }
        }
        return counts
    }
}

// MARK: - ReadingProgress（与安卓 ReadingProgress 对齐）

struct ReadingProgress: Sendable {
    var chapterHref: String?
    var chapterTitle: String?
    var chapterIndex: Int?
    var lineIndex: Int?
    var scrollOffset: Int
    var percent: Float
    var positionLabel: String?

    init(
        chapterHref: String? = nil,
        chapterTitle: String? = nil,
        chapterIndex: Int? = nil,
        lineIndex: Int? = nil,
        scrollOffset: Int = 0,
        percent: Float = 0,
        positionLabel: String? = nil
    ) {
        self.chapterHref = chapterHref
        self.chapterTitle = chapterTitle
        self.chapterIndex = chapterIndex
        self.lineIndex = lineIndex
        self.scrollOffset = scrollOffset
        self.percent = percent
        self.positionLabel = positionLabel
    }
}

// MARK: - ReaderSettings（与安卓 ReaderSettings 对齐）

struct ReaderSettings: Sendable {
    var fontScale: Float
    var fontType: ReaderFontType
    var customFontName: String?
    var customFontPath: String?
    var lineHeight: Float
    var theme: ReaderTheme
    var paragraphSpacing: ParagraphSpacing
    var textAlignment: TextAlignment_
    var pageTurnMode: PageTurnMode
    var autoBrightness: Bool
    var screenAlwaysOn: Bool

    init(
        fontScale: Float = 1.0,
        fontType: ReaderFontType = .system,
        customFontName: String? = nil,
        customFontPath: String? = nil,
        lineHeight: Float = 1.45,
        theme: ReaderTheme = .paper,
        paragraphSpacing: ParagraphSpacing = .small,
        textAlignment: TextAlignment_ = .left,
        pageTurnMode: PageTurnMode = .simulation,
        autoBrightness: Bool = true,
        screenAlwaysOn: Bool = false
    ) {
        self.fontScale = fontScale
        self.fontType = fontType
        self.customFontName = customFontName
        self.customFontPath = customFontPath
        self.lineHeight = lineHeight
        self.theme = theme
        self.paragraphSpacing = paragraphSpacing
        self.textAlignment = textAlignment
        self.pageTurnMode = pageTurnMode
        self.autoBrightness = autoBrightness
        self.screenAlwaysOn = screenAlwaysOn
    }
}

// MARK: - ReaderFontType（与安卓枚举对齐）

enum ReaderFontType: String, CaseIterable, Sendable {
    case system
    case serif
    case sansSerif
    case monospace
    case custom
}

// MARK: - ReaderFontOption（与安卓 data class 对齐）

struct ReaderFontOption: Sendable {
    let type: ReaderFontType
    let label: String
    let description: String
}

// MARK: - ReaderFontCatalog（与安卓 object 对齐）

enum ReaderFontCatalog {
    static let builtInFonts: [ReaderFontOption] = [
        ReaderFontOption(type: .system, label: "系统字体", description: "使用设备默认字体"),
        ReaderFontOption(type: .serif, label: "衬线字体", description: "适合长篇阅读的传统排版"),
        ReaderFontOption(type: .sansSerif, label: "无衬线字体", description: "清爽现代，适合屏幕阅读"),
        ReaderFontOption(type: .monospace, label: "等宽字体", description: "字符宽度一致，适合代码与笔记"),
    ]

    static func isSupportedFontFile(_ fileName: String) -> Bool {
        let normalized = fileName.trimmingCharacters(in: .whitespaces).lowercased()
        return normalized.hasSuffix(".ttf") || normalized.hasSuffix(".otf")
    }

    static func selectedLabel(_ settings: ReaderSettings) -> String {
        switch settings.fontType {
        case .custom:
            return settings.customFontName?.nilIfEmpty ?? "本地导入字体"
        default:
            return builtInFonts.first { $0.type == settings.fontType }?.label ?? "系统字体"
        }
    }
}

// MARK: - ReaderTheme（与安卓枚举对齐）

enum ReaderTheme: String, CaseIterable, Sendable {
    case light
    case paper
    case green
    case gray
    case dark
}

// MARK: - ParagraphSpacing（与安卓枚举对齐）

enum ParagraphSpacing: String, CaseIterable, Sendable {
    case none
    case small
    case large
}

// MARK: - TextAlignment（与安卓枚举对齐，命名加 _ 后缀避免与 SwiftUI.TextAlignment 冲突）

enum TextAlignment_: String, CaseIterable, Sendable {
    case left
    case center
    case right
    case justify
}

// MARK: - PageTurnMode（与安卓枚举对齐）

enum PageTurnMode: String, CaseIterable, Sendable {
    case simulation
    case slide
    case cover
    case pan
    case vertical

    var usesPagedReading: Bool { self != .vertical }
}

// MARK: - AppThemeMode（与安卓枚举对齐）

enum AppThemeMode: String, CaseIterable, Sendable {
    case system
    case light
    case dark
}

// MARK: - AccentColor（与安卓枚举对齐）

enum AccentColor_: String, CaseIterable, Sendable {
    case orange
    case green
    case blue
    case purple
    case red

    var colorValue: UInt64 {
        switch self {
        case .orange: return 0xFFD47A1F
        case .green:  return 0xFF35A65B
        case .blue:   return 0xFF2F80ED
        case .purple: return 0xFF7B4AC5
        case .red:    return 0xFFE34A45
        }
    }
}

// MARK: - Helpers

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
