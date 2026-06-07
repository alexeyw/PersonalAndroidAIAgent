package app.knotwork.android.domain.models

/**
 * Transport protocol used to talk to a remote MCP server. Both variants
 * are end-to-end wired today:
 *
 *  - [SSE] — classic server-sent events through Koog's
 *    `defaultSseTransport`.
 *  - [STREAMABLE_HTTP] — the post-2025-03-26 spec replacement; runs
 *    through the upstream MCP Kotlin SDK's
 *    `HttpClient.mcpStreamableHttpTransport` extension (POST for
 *    outbound, SSE channel for inbound).
 */
enum class McpTransport(
    /** Stable wire identifier persisted in DataStore; never localise this. */
    val wireId: String,
) {
    /** Server-Sent Events transport. */
    SSE(wireId = "sse"),

    /** MCP Streamable HTTP transport (spec 2025-03-26). */
    STREAMABLE_HTTP(wireId = "streamable_http"),
    ;

    /** Parsing helpers tied to the persisted [wireId] format. */
    companion object {
        /** Parses a persisted [wireId] back to the enum; falls back to [SSE]. */
        fun fromWireId(id: String?): McpTransport = entries.firstOrNull { it.wireId == id } ?: SSE
    }
}
