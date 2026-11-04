package app.kestrel.engine

import java.util.Locale
import kotlin.math.abs

/** "45 s", "1 min 28 s", "2 h 5 min", "3 d 4 h": durations for timeline messages, which never assume a time zone. */
internal fun durationText(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) + 500) / 1_000
    val days = totalSeconds / 86_400
    val hours = totalSeconds % 86_400 / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "$days d $hours h"
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 && seconds > 0 -> "$minutes min $seconds s"
        minutes > 0 -> "$minutes min"
        else -> "$seconds s"
    }
}

/** "6.00 s": the bid lead to the hundredth of a second. */
internal fun secondsText(millis: Long): String = String.format(Locale.ROOT, "%.2f s", millis / 1_000.0)

/** How the phone's clock compares with true time, from [ClockSync.offsetMillis] (true minus system). */
internal fun offsetText(offsetMillis: Long): String {
    val seconds = String.format(Locale.ROOT, "%.2f", abs(offsetMillis) / 1_000.0)
    return when {
        offsetMillis > 0 -> "the phone's clock is $seconds s behind"
        offsetMillis < 0 -> "the phone's clock is $seconds s ahead"
        else -> "the phone's clock is right"
    }
}
