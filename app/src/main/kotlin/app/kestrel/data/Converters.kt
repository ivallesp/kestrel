package app.kestrel.data

import androidx.room.TypeConverter
import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind

/**
 * Room column formats. Money is stored as "GBP:4850" (currency, colon, minor units) so the amount never passes
 * through a floating-point type. Enums are stored by name.
 */
class Converters {
    @TypeConverter
    fun moneyToText(money: Money?): String? = money?.let { MoneyColumn.format(it) }

    @TypeConverter
    fun textToMoney(text: String?): Money? = text?.let { MoneyColumn.parse(it) }

    @TypeConverter
    fun siteToText(site: EbaySite?): String? = site?.name

    @TypeConverter
    fun textToSite(text: String?): EbaySite? = text?.let { enumByName<EbaySite>(it) }

    @TypeConverter
    fun statusToText(status: SnipeStatus?): String? = status?.name

    @TypeConverter
    fun textToStatus(text: String?): SnipeStatus? = text?.let { enumByName<SnipeStatus>(it) }

    @TypeConverter
    fun outcomeToText(outcome: SnipeOutcome?): String? = outcome?.name

    @TypeConverter
    fun textToOutcome(text: String?): SnipeOutcome? = text?.let { enumByName<SnipeOutcome>(it) }

    @TypeConverter
    fun timelineKindToText(kind: TimelineKind?): String? = kind?.name

    @TypeConverter
    fun textToTimelineKind(text: String?): TimelineKind? = text?.let { enumByName<TimelineKind>(it) }
}

internal object MoneyColumn {
    private val currencyPattern = Regex("[A-Z]{3}")

    fun format(money: Money): String = "${money.currency}:${money.minor}"

    /** Null for anything this code did not write, so a damaged value can never turn into a different amount. */
    fun parse(text: String): Money? {
        val separator = text.indexOf(':')
        if (separator != 3) return null
        val currency = text.substring(0, separator)
        if (!currencyPattern.matches(currency)) return null
        val minor = text.substring(separator + 1).toLongOrNull() ?: return null
        return Money(minor, currency)
    }
}

/**
 * Enum names only ever grow, and Room refuses to open a database from a newer schema, so an unknown name means the
 * file is damaged. Failing loudly is safer than guessing a site or a status for a bid.
 */
private inline fun <reified T : Enum<T>> enumByName(name: String): T =
    enumValues<T>().firstOrNull { it.name == name }
        ?: throw IllegalStateException("Unknown ${T::class.java.simpleName} '$name' in the database")
