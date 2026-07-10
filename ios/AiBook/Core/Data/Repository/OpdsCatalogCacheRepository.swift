import Foundation
import SwiftData

// MARK: - OpdsCatalogCacheRepository

@MainActor
final class OpdsCatalogCacheRepository {
    private let modelContext: ModelContext

    init(container: ModelContainer) {
        self.modelContext = ModelContext(container)
    }

    func fetchEntries(forConnectionId connId: String) -> [OpdsCatalogEntryDisplay] {
        let all = (try? modelContext.fetch(FetchDescriptor<OpdsCatalogEntryEntity>())) ?? []
        return all.filter { $0.connectionId == connId }.map { $0.toDisplay() }
    }

    func fetchAllEntries() -> [(entry: OpdsCatalogEntryDisplay, connectionId: String)] {
        let descriptor = FetchDescriptor<OpdsCatalogEntryEntity>(
            sortBy: [SortDescriptor(\.syncedAt, order: .reverse)]
        )
        return (try? modelContext.fetch(descriptor).map { ($0.toDisplay(), $0.connectionId) }) ?? []
    }

    func replaceConnectionEntries(connectionId connId: String, entries: [OpdsEntry]) {
        // 删除旧条目
        let all = (try? modelContext.fetch(FetchDescriptor<OpdsCatalogEntryEntity>())) ?? []
        all.filter { $0.connectionId == connId }.forEach { modelContext.delete($0) }

        // 插入新条目
        for entry in entries {
            let entity = OpdsCatalogEntryEntity()
            entity.connectionId = connId
            entity.title = entry.title
            entity.author = entry.author ?? ""
            entity.summary = entry.summary ?? ""
            entity.acquisitionHref = entry.acquisitionLink?.href ?? ""
            entity.acquisitionType = entry.acquisitionLink?.type ?? ""
            entity.coverUri = entry.coverLink?.href ?? ""
            entity.syncedAt = Date()
            modelContext.insert(entity)
        }
        try? modelContext.save()
    }

    func deleteEntries(forConnectionId connId: String) {
        let all = (try? modelContext.fetch(FetchDescriptor<OpdsCatalogEntryEntity>())) ?? []
        all.filter { $0.connectionId == connId }.forEach { modelContext.delete($0) }
        try? modelContext.save()
    }
}
