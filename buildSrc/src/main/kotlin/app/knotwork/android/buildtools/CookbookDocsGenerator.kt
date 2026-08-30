package app.knotwork.android.buildtools

/**
 * Derives the node-type reference of `docs/cookbook.md` from the Kotlin sources
 * that actually define a node, and injects it between dedicated `AUTO-GEN`
 * markers.
 *
 * **Why generated rather than written.** Until this file existed the only
 * per-node reference in the repository was `node-specs.md`, an internal design
 * document the public guide nevertheless pointed readers at — a reference no
 * reader outside the repository could open. Writing the same table by hand into
 * a public document would have reproduced the failure mode `FILE_MAP.md` already
 * demonstrated: the first node type added after publication silently makes the
 * document wrong, and nothing says so.
 *
 * So the reference quotes the sources instead:
 *
 *  - **the vocabulary** — the `NodeType` enum of `:app` (`domain/models`), with
 *    the `:catalog` mirror parsed as an independent second opinion;
 *  - **the ports** — `NodePorts.forType`, which its own KDoc calls the single
 *    source of truth for the canvas, the edge labels and the validator;
 *  - **the context defaults** — `NodeContextConfig.defaultForType` plus the
 *    `CONTEXT_AWARE_NODE_TYPES` set that decides whether the configuration is
 *    consulted at run time at all;
 *  - **the configuration fields and their defaults** — the `NodeConfig` sealed
 *    hierarchy of `:catalog`, one data class per type;
 *  - **whether a fresh node carries a system prompt** —
 *    `DefaultPrompts.getDefaultPromptForNodeType`.
 *
 * **The one thing the sources cannot supply** is what a node is *for* in a
 * sentence a reader who is not a contributor can use: the domain KDoc is written
 * for the person implementing an executor ("Node representing a local LiteRT-LM
 * instance"). Those sentences, and the per-field run-time verdicts, live in
 * [NODE_DOC_META] and [FIELD_REACH] here. They are hand-written but not
 * unguarded: every node type must appear in the meta and every parsed field must
 * carry a verdict, so adding either without a decision fails generation rather
 * than quietly rendering a shorter table.
 *
 * **The failure mode this guards against.** A doc generator paired with its own
 * drift check can delete a real row and stay green — the verify task compares a
 * shortened block against a freshly shortened render and agrees with itself.
 * Every parse step here therefore counts, and the counts are cross-checked
 * against *different files read by different parsers*: the node set derived from
 * `:app`'s enum must equal the one derived from `:catalog`'s enum, from the
 * ports factory, from the context-defaults `when`, and from the config
 * hierarchy. Five independent walks over four files in two modules have to agree
 * before a table is emitted.
 *
 * The run-time verdicts carry a second guard that deliberately does not live
 * here: `CookbookRuntimeReachTest` in `:app` exercises the real
 * `NodeConfigCodec` and compares what it observes against the committed table,
 * so a verdict claiming a field reaches the engine when it does not fails the
 * unit suite rather than this build task.
 *
 * The entry points are [render] (pure transform used by both Gradle tasks) and
 * [drift] (per-block comparison used by the verify task).
 */
object CookbookDocsGenerator {

    /** Thrown when a source cannot be parsed, or the parsed sets do not line up. */
    class GenerationException(message: String) : RuntimeException(message)

    /** The overview table: one row per node type. */
    const val BLOCK_NODE_REFERENCE: String = "NODE_REFERENCE"

    /** The per-type sections: ports, context, and what drives the node. */
    const val BLOCK_NODE_CONFIG: String = "NODE_CONFIG"

    /** The closing appendix: every configuration field, its default and its verdict. */
    const val BLOCK_FIELD_TABLE: String = "FIELD_TABLE"

    /**
     * Every Kotlin source the generated blocks are derived from.
     *
     * Grouped into one value rather than passed as six parameters so the Gradle
     * task's call site names each source where it reads the file, and so adding
     * a source later does not reshape two task actions.
     *
     * @property domainNodeType `:app` `domain/models/NodeType.kt` — the vocabulary.
     * @property catalogNodeType `:catalog` `pipelineeditor/NodeType.kt` — the
     *   editor-side mirror, parsed as an independent second opinion on the set.
     * @property nodePorts `:catalog` `pipelineeditor/NodePorts.kt` — port layout.
     * @property nodeContextConfig `:app` `domain/models/NodeContextConfig.kt` —
     *   per-type context defaults and the context-aware set.
     * @property nodeConfig `:catalog` `pipelineeditor/NodeConfig.kt` — the config
     *   data classes, their fields and their defaults.
     * @property defaultPrompts `:app` `domain/constants/DefaultPrompts.kt` —
     *   which types seed a `systemPrompt` on a freshly created node.
     */
    data class Sources(
        val domainNodeType: String,
        val catalogNodeType: String,
        val nodePorts: String,
        val nodeContextConfig: String,
        val nodeConfig: String,
        val defaultPrompts: String,
    )

    /**
     * Reader-facing metadata for one node type, in the order the cookbook
     * presents them.
     *
     * The order is the editor's palette order (entry and exit first, then the
     * inference nodes, then control flow, then post-processing), so a reader
     * scanning the document meets the nodes in the same order as the radial
     * quick-add menu.
     *
     * @property id Must equal a `NodeType` enum constant name.
     * @property label The name the editor shows on the node card.
     * @property summary One sentence, written for someone wiring a pipeline
     *   rather than someone implementing an executor.
     */
    data class NodeDoc(val id: String, val label: String, val summary: String)

