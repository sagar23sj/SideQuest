# SideQuest_iOS

Native SwiftUI iPhone client for SideQuest. It consumes the existing Go backend
through the same OpenAPI 3 contract (`backend/api/openapi.yaml`) and delivers
feature parity with the Android client. See the spec at
`.kiro/specs/ios-client/` for full requirements and design.

This directory is the **task 1 scaffold**: the Xcode project definition, the two
targets, the shared App Group wiring, the SPM dependencies, and the shared Swift
module. Feature code is filled in by later tasks.

## Layout

```
ios/
├── project.yml                     # XcodeGen project (both targets, settings, deps)
├── App/                            # Main app target (SwiftUI App lifecycle)
│   ├── SideQuestApp.swift          #   @main entry point
│   ├── RootView.swift              #   placeholder root view
│   ├── Info.plist                  #   iPhone-only declarations
│   ├── SideQuest_iOS.entitlements  #   App Group entitlement
│   └── Assets.xcassets/            #   app icon + accent color
├── ShareExtension/                 # Share Extension target (separate process)
│   ├── ShareViewController.swift   #   principal class, hosts SwiftUI sheet
│   ├── Info.plist                  #   NSExtension share-services + activation rule
│   └── ShareExtension.entitlements #   App Group entitlement (identical id)
└── SideQuestKit/                   # Shared Swift module (SwiftPM package)
    ├── Package.swift               #   GRDB (product) + SwiftCheck (tests)
    ├── Sources/SideQuestKit/
    │   ├── AppGroup.swift          #   single source of truth for the App Group id
    │   ├── Models/                 #   Generated_Models (task 2)
    │   ├── Store/                  #   GRDB local store (task 3)
    │   └── Domain/                 #   portable domain logic (task 4)
    └── Tests/SideQuestKitTests/    #   SwiftCheck property-test harness
```

`SideQuestKit` is linked by **both** the main app and the Share Extension, so
the two processes share identical models, store access, and domain logic.

## Key identifiers

| Thing | Value |
| --- | --- |
| Main app bundle id | `com.sidequest` |
| Share Extension bundle id | `com.sidequest.ShareExtension` |
| **App Group (both targets)** | `group.com.sidequest.shared` |
| Minimum_iOS_Version | iOS 16.0 |
| Device family | iPhone only (`TARGETED_DEVICE_FAMILY = 1`) |

The App Group identifier is declared in three places that must stay in sync:
`SideQuestKit/Sources/SideQuestKit/AppGroup.swift` (code), and both targets'
`.entitlements` files (Req 13.2). The smoke test in task 18.3 asserts they match.

## Dependencies (SPM)

Declared in `SideQuestKit/Package.swift`:

- **GRDB.swift** (`from: 6.0.0`) — SQLite local store in the App Group container.
- **SwiftCheck** (`from: 0.12.0`) — property-based testing (test target only).

Both targets pick up GRDB transitively by linking `SideQuestKit`.

## Building (requires macOS + Xcode)

This project is developed on Windows, so the Xcode project is defined
declaratively in `project.yml` rather than committed as a `.pbxproj`. On a Mac:

```sh
brew install xcodegen          # one-time
cd ios
xcodegen generate             # produces SideQuest_iOS.xcodeproj
open SideQuest_iOS.xcodeproj   # build/run from Xcode
```

Run the shared-module property tests directly with SwiftPM:

```sh
cd ios/SideQuestKit
swift test                     # runs SwiftCheck tests (≥100 iterations each)
```

## Verification status

The following could **not** be verified in the current Windows environment
because they require a macOS/Xcode/Swift toolchain:

- Compiling the Swift sources (`App`, `ShareExtension`, `SideQuestKit`).
- Resolving the GRDB and SwiftCheck packages and running `swift test`.
- Generating the `.xcodeproj` with `xcodegen` and building the targets.

The plist/entitlements/JSON/YAML files are plain-text and were authored to be
syntactically valid. Run the build steps above on macOS to fully verify.
