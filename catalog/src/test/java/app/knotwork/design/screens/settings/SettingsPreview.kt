package app.knotwork.design.screens.settings

/**
 * Internal preview fixtures backing the settings hub + category snapshot suites.
 * Stateless test data only — no behaviour. Mirrors the `screens-settings-ia`
 * design gallery states (hub default/loading/restart, Memory full matrix,
 * Generation / Tools / About for breadth).
 */
internal object SettingsPreview {

    // ─── Hub ─────────────────────────────────────────────────────────────────

    fun hubDefault(): SettingsHubViewState = SettingsHubViewState(
        loading = false,
        subtitleVersion = "0.9.2",
        subtitleChannel = "alpha",
        subtitleBuildDate = "2026.05.18",
        systemInstructionsPreview = "Be concise. Prefer bullet points over prose.",
        approveSelection = ApproveToolCallsOption.Sensitive,
        approveAllLabel = "All",
        approveSensitiveLabel = "Sensitive",
        approveNeverLabel = "Never",
        blockDestructive = true,
        backendLabel = "NPU (QNN) · auto-fallback to GPU then CPU.",
        selectedBackend = "NPU · auto",
        backendOptions = listOf("NPU · auto", "GPU", "CPU"),
        longRunningEnabled = true,
        crashReportingEnabled = false,
    )

    fun hubLoading(): SettingsHubViewState = hubDefault().copy(loading = true)

    fun hubRestart(): SettingsHubViewState =
        hubDefault().copy(restartRequiredMessage = "Backend change requires restart.")

    /** Hub with an active "max" query — results across categories incl. a synonym hit. */
    fun hubSearchResults(): SettingsHubViewState = hubDefault().copy(
        searchQuery = "max",
        searchResults = listOf(
            searchRow("MAX_CONTEXT_LENGTH", SettingsCategoryId.Generation, "Max context length", 0, 3),
            searchRow(
                "PIPELINE_MAX_STEPS",
                SettingsCategoryId.Pipelines,
                "Cap autonomous steps",
                basic = true,
                synonym = "max",
            ),
            searchRow("MAX_MEMORY_CHUNKS", SettingsCategoryId.Memory, "Max memory chunks", 0, 3),
            searchRow("WORKSPACE_MAX_FILE_SIZE_BYTES", SettingsCategoryId.Tools, "Workspace max file size", 10, 3),
            searchRow("RESUME_MAX_AGE_HOURS", SettingsCategoryId.Background, "Resume max age", 7, 3),
        ),
    )

    /** Hub with a query that matches nothing — the calm empty state. */
    fun hubSearchEmpty(): SettingsHubViewState = hubDefault().copy(searchQuery = "lidar", searchResults = emptyList())

    private fun searchRow(
        anchor: String,
        category: SettingsCategoryId,
        name: String,
        nameStart: Int = -1,
        len: Int = 0,
        basic: Boolean = false,
        synonym: String? = null,
    ): HubSearchResultRow = HubSearchResultRow(anchor, category, name, nameStart, len, basic, synonym)

    // ─── Generation ──────────────────────────────────────────────────────────

    fun generation(): GenerationSettingsViewState = GenerationSettingsViewState(
        systemInstructions = systemInstructions(),
        toolUsageValue = "Prefer the workspace file tools over inlining large outputs into chat.",
        toolUsageHelper = "Extra guidance on when and how to call tools.",
        advancedSliders = listOf(
            SettingSliderRow(SLIDER_TEMPERATURE, "Temperature", "0.7", 0.7f, 0f..2f),
            SettingSliderRow(SLIDER_TOP_K, "Top-K", "40", 40f, 1f..100f, steps = 99),
            SettingSliderRow(SLIDER_TOP_P, "Top-P", "0.90", 0.9f, 0f..1f),
            SettingSliderRow(SLIDER_REPETITION_PENALTY, "Repetition penalty", "1.10", 1.10f, 1f..2f),
            SettingSliderRow(SLIDER_MAX_CONTEXT, "Max context", "4096 tok", 4096f, 512f..8192f, steps = 14),
            SettingSliderRow(SLIDER_AUDIO_MAX_DURATION, "Voice input length", "60 s", 60f, 5f..120f),
        ),
    )

    // ─── Models ──────────────────────────────────────────────────────────────

    fun models(): ModelsSettingsViewState = ModelsSettingsViewState(
        localModel = localModel(),
        providers = providers(),
    )

    fun modelsRestart(): ModelsSettingsViewState =
        models().copy(restartRequiredMessage = "Backend change requires restart.")

    // ─── Memory ──────────────────────────────────────────────────────────────

