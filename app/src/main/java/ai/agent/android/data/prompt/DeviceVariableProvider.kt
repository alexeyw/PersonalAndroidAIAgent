package ai.agent.android.data.prompt

import ai.agent.android.domain.prompt.PromptVariableProvider
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$DEVICE` placeholder.
 *
 * Resolves to a short "manufacturer + model + Android version" descriptor
 * (e.g. `Samsung Galaxy S25 Ultra · Android 16`). The string is meant
 * for prompt grounding — letting the model tailor instructions to the
 * device class without leaking serial numbers or build fingerprints.
 *
 * The [androidVersionProvider] indirection keeps the provider unit
 * testable on the JVM where [Build.VERSION.RELEASE] is the empty string.
 */
@Singleton
class DeviceVariableProvider internal constructor(
    private val manufacturerProvider: () -> String,
    private val modelProvider: () -> String,
    private val androidVersionProvider: () -> String,
) : PromptVariableProvider {

    @Inject
    constructor() : this(
        manufacturerProvider = { Build.MANUFACTURER.orEmpty() },
        modelProvider = { Build.MODEL.orEmpty() },
        androidVersionProvider = { Build.VERSION.RELEASE.orEmpty() },
    )

    override fun key(): String = KEY

    override suspend fun resolve(): String {
        val manufacturer = manufacturerProvider().trim().replaceFirstChar { it.uppercaseChar() }
        val model = modelProvider().trim()
        val deviceLabel = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
        val version = androidVersionProvider().trim().takeIf { it.isNotBlank() }?.let { "Android $it" }
        return listOfNotNull(deviceLabel.takeIf { it.isNotBlank() }, version).joinToString(SEPARATOR)
            .ifBlank { UNKNOWN }
    }

    private companion object {
        const val KEY = "DEVICE"
        const val SEPARATOR = " · "
        const val UNKNOWN = "unknown device"
    }
}
