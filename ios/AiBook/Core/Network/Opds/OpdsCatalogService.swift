import Foundation

// MARK: - OpdsCatalogService（与安卓 OpdsCatalogService 对齐）

final class OpdsCatalogService {
    private let session: URLSession
    private let parser: OpdsFeedParser

    init(session: URLSession = .shared, parser: OpdsFeedParser = OpdsFeedParser()) {
        self.session = session
        self.parser = parser
    }

    /// 加载 OPDS Feed
    func loadFeed(url: URL, username: String? = nil, password: String? = nil) async throws -> OpdsFeed {
        var request = URLRequest(url: url)
        request.timeoutInterval = 30

        // Basic Auth
        if let username = username, let password = password,
           !username.isEmpty, !password.isEmpty {
            let credentials = "\(username):\(password)"
            if let data = credentials.data(using: .utf8) {
                let base64 = data.base64EncodedString()
                request.setValue("Basic \(base64)", forHTTPHeaderField: "Authorization")
            }
        }

        let (data, _) = try await session.data(for: request)

        guard let feed = parser.parse(data: data) else {
            throw OpdsError.parseFailed
        }

        return feed
    }

    /// 下载 OPDS 书籍文件
    func downloadBook(url: URL, username: String? = nil, password: String? = nil) async throws -> Data {
        var request = URLRequest(url: url)
        request.timeoutInterval = 120

        if let username = username, let password = password,
           !username.isEmpty, !password.isEmpty {
            let credentials = "\(username):\(password)"
            if let data = credentials.data(using: .utf8) {
                let base64 = data.base64EncodedString()
                request.setValue("Basic \(base64)", forHTTPHeaderField: "Authorization")
            }
        }

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw OpdsError.downloadFailed
        }

        return data
    }
}

enum OpdsError: LocalizedError {
    case parseFailed
    case downloadFailed
    case invalidUrl

    var errorDescription: String? {
        switch self {
        case .parseFailed: return "OPDS 目录解析失败"
        case .downloadFailed: return "书籍下载失败"
        case .invalidUrl: return "无效的 OPDS 地址"
        }
    }
}
