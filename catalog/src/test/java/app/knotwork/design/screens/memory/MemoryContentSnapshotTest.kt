package app.knotwork.design.screens.memory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.a11y.FixedKnotworkA11y
import app.knotwork.design.a11y.LocalKnotworkA11y
import app.knotwork.design.theme.KnotworkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi snapshot baseline for `MemoryContent`. Covers the full 7-state
 * matrix from `compose/screens/README.md §C6 · Memory`
 * (Empty / Populated / Searching / LoadingMore / EntryExpanded / Editing /
 * Error) in both themes, plus the Pinned populated variant introduced by
 * Phase 22 / Task 6.
 *
 * The dedicated font-scale 2× baseline lives in [app.knotwork.design.a11y.A11yMatrixSnapshotTest]
 * to avoid duplicating PNGs across suites.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class MemoryContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun memory_empty_light() = snapshot(name = "empty", dark = false) {
        MemoryContent(state = MemoryPreview.empty())
    }

    @Test
    fun memory_empty_dark() = snapshot(name = "empty", dark = true) {
        MemoryContent(state = MemoryPreview.empty())
    }

    @Test
    fun memory_populated_light() = snapshot(name = "populated", dark = false) {
        MemoryContent(state = MemoryPreview.populated())
    }

    @Test
    fun memory_populated_dark() = snapshot(name = "populated", dark = true) {
        MemoryContent(state = MemoryPreview.populated())
    }

    @Test
    fun memory_populated_pinned_light() = snapshot(name = "populated_pinned", dark = false) {
        MemoryContent(state = MemoryPreview.populatedPinned())
    }

    @Test
    fun memory_populated_pinned_dark() = snapshot(name = "populated_pinned", dark = true) {
        MemoryContent(state = MemoryPreview.populatedPinned())
    }

    @Test
    fun memory_searching_light() = snapshot(name = "searching", dark = false) {
        MemoryContent(state = MemoryPreview.searching())
    }

    @Test
    fun memory_searching_dark() = snapshot(name = "searching", dark = true) {
        MemoryContent(state = MemoryPreview.searching())
    }

    @Test
    fun memory_no_matches_light() = snapshot(name = "no_matches", dark = false) {
        MemoryContent(state = MemoryPreview.noMatches())
    }

    @Test
    fun memory_no_matches_dark() = snapshot(name = "no_matches", dark = true) {
        MemoryContent(state = MemoryPreview.noMatches())
    }

    @Test
    fun memory_loading_more_light() = snapshot(name = "loading_more", dark = false) {
        MemoryContent(state = MemoryPreview.loadingMore())
    }

    @Test
    fun memory_loading_more_dark() = snapshot(name = "loading_more", dark = true) {
        MemoryContent(state = MemoryPreview.loadingMore())
    }

    @Test
    fun memory_entry_expanded_light() = snapshot(name = "entry_expanded", dark = false) {
        MemoryContent(state = MemoryPreview.entryExpanded())
    }

    @Test
    fun memory_entry_expanded_dark() = snapshot(name = "entry_expanded", dark = true) {
        MemoryContent(state = MemoryPreview.entryExpanded())
    }

    @Test
    fun memory_editing_light() = snapshot(name = "editing", dark = false) {
        MemoryContent(state = MemoryPreview.editing())
    }

    @Test
    fun memory_editing_dark() = snapshot(name = "editing", dark = true) {
        MemoryContent(state = MemoryPreview.editing())
    }

    @Test
    fun memory_error_light() = snapshot(name = "error", dark = false) {
        MemoryContent(state = MemoryPreview.error())
    }

    @Test
    fun memory_error_dark() = snapshot(name = "error", dark = true) {
        MemoryContent(state = MemoryPreview.error())
    }

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/memory_${name}_$themeTag.png",
        )
    }
}

/** Internal preview fixtures backing the memory snapshot suite. */
internal object MemoryPreview {

    private fun rows(): List<MemoryRow> = listOf(
        MemoryRow(
            id = "m1",
            title = "Project deadlines",
            body = "Phase 21 ships by 2026-05-20; v0.1 tag follows once Task 11 closes.",
            tags = listOf("project", "deadlines"),
            relevanceScore = "0.94",
            lastAccessed = "2 hours ago",
        ),
        MemoryRow(
            id = "m2",
            title = "Preferred IDE",
            body = "Android Studio Iguana with the Knotwork plugin enabled.",
            tags = listOf("tooling"),
            relevanceScore = "0.81",
            lastAccessed = "yesterday",
        ),
        MemoryRow(
            id = "m3",
            title = "Coffee order",
            body = "Flat white, no sugar.",
            tags = listOf("personal"),
            relevanceScore = "0.66",
            lastAccessed = "3 days ago",
        ),
    )

    private fun detailFor(id: String): MemoryEntryDetail {
        val row = rows().first { it.id == id }
        return MemoryEntryDetail(
            id = row.id,
            title = row.title,
            body = row.body,
            tags = row.tags,
            lastAccessed = row.lastAccessed,
            isPinned = row.isPinned,
        )
    }

    fun empty(): MemoryViewState = MemoryViewState(visualState = MemoryVisualState.Empty)

    fun populated(): MemoryViewState = MemoryViewState(
        visualState = MemoryVisualState.Populated,
        entries = rows(),
    )

    /**
     * Populated variant with the second entry pinned and floated to the top —
     * mirrors the post-sort order produced by `MemoryScreen.kt` after
     * Phase 22 / Task 6.
     */
    fun populatedPinned(): MemoryViewState {
        val all = rows()
        val pinned = all[1].copy(isPinned = true)
        return MemoryViewState(
            visualState = MemoryVisualState.Populated,
            entries = listOf(pinned) + all.filter { it.id != pinned.id },
        )
    }

    fun searching(): MemoryViewState = MemoryViewState(
        visualState = MemoryVisualState.Searching,
        entries = rows().take(n = 2),
        searchQuery = "phase",
        sortMode = MemorySortMode.Relevance,
    )

    fun noMatches(): MemoryViewState = MemoryViewState(
        visualState = MemoryVisualState.Searching,
        entries = emptyList(),
        searchQuery = "zzz",
    )

    /** Populated rows + trailing `StripedPlaceholder` skeleton. */
    fun loadingMore(): MemoryViewState = MemoryViewState(
        visualState = MemoryVisualState.LoadingMore,
        entries = rows(),
    )

    /** Detail overlay over a populated surface (read-only). */
    fun entryExpanded(): MemoryViewState = MemoryViewState(
        visualState = MemoryVisualState.EntryExpanded,
        entries = rows(),
        expandedEntry = detailFor(id = "m1"),
    )

    /** Detail overlay in edit mode — multi-line `OutlinedTextField` + Save/Cancel. */
    fun editing(): MemoryViewState = MemoryViewState(
        visualState = MemoryVisualState.Editing,
        entries = rows(),
        expandedEntry = detailFor(id = "m1"),
    )

    fun error(): MemoryViewState = MemoryViewState(
        visualState = MemoryVisualState.Error,
        errorMessage = "Vector store unreachable: SQLCipher passphrase missing.",
    )
}
