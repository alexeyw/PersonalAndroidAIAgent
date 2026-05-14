package ai.agent.android.data.tools.local

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.ToolRisk
import android.content.Context
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager class responsible for orchestrating AppFunctions execution.
 * It provides a unified interface for the AI agent to discover and trigger
 * application functions securely through the Android 16 AppFunctionManager.
 */
class LocalAppFunctionManager(private val context: Context, private val codec: AppFunctionDataCodec) {

    private val appFunctionManager: AppFunctionManager? by lazy {
        AppFunctionManager.getInstance(context)
    }

    /**
     * In-memory snapshot of the most recent discovery pass, keyed by the AppFunction id
     * (the same string surfaced through [getAvailableFunctions] as `AgentTool.name`).
     *
     * Holds the two facts the caller-side execution path needs but [AgentTool] cannot
     * carry: the target package and the typed parameter metadata required by
     * `AppFunctionDataCodec.encode`. Rewritten on every [getAvailableFunctions] call —
     * the freshest system-reported state always wins; functions that have disappeared
     * from discovery are evicted.
     */
    private val discoveredCache = ConcurrentHashMap<String, DiscoveredAppFunction>()

    /**
     * Executes an AppFunction by its identifier using the system AppFunctionManager.
     *
     * @param request The request object containing targetPackageName, functionIdentifier, and parameters.
     * @return ExecuteAppFunctionResponse
     */
    suspend fun executeFunction(request: ExecuteAppFunctionRequest): ExecuteAppFunctionResponse {
        val manager =
            appFunctionManager ?: throw IllegalStateException("AppFunctionManager is not available on this device.")
        return manager.executeAppFunction(request)
    }

    /**
     * End-to-end invocation of a discovered AppFunction by name:
     *  1. Looks up the cached parameter metadata and target package (re-discovers on cache
     *     miss).
     *  2. Encodes [arguments] via [AppFunctionDataCodec.encode] into a typed payload.
     *  3. Builds [ExecuteAppFunctionRequest] and dispatches through the system
     *     `AppFunctionManager`.
     *  4. Renders the response through [AppFunctionDataCodec.decode] back into a flat JSON
     *     string suitable for the agent's observation log.
     *
     * Any [AppFunctionException] thrown by the system is wrapped into an
     * [IllegalStateException] with the original cause preserved, so the caller (typically
     * `ToolRepositoryImpl`) does not have to depend on the `androidx.appfunctions` exception
     * hierarchy. This is the single entry point that touches Android AppFunctions types on
     * behalf of the caller path.
     *
     * @throws IllegalArgumentException if [name] is not a currently discovered AppFunction.
     * @throws IllegalStateException if the system reports a failure or required metadata is
     *   missing.
     */
    suspend fun invokeByName(name: String, arguments: String): String {
        val parameters = getParametersMetadata(name)
            ?: throw IllegalArgumentException("AppFunction $name has no parameter metadata")
        val targetPackage = getTargetPackageName(name)
            ?: throw IllegalArgumentException("AppFunction $name has no target package")
        return runCatching {
            val data = codec.encode(arguments, parameters)
            val request = ExecuteAppFunctionRequest(targetPackage, name, data)
            val response = executeFunction(request)
            codec.decode(response)
        }.getOrElse { e ->
            if (e is AppFunctionException) {
                throw IllegalStateException("AppFunction $name failed: ${e.message}", e)
            }
            throw e
        }
    }

    /**
     * Queries the system to discover available AppFunctions for this application.
     *
     * Every discovered AppFunction is tagged with [ToolRisk.SENSITIVE]. The platform
     * `AppFunctionManager` metadata exposes no trustworthy signal about side effects —
     * a function called `read_*` from a third-party package may still write, send or
     * delete. Defaulting conservatively means the Human-in-the-Loop gate prompts the
     * user on the first call; users can downgrade specific AppFunctions to
     * [ToolRisk.READ_ONLY] (or upgrade to [ToolRisk.DESTRUCTIVE]) via
     * `SettingsRepository.setAppFunctionRiskOverride`. The override is the
     * authoritative source consumed by `ToolRepository.getRisk`.
     */
    suspend fun getAvailableFunctions(): List<AgentTool> {
        val manager = appFunctionManager ?: run {
            discoveredCache.clear()
            return emptyList()
        }
        val searchSpec = AppFunctionSearchSpec()

        // Observe app functions once, refresh the discovery cache, and project to the
        // public AgentTool model. The cache rewrite mirrors the observation slice — any
        // function the system no longer reports is evicted so stale execution targets
        // cannot leak into the caller-side path.
        return manager.observeAppFunctions(searchSpec)
            .map { packages ->
                val fresh = mutableMapOf<String, DiscoveredAppFunction>()
                val tools = packages.flatMap { pkg ->
                    pkg.appFunctions.map { metadata ->
                        fresh[metadata.id] = DiscoveredAppFunction(
                            packageName = pkg.packageName,
                            parameters = metadata.parameters,
                        )
                        AgentTool(
                            name = metadata.id,
                            description = metadata.description ?: "App function ${metadata.id}",
                            parameters = generateJsonSchema(metadata.parameters),
                            risk = ToolRisk.SENSITIVE,
                        )
                    }
                }
                discoveredCache.keys.retainAll(fresh.keys)
                discoveredCache.putAll(fresh)
                tools
            }.first()
    }

