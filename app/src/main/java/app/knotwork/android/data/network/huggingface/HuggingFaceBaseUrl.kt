package app.knotwork.android.data.network.huggingface

import javax.inject.Qualifier

/**
 * Qualifies the Hugging Face Hub base URL injected into [HuggingFaceModelApi].
 * Exists so the host is a single DI-provided value (production points at the
 * public Hub; instrumented/unit tests can point the client at a local mock
 * server) rather than a hard-coded literal inside the client.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HuggingFaceBaseUrl
