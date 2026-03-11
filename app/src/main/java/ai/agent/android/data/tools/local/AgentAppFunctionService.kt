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
        // With androidx.appfunctions, the Jetpack library usually handles the routing
        // via generated code if we use the provided base classes, but since this API
        // is very new (1.0.0-alpha08), we declare the service here to ensure the 
        // system can bind to it. The actual routing to GetSystemTimeTool and 
        // SetAlarmTool methods annotated with @AppFunction will be handled by the 
        // Jetpack compiler's generated code when properly integrated.
        
        // This is an abstract method in the Android 16 SDK, so we must not call super.
        // The Jetpack appfunctions library will typically provide its own service implementation
        // that handles this routing if configured fully via its provided BaseAppFunctionService.
        // For now, we leave it as a stub.
    }
}