    /**
     * The reader-facing sentence per node type. The *set* of ids must equal the
     * `NodeType` enum; a type added without a sentence fails generation.
     */
    val NODE_DOC_META: List<NodeDoc> = listOf(
        NodeDoc(
            id = "INPUT",
            label = "Input",
            summary = "Where the run starts: your message, a shared page, or the prompt a trigger carries. " +
                "Exactly one per pipeline.",
        ),
        NodeDoc(
            id = "OUTPUT",
            label = "Output",
            summary = "Where the run ends and the answer reaches you. With no prompt of its own it forwards " +
                "the previous node's text verbatim; give it one and it re-formats that text with one more " +
                "model pass. Exactly one per pipeline.",
        ),
        NodeDoc(
            id = "LITE_RT",
            label = "LiteRT",
            summary = "One inference step on the model running on the phone. The default answering node, and " +
                "the one to reach for unless a step genuinely needs a larger model.",
        ),
        NodeDoc(
            id = "CLOUD",
            label = "Cloud",
            summary = "One inference step against a configured cloud provider. Everything this node is given " +
                "leaves the device, so it is a node to place deliberately rather than by default.",
        ),
        NodeDoc(
            id = "INTENT_ROUTER",
            label = "Intent Router",
            summary = "Sorts the incoming text into one of the classes you declare and sends the run down the " +
                "matching branch — the way to give one pipeline several behaviours.",
        ),
        NodeDoc(
            id = "IF_CONDITION",
            label = "If Condition",
            summary = "A two-way branch: a yes/no question about the input, or a deterministic check for " +
                "whether the run carries an image.",
        ),
        NodeDoc(
            id = "CLARIFICATION",
            label = "Clarification",
            summary = "Pauses the run to ask you a question and waits for the answer, which then becomes the " +
                "node's output. The one node that deliberately stops mid-run.",
        ),
        NodeDoc(
            id = "TOOL",
            label = "Tool",
            summary = "Calls one tool — a built-in, an AppFunction from another app, or one published by an " +
                "MCP server — and passes the result on. Anything its risk level does not clear waits for " +
                "your approval.",
        ),
        NodeDoc(
            id = "DECOMPOSITION",
            label = "Decomposition",
            summary = "Turns one instruction into a list of subtasks. On its own it only produces the list; " +
                "pair it with a Queue Processor to work through it.",
        ),
        NodeDoc(
            id = "QUEUE_PROCESSOR",
            label = "Queue Processor",
            summary = "Walks a list of subtasks one at a time, sending each down the Item branch and taking " +
                "the Done branch when the list is empty. The only node that can send a run backwards.",
        ),
        NodeDoc(
            id = "EVALUATION",
            label = "Evaluation",
            summary = "Judges what the previous step produced and answers Pass, Retry or Fail — the node that " +
                "lets a pipeline have another go instead of handing you a bad answer.",
        ),
        NodeDoc(
            id = "SUMMARY",
            label = "Summary",
            summary = "Condenses what several earlier steps produced into one piece of text, typically just " +
                "before the Output node at the end of a loop.",
        ),
        NodeDoc(
            id = "PIPELINE",
            label = "Pipeline",
            summary = "Runs another saved pipeline as a single step and returns its answer — a function call " +
                "between pipelines, and the way to reuse a branch instead of copying it.",
        ),
        NodeDoc(
            id = "SKILL",
            label = "Skill",
            summary = "Runs a reusable skill: a fixed instruction plus the list of tools that skill may use. " +
                "The allowlist is enforced when a tool is called, not merely suggested in the prompt.",
        ),
    )

    /**
     * One thing that decides what a node does while a pipeline runs, and where
     * its value comes from.
     *
     * This is the table the previous shape of this document lacked, and its
     * absence was the reason the document confused rather than explained: the
     * per-type tables enumerated the **editor's form fields**, which is a
     * different set from the node's actual inputs. For four node types the two
     * sets barely overlap — the form shows a prompt box that goes nowhere while
     * the prompt the node really runs on appeared in no table at all.
     *
     * So the reference now leads with the node's inputs and says, for each,
     * which form field sets it — or that none does.
     *
     * @property decides What this input decides, in the reader's terms.
     * @property property The `NodeModel` property the engine reads.
     * @property setVia Where the value comes from.
     */
    data class RuntimeInput(val decides: String, val property: String, val setVia: SetVia)

    /** Where a [RuntimeInput]'s value comes from. */
    sealed interface SetVia {
        /**
         * A field on the node's configuration sheet writes it. Which field is
         * resolved from [FIELD_REACH] rather than named twice; generation fails
         * when no field writes the property.
         */
        data object Sheet : SetVia

        /**
         * Nothing on the sheet writes it.
         *
         * @property instruction What the reader can do instead. Generation
         *   fails if a sheet field turns out to write the property after all,
         *   so a control that gets wired up cannot leave this text behind.
         */
        data class NoControl(val instruction: String) : SetVia
    }

    /**
     * What happens to a configuration field once the sheet is saved.
     *
     * The distinction is the point of the table. A field the editor stores but
     * the engine never reads is not a setting — it is a control that does
     * nothing, and publishing it beside the ones that work is how a reference
     * document starts lying. [Graph] exists because two fields are neither: they
     * do not reach the engine as values, but they decide which outbound ports
     * the node has, and a run follows those.
     */
    sealed interface Reach {
        /**
         * The value is written onto the pipeline's stored node and read while
         * the pipeline runs.
         *
         * @property field The `NodeModel` property it lands on.
         */
        data class Runtime(val field: String) : Reach

        /**
         * The value shapes the node's outbound ports, and the run follows the
         * edges those ports carry.
         *
         * @property effect What the value decides about the graph.
         */
        data class Graph(val effect: String) : Reach

        /**
         * The value is stored in the node's saved configuration and exported
         * with it, but nothing reads it while a pipeline runs.
         *
         * @property note What happens instead, so the row is a redirection
         *   rather than only a denial.
         */
        data class EditorOnly(val note: String) : Reach
    }

    /**
     * Shared note for the four prompt fields the editor cannot write through.
     *
     * Deliberately not a cross-reference: the row above in the same section
     * already says where the prompt really comes from, and a note pointing at
     * another note is how a reader ends up reading neither.
     */
    private const val PROMPT_NOT_WRITTEN_BACK: String =
        "editing it changes nothing; the prompt this node runs on is the one in the table above"

    /** Shared note for the local sampling controls the inference engine ignores. */
    private const val SAMPLER_UNTOUCHED: String = "the engine leaves the model's own sampler in place"

    /** Shared note for a picker field that only mirrors a resolved display name. */
    private const val RESOLVED_FOR_DISPLAY: String = "shown in the picker; resolved from the id"

    /** Shared note for the read-only previews of a selected skill. */
    private const val EDIT_IN_SKILL_LIBRARY: String = "read-only preview; edit the skill in the skill library"

