import Foundation

// MARK: - ServerRepository（与安卓 ServerRepository 对齐 — 完整实现）

@MainActor
final class ServerRepository {
    private let configStore: ServerConfigStore
    private let authApi: AuthApi

    init(configStore: ServerConfigStore, authApi: AuthApi) {
        self.configStore = configStore
        self.authApi = authApi
    }

    var isAuthenticated: Bool {
        guard let token = configStore.token else { return false }
        return !token.isEmpty
    }

    var baseUrl: String? {
        configStore.serverUrl
    }

    var username: String? {
        configStore.username
    }

    // MARK: - 登录

    func login(username: String, password: String) async throws -> Bool {
        guard let serverUrl = configStore.serverUrl, !serverUrl.isEmpty else {
            throw ServerError.noServerUrl
        }

        do {
            let response = try await authApi.login(username: username, password: password)
            configStore.token = response.token
            configStore.username = response.username
            configStore.email = response.email
            return true
        } catch let apiError as ApiError {
            switch apiError {
            case .unauthorized:
                throw ServerError.invalidCredentials
            default:
                throw ServerError.networkError(apiError)
            }
        }
    }

    // MARK: - 注册

    func register(username: String, password: String, email: String? = nil) async throws -> Bool {
        guard let serverUrl = configStore.serverUrl, !serverUrl.isEmpty else {
            throw ServerError.noServerUrl
        }

        do {
            let response = try await authApi.register(username: username, password: password, email: email)
            configStore.token = response.token
            configStore.username = response.username
            configStore.email = response.email
            return true
        } catch {
            throw ServerError.networkError(error)
        }
    }

    // MARK: - 退出

    func logout() {
        configStore.token = nil
        configStore.username = nil
        configStore.email = nil
    }

    // MARK: - 测试连接

    func testConnection(url: String) async throws -> Bool {
        guard let testUrl = URL(string: url) else {
            throw ServerError.invalidUrl
        }

        let session = URLSession(configuration: .default)
        var request = URLRequest(url: testUrl.appendingPathComponent("api/books?page=0&size=1"))
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (_, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ServerError.networkError(ApiError.invalidResponse)
        }

        return (200...299).contains(httpResponse.statusCode) || httpResponse.statusCode == 401
    }
}

// MARK: - ServerError

enum ServerError: LocalizedError {
    case noServerUrl
    case invalidUrl
    case invalidCredentials
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .noServerUrl: return "未配置服务器地址"
        case .invalidUrl: return "无效的服务器地址"
        case .invalidCredentials: return "用户名或密码错误"
        case .networkError(let error): return "网络错误: \(error.localizedDescription)"
        }
    }
}