    fun memory(): MemorySettingsViewState = MemorySettingsViewState(
        stats = listOf(
            MemoryStatCell("Chunks", "1 248"),
            MemoryStatCell("Size", "14.2 MB"),
            MemoryStatCell("Threads", "38"),
            MemoryStatCell("Avg score", "0.74"),
        ),
        autoExtractEnabled = true,
        autoExtractLabel = "Auto-extract from conversations",
        autoExtractSubtitle = "Saves durable facts to memory after each chat",
        compactionEnabled = true,
        compactionLabel = "Background compaction",
        compactionSubtitle = "Daily clustering of stale chunks while charging",
        chatHistoryCompressionEnabled = true,
        chatHistoryCompressionLabel = "Compress chat history",
        chatHistoryCompressionSubtitle = "Summarise long threads past the live window",
        advancedSliders = memorySliders(),
        verboseLoggingEnabled = false,
        embeddingTitle = "Embedding model",
        embeddingOptions = listOf(
            EmbeddingOptionRow("use", "On-device (Universal Sentence Encoder)"),
            EmbeddingOptionRow("openai_3_small", "OpenAI (text-embedding-3-small)"),
        ),
        selectedEmbeddingId = "use",
        selectedEmbeddingLabel = "On-device (Universal Sentence Encoder)",
        exportLabel = "Export base",
        importLabel = "Import",
        reembedLabel = "Re-embed",
        clearLabel = "Clear",
    )

    fun memoryError(): MemorySettingsViewState = memory().copy(
        advancedSliders = memorySliders().map {
            if (it.id == SLIDER_MEMORY_MAX_CHUNKS) it.copy(valueLabel = "24 000", value = 24_000f) else it
        },
        validationError = "Out of range — max is 20 000.",
    )

    fun memoryPending(): MemorySettingsViewState = memory().copy(reembedProgressPercent = 42)

    fun memoryReembedBanner(): MemorySettingsViewState =
        memory().copy(reembedBanner = "Stored vectors were created with a different provider. Re-embed to realign.")

    // ─── Pipelines ───────────────────────────────────────────────────────────

    fun pipelines(): PipelinesSettingsViewState = PipelinesSettingsViewState(
        capAutonomousSteps = SettingSliderRow(SLIDER_PIPELINE_CAP_STEPS, "Cap autonomous steps", "20", 20f, 5f..100f),
        advancedSliders = listOf(
            SettingSliderRow(SLIDER_PIPELINE_NESTING_DEPTH, "Max nesting depth", "3", 3f, 1f..5f, steps = 3),
            SettingSliderRow(
                SLIDER_PIPELINE_STRUCTURED_REPAIRS,
                "Structured-output repairs",
                "2",
                2f,
                0f..4f,
                steps = 3,
            ),
        ),
    )

    // ─── Tools ───────────────────────────────────────────────────────────────

    fun tools(): ToolsSettingsViewState = ToolsSettingsViewState(
        approveSelection = ApproveToolCallsOption.Sensitive,
        approveAllLabel = "All",
        approveSensitiveLabel = "Sensitive",
        approveNeverLabel = "Never",
        blockDestructive = true,
        blockDestructiveSubtitle = "Always require a typed confirm for destructive calls.",
        blockNetwork = true,
        blockNetworkSubtitle = "LiteRT runs offline · cloud gated separately.",
    )

    // ─── Background ──────────────────────────────────────────────────────────

    fun background(): BackgroundSettingsViewState = BackgroundSettingsViewState(
        longRunningEnabled = true,
        scheduledResultsEnabled = true,
        shareTargetPipelineLabel = "Default System Pipeline",
        shareReuseSessionEnabled = true,
        quickTilePipelineLabel = "Not set",
        advancedSliders = listOf(
            SettingSliderRow(SLIDER_BACKGROUND_RESUME_MAX_AGE, "Resume window", "48 h", 48f, 1f..168f),
            SettingSliderRow(SLIDER_BACKGROUND_APPROVAL_WINDOW, "Approval window", "24 h", 24f, 1f..168f),
        ),
    )

    // ─── Privacy ─────────────────────────────────────────────────────────────

    fun privacy(): PrivacySettingsViewState = PrivacySettingsViewState(
        crashReportingEnabled = false,
        advancedSliders = listOf(
            SettingSliderRow(SLIDER_PRIVACY_RETENTION_RUNS, "Trace retention · runs", "30", 30f, 5f..100f),
            SettingSliderRow(SLIDER_PRIVACY_RETENTION_AGE, "Trace retention · age", "30 d", 30f, 7f..180f),
        ),
    )

    /**
     * Privacy state for the FOSS distribution: the crash-reporting consent row is
     * hidden (`crashReportingAvailable = false`), leaving only the Advanced
     * retention sliders and a Basic row count of zero.
     */
    fun privacyFossHidden(): PrivacySettingsViewState = privacy().copy(crashReportingAvailable = false)

    // ─── Usage statistics ────────────────────────────────────────────────────

