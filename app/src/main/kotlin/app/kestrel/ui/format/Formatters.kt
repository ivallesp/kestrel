package app.kestrel.ui.format

import app.kestrel.ebay.MoneyText
import app.kestrel.model.Money
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Every money and time string the screens show, in one place (DESIGN 2.6, 6.4 and P3). Times are formatted from an
// Instant in Europe/London and name GMT or BST for that instant; seconds appear only within an hour of the moment
// (the arm summary and receipts always have them); days are day names with dates, "Today" only for today, and never
// "tomorrow" or "yesterday".

/** The zone every day and time label is shown in. */
val AuctionZone: ZoneId = ZoneId.of("Europe/London")

/** Between a number and its unit: "13 m", "6 s", "0.8 s". Never breaks and stays compact. */
const val NARROW_NO_BREAK_SPACE = '\u202F'

/** A space that does not break; inside tabular runs it replaces U+0020, which Google Sans Flex's tnum widens. */
const val NO_BREAK_SPACE = '\u00A0'

/** Joins two characters so a line never breaks between them ("f/1.8", "MZ-N707"). */
const val WORD_JOINER = '\u2060'

/** Lets a line break after a no-break space in wrapping figure text. */
const val ZERO_WIDTH_SPACE = '\u200B'

private val UK: Locale = Locale.UK
private val Clock = DateTimeFormatter.ofPattern("HH:mm", UK)
private val ClockSeconds = DateTimeFormatter.ofPattern("HH:mm:ss", UK)
private val DayDate = DateTimeFormatter.ofPattern("EEE d MMM", UK)
private val LongDate = DateTimeFormatter.ofPattern("EEEE d MMMM", UK)
private val MonthName = DateTimeFormatter.ofPattern("MMMM", UK)
private val MonthAndYear = DateTimeFormatter.ofPattern("MMMM yyyy", UK)
private val ZoneShortName = DateTimeFormatter.ofPattern("zzz", UK)

private val ONE_HOUR: Duration = Duration.ofHours(1)

/** "£2,150.00": the currency symbol, thousands separators and two decimals, as eBay's own pages write it. */
fun Money.formatted(): String = MoneyText.format(this)

private fun Instant.local(zone: ZoneId): ZonedDateTime = atZone(zone)

/** "21:02" */
fun Instant.clock(zone: ZoneId = AuctionZone): String = Clock.format(local(zone))

/** "18:21:34" */
fun Instant.clockWithSeconds(zone: ZoneId = AuctionZone): String = ClockSeconds.format(local(zone))

/** "18:21:33.9": receipts, to the tenth of a second (truncated, so a bid at .95 never reads as the next second). */
fun Instant.clockWithTenths(zone: ZoneId = AuctionZone): String {
    val tenths = local(zone).nano / 100_000_000
    return "${clockWithSeconds(zone)}.$tenths"
}

/** "GMT" in winter, "BST" in summer. */
fun Instant.zoneName(zone: ZoneId = AuctionZone): String = ZoneShortName.format(local(zone))

/** "Sun 9 Nov" */
fun Instant.dayDate(zone: ZoneId = AuctionZone): String = DayDate.format(local(zone))

/** "Sunday 9 November" */
fun Instant.longDate(zone: ZoneId = AuctionZone): String = LongDate.format(local(zone))

/** "November", or "December 2024" when the year is not the year of [now]. */
fun Instant.monthHeading(
    now: Instant,
    zone: ZoneId = AuctionZone,
): String =
    if (local(zone).year ==
        now.local(zone).year
    ) {
        MonthName.format(local(zone))
    } else {
        MonthAndYear.format(local(zone))
    }

/** True when [instant] falls on the same London calendar day as [now]. */
fun isSameDay(
    now: Instant,
    instant: Instant,
    zone: ZoneId = AuctionZone,
): Boolean = now.local(zone).toLocalDate() == instant.local(zone).toLocalDate()

/** True when [instant] is less than an hour from [now], before or after. Seconds are shown only then. */
fun isWithinAnHour(
    now: Instant,
    instant: Instant,
): Boolean = Duration.between(now, instant).abs() < ONE_HOUR

/** "Today" or "Mon 10 Nov". */
fun dayLabel(
    now: Instant,
    instant: Instant,
    zone: ZoneId = AuctionZone,
): String = if (isSameDay(now, instant, zone)) "Today" else instant.dayDate(zone)

/**
 * The absolute time of a moment, seconds and zone only within the hour: "Today 18:21:40 GMT", "Today 21:02",
 * "Mon 10 Nov, 20:10", "Mon 10 Nov, 00:10:05 GMT".
 */
fun absoluteTime(
    now: Instant,
    instant: Instant,
    zone: ZoneId = AuctionZone,
): String {
    val close = isWithinAnHour(now, instant)
    val time = if (close) "${instant.clockWithSeconds(zone)} ${instant.zoneName(zone)}" else instant.clock(zone)
    return if (isSameDay(now, instant, zone)) "Today $time" else "${instant.dayDate(zone)}, $time"
}

/** A clock time with seconds only within the hour: "18:21:34" or "21:02". */
fun clockFor(
    now: Instant,
    instant: Instant,
    zone: ZoneId = AuctionZone,
): String = if (isWithinAnHour(now, instant)) instant.clockWithSeconds(zone) else instant.clock(zone)

