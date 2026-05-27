package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.PipelinePresetDao
import ai.agent.android.data.local.models.PipelinePresetEntity
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.domain.models.PipelinePresetImportOutcome
import ai.agent.android.domain.models.PresetCategory
import ai.agent.android.domain.pipelineio.PipelinePresetJsonSerializer
import ai.agent.android.domain.repositories.PipelinePresetRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Local implementation of [PipelinePresetRepository] composed from two
 * independent stores:
 *
 * - **Bundled** presets are read lazily on first subscription from
 *   `assets/presets/pipelines/*.json`, parsed via
 *   [PipelinePresetJsonSerializer], and cached for the lifetime of the
 *   process (the asset set cannot change at runtime, so re-reading on
 *   every subscription is pure waste).
 * - **User** presets are observed from [PipelinePresetDao] and mapped to
 *   the domain shape on every emission.
 *
 * The two flows are exposed separately so the picker UI can render the
 * bundled and user buckets in distinct tabs without re-deriving them on
 * every recomposition.
 *
 * Asset I/O and JSON parsing run on [Dispatchers.IO]. Malformed asset
 * files are logged and skipped rather than failing the whole catalogue
 * load, so a single corrupt file added in Task 2/9 cannot hide the rest
 * of the starter set from the user.
 */
class LocalPipelinePresetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PipelinePresetDao,
) : PipelinePresetRepository {

    private val bundledCacheMutex = Mutex()

    @Volatile
    private var bundledCache: List<PipelinePreset>? = null

    override fun getBundledPresets(): Flow<List<PipelinePreset>> = flow {
        emit(loadBundledOnce())
    }

    override fun getUserPresets(): Flow<List<PipelinePreset>> =
        dao.getAll().map { rows -> rows.map { it.toDomainModel() } }

    override suspend fun getPresetById(id: String): PipelinePreset? {
        loadBundledOnce().firstOrNull { it.id == id }?.let { return it }
        return dao.getById(id)?.toDomainModel()
    }

    override suspend fun saveUserPreset(preset: PipelinePreset) {
        require(!preset.isBundled) {
            "Bundled presets are read-only and cannot be saved as user presets"
        }
        val entity = PipelinePresetEntity(
            id = preset.id,
            name = preset.name,
            description = preset.description,
            categoryKey = preset.category.key,
            graphJson = PipelinePresetJsonSerializer.serialize(preset),
            tagsCsv = preset.tags.joinToString(separator = TAG_SEPARATOR),
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
    }

    override suspend fun deleteUserPreset(id: String) {
        dao.deleteById(id)
    }

    private suspend fun loadBundledOnce(): List<PipelinePreset> {
        bundledCache?.let { return it }
        return bundledCacheMutex.withLock {
            bundledCache?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) { readBundledFromAssets() }
            bundledCache = loaded
            loaded
        }
    }

    private fun readBundledFromAssets(): List<PipelinePreset> {
        val assetManager = context.assets
        val fileNames: Array<String> = try {
            assetManager.list(ASSETS_BUNDLED_DIR) ?: emptyArray()
        } catch (e: java.io.IOException) {
            // The directory itself is missing — this is legitimate during
            // Task 1/9 of Phase 24 (the catalogue file set is filled in
            // Task 2/9). Log once and return an empty list so the picker
            // simply shows the "no bundled presets yet" empty state.
            Timber.w(e, "No bundled preset directory at assets/%s", ASSETS_BUNDLED_DIR)
            return emptyList()
        }

        return fileNames
            .filter { it.endsWith(JSON_EXTENSION, ignoreCase = true) }
            .mapNotNull { fileName -> readBundledFile(fileName) }
    }

    private fun readBundledFile(fileName: String): PipelinePreset? {
        val path = "$ASSETS_BUNDLED_DIR/$fileName"
        val jsonText = try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            Timber.w(e, "Failed to read bundled preset file: %s", path)
            return null
        }

        return when (val outcome = PipelinePresetJsonSerializer.parse(jsonText, isBundled = true)) {
            is PipelinePresetImportOutcome.Success -> outcome.preset
            is PipelinePresetImportOutcome.SchemaMismatch -> {
                Timber.w(
                    "Bundled preset %s has schemaVersion=%d (expected %d); loading on best-effort basis",
                    path,
                    outcome.foundVersion,
                    outcome.expectedVersion,
                )
                outcome.preset
            }
            is PipelinePresetImportOutcome.Failure -> {
                Timber.w("Skipping malformed bundled preset %s: %s", path, outcome.message)
                null
            }
        }
    }

    private fun PipelinePresetEntity.toDomainModel(): PipelinePreset {
        // Round-trip the stored graphJson through the serializer so any
        // schema-version drift between save-time and load-time surfaces as
        // a log warning rather than silently producing a broken preset.
        val parsedGraph = when (
            val outcome = PipelinePresetJsonSerializer.parse(graphJson, isBundled = false)
        ) {
            is PipelinePresetImportOutcome.Success -> outcome.preset.graph
            is PipelinePresetImportOutcome.SchemaMismatch -> {
                Timber.w(
                    "User preset %s stored with schemaVersion=%d (expected %d); using best-effort decode",
                    id,
                    outcome.foundVersion,
                    outcome.expectedVersion,
                )
                outcome.preset.graph
            }
            is PipelinePresetImportOutcome.Failure -> {
                // Defensive: a corrupt row should not propagate an
                // exception to subscribers. Returning an empty graph is
                // visibly broken in the picker (no nodes) and prompts
                // the user to delete the row.
                Timber.e("Failed to decode stored user preset %s: %s", id, outcome.message)
                PipelineGraph(id = id, name = name)
            }
        }

        return PipelinePreset(
            id = id,
            name = name,
            description = description,
            category = PresetCategory.fromKey(categoryKey),
            graph = parsedGraph,
            tags = if (tagsCsv.isEmpty()) emptyList() else tagsCsv.split(TAG_SEPARATOR),
            isBundled = false,
        )
    }

    private companion object {
        const val ASSETS_BUNDLED_DIR = "presets/pipelines"
        const val JSON_EXTENSION = ".json"
        const val TAG_SEPARATOR = ","
    }
}