    /**
     * Per-field verdicts, keyed `ConfigClass.field`.
     *
     * Hand-written, because "does the engine read this" is a question about
     * `NodeConfigCodec` and the executors rather than about the declaration —
     * but not unguarded. Generation fails when a parsed field has no entry, and
     * `CookbookRuntimeReachTest` checks the verdicts themselves against the real
     * codec.
     *
     * The four [Reach.EditorOnly] prompt fields are not an oversight in this
     * table: they are a defect in the codec, recorded as what a reader will
     * actually observe rather than smoothed over here.
     */
    val FIELD_REACH: Map<String, Reach> = mapOf(
        "InputConfig.inputName" to
            Reach.EditorOnly("the entry contract is fixed; the run's text arrives as it is"),
        "InputConfig.schemaJson" to
            Reach.EditorOnly("typed pipeline inputs are not implemented"),
        "OutputConfig.format" to
            Reach.EditorOnly("ask for the format in the prompt instead"),
        "OutputConfig.systemPrompt" to Reach.Runtime("systemPrompt"),
        "LiteRtConfig.modelId" to Reach.Runtime("modelPath"),
        "LiteRtConfig.systemPrompt" to Reach.Runtime("systemPrompt"),
        "LiteRtConfig.temperature" to Reach.EditorOnly(SAMPLER_UNTOUCHED),
        "LiteRtConfig.topP" to Reach.EditorOnly(SAMPLER_UNTOUCHED),
        "LiteRtConfig.maxNewTokens" to Reach.EditorOnly(SAMPLER_UNTOUCHED),
        "LiteRtConfig.stopTokens" to Reach.EditorOnly(SAMPLER_UNTOUCHED),
        "CloudConfig.provider" to Reach.Runtime("cloudProvider"),
        "CloudConfig.model" to
            Reach.EditorOnly("the model comes from the provider's own setting"),
        "CloudConfig.systemPrompt" to Reach.Runtime("systemPrompt"),
        "CloudConfig.temperature" to
            Reach.EditorOnly("the request carries no sampling parameters"),
        "CloudConfig.maxTokens" to
            Reach.EditorOnly("the request carries no sampling parameters"),
        "CloudConfig.timeoutMs" to
            Reach.EditorOnly("cloud timeouts come from the client's own configuration"),
        "IntentRouterConfig.classes" to
            Reach.Graph("which branches exist — one port per class, and the run follows the edge the model picks"),
        "IntentRouterConfig.classifierPrompt" to Reach.EditorOnly(PROMPT_NOT_WRITTEN_BACK),
        "IntentRouterConfig.fallbackClass" to
            Reach.EditorOnly("an answer matching no class takes the node's first outgoing edge"),
        "IntentRouterConfig.engineProvider" to Reach.Runtime("cloudProvider"),
        "IfConditionConfig.expression" to Reach.Runtime("conditionPrompt"),
        "IfConditionConfig.labelTrue" to
            Reach.EditorOnly("the ports are always labelled True and False"),
        "IfConditionConfig.labelFalse" to
            Reach.EditorOnly("the ports are always labelled True and False"),
        "IfConditionConfig.branchOnImage" to Reach.Runtime("conditionHasImage"),
        "IfConditionConfig.engineProvider" to Reach.Runtime("cloudProvider"),
        "ClarificationConfig.questionTemplate" to Reach.Runtime("systemPrompt"),
        "ClarificationConfig.quickReplies" to
            Reach.EditorOnly("the answer options are produced by the model"),
        "ClarificationConfig.timeoutMs" to Reach.Runtime("clarificationTimeoutMs"),
        "ToolConfig.toolId" to Reach.Runtime("toolName"),
        "ToolConfig.argumentMapping" to
            Reach.EditorOnly("arguments are produced by the model from the node's input"),
        "ToolConfig.confirmOverride" to
            Reach.EditorOnly("approval follows the tool's risk level and your settings"),
        "ToolConfig.engineProvider" to Reach.Runtime("cloudProvider"),
        "DecompositionConfig.planningPrompt" to Reach.EditorOnly(PROMPT_NOT_WRITTEN_BACK),
        "DecompositionConfig.maxSubtasks" to
            Reach.EditorOnly("how many subtasks appear is decided by the prompt and the run's step ceiling"),
        "DecompositionConfig.outputSchemaJson" to
            Reach.EditorOnly("the subtask list is validated against a fixed shape"),
        "DecompositionConfig.engineProvider" to Reach.Runtime("cloudProvider"),
        "QueueProcessorConfig.inputList" to
            Reach.EditorOnly("the queue is the subtask list produced upstream"),
        "QueueProcessorConfig.itemVariable" to
            Reach.EditorOnly("the current subtask arrives as the node's input"),
        "QueueProcessorConfig.parallelism" to
            Reach.EditorOnly("subtasks always run one at a time"),
        "QueueProcessorConfig.stopOnError" to
            Reach.EditorOnly("what happens after a failed subtask is decided by the graph, not by this switch"),
        "EvaluationConfig.criteriaPrompt" to Reach.EditorOnly(PROMPT_NOT_WRITTEN_BACK),
        "EvaluationConfig.maxRetries" to
            Reach.Graph("whether the node has a Retry branch at all — it does not cap how often that branch is taken"),
        "EvaluationConfig.engineProvider" to Reach.Runtime("cloudProvider"),
        "SummaryConfig.format" to
            Reach.EditorOnly("ask for the shape in the prompt instead"),
        "SummaryConfig.customPrompt" to Reach.EditorOnly(PROMPT_NOT_WRITTEN_BACK),
        "SummaryConfig.targetLengthChars" to
            Reach.EditorOnly("ask for the length in the prompt instead"),
        "PipelineConfig.targetPipelineId" to Reach.Runtime("targetPipelineId"),
        "PipelineConfig.targetPipelineName" to Reach.EditorOnly(RESOLVED_FOR_DISPLAY),
        "SkillConfig.skillId" to Reach.Runtime("skillId"),
        "SkillConfig.skillName" to Reach.EditorOnly(RESOLVED_FOR_DISPLAY),
        "SkillConfig.instructionPreview" to Reach.EditorOnly(EDIT_IN_SKILL_LIBRARY),
        "SkillConfig.toolRestrictionSummary" to Reach.EditorOnly(EDIT_IN_SKILL_LIBRARY),
        "SkillConfig.engine" to Reach.Runtime("cloudProvider"),
    )

    /** How a sheet-less prompt can still be changed, worded once. */
    private const val EDIT_THE_FILE: String =
        "nothing in the app writes it — export the pipeline, change `config.systemPrompt` on the node, " +
            "and import it again; the browser editor also writes it correctly"

