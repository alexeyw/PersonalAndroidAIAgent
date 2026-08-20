package app.knotwork.android.domain.engine.stuck

/**
 * One executed step of the run walk, as the stuck-detector sees it.
 *
 * Deliberately a projection rather than the node itself. The detector needs to
 * compare steps to each other and nothing else — it never renders them, never
 * re-executes them, and must never hold a run's transcripts alive for the
 * length of a nightly loop. So the caller reduces the texts to fingerprints at
 * the observation site (see [GraphStuckDetector.fingerprint]) and this type
 * carries only what a comparison needs.
 *
 * The fields mirror exactly what the engine already persists per checkpoint in
 * a `RunTraceRecord.NodeIo`: the node, the input it executed on, the output it
 * produced. That is not a coincidence — the checkpoint *is* the observation
 * point, so the detector can never be looking at something the run trace
 * cannot later show a person who taps **Open console**.
 *
 * @property nodeId Id of the node that executed. Identity, not type: two
 *   distinct `CLOUD` nodes taking turns are not one node repeating.
 * @property inputFingerprint Fingerprint of the exact text handed to the
 *   executor.
 * @property outputFingerprint Fingerprint of the text the node produced. Equal
 *   to [inputFingerprint] when the node forwarded its input untouched, which is
 *   how a pass-through is recognised without the detector having to carry a
 *   node-type table it would then have to keep in step with `NodeType`.
 */
data class RunStepObservation(val nodeId: String, val inputFingerprint: String, val outputFingerprint: String) {
    /**
     * Whether this step forwarded its input without changing it.
     *
     * A pass-through is evidence of nothing on its own: `INPUT`, an echo
     * `OUTPUT`, `IF_CONDITION` and `INTENT_ROUTER` all hand their input onward
     * verbatim by design, and a node that emits no result at all leaves the
     * engine forwarding the input as the output. Treating any of those as
     * "produced something new" would let a run reset its progress counter
     * without doing any work.
     */
    val isPassThrough: Boolean get() = outputFingerprint == inputFingerprint
}
