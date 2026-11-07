package app.kestrel

/**
 * What differs between the two builds of Kestrel (the `distribution` flavours in app/build.gradle.kts). The GitHub
 * build is sideloaded. The Google Play build follows Play's policies: Play keeps USE_EXACT_ALARM for alarm, timer and
 * calendar apps, so the user allows exact alarms once; battery is set on Android's own page instead of through a direct
 * exemption request; and nothing in the app links to a payment page.
 */
enum class Distribution(
    /** "Buy me a coffee" opens the Ko-fi page. */
    val coffeeLink: Boolean,
    /** Exact alarms come with the install (USE_EXACT_ALARM) rather than from the user (SCHEDULE_EXACT_ALARM). */
    val exactAlarmsAtInstall: Boolean,
    /** Ready to snipe asks for Unrestricted battery in one tap (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). */
    val directBatteryRequest: Boolean,
) {
    GITHUB(coffeeLink = true, exactAlarmsAtInstall = true, directBatteryRequest = true),
    PLAY(coffeeLink = false, exactAlarmsAtInstall = false, directBatteryRequest = false),
    ;

    companion object {
        /** This build's, from its flavour. */
        val CURRENT: Distribution = valueOf(BuildConfig.DISTRIBUTION)
    }
}
