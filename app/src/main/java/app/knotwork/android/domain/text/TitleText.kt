package app.knotwork.android.domain.text

/** A run of one or more whitespace characters (spaces, tabs, line breaks). */
private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Collapses every run of whitespace (spaces, tabs, newlines) into a single space
 * and trims the ends, turning arbitrary multi-line text into one clean line.
 *
 * @return the whitespace-collapsed, trimmed string.
 */
fun String.collapseWhitespace(): String = trim().replace(WHITESPACE_RUN, " ")

/**
 * Builds a single-line title from arbitrary text: [collapseWhitespace] first, then
 * truncate to [maxLength] characters (trimming a trailing space left by the cut)
 * with [ellipsis] appended when the text overflows.
 *
 * Shared by the chat auto-rename and the share-target session naming so the two
 * cannot drift; each caller passes its own [maxLength] / [ellipsis].
 *
 * @param maxLength Maximum characters kept before the ellipsis. Must be positive.
 * @param ellipsis Suffix appended only when the collapsed text exceeds [maxLength].
 * @return the collapsed, length-capped title (empty when the input is blank).
 */
fun String.toSingleLineTitle(maxLength: Int, ellipsis: String): String {
    val normalized = collapseWhitespace()
    return if (normalized.length > maxLength) {
        normalized.take(maxLength).trimEnd() + ellipsis
    } else {
        normalized
    }
}
