package app.knotwork.android.presentation.ui.tools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.models.McpAuth
import app.knotwork.android.domain.models.McpServerConfig
import app.knotwork.android.domain.models.McpTransport
import app.knotwork.android.domain.models.UpdateMcpServerResult
import app.knotwork.android.domain.repositories.McpServerRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.design.screens.tools.AddMcpServerForm
import app.knotwork.design.screens.tools.McpAuthSelector
import app.knotwork.design.screens.tools.McpHeaderRow
import app.knotwork.design.screens.tools.McpTransportOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the standalone `McpServerConfigScreen`.
 *
 * Drives the [AddMcpServerForm] state for both Add and Edit modes:
 *
 *  - **Add** — no `originalUrl` nav argument; submission calls
 *    `SettingsRepository.addMcpServer`.
 *  - **Edit** — `originalUrl` is supplied via the nav graph; the VM
 *    fetches the existing config from `SettingsRepository.mcpServers`
 *    on first observation and pre-fills the form. Submission calls
 *    `SettingsRepository.updateMcpServer` and disconnects the
 *    underlying client so the next fetch picks up new headers.
 */
@HiltViewModel
class McpServerConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val mcpServerRepository: McpServerRepository,
) : ViewModel() {

    /**
     * Original URL passed via the nav argument. `null` or blank means
     * Add mode; non-null means Edit mode and triggers a one-shot read
     * from `SettingsRepository.mcpServers` to populate the form.
     */
    private val originalUrl: String? = savedStateHandle
        .get<String>(EXTRA_ORIGINAL_URL)
        ?.takeIf { it.isNotBlank() }

    private val _form = MutableStateFlow(
        AddMcpServerForm(editingUrl = originalUrl),
    )
    val form: StateFlow<AddMcpServerForm> = _form.asStateFlow()

    /** One-shot terminal events emitted after a successful submission. */
    private val _events = MutableStateFlow<Event?>(null)
    val events: StateFlow<Event?> = _events.asStateFlow()

    init {
        if (originalUrl != null) {
            viewModelScope.launch {
                val existing = settingsRepository.mcpServers.first().firstOrNull { it.url == originalUrl }
                if (existing != null) {
                    _form.update { it.fromConfig(existing) }
                }
            }
        }
    }

    fun onUrlChange(value: String) {
        _form.update { it.copy(url = value, urlError = validateUrl(input = value, requireNonEmpty = false)) }
    }

    fun onNameChange(value: String) = _form.update { it.copy(name = value) }

    fun onTransportSelect(option: McpTransportOption) = _form.update { it.copy(transport = option) }

    fun onAuthTypeSelect(option: McpAuthSelector) = _form.update { it.copy(authType = option) }

    fun onBearerTokenChange(value: String) = _form.update { it.copy(bearerToken = value) }

    fun onBasicUsernameChange(value: String) = _form.update { it.copy(basicUsername = value) }

    fun onBasicPasswordChange(value: String) = _form.update { it.copy(basicPassword = value) }

    fun onApiKeyHeaderNameChange(value: String) = _form.update { it.copy(apiKeyHeaderName = value) }

    fun onApiKeyValueChange(value: String) = _form.update { it.copy(apiKeyValue = value) }

    fun onHeaderAdd() = _form.update { it.copy(headers = it.headers + McpHeaderRow()) }

    fun onHeaderChange(index: Int, key: String, value: String) {
        _form.update { current ->
            if (index !in current.headers.indices) return@update current
            val next = current.headers.toMutableList()
            next[index] = McpHeaderRow(key = key, value = value)
            current.copy(headers = next)
        }
    }

    fun onHeaderRemove(index: Int) {
        _form.update { current ->
            if (index !in current.headers.indices) return@update current
            val next = current.headers.toMutableList()
            next.removeAt(index)
            current.copy(headers = next)
        }
    }

    /**
     * Persists the current form. On invalid URL, surfaces the error in
     * the form state and stays open. On a URL collision with another
     * persisted server (Edit mode only), surfaces the collision message
     * and stays open. On success, emits [Event.Saved] so the host can
     * `popBackStack`.
     */
    fun onSubmit() {
        val current = _form.value
        val error = validateUrl(input = current.url, requireNonEmpty = true)
        if (error != null) {
            _form.update { it.copy(urlError = error) }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(submitting = true, urlError = null) }
            val config = current.toDomain()
            if (originalUrl != null) {
                // Drop the cached client so the next fetch reconnects with the
                // new headers/transport/URL instead of reusing the old session.
                mcpServerRepository.disconnect(serverUrl = originalUrl)
                val outcome = settingsRepository.updateMcpServer(originalUrl = originalUrl, updated = config)
                when (outcome) {
                    is UpdateMcpServerResult.Success -> _events.value = Event.Saved
                    is UpdateMcpServerResult.UrlCollision -> {
                        _form.update {
                            it.copy(
                                submitting = false,
                                urlError = formatCollisionMessage(outcome),
                            )
                        }
                    }
                }
            } else {
                settingsRepository.addMcpServer(config = config)
                _events.value = Event.Saved
            }
        }
    }

    /** Acknowledged by the host after [Event.Saved] has been handled. */
    fun consumeEvent() {
        _events.value = null
    }

    /** Terminal events. Currently just `Saved`; expand if more arise. */
    sealed interface Event {
        data object Saved : Event
    }

    companion object {
        /** Nav-argument key — must match `NavRoutes.MCP_SERVER_CONFIG_URL_ARG`. */
        const val EXTRA_ORIGINAL_URL: String = "originalUrl"

        private const val URL_REQUIRED_MESSAGE = "Enter a server URL."
        private const val URL_SCHEME_REQUIRED_MESSAGE =
            "URL must start with http://, https:// or mcp://."
        private const val URL_HOST_REQUIRED_MESSAGE = "URL needs a host name."

        /**
         * Renders a [UpdateMcpServerResult.UrlCollision] into the inline
         * error string shown beneath the URL field. Falls back to the
         * URL itself when the colliding row has no display name set.
         */
        internal fun formatCollisionMessage(collision: UpdateMcpServerResult.UrlCollision): String {
            val label = collision.collidingDisplayName ?: collision.collidingUrl
            return "A server with this URL already exists: \"$label\"."
        }

        /**
         * Validates [input]. When [requireNonEmpty] is `true`, an empty
         * string returns the "required" error; otherwise empty silently
         * passes so the user isn't yelled at while typing.
         */
        @Suppress("ReturnCount")
        internal fun validateUrl(input: String, requireNonEmpty: Boolean): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return if (requireNonEmpty) URL_REQUIRED_MESSAGE else null
            val lower = trimmed.lowercase()
            val schemes = listOf("http://", "https://", "mcp://")
            val matched = schemes.firstOrNull { lower.startsWith(prefix = it) }
                ?: return URL_SCHEME_REQUIRED_MESSAGE
            val afterScheme = trimmed.substring(startIndex = matched.length)
            if (afterScheme.isEmpty() || afterScheme.startsWith(prefix = "/")) {
                return URL_HOST_REQUIRED_MESSAGE
            }
            return null
        }
    }
}

