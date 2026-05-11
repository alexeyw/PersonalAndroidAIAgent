package ai.agent.android.data.engine

import android.content.Context
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import javax.inject.Inject

/**
 * Factory interface for creating [TextEmbedder] instances, allowing for easier unit testing.
 */
interface TextEmbedderFactory {
    /**
     * Creates a [TextEmbedder] from the given [options].
     *
     * @param context Application context
     * @param options Configuration options for the embedder
     * @return A ready-to-use [TextEmbedder]
     */
    fun createFromOptions(context: Context, options: TextEmbedder.TextEmbedderOptions): TextEmbedder
}

/**
 * Default implementation of [TextEmbedderFactory] that delegates to MediaPipe's static factory.
 */
class DefaultTextEmbedderFactory @Inject constructor() : TextEmbedderFactory {
    /**
     * @inheritDoc
     */
    override fun createFromOptions(context: Context, options: TextEmbedder.TextEmbedderOptions): TextEmbedder =
        TextEmbedder.createFromOptions(context, options)
}
