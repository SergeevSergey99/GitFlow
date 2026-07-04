package com.gitflow.android.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gitflow.android.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Central place for date/size formatting so the same format isn't reimplemented per screen.
 * Replaces the former per-file `timeAgo` / `formatSize*` / `formatDate` copies.
 */

/** Relative "just now / Nm / Nh / Nd ago", falling back to an absolute date. Localized. */
@Composable
fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> stringResource(R.string.repo_card_time_just_now)
        diff < 3_600_000L -> stringResource(R.string.repo_card_time_minutes, diff / 60_000L)
        diff < 86_400_000L -> stringResource(R.string.repo_card_time_hours, diff / 3_600_000L)
        diff < 604_800_000L -> stringResource(R.string.repo_card_time_days, diff / 86_400_000L)
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Human-readable byte size: B / KB / MB / GB. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
}

/** Full commit timestamp, e.g. "Monday, July 4, 2026 at 12:00:00". */
fun formatDate(timestamp: Long): String =
    SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

/** Compact timestamp for history/list rows: "dd/MM/yy HH:mm". */
fun formatHistoryDate(timestamp: Long): String {
    if (timestamp <= 0L) return "--/--/-- --:--"
    return SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/**
 * ISO-8601 timestamp (e.g. "2026-07-04T12:00:00Z") -> "dd.MM.yyyy" in the device's zone.
 * Uses java.time so the trailing Z is treated as UTC — the old SimpleDateFormat parsed it as
 * a literal in the local zone, shifting the day near midnight for users far from UTC.
 */
fun isoToShortDate(iso: String): String {
    if (iso.isBlank()) return ""
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
    return try {
        formatter.format(Instant.parse(iso))
    } catch (e: Exception) {
        try {
            formatter.format(OffsetDateTime.parse(iso).toInstant())
        } catch (_: Exception) {
            iso.take(10) // "yyyy-MM-dd" prefix as a last resort
        }
    }
}
