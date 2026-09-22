package com.notchhud.island.service

import com.notchhud.island.core.Agent
import com.notchhud.island.core.AgentState
import com.notchhud.island.core.IslandState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Link to the macOS Notch HUD. The Mac runs the server (it is the device that
 * actually watches Claude Code / Codex); the phone connects out to it over the LAN,
 * which keeps the phone free of any inbound listener.
 *
 * Wire format, one JSON object per frame:
 *   { "type": "agent",    "id", "name", "project", "task", "state", "ask?", "command?" }
 *   { "type": "approval", "id", "decision": "approve" | "deny" }
 *   { "type": "settings", ... }          reserved: settings mirroring
 *   { "type": "hello",    "device": "android" }
 */
class CompanionClient(private val url: String) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private val stopped = AtomicBoolean(false)
    private var backoffMs = 1_000L

    @Serializable
    private data class AgentFrame(
        val type: String = "agent",
        val id: String,
        val name: String = "Agent",
        val project: String = "",
        val task: String = "",
        val state: String = "running",
        val ask: String? = null,
        val command: String? = null,
    )

    @Serializable
    private data class ApprovalFrame(val type: String = "approval", val id: String, val decision: String)

    @Serializable
    private data class HelloFrame(val type: String = "hello", val device: String = "android")

    fun connect() {
        if (url.isBlank() || stopped.get()) return
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                backoffMs = 1_000L
                webSocket.send(json.encodeToString(HelloFrame()))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val frame = json.decodeFromString<AgentFrame>(text)
                    if (frame.type != "agent") return@runCatching
                    if (frame.state == "gone") {
                        IslandState.removeAgent(frame.id)
                        return@runCatching
                    }
                    IslandState.upsertAgent(
                        Agent(
                            id = frame.id,
                            name = frame.name,
                            project = frame.project,
                            task = frame.task,
                            state = runCatching { AgentState.valueOf(frame.state.uppercase()) }
                                .getOrDefault(AgentState.RUNNING),
                            ask = frame.ask,
                            command = frame.command,
                        )
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = reconnect()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = reconnect()
        })
    }

    /** Approve/Deny travels back to the Mac, which is what actually unblocks the agent. */
    fun sendDecision(agentId: String, approve: Boolean) {
        socket?.send(json.encodeToString(ApprovalFrame(id = agentId, decision = if (approve) "approve" else "deny")))
    }

    fun close() {
        stopped.set(true)
        socket?.close(1000, "stopping")
        socket = null
    }

    private fun reconnect() {
        if (stopped.get()) return
        socket = null
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        Thread {
            Thread.sleep(delay)
            if (!stopped.get()) connect()
        }.apply { isDaemon = true }.start()
    }
}
