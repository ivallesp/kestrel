package app.kestrel.ui.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every screen of the app, as a Navigation 3 key. Keys are serialisable, so the back stack survives process death.
 * Ids are domain snipe and group ids.
 */
@Serializable
sealed interface Destination : NavKey {
    /** S1: the list of snipes. */
    @Serializable
    data object Home : Destination

    /** S2: a new snipe, from a shared or pasted [link] (null opens the link entry). */
    @Serializable
    data class Add(
        val link: String? = null,
    ) : Destination

    /** S3: one snipe's detail and its final minute; [editMax] opens its edit sheet (the Live Update's "Edit max"). */
    @Serializable
    data class Lot(
        val id: Long,
        val editMax: Boolean = false,
    ) : Destination

    /** S4: one snipe's result and receipt. */
    @Serializable
    data class Result(
        val id: Long,
    ) : Destination

    /** S5: the readiness checks. */
    @Serializable
    data object Ready : Destination

    /** S6: the sign-in introduction. */
    @Serializable
    data object SignIn : Destination

    /** S6: eBay's sign-in page in Kestrel's own browser. */
    @Serializable
    data object SignInBrowser : Destination

    /** A visible browser on snipe [id]'s listing, for captchas and verification Kestrel hands to the user. */
    @Serializable
    data class HandOff(
        val id: Long,
    ) : Destination

    /** One "win 1 of N" group. */
    @Serializable
    data class Group(
        val id: Long,
    ) : Destination

    @Serializable
    data object Settings : Destination

    /** Every finished snipe. */
    @Serializable
    data object History : Destination

    /** The first launch. */
    @Serializable
    data object Onboarding : Destination
}
