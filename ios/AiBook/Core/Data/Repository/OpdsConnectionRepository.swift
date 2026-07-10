import Foundation
import SwiftData

// MARK: - OpdsConnectionRepository

@MainActor
final class OpdsConnectionRepository {
    private let modelContext: ModelContext

    init(container: ModelContainer) {
        self.modelContext = ModelContext(container)
    }

    func fetchAll() -> [OpdsConnection] {
        let descriptor = FetchDescriptor<OpdsConnectionEntity>(sortBy: [SortDescriptor(\.name)])
        return (try? modelContext.fetch(descriptor).map { $0.toModel() }) ?? []
    }

    func fetch(byId connId: String) -> OpdsConnection? {
        fetchEntity(byId: connId)?.toModel()
    }

    func save(_ connection: OpdsConnection) {
        if let existing = fetchEntity(byId: connection.id) {
            existing.update(from: connection)
        } else {
            let entity = OpdsConnectionEntity()
            entity.id = connection.id
            entity.update(from: connection)
            modelContext.insert(entity)
        }
        try? modelContext.save()
    }

    func delete(id connId: String) {
        guard let entity = fetchEntity(byId: connId) else { return }
        modelContext.delete(entity)
        try? modelContext.save()
    }

    func updateSyncState(id connId: String, state: OpdsSyncState, bookCount: Int = 0, error: String? = nil) {
        guard let entity = fetchEntity(byId: connId) else { return }
        entity.syncStateRaw = state.rawValue
        entity.bookCount = bookCount
        entity.lastErrorMessage = error ?? ""
        if state == .success { entity.lastSyncedAt = Date() }
        try? modelContext.save()
    }

    // MARK: - Private

    private func fetchEntity(byId connId: String) -> OpdsConnectionEntity? {
        let all = (try? modelContext.fetch(FetchDescriptor<OpdsConnectionEntity>())) ?? []
        return all.first { $0.id == connId }
    }
}
