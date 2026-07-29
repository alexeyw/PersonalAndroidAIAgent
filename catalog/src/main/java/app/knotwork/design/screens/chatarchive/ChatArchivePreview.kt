package app.knotwork.design.screens.chatarchive

/**
 * Deterministic fixtures for the chat-archive surface — shared by `@Preview`s
 * and the Roborazzi snapshot baselines so both render the exact same content.
 *
 * The populated set deliberately carries the awkward cases the design calls
 * out: a title long enough to ellipsise, a favorited chat, and a chat whose
 * background run settled *after* it was archived.
 */
object ChatArchivePreview {

    private val rows: List<ChatArchiveRowUi> = listOf(
        ChatArchiveRowUi(
            id = "a1",
            title = "Weekly review of the inbox and the follow-ups I keep postponing",
            archivedLabel = "Archived 2 h ago",
        ),
        ChatArchiveRowUi(
            id = "a2",
            title = "Trip planning — Lisbon",
            archivedLabel = "Archived yesterday",
            starred = true,
        ),
        ChatArchiveRowUi(
            id = "a3",
            title = "Morning digest",
            archivedLabel = "Archived 3 d ago",
            ranAfterArchiving = true,
        ),
        ChatArchiveRowUi(
            id = "a4",
            title = "Recipe ideas",
            archivedLabel = "Archived 2 w ago",
        ),
    )

    /** Populated list — the default state. */
    fun populated(): ChatArchiveViewState = ChatArchiveViewState(
        visualState = ChatArchiveVisualState.Default,
        rows = rows,
        subtitle = "4 chats · newest first",
    )

    /** Populated list with the first row's swipe action revealed. */
    fun swipeOpen(): ChatArchiveViewState = populated().copy(revealedRowId = "a1")

    /** Populated list with the second row's overflow menu open. */
    fun rowMenu(): ChatArchiveViewState = populated().copy(openMenuRowId = "a2")

    /** Populated list with the delete-forever confirmation up. */
    fun deleteConfirm(): ChatArchiveViewState = populated().copy(deleteTarget = rows[1])

    /** Teaching empty state. */
    fun empty(): ChatArchiveViewState = ChatArchiveViewState(visualState = ChatArchiveVisualState.Empty)

    /** Skeleton state shown while the archived list is first read. */
    fun loading(): ChatArchiveViewState = ChatArchiveViewState(visualState = ChatArchiveVisualState.Loading)

    /** Load failure. */
    fun error(): ChatArchiveViewState = ChatArchiveViewState(
        visualState = ChatArchiveVisualState.Error,
        errorMessage = "Your archived chats are still on the device — reading them failed. Try again.",
    )
}
