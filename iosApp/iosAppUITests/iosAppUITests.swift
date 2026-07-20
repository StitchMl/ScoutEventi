import XCTest

final class iosAppUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSmokeDataIsVisibleAndSearchFiltersEvents() throws {
        let app = launchApp()

        XCTAssertTrue(app.navigationBars["ScoutEventi"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Campo RS di prova"].waitForExistence(timeout: 5))

        let searchField = app.searchFields["Cerca eventi, regioni, stato"].firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        searchField.tap()
        searchField.typeText("Capi")

        XCTAssertTrue(app.staticTexts["Evento Capi smoke test"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Campo RS di prova"].exists)
    }

    func testRegionFilterShowsOnlySelectedRegion() throws {
        let app = launchApp()

        let regionMenu = regionFilterButton(in: app)
        XCTAssertTrue(regionMenu.waitForExistence(timeout: 5))
        regionMenu.tap()

        let lazioButton = app.buttons["Lazio"].firstMatch
        XCTAssertTrue(lazioButton.waitForExistence(timeout: 5))
        lazioButton.tap()

        XCTAssertTrue(app.staticTexts["Campo RS di prova"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Cantiere EG simulator"].exists)
    }

    private func launchApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--scouteventi-smoke-data", "--ui-testing"]
        app.launch()
        return app
    }

    private func regionFilterButton(in app: XCUIApplication) -> XCUIElement {
        let identified = app.buttons["region-filter-menu"].firstMatch
        if identified.exists {
            return identified
        }
        return app.buttons["Tutte"].firstMatch
    }
}
