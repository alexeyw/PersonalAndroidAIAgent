package ai.agent.android.domain.engine

/**
 * Interface representing an engine capable of generating text embeddings.
 * Embeddings are numerical representations (vectors) of text that capture semantic meaning.
 */
interface TextEmbeddingEngine {

    /**
     * Generates a float array embedding for the provided text.
     *
     * @param text The input string to be embedded.
     * @return A float array representing the text embedding.
     */
    suspend fun generateEmbedding(text: String): FloatArray
}
