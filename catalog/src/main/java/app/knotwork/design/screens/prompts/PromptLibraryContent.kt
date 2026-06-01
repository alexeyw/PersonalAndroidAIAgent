@file:Suppress("MatchingDeclarationName") // Hosts PromptLibraryContent + helpers.

package app.knotwork.design.screens.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.components.misc.EmptyState
import app.knotwork.design.components.pipelineeditor.headerTint
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Side length of the per-row top accent strip rendered above each prompt card. */
private val CardAccentStrip = 2.dp

/** Side length of the leading icon tile on the editor-sheet header. */
private val EditorHeaderTile = 32.dp

/** Inner glyph size on the editor-sheet header. */
private val EditorHeaderGlyph = 18.dp

/** Side length of the FAB icon. */
private val FabIconSize = 24.dp

/**
 * Stateless Knotwork Prompt Library surface.
 *
 * @param state immutable view state — drives loader / empty / default / error layouts.
 * @param modifier optional layout modifier applied to the root scaffold.
 * @param strings localised display strings.
 * @param callbacks one-shot callback bundle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryContent(
    state: PromptLibraryViewState,
    modifier: Modifier = Modifier,
    strings: PromptLibraryStrings = PromptLibraryStrings(),
    callbacks: PromptLibraryCallbacks = noopPromptLibraryCallbacks(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
            androidx.compose.foundation.layout.Column {
                PromptsTopBar(state = state, strings = strings, callbacks = callbacks)
                androidx.compose.material3.HorizontalDivider(color = KnotworkTheme.extended.divider)
            }
        },
        floatingActionButton = {
            if (state.visualState != PromptLibraryVisualState.Loading) {
                FloatingActionButton(
                    onClick = callbacks.onNewPrompt,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = KnotworkTheme.shapes.md,
                ) {
                    Icon(
                        imageVector = AppIcons.Add,
                        contentDescription = strings.fabCd,
                        modifier = Modifier.size(FabIconSize),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.visualState != PromptLibraryVisualState.Loading &&
                state.visualState != PromptLibraryVisualState.Error
            ) {
                PromptsCategoryTabs(state = state, callbacks = callbacks)
            }
            when (state.visualState) {
                PromptLibraryVisualState.Loading -> PromptsLoading()
                PromptLibraryVisualState.Empty -> PromptsEmpty(strings = strings)
                PromptLibraryVisualState.Error -> PromptsError(state = state, strings = strings, callbacks = callbacks)
                PromptLibraryVisualState.Default -> if (state.prompts.isEmpty()) {
                    PromptsEmpty(strings = strings)
                } else {
                    PromptsList(state = state, strings = strings, callbacks = callbacks)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptsTopBar(
    state: PromptLibraryViewState,
    strings: PromptLibraryStrings,
    callbacks: PromptLibraryCallbacks,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = strings.title,
                    style = KnotworkTextStyles.TitleMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.subtitle.isNotEmpty()) {
                    Text(
                        text = state.subtitle,
                        style = KnotworkTextStyles.MonoSm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onBack) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = strings.backCd,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        // TopAppBar slot intentionally empty — search was removed in Phase 24 /
        // Task 5 review pass. Reserved for future actions (Import / Export).
        actions = {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptsCategoryTabs(state: PromptLibraryViewState, callbacks: PromptLibraryCallbacks) {
    val selectedIndex = state.categories.indexOf(state.selectedCategory).coerceAtLeast(0)
    if (state.categories.isEmpty()) return
    // Indicator tint follows the currently-selected node type so the
    // underline reads as "this is the IF_CONDITION view" instead of a
    // generic accent — matches the per-node colour applied to the
    // card's top accent strip and category pill.
    val selectedTint = if (selectedIndex in state.categories.indices) {
        categoryTint(category = state.categories[selectedIndex])
    } else {
        MaterialTheme.colorScheme.primary
    }
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = selectedTint,
        edgePadding = KnotworkTheme.spacing.sp4,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier
                        .tabIndicatorOffsetSafe(tabPositions[selectedIndex]),
                    color = selectedTint,
                )
            }
        },
    ) {
        state.categories.forEachIndexed { index, category ->
            Tab(
                selected = index == selectedIndex,
                onClick = { callbacks.onCategorySelected(category) },
                text = {
                    Text(
                        text = category,
                        // Mono small so category labels (`IF_CONDITION`,
                        // `INTENT_ROUTER`, …) keep an even character
                        // grid identical to the in-card pill — matches
                        // the design mockup.
                        style = KnotworkTextStyles.MonoSm,
                        color = if (index == selectedIndex) {
                            selectedTint
                        } else {
                            KnotworkTheme.extended.onSurfaceMuted
                        },
                    )
                },
            )
        }
    }
}

/**
 * Wrapper around the deprecated `Modifier.tabIndicatorOffset` to keep call
 * sites tidy. The signature has flipped between Compose versions; this
 * shim insulates us from that drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.tabIndicatorOffsetSafe(position: androidx.compose.material3.TabPosition): Modifier =
    androidx.compose.material3.TabRowDefaults.run {
        this@tabIndicatorOffsetSafe.tabIndicatorOffset(position)
    }

@Composable
private fun PromptsLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PromptsEmpty(strings: PromptLibraryStrings) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = strings.emptyTitle,
            subtitle = strings.emptySubtitle,
        )
    }
}

@Composable
private fun PromptsError(
    state: PromptLibraryViewState,
    strings: PromptLibraryStrings,
    callbacks: PromptLibraryCallbacks,
) {
    Box(modifier = Modifier.fillMaxSize().padding(KnotworkTheme.spacing.sp6), contentAlignment = Alignment.Center) {
        EmptyState(
            title = strings.errorTitle,
            subtitle = state.errorMessage.orEmpty(),
            ctaLabel = strings.errorRetry,
            onCtaClick = callbacks.onRetry,
        )
    }
}

@Composable
private fun PromptsList(
    state: PromptLibraryViewState,
    strings: PromptLibraryStrings,
    callbacks: PromptLibraryCallbacks,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = KnotworkTheme.spacing.sp4,
            end = KnotworkTheme.spacing.sp4,
            top = KnotworkTheme.spacing.sp3,
            bottom = KnotworkTheme.spacing.sp16,
        ),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        items(items = state.prompts, key = { it.id }) { prompt ->
            PromptCard(
                prompt = prompt,
                strings = strings,
                variables = state.availableVariables,
                onEdit = { callbacks.onEditPrompt(prompt.id) },
                onDelete = { callbacks.onDeletePrompt(prompt.id) },
                onDuplicate = { callbacks.onDuplicatePrompt(prompt.id) },
                onPreview = { callbacks.onPreviewPrompt(prompt.id) },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PromptCard(
    prompt: PromptRow,
    strings: PromptLibraryStrings,
    variables: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onPreview: () -> Unit,
) {
    // Card accent + chip share the same node-type hue from the
    // catalog palette so a card visually echoes the matching node on
    // the editor canvas (CLARIFICATION → green, CLOUD → blue, …).
    val categoryTint = categoryTint(category = prompt.category)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CardAccentStrip)
                .background(color = categoryTint),
        )
        Column(
            modifier = Modifier.padding(
                horizontal = KnotworkTheme.spacing.sp3,
                vertical = KnotworkTheme.spacing.sp3,
            ),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(KnotworkTheme.shapes.full)
                        .background(color = categoryTint.copy(alpha = 0.20f))
                        .padding(horizontal = KnotworkTheme.spacing.sp2, vertical = KnotworkTheme.spacing.sp1),
                ) {
                    Text(
                        text = prompt.category,
                        // Mono small to match the tab labels — both
                        // surfaces read as the same `IF_CONDITION`
                        // typographic gesture (per `prompts` mockup).
                        style = KnotworkTextStyles.MonoSm,
                        color = categoryTint,
                    )
                }
                Text(
                    text = prompt.name,
                    style = KnotworkTextStyles.BodyBase,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = KnotworkTheme.spacing.sp2),
                    // Name wraps onto multiple lines so long titles aren't
                    // truncated. The card grows vertically.
                )
                // Preview is available on every row (read-only OR mutable);
                // Edit + Delete only render when the row is mutable.
                CompactIconButton(
                    icon = AppIcons.Search,
                    contentDescription = strings.previewCd,
                    onClick = onPreview,
                )
                if (!prompt.isReadOnly) {
                    CompactIconButton(icon = AppIcons.Edit, contentDescription = strings.editCd, onClick = onEdit)
                    CompactIconButton(
                        icon = AppIcons.Trash,
                        contentDescription = strings.deleteCd,
                        onClick = onDelete,
                    )
                }
            }
            Text(
                text = highlightVariables(text = prompt.body, variables = variables),
                style = KnotworkTextStyles.BodySm,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.usedByFormat.format(prompt.usedByCount),
                    style = KnotworkTextStyles.Caption,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                KnotworkTextButton(text = strings.duplicate, onClick = onDuplicate)
            }
        }
    }
}

/**
 * Resolve the node-type hue tint for a category label by parsing it back
 * into a `NodeType` and reading the `headerTint()` palette colour.
 *
 * Returns the canonical Knotwork primary as a fallback when the category
 * is unrecognised (e.g. legacy / user-typed strings).
 */
