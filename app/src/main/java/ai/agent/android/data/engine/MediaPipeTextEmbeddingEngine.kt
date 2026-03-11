package ai.agent.android.data.engine

import ai.agent.android.domain.engine.TextEmbeddingEngine
import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Implementation of [TextEmbeddingEngine] using MediaPipe Tasks Text Embedder.
 * This class loads a lightweight model (e.g., universal_sentence_encoder.tflite)
 * and uses it to generate text embeddings locally on the device.
 *
 * @property context The application context used to initialize the MediaPipe embedder.
 */
class MediaPipeTextEmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextEmbeddingEngine {

    private var textEmbedder: TextEmbedder? = null
    private val modelFileName = "universal_sentence_encoder.tflite"

    /**
     * Initializes the [TextEmbedder] if it hasn't been initialized yet.
     * It attempts to load the model from the external files directory (where it might be downloaded)
     * or from assets.
     */
    private suspend fun initializeEmbedderIfNeeded() {
        if (textEmbedder != null) return

        withContext(Dispatchers.IO) {
            val modelFile = File(context.getExternalFilesDir(null), modelFileName)
            
            val baseOptionsBuilder = BaseOptions.builder()
            
            if (modelFile.exists()) {
                baseOptionsBuilder.setModelAssetPath(modelFile.absolutePath)
            } else {
                // As a fallback, try to load from assets if it's bundled
                baseOptionsBuilder.setModelAssetPath(modelFileName)
            }

            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setQuantize(true) // Quantization helps reduce memory usage
                .build()

            textEmbedder = TextEmbedder.createFromOptions(context, options)
        }
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        initializeEmbedderIfNeeded()
        
        val embedder = textEmbedder ?: throw IllegalStateException("TextEmbedder is not initialized")
        val result = embedder.embed(text)
        
        val embeddings = result.embeddingResult().embeddings()
        if (embeddings.isEmpty()) {
            throw IllegalStateException("Failed to generate embedding: empty result")
        }
        
        embeddings.first().floatEmbedding()
    }
}
