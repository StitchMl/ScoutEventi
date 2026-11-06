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

        // WebiComp è online: resta visibile anche quando la regione non coincide.
        XCTAssertTrue(app.staticTexts["Campo RS di prova"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Evento Lazio in presenza"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Cantiere EG simulator"].exists)
    }

    func testSearchMatchesEventTypeAndResetClearsFilters() throws {
        let app = launchApp()
        let searchField = app.searchFields["Cerca eventi, regioni, stato"].firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))

        searchField.tap()
        searchField.typeText("MF-PT")
        XCTAssertTrue(app.staticTexts["Evento Capi smoke test"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Cantiere EG simulator"].exists)

        let clearButton = app.buttons["Clear text"].firstMatch
        XCTAssertTrue(clearButton.waitForExistence(timeout: 5))
        clearButton.tap()
        XCTAssertTrue(app.staticTexts["Cantiere EG simulator"].waitForExistence(timeout: 5))
    }

    func testAccessibilityAudit() throws {
        guard #available(iOS 17.0, *) else { return }
        let app = launchApp()
        XCTAssertTrue(app.navigationBars["ScoutEventi"].waitForExistence(timeout: 10))
        try app.performAccessibilityAudit(for: [.sufficientElementDescription, .hitRegion])
    }

    func testExactTypeAndUnitFilters() throws {
        let app = launchApp()

        app.buttons["type-filter-menu"].firstMatch.tap()
        app.buttons["MF-AM E/G"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Cantiere EG simulator"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Campo RS di prova"].exists)

        app.buttons["unit-filter-menu"].firstMatch.tap()
        app.buttons["E/G"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Cantiere EG simulator"].waitForExistence(timeout: 5))
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
