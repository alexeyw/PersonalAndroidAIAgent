package ai.agent.android.domain.engine

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.ToolInvocationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the textual input that [GraphExecutionEngine] feeds into a node's
 * executor by concatenating only the context blocks the node opted into via
 * its [NodeContextConfig].
 *
 * The builder is the single source of truth for context layout: every block
 * uses the same delimiter style (`--- <Block Name> ---`) and the order is
 * fixed regardless of which subset of flags is enabled. The deterministic
 * order matters because:
 *
 *  1. Prompt caches downstream (Anthropic, OpenAI, on-device LiteRT) hash a
 *     prefix of the prompt; reordering blocks would invalidate the cache.
 *  2. LLMs are sensitive to position effects — keeping the user-facing
 *     payload (`Previous Node Output`) at the end mirrors a typical
 *     "system → context → user message" arrangement.
 *
 * Block order (top to bottom):
 *
 *  1. **Original Task** — the immutable user message that started the run.
 *     Sets the goal up-front so the LLM does not lose sight of it after
 *     several intermediate transformations.
 *  2. **Chat History** — prior session messages (numbered).
 *  3. **Long-Term Memory** — semantically retrieved memory chunks (numbered).
 *  4. **Tool Results** — outputs from tool invocations earlier in this run.
 *  5. **Previous Node Output** — the immediate predecessor's payload, the
 *     thing the current node is expected to act on.
 *
 * Empty data blocks (e.g. `chatHistory = true` but the session has no
 * messages yet) are dropped entirely — emitting a header without content is
 * pure noise for the model. If every enabled block is empty (or no flags are
 * enabled at all) the builder returns an empty string; callers must decide
 * how to handle that, since the ban on fully-empty contexts is enforced at
 * the validation layer (see Phase 15 task 3/6).
 */
@Singleton
class NodeContextBuilder @Inject constructor() {

    /**
     * Renders the assembled context string for a single node execution.
     *
     * @param config The node's per-node selection of enabled context blocks.
     * @param ctx Snapshot of pipeline-scoped data captured by the engine just
     * before the node fires.
     * @return The concatenated context string, with blocks separated by a
     * blank line. May be empty if no enabled flag yields any content.
     */
    fun build(config: NodeContextConfig, ctx: PipelineExecutionContext): String {
        val blocks = mutableListOf<String>()

        if (config.originalTask && ctx.originalUserMessage.isNotBlank()) {
            blocks += renderBlock(HEADER_ORIGINAL_TASK, ctx.originalUserMessage.trim())
        }

        if (config.chatHistory && ctx.chatHistory.isNotEmpty()) {
            blocks += renderBlock(HEADER_CHAT_HISTORY, formatChatHistory(ctx.chatHistory))
        }

        if (config.longTermMemory && ctx.memoryEntries.isNotEmpty()) {
            blocks += renderBlock(HEADER_LONG_TERM_MEMORY, formatMemory(ctx.memoryEntries))
        }

        if (config.toolResults && ctx.toolResults.isNotEmpty()) {
            blocks += renderBlock(HEADER_TOOL_RESULTS, formatToolResults(ctx.toolResults))
        }

        if (config.nodeInput && ctx.previousNodeOutput.isNotBlank()) {
            blocks += renderBlock(HEADER_PREVIOUS_NODE_OUTPUT, ctx.previousNodeOutput.trim())
        }

        return blocks.joinToString(BLOCK_SEPARATOR)
    }

    private fun renderBlock(header: String, body: String): String = "$header\n$body"

    private fun formatChatHistory(messages: List<ChatMessage>): String = messages.mapIndexed { index, message ->
        "${index + 1}. ${message.role.name}: ${message.content}"
    }.joinToString("\n")

    private fun formatMemory(entries: List<MemoryChunk>): String =
        entries.mapIndexed { index, chunk -> "${index + 1}. ${chunk.text}" }.joinToString("\n")

    private fun formatToolResults(results: List<ToolInvocationResult>): String = results.mapIndexed { index, result ->
        "${index + 1}. ${result.toolName}: ${result.output}"
    }.joinToString("\n")

    private companion object {
        private const val HEADER_ORIGINAL_TASK = "--- Original Task ---"
        private const val HEADER_CHAT_HISTORY = "--- Chat History ---"
        private const val HEADER_LONG_TERM_MEMORY = "--- Long-Term Memory ---"
        private const val HEADER_TOOL_RESULTS = "--- Tool Results ---"
        private const val HEADER_PREVIOUS_NODE_OUTPUT = "--- Previous Node Output ---"
        private const val BLOCK_SEPARATOR = "\n\n"
    }
}
