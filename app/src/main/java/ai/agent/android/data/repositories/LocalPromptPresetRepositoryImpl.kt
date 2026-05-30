package ai.agent.android.data.repositories

import ai.agent.android.data.local.TagsCsv
import ai.agent.android.data.local.dao.PromptPresetDao
import ai.agent.android.data.local.models.PromptPresetEntity
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptPreset
import ai.agent.android.domain.models.PromptPresetImportOutcome
import ai.agent.android.domain.promptio.PromptPresetJsonSerializer
import ai.agent.android.domain.repositories.PromptPresetRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Local implementation of [PromptPresetRepository] composed from two
 * independent stores:
 *
 * - **Bundled** presets are read lazily on first subscription from
 *   `assets/presets/prompts`, parsed via [PromptPresetJsonSerializer],
 *   and cached for the lifetime of the process (the asset set cannot
 *   change at runtime, so re-reading on every subscription is pure
 *   waste).
 * - **User** presets are observed from [PromptPresetDao] and mapped to
 *   the domain shape on every emission.
 *
 * Asset I/O and JSON parsing run on [Dispatchers.IO]. Malformed asset
 * files are logged and skipped rather than failing the whole catalogue
 * load, so a single corrupt file cannot hide the rest of the starter set
 * from the user.
 */
class LocalPromptPresetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PromptPresetDao,
) : PromptPresetRepository {

    private val bundledCacheMutex = Mutex()

    @Volatile
    private var bundledCache: List<PromptPreset>? = null

    override fun getBundledPresets(): Flow<List<PromptPreset>> = flow {
        emit(loadBundledOnce())
    }

    override fun getUserPresets(): Flow<List<PromptPreset>> =
        dao.getAll().map { rows -> rows.mapNotNull { it.toDomainModelOrNull() } }

    override fun getPresetsForType(nodeType: NodeType): Flow<List<PromptPreset>> = combine(
        getBundledPresets(),
        dao.getAllForType(nodeType.name).map { rows -> rows.mapNotNull { it.toDomainModelOrNull() } },
    ) { bundled, user ->
        bundled.filter { it.nodeType == nodeType } + user
    }

    override suspend fun getPresetById(id: String): PromptPreset? {
        loadBundledOnce().firstOrNull { it.id == id }?.let { return it }
        return dao.getById(id)?.toDomainModelOrNull()
    }

    override suspend fun saveUserPreset(preset: PromptPreset) {
        require(!preset.isBundled) {
            "Bundled presets are read-only and cannot be saved as user presets"
        }
        val entity = PromptPresetEntity(
            id = preset.id,
            name = preset.name,
            description = preset.description,
            nodeTypeKey = preset.nodeType.name,
            systemPrompt = preset.systemPrompt,
            tagsCsv = TagsCsv.encode(preset.tags),
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
    }

    override suspend fun deleteUserPreset(id: String) {
        dao.deleteById(id)
    }

    private suspend fun loadBundledOnce(): List<PromptPreset> {
        bundledCache?.let { return it }
        return bundledCacheMutex.withLock {
            bundledCache?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) { readBundledFromAssets() }
            bundledCache = loaded
            loaded
        }
    }

    private fun readBundledFromAssets(): List<PromptPreset> {
        val assetManager = context.assets
        val fileNames: Array<String> = try {
            assetManager.list(ASSETS_BUNDLED_DIR) ?: emptyArray()
        } catch (e: java.io.IOException) {
            Timber.w(e, "No bundled prompt-preset directory at assets/%s", ASSETS_BUNDLED_DIR)
            return emptyList()
        }

        return fileNames
            .filter { it.endsWith(JSON_EXTENSION, ignoreCase = true) }
            .mapNotNull { fileName -> readBundledFile(fileName) }
    }

    private fun readBundledFile(fileName: String): PromptPreset? {
        val path = "$ASSETS_BUNDLED_DIR/$fileName"
        val jsonText = try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            Timber.w(e, "Failed to read bundled prompt-preset file: %s", path)
            return null
        }

        return when (val outcome = PromptPresetJsonSerializer.parse(jsonText, isBundled = true)) {
            is PromptPresetImportOutcome.Success -> outcome.preset
            is PromptPresetImportOutcome.SchemaMismatch -> {
                Timber.w(
                    "Bundled prompt preset %s has schemaVersion=%d (expected %d); " +
                        "loading on best-effort basis",
                    path,
                    outcome.foundVersion,
                    outcome.expectedVersion,
                )
                outcome.preset
            }
            is PromptPresetImportOutcome.Failure -> {
                Timber.w("Skipping malformed bundled prompt preset %s: %s", path, outcome.message)
                null
            }
        }
    }

    /**
     * Decodes a Room row into the domain model, or returns `null` if the
     * stored `nodeTypeKey` no longer maps to a known [NodeType] (e.g.
     * after an enum rename). A `null` here surfaces in the picker as a
     * missing row rather than crashing the entire Flow emission.
     */
    private fun PromptPresetEntity.toDomainModelOrNull(): PromptPreset? {
        val resolvedNodeType = try {
            NodeType.valueOf(nodeTypeKey)
        } catch (e: IllegalArgumentException) {
            Timber.w(
                "User prompt preset %s references unknown NodeType '%s'; dropping from catalogue",
                id,
                nodeTypeKey,
            )
            return null
        }
        return PromptPreset(
            id = id,
            name = name,
            description = description,
            nodeType = resolvedNodeType,
            systemPrompt = systemPrompt,
            tags = TagsCsv.decode(tagsCsv),
            isBundled = false,
        )
    }

    private companion object {
        const val ASSETS_BUNDLED_DIR = "presets/prompts"
        const val JSON_EXTENSION = ".json"
    }
}
