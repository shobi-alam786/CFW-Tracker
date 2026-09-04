package com.example.data.kobo

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats KoboToolbox's `_submission_time` field consistently everywhere it's displayed.
 *
 * KoboToolbox always returns `_submission_time` as UTC (e.g. "2026-08-29T15:53:00" — no
 * offset suffix in practice). Parsing that string with the device's *default* time zone
 * makes "15:53 UTC" display as "3:53 PM" verbatim, instead of converting it to the device's
 * local time (21:53 / 9:53 PM in UTC+6). This utility parses as UTC and formats in the
 * device's local zone, and is the single place that logic lives so it can't drift out of
 * sync between screens again.
 */
object KoboDateUtils {

    private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"

    /** Parses a Kobo UTC timestamp into a real instant. Returns null if it can't be parsed. */
    fun parse(raw: String): Date? {
        if (raw.isBlank()) return null
        return try {
            val cleaned = raw
                .substringBefore(".")
                .removeSuffix("Z")
                .substringBefore("+")
            val parser = SimpleDateFormat(ISO_PATTERN, Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            parser.parse(cleaned)
        } catch (_: Exception) {
            null
        }
    }

    /** e.g. "Aug 29, 2026 • 9:53 PM" (converted to the device's local time zone). */
    fun formatFull(raw: String): String {
        val date = parse(raw) ?: return raw.ifBlank { "Unknown date" }
        return SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(date)
    }

    /** e.g. "Aug 29, 2026" — no time component. */
    fun formatDateOnly(raw: String): String {
        val date = parse(raw) ?: return raw.ifBlank { "Unknown date" }
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }

    /** e.g. "9:53 PM" (local time zone). */
    fun formatTimeOnly(raw: String): String {
        val date = parse(raw) ?: return raw.ifBlank { "—" }
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    }

    /** e.g. "Today", "Yesterday", or "Aug 29, 2026" — based on local calendar day. */
    fun groupLabel(raw: String): String {
        val date = parse(raw) ?: return "Unknown Date"
        val cal = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            else -> formatDateOnly(raw)
        }
    }

    /** True when both raw Kobo timestamps fall on the same local calendar day. */
    fun isSameDay(rawA: String, rawB: String): Boolean {
        val a = parse(rawA) ?: return false
        val b = parse(rawB) ?: return false
        val calA = Calendar.getInstance().apply { time = a }
        val calB = Calendar.getInstance().apply { time = b }
        return isSameDay(calA, calB)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
