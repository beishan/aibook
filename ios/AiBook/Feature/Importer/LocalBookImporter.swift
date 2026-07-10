import Foundation
import Observation

// MARK: - LocalBookImporter（与安卓 LocalBookImportViewModel 对齐）

@Observable
final class LocalBookImporter {
    var addedCount: Int = 0
    var restoredCount: Int = 0
    var duplicateCount: Int = 0
    var unsupportedCount: Int = 0
    var failedCount: Int = 0
    var isImporting: Bool = false

    private let locator: ServiceLocator

    init(locator: ServiceLocator = .shared) {
        self.locator = locator
    }

    @MainActor func importBooks(from urls: [URL]) {
        isImporting = true
        addedCount = 0
        restoredCount = 0
        duplicateCount = 0
        unsupportedCount = 0
        failedCount = 0

        for url in urls {
            let fileName = url.lastPathComponent
            let result = locator.bookRepository.importBook(from: url, fileName: fileName)

            switch result {
            case .added: addedCount += 1
            case .restored: restoredCount += 1
            case .duplicate: duplicateCount += 1
            case .unsupported: unsupportedCount += 1
            case .failed: failedCount += 1
            }
        }

        isImporting = false
    }

    var summary: String {
        var parts: [String] = []
        if addedCount > 0 { parts.append("新增 \(addedCount) 本") }
        if restoredCount > 0 { parts.append("恢复 \(restoredCount) 本") }
        if duplicateCount > 0 { parts.append("重复 \(duplicateCount) 本") }
        if unsupportedCount > 0 { parts.append("不支持 \(unsupportedCount) 本") }
        if failedCount > 0 { parts.append("失败 \(failedCount) 本") }
        return parts.isEmpty ? "无文件" : parts.joined(separator: "，")
    }
}
