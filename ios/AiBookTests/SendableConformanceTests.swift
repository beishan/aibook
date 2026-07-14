import XCTest
@testable import AiBook

final class SendableConformanceTests: XCTestCase {
    func testCrossActorModelsAreSendable() {
        requireSendable(LocalBook.self)
        requireSendable(ReadingProgress.self)
        requireSendable(ReaderSettings.self)
        requireSendable(ShelfFolder.self)
        requireSendable(OpdsFeed.self)
        requireSendable(OpdsEntry.self)
        requireSendable(OpdsLink.self)
        requireSendable(OpdsConnection.self)
        requireSendable(StoreBook.self)
        requireSendable(StoreCatalogFilter.self)
        requireSendable(ReaderChapter.self)
        requireSendable(ReaderPage.self)
        requireSendable(ReaderBookmark.self)
        requireSendable(ReaderHighlight.self)
    }

    private func requireSendable<T: Sendable>(_: T.Type) {}
}
