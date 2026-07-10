import Foundation

// MARK: - AuthApi（认证 API，与安卓 AuthApi 对齐）

struct AuthResponse: Decodable {
    let token: String
    let username: String
    let email: String?
    let role: String?
}

struct LoginRequest: Encodable {
    let username: String
    let password: String
}

struct RegisterRequest: Encodable {
    let username: String
    let password: String
    let email: String?
}

final class AuthApi {
    private let client: ApiClient

    init(client: ApiClient) {
        self.client = client
    }

    func login(username: String, password: String) async throws -> AuthResponse {
        try await client.post("api/auth/login", body: LoginRequest(username: username, password: password))
    }

    func register(username: String, password: String, email: String? = nil) async throws -> AuthResponse {
        try await client.post("api/auth/register", body: RegisterRequest(username: username, password: password, email: email))
    }
}
