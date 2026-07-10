import Foundation
import SwiftUI

// MARK: - ShelfViewModel（与安卓 ShelfViewModel 对齐）

@Observable
final class ShelfViewModel {
    var books: [LocalBook] = []
    var folders: [ShelfFolder] = []
    var selection: ShelfFolderSelection = .all
    var sortOption: ShelfSortOption = .recentRead
    var isManaging: Bool = false
    var selectedIds: Set<String> = []
    var showFolderPicker: Bool = false

    private let locator: ServiceLocator

    init(locator: ServiceLocator = .shared) {
        self.locator = locator
    }

    // MARK: - 计算属性

    var filteredBooks: [LocalBook] {
        let filtered = ShelfFolderCatalog.filterBooks(books, selection: selection)
        return ShelfBookSorter.sort(filtered, by: sortOption)
    }

    var recentBook: LocalBook? {
        books
            .filter { $0.status == .reading && $0.shelved }
            .sorted { ($0.lastReadAt ?? .distantPast) > ($1.lastReadAt ?? .distantPast) }
            .first
    }

    var folderCounts: [String: Int] {
        ShelfFolderCatalog.folderCounts(books)
    }

    // MARK: - 加载

    func load() {
        books = locator.bookRepository.fetchShelvedBooks()
        folders = locator.shelfFolderRepository.fetchAll()
    }

    // MARK: - 操作

    func cycleSort() {
        let all = ShelfSortOption.allCases
        guard let idx = all.firstIndex(of: sortOption) else { return }
        sortOption = all[(idx + 1) % all.count]
    }

    func toggleFavorite(bookId: String) {
        guard let book = books.first(where: { $0.id == bookId }) else { return }
        locator.bookRepository.updateFavorite(bookId: bookId, favorite: !book.favorite)
        load()
    }

    func moveToFolder(bookId: String, folderId: String?) {
        locator.bookRepository.updateFolder(bookId: bookId, folderId: folderId)
        load()
    }

    func removeFromShelf(bookId: String) {
        locator.bookRepository.updateShelved(bookId: bookId, shelved: false)
        load()
    }

    func createFolder(name: String) -> ShelfFolder {
        let folder = locator.shelfFolderRepository.create(name: name)
        load()
        return folder
    }

    func bulkFavorite() {
        for id in selectedIds {
            locator.bookRepository.updateFavorite(bookId: id, favorite: true)
        }
        load()
        isManaging = false
    }

    func bulkMoveToFolder(folderId: String?) {
        for id in selectedIds {
            locator.bookRepository.updateFolder(bookId: id, folderId: folderId)
        }
        load()
        isManaging = false
    }

    func bulkRemoveFromShelf() {
        for id in selectedIds {
            locator.bookRepository.updateShelved(bookId: id, shelved: false)
        }
        load()
        isManaging = false
    }

    func toggleSelection(bookId: String) {
        if selectedIds.contains(bookId) {
            selectedIds.remove(bookId)
        } else {
            selectedIds.insert(bookId)
        }
    }

    func importBooks(from urls: [URL]) {
        for url in urls {
            let fileName = url.lastPathComponent
            _ = locator.bookRepository.importBook(from: url, fileName: fileName)
        }
        load()
    }
}
