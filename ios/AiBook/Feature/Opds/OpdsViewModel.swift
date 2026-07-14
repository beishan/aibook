import Foundation
import Observation

// MARK: - OpdsViewModel（与安卓 OpdsViewModel 对齐 — 完整实现）

@MainActor
@Observable
final class OpdsViewModel {
    var connections: [OpdsConnection] = []
    var isLoading: Bool = false
    var errorMessage: String?
    var successMessage: String?

    // 浏览状态
    var isBrowsing: Bool = false
    var browsingConnection: OpdsConnection?
    var browsingEntries: [OpdsEntry] = []
    var browsingTitle: String = ""
    var browsingSubLinks: [(title: String, href: String)] = []
    var navigationStack: [(url: String, title: String)] = []

    // 下载状态
    var downloadingBookId: String?
    var downloadProgress: Double = 0

    private let locator: ServiceLocator

    init(locator: ServiceLocator) {
        self.locator = locator
    }

    // MARK: - 连接管理

    func load() {
        connections = locator.opdsConnectionRepository.fetchAll()
    }

    func addConnection(name: String, url: String, username: String?, password: String?) {
        guard !name.isEmpty, !url.isEmpty else {
            errorMessage = "名称和地址不能为空"
            return
        }

        // 确保 URL 有协议前缀
        let normalizedUrl = url.hasPrefix("http") ? url : "http://\(url)"

        let conn = OpdsConnection(
            id: UUID().uuidString,
            name: name,
            baseUrl: normalizedUrl,
            username: username?.nilIfEmpty,
            password: password?.nilIfEmpty,
            enabled: true,
            lastSyncedAt: nil,
            bookCount: 0,
            syncState: .idle,
            lastErrorMessage: nil
        )
        locator.opdsConnectionRepository.save(conn)
        load()
        successMessage = "书源添加成功"
    }

    func updateConnection(id: String, name: String, url: String, username: String?, password: String?) {
        guard var conn = connections.first(where: { $0.id == id }) else { return }
        conn.name = name
        conn.baseUrl = url.hasPrefix("http") ? url : "http://\(url)"
        conn.username = username?.nilIfEmpty
        conn.password = password?.nilIfEmpty
        locator.opdsConnectionRepository.save(conn)
        load()
        successMessage = "书源更新成功"
    }

    func deleteConnection(id: String) {
        locator.opdsConnectionRepository.delete(id: id)
        locator.opdsCatalogCacheRepository.deleteEntries(forConnectionId: id)
        load()
        successMessage = "书源已删除"
    }

    func toggleConnection(id: String) {
        guard var conn = connections.first(where: { $0.id == id }) else { return }
        conn.enabled.toggle()
        locator.opdsConnectionRepository.save(conn)
        load()
    }

    // MARK: - 同步

    func syncConnection(id: String) async {
        guard let conn = connections.first(where: { $0.id == id }),
              let url = URL(string: conn.baseUrl) else {
            await MainActor.run { errorMessage = "无效的书源地址" }
            return
        }

        await MainActor.run {
            locator.opdsConnectionRepository.updateSyncState(id: id, state: .syncing)
            load()
        }

        do {
            let collector = OpdsSyncCollector()
            let result = try await collector.collect(
                from: url,
                username: conn.username,
                password: conn.password
            )

            await MainActor.run {
                locator.opdsCatalogCacheRepository.replaceConnectionEntries(
                    connectionId: id,
                    entries: result.entries
                )
                locator.opdsConnectionRepository.updateSyncState(
                    id: id, state: .success, bookCount: result.totalCount
                )
                load()
                successMessage = "同步成功，共 \(result.totalCount) 本书"
            }
        } catch {
            await MainActor.run {
                locator.opdsConnectionRepository.updateSyncState(
                    id: id, state: .failed, error: error.localizedDescription
                )
                load()
                errorMessage = "同步失败: \(error.localizedDescription)"
            }
        }
    }

    func syncAll() async {
        for conn in connections where conn.enabled {
            await syncConnection(id: conn.id)
        }
    }

    // MARK: - 目录浏览

    func browseConnection(_ conn: OpdsConnection) async {
        guard let url = URL(string: conn.baseUrl) else {
            await MainActor.run { errorMessage = "无效的书源地址" }
            return
        }

        await MainActor.run {
            browsingConnection = conn
            isBrowsing = true
            browsingTitle = conn.name
            navigationStack = [(conn.baseUrl, conn.name)]
            isLoading = true
        }

        await loadFeed(url: url, connection: conn)
    }

