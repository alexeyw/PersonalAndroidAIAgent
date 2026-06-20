package app.knotwork.android.data.network.huggingface

import java.io.IOException

/**
 * Raised when the Hugging Face Hub returns a non-2xx response while listing or
 * fetching model metadata. Extends [IOException] so it travels the same path as
 * a transport failure and is caught by the repository's network `catch`.
 *
 * @property code HTTP status code returned by the Hub.
 */
class HuggingFaceApiException(val code: Int, message: String) : IOException(message)
