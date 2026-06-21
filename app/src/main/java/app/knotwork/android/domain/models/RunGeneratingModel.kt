package app.knotwork.android.domain.models

/**
 * Mutable run-tree-scoped holder for the identity of the model that produced the
 * current answer. Written by whichever answering node ran last — a `LITE_RT`
 * node records its loaded on-device model path, a `CLOUD` node records its
 * provider label — and read by the root `OUTPUT` node so the persisted chat
 * message is attributed to the model that actually generated it, not to whatever
 * model happens to be active when the message is later rendered.
 *
 * Shared across the whole run tree: the engine threads the same instance through
 * [ExecutionScope] into sub-pipelines (exactly like [RunImageDelivery]) so an
 * answer produced inside a sub-pipeline still attributes correctly at the root
 * `OUTPUT`. The engine's `invoke` flow is the only writer and the recursion is
 * synchronous within a single coroutine, so the `@Volatile` fields need no
 * further synchronisation.
 *
 * @property localModelPath On-disk path of the on-device model that produced the
 *   most recent answer, or `null` when that answer came from a cloud provider.
 * @property cloudLabel Display label of the cloud provider that produced the most
 *   recent answer, or `null` when that answer came from an on-device model.
 */
class RunGeneratingModel(@Volatile var localModelPath: String? = null, @Volatile var cloudLabel: String? = null)
