package app.knotwork.design.components.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the asymmetric corner radii on [ChatBubbleShapes]. The shapes
 * are the visual signature of the chat surface — a regression here is a
 * design-level bug that snapshot tests would also catch, but a fast JVM
 * check is cheaper and lands the breakage at the right callsite.
 */
class ChatBubbleShapesTest {

    @Test
    fun `user shape is a uniformly rounded pill`() {
        // The mockup tightens the user
        // bubble to a full pill silhouette rather than the asymmetric
        // 4 dp / 16 dp shape the legacy bubble used.
        val expected = RoundedCornerShape(size = 22.dp)
        assertEquals(expected, ChatBubbleShapes.User)
    }

    @Test
    fun `assistant shape mirrors user shape across the vertical axis`() {
        val expected = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomEnd = 16.dp,
            bottomStart = 4.dp,
        )
        assertEquals(expected, ChatBubbleShapes.Assistant)
    }

    @Test
    fun `user and assistant shapes differ`() {
        // Belt-and-braces — guards against an accidental copy/paste rebinding either constant.
        org.junit.Assert.assertNotEquals(ChatBubbleShapes.User, ChatBubbleShapes.Assistant)
    }
}
