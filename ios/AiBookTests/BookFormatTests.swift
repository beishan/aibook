import XCTest
@testable import AiBook

final class BookFormatTests: XCTestCase {
    func testRecognizesSupportedExtensionsCaseInsensitively() {
        XCTAssertEqual(BookFormat.fromFileName("Novel.EPUB"), .epub)
        XCTAssertEqual(BookFormat.fromFileName("notes.md"), .markdown)
        XCTAssertEqual(BookFormat.fromFileName("page.HTM"), .htm)
    }

    func testRejectsUnsupportedOrMissingExtensions() {
        XCTAssertNil(BookFormat.fromFileName("archive.zip"))
        XCTAssertNil(BookFormat.fromFileName("README"))
    }
}
