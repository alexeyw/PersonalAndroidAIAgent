package ai.agent.android.data.local

/**
 * Single codec for the comma-separated `tagsCsv` column shared by every
 * tag-bearing entity (`MemoryChunkEntity`, `PromptPresetEntity`,
 * `PipelinePresetEntity`). Centralising it keeps the separator and
 * blank-handling rules in one place instead of forking them per repository.
 */
object TagsCsv {

    private const val SEPARATOR = ","

    /** Encodes a tag list to CSV, trimming each tag and dropping blanks. */
    fun encode(tags: List<String>): String =
        tags.mapNotNull { it.trim().ifEmpty { null } }.joinToString(separator = SEPARATOR)

    /** Decodes a `tagsCsv` value back into a tag list, trimming and dropping blanks. */
    fun decode(csv: String): List<String> = csv.split(SEPARATOR).mapNotNull { it.trim().ifEmpty { null } }
}
