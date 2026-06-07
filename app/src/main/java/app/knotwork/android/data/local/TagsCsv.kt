package app.knotwork.android.data.local

/**
 * Single codec for the comma-separated `tagsCsv` column shared by every
 * tag-bearing entity (`MemoryChunkEntity`, `PromptPresetEntity`,
 * `PipelinePresetEntity`). Centralising it keeps the separator and
 * blank-handling rules in one place instead of forking them per repository.
 */
object TagsCsv {

    private const val SEPARATOR = ","

    /**
     * Encodes a tag list to CSV. Each tag is trimmed, blanks are dropped, and —
     * since the format is unescaped — any literal separator inside a tag is
     * replaced with a space so encode/decode round-trips without silently
     * splitting one tag into several (e.g. a user-typed `"sci-fi, fantasy"`
     * becomes the single tag `"sci-fi fantasy"` rather than two tags).
     */
    fun encode(tags: List<String>): String = tags
        // Replace any embedded separator with a space, then collapse whitespace
        // so a comma+space (", ") doesn't leave a double space in the tag.
        .map { tag -> tag.replace(SEPARATOR, " ").split(" ").filter(String::isNotEmpty).joinToString(" ") }
        .filter { it.isNotEmpty() }
        .joinToString(separator = SEPARATOR)

    /** Decodes a `tagsCsv` value back into a tag list, trimming and dropping blanks. */
    fun decode(csv: String): List<String> = csv.split(SEPARATOR).mapNotNull { it.trim().ifEmpty { null } }
}
