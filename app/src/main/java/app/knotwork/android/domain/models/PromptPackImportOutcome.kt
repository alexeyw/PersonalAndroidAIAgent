package app.knotwork.android.domain.models

import app.knotwork.android.domain.promptpack.FrontmatterParseResult

/**
 * Outcome of reading a **prompt pack** — the markdown-with-frontmatter file
 * form of a [PromptPreset].
 *
 * Deliberately separate from [PromptPresetImportOutcome], which describes the
 * JSON form used by the bundled assets under `assets/presets/prompts`. The
 * two formats answer to different readers (a curated asset the build controls
 * versus a file a stranger may have written), and the file form needs two
 * shapes the asset form does not: a refusal that is reported rather than
 * silent, and a per-cause failure the UI can put one sentence to.
 */
sealed class PromptPackImportOutcome {

    /**
     * The file parsed cleanly, declared nothing it is not allowed to declare,
     * and carried no keys this build does not understand.
     *
     * @property preset The prompt ready to be persisted as a user preset.
     */
    data class Success(val preset: PromptPackCandidate) : PromptPackImportOutcome()

    /**
     * The file produced a usable prompt, but something about it has to be
     * said out loud before it is saved: a schema version this build does not
     * know, keys it does not recognise, or — the load-bearing case — a
     * request for a capability a prompt is not allowed to grant.
     *
     * The prompt itself is always complete: a prompt pack carries text, and
     * the text is exactly what survived.
     *
     * @property preset The prompt ready to be persisted.
     * @property notes What the reader had to leave out, and why.
     */
    data class Partial(val preset: PromptPackCandidate, val notes: PromptPackImportNotes) : PromptPackImportOutcome()

    /**
     * Nothing was imported. [cause] names which of the recognised failures
     * happened so the UI can state it in one sentence instead of calling the
     * file invalid.
     */
    data class Failure(val cause: PromptPackParseError) : PromptPackImportOutcome()
}

/**
 * A prompt read out of a file, before it is reconciled with what is already
 * in the library.
 *
 * Kept apart from [PromptPreset] itself because two of that model's fields
 * are decided by the *importer*, not by the file: `isBundled` is always
 * `false` for anything a user imports (a file may not smuggle itself into
 * the read-only catalogue), and the final `id` may have to change when it
 * collides with something already saved.
 *
 * @property id Identifier as read, or derived from the file name when the
 *   frontmatter omits one.
 * @property name Display name, exactly as written in the file.
 * @property description Human-readable summary; empty when the file omits it.
 * @property nodeType The node type this prompt applies to. Always one of
 *   `PromptPresetConstants.LLM_DRIVEN_NODE_TYPES` — the reader refuses
 *   anything else before a candidate is ever built.
 * @property systemPrompt The prompt body: everything after the frontmatter.
 * @property tags Free-form labels used by the picker's tag filter.
 */
data class PromptPackCandidate(
    val id: String,
    val name: String,
    val description: String,
    val nodeType: NodeType,
    val systemPrompt: String,
    val tags: List<String> = emptyList(),
) {
    /**
     * Materialises this candidate as a user preset.
     *
     * @param id Final identifier, which the caller may have re-keyed to
     *   avoid a collision.
     * @return A [PromptPreset] with `isBundled = false`. There is no
     *   overload that produces a bundled preset: an imported file must never
     *   be able to claim the read-only catalogue, which is the tier the user
     *   cannot edit or delete.
     */
    fun toUserPreset(id: String = this.id): PromptPreset = PromptPreset(
        id = id,
        name = name,
        description = description,
        nodeType = nodeType,
        systemPrompt = systemPrompt,
        tags = tags,
        isBundled = false,
    )
}

/**
 * Everything a prompt pack asked for that did not make it into the library.
 *
 * @property versionMismatch Non-null when the file declared a `schemaVersion`
 *   this build does not emit.
 * @property refused Capabilities the file asked for that a prompt cannot
 *   grant. Never honoured, always reported.
 * @property droppedKeys Frontmatter keys this build does not understand,
 *   in document order. Interop keys that other runtimes emit and we
 *   deliberately tolerate (see `PromptPackMarkdownSerializer.TOLERATED_KEYS`)
 *   are not listed here — they are expected, not lost.
 */
