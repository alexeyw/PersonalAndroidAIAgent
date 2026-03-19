package ai.agent.android.presentation.ui.orchestrator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import kotlin.math.roundToInt

/**
 * A draggable and interactive visual node component.
 *
 * @param node The [NodeModel] detailing type, id, and coordinates.
 * @param modifier The [Modifier] for this composable.
 * @param onPositionChanged Callback invoked when the node is dragged.
 */
@Composable
fun DraggableNode(
    node: NodeModel,
    modifier: Modifier = Modifier,
    onPositionChanged: (String, Float, Float) -> Unit
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
                    onPositionChanged(node.id, node.x + dragAmount.x, node.y + dragAmount.y)
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, nodeColor, RoundedCornerShape(8.dp))
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
        }
    }
}
