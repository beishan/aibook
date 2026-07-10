import Foundation

// MARK: - OpdsSyncCollector（与安卓 OpdsSyncCollector 对齐 — 递归遍历 OPDS 目录树）

final class OpdsSyncCollector {
    private let catalogService: OpdsCatalogService
    private let maxDepth: Int

    init(catalogService: OpdsCatalogService = OpdsCatalogService(), maxDepth: Int = 4) {
        self.catalogService = catalogService
        self.maxDepth = maxDepth
    }

    struct SyncCollection {
        let entries: [OpdsEntry]
        let totalCount: Int
    }

    /// 递归收集所有可获取的书籍条目
    func collect(
        from url: URL,
        username: String? = nil,
        password: String? = nil,
        depth: Int = 0
    ) async throws -> SyncCollection {
        guard depth < maxDepth else {
            return SyncCollection(entries: [], totalCount: 0)
        }

        let feed = try await catalogService.loadFeed(url: url, username: username, password: password)

        var allEntries: [OpdsEntry] = []

        for entry in feed.entries {
            if entry.acquisitionLink != nil {
                // 这是一个可下载的书籍
                allEntries.append(entry)
            } else if let alternateHref = entry.alternateLink?.href,
                      let alternateUrl = URL(string: alternateHref, relativeTo: url) {
                // 这是一个子目录，递归遍历
                let subCollection = try await collect(
                    from: alternateUrl,
                    username: username,
                    password: password,
                    depth: depth + 1
                )
                allEntries.append(contentsOf: subCollection.entries)
            }
        }

        return SyncCollection(entries: allEntries, totalCount: allEntries.count)
    }
}
