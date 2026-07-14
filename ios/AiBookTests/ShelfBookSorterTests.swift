import XCTest
@testable import AiBook

final class ShelfBookSorterTests: XCTestCase {
    private let sharedDate = Date(timeIntervalSince1970: 1_700_000_000)

    func testImportedAtUsesIDAsDeterministicTieBreaker() {
        XCTAssertEqual(sortedIDs(for: .importedAt), ["a", "b"])
    }

    func testRecentReadUsesIDAsDeterministicTieBreaker() {
        XCTAssertEqual(sortedIDs(for: .recentRead), ["a", "b"])
    }

    func testTitleUsesIDAsDeterministicTieBreaker() {
        XCTAssertEqual(sortedIDs(for: .title), ["a", "b"])
    }

    func testFavoriteFirstUsesIDAsDeterministicTieBreaker() {
        XCTAssertEqual(sortedIDs(for: .favoriteFirst), ["a", "b"])
    }

    private func sortedIDs(for option: ShelfSortOption) -> [String] {
        ShelfBookSorter.sort(
            [makeBook(id: "b"), makeBook(id: "a")],
            by: option
        ).map(\.id)
    }

    private func makeBook(id: String) -> LocalBook {
        LocalBook(
            id: id,
            title: "相同书名",
            author: nil,
            format: .epub,
            uri: "/tmp/\(id).epub",
            sha256: nil,
            coverUri: nil,
            folderId: nil,
            status: .unread,
            favorite: false,
            shelved: true,
            visibleInStore: true,
            importedAt: sharedDate,
            lastReadAt: sharedDate,
            progress: ReadingProgress()
        )
    }
}
