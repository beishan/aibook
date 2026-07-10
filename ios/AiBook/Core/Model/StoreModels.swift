import Foundation

// MARK: - StoreItemKind（与安卓 StoreItemKind 对齐）

enum StoreItemKind {
    case local
    case opds
}

// MARK: - StoreBook（与安卓 StoreBook 对齐）

struct StoreBook: Identifiable {
    var id: String { "\(kind)-\(sourceId ?? "")-\(title)-\(format.rawValue)" }

    let kind: StoreItemKind
    let sourceId: String?
    let sourceName: String?
    let title: String
    let author: String?
    let format: BookFormat
    let acquisitionHref: String?
    let acquisitionType: String?
    var downloadedLocalId: String?
    var coverUri: String?
    var shelved: Bool
}

// MARK: - StoreSortOption（与安卓枚举对齐）

enum StoreSortOption: String, CaseIterable {
    case recent
    case title
    case author
    case source
}

// MARK: - StoreCatalogFilter（与安卓 data class 对齐）

struct StoreCatalogFilter {
    var sourceId: String?
    var format: BookFormat?
    var category: String?
    var query: String?
    var sort: StoreSortOption

    init(
        sourceId: String? = nil,
        format: BookFormat? = nil,
        category: String? = nil,
        query: String? = nil,
        sort: StoreSortOption = .recent
    ) {
        self.sourceId = sourceId
        self.format = format
        self.category = category
        self.query = query
        self.sort = sort
    }
}

// MARK: - StoreCatalog（与安卓 StoreCatalog object 对齐 — 聚合+去重+筛选+排序）

enum StoreCatalog {
    /// 聚合本地书籍和 OPDS 缓存条目为统一的 StoreBook 列表
    static func aggregate(
        localBooks: [LocalBook],
        opdsEntries: [(entry: OpdsCatalogEntryDisplay, connectionName: String?, connectionId: String)]
    ) -> [StoreBook] {
        var result: [StoreBook] = []

        // 本地书籍
        for book in localBooks where book.visibleInStore {
            result.append(StoreBook(
                kind: .local,
                sourceId: nil,
                sourceName: nil,
                title: book.title,
                author: book.author,
                format: book.format,
                acquisitionHref: nil,
                acquisitionType: nil,
                downloadedLocalId: book.id,
                coverUri: book.coverUri,
                shelved: book.shelved
            ))
        }

        // OPDS 条目
        for (entry, connectionName, connectionId) in opdsEntries {
            let format = BookFormat.fromFileName(entry.title) ?? .epub

            // 去重：如果本地已有同名同格式书籍，跳过
            let duplicate = localBooks.contains { local in
                local.title.trimmingCharacters(in: .whitespaces) == entry.title.trimmingCharacters(in: .whitespaces)
                && local.format == format
            }
            if duplicate { continue }

            result.append(StoreBook(
                kind: .opds,
                sourceId: connectionId,
                sourceName: connectionName,
                title: entry.title,
                author: entry.author,
                format: format,
                acquisitionHref: entry.acquisitionHref,
                acquisitionType: entry.acquisitionType,
                downloadedLocalId: nil,
                coverUri: entry.coverUri,
                shelved: false
            ))
        }

        return result
    }

    /// 筛选 + 排序
    static func filterAndSort(_ books: [StoreBook], filter: StoreCatalogFilter) -> [StoreBook] {
        var filtered = books

        // 来源筛选
        if let sourceId = filter.sourceId {
            filtered = filtered.filter { $0.sourceId == sourceId || ($0.kind == .local && sourceId == "local") }
        }

        // 格式筛选
        if let format = filter.format {
            filtered = filtered.filter { $0.format == format }
        }

        // 搜索
        if let query = filter.query, !query.isEmpty {
            let q = query.lowercased()
            filtered = filtered.filter {
                ($0.title.lowercased().contains(q)) ||
                ($0.author?.lowercased().contains(q) ?? false)
            }
        }

        // 排序
        switch filter.sort {
        case .recent:
            break // 保持默认顺序
        case .title:
            filtered.sort { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .author:
            filtered.sort { ($0.author ?? "").localizedCaseInsensitiveCompare($1.author ?? "") == .orderedAscending }
        case .source:
            filtered.sort { ($0.sourceName ?? "").localizedCaseInsensitiveCompare($1.sourceName ?? "") == .orderedAscending }
        }

        return filtered
    }
}

// MARK: - OPDS 目录缓存条目展示模型（用于聚合）

struct OpdsCatalogEntryDisplay {
    let title: String
    let author: String?
    let summary: String?
    let acquisitionHref: String?
    let acquisitionType: String?
    let coverUri: String?
}
