package app.knotwork.design.screens.discover

/**
 * Deterministic fixtures backing the `DiscoverContent` / `DiscoverDetailContent`
 * previews and the Roborazzi snapshot matrix. Internal so `:app` code cannot
 * reach them.
 */
internal object DiscoverPreview {

    fun rows(): List<DiscoverModelRow> = listOf(
        DiscoverModelRow(
            repoId = "litert-community/gemma-4-E2B-it-litert-lm",
            name = "gemma-4-E2B-it-litert-lm",
            meta = "↓ 12.4k · ♥ 86 · apache-2.0",
            fileCountLabel = "3 files",
        ),
        DiscoverModelRow(
            repoId = "litert-community/gemma-4-E4B-it-litert-lm",
            name = "gemma-4-E4B-it-litert-lm",
            meta = "↓ 7.1k · ♥ 54 · apache-2.0",
            fileCountLabel = "4 files",
        ),
        DiscoverModelRow(
            repoId = "litert-community/Llama-3.2-1B-Instruct-litert-lm",
            name = "Llama-3.2-1B-Instruct-litert-lm",
            meta = "↓ 3.0k · ♥ 22 · llama3.2",
            fileCountLabel = "2 files",
            gated = true,
        ),
    )

    fun loading(): DiscoverViewState = DiscoverViewState(visualState = DiscoverVisualState.Loading)

    fun populated(): DiscoverViewState = DiscoverViewState(
        visualState = DiscoverVisualState.Populated,
        query = "",
        rows = rows(),
    )

    fun empty(): DiscoverViewState = DiscoverViewState(
        visualState = DiscoverVisualState.Empty,
        query = "no-such-model",
    )

    fun error(): DiscoverViewState = DiscoverViewState(
        visualState = DiscoverVisualState.Error,
        errorMessage = "Couldn't reach Hugging Face. Check your connection and try again.",
    )

    fun detailLoading(): DiscoverDetailViewState = DiscoverDetailViewState(
        visualState = DiscoverDetailVisualState.Loading,
        title = "gemma-4-E2B-it-litert-lm",
        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
    )

    fun detailLoaded(): DiscoverDetailViewState = DiscoverDetailViewState(
        visualState = DiscoverDetailVisualState.Loaded,
        title = "gemma-4-E2B-it-litert-lm",
        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
        metaLine = "↓ 12.4k · ♥ 86",
        license = "apache-2.0",
        files = listOf(
            DiscoverFileRow(
                fileName = "gemma-4-E2B-it.litertlm",
                sizeLabel = "2.4 GB",
                status = DiscoverFileStatus.Idle,
            ),
            DiscoverFileRow(
                fileName = "gemma-4-E2B-it-int4.litertlm",
                sizeLabel = "1.2 GB",
                status = DiscoverFileStatus.Downloading(progress = 42),
            ),
            DiscoverFileRow(
                fileName = "gemma-4-E2B-it-gpu.litertlm",
                sizeLabel = "2.9 GB",
                status = DiscoverFileStatus.Installed,
            ),
        ),
    )

    fun detailGated(): DiscoverDetailViewState = DiscoverDetailViewState(
        visualState = DiscoverDetailVisualState.Loaded,
        title = "Llama-3.2-1B-Instruct-litert-lm",
        repoId = "litert-community/Llama-3.2-1B-Instruct-litert-lm",
        metaLine = "↓ 3.0k · ♥ 22",
        license = "llama3.2",
        gated = true,
        files = listOf(
            DiscoverFileRow(
                fileName = "Llama-3.2-1B-Instruct.litertlm",
                sizeLabel = "1.1 GB",
                status = DiscoverFileStatus.Idle,
            ),
        ),
    )

    fun detailLicenseDialog(): DiscoverDetailViewState = detailLoaded().copy(
        pendingLicenseFileName = "gemma-4-E2B-it.litertlm",
    )

    fun detailError(): DiscoverDetailViewState = DiscoverDetailViewState(
        visualState = DiscoverDetailVisualState.Error,
        title = "gemma-4-E2B-it-litert-lm",
        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
        errorMessage = "Couldn't load this model's details.",
    )
}