/** The S1 overline: "SUNDAY 9 NOVEMBER · TIMES IN GMT". The zone is the one in force on that day. */
fun zoneOverline(
    now: Instant,
    zone: ZoneId = AuctionZone,
): String = "${now.longDate(zone)} · times in ${now.zoneName(zone)}".uppercase(UK)

/**
 * The exact contract above "Arm snipe", with seconds and zone however far away it is:
 * "Bids £90.00 on Thu 13 Nov at 20:04:12 GMT", or "Bids £120.00 today at 18:21:34 GMT".
 */
fun armSummary(
    max: Money,
    bidAt: Instant,
    now: Instant,
    zone: ZoneId = AuctionZone,
): String {
    val day = if (isSameDay(now, bidAt, zone)) "today" else "on ${bidAt.dayDate(zone)}"
    return "Bids ${max.formatted()} $day at ${bidAt.clockWithSeconds(zone)} ${bidAt.zoneName(zone)}"
}

/**
 * A receipt time: tenths on the day of the result ("18:21:33.9"), the day and seconds otherwise
 * ("Wed 5 Nov 21:12:44").
 */
fun receiptTime(
    instant: Instant,
    resultDay: Instant,
    zone: ZoneId = AuctionZone,
): String =
    if (isSameDay(resultDay, instant, zone)) {
        instant.clockWithTenths(zone)
    } else {
        "${instant.dayDate(zone)} ${instant.clockWithSeconds(zone)}"
    }

/**
 * Time left, more precise near the end: "4 d 1 h", "2 h 53 m", "13 m 07 s", "36 s". Seconds appear only under an
 * hour. Number and unit are joined by U+202F; groups are separated by a normal space.
 */
fun Duration.countdown(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = totalSeconds % 86_400 / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val secondsPart = totalSeconds % 60
    return when {
        totalSeconds < 60 -> unit(secondsPart, "s")
        totalSeconds < 3_600 -> "${unit(minutes, "m")} ${unit(twoDigits(secondsPart), "s")}"
        totalSeconds < 86_400 -> "${unit(hours, "h")} ${unit(twoDigits(minutes), "m")}"
        else -> "${unit(days, "d")} ${unit(hours, "h")}"
    }
}

/** The same precision in words, for TalkBack: "13 minutes 7 seconds", "2 hours 53 minutes", "1 day 2 hours". */
fun Duration.spoken(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = totalSeconds % 86_400 / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val secondsPart = totalSeconds % 60
    return when {
        totalSeconds < 60 -> plural(secondsPart, "second", "seconds")
        totalSeconds < 3_600 -> "${plural(minutes, "minute", "minutes")} ${plural(secondsPart, "second", "seconds")}"
        totalSeconds < 86_400 -> "${plural(hours, "hour", "hours")} ${plural(minutes, "minute", "minutes")}"
        else -> "${plural(days, "day", "days")} ${plural(hours, "hour", "hours")}"
    }
}

/** Time left from [now] to [end], as [countdown]. */
fun timeLeft(
    now: Instant,
    end: Instant,
): String = Duration.between(now, end).countdown()

/** "6 s" */
fun secondsText(count: Long): String = unit(count, "s")

/** "6.1 s": a lead or a reply time in tenths (truncated). */
fun secondsWithTenths(millis: Long): String {
    val tenths = millis.coerceAtLeast(0) / 100
    return unit("${tenths / 10}.${tenths % 10}", "s")
}

/** "0.04 s": a clock offset in hundredths (rounded). */
fun secondsWithHundredths(millis: Long): String {
    val hundredths = (kotlin.math.abs(millis) + 5) / 10
    return unit("${hundredths / 100}.${twoDigits(hundredths % 100)}", "s")
}

/** "1 lot", "2 lots", "14 bids". */
fun plural(
    count: Long,
    one: String,
    many: String,
): String = if (count == 1L) "1 $one" else "$count $many"

fun plural(
    count: Int,
    one: String,
    many: String,
): String = plural(count.toLong(), one, many)

/**
 * Listing titles for display (rule T7): a no-break space before "f/", and a word joiner after "/" and after a "-"
 * between letters or digits, so "50mm f/1.8", "MZ-N707" and "DMG-01" never break.
 */
fun String.forDisplay(): String =
    replace(" f/", "${NO_BREAK_SPACE}f/")
        .replace(SlashBeforeWord, "/$WORD_JOINER")
        .replace(HyphenInsideWord, "-$WORD_JOINER")

private val SlashBeforeWord = Regex("""/(?=[0-9A-Za-z])""")
private val HyphenInsideWord = Regex("""(?<=[0-9A-Za-z])-(?=[0-9A-Za-z])""")

/**
 * Kestrel's own words with typographic quotes (rule SC3): apostrophes become ’ and double quotes become “ and ”.
 * For text the engine wrote; eBay's quoted sentences are shown exactly as eBay wrote them.
 */
fun String.withCurlyQuotes(): String {
    val builder = StringBuilder(length)
    forEachIndexed { index, character ->
        val previous = if (index == 0) ' ' else this[index - 1]
        val opens = previous.isWhitespace() || previous in "([{“‘"
        builder.append(
            when (character) {
                '\'' -> if (opens) '‘' else '’'
                '"' -> if (opens) '“' else '”'
                else -> character
            },
        )
    }
    return builder.toString()
}

private fun unit(
    value: Any,
    unit: String,
): String = "$value$NARROW_NO_BREAK_SPACE$unit"

private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')
