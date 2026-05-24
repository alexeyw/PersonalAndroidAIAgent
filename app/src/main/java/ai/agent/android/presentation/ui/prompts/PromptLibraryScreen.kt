package ai.agent.android.presentation.ui.prompts

import ai.agent.android.R
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.presentation.ui.common.asString
import ai.agent.android.presentation.ui.components.PromptPreviewBottomSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextArea
import app.knotwork.design.components.controls.KnotworkTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: PromptLibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(0) }
    // Only show categories that have items, or standard core ones
    val categories = remember(uiState.promptTemplates) {
        val usedCategories = uiState.promptTemplates.map { it.category }.distinct()
        val baseCategories = listOf(
            NodeType.INTENT_ROUTER.name,
            NodeType.DECOMPOSITION.name,
            NodeType.SUMMARY.name,
            NodeType.TOOL.name,
            NodeType.IF_CONDITION.name,
        )
        (baseCategories + usedCategories).distinct().sorted()
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<PromptTemplate?>(null) }

    val errorText = uiState.errorMessage?.asString()
    LaunchedEffect(errorText) {
        errorText?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showEditDialog) {
        EditPromptDialog(
            prompt = editingPrompt,
            availableCategories = categories,
            availableVariables = uiState.availableVariables,
            onSave = { prompt ->
                viewModel.savePrompt(prompt)
                showEditDialog = false
            },
            onPreviewRequested = { template -> viewModel.requestPromptPreview(template) },
            onDismiss = { showEditDialog = false },
        )
    }

    val previewState = uiState.previewState
    if (previewState !is PromptPreviewState.Hidden) {
        PromptPreviewBottomSheet(
            segments = (previewState as? PromptPreviewState.Ready)?.segments,
            onDismiss = { viewModel.dismissPromptPreview() },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prompts_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingPrompt = null
                showEditDialog = true
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.prompts_add_prompt_cd),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (categories.isNotEmpty()) {
                val currentCategory = categories.getOrNull(selectedTab) ?: categories.first()

                androidx.compose.material3.ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                ) {
                    categories.forEachIndexed { index, category ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(category) },
                        )
                    }
                }

                val promptsToShow = uiState.promptTemplates.filter { it.category == currentCategory }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (promptsToShow.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.prompts_empty_category),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(promptsToShow, key = { it.id }) { prompt ->
                            PromptCard(
                                prompt = prompt,
                                onEdit = {
                                    editingPrompt = prompt
                                    showEditDialog = true
                                },
                                onDelete = { viewModel.deletePrompt(prompt.id) },
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.prompts_no_prompts))
                }
            }
        }
    }
}

@Composable
private fun PromptCard(prompt: PromptTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = prompt.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.prompts_card_edit_cd))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.prompts_card_delete_cd),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPromptDialog(
    prompt: PromptTemplate?,
    availableCategories: List<String>,
    availableVariables: List<String>,
    onSave: (PromptTemplate) -> Unit,
    onPreviewRequested: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(prompt?.name ?: "") }
    var text by remember { mutableStateOf(prompt?.text ?: "") }
    var category by remember {
        mutableStateOf(
            prompt?.category ?: availableCategories.firstOrNull() ?: NodeType.INTENT_ROUTER.name,
        )
    }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (prompt == null) R.string.prompts_dialog_new_title else R.string.prompts_dialog_edit_title,
                ),
            )
        },
        text = {
            Column {
                KnotworkField(label = stringResource(R.string.prompts_field_name)) {
                    KnotworkTextField(value = name, onValueChange = { name = it })
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Category Dropdown
                Box {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.prompts_field_category)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.prompts_select_category_cd),
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        NodeType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    category = type.name
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // `KnotworkTextArea` ships variable-aware behaviour out of
                    // the box: the `insertChips` strip below the field replaces
                    // the previous `VariableChipsRow` companion and the
                    // `highlightVariables` pass colours `\$[A-Z_]+` tokens
                    // inline, so the preview-icon column stays as the only
                    // adjacent affordance.
                    KnotworkField(
                        label = stringResource(R.string.prompts_field_text),
                        modifier = Modifier.weight(1f),
                    ) {
                        KnotworkTextArea(
                            value = text,
                            onValueChange = { text = it },
                            minLines = 4,
                            maxLines = 8,
                            insertChips = availableVariables.map { it.trimStart('$') },
                        )
                    }
                    IconButton(
                        onClick = { onPreviewRequested(text) },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.prompts_preview_cd),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && text.isNotBlank()) {
                        onSave(
                            PromptTemplate(
                                id = prompt?.id ?: 0,
                                name = name,
                                text = text,
                                category = category,
                            ),
                        )
                    }
                },
                enabled = name.isNotBlank() && text.isNotBlank(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
