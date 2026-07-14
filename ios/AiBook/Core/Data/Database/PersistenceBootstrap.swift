import Foundation
import SwiftData

struct PersistenceStartupIssue: Identifiable, Sendable {
    let id: UUID
    let message: String

    init(
        id: UUID = UUID(),
        message: String = "本地数据库无法打开，当前已使用临时内存数据库。退出应用后，本次数据不会保留。"
    ) {
        self.id = id
        self.message = message
    }
}

struct PersistenceBootstrapResult {
    let container: ModelContainer
    let issue: PersistenceStartupIssue?
}

@MainActor
enum PersistenceBootstrap {
    static func bootstrap(
        persistent: () throws -> ModelContainer = AiBookContainer.createPersistent,
        inMemory: () throws -> ModelContainer = AiBookContainer.createInMemory
    ) throws -> PersistenceBootstrapResult {
        do {
            return PersistenceBootstrapResult(
                container: try persistent(),
                issue: nil
            )
        } catch {
            return PersistenceBootstrapResult(
                container: try inMemory(),
                issue: PersistenceStartupIssue()
            )
        }
    }
}
