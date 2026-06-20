package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.DiscoverableModelFile
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Installs a `.litertlm` file picked from a Discover card by streaming it
 * through the existing [ModelDownloadManager] and registering the result in
 * the local model store on success.
 *
 * The use case reuses the same download path the Models screen uses (including
 * the stored Hugging Face token for gated repositories), so behaviour stays
 * consistent. Unlike the custom-URL path it persists the **real** file size
 * reported by the Hub (carried on [DiscoverableModelFile.sizeBytes]) so the
 * Models screen shows an accurate size without a follow-up disk stat. The
 * installed model is **not** auto-activated — the user activates it from the
 * Models screen, matching the onboarding/custom-URL behaviour.
 *
 * @property downloadManager streaming downloader (raw OkHttp under the hood).
 * @property localModelRepository local model registry written on success.
 * @property settingsRepository source of the stored Hugging Face token.
 */
class InstallDiscoveredModelUseCase @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val localModelRepository: LocalModelRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Streams the download of [file] and inserts a [LocalModel] row when the
     * download completes successfully.
     *
     * @param file the `.litertlm` file to install (its resolve URL, on-disk
     *   name and size).
     * @return a [Flow] of [DownloadState] mirroring the download progress;
     *   the terminal [DownloadState.Success] is emitted only after the local
     *   row has been registered.
     */
    operator fun invoke(file: DiscoverableModelFile): Flow<DownloadState> = flow {
        val token = settingsRepository.huggingFaceAuthToken.first()
        emitAll(
            downloadManager.downloadModel(url = file.resolveUrl, fileName = file.fileName, authToken = token)
                .onEach { state ->
                    if (state is DownloadState.Success) {
                        localModelRepository.insertModel(
                            LocalModel(
                                name = file.fileName,
                                path = state.fileUri,
                                size = file.sizeBytes,
                                isActive = false,
                            ),
                        )
                    }
                },
        )
    }
}
