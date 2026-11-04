package app.kestrel.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.R

/**
 * The Material Symbols Rounded glyphs Kestrel uses (weight 400, vendored as vector XML; DESIGN 2.11). Filled glyphs
 * only for warning, error, trophy, check_circle and battery_alert. Sizes: 16 dp in flags, 18 dp in state labels and
 * chips, 20 to 24 dp in rows, 26 dp in the seal.
 */
object Symbols {
    // States.
    @DrawableRes val Visibility = R.drawable.ic_visibility

    @DrawableRes val Schedule = R.drawable.ic_schedule

    @DrawableRes val Warning = R.drawable.ic_warning_fill

    @DrawableRes val Gavel = R.drawable.ic_gavel

    @DrawableRes val Trophy = R.drawable.ic_trophy_fill

    @DrawableRes val Flag = R.drawable.ic_flag

    @DrawableRes val Error = R.drawable.ic_error_fill

    @DrawableRes val CheckCircle = R.drawable.ic_check_circle_fill

    // Chrome.
    @DrawableRes val ArrowBack = R.drawable.ic_arrow_back

    @DrawableRes val Close = R.drawable.ic_close

    @DrawableRes val MoreVert = R.drawable.ic_more_vert

    @DrawableRes val Share = R.drawable.ic_share

    @DrawableRes val Add = R.drawable.ic_add

    @DrawableRes val ContentPaste = R.drawable.ic_content_paste

    @DrawableRes val ExpandMore = R.drawable.ic_expand_more

    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right

    @DrawableRes val Edit = R.drawable.ic_edit

    @DrawableRes val ArrowUpward = R.drawable.ic_arrow_upward

    @DrawableRes val Refresh = R.drawable.ic_refresh

    /** Only for "Open on eBay" menu items, never on "Continue to eBay". */
    @DrawableRes val OpenInNew = R.drawable.ic_open_in_new

    /** The "Stop sniping" menu item. */
    @DrawableRes val DoNotDisturbOn = R.drawable.ic_do_not_disturb_on

    /** The "Delete" menu item, after a divider; its snackbar offers Undo. */
    @DrawableRes val Delete = R.drawable.ic_delete

    @DrawableRes val Check = R.drawable.ic_check

    // Facts.
    @DrawableRes val Timer = R.drawable.ic_timer

    @DrawableRes val Link = R.drawable.ic_link

    @DrawableRes val Storefront = R.drawable.ic_storefront

    @DrawableRes val Payments = R.drawable.ic_payments

    @DrawableRes val Info = R.drawable.ic_info

    @DrawableRes val Mail = R.drawable.ic_mail

    @DrawableRes val TrendingUp = R.drawable.ic_trending_up

    @DrawableRes val Sync = R.drawable.ic_sync

    @DrawableRes val CloudOff = R.drawable.ic_cloud_off

    @DrawableRes val HourglassTop = R.drawable.ic_hourglass_top

    // Checks.
    @DrawableRes val AccountCircle = R.drawable.ic_account_circle

    @DrawableRes val Notifications = R.drawable.ic_notifications

    @DrawableRes val AlarmOn = R.drawable.ic_alarm_on

    @DrawableRes val BatteryAlert = R.drawable.ic_battery_alert_fill

    @DrawableRes val BatteryFull = R.drawable.ic_battery_full

    /** Live Updates. */
    @DrawableRes val Update = R.drawable.ic_update

    /** Dry run. */
    @DrawableRes val TaskAlt = R.drawable.ic_task_alt

    // Sign-in.
    @DrawableRes val Lock = R.drawable.ic_lock

    @DrawableRes val Smartphone = R.drawable.ic_smartphone

    @DrawableRes val AlternateEmail = R.drawable.ic_alternate_email

    @DrawableRes val Sms = R.drawable.ic_sms

    @DrawableRes val Person = R.drawable.ic_person
}

/** A Material Symbols vector, tinted with the current content colour unless [tint] says otherwise. */
@Composable
fun SymbolIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
