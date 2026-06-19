package app.knotwork.android.domain.services

/**
 * On-device store for **ephemeral** voice-input audio clips.
 *
 * Voice input is a preprocessing step: a clip is recorded (or picked) only to be
 * transcribed into text, after which the audio is discarded — the transcript,
 * not the audio, is what the user sends. This store therefore owns a private
 * **cache** directory (`cacheDir/audio/`) rather than persistent app storage:
 * the files are short-lived, deleted right after a successful transcription, and
 * the OS may reclaim the cache directory under storage pressure.
 *
 * Two ways a clip enters the store:
 *  - [newRecordingFile] hands the recorder a fresh absolute path to capture into;
 *  - [importFromUri] copies a user-picked audio document (selected through an
 *    `audio/` MIME filter) into the store so it has a stable absolute path (the
 *    LiteRT-LM `Content.AudioFile` API consumes a filesystem path, not a URI).
 *
 * [delete] removes a single clip and is idempotent, so the transcription use
 * case can clean up unconditionally without first checking existence.
 */
interface AudioCaptureStore {
    /**
     * Allocates a fresh, unique absolute path inside the audio cache directory
     * for the recorder to write a new clip into. Pure path arithmetic — creates
     * the parent directory if needed but does not create the file itself.
     *
     * @return The absolute path the recorder should capture the WAV into.
     */
    fun newRecordingFile(): String

    /**
     * Copies the audio behind a user-picked content URI into the store so it has
     * a stable absolute filesystem path for transcription.
     *
     * @param uri content URI string of the picked audio document.
     * @return [Result.success] with the absolute path of the copied clip, or
     *   [Result.failure] when the URI cannot be read or the copy fails.
     */
    suspend fun importFromUri(uri: String): Result<String>

    /**
     * Deletes a single stored audio clip. Succeeds (no-op) when the file is
     * already absent so cleanup is idempotent.
     *
     * @param path The absolute path returned by [newRecordingFile] or
     *   [importFromUri].
     * @return [Result.success] when the file is gone, [Result.failure] on an
     *   unexpected I/O error.
     */
    suspend fun delete(path: String): Result<Unit>
}
