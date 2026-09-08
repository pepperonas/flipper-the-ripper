package io.celox.flipperripper.data.share

/**
 * The names that pair a published shortcut with the `<share-target>` in `res/xml/shortcuts.xml`.
 *
 * The system matches the two by *string*: a share-target whose category no shortcut carries is
 * simply never offered, and nothing anywhere reports that. Both halves therefore live here, and a
 * test reads the XML to check they still agree.
 */
object ShareShortcut {
    /** Category shared by the manifest's share-target and the shortcut published at start-up. */
    const val CATEGORY = "io.celox.flipperripper.category.SHARE_LINK"

    /** Id of the single shortcut. Stable, because publishing the same id updates rather than adds. */
    const val ID = "share-link"
}
