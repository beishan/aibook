import Foundation

// MARK: - Screen（与安卓 Screen.kt 完全对齐 — 18 个路由）

enum Screen: Hashable {
    // 底部 Tab
    case shelf
    case store
    case opds
    case settings

    // 书架子页面
    case bookDetail(bookId: String)
    case reader(bookId: String)
    case remoteReader(bookId: String)

    // 书城子页面
    case storeCategory
    case storeRemoteBookDetail(bookId: String)

    // OPDS 子页面
    case opdsAddSource(connectionId: String?)

    // 设置子页面
    case themeSettings
    case scanDirectories
    case syncConnectionSettings
    case storageCache
    case privacyPermissions
    case about
}
