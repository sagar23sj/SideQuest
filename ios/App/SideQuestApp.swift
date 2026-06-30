import SwiftUI
import SideQuestKit

/// SideQuest_iOS main app entry point (SwiftUI App lifecycle — Req 1.4).
///
/// Composition happens once here: ``AppEnvironment`` assembles the shared store,
/// repositories, and services (task 18.1). Background-task handlers are
/// registered during launch — `init` runs before the app finishes launching, as
/// the OS requires for `BGTaskScheduler` (Req 6.5) — and the environment is
/// handed to the view tree as an `@EnvironmentObject` so every screen binds to
/// the same wired repository `ValueObservation` streams and services.
@main
struct SideQuestApp: App {

    @StateObject private var environment: AppEnvironment

    init() {
        let environment = AppEnvironment()
        // Register BGTaskScheduler handlers while still launching (Req 6.5).
        environment.registerBackgroundTasks()
        _environment = StateObject(wrappedValue: environment)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(environment)
        }
    }
}
