import Foundation
import Observation

// MARK: - SettingsViewModel（与安卓 SettingsViewModel 对齐 — 完整实现）

@MainActor
@Observable
final class SettingsViewModel {
    var serverUrl: String = ""
    var isLoggedIn: Bool = false
    var username: String = ""
    var appThemeMode: AppThemeMode = .system
    var accentColor: AccentColor_ = .orange
    var wifiOnlySync: Bool = false
    var storageUsed: String = "计算中..."
    var bookCount: Int = 0
    var remoteBookCount: Int = 0

    private let locator: ServiceLocator

    init(locator: ServiceLocator) {
        self.locator = locator
    }

    func load() {
        serverUrl = locator.serverConfigStore.serverUrl ?? ""
        isLoggedIn = locator.serverRepository.isAuthenticated
        username = locator.serverConfigStore.username ?? ""
        appThemeMode = locator.readerSettingsStore.appThemeMode
        accentColor = locator.readerSettingsStore.accentColor
        wifiOnlySync = locator.serverConfigStore.wifiOnlySync
        bookCount = locator.bookRepository.fetchAllBooks().count
        calculateStorage()
    }

    func setThemeMode(_ mode: AppThemeMode) {
        locator.readerSettingsStore.appThemeMode = mode
        appThemeMode = mode
    }

    func setAccentColor(_ color: AccentColor_) {
        locator.readerSettingsStore.accentColor = color
        accentColor = color
    }

    func logout() {
        locator.serverRepository.logout()
        isLoggedIn = false
        username = ""
    }

    private func calculateStorage() {
        let docsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        if let size = FileManager.default.directorySize(at: docsDir) {
            storageUsed = ByteCountFormatter.string(fromByteCount: Int64(size), countStyle: .file)
        } else {
            storageUsed = "未知"
        }
    }
}

private extension FileManager {
    func directorySize(at url: URL) -> UInt64? {
        guard let enumerator = enumerator(at: url, includingPropertiesForKeys: [.fileSizeKey]) else { return nil }
        var total: UInt64 = 0
        for case let fileURL as URL in enumerator {
            let attrs = try? fileURL.resourceValues(forKeys: [.fileSizeKey])
            total += UInt64(attrs?.fileSize ?? 0)
        }
        return total
    }
}
