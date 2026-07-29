package app.knotwork.android.presentation.ui.chat.archive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.usecases.ArchiveChatUseCase
import app.knotwork.android.domain.usecases.UnarchiveChatUseCase
import app.knotwork.design.theme.KnotworkTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Compose happy path for the chat archive: **archive → find it in the archive →
 * restore**, driven end to end through the real
 * [ArchiveChatUseCase] / [UnarchiveChatUseCase] and a real
 * [ChatArchiveViewModel] over a fake [ChatRepository] whose archive writes push
 * back into the observed flows — so the round-trip is genuine rather than a
 * sequence of stubbed reads.
 *
 * The archive screen itself only ever *shows* archived chats, so the archiving
 * half runs through the use case (the drawer's affordance) and the screen then
 * has to reflect it without being told.
 */
class ChatArchiveScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun archiveChat_appearsInArchive_thenRestoreRemovesIt() = runBlocking {
        val sessions = MutableStateFlow(
            listOf(
                ChatSession(id = SESSION_ID, name = CHAT_TITLE, updatedAt = 1_000L),
                ChatSession(id = "other", name = "Untouched chat", updatedAt = 900L),
            ),
        )
        val chatRepository = mockk<ChatRepository>(relaxed = true) {
            every { getArchivedSessionsFlow() } returns sessions.map { all -> all.filter { it.isArchived } }
        }
        coEvery { chatRepository.setSessionArchived(any(), any()) } answers {
            val id = firstArg<String>()
            val archived = secondArg<Boolean>()
            sessions.value = sessions.value.map { session ->
                if (session.id == id) {
                    session.copy(isArchived = archived, archivedAt = if (archived) 2_000L else null)
                } else {
                    session
                }
            }
        }

        val viewModel = ChatArchiveViewModel(
            chatRepository = chatRepository,
            unarchiveChatUseCase = UnarchiveChatUseCase(chatRepository),
            exportChatUseCase = mockk(relaxed = true),
        )

        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) {
                ChatArchiveScreen(onBack = {}, onOpenChat = {}, viewModel = viewModel)
            }
        }

        // Nothing archived yet — the teaching empty state, and no CTA on it.
        composeTestRule.onNodeWithText(EMPTY_TITLE).assertIsDisplayed()

        // Archive the chat the way the drawer does.
        ArchiveChatUseCase(chatRepository).invoke(SESSION_ID).getOrThrow()

        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule.onAllNodesWithTextSafe(CHAT_TITLE) > 0
        }
        composeTestRule.onNodeWithText(CHAT_TITLE).assertIsDisplayed()
        // The untouched chat must not have been dragged along.
        composeTestRule.onAllNodesWithTextSafe("Untouched chat").let { count ->
            check(count == 0) { "Only the archived chat belongs on this screen, found $count others" }
        }

        // Restore it from the inline pill.
        composeTestRule.onAllNodesWithText(RESTORE).onFirst().performClick()

        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule.onAllNodesWithTextSafe(CHAT_TITLE) == 0
        }
        // Back to the empty state — and nothing was deleted along the way.
        composeTestRule.onNodeWithText(EMPTY_TITLE).assertIsDisplayed()
        check(sessions.value.none { it.isArchived }) { "The chat must be back in the main list" }
        check(sessions.value.size == 2) { "Restoring must not remove anything" }
    }

    /** Node count for [text], usable inside a `waitUntil` predicate. */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafe(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size

    private companion object {
        const val SESSION_ID = "session-archive-me"
        const val CHAT_TITLE = "Weekly review"
        const val EMPTY_TITLE = "Nothing archived"
        const val RESTORE = "Restore"
        const val TIMEOUT_MS = 5_000L
    }
}
