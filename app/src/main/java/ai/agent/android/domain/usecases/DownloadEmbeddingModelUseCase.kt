package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.DownloadState
import ai.agent.android.domain.repositories.ModelDownloadManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for downloading a lightweight text embedding model (e.g., Universal Sentence Encoder).
 * The downloaded model is required for generating local text embeddings via MediaPipe.
 */
class DownloadEmbeddingModelUseCase @Inject constructor(
    private val modelDownloadManager: ModelDownloadManager
) {
    /**
     * Initiates the download of the universal sentence encoder model.
     *
     * @param url The URL where the `.tflite` model is hosted.
     * @return A [Flow] emitting [DownloadState] updates.
     */
    operator fun invoke(url: String = DEFAULT_MODEL_URL): Flow<DownloadState> {
        // "universal_sentence_encoder.tflite" must match the name expected by MediaPipeTextEmbeddingEngine
        return modelDownloadManager.downloadModel(url, "universal_sentence_encoder.tflite", null)
    }

    companion object {
        // A placeholder or default URL for the tflite model.
        // In a real scenario, this could point to a Kaggle Models or HuggingFace URL.
        const val DEFAULT_MODEL_URL = "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/1/universal_sentence_encoder.tflite"
    }
}
