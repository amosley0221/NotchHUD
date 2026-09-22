package com.notchhud.island

import android.app.Application
import com.notchhud.island.core.CrashReporter
import com.notchhud.island.core.SettingsRepository
import com.notchhud.island.service.ServiceRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IslandApp : Application() {

    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // First thing, so a crash anywhere else in startup is recorded.
        CrashReporter.install(this)
        settings = SettingsRepository(this)
        // Seed ServiceRuntime early: the notification listener can start before the
        // overlay service does, and it needs the module/Quiet rules to gate on.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            ServiceRuntime.update(settings.flow.first())
        }
    }
}
