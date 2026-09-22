// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "NotchHUD",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "NotchHUD", targets: ["NotchHUD"]),
        .executable(name: "notchhud", targets: ["notchhud"]),
    ],
    targets: [
        // The agent app itself. Built as a plain executable and wrapped into a
        // .app bundle by Scripts/make_app.sh — no Xcode project to keep in sync.
        .executableTarget(
            name: "NotchHUD",
            path: "Sources/NotchHUD",
            swiftSettings: [.unsafeFlags(["-parse-as-library"])]
        ),
        // Tiny CLI that pushes agent events into the running app.
        .executableTarget(name: "notchhud", path: "Sources/notchhud"),
    ]
)
