import Foundation
import Network

/// `notchhud` — pushes agent events into the running Notch HUD app.
///
///   notchhud emit '{"id":"claude","name":"Claude Code","state":"running","task":"Refactoring"}'
///   notchhud running    claude "Claude Code" "Refactoring auth"
///   notchhud permission claude "Run the migration?" "npm run migrate"
///   notchhud done       claude
///   notchhud error      claude "Tests failed"
///   notchhud gone       claude
///
/// One line of JSON to 127.0.0.1:8787. Nothing is installed, nothing is polled.
@main
enum NotchHUDCLI {

    static let port: UInt16 = UInt16(ProcessInfo.processInfo.environment["NOTCHHUD_PORT"] ?? "") ?? 8787

    static func main() {
        let arguments = Array(CommandLine.arguments.dropFirst())
        guard let command = arguments.first else { usage() }
        let rest = Array(arguments.dropFirst())

        switch command {
        case "settings":
            open("notchhud://settings")

        case "emit":
            guard let payload = rest.first else { usage() }
            send(payload)

        case "running", "done", "gone":
            guard let id = rest.first else { usage() }
            var fields = ["\"id\":\(escape(id))", "\"state\":\"\(command)\""]
            if rest.count > 1 { fields.append("\"name\":\(escape(rest[1]))") }
            if rest.count > 2 { fields.append("\"task\":\(escape(rest[2]))") }
            send("{\(fields.joined(separator: ","))}")

        case "error":
            guard let id = rest.first else { usage() }
            var fields = ["\"id\":\(escape(id))", "\"state\":\"error\""]
            fields.append("\"task\":\(escape(rest.count > 1 ? rest[1] : "Failed"))")
            if rest.count > 2 { fields.append("\"name\":\(escape(rest[2]))") }
            send("{\(fields.joined(separator: ","))}")

        case "permission":
            guard rest.count >= 2 else { usage() }
            var fields = [
                "\"id\":\(escape(rest[0]))",
                "\"state\":\"permission\"",
                "\"ask\":\(escape(rest[1]))",
            ]
            if rest.count > 2 { fields.append("\"command\":\(escape(rest[2]))") }
            if rest.count > 3 { fields.append("\"name\":\(escape(rest[3]))") }
            send("{\(fields.joined(separator: ","))}")

        default:
            usage()
        }
    }

    static func usage() -> Never {
        FileHandle.standardError.write(Data("""
        notchhud — push agent state into the Notch HUD

          notchhud settings
          notchhud emit '<json>'
          notchhud running    <id> [name] [task]
          notchhud permission <id> <ask> [command] [name]
          notchhud done       <id> [name]
          notchhud error      <id> [message] [name]
          notchhud gone       <id>

        The HUD must be running. Override the port with NOTCHHUD_PORT.

        """.utf8))
        exit(2)
    }

    /// Hands a URL to LaunchServices, which routes it to the running app.
    static func open(_ urlString: String) {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        process.arguments = [urlString]
        try? process.run()
        process.waitUntilExit()
    }

    static func send(_ json: String) {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { exit(1) }
        let connection = NWConnection(host: "127.0.0.1", port: nwPort, using: .tcp)
        let semaphore = DispatchSemaphore(value: 0)
        let failure = Failure()

        connection.stateUpdateHandler = { state in
            switch state {
            case .ready:
                connection.send(
                    content: Data((json + "\n").utf8),
                    completion: .contentProcessed { _ in
                        connection.cancel()
                        semaphore.signal()
                    }
                )
            case .failed, .cancelled:
                failure.set()
                semaphore.signal()
            default:
                break
            }
        }

        connection.start(queue: .global())
        if semaphore.wait(timeout: .now() + 3) == .timedOut { failure.set() }

        if failure.value {
            FileHandle.standardError.write(
                Data("notchhud: could not reach the HUD on 127.0.0.1:\(port)\n".utf8)
            )
            exit(1)
        }
    }

    /// The state handler runs on another queue, so the flag needs a lock rather
    /// than a captured `var`.
    private final class Failure: @unchecked Sendable {
        private let lock = NSLock()
        private var flag = false

        var value: Bool {
            lock.lock()
            defer { lock.unlock() }
            return flag
        }

        func set() {
            lock.lock()
            defer { lock.unlock() }
            flag = true
        }
    }

    /// Quotes a value the way JSON wants it, without hand-rolling the escaping.
    static func escape(_ value: String) -> String {
        let data = try? JSONSerialization.data(withJSONObject: [value], options: [])
        guard let data, let string = String(data: data, encoding: .utf8) else { return "\"\"" }
        return String(string.dropFirst().dropLast())
    }
}
