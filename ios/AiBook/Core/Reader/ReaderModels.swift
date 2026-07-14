import Foundation

// MARK: - ReaderChapter（与安卓 ReaderChapter 对齐）

struct ReaderChapter: Identifiable, Sendable {
    var id: Int { index }
    let index: Int
    let title: String
    let href: String
    var content: [String]       // 纯文本段落数组
    var imageUri: String?
}

// MARK: - ReaderPage（与安卓 ReaderPage 对齐）

struct ReaderPage: Sendable {
    let index: Int
    let text: String
    let progress: Double
}

// MARK: - ReaderBookmark（与安卓 ReaderBookmark 对齐）

struct ReaderBookmark: Identifiable, Sendable {
    let id: String
    let bookId: String
    let chapterHref: String
    let chapterTitle: String
    let progress: Double
    let createdAt: Date
    var note: String? = nil
}

// MARK: - ReaderHighlight（与安卓 ReaderHighlight 对齐）

struct ReaderHighlight: Identifiable, Sendable {
    let id: String
    let bookId: String
    let chapterHref: String
    let startOffset: Int
    let endOffset: Int
    let excerpt: String
    let note: String?
    let createdAt: Date
}
