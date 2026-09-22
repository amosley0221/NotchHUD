package com.notchhud.island.ui.setup

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.notchhud.island.BuildConfig
import com.notchhud.island.core.CrashReporter
import com.notchhud.island.service.IslandNotificationListener
import com.notchhud.island.service.IslandOverlayService

/**
 * First-run permissions and the on/off switch. Deliberately plain: the island
 * itself is the product, this screen only exists to get it the access it needs.
 */
class MainActivity : ComponentActivity() {

    private val requestRuntimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetupTheme {
                Surface(Modifier.fillMaxSize()) {
                    SetupScreen(
                        onRequestRuntime = ::requestRuntime,
                        onStart = { IslandOverlayService.start(this) },
                        onStop = { IslandOverlayService.stop(this) },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    )
                }
            }
        }
    }

    private fun requestRuntime() {
        val wanted = buildList {
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }

        if (wanted.isNotEmpty()) requestRuntimePermissions.launch(wanted.toTypedArray())
    }
}

@Composable
private fun SetupScreen(
    onRequestRuntime: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    // Permission state only changes while we are away in system Settings, so
    // re-read it on every resume rather than polling.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val canDrawOverlays = remember(refresh) { Settings.canDrawOverlays(context) }
    val listenerEnabled = remember(refresh) { isNotificationListenerEnabled(context) }
    val runtimeGranted = remember(refresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val batteryExempt = remember(refresh) { isIgnoringBatteryOptimizations(context) }
    val lastCrash = remember(refresh) { CrashReporter.lastCrash(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Island HUD", fontSize = 26.sp, fontWeight = FontWeight.W600)

        if (lastCrash != null) CrashCard(lastCrash) { CrashReporter.clear(context); refresh++ }
        Text(
            "A Dynamic-Island-style surface wrapped around your camera cutout. " +
                "Nothing is shown until a real source has data.",
            fontSize = 13.sp,
        )

        Spacer(Modifier.width(1.dp))

        PermissionRow(
            title = "Draw over other apps",
            granted = canDrawOverlays,
            detail = "Required — this is the island itself.",
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }

        PermissionRow(
            title = "Notification access",
            granted = listenerEnabled,
            detail = "Mirrors messages, calls and now-playing into the island.",
        ) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        PermissionRow(
            title = "Calendar, location, notifications",
            granted = runtimeGranted,
            detail = "Meetings, weather and the island's own foreground notice.",
            onClick = onRequestRuntime,
        )

        PermissionRow(
            title = "Ignore battery optimisation",
            granted = batteryExempt,
            detail = "Samsung will otherwise put the island to sleep. Also turn off " +
                "\"Put unused apps to sleep\" in Device care.",
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        }

        Spacer(Modifier.width(1.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStart) { Text("Start island") }
            OutlinedButton(onClick = onStop) { Text("Stop") }
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
        }

        Text(
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_SHA.take(7)}",
            fontSize = 11.sp,
        )    }
}

/**
 * Shown only when the app died last time. An overlay service crashes with nothing
 * on screen to explain it, and pulling a logcat off a phone is a lot to ask, so the
 * trace is right here with a button to copy it.
 */
@Composable
private fun CrashCard(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x33FF5A5A), RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Text("The app stopped last time", fontSize = 15.sp, fontWeight = FontWeight.W600)
        Text(report.lineSequence().first(), fontSize = 12.sp)

        if (expanded) {
            Text(
                report,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "Show details")
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Island HUD crash", report))
            }) { Text("Copy") }
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (granted) "✓  $title" else "○  $title",
                fontSize = 15.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.weight(1f),
            )
            if (!granted) OutlinedButton(onClick = onClick) { Text("Grant") }
        }
        Text(detail, fontSize = 12.sp)
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    return flat.contains(IslandNotificationListener::class.java.name)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    pm.isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(false)
