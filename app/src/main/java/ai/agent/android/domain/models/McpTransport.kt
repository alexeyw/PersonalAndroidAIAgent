package ai.agent.android.domain.models

/**
 * Transport protocol used to talk to a remote MCP server.
 *
 * The Android client today wires only [SSE] end-to-end (via Koog 0.8's
 * `defaultSseTransport`). [STREAMABLE_HTTP] is the post-2025-03-26 spec
 * replacement for SSE; we capture the user's intent so the persisted
 * config carries it forward, but the underlying client falls back to
 * SSE until Koog ships a streamable-HTTP transport.
 */
enum class McpTransport(
    /** Stable wire identifier persisted in DataStore; never localise this. */
    val wireId: String,
) {
    /** Server-Sent Events transport — fully wired through Koog 0.8. */
    SSE(wireId = "sse"),

    /**
     * MCP Streamable HTTP transport (spec 2025-03-26). The persisted intent
     * survives; the runtime falls back to SSE until Koog ships native
     * Streamable HTTP support.
     */
    STREAMABLE_HTTP(wireId = "streamable_http"),
    ;

    /** Parsing helpers tied to the persisted [wireId] format. */
    companion object {
        /** Parses a persisted [wireId] back to the enum; falls back to [SSE]. */
        fun fromWireId(id: String?): McpTransport = entries.firstOrNull { it.wireId == id } ?: SSE
    }
}