    func browseSubLink(_ href: String, title: String) async {
        guard let conn = browsingConnection else { return }

        // 构建完整 URL
        guard let baseUrl = navigationStack.last?.0,
              let fullUrl = URL(string: href, relativeTo: URL(string: baseUrl)) ?? URL(string: href) else {
            await MainActor.run { errorMessage = "无效的链接地址" }
            return
        }

        await MainActor.run {
            navigationStack.append((fullUrl.absoluteString, title))
            browsingTitle = title
            isLoading = true
        }

        await loadFeed(url: fullUrl, connection: conn)
    }

    func navigateBack() async {
        guard navigationStack.count > 1 else { return }
        navigationStack.removeLast()

        let prev = navigationStack.last!
        browsingTitle = prev.title

        guard let url = URL(string: prev.0), let conn = browsingConnection else { return }
        await MainActor.run { isLoading = true }
        await loadFeed(url: url, connection: conn)
    }

    var canNavigateBack: Bool {
        navigationStack.count > 1
    }

    private func loadFeed(url: URL, connection: OpdsConnection) async {
        do {
            let feed = try await locator.opdsCatalogService.loadFeed(
                url: url,
                username: connection.username,
                password: connection.password
            )

            await MainActor.run {
                // 分类：子链接 vs 书籍条目
                var subLinks: [(title: String, href: String)] = []
                var entries: [OpdsEntry] = []

                for entry in feed.entries {
                    if entry.acquisitionLink != nil {
                        entries.append(entry)
                    } else if let altLink = entry.alternateLink {
                        subLinks.append((title: entry.title, href: altLink.href))
                    }
                }

                browsingSubLinks = subLinks
                browsingEntries = entries
                isLoading = false
            }
        } catch {
            await MainActor.run {
                isLoading = false
                errorMessage = "加载失败: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - 下载

    func downloadBook(_ entry: OpdsEntry) async {
        guard let conn = browsingConnection,
              let acquisitionHref = entry.acquisitionLink?.href else {
            await MainActor.run { errorMessage = "无法下载此书籍" }
            return
        }

        // 构建下载 URL
        guard let baseUrl = navigationStack.last?.0,
              let downloadUrl = URL(string: acquisitionHref, relativeTo: URL(string: baseUrl)) ?? URL(string: acquisitionHref) else {
            await MainActor.run { errorMessage = "无效的下载地址" }
            return
        }

        await MainActor.run {
            downloadingBookId = entry.title
            downloadProgress = 0
        }

        do {
            let data = try await locator.opdsCatalogService.downloadBook(
                url: downloadUrl,
                username: conn.username,
                password: conn.password
            )

            // 确定文件格式
            let contentType = entry.acquisitionLink?.type ?? ""
            let ext = fileExtension(from: contentType, href: acquisitionHref)
            let fileName = "\(entry.title).\(ext)"

            // 保存到临时文件
            let tempDir = FileManager.default.temporaryDirectory
            let tempFile = tempDir.appendingPathComponent(fileName)
            try data.write(to: tempFile)

            // 导入到书库
            let preparation = await BookImportPreparer().prepare(url: tempFile, fileName: fileName)
            let result: ImportResult
            switch preparation {
            case .prepared(let prepared):
                result = locator.bookRepository.importPrepared(prepared)
            case .unsupported:
                result = .unsupported
            case .failed:
                result = .failed
            }

            // 清理临时文件
            try? FileManager.default.removeItem(at: tempFile)

            await MainActor.run {
                downloadingBookId = nil
                switch result {
                case .added:
                    successMessage = "《\(entry.title)》下载成功"
                case .duplicate:
                    successMessage = "《\(entry.title)》已存在"
                case .restored:
                    successMessage = "《\(entry.title)》已恢复"
                case .unsupported:
                    errorMessage = "不支持的文件格式"
                case .failed:
                    errorMessage = "下载失败"
                }
            }
        } catch {
            await MainActor.run {
                downloadingBookId = nil
                errorMessage = "下载失败: \(error.localizedDescription)"
            }
        }
    }

    private func fileExtension(from contentType: String, href: String) -> String {
        if contentType.contains("epub") { return "epub" }
        if contentType.contains("pdf") { return "pdf" }
        if contentType.contains("html") { return "html" }
        if contentType.contains("text") { return "txt" }
        // 从 URL 推断
        let urlExt = (href as NSString).pathExtension.lowercased()
        if !urlExt.isEmpty { return urlExt }
        return "epub" // 默认
    }

    // MARK: - 消息清除

    func clearMessages() {
        errorMessage = nil
        successMessage = nil
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
