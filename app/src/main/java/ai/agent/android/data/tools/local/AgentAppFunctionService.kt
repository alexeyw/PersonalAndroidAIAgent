package ai.agent.android.data.tools.local

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.content.pm.SigningInfo
import android.os.CancellationSignal
import android.os.OutcomeReceiver

/**
 * The service that exposes our AppFunctions to the system and AI agents.
 * 
 * Note: If we use androidx.appfunctions, we might not strictly need to manually
 * override onExecuteFunction unless we are doing custom routing, but it's required
 * to declare a service that extends AppFunctionService in the manifest.
 */
class AgentAppFunctionService : AppFunctionService() {

    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSignature: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
    ) {
        try {
            val result = dispatchFunction(request)
            callback.onResult(result)
        } catch (e: AppFunctionException) {
            callback.onError(e)
        } catch (e: Exception) {
            callback.onError(
                AppFunctionException(AppFunctionException.ERROR_APP_UNKNOWN_ERROR, e.message ?: "")
            )
        }
    }

    private fun dispatchFunction(request: ExecuteAppFunctionRequest): ExecuteAppFunctionResponse {
        throw AppFunctionException(
            AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
            "AppFunction '${request.functionIdentifier}' routing not implemented"
        )
    }
}
