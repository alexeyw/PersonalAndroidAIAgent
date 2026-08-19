package app.knotwork.android.presentation.ui.automation

import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.android.domain.models.ExternalAutomationJournalEntry

/**
 * State of the external-automation request journal screen: the contract's
 * current posture, and every request that has reached it.
 *
 * @property contractEnabled Whether the entry point currently accepts anything.
 * @property boundPipelineName Name of the one pipeline outside apps may run, or
 *   `null` when nothing is bound — including the case where the bound id no
 *   longer resolves to a pipeline, which the authorizer treats the same way.
 * @property entries The journal, newest first, or `null` while the first read
 *   from the encrypted store is still in flight. The nullable-versus-empty
 *   distinction is load-bearing: an empty journal is a teaching state, an
 *   unread one is a skeleton.
 */
data class ExternalAutomationJournalUiState(
    val contractEnabled: Boolean = SettingsDefaults.EXTERNAL_AUTOMATION_ENABLED_DEFAULT,
    val boundPipelineName: String? = null,
    val entries: List<ExternalAutomationJournalEntry>? = null,
)
