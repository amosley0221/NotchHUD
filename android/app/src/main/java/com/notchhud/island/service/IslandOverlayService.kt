package com.notchhud.island.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
import com.notchhud.island.data.CalendarRepository
import com.notchhud.island.data.EspnRepository
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
    // display and density. A plain Service context throws.
    private val overlayContext: Context by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
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
        windowManager = overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepo = SettingsRepository(applicationContext)
        mediaMonitor = MediaMonitor(applicationContext)
        haptics = Haptics(applicationContext)

        startForeground(NOTIFICATION_ID, buildNotification())

        // Everything past this point touches window geometry, OEM-specific
        // behaviour and permissions that can be revoked while we run. A failure
        // here would otherwise kill the process with nothing on screen to explain
        // it, so record it where the setup screen can show it and stop cleanly.
        try {
            IslandState.setCutout(CutoutReader.read(overlayContext, windowManager))
            IslandState.setLocked(
                (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
            )

            addOverlay()
            registerSystemReceiver()
            mediaMonitor.start()
            observeSettings()
            startTransientReaper()
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
        mediaMonitor.stop()
        companion?.close()
        systemReceiver?.let { runCatching { unregisterReceiver(it) } }
        removeOverlay()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Fold, unfold or rotate: re-read the cutout and re-place the window.
        IslandState.setCutout(CutoutReader.read(overlayContext, windowManager))
        updateWindowPosition()
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
        viewHost.attachTo(compose)
        viewHost.onStart()

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

            var x = geo.centerX - width / 2
            x = x.coerceIn(marginPx, (geo.screenWidth - width - marginPx).coerceAtLeast(marginPx))

            // Pill top = cutout centre − half the pill height, so the camera sits
            // vertically centred inside the black shape.
            val pillHeightPx = ServiceRuntime.current.islandSize.pillHeightDp * density
            val y = (geo.centerY - pillHeightPx / 2).toInt().coerceAtLeast(0)

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

        if (settings.modules[Modules.WEATHER] == true && settings.weatherLat != null && settings.weatherLon != null) {
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
        val lat = settings.weatherLat ?: return
        val lon = settings.weatherLon ?: return
        while (currentCoroutineContext().isActive) {
            val city = settings.weatherCity.ifBlank { weatherRepo.cityFor(lat, lon) }
            IslandState.setWeather(weatherRepo.fetch(lat, lon, city))
            delay(15 * 60 * 1000L)
        }
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
