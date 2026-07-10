import Foundation
import SwiftData

// MARK: - ShelfFolderRepository

@MainActor
final class ShelfFolderRepository {
    private let modelContext: ModelContext

    init(container: ModelContainer) {
        self.modelContext = ModelContext(container)
    }

    func fetchAll() -> [ShelfFolder] {
        let descriptor = FetchDescriptor<ShelfFolderEntity>(
            sortBy: [SortDescriptor(\.createdAtEpochMillis)]
        )
        return (try? modelContext.fetch(descriptor).map { $0.toModel() }) ?? []
    }

    func create(name: String) -> ShelfFolder {
        let entity = ShelfFolderEntity()
        entity.id = UUID().uuidString
        entity.name = name
        entity.createdAtEpochMillis = Int64(Date().timeIntervalSince1970 * 1000)
        modelContext.insert(entity)
        try? modelContext.save()
        return entity.toModel()
    }

    func delete(id folderId: String) {
        let all = (try? modelContext.fetch(FetchDescriptor<ShelfFolderEntity>())) ?? []
        guard let entity = all.first(where: { $0.id == folderId }) else { return }
        modelContext.delete(entity)
        try? modelContext.save()
    }
}
