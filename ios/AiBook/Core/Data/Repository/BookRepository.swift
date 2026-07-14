import Foundation
import SwiftData

// MARK: - BookRepository

@MainActor
final class BookRepository {
    private let modelContext: ModelContext

    init(container: ModelContainer) {
        self.modelContext = ModelContext(container)
    }

    // MARK: - 查询

    func fetchAllBooks() -> [LocalBook] {
        let descriptor = FetchDescriptor<BookEntity>(sortBy: [SortDescriptor(\.importedAt, order: .reverse)])
        return (try? modelContext.fetch(descriptor).map { $0.toLocalBook() }) ?? []
    }

    func fetchShelvedBooks() -> [LocalBook] {
        var descriptor = FetchDescriptor<BookEntity>(sortBy: [SortDescriptor(\.importedAt, order: .reverse)])
        descriptor.predicate = #Predicate<BookEntity> { $0.shelved }
        return (try? modelContext.fetch(descriptor).map { $0.toLocalBook() }) ?? []
    }

    func fetchBook(byId bookId: String) -> LocalBook? {
        fetchEntity(byId: bookId)?.toLocalBook()
    }

    // MARK: - 导入

    func importPrepared(_ prepared: PreparedBookImport) -> ImportResult {
        // 去重
        if let existing = findEntityBySha256(prepared.sha256) {
            if !existing.shelved {
                existing.shelved = true
                try? modelContext.save()
                return .restored(existing.id)
            }
            return .duplicate(existing.id)
        }

        // 复制文件
        let bookId = UUID().uuidString
        let booksDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("books", isDirectory: true)
        try? FileManager.default.createDirectory(at: booksDir, withIntermediateDirectories: true)
        let destUrl = booksDir.appendingPathComponent("\(bookId).\(prepared.format.extension)")

        do {
            try prepared.data.write(to: destUrl)
        } catch {
            return .failed
        }

        // 创建实体
        let entity = BookEntity()
        entity.id = bookId
        entity.formatRaw = prepared.format.rawValue
        entity.uri = destUrl.path
        entity.sha256 = prepared.sha256
        entity.shelved = true
        entity.importedAt = Date()

        // EPUB 元数据
        if prepared.format == .epub {
            if let epub = try? EpubParser.parse(url: destUrl) {
                entity.title = epub.title ?? prepared.normalizedTitle
                entity.author = epub.author ?? ""
                if let coverData = epub.coverData {
                    let coversDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                        .appendingPathComponent("covers", isDirectory: true)
                    try? FileManager.default.createDirectory(at: coversDir, withIntermediateDirectories: true)
                    let coverUrl = coversDir.appendingPathComponent("\(bookId).jpg")
                    try? coverData.write(to: coverUrl)
                    entity.coverUri = coverUrl.path
                }
            } else {
                entity.title = prepared.normalizedTitle
            }
        } else {
            entity.title = prepared.normalizedTitle
        }

        modelContext.insert(entity)
        try? modelContext.save()
        return .added(bookId)
    }

    // MARK: - 更新

    func updateProgress(bookId: String, progress: ReadingProgress) {
        guard let entity = fetchEntity(byId: bookId) else { return }
        entity.progress = progress
        entity.statusRaw = ReadingStatus.reading.rawValue
        entity.lastReadAt = Date()
        try? modelContext.save()
    }

    func updateShelved(bookId: String, shelved: Bool) {
        guard let entity = fetchEntity(byId: bookId) else { return }
        entity.shelved = shelved
        try? modelContext.save()
    }

    func updateFavorite(bookId: String, favorite: Bool) {
        guard let entity = fetchEntity(byId: bookId) else { return }
        entity.favorite = favorite
        try? modelContext.save()
    }

    func updateFolder(bookId: String, folderId: String?) {
        guard let entity = fetchEntity(byId: bookId) else { return }
        entity.folderId = folderId
        try? modelContext.save()
    }

    func deleteBook(bookId: String) {
        guard let entity = fetchEntity(byId: bookId) else { return }
        if !entity.uri.isEmpty { try? FileManager.default.removeItem(atPath: entity.uri) }
        if !entity.coverUri.isEmpty { try? FileManager.default.removeItem(atPath: entity.coverUri) }
        modelContext.delete(entity)
        try? modelContext.save()
    }

    // MARK: - Private

    private func fetchEntity(byId bookId: String) -> BookEntity? {
        let all = (try? modelContext.fetch(FetchDescriptor<BookEntity>())) ?? []
        return all.first { $0.id == bookId }
    }

    private func findEntityBySha256(_ sha: String) -> BookEntity? {
        let all = (try? modelContext.fetch(FetchDescriptor<BookEntity>())) ?? []
        return all.first { $0.sha256 == sha }
    }
}

// MARK: - ImportResult

enum ImportResult {
    case added(_ bookId: String)
    case restored(_ bookId: String)
    case duplicate(_ bookId: String)
    case unsupported
    case failed
}
