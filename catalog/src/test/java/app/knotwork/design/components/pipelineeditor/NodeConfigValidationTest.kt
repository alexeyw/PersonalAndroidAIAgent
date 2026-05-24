package app.knotwork.design.components.pipelineeditor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for [NodeConfigValidation] — covers happy path + every failure
 * mode listed in `node-specs.md` §Validation rules.
 *
 * Uses Robolectric ([AndroidJUnit4]) because the JSON-Schema validator
 * calls `org.json.JSONObject`, which is an Android-platform class — pure
 * JVM unit tests have only the stub `android.jar` where its
 * constructor throws "Method not mocked". The Compose-free validator
 * itself does not need Compose's test rule, just the platform jar.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NodeConfigValidationTest {

    private val noPeers = emptySet<String>()

    @Test
    fun `given blank title when validateTitle then returns TITLE_EMPTY`() {
        assertEquals(ValidationFailure.TITLE_EMPTY, NodeConfigValidation.validateTitle("   ", noPeers))
    }

    @Test
    fun `given duplicate title when validateTitle then returns TITLE_DUPLICATE`() {
        assertEquals(
            ValidationFailure.TITLE_DUPLICATE,
            NodeConfigValidation.validateTitle("router", setOf("router")),
        )
    }

    @Test
    fun `given unique non-blank title when validateTitle then returns null`() {
        assertNull(NodeConfigValidation.validateTitle("fresh", setOf("other")))
    }

    @Test
    fun `given valid LiteRtConfig when validate then no errors`() {
        val errors = NodeConfigValidation.validate(
            config = LiteRtConfig(
                title = "node-a",
                modelId = "gemma-2b-it",
                systemPrompt = "Answer concisely.",
            ),
            peerTitles = noPeers,
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `given LiteRtConfig with out-of-range temperature when validate then OUT_OF_RANGE`() {
        val errors = NodeConfigValidation.validate(
            config = LiteRtConfig(
                title = "node-a",
                modelId = "gemma-2b-it",
                systemPrompt = "x",
                temperature = 5f,
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.OUT_OF_RANGE, errors[FieldId.TEMPERATURE])
    }

    @Test
    fun `given CloudConfig with blank model when validate then no MODEL error (model lives in Settings)`() {
        // Phase 22 / Task 14 review round 3: the per-node Model field was removed from
        // the Cloud sheet — model ids live once per provider in Settings → External
        // providers. The validator therefore no longer flags a blank `model`; the
        // executor falls back to the provider's configured model at runtime.
        val errors = NodeConfigValidation.validate(
            config = CloudConfig(title = "cloud-a", model = "", systemPrompt = "x"),
            peerTitles = noPeers,
        )

        assertEquals(null, errors[FieldId.MODEL])
    }

    @Test
    fun `given IntentRouterConfig with one class when validate then INTENT_CLASS_COUNT`() {
        val errors = NodeConfigValidation.validate(
            config = IntentRouterConfig(
                title = "router",
                classes = listOf(IntentClass(name = "simple")),
                classifierPrompt = "x",
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.INTENT_CLASS_COUNT, errors[FieldId.CLASSES])
    }

    @Test
    fun `given IntentRouterConfig with seven classes when validate then INTENT_CLASS_COUNT`() {
        val errors = NodeConfigValidation.validate(
            config = IntentRouterConfig(
                title = "router",
                classes = (1..7).map { IntentClass(name = "c$it") },
                classifierPrompt = "x",
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.INTENT_CLASS_COUNT, errors[FieldId.CLASSES])
    }

    @Test
    fun `given IfConditionConfig with blank labels when validate then REQUIRED on both`() {
        val errors = NodeConfigValidation.validate(
            config = IfConditionConfig(title = "branch", expression = "x", labelTrue = "", labelFalse = ""),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.REQUIRED, errors[FieldId.LABEL_TRUE])
        assertEquals(ValidationFailure.REQUIRED, errors[FieldId.LABEL_FALSE])
    }

    @Test
    fun `given ClarificationConfig with five quick replies when validate then OUT_OF_RANGE`() {
        val errors = NodeConfigValidation.validate(
            config = ClarificationConfig(
                title = "clarify",
                questionTemplate = "x",
                quickReplies = listOf("a", "b", "c", "d", "e"),
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.OUT_OF_RANGE, errors[FieldId.QUICK_REPLIES])
    }

    @Test
    fun `given ToolConfig with empty toolId when validate then REQUIRED`() {
        val errors = NodeConfigValidation.validate(
            config = ToolConfig(title = "tool", toolId = ""),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.REQUIRED, errors[FieldId.TOOL_ID])
    }

    @Test
    fun `given ToolConfig with blank argument key when validate then REQUIRED on mapping`() {
        val errors = NodeConfigValidation.validate(
            config = ToolConfig(
                title = "tool",
                toolId = "fs.write",
                argumentMapping = listOf(ToolArgument(name = "", expression = "x")),
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.REQUIRED, errors[FieldId.ARGUMENT_MAPPING])
    }

    @Test
    fun `given ToolConfig with duplicate argument keys when validate then KEY_DUPLICATE`() {
        val errors = NodeConfigValidation.validate(
            config = ToolConfig(
                title = "tool",
                toolId = "fs.write",
                argumentMapping = listOf(
                    ToolArgument(name = "path", expression = "a"),
                    ToolArgument(name = "path", expression = "b"),
                ),
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.KEY_DUPLICATE, errors[FieldId.ARGUMENT_MAPPING])
    }

    @Test
    fun `given ToolConfig with unique keys when validate then no ARGUMENT_MAPPING error`() {
        val errors = NodeConfigValidation.validate(
            config = ToolConfig(
                title = "tool",
                toolId = "fs.write",
                argumentMapping = listOf(
                    ToolArgument(name = "path", expression = "a"),
                    ToolArgument(name = "content", expression = "b"),
                ),
            ),
            peerTitles = noPeers,
        )

        assertNull(errors[FieldId.ARGUMENT_MAPPING])
    }

    @Test
    fun `given IntentRouterConfig with duplicate class names when validate then CLASS_NAME_DUPLICATE`() {
        val errors = NodeConfigValidation.validate(
            config = IntentRouterConfig(
                title = "router",
                classes = listOf(
                    IntentClass(name = "simple"),
                    IntentClass(name = "simple"),
                ),
                classifierPrompt = "x",
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.CLASS_NAME_DUPLICATE, errors[FieldId.CLASSES])
    }

    @Test
    fun `given DecompositionConfig with invalid maxSubtasks when validate then OUT_OF_RANGE`() {
        val errors = NodeConfigValidation.validate(
            config = DecompositionConfig(title = "decompose", planningPrompt = "x", maxSubtasks = 25),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.OUT_OF_RANGE, errors[FieldId.MAX_SUBTASKS])
    }

    @Test
    fun `given DecompositionConfig with invalid schemaJson when validate then INVALID_JSON`() {
        val errors = NodeConfigValidation.validate(
            config = DecompositionConfig(
                title = "decompose",
                planningPrompt = "x",
                outputSchemaJson = "not json",
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.INVALID_JSON, errors[FieldId.OUTPUT_SCHEMA_JSON])
    }

    @Test
    fun `given QueueProcessorConfig with parallelism nine when validate then PARALLELISM_RANGE`() {
        val errors = NodeConfigValidation.validate(
            config = QueueProcessorConfig(
                title = "queue",
                inputList = "items",
                parallelism = 9,
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.PARALLELISM_RANGE, errors[FieldId.PARALLELISM])
    }

    @Test
    fun `given EvaluationConfig with maxRetries six when validate then OUT_OF_RANGE`() {
        val errors = NodeConfigValidation.validate(
            config = EvaluationConfig(title = "eval", criteriaPrompt = "x", maxRetries = 6),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.OUT_OF_RANGE, errors[FieldId.MAX_RETRIES])
    }

    @Test
    fun `given SummaryConfig with CUSTOM format and no prompt when validate then REQUIRED`() {
        val errors = NodeConfigValidation.validate(
            config = SummaryConfig(
                title = "sum",
                format = SummaryFormat.CUSTOM,
                customPrompt = null,
                targetLengthChars = 600,
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.REQUIRED, errors[FieldId.CUSTOM_PROMPT])
    }

    @Test
    fun `given SummaryConfig with target length five when validate then OUT_OF_RANGE`() {
        val errors = NodeConfigValidation.validate(
            config = SummaryConfig(
                title = "sum",
                format = SummaryFormat.BULLETS,
                targetLengthChars = 5,
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.OUT_OF_RANGE, errors[FieldId.TARGET_LENGTH_CHARS])
    }

    @Test
    fun `given IntentRouterConfig with stale fallback class when validate then FALLBACK_NOT_IN_CLASSES`() {
        val errors = NodeConfigValidation.validate(
            config = IntentRouterConfig(
                title = "router",
                classes = listOf(
                    IntentClass(name = "simple"),
                    IntentClass(name = "complex"),
                ),
                classifierPrompt = "x",
                fallbackClass = "removed",
            ),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.FALLBACK_NOT_IN_CLASSES, errors[FieldId.FALLBACK_CLASS])
    }

    @Test
    fun `given IntentRouterConfig with resolved fallback class when validate then no fallback error`() {
        val errors = NodeConfigValidation.validate(
            config = IntentRouterConfig(
                title = "router",
                classes = listOf(
                    IntentClass(name = "simple"),
                    IntentClass(name = "complex"),
                ),
                classifierPrompt = "x",
                fallbackClass = "simple",
            ),
            peerTitles = noPeers,
        )

        assertNull(errors[FieldId.FALLBACK_CLASS])
    }

    @Test
    fun `given IntentRouterConfig with null fallback class when validate then no fallback error`() {
        val errors = NodeConfigValidation.validate(
            config = IntentRouterConfig(
                title = "router",
                classes = listOf(
                    IntentClass(name = "simple"),
                    IntentClass(name = "complex"),
                ),
                classifierPrompt = "x",
                fallbackClass = null,
            ),
            peerTitles = noPeers,
        )

        assertNull(errors[FieldId.FALLBACK_CLASS])
    }

    @Test
    fun `given InputConfig with invalid schemaJson when validate then INVALID_JSON`() {
        val errors = NodeConfigValidation.validate(
            config = InputConfig(title = "in", schemaJson = "not json"),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.INVALID_JSON, errors[FieldId.SCHEMA_JSON])
    }
}
