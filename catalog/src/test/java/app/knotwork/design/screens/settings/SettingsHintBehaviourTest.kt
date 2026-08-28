package app.knotwork.design.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of the settings hint affordance: the parts a Roborazzi baseline
 * cannot show.
 *
 * A snapshot proves the glyph is drawn and the panel fits. It cannot prove that
 * the glyph is reachable by a screen reader, that it meets the touch minimum
 * despite being drawn at 18 dp, that reaching for it does not flip the switch it
 * explains, or that opening one explanation closes another.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsHintBehaviourTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the help glyph meets the touch minimum even though it is drawn smaller`() {
        setContent { MemorySettingsContent(state = SettingsPreview.memory()) }

        val bounds = composeTestRule
            .onNodeWithContentDescription("Explain: Auto-extract from conversations")
            .fetchSemanticsNode()
            .touchBoundsInRoot
        val density = composeTestRule.density

        // Asserted on the TOUCH bounds, not the node size: the glyph is laid out
        // at 28 dp on purpose (a 48 dp layout box pushed it onto a second line),
        // and Compose expands the touchable area around it.
        with(density) {
            assertTrue("touch width was ${bounds.width.toDp()}", bounds.width.toDp().value >= MIN_TARGET_DP)
            assertTrue("touch height was ${bounds.height.toDp()}", bounds.height.toDp().value >= MIN_TARGET_DP)
        }
    }

    @Test
    fun `tapping the glyph opens the explanation without flipping the switch it explains`() {
        var toggles = 0
        setContent {
            MemorySettingsContent(
                state = SettingsPreview.memory(),
                callbacks = SettingsCallbacks(onAutoExtractToggle = { toggles++ }),
            )
        }

        composeTestRule.onNodeWithContentDescription("Explain: Auto-extract from conversations").performClick()

        composeTestRule.onNodeWithText(AUTO_EXTRACT_HINT).assertIsDisplayed()
        // The row's own click area excludes the glyph, so reaching for the
        // explanation cannot change the setting being explained. Asserted on the
        // callback rather than on a node's state: the row carries plain
        // `OnClick` semantics, so `assertIsOn` would resolve to the label's
        // merged text node and quietly pass whatever happened.
        assertEquals("the glyph must not toggle the row", 0, toggles)
    }

    @Test
    fun `the row still toggles when tapped anywhere else`() {
        var toggles = 0
        setContent {
            MemorySettingsContent(
                state = SettingsPreview.memory(),
                callbacks = SettingsCallbacks(onAutoExtractToggle = { toggles++ }),
            )
        }

        composeTestRule.onNodeWithText("Auto-extract from conversations").performClick()

        assertEquals("the row must stay clickable after gaining a glyph", 1, toggles)
    }

    @Test
    fun `opening a second explanation closes the first`() {
        setContent { MemorySettingsContent(state = SettingsPreview.memory()) }

        composeTestRule.onNodeWithContentDescription("Explain: Auto-extract from conversations").performClick()
        composeTestRule.onNodeWithText(AUTO_EXTRACT_HINT).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Explain: Background compaction").performClick()

        composeTestRule.onNodeWithText(COMPACTION_HINT).assertIsDisplayed()
        composeTestRule.onNodeWithText(AUTO_EXTRACT_HINT).assertDoesNotExist()
    }

    @Test
    fun `tapping the open glyph closes its explanation`() {
        setContent { MemorySettingsContent(state = SettingsPreview.memory()) }
        val glyph = composeTestRule.onNodeWithContentDescription("Explain: Auto-extract from conversations")

        glyph.performClick()
        composeTestRule.onNodeWithText(AUTO_EXTRACT_HINT).assertIsDisplayed()
        glyph.performClick()

        composeTestRule.onNodeWithText(AUTO_EXTRACT_HINT).assertDoesNotExist()
    }

    /**
     * Regression: the deep-linked row kept its help glyph.
     *
     * `SettingsAnchor` renders a highlighted row through a different branch than
     * an ordinary one, and the anchor `CompositionLocalProvider` originally
     * wrapped only the ordinary branch — so arriving at a setting from search,
     * the one moment the user has demonstrably asked what it is, was the one
     * moment with no explanation to open.
     */
    @Test
    fun `the row a search deep-link highlights still offers its explanation`() {
        composeTestRule.setContent {
            val hints = remember { SettingsHintController { anchor -> HINTS[anchor] } }
            KnotworkTheme {
                CompositionLocalProvider(
                    LocalSettingsHints provides hints,
                    LocalSettingsHighlightKey provides SettingsRowAnchors.AUTO_EXTRACT_ENABLED,
                ) {
                    MemorySettingsContent(state = SettingsPreview.memory())
                }
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Explain: Auto-extract from conversations")
            .performClick()

        composeTestRule.onNodeWithText(AUTO_EXTRACT_HINT).assertIsDisplayed()
    }

    /**
     * The assertion the app-side completeness gate cannot make: that a control
     * which is not one of the standard rows still *renders* an affordance.
     *
     * Six settings are drawn by bespoke Composables — the two textareas, the two
     * dropdown rows, the approval segmented control and the memory action strip
     * — and every one of them shipped its explanation with no way to open it
     * until this was asserted. A hint nobody can reach is the same defect as a
     * hint that is wrong, one step removed.
     */
    @Test
    fun `bespoke settings controls render a help glyph too`() {
        composeTestRule.setContent {
            val hints = remember { SettingsHintController { SettingsHint(BESPOKE_HINT) } }
            KnotworkTheme {
                CompositionLocalProvider(LocalSettingsHints provides hints) {
                    GenerationSettingsContent(state = SettingsPreview.generation(), advancedExpanded = true)
                }
            }
        }

        // Asserting the glyph is displayed is NOT enough, and this test used to
        // stop there. The System-instructions header shipped a glyph with no
        // panel beneath it: it rendered, TalkBack announced it, it took the
        // one-open-at-a-time slot from whatever was open — and showed nothing.
        // So every bespoke control is opened and its panel demanded.
        listOf("System instructions", "Tool-usage instruction").forEach { name ->
            composeTestRule.onNodeWithContentDescription("Explain: $name").assertIsDisplayed()
        }
        composeTestRule.onNodeWithContentDescription("Explain: System instructions").performClick()
        composeTestRule.onNodeWithText(BESPOKE_HINT).assertIsDisplayed()
    }

    @Test
    fun `the memory action strip and the embedding dropdown carry a glyph`() {
        composeTestRule.setContent {
            val hints = remember { SettingsHintController { SettingsHint(BESPOKE_HINT) } }
            // Opened through the controller rather than by tapping: both rows sit
            // below the fold on a 760 dp frame, and a tap there does not land.
            // What is being asserted is that the control renders BOTH halves of
            // the affordance — the glyph and the panel it opens — which is what
            // the System-instructions header failed to do while a glyph-only
            // assertion passed.
            LaunchedEffect(Unit) { hints.toggle(SettingsRowAnchors.ACTIVE_EMBEDDING_PROVIDER_ID) }
            KnotworkTheme {
                CompositionLocalProvider(LocalSettingsHints provides hints) {
                    MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Explain: Memory data").assertExists()
        composeTestRule.onNodeWithContentDescription("Explain: Embedding model").assertExists()
        composeTestRule.onNodeWithText(BESPOKE_HINT).assertExists()
    }

    @Test
    fun `the memory action strip opens its explanation too`() {
        composeTestRule.setContent {
            val hints = remember { SettingsHintController { SettingsHint(BESPOKE_HINT) } }
            LaunchedEffect(Unit) { hints.toggle(SettingsRowAnchors.MEMORY_ACTIONS) }
            KnotworkTheme {
                CompositionLocalProvider(LocalSettingsHints provides hints) {
                    MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
                }
            }
        }

        composeTestRule.onNodeWithText(BESPOKE_HINT).assertExists()
    }

    @Test
    fun `a row with no hint has no glyph`() {
        setContent { MemorySettingsContent(state = SettingsPreview.memory()) }

        // "Compress chat history" is in the fixture but carries no hint there,
        // so it must render no affordance at all rather than an inert one.
        composeTestRule.onNodeWithContentDescription("Explain: Compress chat history").assertDoesNotExist()
    }

    @Test
    fun `a control reused outside a settings screen renders no glyph`() {
        composeTestRule.setContent {
            KnotworkTheme {
                CompositionLocalProvider(LocalSettingsHints provides SettingsHintController { HINTS["TEMPERATURE"] }) {
                    // No enclosing SettingsAnchor: the node editor composes this
                    // same slider, and it must not sprout a settings glyph.
                    KnotworkParamSlider(
                        label = "Temperature",
                        valueLabel = "0.7",
                        value = 0.7f,
                        onValueChange = {},
                        valueRange = 0f..2f,
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Explain: Temperature").assertDoesNotExist()
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val hints = remember { SettingsHintController { anchor -> HINTS[anchor] } }
            KnotworkTheme {
                CompositionLocalProvider(
                    LocalSettingsHints provides hints,
                    LocalDensity provides composeTestRule.density,
                ) { content() }
            }
        }
    }

    private companion object {
        const val MIN_TARGET_DP = 48f
        const val BESPOKE_HINT = "What this control does, in one sentence."
        const val AUTO_EXTRACT_HINT = "Durable facts from your chats are saved and reused later."
        const val COMPACTION_HINT = "Old memories get merged into shorter summaries once there are many."

        val HINTS: Map<String, SettingsHint> = mapOf(
            "AUTO_EXTRACT_ENABLED" to SettingsHint(AUTO_EXTRACT_HINT),
            "MEMORY_COMPACTION_ENABLED" to SettingsHint(COMPACTION_HINT),
            "TEMPERATURE" to SettingsHint("Low keeps the wording predictable."),
        )
    }
}
