import AppKit
import Foundation

/// Now playing.
///
/// macOS has never had a public API for "what is any app playing". The private
/// MediaRemote framework was the universal answer, and Apple restricted it to
/// entitled processes in macOS 15.4. So this does the honest thing: it tries
/// MediaRemote via dlopen, and if the symbols are missing or return nothing it
/// falls back to scripting the two players that expose their state (Music and
/// Spotify). When neither works the Media tab says so rather than pretending.
@MainActor
final class MediaService {

    static let shared = MediaService()

    private var task: Task<Void, Never>?
    private var lastTrackKey: String?

    private typealias GetNowPlayingInfo = @convention(c) (DispatchQueue, @escaping ([String: Any]) -> Void) -> Void
    private typealias SendCommand = @convention(c) (Int, [String: Any]?) -> Bool

    private var getNowPlayingInfo: GetNowPlayingInfo?
    private var sendCommand: SendCommand?

    private init() { loadMediaRemote() }

    func start() {
        task?.cancel()
        task = Task { [weak self] in
            while !Task.isCancelled {
                await self?.refresh()
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }

    private func loadMediaRemote() {
        let path = "/System/Library/PrivateFrameworks/MediaRemote.framework/MediaRemote"
        guard let handle = dlopen(path, RTLD_NOW) else { return }
        if let symbol = dlsym(handle, "MRMediaRemoteGetNowPlayingInfo") {
            getNowPlayingInfo = unsafeBitCast(symbol, to: GetNowPlayingInfo.self)
        }
        if let symbol = dlsym(handle, "MRMediaRemoteSendCommand") {
            sendCommand = unsafeBitCast(symbol, to: SendCommand.self)
        }
    }

    private func refresh() async {
        guard Settings.shared.isEnabled(.media) else {
            HUDState.shared.media = nil
            return
        }

        if let info = await mediaRemoteInfo(), let playing = info { publish(playing); return }
        if let scripted = scriptedInfo() { publish(scripted); return }
        HUDState.shared.media = nil
    }

    private func mediaRemoteInfo() async -> NowPlaying?? {
        guard let getNowPlayingInfo else { return nil }
        return await withCheckedContinuation { continuation in
            var resumed = false
            getNowPlayingInfo(DispatchQueue.main) { info in
                guard !resumed else { return }
                resumed = true
                guard let title = info["kMRMediaRemoteNowPlayingInfoTitle"] as? String, !title.isEmpty else {
                    continuation.resume(returning: .some(nil))
                    return
                }
                let duration = info["kMRMediaRemoteNowPlayingInfoDuration"] as? Double ?? 0
                let elapsed = info["kMRMediaRemoteNowPlayingInfoElapsedTime"] as? Double ?? 0
                let rate = info["kMRMediaRemoteNowPlayingInfoPlaybackRate"] as? Double ?? 0
                var artwork: NSImage?
                if let data = info["kMRMediaRemoteNowPlayingInfoArtworkData"] as? Data {
                    artwork = NSImage(data: data)
                }
                continuation.resume(
                    returning: NowPlaying(
                        title: title,
                        artist: info["kMRMediaRemoteNowPlayingInfoArtist"] as? String ?? "",
                        app: "",
                        playing: rate > 0,
                        progress: duration > 0 ? elapsed / duration : 0,
                        artwork: artwork
                    )
                )
            }
        }
    }

    /// AppleScript fallback for the two players that publish their own state.
    private func scriptedInfo() -> NowPlaying? {
        let running = Set(NSWorkspace.shared.runningApplications.compactMap(\.bundleIdentifier))

        for (bundleID, appName) in [("com.spotify.client", "Spotify"), ("com.apple.Music", "Music")] {
            guard running.contains(bundleID) else { continue }
            let script = """
            tell application "\(appName)"
              if player state is playing or player state is paused then
                set t to name of current track
                set a to artist of current track
                set p to player position
                set d to duration of current track
                set s to (player state as text)
                return t & "\u{1}" & a & "\u{1}" & p & "\u{1}" & d & "\u{1}" & s
              end if
            end tell
            """
            guard let output = runAppleScript(script) else { continue }
            let parts = output.components(separatedBy: "\u{1}")
            guard parts.count == 5 else { continue }

            // Spotify reports duration in ms, Music in seconds.
            let position = Double(parts[2]) ?? 0
            var duration = Double(parts[3]) ?? 0
            if appName == "Spotify" { duration /= 1000 }

            return NowPlaying(
                title: parts[0],
                artist: parts[1],
                app: appName,
                playing: parts[4].contains("playing"),
                progress: duration > 0 ? position / duration : 0,
                artwork: nil
            )
        }
        return nil
    }

    private func runAppleScript(_ source: String) -> String? {
        var error: NSDictionary?
        let script = NSAppleScript(source: source)
        let result = script?.executeAndReturnError(&error)
        if error != nil { return nil }
        return result?.stringValue
    }

    private func publish(_ media: NowPlaying) {
        HUDState.shared.media = media

        let key = "\(media.title)|\(media.artist)"
        guard media.playing, key != lastTrackKey else { return }
        lastTrackKey = key
        HUDState.shared.show(
            Toast(keys: ["♫"], label: "\(media.title) — \(media.artist)", meter: nil,
                  state: nil, stateColor: nil, duration: 1.4),
            module: .media
        )
    }

    // Transport. MediaRemote command ids: 0 play, 1 pause, 2 toggle, 4 next, 5 previous.
    func playPause() { _ = sendCommand?(2, nil) }
    func next() { _ = sendCommand?(4, nil) }
    func previous() { _ = sendCommand?(5, nil) }
}
