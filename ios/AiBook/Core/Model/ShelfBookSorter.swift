import Foundation

// MARK: - ShelfSortOption（与安卓排序选项对齐）

enum ShelfSortOption: String, CaseIterable {
    case recentRead
    case importedAt
    case title
    case favoriteFirst
}

// MARK: - ShelfBookSorter（与安卓 ShelfBookSorter.kt 对齐）

enum ShelfBookSorter {
    static func sort(_ books: [LocalBook], by option: ShelfSortOption) -> [LocalBook] {
        switch option {
        case .recentRead:
            return books.sorted { a, b in
                let dateA = a.lastReadAt ?? .distantPast
                let dateB = b.lastReadAt ?? .distantPast
                return dateA == dateB ? a.id < b.id : dateA > dateB
            }
        case .importedAt:
            return books.sorted {
                $0.importedAt == $1.importedAt ? $0.id < $1.id : $0.importedAt > $1.importedAt
            }
        case .title:
            return books.sorted { a, b in
                let comparison = a.title.localizedCaseInsensitiveCompare(b.title)
                return comparison == .orderedSame ? a.id < b.id : comparison == .orderedAscending
            }
        case .favoriteFirst:
            return books.sorted { a, b in
                if a.favorite != b.favorite { return a.favorite }
                let dateA = a.lastReadAt ?? a.importedAt
                let dateB = b.lastReadAt ?? b.importedAt
                return dateA == dateB ? a.id < b.id : dateA > dateB
            }
        }
    }
}
