@preconcurrency import Foundation

// MARK: - ApiClient（URLSession 封装，对应安卓 Retrofit + OkHttp）

@MainActor
final class ApiClient {
    private let session: URLSession
    private let configStore: ServerConfigStore

    init(configStore: ServerConfigStore) {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)
        self.configStore = configStore
    }

    // MARK: - GET 请求

    func get<T: Decodable & Sendable>(_ path: String) async throws -> T {
        try await request(path: path, method: .get)
    }

    // MARK: - POST 请求

    func post<T: Decodable & Sendable>(
        _ path: String,
        body: (any Encodable & Sendable)? = nil
    ) async throws -> T {
        try await request(path: path, method: .post, body: body)
    }

    // MARK: - PUT 请求

    func put<T: Decodable & Sendable>(
        _ path: String,
        body: (any Encodable & Sendable)? = nil
    ) async throws -> T {
        try await request(path: path, method: .put, body: body)
    }

    // MARK: - DELETE 请求

    func delete(_ path: String) async throws {
        let _: EmptyResponse = try await request(path: path, method: .delete)
    }

    // MARK: - 通用请求

    func request<T: Decodable & Sendable>(
        path: String,
        method: HttpMethod = .get,
        body: (any Encodable & Sendable)? = nil
    ) async throws -> T {
        guard let baseUrl = configStore.serverUrl, !baseUrl.isEmpty else {
            throw ApiError.noServerUrl
        }

        let normalizedBase = baseUrl.hasSuffix("/") ? String(baseUrl.dropLast()) : baseUrl
        guard let url = URL(string: "\(normalizedBase)/\(path)") else {
            throw ApiError.invalidUrl
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method.rawValue
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")

        // Bearer Token
        if let token = configStore.token, !token.isEmpty {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        // Body
        if let body = body {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            urlRequest.httpBody = try encoder.encode(body)
        }

        let (data, response) = try await session.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200...299:
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode(T.self, from: data)
        case 401:
            throw ApiError.unauthorized
        case 404:
            throw ApiError.notFound
        default:
            let body = String(data: data, encoding: .utf8) ?? ""
            throw ApiError.httpError(statusCode: httpResponse.statusCode, message: body)
        }
    }

    // MARK: - 原始 Data 请求（用于下载等）

    func requestData(_ path: String, method: HttpMethod = .get) async throws -> Data {
        guard let baseUrl = configStore.serverUrl, !baseUrl.isEmpty else {
            throw ApiError.noServerUrl
        }

        let normalizedBase = baseUrl.hasSuffix("/") ? String(baseUrl.dropLast()) : baseUrl
        guard let url = URL(string: "\(normalizedBase)/\(path)") else {
            throw ApiError.invalidUrl
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method.rawValue

        if let token = configStore.token, !token.isEmpty {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let (data, response) = try await session.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw ApiError.invalidResponse
        }

        return data
    }
}

// MARK: - HttpMethod

enum HttpMethod: String, Sendable {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
}

// MARK: - EmptyResponse

struct EmptyResponse: Decodable, Sendable {}

// MARK: - ApiError

enum ApiError: LocalizedError {
    case noServerUrl
    case invalidUrl
    case invalidResponse
    case unauthorized
    case notFound
    case httpError(statusCode: Int, message: String)
    case decodingError(Error)

    var errorDescription: String? {
        switch self {
        case .noServerUrl: return "未配置服务器地址"
        case .invalidUrl: return "无效的请求地址"
        case .invalidResponse: return "无效的服务器响应"
        case .unauthorized: return "登录已过期，请重新登录"
        case .notFound: return "请求的资源不存在"
        case .httpError(let code, let msg): return "服务器错误 (\(code)): \(msg)"
        case .decodingError(let err): return "数据解析错误: \(err.localizedDescription)"
        }
    }
}
