// swift-tools-version: 5.10
import PackageDescription

// Note on naming: the CLI product cannot be called "notchhud". SwiftPM derives
// per-product build directories from the product name, and on a case-insensitive
// filesystem "NotchHUD-p.build" and "notchhud-p.build" are the same directory, so
// the two link steps clobber each other's object files. The binary is renamed to
// `notchhud` when Scripts/make_app.sh puts it in the bundle.
let package = Package(
    name: "NotchHUD",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "NotchHUD", targets: ["NotchHUD"]),
        .executable(name: "notchhud-cli", targets: ["NotchHUDCLI"]),
    ],
    targets: [
        // The agent app. A plain executable wrapped into a .app by make_app.sh —
        // there is no Xcode project to keep in sync.
        .executableTarget(name: "NotchHUD", path: "Sources/NotchHUD"),
        // Tiny CLI that pushes agent events into the running app.
        .executableTarget(name: "NotchHUDCLI", path: "Sources/NotchHUDCLI"),
    ]
)
