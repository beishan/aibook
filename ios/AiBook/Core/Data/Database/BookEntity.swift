import Foundation
import SwiftData

// MARK: - BookEntity（与安卓 BookEntity 对齐）

@Model
final class BookEntity {
    var id: String = UUID().uuidString
    var title: String = ""
    var author: String = ""
    var formatRaw: String = BookFormat.epub.rawValue
    var uri: String = ""
    var sha256: String = ""
    var coverUri: String = ""
    var folderId: String?
    var statusRaw: String = ReadingStatus.unread.rawValue
    var favorite: Bool = false
    var shelved: Bool = true
    var visibleInStore: Bool = true
    var importedAt: Date = Date()
    var lastReadAt: Date?
    // 阅读进度
    var chapterHref: String?
    var chapterTitle: String?
    var chapterIndex: Int = 0
    var lineIndex: Int = 0
    var scrollOffset: Int = 0
    var progressPercent: Float = 0
    var positionLabel: String = ""
    // 远程关联
    var source: String?
    var remoteBookId: Int64?

    init() {}

    var format: BookFormat {
        get { BookFormat(rawValue: formatRaw) ?? .epub }
        set { formatRaw = newValue.rawValue }
    }

    var status: ReadingStatus {
        get { ReadingStatus(rawValue: statusRaw) ?? .unread }
        set { statusRaw = newValue.rawValue }
    }

    var progress: ReadingProgress {
        get {
            ReadingProgress(
                chapterHref: chapterHref,
                chapterTitle: chapterTitle,
                chapterIndex: chapterIndex,
                lineIndex: lineIndex,
                scrollOffset: scrollOffset,
                percent: progressPercent,
                positionLabel: positionLabel.isEmpty ? nil : positionLabel
            )
        }
        set {
            chapterHref = newValue.chapterHref
            chapterTitle = newValue.chapterTitle
            chapterIndex = newValue.chapterIndex ?? 0
            lineIndex = newValue.lineIndex ?? 0
            scrollOffset = newValue.scrollOffset
            progressPercent = newValue.percent
            positionLabel = newValue.positionLabel ?? ""
        }
    }

    /// 转换为领域模型
    func toLocalBook() -> LocalBook {
        LocalBook(
            id: id,
            title: title,
            author: author.isEmpty ? nil : author,
            format: format,
            uri: uri,
            sha256: sha256.isEmpty ? nil : sha256,
            coverUri: coverUri.isEmpty ? nil : coverUri,
            folderId: folderId,
            status: status,
            favorite: favorite,
            shelved: shelved,
            visibleInStore: visibleInStore,
            importedAt: importedAt,
            lastReadAt: lastReadAt,
            progress: progress
        )
    }

    /// 从领域模型填充
    func update(from book: LocalBook) {
        title = book.title
        author = book.author ?? ""
        formatRaw = book.format.rawValue
        uri = book.uri
        sha256 = book.sha256 ?? ""
        coverUri = book.coverUri ?? ""
        folderId = book.folderId
        statusRaw = book.status.rawValue
        favorite = book.favorite
        shelved = book.shelved
        visibleInStore = book.visibleInStore
        importedAt = book.importedAt
        lastReadAt = book.lastReadAt
        progress = book.progress
    }
}
