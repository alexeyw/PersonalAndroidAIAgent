package ai.agent.android.data.tools.local

import android.content.Context
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import ai.agent.android.domain.models.AgentTool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
    suspend fun executeFunction(
        request: ExecuteAppFunctionRequest
    ): ExecuteAppFunctionResponse {
        val manager = appFunctionManager ?: throw IllegalStateException("AppFunctionManager is not available on this device.")
        return manager.executeAppFunction(request)
    }

    /**
     * Queries the system to discover available AppFunctions for this application.
     */
    suspend fun getAvailableFunctions(): List<AgentTool> {
        val manager = appFunctionManager ?: return emptyList()
        val searchSpec = AppFunctionSearchSpec(packageNames = setOf(context.packageName))
        
        // Observe app functions once and map them to our domain model
        return manager.observeAppFunctions(searchSpec)
            .map { packages ->
                packages.flatMap { pkg ->
                    pkg.appFunctions.map { metadata ->
                        // In a real implementation we would convert the schema to a JSON string
                        // that the LLM can understand, but here we just map basic info.
                        AgentTool(
                            name = metadata.id,
                            description = metadata.description ?: "App function \${metadata.id}",
                            parameters = "{}" // TODO: Parse metadata.schema and parameters into JSON Schema
                        )
                    }
                }
            }.first()
    }
}
