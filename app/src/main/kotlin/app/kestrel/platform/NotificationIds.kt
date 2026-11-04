package app.kestrel.platform

/**
 * Notification ids and PendingIntent request codes. Each snipe owns a block of slots so its notifications and
 * actions never collide with another snipe's. PendingIntents also carry a per-snipe data URI, so even a wrapped id
 * cannot make two snipes share one.
 */
internal object NotificationIds {
    const val SERVICE = 1
    const val UNLOCK_TO_ARM = 2
    const val SCHEDULE_PROBLEM = 3

    fun window(snipeId: Long): Int = forSnipe(snipeId, Slot.WINDOW)

    fun priceAlert(snipeId: Long): Int = forSnipe(snipeId, Slot.PRICE_ALERT)

    fun needsUser(snipeId: Long): Int = forSnipe(snipeId, Slot.NEEDS_USER)

    fun result(snipeId: Long): Int = forSnipe(snipeId, Slot.RESULT)

    fun tapToArm(snipeId: Long): Int = forSnipe(snipeId, Slot.TAP_TO_ARM)

    fun requestCode(
        snipeId: Long,
        slot: Slot,
    ): Int = forSnipe(snipeId, slot)

    enum class Slot {
        WINDOW,
        PRICE_ALERT,
        NEEDS_USER,
        RESULT,
        TAP_TO_ARM,
        OPEN,
        RAISE_MAX,
        CANCEL,
        WINDOW_DISMISSED,
        ALARM_CHECK,
        ALARM_DRY_RUN,
        ALARM_WINDOW,
        ALARM_WINDOW_BACKUP,
        ALARM_CLOCK_SHOW,
    }

    private const val FIRST_SNIPE_ID = 1_000
    private const val SNIPE_ID_WRAP = 100_000_000L

    private fun forSnipe(
        snipeId: Long,
        slot: Slot,
    ): Int = FIRST_SNIPE_ID + (Math.floorMod(snipeId, SNIPE_ID_WRAP)).toInt() * Slot.entries.size + slot.ordinal
}