@Composable
private fun categoryTint(category: String): androidx.compose.ui.graphics.Color {
    val nodeType = runCatching {
        app.knotwork.design.components.pipelineeditor.NodeType.valueOf(category.trim().uppercase())
    }.getOrNull()
    return nodeType?.headerTint() ?: MaterialTheme.colorScheme.primary
}

/**
 * Compact 32-dp `IconButton` used for the per-row edit / delete actions
 * on the Prompt cards. The Material 3 default of 48 dp would push the
 * title off the right edge on common phone widths.
 */
@Composable
private fun CompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size = 32.dp)
            .clip(KnotworkTheme.shapes.sm)
            .clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = KnotworkTheme.extended.onSurface2,
            modifier = Modifier.size(size = 18.dp),
        )
    }
}

/**
 * Highlight every `$VAR` token in [text] with a tonal background pill so
 * placeholders pop visually inside the body. Uses [SpanStyle] backgrounds
 * rather than `InlineTextContent` chips — close enough to the mockup, and
 * preserves the wrapping behaviour of the surrounding body text.
 */
@Composable
private fun highlightVariables(text: String, variables: List<String>): AnnotatedString {
    val highlightBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val highlightForeground = MaterialTheme.colorScheme.primary
    val knownSet = variables.toSet()
    return buildAnnotatedString {
        val regex = Regex(pattern = """\$[A-Z_][A-Z0-9_]*""")
        var cursor = 0
        for (match in regex.findAll(text)) {
            if (cursor < match.range.first) {
                append(text.substring(cursor, match.range.first))
            }
            val token = match.value
            val recognised = token in knownSet || variables.isEmpty()
            val style = if (recognised) {
                SpanStyle(background = highlightBackground, color = highlightForeground)
            } else {
                SpanStyle(color = KnotworkTheme.extended.onSurfaceMuted)
            }
            withStyle(style = style) { append(" $token ") }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

/**
 * Stateless body of the editor `ModalBottomSheet`. The host owns the
 * sheet container itself so it can manage focus, IME, and dismiss
 * behaviour; this composable just draws the form.
 */
@Composable
fun PromptEditorSheetBody(
    state: PromptEditorState,
    availableVariables: List<String>,
    strings: PromptEditorStrings,
    callbacks: PromptLibraryCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        // `verticalScroll` keeps the prompt body and trailing action row
        // reachable inside the bottom sheet even when the multi-line
        // prompt text and the variable chip row push the form past the
        // sheet viewport (especially with `imePadding` adding the
        // keyboard inset).
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(state = rememberScrollState())
            .imePadding()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp3),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(EditorHeaderTile)
                    .background(
                        color = KnotworkTheme.extended.surface3,
                        shape = KnotworkTheme.shapes.sm,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(EditorHeaderGlyph),
                )
            }
            Text(
                text = if (state.id == null) strings.titleNew else strings.titleEdit,
                style = KnotworkTextStyles.TitleMd,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = KnotworkTheme.spacing.sp3),
            )
            IconButton(onClick = callbacks.onEditorCancel) {
                Icon(
                    imageVector = AppIcons.X,
                    contentDescription = strings.closeCd,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        FieldLabel(label = strings.nameLabel)
        EditorTextField(
            value = state.name,
            placeholder = strings.namePlaceholder,
            onValueChange = callbacks.onEditorNameChange,
        )
        FieldLabel(label = strings.categoryLabel)
        CategoryDropdown(
            value = state.category,
            placeholder = strings.categoryPlaceholder,
            onValueChange = callbacks.onEditorCategoryChange,
        )
        FieldLabel(label = strings.bodyLabel)
        EditorTextField(
            value = state.body,
            placeholder = strings.bodyPlaceholder,
            onValueChange = callbacks.onEditorBodyChange,
            multiline = true,
        )
        FieldLabel(label = strings.insertLabel)
        // Horizontal LazyRow per design feedback — variable list scrolls
        // sideways so the chip row stays single-line on every phone width
        // even with the full `$DATE / $DEVICE / $LANG / $USER / $TZ /
        // $MODEL / $MEMORY_SUMMARY / …` catalog.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            contentPadding = PaddingValues(horizontal = KnotworkTheme.spacing.sp1),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = availableVariables, key = { it }) { token ->
                KnotworkChip(label = token, onClick = { callbacks.onEditorVariableInsert(token) })
            }
        }
        if (state.usedByCount > 0) {
            FooterHint(text = strings.footerFormat.format(state.usedByCount))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            KnotworkTextButton(
                text = strings.cancel,
                onClick = callbacks.onEditorCancel,
                modifier = Modifier.weight(1f),
            )
            app.knotwork.design.components.buttons.KnotworkPrimaryButton(
                text = strings.save,
                onClick = callbacks.onEditorSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(
        text = label,
        style = KnotworkTextStyles.LabelSm,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

/**
 * Read-only field whose tap opens a `DropdownMenu` listing the 12
 * catalog `NodeType` values. Used for the prompt category field so
 * users can only pick a valid node type instead of typing free-form
 * text that wouldn't match any node in the editor.
 */
@Composable
private fun CategoryDropdown(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value = false) }
    val nodeTypes = app.knotwork.design.components.pipelineeditor.NodeType.entries
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(KnotworkTheme.shapes.md)
                .background(color = KnotworkTheme.extended.surface2)
                .clickable(
                    onClick = { expanded.value = true },
                    role = androidx.compose.ui.semantics.Role.DropdownList,
                )
                .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp3),
        ) {
            Text(
                text = value.ifEmpty { placeholder },
                style = KnotworkTextStyles.BodyBase,
                color = if (value.isEmpty()) {
                    KnotworkTheme.extended.onSurfaceMuted
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = AppIcons.ArrowDown,
                contentDescription = null,
                tint = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            containerColor = KnotworkTheme.extended.surface1,
        ) {
            nodeTypes.forEach { nodeType ->
                val tint = nodeType.headerTint()
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            text = nodeType.name,
                            // Mono-small so the dropdown items echo the
                            // category pill + tab typography on the
                            // prompt cards.
                            style = KnotworkTextStyles.MonoSm,
                            color = tint,
                        )
                    },
                    onClick = {
                        expanded.value = false
                        onValueChange(nodeType.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
) {
    // Multi-line bodies render in mono (matches the Settings system-instructions
    // card — Phase 22 / Task 16 follow-up F7) so prompt sources read with the
    // same monospaced rhythm everywhere they're edited. The single-line Name
    // field keeps the proportional [BodyBase] face — it's a display label, not
    // a code-like payload.
    val textStyle = if (multiline) KnotworkTextStyles.MonoSm else KnotworkTextStyles.BodyBase
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface2)
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp3),
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = textStyle,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun FooterHint(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.sm)
            .background(color = KnotworkTheme.extended.surface2)
            .padding(KnotworkTheme.spacing.sp3),
    ) {
        Icon(
            imageVector = AppIcons.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = KnotworkTextStyles.BodySm,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Localised string bundle threaded into [PromptLibraryContent]. */
@Suppress("LongParameterList")
data class PromptLibraryStrings(
    val title: String = "Prompt library",
    val backCd: String = "Back",
    val fabCd: String = "Add prompt",
    val editCd: String = "Edit prompt",
    val deleteCd: String = "Delete prompt",
    val previewCd: String = "Preview",
    val duplicate: String = "Duplicate",
    val usedByFormat: String = "used by %1\$d pipelines",
    val emptyTitle: String = "No prompts yet",
    val emptySubtitle: String = "Tap + to add the first one",
    val errorTitle: String = "Couldn't load prompts",
    val errorRetry: String = "Retry",
)

/** Localised string bundle threaded into [PromptEditorSheetBody]. */
@Suppress("LongParameterList")
data class PromptEditorStrings(
    val titleNew: String = "New prompt",
    val titleEdit: String = "Edit prompt",
    val nameLabel: String = "NAME",
    val namePlaceholder: String = "Untitled",
    val categoryLabel: String = "CATEGORY (NODE TYPE)",
    val categoryPlaceholder: String = "DECOMPOSITION",
    val bodyLabel: String = "PROMPT TEXT",
    val bodyPlaceholder: String = "Write the prompt here…",
    val insertLabel: String = "INSERT",
    val footerFormat: String = "Used by %1\$d pipelines · changes apply on next run",
    val cancel: String = "Cancel",
    val save: String = "Save",
    val closeCd: String = "Close",
)
