import Foundation
import SwiftData

// MARK: - OpdsCatalogEntryEntity（与安卓 OpdsCatalogEntryEntity 对齐）

@Model
final class OpdsCatalogEntryEntity {
    var id: String = UUID().uuidString
    var connectionId: String = ""
    var title: String = ""
    var author: String = ""
    var summary: String = ""
    var acquisitionHref: String = ""
    var acquisitionType: String = ""
    var coverUri: String = ""
    var syncedAt: Date = Date()

    init() {}

    func toDisplay() -> OpdsCatalogEntryDisplay {
        OpdsCatalogEntryDisplay(
            title: title,
            author: author.isEmpty ? nil : author,
            summary: summary.isEmpty ? nil : summary,
            acquisitionHref: acquisitionHref.isEmpty ? nil : acquisitionHref,
            acquisitionType: acquisitionType.isEmpty ? nil : acquisitionType,
            coverUri: coverUri.isEmpty ? nil : coverUri
        )
    }
}