    /**
     * What each node type actually runs on, in the order a reader meets it.
     *
     * Hand-written, because "which properties does this executor read" is a
     * question about the executors and no declaration answers it. The guard is
     * one-directional and deliberately so: every property a sheet field writes
     * ([Reach.Runtime]) must appear here, so a control that gets wired to the
     * engine cannot stay out of the reference. The reverse — an input the
     * engine reads that nobody listed — is not mechanically checkable from
     * here and stays a review obligation.
     *
     * `modelPath` is left out of every list but `LITE_RT`'s on purpose. Every
     * node that runs the on-device model reads it, so nine identical rows would
     * say once each what the page says better once overall; the prose above the
     * reference carries it instead.
     */
    val RUNTIME_INPUTS: Map<String, List<RuntimeInput>> = mapOf(
        "INPUT" to emptyList(),
        "OUTPUT" to listOf(
            RuntimeInput(
                decides = "Whether the answer is re-worded before you see it, and how",
                property = "systemPrompt",
                setVia = SetVia.Sheet,
            ),
        ),
        "LITE_RT" to listOf(
            RuntimeInput("The instruction the model follows", "systemPrompt", SetVia.Sheet),
            RuntimeInput("Which model answers", "modelPath", SetVia.Sheet),
        ),
        "CLOUD" to listOf(
            RuntimeInput("The instruction the model follows", "systemPrompt", SetVia.Sheet),
            RuntimeInput("Which provider answers", "cloudProvider", SetVia.Sheet),
        ),
        "INTENT_ROUTER" to listOf(
            RuntimeInput(
                decides = "The instruction that sorts the message into a class",
                property = "systemPrompt",
                setVia = SetVia.NoControl(EDIT_THE_FILE),
            ),
            RuntimeInput("Whether the sorting runs on-device or in the cloud", "cloudProvider", SetVia.Sheet),
        ),
        "IF_CONDITION" to listOf(
            RuntimeInput(
                decides = "**Checked first:** whether an attached image sends the run down True",
                property = "conditionHasImage",
                setVia = SetVia.Sheet,
            ),
            RuntimeInput(
                decides = "**Checked second:** keywords that send the run down True when the input contains one",
                property = "conditionKeywords",
                setVia = SetVia.NoControl(
                    "nothing in the app writes it, and an imported pipeline that carries keywords never " +
                        "reaches the question below",
                ),
            ),
            RuntimeInput(
                decides = "**Checked third:** an input length above which the run goes down True",
                property = "conditionComplexity",
                setVia = SetVia.NoControl(
                    "nothing in the app writes it, and like keywords it is checked before the question",
                ),
            ),
            RuntimeInput(
                decides = "**Checked last:** the yes/no question put to the model",
                property = "conditionPrompt",
                setVia = SetVia.Sheet,
            ),
            RuntimeInput("Whether the question runs on-device or in the cloud", "cloudProvider", SetVia.Sheet),
        ),
        "CLARIFICATION" to listOf(
            RuntimeInput("The question you are asked", "systemPrompt", SetVia.Sheet),
            RuntimeInput("How long the run waits for your answer", "clarificationTimeoutMs", SetVia.Sheet),
        ),
        "TOOL" to listOf(
            RuntimeInput(
                decides = "Which tool is called — or, left empty, that the model picks one",
                property = "toolName",
                setVia = SetVia.Sheet,
            ),
            RuntimeInput("Whether the call is composed on-device or in the cloud", "cloudProvider", SetVia.Sheet),
        ),
        "DECOMPOSITION" to listOf(
            RuntimeInput(
                decides = "The instruction that produces the subtask list",
                property = "systemPrompt",
                setVia = SetVia.NoControl(EDIT_THE_FILE),
            ),
            RuntimeInput("Whether the planning runs on-device or in the cloud", "cloudProvider", SetVia.Sheet),
        ),
        "QUEUE_PROCESSOR" to emptyList(),
        "EVALUATION" to listOf(
            RuntimeInput(
                decides = "The instruction that judges the result",
                property = "systemPrompt",
                setVia = SetVia.NoControl(EDIT_THE_FILE),
            ),
            RuntimeInput("Whether the judgement runs on-device or in the cloud", "cloudProvider", SetVia.Sheet),
        ),
        "SUMMARY" to listOf(
            RuntimeInput(
                decides = "The instruction that combines the results",
                property = "systemPrompt",
                setVia = SetVia.NoControl(EDIT_THE_FILE),
            ),
        ),
        "PIPELINE" to listOf(
            RuntimeInput("Which pipeline is run as this step", "targetPipelineId", SetVia.Sheet),
        ),
        "SKILL" to listOf(
            RuntimeInput("Which skill is run — its instruction and its tool allowlist", "skillId", SetVia.Sheet),
            RuntimeInput("Whether the skill runs on-device or in the cloud", "cloudProvider", SetVia.Sheet),
        ),
    )

    /**
     * Types whose run-time use of the context configuration carries a caveat the
     * `CONTEXT_AWARE_NODE_TYPES` set cannot express on its own.
     *
     * Written out rather than derived: the exception is a condition inside
     * `usesContextConfig()` (an `OUTPUT` node with no prompt of its own bypasses
     * context composition entirely), and a parser reading conditions out of a
     * function body would be guessing. Keys must name a type that is in the
     * context-aware set, so the caveat cannot outlive the behaviour.
     */
    val CONTEXT_EXCEPTIONS: Map<String, String> = mapOf(
        "OUTPUT" to "only when the node has a prompt of its own; in pass-through mode the upstream text is " +
            "forwarded without any context being composed",
    )

    /**
     * Rebuilds both `AUTO-GEN` blocks of the cookbook.
     *
     * Pure and idempotent: `render(render(x)) == render(x)`.
     *
     * @param markdown Current contents of `docs/cookbook.md`.
     * @param sources The Kotlin sources the blocks are derived from.
     * @return The Markdown with both block bodies replaced.
     * @throws GenerationException when any source fails to parse, or the parsed
     *   sets do not cover the node vocabulary exactly.
     */
    fun render(markdown: String, sources: Sources): String {
        val nodes = buildNodes(sources)
        val withOverview = replaceBlock(markdown, BLOCK_NODE_REFERENCE, renderOverview(nodes))
        val withSections = replaceBlock(withOverview, BLOCK_NODE_CONFIG, renderSections(nodes))
        return replaceBlock(withSections, BLOCK_FIELD_TABLE, renderFieldTable(nodes))
    }

    /**
     * Reports which blocks of the committed cookbook have drifted.
     *
     * @param markdown Current contents of `docs/cookbook.md`.
     * @param sources The Kotlin sources the blocks are derived from.
     * @return Names of the drifted blocks; empty when the document is current.
     */
    fun drift(markdown: String, sources: Sources): List<String> {
        val nodes = buildNodes(sources)
        return buildList {
            if (blockBody(markdown, BLOCK_NODE_REFERENCE) != renderOverview(nodes)) add(BLOCK_NODE_REFERENCE)
            if (blockBody(markdown, BLOCK_NODE_CONFIG) != renderSections(nodes)) add(BLOCK_NODE_CONFIG)
            if (blockBody(markdown, BLOCK_FIELD_TABLE) != renderFieldTable(nodes)) add(BLOCK_FIELD_TABLE)
        }
    }

    /**
     * Everything the document says about one node type, assembled from every
     * source.
     *
     * @property doc The reader-facing metadata from [NODE_DOC_META].
     * @property ports The port layout parsed from `NodePorts.forType`.
     * @property context The context blocks a freshly created node starts with.
     * @property usesContext Whether the engine consults that configuration.
     * @property hasDefaultPrompt Whether a fresh node is seeded with a prompt.
     * @property configClass The `NodeConfig` data class that configures it.
     * @property fields That class's own fields, in declaration order.
     * @property inputs What the node actually runs on, and where each value
     *   comes from.
     */
    data class NodeEntry(
        val doc: NodeDoc,
        val ports: Ports,
        val context: List<String>,
        val usesContext: Boolean,
        val hasDefaultPrompt: Boolean,
        val configClass: String,
        val fields: List<ConfigField>,
        val inputs: List<RuntimeInput>,
    )

