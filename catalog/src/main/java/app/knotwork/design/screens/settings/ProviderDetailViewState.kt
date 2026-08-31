package app.knotwork.design.screens.settings

/**
 * Everything the provider detail surface needs, already resolved.
 *
 * No provider enum: `:app` decides what a given provider has — Ollama a base URL
 * and no API key, the others a key and a model list — so adding a provider never
 * reaches this module.
 *
 * @property title Screen title, e.g. "OpenAI settings".
 * @property providerLabel The provider's own name, shown on the row.
 * @property backContentDescription Accessible label of the back control.
 * @property apiKey Current key, or `null` when the provider has none. `null` and
 *   `""` are different states: the first hides the field, the second shows an
 *   empty one the user has yet to fill.
 * @property apiKeyLabel Label of the key field.
 * @property model Current model id.
 * @property modelLabel Label of the model field.
 * @property availableModels Ids offered in the model dropdown; empty means the
 *   model is typed rather than picked.
 * @property ollama Base-URL and context-window inputs, when the provider has them.
 * @property cleartextConsent Pending consent for an unencrypted origin, or `null`.
 * @property retry The cloud-retry policy, which applies to every provider.
 */
data class ProviderDetailViewState(
    val title: String,
    val providerLabel: String,
    val backContentDescription: String,
    val apiKey: String?,
    val apiKeyLabel: String,
    val model: String,
    val modelLabel: String,
    val availableModels: List<String>,
    val ollama: OllamaProviderInputs? = null,
    val cleartextConsent: CleartextConsentUi? = null,
    val retry: CloudRetryViewState,
)

/**
 * The unencrypted-origin notice.
 *
 * @property body The sentence naming the origin.
 * @property actionLabel Label of the approve action.
 */
data class CleartextConsentUi(val body: String, val actionLabel: String)

/**
 * The cloud-retry policy sliders.
 *
 * Bounds and step counts arrive resolved rather than being constants here: they
 * come from the same settings defaults the store coerces against, and a second
 * copy in this module would be a range that could disagree with the one actually
 * enforced.
 *
 * @property sectionTitle Section heading, also the hint's subject.
 * @property sectionAnchor Hint anchor of the section heading.
 * @property attempts Current attempt budget; `1` disables retries.
 * @property attemptsLabel Label of the attempts slider.
 * @property attemptsValueLabel Current attempts, rendered.
 * @property attemptsRange Allowed attempts.
 * @property attemptsSteps Discrete stops between the bounds.
 * @property attemptsAnchor Hint anchor of the attempts slider.
 * @property delayMs Current base backoff delay.
 * @property delayLabel Label of the delay slider.
 * @property delayValueLabel Current delay, rendered with its unit.
 * @property delayRange Allowed delay.
 * @property delayAnchor Hint anchor of the delay slider.
 */
data class CloudRetryViewState(
    val sectionTitle: String,
    val sectionAnchor: String,
    val attempts: Int,
    val attemptsLabel: String,
    val attemptsValueLabel: String,
    val attemptsRange: ClosedFloatingPointRange<Float>,
    val attemptsSteps: Int,
    val attemptsAnchor: String,
    val delayMs: Long,
    val delayLabel: String,
    val delayValueLabel: String,
    val delayRange: ClosedFloatingPointRange<Float>,
    val delayAnchor: String,
)

/**
 * Callback bag for [ProviderDetailContent].
 *
 * @property onBack Pop back to the provider picker.
 * @property onApiKeyChange The key field changed.
 * @property onModelChange The model field changed.
 * @property onOllamaBaseUrlChange The base-URL field changed.
 * @property onOllamaContextWindowChange The context-window field changed.
 * @property onApproveCleartextOrigin The user allowed the unencrypted origin.
 * @property onRetryAttemptsChange The attempts slider settled.
 * @property onRetryDelayChange The delay slider settled.
 */
data class ProviderDetailCallbacks(
    val onBack: () -> Unit,
    val onApiKeyChange: (String) -> Unit,
    val onModelChange: (String) -> Unit,
    val onOllamaBaseUrlChange: (String) -> Unit,
    val onOllamaContextWindowChange: (String) -> Unit,
    val onApproveCleartextOrigin: () -> Unit,
    val onRetryAttemptsChange: (Int) -> Unit,
    val onRetryDelayChange: (Long) -> Unit,
)

/** Inert callbacks, so a preview or a snapshot needs none. */
fun noopProviderDetailCallbacks(): ProviderDetailCallbacks = ProviderDetailCallbacks(
    onBack = {},
    onApiKeyChange = {},
    onModelChange = {},
    onOllamaBaseUrlChange = {},
    onOllamaContextWindowChange = {},
    onApproveCleartextOrigin = {},
    onRetryAttemptsChange = {},
    onRetryDelayChange = {},
)
