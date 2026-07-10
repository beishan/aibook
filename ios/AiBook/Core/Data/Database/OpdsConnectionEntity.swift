import Foundation
import SwiftData

// MARK: - OpdsConnectionEntity（与安卓 OpdsConnectionEntity 对齐）

@Model
final class OpdsConnectionEntity {
    var id: String = UUID().uuidString
    var name: String = ""
    var baseUrl: String = ""
    var username: String = ""
    var password: String = ""
    var enabled: Bool = true
    var lastSyncedAt: Date?
    var bookCount: Int = 0
    var syncStateRaw: String = OpdsSyncState.idle.rawValue
    var lastErrorMessage: String = ""

    init() {}

    var syncState: OpdsSyncState {
        get { OpdsSyncState(rawValue: syncStateRaw) ?? .idle }
        set { syncStateRaw = newValue.rawValue }
    }

    func toModel() -> OpdsConnection {
        OpdsConnection(
            id: id,
            name: name,
            baseUrl: baseUrl,
            username: username.isEmpty ? nil : username,
            password: password.isEmpty ? nil : password,
            enabled: enabled,
            lastSyncedAt: lastSyncedAt,
            bookCount: bookCount,
            syncState: syncState,
            lastErrorMessage: lastErrorMessage.isEmpty ? nil : lastErrorMessage
        )
    }

    func update(from conn: OpdsConnection) {
        name = conn.name
        baseUrl = conn.baseUrl
        username = conn.username ?? ""
        password = conn.password ?? ""
        enabled = conn.enabled
        lastSyncedAt = conn.lastSyncedAt
        bookCount = conn.bookCount
        syncStateRaw = conn.syncState.rawValue
        lastErrorMessage = conn.lastErrorMessage ?? ""
    }
}
