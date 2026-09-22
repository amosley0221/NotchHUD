package com.notchhud.island.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notchhud.island.core.CallInfo
import com.notchhud.island.core.InboxItem
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.Modules

/**
 * Mirrors other apps' notifications into the island. Android never lets a third
 * party suppress the original, so the user silences those apps themselves — this
 * only reads and re-presents them, and can invoke their own actions.
 */
class IslandNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: IslandNotificationListener? = null
            private set

        private val MESSAGING = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.whatsapp",
            "org.thoughtcrime.securesms",
            "com.google.android.talk",
        )
        private val EMAIL = setOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.fsck.k9",
        )
        private const val TEAMS = "com.microsoft.teams"
    }

    override fun onListenerConnected() {
        instance = this
        // Seed the inbox from whatever is already on screen so a restart is not a blank slate.
        runCatching { activeNotifications?.forEach { handle(it, initial = true) } }
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, initial = false)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IslandState.removeInbox(sbn.key)
        if (IslandState.call.value != null && sbn.packageName == callPackage) {
            IslandState.setCall(null)
            callPackage = null
        }
    }

    private var callPackage: String? = null

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val kind = when {
            n.category == Notification.CATEGORY_CALL -> InboxItem.Kind.CALL
            sbn.packageName == TEAMS -> InboxItem.Kind.TEAMS
            sbn.packageName in EMAIL -> InboxItem.Kind.EMAIL
            sbn.packageName in MESSAGING -> InboxItem.Kind.MESSAGE
            else -> InboxItem.Kind.OTHER
        }

        val item = InboxItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel(sbn.packageName),
            title = title,
            body = body,
            postedAt = sbn.postTime,
            kind = kind,
        )
        IslandState.addInbox(item)

        if (initial) return

        if (kind == InboxItem.Kind.CALL && n.actions?.isNotEmpty() == true) {
            callPackage = sbn.packageName
            IslandState.setCall(
                CallInfo(
                    name = title.ifBlank { item.appLabel },
                    source = "${item.appLabel} · incoming call",
                    packageName = sbn.packageName,
                    video = body.contains("video", ignoreCase = true),
                )
            )
            return
        }

        NotificationRouter.route(this, item)
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    /** Fires one of the original notification's own actions (Answer, Decline, Reply, Join). */
    fun invokeAction(key: String, predicate: (String) -> Boolean): Boolean {
        val sbn = runCatching { getActiveNotifications(arrayOf(key)).firstOrNull() }.getOrNull() ?: return false
        val action = sbn.notification.actions?.firstOrNull { predicate(it.title?.toString().orEmpty()) } ?: return false
        return runCatching { action.actionIntent.send(); true }.getOrDefault(false)
    }

    fun dismiss(key: String) {
        runCatching { cancelNotification(key) }
        IslandState.removeInbox(key)
    }

    /** Which module a mirrored notification belongs to, for module + Quiet gating. */
    fun moduleFor(kind: InboxItem.Kind): String = when (kind) {
        InboxItem.Kind.TEAMS -> Modules.TEAMS
        InboxItem.Kind.EMAIL -> Modules.EMAIL
        InboxItem.Kind.MESSAGE -> Modules.MESSAGES
        InboxItem.Kind.CALL -> Modules.CALLS
        InboxItem.Kind.OTHER -> Modules.SYSTEM
    }
}
