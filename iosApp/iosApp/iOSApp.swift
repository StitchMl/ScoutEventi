import SwiftUI
import UIKit

@main
struct iOSApp: App {
    init() {
        if ProcessInfo.processInfo.arguments.contains("--ui-testing") {
            UIView.setAnimationsEnabled(false)
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
