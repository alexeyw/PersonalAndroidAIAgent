package ai.agent.android.presentation.ui.orchestrator.components

import ai.agent.android.R
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Provider ids offered in the CLOUD-node dropdown, in display order. Begins with the
 * "auto" sentinel (let the executor pick a provider at runtime) followed by the cloud
 * [CloudProvider]s. [CloudProvider.OLLAMA] is intentionally excluded because Ollama is
 * surfaced through a separate routing path (`RoutingDecision.LocalOllama`), not as a
 * cloud target.
 */
private val cloudProviderDropdownOptions: List<String> = listOf(
    CloudProvider.AUTO_KEY,
    CloudProvider.GOOGLE.id,
    CloudProvider.ANTHROPIC.id,
    CloudProvider.OPENAI.id,
    CloudProvider.DEEPSEEK.id,
)

/**
 * A draggable and interactive visual node component.
 *
 * @param node The [NodeModel] detailing type, id, and coordinates.
 * @param modifier The [Modifier] for this composable.
 * @param isConnecting Whether this node is currently selected to connect to another.
 * @param connectingIsOutput Whether the currently selected port is an output port.
 * @param connectingLabel The label for the connection.
 * @param onPositionDelta Callback invoked when the node is dragged, providing the delta x and y.
 * @param onConnectClick Callback invoked when the connect button/port is clicked. Passes a boolean indicating if it's an output port and an optional label.
 * @param onDeleteClick Callback invoked when the delete button is clicked.
 * @param onConfigureClick Callback invoked when the configure button is clicked (used for IF_CONDITION).
 * @param availableTools List of tools available for tool nodes.
 * @param onToolSelected Callback invoked when a tool is selected for a tool node.
 * @param onCloudProviderSelected Callback invoked when a cloud provider is selected for a CLOUD node.
 */
@Composable
fun DraggableNode(
    node: NodeModel,
    modifier: Modifier = Modifier,
    isConnecting: Boolean = false,
    connectingIsOutput: Boolean = true,
    connectingLabel: String? = null,
    onPositionDelta: (String, Float, Float) -> Unit,
    onConnectClick: (Boolean, String?) -> Unit,
    onDeleteClick: () -> Unit,
    onConfigureClick: () -> Unit = {},
    availableTools: List<AgentTool> = emptyList(),
    onToolSelected: (String, String) -> Unit = { _, _ -> },
    onCloudProviderSelected: (String, String) -> Unit = { _, _ -> },
) {
    val nodeColor = when (node.type) {
        NodeType.LITE_RT -> colorResource(R.color.node_color_lite_rt)
        NodeType.CLOUD -> colorResource(R.color.node_color_cloud)
        NodeType.TOOL -> colorResource(R.color.node_color_tool)
        NodeType.IF_CONDITION -> colorResource(R.color.node_color_if_condition)
        NodeType.INTENT_ROUTER -> colorResource(R.color.node_color_intent_router)
        NodeType.DECOMPOSITION -> colorResource(R.color.node_color_decomposition)
        NodeType.QUEUE_PROCESSOR -> colorResource(R.color.node_color_queue_processor)
        NodeType.EVALUATION -> colorResource(R.color.node_color_evaluation)
        NodeType.SUMMARY -> colorResource(R.color.node_color_summary)
        NodeType.CLARIFICATION -> colorResource(R.color.node_color_clarification)
        NodeType.INPUT -> colorResource(R.color.node_color_input)
        NodeType.OUTPUT -> colorResource(R.color.node_color_output)
    }

    Box(
        modifier = modifier
            .pointerInput(node.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // dragAmount is in the local scaled coordinate space, which perfectly maps to our logical space
                    onPositionDelta(node.id, dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Main Node Box
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp) // Reserve space for ports
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (isConnecting) 4.dp else 2.dp,
                    color = if (isConnecting) MaterialTheme.colorScheme.primary else nodeColor,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = node.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = nodeColor,
                )

                if (node.type == NodeType.TOOL) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { expanded = true }) {
                            Text(node.toolName ?: stringResource(R.string.orchestrator_node_select_tool))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            availableTools.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(tool.name) },
                                    onClick = {
                                        onToolSelected(node.id, tool.name)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (node.type == NodeType.CLOUD) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { expanded = true }) {
                            Text(
                                node.cloudProvider?.replaceFirstChar { it.uppercase() }
                                    ?: stringResource(R.string.orchestrator_node_provider_auto),
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            cloudProviderDropdownOptions.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        onCloudProviderSelected(node.id, provider)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (node.type != NodeType.INPUT) {
                    Button(onClick = onConfigureClick, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.orchestrator_node_configure))
                    }
                }

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.orchestrator_node_delete_cd),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // Input port
        if (node.type != NodeType.INPUT) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnecting &&
                            !connectingIsOutput
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            nodeColor
                        },
                    )
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onConnectClick(false, null) },
            )
        }

        // Output port
        if (node.type != NodeType.OUTPUT) {
            if (node.type == NodeType.IF_CONDITION) {
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnecting &&
                                    connectingIsOutput
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    nodeColor
                                },
                            )
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { expanded = true },
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.orchestrator_node_branch_true)) },
                            onClick = {
                                onConnectClick(true, "True")
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.orchestrator_node_branch_false)) },
                            onClick = {
                                onConnectClick(true, "False")
                                expanded = false
                            },
                        )
                    }
                }
            } else if (node.type == NodeType.QUEUE_PROCESSOR) {
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnecting &&
                                    connectingIsOutput
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    nodeColor
                                },
                            )
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { expanded = true },
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.orchestrator_node_queue_item)) },
                            onClick = {
                                onConnectClick(true, "Item")
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.orchestrator_node_queue_done)) },
                            onClick = {
                                onConnectClick(true, "Done")
                                expanded = false
                            },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnecting &&
                                connectingIsOutput
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                nodeColor
                            },
                        )
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onConnectClick(true, null) },
                )
            }
        }
    }
}
