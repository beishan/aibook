import Foundation
import SwiftData

// MARK: - AiBookContainer（SwiftData ModelContainer 配置）

@MainActor
enum AiBookContainer {
    static func create() -> ModelContainer {
        let schema = Schema([
            BookEntity.self,
            OpdsConnectionEntity.self,
            OpdsCatalogEntryEntity.self,
            ShelfFolderEntity.self,
            ScanDirectoryEntity.self,
        ])
        let config = ModelConfiguration(isStoredInMemoryOnly: false)
        do {
            return try ModelContainer(for: schema, configurations: config)
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }

    static func createInMemory() -> ModelContainer {
        let schema = Schema([
            BookEntity.self,
            OpdsConnectionEntity.self,
            OpdsCatalogEntryEntity.self,
            ShelfFolderEntity.self,
            ScanDirectoryEntity.self,
        ])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        do {
            return try ModelContainer(for: schema, configurations: config)
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }
}
