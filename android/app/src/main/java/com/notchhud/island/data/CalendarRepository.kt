package com.notchhud.island.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.notchhud.island.core.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Today's events from CalendarContract. Join URLs are parsed out of the location,
 * description and title so the Join button can exist without a Teams/Zoom SDK.
 */
class CalendarRepository(private val context: Context) {

    private val joinPattern = Regex(
        """https?://(?:[\w.-]*)(?:teams\.microsoft\.com|teams\.live\.com|zoom\.us|meet\.google\.com|webex\.com|whereby\.com)/\S+""",
        RegexOption.IGNORE_CASE,
    )

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun today(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 24 * 60 * 60 * 1000L

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString()).appendPath(end.toString()).build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY,
        )

        val out = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(7) == 1) continue // skip all-day: they are not "next meeting" material
                    val title = c.getString(1) ?: continue
                    val location = c.getString(4)
                    val description = c.getString(5)
                    out += CalendarEvent(
                        id = c.getLong(0),
                        title = title,
                        start = c.getLong(2),
                        end = c.getLong(3),
                        location = location,
                        colorArgb = c.getInt(6).takeIf { it != 0 } ?: 0xFF3D8BFF.toInt(),
                        joinUrl = findJoinUrl(location, description, title),
                    )
                }
            }
        }
        out
    }

    private fun findJoinUrl(vararg fields: String?): String? =
        fields.filterNotNull().firstNotNullOfOrNull { joinPattern.find(it)?.value?.trimEnd('>', ')', ',', '.') }
}
