import Foundation
import SwiftData

// MARK: - AiBookContainer（SwiftData ModelContainer 配置）

@MainActor
enum AiBookContainer {
    static func createPersistent() throws -> ModelContainer {
        let schema = Schema([
            BookEntity.self,
            OpdsConnectionEntity.self,
            OpdsCatalogEntryEntity.self,
            ShelfFolderEntity.self,
            ScanDirectoryEntity.self,
        ])
        let config = ModelConfiguration(isStoredInMemoryOnly: false)
        return try ModelContainer(for: schema, configurations: config)
    }

    static func createInMemory() throws -> ModelContainer {
        let schema = Schema([
            BookEntity.self,
            OpdsConnectionEntity.self,
            OpdsCatalogEntryEntity.self,
            ShelfFolderEntity.self,
            ScanDirectoryEntity.self,
        ])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        return try ModelContainer(for: schema, configurations: config)
    }
}
