package com.notchhud.island.service

import androidx.compose.ui.graphics.Color
import com.notchhud.island.core.InboxItem
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.Modules
import com.notchhud.island.core.Tokens
import com.notchhud.island.core.Transient

/**
 * Decides whether a mirrored notification becomes a visible transient or is held.
 *
 * Quiet mode holds instead of dropping: the item is already in the inbox, we just
 * suppress the pill takeover, and the held count is what the user reviews later.
 */
object NotificationRouter {

    fun route(listener: IslandNotificationListener, item: InboxItem) {
        val settings = ServiceRuntime.settings ?: return
        val module = listener.moduleFor(item.kind)

        if (settings.modules[module] == false) return

        val quiet = ServiceRuntime.quietActive
        if (quiet && module !in settings.breakthrough) {
            IslandState.setHeldCount(IslandState.heldCount.value + 1)
            return
        }

        val color = when (item.kind) {
            InboxItem.Kind.TEAMS -> Tokens.Teams
            InboxItem.Kind.MESSAGE -> Tokens.Messages
            InboxItem.Kind.EMAIL -> settings.theme.accent
            else -> Tokens.TextQuaternary
        }

        IslandState.showTransient(
            Transient(
                left = item.title.ifBlank { item.appLabel },
                right = item.body.take(60).ifBlank { null },
                color = color,
                glyph = glyphFor(item.kind),
                durationMs = 2200L,
            )
        )
    }

    fun glyphFor(kind: InboxItem.Kind): String = when (kind) {
        InboxItem.Kind.TEAMS -> "T"
        InboxItem.Kind.EMAIL -> "✉"
        InboxItem.Kind.MESSAGE -> "✱"
        InboxItem.Kind.CALL -> "☎"
        InboxItem.Kind.OTHER -> "•"
    }

    fun systemTransient(left: String, right: String?, color: Color, meter: Float? = null, durationMs: Long = 1400L) {
        val settings = ServiceRuntime.settings ?: return
        if (settings.modules[Modules.SYSTEM] == false) return
        if (ServiceRuntime.quietActive && Modules.SYSTEM !in settings.breakthrough) return
        IslandState.showTransient(Transient(left, right, color, meter = meter, durationMs = durationMs))
    }
}
