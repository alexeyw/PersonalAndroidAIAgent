package app.knotwork.android.domain.report

/**
 * Why the user is flagging a model-authored message.
 *
 * The list is deliberately short and non-overlapping: a reporting form that
 * asks for a taxonomy nobody can apply produces reports nobody can act on.
 * [OTHER] exists so a report is never blocked by the absence of a matching
 * category — the free-text note carries the detail in that case.
 */
enum class ContentReportReason {
    /** Output that could cause harm if acted on, or that describes harmful acts. */
    HARMFUL_OR_UNSAFE,

    /** Sexually explicit output. */
    SEXUALLY_EXPLICIT,

    /** Hateful, harassing, or demeaning output directed at a person or group. */
    HATE_OR_HARASSMENT,

    /** Confidently stated output that is factually wrong or fabricated. */
    MISLEADING,

    /** Anything the categories above do not cover; detail goes in the note. */
    OTHER,
}

/**
 * A user's flag against one model-authored message, assembled in the chat
 * screen and rendered to text by [ContentReportComposer].
 *
 * The object is a value carrier only — it is never persisted and never sent
 * anywhere on its own. The user decides what happens to the rendered text
 * (copy it, or carry it into a report they submit themselves), which is why
 * nothing here is a repository concern.
 *
 * @property reason The category the user picked.
 * @property note Free-text detail the user typed; may be blank.
 * @property messageText The reported model output, verbatim as shown in the chat.
 * @property appVersion Version name of the running build (e.g. `0.7.0`).
 * @property buildIdentifier Short build identifier — the git SHA baked into the build.
 * @property device Device descriptor (`manufacturer model`).
 * @property androidVersion Android release the device runs (e.g. `16`).
 * @property modelIdentifier Identifier of the model that produced the message,
 *   or `null` when the chat screen cannot resolve it.
 */
data class ContentReport(
    val reason: ContentReportReason,
    val note: String,
    val messageText: String,
    val appVersion: String,
    val buildIdentifier: String,
    val device: String,
    val androidVersion: String,
    val modelIdentifier: String? = null,
)