    fun usageTelemetry(): UsageTelemetryViewState = UsageTelemetryViewState(
        recordingEnabled = true,
        isEmpty = false,
        runsHeadline = "12 total",
        outcomes = listOf(
            UsageStatRow("Completed", "9 (75%)"),
            UsageStatRow("Failed", "2 (16%)"),
            UsageStatRow("Cancelled", "1 (8%)"),
            UsageStatRow("Interrupted", "0 (0%)"),
        ),
        pipelines = listOf(
            UsageStatRow("Daily digest", "7"),
            UsageStatRow("Research assistant", "5"),
        ),
        triggersHeadline = "4 total",
        triggers = listOf(
            UsageStatRow("Charging", "3"),
            UsageStatRow("Daily schedule", "1"),
        ),
        activeDays = listOf(
            UsageStatRow("Days", "5"),
            UsageStatRow("First", "2026-06-20"),
            UsageStatRow("Last", "2026-06-25"),
        ),
    )

    /** Empty Usage statistics state (recording on, nothing recorded yet). */
    fun usageTelemetryEmpty(): UsageTelemetryViewState = UsageTelemetryViewState(
        recordingEnabled = true,
        isEmpty = true,
        runsHeadline = "0 total",
        outcomes = emptyList(),
        pipelines = emptyList(),
        triggersHeadline = "0 total",
        triggers = emptyList(),
        activeDays = emptyList(),
    )

    // ─── About ───────────────────────────────────────────────────────────────

    fun about(): AboutSettingsViewState = AboutSettingsViewState(
        identity = identity(),
        versionLine = "v0.9.2 · alpha · 2026.05.18",
    )

    // ─── Shared building blocks ──────────────────────────────────────────────

    private fun identity(): IdentityCardState = IdentityCardState(
        displayName = "Anonymous · this device",
        avatarInitials = "AA",
        metaLine = "device-id 4f3a-92d1 · keys in Android Keystore",
    )

    private fun systemInstructions(): SystemInstructionsCardState = SystemInstructionsCardState(
        value = "Be concise. Prefer bullet points over prose. " +
            "Use \$DATE for any date reference and \$LANG for the user's language.",
        placeholder = "Be concise.",
        variableChips = listOf("\$DATE", "\$TIME", "\$LANG", "\$LOCATION", "\$USER", "\$DEVICE"),
        characterCount = 218,
        characterLimit = 4_000,
        approximateTokens = 62,
        helperText = "Prepended to every system prompt the agent sends.",
        validationError = null,
    )

    private fun localModel(): LocalModelCardState = LocalModelCardState(
        modelName = "gemma-2b-it-q4",
        metaLine = "1.4 GB · 2 048 ctx · Q4_K_M · downloaded 12 May",
        backendLabel = "NPU (QNN) · auto-fallback to GPU then CPU.",
        backendOptions = listOf("NPU · auto", "GPU", "CPU"),
        selectedBackend = "NPU · auto",
        testProbeText = "Last probe · 248 tok in 1.42 s · 174 tok/s",
        testProbeIsError = false,
    )

    private fun providers(): List<ProviderRowState> = listOf(
        ProviderRowState("openai", "OpenAI", "sk-…3a9f", "gpt-4o-mini", null, false),
        ProviderRowState("anthropic", "Anthropic", "sk-ant-…b21c", "claude-sonnet-4", null, false),
        ProviderRowState("google", "Google", null, null, null, false),
        ProviderRowState("ollama", "Ollama", "192.168.1.42:11434", "mistral-7b · 4096", "192.168.1.42:11434", true),
    )

    private fun memorySliders(): List<SettingSliderRow> = listOf(
        SettingSliderRow(SLIDER_MEMORY_AUTO_SUMMARIZE, "Auto-summarize threshold", "80 %", 80f, 0f..100f),
        SettingSliderRow(SLIDER_MEMORY_SEARCH_TOP_K, "Search results (top-K)", "5", 5f, 1f..20f),
        SettingSliderRow(SLIDER_MEMORY_SEARCH_THRESHOLD, "Similarity threshold", "0.55", 0.55f, 0.3f..0.9f),
        SettingSliderRow(SLIDER_MEMORY_RECENCY_HALF_LIFE, "Recency half-life", "30 d", 30f, 7f..180f),
        SettingSliderRow(SLIDER_MEMORY_COMPACTION_AGE, "Compaction age", "30 d", 30f, 7f..90f),
        SettingSliderRow(SLIDER_MEMORY_MAX_CHUNKS, "Max chunks", "5 000", 5000f, 1000f..20000f),
        SettingSliderRow(SLIDER_MEMORY_COMPRESSION_THRESHOLD, "Compression threshold", "3 500", 3500f, 1000f..8000f),
        SettingSliderRow(SLIDER_MEMORY_LIVE_WINDOW, "Live message window", "10", 10f, 2f..50f),
        SettingSliderRow(SLIDER_MEMORY_SUMMARY_LIMIT, "Memory summary size", "5", 5f, 1f..50f),
    )
}
