import AppKit
import ApplicationServices
import Carbon.HIToolbox
import Foundation

/// Instant feedback for keyboard shortcuts and system keys.
///
/// A CGEventTap needs Accessibility permission. Without it the tap simply never
/// installs and the rest of the HUD carries on — this is the one module that
/// silently degrades rather than failing loudly, because the permission is the
/// user's to give and nagging about it every launch would be worse.
@MainActor
final class ShortcutService {

    static let shared = ShortcutService()

    private var eventTap: CFMachPort?
    private var runLoopSource: CFRunLoopSource?
    private var lastCapsLock = false

    private init() {}

    var hasAccessibilityPermission: Bool {
        AXIsProcessTrusted()
    }

    func requestAccessibilityPermission() {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        _ = AXIsProcessTrustedWithOptions(options)
    }

    func start() {
        guard Settings.shared.isEnabled(.shortcuts) || Settings.shared.isEnabled(.systemHUD) else { return }
        guard hasAccessibilityPermission, eventTap == nil else { return }

        let mask = (1 << CGEventType.keyDown.rawValue) | (1 << CGEventType.flagsChanged.rawValue)

        let callback: CGEventTapCallBack = { _, type, event, _ in
            Task { @MainActor in ShortcutService.shared.handle(type: type, event: event.copy()) }
            return Unmanaged.passUnretained(event)
        }

        guard let tap = CGEvent.tapCreate(
            tap: .cgSessionEventTap,
            place: .headInsertEventTap,
            options: .listenOnly,          // observe only; never swallow the user's keys
            eventsOfInterest: CGEventMask(mask),
            callback: callback,
            userInfo: nil
        ) else { return }

        let source = CFMachPortCreateRunLoopSource(kCFAllocatorDefault, tap, 0)
        CFRunLoopAddSource(CFRunLoopGetCurrent(), source, .commonModes)
        CGEvent.tapEnable(tap: tap, enable: true)

        eventTap = tap
        runLoopSource = source
    }

    func stop() {
        if let eventTap { CGEvent.tapEnable(tap: eventTap, enable: false) }
        if let runLoopSource { CFRunLoopRemoveSource(CFRunLoopGetCurrent(), runLoopSource, .commonModes) }
        eventTap = nil
        runLoopSource = nil
    }

    private func handle(type: CGEventType, event: CGEvent?) {
        guard let event else { return }
        let theme = Settings.shared.theme

        switch type {
        case .flagsChanged:
            let caps = event.flags.contains(.maskAlphaShift)
            guard caps != lastCapsLock else { return }
            lastCapsLock = caps
            HUDState.shared.show(
                Toast(keys: ["AB"], label: "Caps Lock", meter: nil,
                      state: caps ? "On" : "Off",
                      stateColor: caps ? theme.done : theme.error,
                      duration: 1.4),
                module: .shortcuts
            )

        case .keyDown:
            let keyCode = Int(event.getIntegerValueField(.keyboardEventKeycode))
            let flags = event.flags
            guard flags.contains(.maskCommand) else { return }

            let shift = flags.contains(.maskShift)
            let toast: Toast? = switch keyCode {
            case kVK_ANSI_C: Toast(keys: ["⌘C"], label: "Copied", meter: nil, state: nil, stateColor: nil, duration: 1.4)
            case kVK_ANSI_V: Toast(keys: ["⌘V"], label: "Pasted", meter: nil, state: nil, stateColor: nil, duration: 1.4)
            case kVK_ANSI_3 where shift: Toast(keys: ["⌘⇧3"], label: "Screenshot saved", meter: nil, state: nil, stateColor: nil, duration: 1.4)
            case kVK_ANSI_4 where shift: Toast(keys: ["⌘⇧4"], label: "Screenshot saved", meter: nil, state: nil, stateColor: nil, duration: 1.4)
            default: nil
            }

            if let toast { HUDState.shared.show(toast, module: .shortcuts) }

        default:
            break
        }
    }
}
