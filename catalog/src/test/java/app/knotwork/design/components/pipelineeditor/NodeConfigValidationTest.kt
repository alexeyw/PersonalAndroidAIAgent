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
    fun `given LiteRtConfig with out-of-range sampling when validate then no error (fields have no control)`() {
        // ADR 0005 removed temperature / topP / maxNewTokens from the LITE_RT
        // sheet. Validating them anyway blocked Save on a field the user cannot
        // see: an imported pipeline carrying any out-of-range value produced an
        // error with nothing on screen to correct. The values still round-trip
        // on `LiteRtConfig`, and nothing reads them during a run.
        val errors = NodeConfigValidation.validate(
            config = LiteRtConfig(
                title = "node-a",
                modelId = "gemma-2b-it",
                systemPrompt = "x",
                temperature = 5f,
                topP = 9f,
                maxNewTokens = 999_999,
            ),
            peerTitles = noPeers,
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `given CloudConfig with out-of-range sampling when validate then no error (fields have no control)`() {
        // Same reason as the LITE_RT case above, and as the blank-model case
        // below: the Cloud sheet offers neither temperature, max tokens nor
        // timeout since ADR 0005.
        val errors = NodeConfigValidation.validate(
            config = CloudConfig(
                title = "cloud-a",
                systemPrompt = "x",
                temperature = 5f,
                maxTokens = 999_999,
                timeoutMs = 1,
            ),
            peerTitles = noPeers,
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `given CloudConfig with blank model when validate then no MODEL error (model lives in Settings)`() {
        // The per-node Model field was removed from
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
    fun `given IfConditionConfig with image branch and blank expression when validate then no expression error`() {
        val errors = NodeConfigValidation.validate(
            config = IfConditionConfig(title = "branch", expression = "", branchOnImage = true),
            peerTitles = noPeers,
        )

        assertEquals(null, errors[FieldId.EXPRESSION])
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
    fun `given ToolConfig with empty toolId (Auto) when validate then no TOOL_ID error`() {
        // A blank toolId is the "Auto" selection — the agent picks the tool at
        // run time — so it is valid and must not block Save.
        val errors = NodeConfigValidation.validate(
            config = ToolConfig(title = "tool", toolId = ""),
            peerTitles = noPeers,
        )

        assertNull(errors[FieldId.TOOL_ID])
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
    fun `given EvaluationConfig with maxRetries six when validate then OUT_OF_RANGE`() {
        val errors = NodeConfigValidation.validate(
            config = EvaluationConfig(title = "eval", criteriaPrompt = "x", maxRetries = 6),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.OUT_OF_RANGE, errors[FieldId.MAX_RETRIES])
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
    fun `given PipelineConfig with no target when validate then TARGET_PIPELINE_MISSING`() {
        val errors = NodeConfigValidation.validate(
            config = PipelineConfig(title = "run sub", targetPipelineId = ""),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.TARGET_PIPELINE_MISSING, errors[FieldId.TARGET_PIPELINE_ID])
    }

    @Test
    fun `given PipelineConfig with a target when validate then no target error`() {
        val errors = NodeConfigValidation.validate(
            config = PipelineConfig(title = "run sub", targetPipelineId = "target-1"),
            peerTitles = noPeers,
        )

        assertEquals(null, errors[FieldId.TARGET_PIPELINE_ID])
    }

    @Test
    fun `given SkillConfig with no skill when validate then TARGET_SKILL_MISSING`() {
        val errors = NodeConfigValidation.validate(
            config = SkillConfig(title = "translate", skillId = ""),
            peerTitles = noPeers,
        )

        assertEquals(ValidationFailure.TARGET_SKILL_MISSING, errors[FieldId.SKILL_ID])
    }

    @Test
    fun `given SkillConfig with a skill when validate then no skill error`() {
        val errors = NodeConfigValidation.validate(
            config = SkillConfig(title = "translate", skillId = "skill-1"),
            peerTitles = noPeers,
        )

        assertEquals(null, errors[FieldId.SKILL_ID])
    }

    @Test
    fun `given IfConditionConfig with blank keywords and no threshold when validate then no errors`() {
        // Both deterministic checks are opt-in: blank keywords and a null
        // threshold mean "do not check", which is the default state of every
        // IF_CONDITION node and must never block Save.
        val errors = NodeConfigValidation.validate(
            IfConditionConfig(title = "Branch", expression = "is it a question?"),
            peerTitles = emptySet(),
        )

        assertNull(errors[FieldId.KEYWORDS])
        assertNull(errors[FieldId.COMPLEXITY_THRESHOLD])
    }

    @Test
    fun `given IfConditionConfig with a non-positive threshold when validate then OUT_OF_RANGE`() {
        // The slider maps 0 to null on the way out, so a zero arriving here came
        // from an imported file rather than the sheet. It is refused because the
        // engine reads `threshold > 0` and would silently ignore it.
        val errors = NodeConfigValidation.validate(
            IfConditionConfig(title = "Branch", expression = "long?", complexityThreshold = 0),
            peerTitles = emptySet(),
        )

        assertEquals(ValidationFailure.OUT_OF_RANGE, errors[FieldId.COMPLEXITY_THRESHOLD])
    }

    @Test
    fun `given QueueProcessorConfig when validate then nothing beyond the title can fail`() {
        // The queue comes from upstream and the only field is a toggle, so the
        // sheet has no way to be invalid. Asserted rather than assumed: this is
        // what makes the empty verdict a decision instead of an omission.
        val errors = NodeConfigValidation.validate(
            QueueProcessorConfig(title = "Each subtask", stopOnError = false),
            peerTitles = emptySet(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `given SummaryConfig with no custom prompt when validate then no error`() {
        // Blank leaves the built-in summarisation prompt in place. It used to be
        // required whenever a `format` chip said CUSTOM — a rule enforced by a
        // control that decided nothing.
        val errors = NodeConfigValidation.validate(
            SummaryConfig(title = "Summarise"),
            peerTitles = emptySet(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `given InputConfig when validate then nothing beyond the title can fail`() {
        val errors = NodeConfigValidation.validate(InputConfig(title = "Start"), peerTitles = emptySet())

        assertTrue(errors.isEmpty())
    }
}
