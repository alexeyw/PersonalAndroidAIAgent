package ai.agent.android.presentation.ui.orchestrator.components

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A draggable and interactive visual node component.
 *
 * @param node The [NodeModel] detailing type, id, and coordinates.
 * @param modifier The [Modifier] for this composable.
 * @param isConnecting Whether this node is currently selected to connect to another.
 * @param onPositionDelta Callback invoked when the node is dragged, providing the delta x and y.
 * @param onConnectClick Callback invoked when the connect button is clicked. Passes an optional label for the connection.
 * @param onDeleteClick Callback invoked when the delete button is clicked.
 * @param onConfigureClick Callback invoked when the configure button is clicked (used for IF_CONDITION).
 * @param availableTools List of tools available for tool nodes.
 * @param onToolSelected Callback invoked when a tool is selected for a tool node.
 */
@Composable
fun DraggableNode(
    node: NodeModel,
    modifier: Modifier = Modifier,
    isConnecting: Boolean = false,
    connectingLabel: String? = null,
    onPositionDelta: (String, Float, Float) -> Unit,
    onConnectClick: (String?) -> Unit,
    onDeleteClick: () -> Unit,
    onConfigureClick: () -> Unit = {},
    availableTools: List<AgentTool> = emptyList(),
    onToolSelected: (String, String) -> Unit = { _, _ -> },
    onCloudProviderSelected: (String, String) -> Unit = { _, _ -> }
) {
    val nodeColor = when (node.type) {
        NodeType.LITE_RT -> Color(0xFF4CAF50)
        NodeType.CLOUD -> Color(0xFF2196F3)
        NodeType.TOOL -> Color(0xFFFF9800)
        NodeType.IF_CONDITION -> Color(0xFFFFC107)
        NodeType.INTENT_ROUTER -> Color(0xFFE91E63)
        NodeType.DECOMPOSITION -> Color(0xFF3F51B5)
        NodeType.QUEUE_PROCESSOR -> Color(0xFF795548)
        NodeType.EVALUATION -> Color(0xFF009688)
        NodeType.SUMMARY -> Color(0xFF8BC34A)
        NodeType.INPUT -> Color(0xFF607D8B)
        NodeType.OUTPUT -> Color(0xFFF44336)
    }

    Box(
        modifier = modifier
            .pointerInput(node.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // dragAmount is in the local scaled coordinate space, which perfectly maps to our logical space
                    onPositionDelta(node.id, dragAmount.x, dragAmount.y)
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isConnecting) 4.dp else 2.dp, 
                color = if (isConnecting) MaterialTheme.colorScheme.primary else nodeColor, 
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = node.type.name,
                style = MaterialTheme.typography.labelSmall,
                color = nodeColor
            )
            
            if (node.type == NodeType.TOOL) {
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { expanded = true }) {
                        Text(node.toolName ?: "Select Tool")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableTools.forEach { tool ->
                            DropdownMenuItem(
                                text = { Text(tool.name) },
                                onClick = {
                                    onToolSelected(node.id, tool.name)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (node.type == NodeType.CLOUD) {
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { expanded = true }) {
                        Text(node.cloudProvider?.replaceFirstChar { it.uppercase() } ?: "Auto")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf("auto", "google", "anthropic", "openai", "deepseek").forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onCloudProviderSelected(node.id, provider)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (node.type != NodeType.INPUT && node.type != NodeType.OUTPUT) {
                Button(onClick = onConfigureClick, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Configure")
                }
            }

            if (node.type == NodeType.IF_CONDITION) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { onConnectClick("True") }) {
                        Text(
                            text = "True",
                            color = if (isConnecting && connectingLabel == "True") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { onConnectClick("False") }) {
                        Text(
                            text = "False",
                            color = if (isConnecting && connectingLabel == "False") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (node.type != NodeType.IF_CONDITION) {
                    IconButton(onClick = { onConnectClick(null) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Connect",
                            tint = if (isConnecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
