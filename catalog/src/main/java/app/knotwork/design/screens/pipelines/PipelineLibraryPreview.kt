package app.knotwork.design.screens.pipelines

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.Color
import app.knotwork.design.components.chips.Status

/**
 * Deterministic fixtures backing the `PipelineLibraryContent` preview and
 * Roborazzi snapshot matrix. Internal so `:app` code cannot reach them.
 */
internal object PipelineLibraryPreview {

    fun rows(): List<PipelineLibraryRow> = listOf(
        PipelineLibraryRow(
            id = "p1",
            title = "Daily standup notes",
            subtitle = "Last run 8 min ago · 4 nodes",
            status = Status.Success,
            leadingTint = Color(color = 0xFF6FBF73),
            leadingIcon = Icons.Outlined.Bolt,
        ),
        PipelineLibraryRow(
            id = "p2",
            title = "Web research bundle",
            subtitle = "Last run yesterday · 7 nodes",
            status = Status.Warning,
            leadingTint = Color(color = 0xFFEAA84A),
            leadingIcon = Icons.Outlined.Search,
        ),
        PipelineLibraryRow(
            id = "p3",
            title = "Code-review helper",
            subtitle = "Last run 3 days ago · 6 nodes",
            status = Status.Idle,
            leadingTint = Color(color = 0xFF7A8CFF),
            leadingIcon = Icons.Outlined.Build,
        ),
        PipelineLibraryRow(
            id = "p4",
            title = "Triage open PRs",
            subtitle = "Never run · 3 nodes",
            status = Status.Idle,
            leadingTint = Color(color = 0xFFC97AFF),
            leadingIcon = Icons.AutoMirrored.Outlined.AltRoute,
        ),
    )

    fun empty(): PipelineLibraryViewState = PipelineLibraryViewState(
        visualState = PipelineLibraryVisualState.Empty,
    )

    fun loading(): PipelineLibraryViewState = PipelineLibraryViewState(
        visualState = PipelineLibraryVisualState.Loading,
        totalCount = 0,
    )

    fun populated(): PipelineLibraryViewState = PipelineLibraryViewState(
        visualState = PipelineLibraryVisualState.Populated,
        pipelines = rows(),
        totalCount = rows().size,
    )

    fun filtering(): PipelineLibraryViewState {
        val filtered = rows().take(n = 2)
        return PipelineLibraryViewState(
            visualState = PipelineLibraryVisualState.Filtering,
            pipelines = filtered,
            totalCount = rows().size,
            searchQuery = "rev",
        )
    }

    fun filteringNoMatches(): PipelineLibraryViewState = PipelineLibraryViewState(
        visualState = PipelineLibraryVisualState.Filtering,
        pipelines = emptyList(),
        totalCount = rows().size,
        searchQuery = "zzz",
    )

    fun swipeOpen(): PipelineLibraryViewState {
        val markedRevealed = rows().mapIndexed { index, row ->
            if (index == 1) row.copy(revealed = true) else row
        }
        return PipelineLibraryViewState(
            visualState = PipelineLibraryVisualState.SwipeOpen,
            pipelines = markedRevealed,
        )
    }

    fun multiSelect(): PipelineLibraryViewState {
        val markedSelected = rows().mapIndexed { index, row ->
            if (index == 0 || index == 2) row.copy(selected = true) else row
        }
        return PipelineLibraryViewState(
            visualState = PipelineLibraryVisualState.MultiSelect,
            pipelines = markedSelected,
            selectedCount = 2,
        )
    }

    fun error(): PipelineLibraryViewState = PipelineLibraryViewState(
        visualState = PipelineLibraryVisualState.Error,
        errorMessage = "Failed to read pipelines from the local database.",
    )
}
