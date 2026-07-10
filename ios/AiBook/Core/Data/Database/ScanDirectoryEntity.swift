import Foundation
import SwiftData

// MARK: - ScanDirectoryEntity（与安卓 ScanDirectoryEntity 对齐）

@Model
final class ScanDirectoryEntity {
    var id: String = UUID().uuidString
    var path: String = ""
    var enabled: Bool = true
    var lastScannedAt: Date?
    var totalFiles: Int = 0
    var importedCount: Int = 0
    var skippedCount: Int = 0
    var errorCount: Int = 0

    init() {}
}
