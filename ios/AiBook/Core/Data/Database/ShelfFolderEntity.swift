import Foundation
import SwiftData

// MARK: - ShelfFolderEntity（与安卓 ShelfFolderEntity 对齐）

@Model
final class ShelfFolderEntity {
    var id: String = UUID().uuidString
    var name: String = ""
    var createdAtEpochMillis: Int64 = 0

    init() {}

    func toModel() -> ShelfFolder {
        ShelfFolder(
            id: id,
            name: name,
            createdAtEpochMillis: createdAtEpochMillis
        )
    }
}
