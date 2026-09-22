package com.notchhud.island.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentCallbacks
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.notchhud.island.R
import com.notchhud.island.core.CrashReporter
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.IslandView
import com.notchhud.island.core.Modules
import com.notchhud.island.core.SettingsRepository
import com.notchhud.island.core.SettingsSnapshot
import com.notchhud.island.core.SportsMode
import com.notchhud.island.core.Tokens
import com.notchhud.island.data.CalendarRepository
import com.notchhud.island.data.EspnRepository
import com.notchhud.island.data.LocationProvider
import com.notchhud.island.data.WeatherRepository
import com.notchhud.island.ui.IslandRoot
import com.notchhud.island.ui.setup.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the overlay window and every data collector. One service, because the
 * island is a single long-lived surface and everything it shows has to outlive
 * any activity.
 */
class IslandOverlayService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "island_overlay"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandOverlayService::class.java))
        }

        @Volatile
        var running: Boolean = false
            private set
    }

    // An overlay must be built from a window context on Android 11+: it is what
    // makes getCurrentWindowMetrics legal and what gives the view the right
    // display and density.
    //
    // Getting one from a Service takes two steps. createWindowContext(type, options)
    // infers the display by calling getDisplay() on the receiver, and a Service is
    // not associated with a display, so that overload throws
    // UnsupportedOperationException. Naming the display first with
    // createDisplayContext() produces a context that *is* associated with one, and
    // createWindowContext on that is valid.
    private val overlayContext: Context by lazy { createOverlayContext() }

    private fun createOverlayContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return this
        return runCatching {
            val displays = getSystemService(DisplayManager::class.java)
            val display = displays.getDisplay(Display.DEFAULT_DISPLAY)
            createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }.getOrElse {
            // Degrade rather than die: the island still draws, and CutoutReader
            // falls back to display metrics when the window metrics are unavailable.
            CrashReporter.record(this, "creating the overlay window context", it)
            this
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var mediaMonitor: MediaMonitor
    private lateinit var haptics: Haptics

    private var host: OverlayViewHost? = null
    private var rootView: View? = null
    private var systemReceiver: SystemEventReceiver? = null
    private var companion: CompanionClient? = null
    private var companionUrl: String = ""

    private var sportsJob: Job? = null
    private var weatherJob: Job? = null
    private var calendarJob: Job? = null

    private val espn = EspnRepository()
    private val weatherRepo = WeatherRepository()

    override fun onCreate() {
        super.onCreate()
        running = true

        // First, and before anything that can throw. Android gives a service
        // started with startForegroundService five seconds to get here; if we crash
        // on the way, the report is a timeout rather than the actual cause.
        startForeground(NOTIFICATION_ID, buildNotification())

        // Everything past this point touches window geometry, OEM-specific
        // behaviour and permissions that can be revoked while we run. A failure
        // here would otherwise kill the process with nothing on screen to explain
        // it, so record it where the setup screen can show it and stop cleanly.
        try {
            windowManager = overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            settingsRepo = SettingsRepository(applicationContext)
            mediaMonitor = MediaMonitor(applicationContext)
            haptics = Haptics(applicationContext)

            IslandState.setCutout(CutoutReader.read(this))
            IslandState.setLocked(
                (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
            )

            addOverlay()
            registerSystemReceiver()
            mediaMonitor.start()
            observeSettings()
            startTransientReaper()
            startLockMonitor()
            overlayContext.registerComponentCallbacks(overlayConfigCallback)
            getSystemService(DisplayManager::class.java)
                .registerDisplayListener(displayListener, null)
        } catch (t: Throwable) {
            CrashReporter.record(this, "island startup", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        running = false
        // Startup may have aborted before these were assigned.
        if (::mediaMonitor.isInitialized) mediaMonitor.stop()
        companion?.close()
        systemReceiver?.let { runCatching { unregisterReceiver(it) } }
        runCatching { overlayContext.unregisterComponentCallbacks(overlayConfigCallback) }
        runCatching {
            getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        }
        removeOverlay()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshGeometry()
    }

    /**
     * Folding changes the display, not the configuration of the context we happen
     * to hold, so onConfigurationChanged alone missed it and the island kept using
     * the other screen's geometry — including the other screen's nudge.
     * DisplayListener fires on exactly this.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) = refreshGeometry()
        override fun onDisplayAdded(displayId: Int) = refreshGeometry()
        override fun onDisplayRemoved(displayId: Int) = refreshGeometry()
    }

    /** Fold, unfold or rotate: re-read the cutout and re-place the window. */
    private fun refreshGeometry() {
        if (!::windowManager.isInitialized) return
        IslandState.setCutout(CutoutReader.read(this))
        updateWindowPosition()
    }

    /**
     * The window context has its own configuration, and it is the one that actually
     * describes the display the overlay lives on. Listening to it as well as to the
     * service means a rotation cannot be missed.
     */
    private val overlayConfigCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = refreshGeometry()
        override fun onLowMemory() = Unit
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
    }

    // ---------------------------------------------------------------- overlay

    private fun layoutParams(): WindowManager.LayoutParams {
        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            // The window hugs the island, so anything outside it keeps working
            // normally — no full-screen scrim stealing touches from the app below.
            x = 0
            y = 0
        }
    }

    private fun addOverlay() {
        val viewHost = OverlayViewHost()
        val compose = ComposeView(overlayContext).apply {
            setContent {
                IslandRoot(
                    onTap = ::onIslandTap,
                    onHold = ::onIslandHold,
                    onCollapse = { IslandState.setView(IslandView.COMPACT) },
                    onMediaPlayPause = { mediaMonitor.playPause() },
                    onMediaNext = { mediaMonitor.next() },
                    onMediaPrevious = { mediaMonitor.previous() },
                    onAgentDecision = ::onAgentDecision,
                    onCallAnswer = ::onCallAnswer,
                    onCallDecline = ::onCallDecline,
                    onDismissInbox = { key -> IslandNotificationListener.instance?.dismiss(key) },
                    onClearInbox = { IslandState.clearInbox(); IslandState.setHeldCount(0) },
                    onOpenUrl = ::openInBrowser,
                    onToggleQuiet = ::toggleQuiet,
                    onMeasured = { updateWindowPosition() },
                )
            }
        }
        val container = object : android.widget.FrameLayout(overlayContext) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                // Tapping anywhere off the island collapses it back to compact.
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (IslandState.view.value != IslandView.COMPACT) {
                        IslandState.setView(IslandView.COMPACT)
                    }
                    return true
                }
                return super.onTouchEvent(event)
            }
        }
        container.addView(compose)

        // The owners must be on the view handed to WindowManager, not only on the
        // ComposeView. Compose resolves its recomposer from the *root* view of the
        // window, so it looks for the lifecycle owner starting at the container;
        // setting it one level down is invisible to that lookup and the view throws
        // "ViewTreeLifecycleOwner not found" the moment it attaches. Both are set so
        // the ComposeView is also self-sufficient if it is ever reparented.
        viewHost.attachTo(container)
        viewHost.attachTo(compose)
        viewHost.onStart()

        runCatching { windowManager.addView(container, layoutParams()) }
            .onFailure { error ->
                // Almost always the overlay permission being absent or revoked.
                CrashReporter.record(this, "adding the overlay window", error)
                viewHost.onStop()
                stopSelf()
                return
            }

        host = viewHost
        rootView = container
        updateWindowPosition()
    }

    private fun removeOverlay() {
        rootView?.let { runCatching { windowManager.removeView(it) } }
        host?.onStop()
        rootView = null
        host = null
    }

    /**
     * Centres the window on the cutout and clamps it to stay at least 8 dp inside
     * the screen — on the inner screen the cutout sits in the right half, so a
     * naive centre would push the island off the edge.
     */
    private fun updateWindowPosition() {
        val view = rootView ?: return
        val geo = IslandState.cutout.value
        val density = overlayContext.resources.displayMetrics.density
        val marginPx = (8 * density).toInt()

        view.post {
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@post
            val width = view.width.takeIf { it > 0 } ?: return@post

            // Manual nudge: detection cannot be trusted on every OEM screen, so the
            // user gets the last word on where the island sits.
            val settings = ServiceRuntime.current
            val offset = if (geo.folded) settings.offsetFolded else settings.offsetUnfolded
            val anchorX = geo.centerX + (offset * geo.screenWidth).toInt()

            var x = anchorX - width / 2
            x = x.coerceIn(marginPx, (geo.screenWidth - width - marginPx).coerceAtLeast(marginPx))

            // Pill top = cutout centre − half the pill height, so the camera sits
            // vertically centred inside the black shape.
            val cutoutHeightPx = geo.height.toFloat()
            val pillHeightPx = maxOf(
                ServiceRuntime.current.islandSize.pillHeightDp * density,
                cutoutHeightPx + settings.pillPadding * 2 * density,
            )
            val maxY = (geo.screenHeight - pillHeightPx.toInt() - marginPx).coerceAtLeast(0)
            // The pill hugs the top of its window now, so centring the window on the
            // cutout centres the pill on the camera.
            val offsetY = if (geo.folded) settings.offsetYFolded else settings.offsetYUnfolded
            val y = (geo.centerY - pillHeightPx / 2 + offsetY * density).toInt().coerceIn(0, maxY)

            if (params.x != x || params.y != y) {
                params.x = x
                params.y = y
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    // ------------------------------------------------------------ interaction

    private fun onIslandTap() {
        val settings = ServiceRuntime.current
        haptics.tick()
        if (!settings.tapShowsDetail) return
        IslandState.setView(
            if (IslandState.view.value == IslandView.COMPACT) IslandView.DETAIL else IslandView.COMPACT
        )
    }

    private fun onIslandHold() {
        haptics.confirm()
        IslandState.setView(
            if (IslandState.view.value == IslandView.EXPANDED) IslandView.COMPACT else IslandView.EXPANDED
        )
    }

    private fun onAgentDecision(agentId: String, approve: Boolean) {
        haptics.confirm()
        companion?.sendDecision(agentId, approve)
        val agent = IslandState.agents.value.firstOrNull { it.id == agentId } ?: return
        IslandState.upsertAgent(
            agent.copy(
                state = if (approve) com.notchhud.island.core.AgentState.RUNNING
                else com.notchhud.island.core.AgentState.ERROR,
                ask = null,
                task = if (approve) agent.task else "Denied — waiting for instructions",
            )
        )
    }

    private fun onCallAnswer() {
        val call = IslandState.call.value ?: return
        val listener = IslandNotificationListener.instance
        val item = IslandState.inbox.value.firstOrNull { it.packageName == call.packageName }
        val answered = item != null && listener?.invokeAction(item.key) { title ->
            title.contains("answer", true) || title.contains("accept", true)
        } == true
        if (!answered) {
            // No usable action on the notification: hand off to the app itself.
            runCatching {
                packageManager.getLaunchIntentForPackage(call.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            }
        }
        IslandState.setCall(null)
        IslandState.setView(IslandView.COMPACT)
    }

    private fun onCallDecline() {
        val call = IslandState.call.value ?: return
        val listener = IslandNotificationListener.instance
        val item = IslandState.inbox.value.firstOrNull { it.packageName == call.packageName }
        if (item != null) {
            listener?.invokeAction(item.key) { title ->
                title.contains("decline", true) || title.contains("dismiss", true) ||
                    title.contains("hang up", true) || title.contains("reject", true)
            }
        }
        IslandState.setCall(null)
        IslandState.setView(IslandView.COMPACT)
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val chrome = intent.clone() as Intent
        chrome.setPackage("com.android.chrome")
        val launched = runCatching { startActivity(chrome); true }.getOrDefault(false)
        if (!launched) runCatching { startActivity(intent) }
        IslandState.setView(IslandView.COMPACT)
    }

    private fun toggleQuiet() {
        lifecycleScope.launch {
            val current = ServiceRuntime.current.quietManual
            settingsRepo.setQuietManual(!current)
            if (current) IslandState.setHeldCount(0)
        }
    }

    // --------------------------------------------------------------- plumbing

    private fun registerSystemReceiver() {
        val receiver = SystemEventReceiver()
        systemReceiver = receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, SystemEventReceiver.filter(), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, SystemEventReceiver.filter())
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepo.flow.collectLatest { settings ->
                ServiceRuntime.update(settings)
                ServiceRuntime.setQuiet(resolveQuiet(settings))
                IslandState.setQuiet(ServiceRuntime.isQuiet)

                restartCollectors(settings)
                reconnectCompanion(settings)
                updateWindowPosition()
            }
        }
    }

    private fun resolveQuiet(settings: SettingsSnapshot): Boolean {
        if (settings.quietManual) return true
        if (settings.quietFollowsDnd) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = runCatching { nm.currentInterruptionFilter }.getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL)
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) return true
        }
        if (settings.quietOnCall) {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                am.mode == android.media.AudioManager.MODE_IN_CALL
            ) return true
        }
        return false
    }

    private fun reconnectCompanion(settings: SettingsSnapshot) {
        if (settings.companionUrl == companionUrl) return
        companionUrl = settings.companionUrl
        companion?.close()
        companion = if (companionUrl.isNotBlank()) {
            CompanionClient(companionUrl).also { it.connect() }
        } else {
            IslandState.agents.value.forEach { IslandState.removeAgent(it.id) }
            null
        }
    }

    private fun restartCollectors(settings: SettingsSnapshot) {
        sportsJob?.cancel()
        weatherJob?.cancel()
        calendarJob?.cancel()

        if (settings.modules[Modules.SPORTS] == true && settings.teams.isNotEmpty()) {
            sportsJob = lifecycleScope.launch { pollSports(settings) }
        } else {
            IslandState.setGame(null)
            IslandState.setPlays(emptyList())
        }

        val hasWeatherLocation = settings.useDeviceLocation ||
            (settings.weatherLat != null && settings.weatherLon != null)
        if (settings.modules[Modules.WEATHER] == true && hasWeatherLocation) {
            weatherJob = lifecycleScope.launch { pollWeather(settings) }
        } else {
            IslandState.setWeather(null)
        }

        if (settings.modules[Modules.MEETINGS] == true) {
            calendarJob = lifecycleScope.launch { pollCalendar() }
        } else {
            IslandState.setEvents(emptyList())
        }
    }

    /** 10 s while a followed game is live, 5 min otherwise — straight from the spec. */
    private suspend fun pollSports(settings: SettingsSnapshot) {
        var seenPlayIds = emptySet<String>()
        while (currentCoroutineContext().isActive) {
            val game = espn.currentGame(settings.leagues, settings.teams)
            IslandState.setGame(game)

            if (game != null && game.live && settings.sportsMode != SportsMode.SCORE_ONLY) {
                val plays = espn.plays(game.league, game.id)
                val fresh = plays.filter { it.id !in seenPlayIds }
                if (seenPlayIds.isNotEmpty()) {
                    val toShow = when (settings.sportsMode) {
                        SportsMode.SCORING_PLAYS -> fresh.filter { it.scoring }
                        SportsMode.PLAY_BY_PLAY -> fresh
                        else -> emptyList()
                    }
                    // Coalesce: several plays can land in one poll; show the newest.
                    toShow.firstOrNull()?.let { play ->
                        NotificationRouter.systemTransient(
                            left = "${play.awayScore}–${play.homeScore}",
                            right = play.text,
                            color = com.notchhud.island.core.Tokens.LiveSports,
                            durationMs = if (play.scoring) 2400L else 1800L,
                        )
                    }
                }
                seenPlayIds = plays.map { it.id }.toSet()
                IslandState.setPlays(plays)
            }

            delay(if (game?.live == true) 12_000L else 300_000L)
        }
    }

    private suspend fun pollWeather(settings: SettingsSnapshot) {
        val locations = LocationProvider(applicationContext)

        while (currentCoroutineContext().isActive) {
            val fix = resolveWeatherLocation(settings, locations)
            IslandState.setWeather(fix?.let { weatherRepo.fetch(it.latitude, it.longitude, it.city) })
            // A device fix moves with you, so re-check more often than a pinned city.
            delay(if (settings.useDeviceLocation) 10 * 60 * 1000L else 15 * 60 * 1000L)
        }
    }

    private data class WeatherFix(val latitude: Double, val longitude: Double, val city: String)

    private suspend fun resolveWeatherLocation(
        settings: SettingsSnapshot,
        locations: LocationProvider,
    ): WeatherFix? {
        if (settings.useDeviceLocation) {
            val location = locations.current() ?: return null
            val city = locations.cityName(location.latitude, location.longitude).orEmpty()
            return WeatherFix(location.latitude, location.longitude, city)
        }
        val lat = settings.weatherLat ?: return null
        val lon = settings.weatherLon ?: return null
        return WeatherFix(lat, lon, settings.weatherCity)
    }

    private suspend fun pollCalendar() {
        val repo = CalendarRepository(applicationContext)
        while (currentCoroutineContext().isActive) {
            IslandState.setEvents(repo.today())
            delay(5 * 60 * 1000L)
        }
    }

    /** Transients expire on their own clock; one ticker is cheaper than a timer each. */
    private fun startTransientReaper() {
        lifecycleScope.launch {
            while (currentCoroutineContext().isActive) {
                IslandState.clearTransientIfExpired()
                delay(200L)
            }
        }
    }

    /**
     * Keeps the island's idea of the keyguard honest.
     *
     * ACTION_USER_PRESENT is a fast path, not a guarantee — it does not reliably
     * arrive on every unlock flow, and when it went missing the island sat showing
     * a padlock over an unlocked phone with nothing able to clear it. The keyguard
     * state is cheap to query, so ask instead of waiting to be told: a binder call
     * a second costs nothing next to the animations already running, and the state
     * can no longer get stuck whatever the OEM does or does not broadcast.
     *
     * The broadcast receiver still handles the same transitions, so an unlock it
     * does catch is reflected immediately; both paths are idempotent, and this loop
     * acts only when the two disagree.
     */
    private fun startLockMonitor() {
        lifecycleScope.launch {
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager

            while (currentCoroutineContext().isActive) {
                // A dark screen is locked for our purposes, and skipping the query
                // keeps this loop off the CPU while the phone is in a pocket.
                val locked = if (power.isInteractive) keyguard.isKeyguardLocked else true

                if (locked != IslandState.locked.value) {
                    if (locked) {
                        IslandState.setUnlocking(false)
                        IslandState.setLocked(true)
                    } else {
                        // Set unlocking first: the lock wing has to still be on
                        // screen for the shackle animation to play out of.
                        if (ServiceRuntime.current.unlockAnimation) IslandState.setUnlocking(true)
                        IslandState.setLocked(false)
                    }
                }
                // Cheap safety net for any fold or rotation the callbacks miss. The
                // read no longer depends on where the window is, so this cannot
                // feed back into itself, and setCutout ignores an equal value.
                val geometry = CutoutReader.read(this@IslandOverlayService)
                if (geometry != IslandState.cutout.value) {
                    IslandState.setCutout(geometry)
                    updateWindowPosition()
                }

                delay(600L)
            }
        }
    }

    // ----------------------------------------------------------- notification

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.service_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
