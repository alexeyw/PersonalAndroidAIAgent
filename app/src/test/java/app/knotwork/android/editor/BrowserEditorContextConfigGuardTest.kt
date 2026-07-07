package app.knotwork.android.editor

import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drift guard for the browser pipeline editor's hand-maintained
 * `defaultContextConfig(typeId)` JS function in `pipeline-editor.html`.
 *
 * That function mirrors [NodeContextConfig.defaultForType] but lives outside the
 * `AUTO-GEN` blocks, so `verifyBrowserEditorConstants` does not cover it. This
 * test parses the JS `switch` and asserts every **explicitly-cased** node type
 * resolves to the same per-type context default as the domain. Node types that
 * intentionally fall through to the JS `default` branch (see [UNCASED_TYPES]) are
 * excluded — the browser deliberately seeds them with all blocks enabled.
 */
class BrowserEditorContextConfigGuardTest {

    @Test
    fun `browser defaultContextConfig matches the domain default for every cased node type`() {
        val body = extractSwitchBody(editorHtml())
        val cased = parseCases(body)

        // Sanity: the parse found the expected core cases, so a silent regex miss
        // cannot make the test vacuously pass.
        assertTrue("Parsed too few cases from defaultContextConfig: ${cased.keys}", cased.size >= 8)

        NodeType.values().forEach { type ->
            if (type.name in UNCASED_TYPES) return@forEach
            val flags = cased[type.name]
                ?: error("${type.name} missing from defaultContextConfig() — add a case or list it in UNCASED_TYPES")
            assertEquals(
                "Browser context default drifted for ${type.name}",
                NodeContextConfig.defaultForType(type),
                flags,
            )
        }
    }

    private fun parseCases(switchBody: String): Map<String, NodeContextConfig> {
        // Group consecutive `case 'X':` labels that share one `return flags(...)`.
        val result = mutableMapOf<String, NodeContextConfig>()
        val pendingIds = mutableListOf<String>()
        switchBody.lineSequence().forEach { raw ->
            val line = raw.trim()
            CASE_REGEX.find(line)?.let {
                pendingIds += it.groupValues[1]
                return@forEach
            }
            FLAGS_REGEX.find(line)?.let { m ->
                val (chat, orig, mem, tool) = m.destructured
                val config = NodeContextConfig(
                    chatHistory = chat.toBoolean(),
                    originalTask = orig.toBoolean(),
                    nodeInput = true, // JS `flags` always sets nodeInput = true
                    longTermMemory = mem.toBoolean(),
                    toolResults = tool.toBoolean(),
                )
                pendingIds.forEach { result[it] = config }
                pendingIds.clear()
            }
        }
        return result
    }

    private fun extractSwitchBody(html: String): String {
        val start = html.indexOf("function defaultContextConfig")
        require(start >= 0) { "defaultContextConfig not found in pipeline-editor.html" }
        val switchAt = html.indexOf("switch (typeId)", start)
        val defaultAt = html.indexOf("default:", switchAt)
        require(switchAt in 0 until defaultAt) { "Could not locate the defaultContextConfig switch body" }
        return html.substring(switchAt, defaultAt)
    }

    private fun editorHtml(): String {
        // Walk up from the test's working directory (module dir under Gradle) to the
        // repo root that holds pipeline-editor.html.
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "pipeline-editor.html")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("pipeline-editor.html not found walking up from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val CASE_REGEX = Regex("""case '([A-Z_]+)':""")
        val FLAGS_REGEX =
            Regex("""return flags\(\s*(true|false)\s*,\s*(true|false)\s*,\s*(true|false)\s*,\s*(true|false)\s*\)""")

        /** Types deliberately left to the JS `default` (all-enabled) branch. */
        val UNCASED_TYPES = setOf(NodeType.SKILL.name, NodeType.PIPELINE.name)
    }
}
