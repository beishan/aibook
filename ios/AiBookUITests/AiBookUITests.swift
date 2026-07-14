import XCTest

final class AiBookUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testMainTabsAreAvailableAfterLaunch() {
        let app = XCUIApplication()
        app.launch()

        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.waitForExistence(timeout: 5))

        for identifier in ["tab.shelf", "tab.store", "tab.opds", "tab.settings"] {
            XCTAssertTrue(
                tabBar.buttons[identifier].exists,
                "主入口 \(identifier) 应在应用启动后可用"
            )
        }
    }
}
