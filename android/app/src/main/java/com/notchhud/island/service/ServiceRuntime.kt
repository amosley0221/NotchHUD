package com.notchhud.island.service

import com.notchhud.island.core.SettingsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The current settings snapshot and derived Quiet state.
 *
 * This is a StateFlow rather than a plain field because the island is Compose:
 * a field it reads directly is invisible to the snapshot system, so changing the
 * theme or the island size in Settings would not redraw anything until some
 * unrelated state happened to recompose. [current] stays available for the
 * callbacks that have no composition or coroutine scope of their own — the
 * notification listener and the broadcast receivers.
 *
 * The overlay service owns the writes.
 */
object ServiceRuntime {

    private val _settings = MutableStateFlow(SettingsSnapshot())
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    private val _quietActive = MutableStateFlow(false)
    val quietActive: StateFlow<Boolean> = _quietActive.asStateFlow()

    /** Snapshot read for non-reactive callers. */
    val current: SettingsSnapshot get() = _settings.value

    /** Whether Quiet is on right now, for non-reactive callers. */
    val isQuiet: Boolean get() = _quietActive.value

    fun update(snapshot: SettingsSnapshot) { _settings.value = snapshot }
    fun setQuiet(active: Boolean) { _quietActive.value = active }
}