private fun AddMcpServerForm.fromConfig(config: McpServerConfig): AddMcpServerForm {
    val base = copy(
        url = config.url,
        urlError = null,
        name = config.name.orEmpty(),
        transport = config.transport.toCatalogOption(),
        authType = McpAuthSelector.NONE,
        bearerToken = "",
        basicUsername = "",
        basicPassword = "",
        apiKeyHeaderName = "",
        apiKeyValue = "",
        headers = config.headers.entries.map { McpHeaderRow(key = it.key, value = it.value) },
        editingUrl = config.url,
    )
    return when (val auth = config.auth) {
        is McpAuth.None -> base
        is McpAuth.Bearer -> base.copy(authType = McpAuthSelector.BEARER, bearerToken = auth.token)
        is McpAuth.Basic -> base.copy(
            authType = McpAuthSelector.BASIC,
            basicUsername = auth.username,
            basicPassword = auth.password,
        )
        is McpAuth.ApiKey -> base.copy(
            authType = McpAuthSelector.API_KEY,
            apiKeyHeaderName = auth.headerName,
            apiKeyValue = auth.value,
        )
    }
}

private fun AddMcpServerForm.toDomain(): McpServerConfig {
    val cleaned = headers
        .filter { it.key.isNotBlank() }
        .associate { it.key.trim() to it.value }
    return McpServerConfig(
        url = url.trim(),
        name = name.trim().takeIf { it.isNotBlank() },
        transport = transport.toDomain(),
        auth = authToDomain(),
        headers = cleaned,
    )
}

private fun AddMcpServerForm.authToDomain(): McpAuth = when (authType) {
    McpAuthSelector.NONE -> McpAuth.None
    McpAuthSelector.BEARER -> if (bearerToken.isBlank()) McpAuth.None else McpAuth.Bearer(token = bearerToken)
    McpAuthSelector.BASIC -> if (basicUsername.isBlank() && basicPassword.isBlank()) {
        McpAuth.None
    } else {
        McpAuth.Basic(username = basicUsername, password = basicPassword)
    }
    McpAuthSelector.API_KEY -> if (apiKeyHeaderName.isBlank() || apiKeyValue.isBlank()) {
        McpAuth.None
    } else {
        McpAuth.ApiKey(headerName = apiKeyHeaderName.trim(), value = apiKeyValue)
    }
}

private fun McpTransportOption.toDomain(): McpTransport = when (this) {
    McpTransportOption.SSE -> McpTransport.SSE
    McpTransportOption.StreamableHttp -> McpTransport.STREAMABLE_HTTP
}

private fun McpTransport.toCatalogOption(): McpTransportOption = when (this) {
    McpTransport.SSE -> McpTransportOption.SSE
    McpTransport.STREAMABLE_HTTP -> McpTransportOption.StreamableHttp
}
