import Foundation

// MARK: - BookApi（书籍 API，与安卓 BookApi 对齐）

struct BookListResponse: Decodable, Sendable {
    let content: [BookDto]
    let totalElements: Int
    let totalPages: Int
    let number: Int
}

struct BookDto: Decodable, Sendable {
    let id: Int64
    let title: String
    let author: String?
    let format: String?
    let coverUrl: String?
    let description: String?
    let fileSize: Int64?
    let pageCount: Int?
}

struct ReadingProgressRequest: Encodable, Sendable {
    let chapterIndex: Int
    let chapterTitle: String?
    let progressPercent: Double
}

struct ReadingTimeRequest: Encodable, Sendable {
    let readingTimeSeconds: Int
}

@MainActor
final class BookApi {
    private let client: ApiClient

    init(client: ApiClient) {
        self.client = client
    }

    func getBooks(page: Int = 0, size: Int = 20, sort: String = "importedAt,desc") async throws -> BookListResponse {
        try await client.get("api/books?page=\(page)&size=\(size)&sort=\(sort)")
    }

    func searchBooks(query: String, page: Int = 0, size: Int = 20) async throws -> BookListResponse {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        return try await client.get("api/books/search?keyword=\(encoded)&page=\(page)&size=\(size)")
    }

    func getFavorites(page: Int = 0, size: Int = 20) async throws -> BookListResponse {
        try await client.get("api/books/favorites?page=\(page)&size=\(size)")
    }

    func getBookDetail(id: Int64) async throws -> BookDto {
        try await client.get("api/books/\(id)")
    }

    func getProcessedContent(id: Int64) async throws -> String {
        let data = try await client.requestData("api/books/\(id)/content-processed")
        return String(data: data, encoding: .utf8) ?? ""
    }

    func saveReadingProgress(bookId: Int64, progress: ReadingProgressRequest) async throws {
        let _: EmptyResponse = try await client.post("api/books/\(bookId)/reading-progress", body: progress)
    }

    func updateReadingTime(bookId: Int64, seconds: Int) async throws {
        let _: EmptyResponse = try await client.put("api/books/\(bookId)/reading-time", body: ReadingTimeRequest(readingTimeSeconds: seconds))
    }
}
