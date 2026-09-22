#!/bin/bash
# Assembles NotchHUD.app around the SwiftPM binaries and zips it.
#
# There is no Xcode project on purpose: the app is a plain executable plus an
# Info.plist, and keeping it that way means `swift build` is the whole build.
set -euo pipefail

cd "$(dirname "$0")/.."

CONFIG="${CONFIG:-release}"
VERSION="$(grep -E '^versionName=' ../android/version.properties | cut -d= -f2 | tr -d '[:space:]')"
BIN_DIR="$(swift build -c "$CONFIG" --show-bin-path)"
APP="dist/NotchHUD.app"

rm -rf dist
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

cp "$BIN_DIR/NotchHUD" "$APP/Contents/MacOS/NotchHUD"
cp "$BIN_DIR/notchhud" "$APP/Contents/MacOS/notchhud"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Notch HUD</string>
  <key>CFBundleDisplayName</key><string>Notch HUD</string>
  <key>CFBundleExecutable</key><string>NotchHUD</string>
  <key>CFBundleIdentifier</key><string>com.notchhud.mac</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>${VERSION}</string>
  <key>CFBundleVersion</key><string>${VERSION}</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>

  <!-- Agent app: no Dock icon, no app menu. The HUD is the interface. -->
  <key>LSUIElement</key><true/>

  <key>NSCalendarsFullAccessUsageDescription</key>
  <string>Notch HUD shows your next meeting and its Join link in the HUD.</string>
  <key>NSRemindersFullAccessUsageDescription</key>
  <string>Notch HUD shows reminders due today in the HUD.</string>
  <key>NSLocationWhenInUseUsageDescription</key>
  <string>Notch HUD uses your approximate location for the local forecast.</string>
  <key>NSAppleEventsUsageDescription</key>
  <string>Notch HUD asks Music and Spotify what is playing when the system now-playing API is unavailable.</string>

  <key>CFBundleURLTypes</key>
  <array>
    <dict>
      <key>CFBundleURLName</key><string>Notch HUD agent events</string>
      <key>CFBundleURLSchemes</key><array><string>notchhud</string></array>
    </dict>
  </array>
</dict>
</plist>
PLIST

# Ad-hoc signature so Gatekeeper lets a locally built app run at all. A release
# build for other people would need a Developer ID certificate and notarisation.
codesign --force --deep --sign - "$APP" 2>/dev/null || \
  echo "warning: codesign unavailable; the app will need a right-click → Open on first launch"

mkdir -p dist
( cd dist && zip -qry "NotchHUD-${VERSION}-macos.zip" NotchHUD.app )
echo "built dist/NotchHUD-${VERSION}-macos.zip"
