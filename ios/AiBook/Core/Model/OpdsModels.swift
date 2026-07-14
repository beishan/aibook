import Foundation

// MARK: - OpdsFeed（与安卓 OpdsFeed 对齐）

struct OpdsFeed: Sendable {
    let title: String?
    let entries: [OpdsEntry]
}

// MARK: - OpdsEntry（与安卓 OpdsEntry 对齐）

struct OpdsEntry: Sendable {
    let title: String
    let author: String?
    let summary: String?
    let acquisitionLink: OpdsLink?
    let alternateLink: OpdsLink?
    let coverLink: OpdsLink?
}

// MARK: - OpdsLink（与安卓 OpdsLink 对齐）

struct OpdsLink: Sendable {
    let href: String
    let type: String?
    let rel: String?
}

// MARK: - OpdsConnection（与安卓 OpdsConnection 对齐）

struct OpdsConnection: Identifiable, Sendable {
    let id: String
    var name: String
    var baseUrl: String
    var username: String?
    var password: String?
    var enabled: Bool
    var lastSyncedAt: Date?
    var bookCount: Int
    var syncState: OpdsSyncState
    var lastErrorMessage: String?
}

// MARK: - OpdsSyncState（与安卓枚举对齐）

enum OpdsSyncState: String, Codable, Sendable {
    case idle
    case syncing
    case success
    case failed
}
