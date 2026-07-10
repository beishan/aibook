import SwiftUI

// MARK: - ContentView（TabView + NavigationStack 根视图，与安卓 AiBookApp.kt 对齐）

struct ContentView: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var selectedTab: Tab = .shelf

    enum Tab: Hashable {
        case shelf, store, opds, settings
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            // 书架
            NavigationStack {
                ShelfScreen()
                    .navigationDestination(for: Screen.self) { screen in
                        screenDestination(screen)
                    }
            }
            .tabItem {
                Label("书架", systemImage: "book.fill")
            }
            .tag(Tab.shelf)

            // 书城
            NavigationStack {
                BookStoreScreen()
                    .navigationDestination(for: Screen.self) { screen in
                        screenDestination(screen)
                    }
            }
            .tabItem {
                Label("书城", systemImage: "books.vertical")
            }
            .tag(Tab.store)

            // 发现
            NavigationStack {
                OpdsScreen()
                    .navigationDestination(for: Screen.self) { screen in
                        screenDestination(screen)
                    }
            }
            .tabItem {
                Label("发现", systemImage: "magnifyingglass")
            }
            .tag(Tab.opds)

            // 设置
            NavigationStack {
                SettingsScreen()
                    .navigationDestination(for: Screen.self) { screen in
                        screenDestination(screen)
                    }
            }
            .tabItem {
                Label("设置", systemImage: "gearshape")
            }
            .tag(Tab.settings)
        }
        .tint(DesignTokens.accent)
    }

    // MARK: - 统一导航目标

    @ViewBuilder
    private func screenDestination(_ screen: Screen) -> some View {
        switch screen {
        case .bookDetail(let bookId):
            BookDetailScreen(bookId: bookId)
        case .reader(let bookId):
            ReaderScreen(bookId: bookId, isRemote: false)
        case .remoteReader(let bookId):
            ReaderScreen(bookId: bookId, isRemote: true)
        case .storeCategory:
            StoreCategoryScreen(filter: StoreCatalogFilter()) { _ in }
        case .storeRemoteBookDetail(let bookId):
            StoreRemoteBookDetailScreen(bookId: bookId)
        case .opdsAddSource(let connectionId):
            OpdsAddSourceScreen(connectionId: connectionId)
        case .themeSettings:
            ThemeSettingsScreen()
        case .scanDirectories:
            ScanDirectoryScreen()
        case .syncConnectionSettings:
            SyncConnectionSettingsScreen()
        case .storageCache:
            StorageCacheScreen()
        case .privacyPermissions:
            PrivacyPermissionsScreen()
        case .about:
            AboutScreen()
        default:
            EmptyView()
        }
    }
}
