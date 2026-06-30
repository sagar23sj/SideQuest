import SwiftUI
import SideQuestKit

// MARK: - Root navigation: loading → board (task 18.1)
//
// The app entry composition: a brief launch ``LoadingView`` (the deterministic
// thought of the day, Req 12) gives way to the main interface once the
// environment's launch work has run. The main interface is a tab layout that
// exposes the capabilities at parity with the Android client: the action board
// + completion counter (Req 8), bucket management (Req 9.1–9.5), and the
// notification settings (Req 7.12, 7.15). Per-item action planning, timeframe,
// and reminders are reachable from a board row's detail screen.
//
// All screens draw their repositories and services from the shared
// ``AppEnvironment``, so every view model binds to the same repository
// `ValueObservation` streams (Req 5.2) and the same sync / notification / auth
// services.

/// The app root: shows the loading experience, then transitions to the main
/// tabbed interface once launch work completes.
struct RootView: View {

    @EnvironmentObject private var environment: AppEnvironment

    /// Whether the brief launch experience is still showing. Flipped once the
    /// environment's launch work finishes (or the minimum display time elapses).
    @State private var isLoading = true

    var body: some View {
        ZStack {
            if isLoading {
                LoadingView()
                    .transition(.opacity)
            } else {
                MainTabView()
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: isLoading)
        .task {
            // Run launch wiring — register/observe sync triggers, reschedule
            // pending notifications (Req 7.11), and kick off the first sync pass
            // (Req 6.6) — concurrently with a minimum on-screen time for the
            // thought of the day so the transition never flashes.
            async let launch: Void = environment.start()
            async let minimumDisplay: Void = Self.sleep(seconds: 0.8)
            _ = await (launch, minimumDisplay)
            isLoading = false
        }
    }

    /// Non-throwing sleep helper for the minimum loading-screen display time.
    private static func sleep(seconds: Double) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }
}

/// The main tabbed interface presented after launch.
struct MainTabView: View {

    @EnvironmentObject private var environment: AppEnvironment

    var body: some View {
        TabView {
            BoardView(
                itemRepository: environment.itemRepository,
                bucketRepository: environment.bucketRepository,
                notificationService: environment.notificationService,
                detail: { item in
                    AnyView(
                        ItemDetailView(
                            item: item,
                            itemRepository: environment.itemRepository,
                            planRepository: environment.planRepository,
                            notificationService: environment.notificationService
                        )
                    )
                }
            )
            .tabItem {
                Label("Board", systemImage: "square.stack.3d.up")
            }

            BucketsView(
                bucketRepository: environment.bucketRepository,
                bucketManagement: environment.bucketManagement,
                authService: environment.authService
            )
            .tabItem {
                Label("Buckets", systemImage: "tray.full")
            }

            NotificationSettingsView(
                notificationService: environment.notificationService,
                preferences: environment.notificationPreferences,
                itemRepository: environment.itemRepository
            )
            .tabItem {
                Label("Reminders", systemImage: "bell")
            }
        }
        // Surface a non-fatal store-setup warning (e.g. App Group unavailable).
        .alert(
            "Storage warning",
            isPresented: Binding(
                get: { environment.storeWarning != nil },
                set: { _ in }
            ),
            actions: { Button("OK", role: .cancel) {} },
            message: {
                if let warning = environment.storeWarning {
                    Text(warning)
                }
            }
        )
    }
}