    /**
     * A node's port layout as declared by `NodePorts.forType`.
     *
     * @property inbound Number of inbound ports (0 or 1).
     * @property outbound Ordered outbound port labels; empty for a terminal node.
     * @property conditional Labels the factory emits only under a condition.
     * @property perDeclaredClass `true` when the outbound ports are one per class
     *   declared on the node rather than a fixed list.
     */
    data class Ports(
        val inbound: Int,
        val outbound: List<String>,
        val conditional: Set<String>,
        val perDeclaredClass: Boolean,
    )

    /**
     * One configuration field of a `NodeConfig` data class.
     *
     * @property name The property name, as the editor and the exported JSON use it.
     * @property default The declared default, or `null` when the field is required.
     * @property reach What the value does once the sheet is saved.
     */
    data class ConfigField(val name: String, val default: String?, val reach: Reach)

    /**
     * Parses every source and assembles one [NodeEntry] per node type.
     *
     * @throws GenerationException when a source fails to parse, or when the five
     *   independently-derived node sets disagree.
     */
    fun buildNodes(sources: Sources): List<NodeEntry> {
        val domainTypes = parseEnumConstants(sources.domainNodeType, "domain NodeType.kt")
        val catalogTypes = parseEnumConstants(sources.catalogNodeType, "catalog NodeType.kt")
        val ports = parsePorts(sources.nodePorts)
        val contexts = parseContextDefaults(sources.nodeContextConfig)
        val contextAware = parseContextAware(sources.nodeContextConfig)
        val configs = parseConfigClasses(sources.nodeConfig)
        val prompted = parsePromptedTypes(sources.defaultPrompts)

        crossCheck(domainTypes, catalogTypes, ports.keys, contexts.keys, configs.keys)
        checkExceptionsAreLive(contextAware)

        return NODE_DOC_META.map { doc ->
            val (configClass, params) = configs.getValue(doc.id)
            NodeEntry(
                doc = doc,
                ports = ports.getValue(doc.id),
                context = contexts.getValue(doc.id),
                usesContext = doc.id in contextAware,
                hasDefaultPrompt = doc.id in prompted,
                configClass = configClass,
                fields = params.map { (name, default) -> configField(configClass, name, default) },
                inputs = RUNTIME_INPUTS.getValue(doc.id),
            )
        }.also(::checkInputsCoverWrittenProperties)
    }

    /**
     * Requires every property a sheet field writes to appear in that type's
     * [RUNTIME_INPUTS] list, and every [SetVia.NoControl] claim to still be true.
     *
     * The first half stops a newly wired control from staying out of the
     * reference. The second stops the opposite drift, which is the one this
     * document was written about: a field documented as reaching nothing, after
     * somebody wires it up.
     *
     * @throws GenerationException naming the type and the property.
     */
    private fun checkInputsCoverWrittenProperties(nodes: List<NodeEntry>) {
        nodes.forEach { node ->
            val written = node.fields.mapNotNull { (it.reach as? Reach.Runtime)?.field }.toSet()
            val listed = node.inputs.associateBy { it.property }
            (written - listed.keys).sorted().forEach { property ->
                throw GenerationException(
                    "${node.doc.id}: a sheet field writes `$property`, which is not listed among the node's " +
                        "run-time inputs. Add it to CookbookDocsGenerator.RUNTIME_INPUTS.",
                )
            }
            listed.values.filter { it.setVia is SetVia.NoControl && it.property in written }.forEach { input ->
                throw GenerationException(
                    "${node.doc.id}: `${input.property}` is documented as having no control, but a sheet " +
                        "field now writes it. Change its RUNTIME_INPUTS entry to SetVia.Sheet.",
                )
            }
            listed.values.filter { it.setVia is SetVia.Sheet && it.property !in written }.forEach { input ->
                throw GenerationException(
                    "${node.doc.id}: `${input.property}` is documented as set from the sheet, but no field " +
                        "writes it. Change its RUNTIME_INPUTS entry to SetVia.NoControl.",
                )
            }
        }
    }

    /**
     * Pairs one parsed field with its recorded verdict.
     *
     * @throws GenerationException when the field carries no verdict — a field
     *   with no decision would otherwise be published as if it worked.
     */
    private fun configField(configClass: String, name: String, default: String?): ConfigField {
        val key = "$configClass.$name"
        return ConfigField(
            name = name,
            default = default,
            reach = FIELD_REACH[key] ?: throw GenerationException(
                "No run-time verdict recorded for configuration field `$key`. Add it to " +
                    "CookbookDocsGenerator.FIELD_REACH: a field with no decision would be published " +
                    "as if the engine read it.",
            ),
        )
    }

    /**
     * Requires the five independently-parsed node sets to agree, and the
     * hand-written meta to cover exactly that set.
     *
     * This is the guard that matters. Comparing any one of these walks against
     * itself would agree precisely when that walk is wrong; five parsers over
     * four files in two modules cannot fail in the same direction silently.
     *
     * @throws GenerationException naming the first set that disagrees.
     */
    private fun crossCheck(
        domain: Set<String>,
        catalog: Set<String>,
        ports: Set<String>,
        contexts: Set<String>,
        configs: Set<String>,
    ) {
        requireSameSet("the :catalog NodeType mirror", domain, catalog)
        requireSameSet("NodePorts.forType", domain, ports)
        requireSameSet("NodeContextConfig.defaultForType", domain, contexts)
        requireSameSet("the NodeConfig hierarchy", domain, configs)
        requireSameSet("CookbookDocsGenerator.NODE_DOC_META", domain, NODE_DOC_META.map { it.id }.toSet())
        requireSameSet("CookbookDocsGenerator.RUNTIME_INPUTS", domain, RUNTIME_INPUTS.keys)
        if (NODE_DOC_META.size != NODE_DOC_META.distinctBy { it.id }.size) {
            throw GenerationException("NODE_DOC_META lists a node type twice.")
        }
    }

    /** Fails with a message naming both sides of a disagreement. */
    private fun requireSameSet(what: String, expected: Set<String>, actual: Set<String>) {
        if (expected == actual) return
        val missing = (expected - actual).sorted().ifEmpty { listOf("none") }
        val extra = (actual - expected).sorted().ifEmpty { listOf("none") }
        throw GenerationException(
            "$what does not cover the NodeType vocabulary. " +
                "Missing: ${missing.joinToString()}. Unexpected: ${extra.joinToString()}.",
        )
    }

