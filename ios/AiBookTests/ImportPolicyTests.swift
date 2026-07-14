import XCTest
@testable import AiBook

final class ImportPolicyTests: XCTestCase {
    func testSupportedFormatsMatchReaderImportContract() {
        XCTAssertTrue(ImportPolicy.isSupported(fileName: "manual.pdf"))
        XCTAssertTrue(ImportPolicy.isSupported(fileName: "novel.epub"))
        XCTAssertFalse(ImportPolicy.isSupported(fileName: "manual.docx"))
    }

    func testNormalizedTitleRemovesExtensionAndWhitespace() {
        XCTAssertEqual(ImportPolicy.normalizedTitle(from: "  汗牛充栋.epub"), "汗牛充栋")
    }

    func testNormalizedTitleFallsBackToOriginalFileName() {
        XCTAssertEqual(ImportPolicy.normalizedTitle(from: ".epub"), ".epub")
    }
}
