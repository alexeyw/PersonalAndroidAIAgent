package app.knotwork.android.data.repositories

import android.content.Context
import app.knotwork.android.domain.models.Skill
import app.knotwork.android.domain.models.SkillImportOutcome
import app.knotwork.android.domain.skillio.SkillJsonSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Asset-backed [BundledSkillSource]. Lists the JSON files under
 * `assets/presets/skills`, parses each via [SkillJsonSerializer], and returns
 * the bundled skills. All I/O runs on the IO dispatcher.
 */
class AssetBundledSkillSource @Inject constructor(@ApplicationContext private val context: Context) :
    BundledSkillSource {

    override suspend fun load(): List<Skill> = withContext(Dispatchers.IO) { readBundledFromAssets() }

    /**
     * Lists the JSON files under `assets/presets/skills` and parses each,
     * skipping any that cannot be read. Returns an empty list when the
     * directory is absent.
     */
    private fun readBundledFromAssets(): List<Skill> {
        val fileNames: Array<String> = try {
            context.assets.list(ASSETS_BUNDLED_DIR) ?: emptyArray()
        } catch (e: java.io.IOException) {
            Timber.w(e, "No bundled skill directory at assets/%s", ASSETS_BUNDLED_DIR)
            return emptyList()
        }

        return fileNames
            .filter { it.endsWith(JSON_EXTENSION, ignoreCase = true) }
            .mapNotNull { fileName -> readBundledFile(fileName) }
    }

    /**
     * Reads and parses a single bundled skill file, or returns `null` when the
     * file cannot be read or is irrecoverably malformed.
     */
    private fun readBundledFile(fileName: String): Skill? {
        val path = "$ASSETS_BUNDLED_DIR/$fileName"
        val jsonText = try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            Timber.w(e, "Failed to read bundled skill file: %s", path)
            return null
        }

        return when (val outcome = SkillJsonSerializer.parse(jsonText, isBundled = true)) {
            is SkillImportOutcome.Success -> outcome.skill
            is SkillImportOutcome.SchemaMismatch -> {
                Timber.w(
                    "Bundled skill %s has schemaVersion=%d (expected %d); loading on best-effort basis",
                    path,
                    outcome.foundVersion,
                    outcome.expectedVersion,
                )
                outcome.skill
            }
            is SkillImportOutcome.Failure -> {
                Timber.w("Skipping malformed bundled skill %s: %s", path, outcome.message)
                null
            }
        }
    }

    private companion object {
        const val ASSETS_BUNDLED_DIR = "presets/skills"
        const val JSON_EXTENSION = ".json"
    }
}