    /**
     * Returns the typed [AppFunctionParameterMetadata] tree for [name], required by
     * `AppFunctionDataCodec.encode` to translate LLM-emitted JSON arguments into an
     * [androidx.appfunctions.AppFunctionData] payload.
     *
     * Performs a transparent re-discovery pass through [getAvailableFunctions] when the
     * cache misses, so callers that ask before any explicit discovery still get a
     * correct answer. Returns `null` only when [name] is genuinely not a discovered
     * AppFunction on this device.
     */
    suspend fun getParametersMetadata(name: String): List<AppFunctionParameterMetadata>? {
        discoveredCache[name]?.let { return it.parameters }
        getAvailableFunctions()
        return discoveredCache[name]?.parameters
    }

    /**
     * Returns the source `packageName` reported by the system for the discovered
     * AppFunction [name]. Required to build [androidx.appfunctions.ExecuteAppFunctionRequest].
     *
     * Same cache-then-rediscover policy as [getParametersMetadata]; `null` means the
     * function is not visible on this device.
     */
    suspend fun getTargetPackageName(name: String): String? {
        discoveredCache[name]?.let { return it.packageName }
        getAvailableFunctions()
        return discoveredCache[name]?.packageName
    }

    /**
     * Snapshot of the per-AppFunction facts that [AgentTool] cannot carry — held in
     * [discoveredCache] for the caller-side execution path.
     */
    private data class DiscoveredAppFunction(
        val packageName: String,
        val parameters: List<AppFunctionParameterMetadata>,
    )

    private fun generateJsonSchema(parameters: List<AppFunctionParameterMetadata>): String {
        val root = JSONObject()
        root.put("type", "object")

        val properties = JSONObject()
        val required = JSONArray()

        for (param in parameters) {
            properties.put(param.name, mapTypeToJsonSchema(param.dataType, param.description))
            if (param.isRequired) {
                required.put(param.name)
            }
        }

        root.put("properties", properties)
        if (required.length() > 0) {
            root.put("required", required)
        }

        return root.toString()
    }

    private fun mapTypeToJsonSchema(dataType: AppFunctionDataTypeMetadata, description: String?): JSONObject {
        val schema = JSONObject()

        if (description != null) {
            schema.put("description", description)
        }

        when (dataType) {
            is AppFunctionStringTypeMetadata -> schema.put("type", "string")
            is AppFunctionIntTypeMetadata, is AppFunctionLongTypeMetadata -> schema.put("type", "integer")
            is AppFunctionDoubleTypeMetadata, is AppFunctionFloatTypeMetadata -> schema.put("type", "number")
            is AppFunctionBooleanTypeMetadata -> schema.put("type", "boolean")
            is AppFunctionArrayTypeMetadata -> {
                schema.put("type", "array")
                // Assume array of strings for simplicity if we can't extract the inner type easily
                // or we could recursively call mapTypeToJsonSchema if AppFunctionArrayTypeMetadata exposes itemType
                val itemsSchema = JSONObject().put("type", "string")
                schema.put("items", itemsSchema)
            }
            is AppFunctionObjectTypeMetadata, is AppFunctionReferenceTypeMetadata -> {
                schema.put("type", "object")
            }
            else -> schema.put("type", "string")
        }

        if (dataType.isNullable) {
            // JSON Schema approach for nullable: ["type", "null"] or oneOf.
            // For simple agent usage, standard types are often enough.
        }

        return schema
    }
}
