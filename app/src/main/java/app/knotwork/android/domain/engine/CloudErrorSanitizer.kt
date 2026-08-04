package app.knotwork.android.domain.engine

/**
 * Strips credentials out of cloud-provider error text before it is shown, logged, or
 * persisted.
 *
 * Provider errors quote the request that failed, and not every provider keeps the
 * credential in a header. Google authenticates with a query parameter, so a plain
 * transport failure arrives already carrying the key:
 *
 * ```
 * Socket timeout has expired [url=https://…:streamGenerateContent?alt=sse&key=AIza…]
 * ```
 *
 * That string is the node's error, and from there it reaches the run console, the chat
 * error banner, the persisted run trace and logcat. API keys live in the Keystore-backed
 * store precisely so they never appear in any of those places, so the text is scrubbed at
 * the point where a provider exception becomes a message.
 *
 * The redaction is deliberately conservative: it matches the credential-bearing shapes
 * (secret-named query parameters and `Bearer` tokens) rather than trying to recognise any
 * particular provider's key format, which would silently miss the next provider added.
 */
object CloudErrorSanitizer {

    /** Placeholder left in place of a removed secret. */
    private const val MASK = "***"

    /** Fallback for a provider exception that carried no message at all. */
    private const val UNKNOWN = "Unknown error"

    /**
     * Query-parameter names whose values are credentials. Matched case-insensitively; the
     * value runs to the next separator (`&`, whitespace, `]`, `)`, `"`, `'`) or end of text.
     */
    private val SECRET_QUERY_PARAM = Regex(
        """\b(key|api[-_]?key|access[-_]?token|auth[-_]?token|token|password)=([^&\s\]\)"']+)""",
        RegexOption.IGNORE_CASE,
    )

    /** `Authorization: Bearer <token>` and bare `Bearer <token>` fragments. */
    private val BEARER_TOKEN = Regex("""\bBearer\s+\S+""", RegexOption.IGNORE_CASE)

    /**
     * Returns [message] with any embedded credential replaced by [MASK].
     *
     * @param message Raw provider/transport error text, possibly `null`.
     * @return Text safe to surface to the user and to persist, never `null`.
     */
    fun sanitize(message: String?): String {
        val raw = message?.takeIf { it.isNotBlank() } ?: return UNKNOWN
        return raw
            .replace(SECRET_QUERY_PARAM) { match -> "${match.groupValues[1]}=$MASK" }
            .replace(BEARER_TOKEN, "Bearer $MASK")
    }
}
