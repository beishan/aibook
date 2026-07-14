import Foundation
import SwiftData
import Observation

// MARK: - ServiceLocator（与安卓 ServiceLocator.kt 对齐 — 手写单例 DI 容器）

@MainActor
@Observable
final class ServiceLocator {
    static let shared = ServiceLocator()

    // MARK: - SwiftData

    let modelContainer: ModelContainer
    let startupIssue: PersistenceStartupIssue?

    // MARK: - Prefs Stores

    let readerSettingsStore: ReaderSettingsStore
    let serverConfigStore: ServerConfigStore

    // MARK: - Repositories

    let bookRepository: BookRepository
    let opdsConnectionRepository: OpdsConnectionRepository
    let opdsCatalogCacheRepository: OpdsCatalogCacheRepository
    let shelfFolderRepository: ShelfFolderRepository
    let serverRepository: ServerRepository

    // MARK: - Network

    let opdsCatalogService: OpdsCatalogService
    let apiClient: ApiClient
    let authApi: AuthApi
    let bookApi: BookApi

    // MARK: - Init

    private init() {
        // Container
        do {
            let bootstrap = try PersistenceBootstrap.bootstrap()
            self.modelContainer = bootstrap.container
            self.startupIssue = bootstrap.issue
        } catch {
            fatalError("Unable to create persistent or in-memory ModelContainer: \(error)")
        }

        // Prefs
        self.readerSettingsStore = ReaderSettingsStore()
        self.serverConfigStore = ServerConfigStore()

        // Network
        self.apiClient = ApiClient(configStore: serverConfigStore)
        self.authApi = AuthApi(client: apiClient)
        self.bookApi = BookApi(client: apiClient)

        // Repositories
        self.bookRepository = BookRepository(container: modelContainer)
        self.opdsConnectionRepository = OpdsConnectionRepository(container: modelContainer)
        self.opdsCatalogCacheRepository = OpdsCatalogCacheRepository(container: modelContainer)
        self.shelfFolderRepository = ShelfFolderRepository(container: modelContainer)
        self.serverRepository = ServerRepository(configStore: serverConfigStore, authApi: authApi)

        // Services
        self.opdsCatalogService = OpdsCatalogService()
    }
}
