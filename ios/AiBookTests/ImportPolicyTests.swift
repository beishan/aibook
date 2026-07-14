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

    func testBookImportPreparerReadsSupportedFileAndComputesHash() async throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("sample-\(UUID().uuidString).TXT")
        let expectedData = Data("hello".utf8)
        try expectedData.write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }

        let result = await BookImportPreparer().prepare(
            url: url,
            fileName: "sample.TXT"
        )

        guard case .prepared(let prepared) = result else {
            return XCTFail("Expected a prepared import, got \(result)")
        }
        XCTAssertEqual(prepared.format, .txt)
        XCTAssertEqual(prepared.normalizedTitle, "sample")
        XCTAssertEqual(prepared.data, expectedData)
        XCTAssertEqual(prepared.sha256.count, 64)
    }

    func testBookImportPreparerRejectsUnsupportedExtensionBeforeReading() async {
        let result = await BookImportPreparer().prepare(
            url: URL(fileURLWithPath: "/missing/sample.docx"),
            fileName: "sample.docx"
        )

        guard case .unsupported = result else {
            return XCTFail("Expected unsupported, got \(result)")
        }
    }
}
