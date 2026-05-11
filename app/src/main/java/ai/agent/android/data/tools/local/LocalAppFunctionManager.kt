package ai.agent.android.data.tools.local

import ai.agent.android.domain.models.AgentTool
import android.content.Context
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

/**
 * Manager class responsible for orchestrating AppFunctions execution.
 * It provides a unified interface for the AI agent to discover and trigger
 * application functions securely through the Android 16 AppFunctionManager.
 */
class LocalAppFunctionManager(private val context: Context) {

    private val appFunctionManager: AppFunctionManager? by lazy {
        AppFunctionManager.getInstance(context)
    }

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
     * Queries the system to discover available AppFunctions for this application.
     */
    suspend fun getAvailableFunctions(): List<AgentTool> {
        val manager = appFunctionManager ?: return emptyList()
        val searchSpec = AppFunctionSearchSpec()

        // Observe app functions once and map them to our domain model
        return manager.observeAppFunctions(searchSpec)
            .map { packages ->
                packages.flatMap { pkg ->
                    pkg.appFunctions.map { metadata ->
                        AgentTool(
                            name = metadata.id,
                            description = metadata.description ?: "App function ${metadata.id}",
                            parameters = generateJsonSchema(metadata.parameters),
                        )
                    }
                }
            }.first()
    }

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
