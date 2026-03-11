package ai.agent.android.data.tools.local

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/**
 * Manager class responsible for orchestrating AppFunctions execution.
 * It provides a unified interface for the AI agent to discover and trigger
 * application functions securely through the Android 16 AppFunctionManager.
 */
class LocalAppFunctionManager(private val context: Context) {

    /**
     * Executes an AppFunction by its identifier using the system AppFunctionManager.
     *
     * @param targetPackageName The package name of the app providing the function.
     * @param functionIdentifier The unique identifier of the function to execute.
     * @param parameters The parameters for the function, as a serialized string or bundle (depending on implementation).
     *                   For simplicity in this initial version, we pass raw parameters and construct a request.
     * @param callback The callback to handle the execution response.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun executeFunction(
        targetPackageName: String,
        functionIdentifier: String,
        parameters: android.app.appsearch.GenericDocument,
        executor: Executor,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
    ) {
        val appFunctionManager = context.getSystemService(AppFunctionManager::class.java)
            ?: throw IllegalStateException("AppFunctionManager is not available on this device.")

        val request = ExecuteAppFunctionRequest.Builder(targetPackageName, functionIdentifier)
            .setParameters(parameters)
            .build()

        val cancellationSignal = CancellationSignal()

        appFunctionManager.executeAppFunction(
            request,
            executor,
            cancellationSignal,
            callback
        )
    }

    // We can also implement methods here to query AppSearch for available functions,
    // which will be used later for the UI to toggle functions on/off.
}
