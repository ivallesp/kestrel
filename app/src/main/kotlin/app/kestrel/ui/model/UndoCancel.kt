package app.kestrel.ui.model

/**
 * Words for "Undo cancel" (DESIGN S4 "Cancelled" and the Group "After a win" rule). The change itself is
 * SnipeActions.uncancel: the same snipe comes back armed, keeping its timeline; one its group cancelled leaves the group.
 */
object UndoCancel {
    /** The snackbar once it is armed again, with the lot's short name: "Sniping Sony Walkman again". */
    fun message(shortTitle: String): String = "Sniping $shortTitle again"
}
