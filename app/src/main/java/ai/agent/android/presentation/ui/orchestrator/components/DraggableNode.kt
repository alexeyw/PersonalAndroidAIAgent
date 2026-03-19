package ai.agent.android.presentation.ui.orchestrator.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType

/**
 * A draggable and interactive visual node component.
 *
 * @param node The [NodeModel] detailing type, id, and coordinates.
 * @param isConnecting Whether this node is currently selected to connect to another.
 * @param modifier The [Modifier] for this composable.
 * @param onPositionDelta Callback invoked when the node is dragged, providing the delta x and y.
 * @param onConnectClick Callback invoked when the connect button is clicked.
 * @param onDeleteClick Callback invoked when the delete button is clicked.
 */
@Composable
fun DraggableNode(
    node: NodeModel,
    isConnecting: Boolean = false,
    modifier: Modifier = Modifier,
    onPositionDelta: (String, Float, Float) -> Unit,
    onConnectClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val nodeColor = when (node.type) {
        NodeType.LITE_RT -> Color(0xFF4CAF50)
        NodeType.DEEPSEEK -> Color(0xFF2196F3)
        NodeType.OPENAI -> Color(0xFF9C27B0)
        NodeType.ANTHROPIC -> Color(0xFF673AB7)
        NodeType.TOOL -> Color(0xFFFF9800)
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
            Row(modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onConnectClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Connect",
                        tint = if (isConnecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
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