    /** Rejects a caveat recorded for a type that no longer consults its context. */
    private fun checkExceptionsAreLive(contextAware: Set<String>) {
        val stale = CONTEXT_EXCEPTIONS.keys - contextAware
        if (stale.isNotEmpty()) {
            throw GenerationException(
                "CONTEXT_EXCEPTIONS describes ${stale.sorted().joinToString()}, which no longer consults " +
                    "the context configuration at run time.",
            )
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    /** Renders the one-row-per-type overview table plus its generated legend. */
    private fun renderOverview(nodes: List<NodeEntry>): String {
        val out = StringBuilder("\n")
        out.append("| Node | What it does | In | Out | Context on a new node |\n|---|---|---|---|---|\n")
        nodes.forEach { node ->
            out.append("| **").append(node.doc.label).append("**<br>`").append(node.doc.id).append("` | ")
                .append(escapePipes(node.doc.summary)).append(" | ")
                .append(if (node.ports.inbound == 0) "—" else node.ports.inbound.toString()).append(" | ")
                .append(describePorts(node.ports)).append(" | ")
                .append(describeContext(node)).append(" |\n")
        }
        if (nodes.any { it.ports.conditional.isNotEmpty() }) {
            out.append("\n† A port the node only has while its configuration asks for it — see that node's ")
                .append("own section below.\n")
        }
        CONTEXT_EXCEPTIONS.forEach { (id, note) ->
            out.append("\n\\* `").append(id).append("` — ").append(note).append(".\n")
        }
        return out.toString()
    }

    /**
     * Renders one section per node type: what the node runs on, where each
     * value comes from, and — briefly — what the sheet shows that it ignores.
     *
     * Leading with the inputs rather than the form fields is the whole point of
     * the layout. A reader arrives asking "what does this node do and how do I
     * change it", and the form is only sometimes the answer.
     */
    private fun renderSections(nodes: List<NodeEntry>): String {
        val out = StringBuilder("\n")
        nodes.forEach { node ->
            out.append("### ").append(node.doc.label).append(" — `").append(node.doc.id).append("`\n\n")
            out.append(node.doc.summary).append("\n\n")
            out.append("- **Ports.** ").append(sentencePorts(node.ports)).append('\n')
            out.append("- **Context on a new node.** ").append(sentenceContext(node)).append("\n\n")
            out.append(renderDrivers(node))
            renderIgnored(node)?.let { out.append('\n').append(it) }
            out.append('\n')
        }
        return out.toString()
    }

    /** Renders the "what decides what this node does" table, or its absence. */
    private fun renderDrivers(node: NodeEntry): String {
        val graphFields = node.fields.filter { it.reach is Reach.Graph }
        if (node.inputs.isEmpty() && graphFields.isEmpty()) {
            return "**Nothing on this node changes a run.** What it does is fixed; " +
                "what happens around it is decided by the graph.\n"
        }
        val out = StringBuilder("**What decides what it does**\n\n")
        out.append("| What it decides | Where the value comes from |\n|---|---|\n")
        node.inputs.forEach { input ->
            out.append("| ").append(escapePipes(input.decides)).append(" | ")
                .append(escapePipes(describeSetVia(node, input))).append(" |\n")
        }
        graphFields.forEach { field ->
            val effect = (field.reach as Reach.Graph).effect
            out.append("| ").append(escapePipes(effect.replaceFirstChar(Char::titlecase))).append(" | ")
                .append("the sheet's `").append(field.name).append("` |\n")
        }
        return out.toString()
    }

    /**
     * Wording for one input's origin, resolved against the fields that write it.
     *
     * The prompt rows carry one extra fact, and it is the fact that makes the
     * warning rows make sense: a node of a type with a shipped default prompt is
     * not running *no* prompt when nothing writes one — it is running that
     * default, which is why the node still works and why the broken field is
     * easy to miss.
     */
    private fun describeSetVia(node: NodeEntry, input: RuntimeInput): String {
        val seeded = input.property == "systemPrompt" && node.hasDefaultPrompt
        return when (val via = input.setVia) {
            is SetVia.Sheet -> {
                val writers = node.fields
                    .filter { (it.reach as? Reach.Runtime)?.field == input.property }
                    .joinToString(" / ") { "`${it.name}`" }
                val seededNote = if (seeded) " — a new node arrives with this type's shipped default in it" else ""
                "the sheet's $writers$seededNote"
            }

            is SetVia.NoControl -> {
                val stays = if (seeded) "it stays this type's shipped default prompt, and " else ""
                "⚠ $stays${via.instruction}"
            }
        }
    }

    /**
     * Renders the one-line summary of what the sheet shows and the run ignores,
     * grouped so four fields sharing a reason read as one clause rather than four
     * rows. `null` when the type has no such field.
     */
    private fun renderIgnored(node: NodeEntry): String? {
        val ignored = node.fields.mapNotNull { field ->
            (field.reach as? Reach.EditorOnly)?.let { field.name to it.note }
        }
        if (ignored.isEmpty()) return null
        val clauses = ignored.groupBy({ it.second }, { it.first }).entries.joinToString("; ") { (note, names) ->
            names.joinToString(", ") { "`$it`" } + " — " + note
        }
        return "**Also on the sheet, and ignored by the run:** $clauses.\n"
    }

    /**
     * Renders the closing appendix: every configuration field of every type,
     * with its default and its verdict.
     *
     * Kept complete and kept at the back. It is the answer to "what is this box
     * for", which a reader asks second; leading with it was what made the first
     * draft read as a list of things that do not work.
     */
    private fun renderFieldTable(nodes: List<NodeEntry>): String {
        val out = StringBuilder("\n")
        out.append("| Node | Field | Default | Reaches the run |\n|---|---|---|---|\n")
        nodes.forEach { node ->
            node.fields.forEach { field ->
                out.append("| `").append(node.doc.id).append("` | `").append(field.name).append("` | ")
                    .append(field.default?.let { "`${escapePipes(it)}`" } ?: "*required*").append(" | ")
                    .append(escapePipes(describeReach(field.reach))).append(" |\n")
            }
        }
        return out.toString()
    }

    /** Compact outbound-port description for the overview table. */
    private fun describePorts(ports: Ports): String = when {
        ports.perDeclaredClass -> "one per class"
        ports.outbound.isEmpty() -> "—"
        ports.outbound == listOf(DEFAULT_PORT) -> "1"
        else -> ports.outbound.joinToString(" / ") { if (it in ports.conditional) "$it†" else it }
    }

    /** Compact context description for the overview table. */
    private fun describeContext(node: NodeEntry): String = when {
        !node.usesContext -> "input forwarded as-is"
        node.context.isEmpty() -> "**none** — the editor rejects this"
        node.doc.id in CONTEXT_EXCEPTIONS -> node.context.joinToString(", ") + " \\*"
        else -> node.context.joinToString(", ")
    }

    /** Full-sentence port description for a node's own section. */
    private fun sentencePorts(ports: Ports): String {
        val inbound = if (ports.inbound == 0) "No inbound port" else "One inbound port"
        val outbound = when {
            ports.perDeclaredClass -> "one outbound port per class declared on the node"
            ports.outbound.isEmpty() -> "no outbound port"
            ports.outbound == listOf(DEFAULT_PORT) -> "one unlabelled outbound port"
            else -> "outbound ports " + ports.outbound.joinToString(", ") { label ->
                if (label in ports.conditional) "**$label** (conditional)" else "**$label**"
            }
        }
        return "$inbound; $outbound."
    }

    /** Full-sentence context description for a node's own section. */
    private fun sentenceContext(node: NodeEntry): String {
        if (!node.usesContext) {
            return "This type ignores the context configuration — it forwards the upstream text unchanged."
        }
        if (node.context.isEmpty()) {
            return "No block is enabled, which the editor rejects as a validation error."
        }
        val flags = node.context.joinToString(", ") { "`$it`" }
        val caveat = CONTEXT_EXCEPTIONS[node.doc.id]?.let { " — $it" }.orEmpty()
        return "$flags$caveat."
    }

    /** Table-cell wording for one field's verdict. */
    private fun describeReach(reach: Reach): String = when (reach) {
        is Reach.Runtime -> "**Yes** — saved as the node's `${reach.field}`"
        is Reach.Graph -> "**Shapes the graph** — ${reach.effect}"
        is Reach.EditorOnly -> "**No** — stored and exported, but nothing reads it during a run (${reach.note})"
    }

    // ── Parsing ─────────────────────────────────────────────────────────────

    /**
     * Extracts the constant names of the `NodeType` enum declared in [source].
     *
     * @throws GenerationException when the enum body is missing or empty.
     */
    private fun parseEnumConstants(source: String, what: String): Set<String> {
        val body = balancedBody(source, source.indexOf("enum class NodeType {"), '{', '}')
            ?: throw GenerationException("`enum class NodeType` not found in $what.")
        val names = ENUM_CONSTANT_RE.findAll(body).map { it.groupValues[1] }.toSet()
        if (names.isEmpty()) throw GenerationException("No enum constants parsed from $what.")
        return names
    }

    /**
     * Extracts the port layout per type from `NodePorts.forType`.
     *
     * A branch that names no `outbound` argument at all takes the `NodePorts`
     * data class's own default of one unlabelled port — reading it as "no
     * outbound ports" would silently publish every ordinary node as terminal.
     */
    private fun parsePorts(source: String): Map<String, Ports> =
        whenBranches(source, "fun forType(", "NodePorts.forType").mapValues { (_, body) ->
            val labels = mutableListOf<String>()
            val conditional = mutableSetOf<String>()
            body.lineSequence().forEach { line ->
                OUTBOUND_PORT_RE.findAll(line).forEach { match ->
                    val label = match.groupValues[1]
                    if (label == CUSTOM_PORT) return@forEach
                    labels += label
                    if (line.contains("if (")) conditional += label
                }
            }
            Ports(
                inbound = INBOUND_RE.find(body)?.groupValues?.get(1)?.toInt() ?: 1,
                outbound = if (body.contains("outbound")) labels else listOf(DEFAULT_PORT),
                conditional = conditional,
                perDeclaredClass = body.contains("intentClasses"),
            )
        }

    /**
     * Extracts the enabled context blocks per type from
     * `NodeContextConfig.defaultForType`.
     *
     * A flag the branch does not mention keeps the data class's own default of
     * `true`, which is what the constructor does.
     */
    private fun parseContextDefaults(source: String): Map<String, List<String>> =
        whenBranches(source, "fun defaultForType(", "NodeContextConfig.defaultForType").mapValues { (_, body) ->
            CONTEXT_FLAGS.filter { flag ->
                Regex("""\b$flag\s*=\s*(true|false)""").find(body)?.groupValues?.get(1) != "false"
            }
        }

    /**
     * Extracts the set of types whose context configuration the engine consults.
     *
     * @throws GenerationException when the set declaration is missing or empty.
     */
    private fun parseContextAware(source: String): Set<String> {
        val marker = source.indexOf("CONTEXT_AWARE_NODE_TYPES")
        if (marker < 0) throw GenerationException("`CONTEXT_AWARE_NODE_TYPES` not found in NodeContextConfig.kt.")
        val body = balancedBody(source, source.indexOf("setOf(", marker), '(', ')')
            ?: throw GenerationException("`CONTEXT_AWARE_NODE_TYPES` has no parsable `setOf(...)`.")
        val names = NODE_TYPE_REF_RE.findAll(body).map { it.groupValues[1] }.toSet()
        if (names.isEmpty()) throw GenerationException("`CONTEXT_AWARE_NODE_TYPES` parsed as empty.")
        return names
    }

    /**
     * Extracts one entry per `NodeConfig` data class — the class name and its own
     * fields in declaration order — keyed by the node type the class configures.
     *
     * `title` and `description` are skipped: they are the sealed interface's
     * properties, present on every variant and carrying no per-type meaning.
     *
     * @throws GenerationException when a class body declares no `NodeType`, when
     *   two classes claim the same type, or when nothing parses at all.
     */
    private fun parseConfigClasses(source: String): Map<String, Pair<String, List<Pair<String, String?>>>> {
        val result = mutableMapOf<String, Pair<String, List<Pair<String, String?>>>>()
        CONFIG_CLASS_RE.findAll(source).forEach { match ->
            val className = match.groupValues[1]
            val params = balancedBody(source, match.range.last, '(', ')')
                ?: throw GenerationException("Unbalanced parameter list on `$className`.")
            val classBody = balancedBody(source, match.range.last + params.length, '{', '}')
                ?: throw GenerationException("Unbalanced body on `$className`.")
            val type = CONFIG_TYPE_RE.find(classBody)?.groupValues?.get(1)
                ?: throw GenerationException("`$className` declares no `override val type`.")
            if (result.containsKey(type)) {
                throw GenerationException("Two NodeConfig classes claim `NodeType.$type`.")
            }
            result[type] = className to parseParameters(className, params)
        }
        if (result.isEmpty()) throw GenerationException("No NodeConfig data classes parsed from NodeConfig.kt.")
        return result
    }

    /**
     * Splits a constructor parameter list into `name to default` pairs, skipping
     * the two inherited properties.
     *
     * Splitting respects nesting: `List<String> = emptyList()` carries commas
     * that do not separate parameters.
     *
     * @throws GenerationException when a parameter does not parse — an unread
     *   parameter would silently vanish from the published table.
     */
    private fun parseParameters(className: String, params: String): List<Pair<String, String?>> =
        splitTopLevel(params).mapNotNull { raw ->
            val param = raw.trim().trimEnd(',').trim()
            if (param.isEmpty() || param.startsWith("override ")) return@mapNotNull null
            val match = PARAM_RE.find(param)
                ?: throw GenerationException("Unparsed parameter `$param` on `$className`.")
            match.groupValues[1] to match.groupValues[3].trim().ifEmpty { null }
        }

    /**
     * Extracts the node types `getDefaultPromptForNodeType` seeds with a prompt —
     * every branch whose result is not `null`.
     */
    private fun parsePromptedTypes(source: String): Set<String> =
        whenBranches(source, "fun getDefaultPromptForNodeType(", "DefaultPrompts.getDefaultPromptForNodeType")
            .filterValues { it.trim().substringBefore('\n').trim() != "null" }
            .keys

    // ── Generic source-shape helpers ────────────────────────────────────────

    /**
     * Splits the `when (type)` block of the function starting at [functionMarker]
     * into `NodeType constant -> branch body` pairs.
     *
     * Handles all three branch shapes the sources use: one label and its body on
     * a line, several labels on a line sharing one body, and a run of labels
     * followed by an arrow on its own line. Line comments are stripped before
     * matching so a commented-out branch cannot be read as a live one.
     *
     * @throws GenerationException when the function, its `when`, or any branch is
     *   missing.
     */
    private fun whenBranches(source: String, functionMarker: String, what: String): Map<String, String> {
        val fnAt = source.indexOf(functionMarker)
        if (fnAt < 0) throw GenerationException("`$functionMarker` not found while parsing $what.")
        val whenAt = source.indexOf("when (type)", fnAt)
        if (whenAt < 0) throw GenerationException("No `when (type)` found in $what.")
        val body = balancedBody(source, whenAt, '{', '}')
            ?: throw GenerationException("Unbalanced `when (type)` block in $what.")

        val branches = mutableMapOf<String, String>()
        val pending = mutableListOf<String>()
        val current = StringBuilder()
        var owners: List<String> = emptyList()

        body.lineSequence().forEach { line ->
            val code = line.substringBefore("//")
            val arrowAt = code.indexOf("->")
            val refs = NODE_TYPE_REF_RE.findAll(code).map { it.groupValues[1] }.toList()
            val isHeader = arrowAt >= 0 && (refs.isNotEmpty() || pending.isNotEmpty())
            when {
                isHeader -> {
                    owners.forEach { branches[it] = current.toString() }
                    current.setLength(0)
                    owners = pending + NODE_TYPE_REF_RE.findAll(code.substring(0, arrowAt))
                        .map { it.groupValues[1] }
                    pending.clear()
                    current.append(code.substring(arrowAt + 2)).append('\n')
                }
                refs.isNotEmpty() && code.trimEnd().endsWith(',') -> pending += refs
                else -> current.append(line).append('\n')
            }
        }
        owners.forEach { branches[it] = current.toString() }
        if (branches.isEmpty()) throw GenerationException("No `NodeType` branches parsed from $what.")
        return branches
    }

    /**
     * Returns the text between the [open] delimiter at or after [from] and its
     * matching [close], or `null` when [from] is negative or the delimiters are
     * unbalanced.
     *
     * A regex cannot do this: every construct parsed here nests, and matching
     * only the shallow shape is exactly how a generator starts emitting a
     * quietly shorter table.
     */
    private fun balancedBody(source: String, from: Int, open: Char, close: Char): String? {
        if (from < 0) return null
        val start = source.indexOf(open, from)
        if (start < 0) return null
        var depth = 0
        for (i in start until source.length) {
            when (source[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return source.substring(start + 1, i)
                }
            }
        }
        return null
    }

    /** Splits on commas that are not inside brackets, angle brackets or parentheses. */
    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        val buffer = StringBuilder()
        var depth = 0
        text.forEach { char ->
            when (char) {
                '(', '<', '[' -> depth++
                ')', '>', ']' -> depth--
            }
            if (char == ',' && depth == 0) {
                parts += buffer.toString()
                buffer.setLength(0)
            } else {
                buffer.append(char)
            }
        }
        parts += buffer.toString()
        return parts
    }

    /**
     * Replaces one block's body, leaving its markers and everything else intact.
     *
     * @throws GenerationException when the markers are missing or inverted.
     */
    private fun replaceBlock(markdown: String, block: String, body: String): String {
        val open = openMarker(block)
        val bounds = markerBounds(markdown, block)
        return markdown.substring(0, bounds.first + open.length) + body + markdown.substring(bounds.second)
    }

    /**
     * Returns the current body of one block.
     *
     * @throws GenerationException when the markers are missing or inverted.
     */
    private fun blockBody(markdown: String, block: String): String {
        val bounds = markerBounds(markdown, block)
        return markdown.substring(bounds.first + openMarker(block).length, bounds.second)
    }

    /**
     * Locates one block's markers.
     *
     * @return The index of the opening marker and the index of the closing one.
     * @throws GenerationException when either marker is missing or they are inverted.
     */
    private fun markerBounds(markdown: String, block: String): Pair<Int, Int> {
        val start = markdown.indexOf(openMarker(block))
        val end = markdown.indexOf(closeMarker(block))
        if (start < 0 || end < 0 || end < start) {
            throw GenerationException("Markers for block `$block` are missing or inverted in the Markdown.")
        }
        return start to end
    }

    private fun openMarker(block: String): String = "<!-- AUTO-GEN:$block -->"

    private fun closeMarker(block: String): String = "<!-- /AUTO-GEN:$block -->"

    /** Table cells are pipe-delimited, so a literal pipe has to be escaped. */
    private fun escapePipes(text: String): String = text.replace("|", "\\|")

    /** The `OutboundPort` variant standing for a single unlabelled port. */
    private const val DEFAULT_PORT = "Default"

    /** The `OutboundPort` variant carrying a caller-defined label. */
    private const val CUSTOM_PORT = "Custom"

    /** The context flags, in the order `NodeContextConfig` declares them. */
    private val CONTEXT_FLAGS =
        listOf("chatHistory", "originalTask", "nodeInput", "longTermMemory", "toolResults")

    private val ENUM_CONSTANT_RE = Regex("""^ {4}([A-Z][A-Z0-9_]*),\s*$""", RegexOption.MULTILINE)
    private val NODE_TYPE_REF_RE = Regex("""NodeType\.([A-Z][A-Z0-9_]*)""")
    private val OUTBOUND_PORT_RE = Regex("""OutboundPort\.(\w+)""")
    private val INBOUND_RE = Regex("""inbound\s*=\s*(\d+)""")
    private val CONFIG_CLASS_RE = Regex("""data class (\w+Config)\(""")
    private val CONFIG_TYPE_RE = Regex("""override val type: NodeType get\(\) = NodeType\.([A-Z][A-Z0-9_]*)""")
    private val PARAM_RE = Regex("""^val (\w+):\s*([^=]+?)\s*(?:=\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL)
}