data class PromptPackImportNotes(
    val versionMismatch: PromptPackVersionMismatch? = null,
    val refused: List<RefusedCapability> = emptyList(),
    val droppedKeys: List<String> = emptyList(),
) {
    /** `true` when there is nothing at all to tell the user. */
    val isEmpty: Boolean
        get() = versionMismatch == null && refused.isEmpty() && droppedKeys.isEmpty()

    /**
     * `true` when the file asked for a capability.
     *
     * Drives which message the user sees: a refusal outranks a version
     * mismatch, because "this file wanted to add tools" is the more
     * important sentence and showing two dialogs for one import is worse
     * than folding the lesser note into the greater one.
     */
    val hasRefusal: Boolean
        get() = refused.isNotEmpty()
}

/**
 * A `schemaVersion` in the file that this build does not emit.
 *
 * @property foundVersion The version declared by the file.
 * @property expectedVersion The version this build writes.
 */
data class PromptPackVersionMismatch(val foundVersion: Int, val expectedVersion: Int)

/**
 * A capability a prompt pack asked for and did not get.
 *
 * This is the reported half of the format's central invariant: **a prompt
 * pack supplies wording and nothing else**. It cannot add tools, cannot add
 * nodes, and cannot carry scripts. A file that asks is imported as text, and
 * the request is named rather than dropped in silence — a refusal the user
 * never sees is indistinguishable from a refusal that did not happen.
 *
 * @property kind Which capability was asked for.
 * @property values Sanitised detail, taken verbatim from the file and then
 *   stripped of control characters, collapsed onto one line, length-clamped
 *   and capped in count — the text is attacker-supplied, and a dialog that
 *   echoes it unbounded is a dialog a file can forge. They are **not**
 *   resolved against the tool catalogue: this app identifies a tool by the
 *   same machine name a file would write (`$TOOLS` renders `name —
 *   description`), so there is no friendlier name to resolve to. For
 *   [Kind.STEPS] and [Kind.SCRIPTS] the list holds the requested items,
 *   which the UI renders as a count.
 */
data class RefusedCapability(val kind: Kind, val values: List<String>) {

    /** The capability families a prompt pack is refused. */
    enum class Kind {
        /** Tool access: `allowed-tools`, `allowedTools`, `tools`, `mcp`, `permissions`. */
        TOOLS,

        /** Graph structure: `nodes`, `steps`, `pipeline`. */
        STEPS,

        /** Executable content: `scripts`. */
        SCRIPTS,
    }
}

/**
 * Why a prompt pack could not be read at all.
 *
 * One cause per sentence the UI shows. "Invalid file" is not a cause — every
 * member here tells the user something they can act on.
 */
sealed class PromptPackParseError {

    /**
     * The frontmatter block itself is broken.
     *
     * @property reason Which way it was broken, from the parser.
     */
    data class MalformedFrontmatter(val reason: FrontmatterParseResult.Reason) : PromptPackParseError()

    /**
     * The frontmatter parsed but a key the format requires is absent.
     *
     * @property key The missing key (`name` or `nodeType`).
     */
    data class MissingRequiredKey(val key: String) : PromptPackParseError()

    /**
     * There is no prompt below the frontmatter, so there is nothing to save.
     *
     * Its own cause rather than a [MissingRequiredKey]: the body is not a
     * key, and "the file has a name but no prompt text" is the one sentence
     * that explains it.
     */
    data object MissingPromptText : PromptPackParseError()

    /**
     * The declared node type is not a type this app has.
     *
     * @property value The raw value from the file, echoed back so the user
     *   can see what was written. Sanitised before display.
     */
    data class UnknownNodeType(val value: String) : PromptPackParseError()

    /**
     * The declared node type exists but never feeds a user-authored prompt
     * to a model, so a prompt cannot belong to it.
     *
     * @property nodeType The offending type.
     */
    data class NonLlmNodeType(val nodeType: NodeType) : PromptPackParseError()

    /**
     * The display name exceeds `PromptPresetConstants.MAX_NAME_LENGTH`.
     *
     * A failure rather than a silent clamp: the ceiling is the same one the
     * in-app editor enforces, and quietly shortening someone's name is a
     * change they did not ask for and cannot see.
     *
     * @property limit The maximum length, so the message can state it.
     */
    data class NameTooLong(val limit: Int) : PromptPackParseError()

    /**
     * The prompt body exceeds `PromptPresetConstants.MAX_SYSTEM_PROMPT_LENGTH`.
     *
     * Truncating here would be worse than refusing: the body *is* the
     * artefact, and a prompt cut off mid-instruction still looks like a
     * prompt.
     *
     * @property limit The maximum length, so the message can state it.
     */
    data class PromptTooLong(val limit: Int) : PromptPackParseError()
}
