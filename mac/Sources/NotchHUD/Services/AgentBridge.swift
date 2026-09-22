import Foundation
import Network

/// Two listeners:
///
///  - **Agent port (8787, line-delimited JSON)** — what `notchhud emit` and any
///    Claude Code / Codex hook writes to. One JSON object per line.
///  - **Companion port (8788, WebSocket)** — what the Android app connects to.
///    Agent events are broadcast to it; approvals come back the other way.
///
/// The Mac is the server because it is the machine that actually watches the
/// agents; the phone only ever connects out, so it needs no inbound listener.
@MainActor
final class AgentBridge {

    static let shared = AgentBridge()

    private var agentListener: NWListener?
    private var companionListener: NWListener?
    private var companions: [NWConnection] = []

    private init() {}

    struct AgentEvent: Codable {
        var type: String?
        var id: String
        var name: String?
        var project: String?
        var task: String?
        var state: String?
        var ask: String?
        var command: String?
    }

    struct ApprovalEvent: Codable {
        var type: String
        var id: String
        var decision: String
    }

    func start() {
        startAgentListener(port: UInt16(Settings.shared.agentPort))
        startCompanionListener(port: UInt16(Settings.shared.companionPort))
    }

    // MARK: - Agent port

    private func startAgentListener(port: UInt16) {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { return }
        let params = NWParameters.tcp
        params.requiredInterfaceType = .loopback   // local tools only

        do {
            let listener = try NWListener(using: params, on: nwPort)
            listener.newConnectionHandler = { [weak self] connection in
                connection.start(queue: .main)
                self?.receiveLines(on: connection, buffer: Data())
            }
            listener.start(queue: .main)
            agentListener = listener
        } catch {
            NSLog("NotchHUD: could not listen on agent port \(port): \(error)")
        }
    }

    private func receiveLines(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            var buffer = buffer
            if let data { buffer.append(data) }

            while let newline = buffer.firstIndex(of: 0x0A) {
                let line = buffer[buffer.startIndex..<newline]
                buffer = buffer[buffer.index(after: newline)...]
                Task { @MainActor in self.handleAgentLine(Data(line)) }
            }

            if isComplete || error != nil {
                connection.cancel()
            } else {
                Task { @MainActor in self.receiveLines(on: connection, buffer: Data(buffer)) }
            }
        }
    }

    func handleAgentLine(_ data: Data) {
        guard let event = try? JSONDecoder().decode(AgentEvent.self, from: data) else { return }
        apply(event)
    }

    func apply(_ event: AgentEvent) {
        if event.state == "gone" {
            HUDState.shared.remove(agentID: event.id)
            broadcast(event)
            return
        }

        let existing = HUDState.shared.agents.first { $0.id == event.id }
        let agent = Agent(
            id: event.id,
            name: event.name ?? existing?.name ?? "Agent",
            project: event.project ?? existing?.project ?? "",
            task: event.task ?? existing?.task ?? "",
            state: AgentState(rawValue: event.state ?? "") ?? existing?.state ?? .running,
            ask: event.ask ?? (event.state == "permission" ? existing?.ask : nil),
            command: event.command ?? (event.state == "permission" ? existing?.command : nil)
        )
        HUDState.shared.upsert(agent)
        broadcast(event)
    }

    /// Accepts `notchhud://agent?id=…&state=running&task=…` so a hook can be a
    /// one-line `open` call with no networking at all.
    func handle(url: URL) {
        guard url.scheme == "notchhud",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let items = components.queryItems,
              let id = items.first(where: { $0.name == "id" })?.value
        else { return }

        func value(_ name: String) -> String? { items.first { $0.name == name }?.value }

        apply(
            AgentEvent(
                type: "agent",
                id: id,
                name: value("name"),
                project: value("project"),
                task: value("task"),
                state: value("state"),
                ask: value("ask"),
                command: value("command")
            )
        )
    }

    // MARK: - Companion port (WebSocket, for the Android app)

    private func startCompanionListener(port: UInt16) {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { return }

        let params = NWParameters.tcp
        let websocket = NWProtocolWebSocket.Options()
        websocket.autoReplyPing = true
        params.defaultProtocolStack.applicationProtocols.insert(websocket, at: 0)

        do {
            let listener = try NWListener(using: params, on: nwPort)
            listener.newConnectionHandler = { [weak self] connection in
                Task { @MainActor in self?.accept(companion: connection) }
            }
            listener.start(queue: .main)
            companionListener = listener
        } catch {
            NSLog("NotchHUD: could not listen on companion port \(port): \(error)")
        }
    }

    private func accept(companion connection: NWConnection) {
        connection.stateUpdateHandler = { [weak self] state in
            guard case .cancelled = state else { return }
            Task { @MainActor in self?.companions.removeAll { $0 === connection } }
        }
        connection.start(queue: .main)
        companions.append(connection)
        receiveCompanion(on: connection)

        // Bring the phone up to date the moment it connects.
        for agent in HUDState.shared.agents {
            broadcast(
                AgentEvent(
                    type: "agent", id: agent.id, name: agent.name, project: agent.project,
                    task: agent.task, state: agent.state.rawValue, ask: agent.ask, command: agent.command
                )
            )
        }
    }

    private func receiveCompanion(on connection: NWConnection) {
        connection.receiveMessage { [weak self] data, _, _, error in
            guard let self else { return }
            if let data, let approval = try? JSONDecoder().decode(ApprovalEvent.self, from: data),
               approval.type == "approval" {
                Task { @MainActor in
                    HUDState.shared.decide(agentID: approval.id, approve: approval.decision == "approve")
                }
            }
            if error == nil {
                Task { @MainActor in self.receiveCompanion(on: connection) }
            } else {
                connection.cancel()
            }
        }
    }

    private func broadcast(_ event: AgentEvent) {
        var event = event
        event.type = "agent"
        guard let data = try? JSONEncoder().encode(event) else { return }
        let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
        let context = NWConnection.ContentContext(identifier: "agent", metadata: [metadata])

        for connection in companions {
            connection.send(content: data, contentContext: context, isComplete: true, completion: .idempotent)
        }
    }

    /// Echoed back so the phone's copy of the agent matches after a Mac-side decision.
    func publishDecision(agentID: String, approve: Bool) {
        guard let agent = HUDState.shared.agents.first(where: { $0.id == agentID }) else { return }
        broadcast(
            AgentEvent(
                type: "agent", id: agent.id, name: agent.name, project: agent.project,
                task: agent.task, state: agent.state.rawValue, ask: nil, command: agent.command
            )
        )
    }

    /// Every address the phone could use, for the Settings window to display.
    func companionURLs() -> [String] {
        var results: [String] = []
        var addresses: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addresses) == 0, let first = addresses else { return results }
        defer { freeifaddrs(addresses) }

        for pointer in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let interface = pointer.pointee
            guard interface.ifa_addr.pointee.sa_family == UInt8(AF_INET) else { continue }
            let name = String(cString: interface.ifa_name)
            guard name.hasPrefix("en") else { continue }

            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            getnameinfo(
                interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST
            )
            let address = String(cString: host)
            if !address.isEmpty {
                results.append("ws://\(address):\(Settings.shared.companionPort)")
            }
        }
        return results
    }
}
