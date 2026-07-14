import XCTest
@testable import AiBook

final class PersistenceBootstrapTests: XCTestCase {
    @MainActor
    func testSuccessfulPersistentContainerHasNoStartupIssue() throws {
        let result = try PersistenceBootstrap.bootstrap(
            persistent: { try AiBookContainer.createInMemory() },
            inMemory: { throw StubError.unexpectedFallback }
        )

        XCTAssertNil(result.issue)
    }

    @MainActor
    func testPersistentFailureFallsBackToInMemoryContainer() throws {
        let result = try PersistenceBootstrap.bootstrap(
            persistent: { throw StubError.persistentStoreUnavailable },
            inMemory: { try AiBookContainer.createInMemory() }
        )

        XCTAssertNotNil(result.issue)
        XCTAssertTrue(result.issue?.message.contains("临时内存数据库") == true)
    }

    @MainActor
    func testBothContainerFailuresPropagateFallbackError() {
        XCTAssertThrowsError(try PersistenceBootstrap.bootstrap(
            persistent: { throw StubError.persistentStoreUnavailable },
            inMemory: { throw StubError.inMemoryStoreUnavailable }
        )) { error in
            XCTAssertEqual(error as? StubError, .inMemoryStoreUnavailable)
        }
    }

    private enum StubError: Error, Equatable {
        case persistentStoreUnavailable
        case inMemoryStoreUnavailable
        case unexpectedFallback
    }
}
