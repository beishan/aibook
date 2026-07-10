import Foundation
import Observation

// MARK: - StoreViewModel（与安卓 StoreViewModel 对齐 — 完整实现）

@Observable
final class StoreViewModel {
    var allBooks: [StoreBook] = []
    var filteredBooks: [StoreBook] = []
    var filter = StoreCatalogFilter()
    var viewMode: StoreViewMode = .grid
    var isManaging: Bool = false
    var selectedIds: Set<String> = []
    var sources: [(id: String, name: String)] = []

    private let locator: ServiceLocator

    init(locator: ServiceLocator = .shared) {
        self.locator = locator
    }

    // MARK: - 加载

    func load() {
        let localBooks = locator.bookRepository.fetchAllBooks()
        let connections = locator.opdsConnectionRepository.fetchAll()

        // 构建来源列表
        sources = [("local", "本地书籍")]
        for conn in connections {
            sources.append((conn.id, conn.name))
        }

        // 聚合 OPDS 条目
        var opdsEntries: [(entry: OpdsCatalogEntryDisplay, connectionName: String?, connectionId: String)] = []
        for conn in connections {
            let entries = locator.opdsCatalogCacheRepository.fetchEntries(forConnectionId: conn.id)
            for entry in entries {
                opdsEntries.append((entry, conn.name, conn.id))
            }
        }

        allBooks = StoreCatalog.aggregate(localBooks: localBooks, opdsEntries: opdsEntries)
        applyFilter()
    }

    // MARK: - 筛选

    func applyFilter() {
        filteredBooks = StoreCatalog.filterAndSort(allBooks, filter: filter)
    }

    func updateFilter(_ newFilter: StoreCatalogFilter) {
        filter = newFilter
        applyFilter()
    }

    func clearFilter() {
        filter = StoreCatalogFilter()
        applyFilter()
    }

    // MARK: - 视图模式

    func cycleViewMode() {
        let all = StoreViewMode.allCases
        guard let idx = all.firstIndex(of: viewMode) else { return }
        viewMode = all[(idx + 1) % all.count]
    }

    // MARK: - 管理模式

    func toggleSelection(bookId: String) {
        if selectedIds.contains(bookId) {
            selectedIds.remove(bookId)
        } else {
            selectedIds.insert(bookId)
        }
    }

    func bulkDelete() {
        for id in selectedIds {
            if let book = allBooks.first(where: { $0.id == id }),
               book.kind == .local,
               let localId = book.downloadedLocalId {
                locator.bookRepository.deleteBook(bookId: localId)
            }
        }
        isManaging = false
        selectedIds.removeAll()
        load()
    }

    // MARK: - OPDS 下载

    func downloadOpdsBook(_ book: StoreBook) async {
        guard let href = book.acquisitionHref,
              let sourceId = book.sourceId,
              let conn = locator.opdsConnectionRepository.fetch(byId: sourceId),
              let url = URL(string: href) else { return }

        do {
            let data = try await locator.opdsCatalogService.downloadBook(
                url: url,
                username: conn.username,
                password: conn.password
            )

            let ext = BookFormat.fromFileName(book.title)?.extension ?? "epub"
            let fileName = "\(book.title).\(ext)"
            let tempFile = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            try data.write(to: tempFile)

            await MainActor.run {
                _ = locator.bookRepository.importBook(from: tempFile, fileName: fileName)
                try? FileManager.default.removeItem(at: tempFile)
                load()
            }
        } catch {
            // 下载失败
        }
    }

    var hasOpdsBooks: Bool {
        allBooks.contains { $0.kind == .opds }
    }
}

// MARK: - StoreViewMode（4 种视图模式）

enum StoreViewMode: String, CaseIterable {
    case grid
    case listCover
    case compactList
    case smallGrid
}
