package com.notchhud.island.service

import com.notchhud.island.core.SettingsSnapshot

/**
 * The last settings snapshot and derived Quiet state, readable from callbacks that
 * have no coroutine scope of their own (notification listener, broadcast receivers).
 * The overlay service owns the writes.
 */
object ServiceRuntime {
    @Volatile var settings: SettingsSnapshot? = null
    @Volatile var quietActive: Boolean = false
}
