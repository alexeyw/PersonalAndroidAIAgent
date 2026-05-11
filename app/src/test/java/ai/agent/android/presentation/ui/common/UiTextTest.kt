package ai.agent.android.presentation.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UiText].
 *
 * The class is intentionally tiny but it sits on the boundary between layers
 * that hold `Context` and those that must not, so the tests lock the data-class
 * contracts (equality, immutability, default args) and the convenience
 * factories that the rest of the presentation layer relies on.
 */
class UiTextTest {

    @Test
    fun `given two Resource instances with equal id and args when compared then they are equal`() {
        val a = UiText.Resource(id = 42, args = listOf("foo", 7))
        val b = UiText.Resource(id = 42, args = listOf("foo", 7))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `given two Resource instances with different args when compared then they are not equal`() {
        val a = UiText.Resource(id = 1, args = listOf("foo"))
        val b = UiText.Resource(id = 1, args = listOf("bar"))
        assertNotEquals(a, b)
    }

    @Test
    fun `given Resource without explicit args when constructed then args defaults to empty list`() {
        val sut = UiText.Resource(id = 99)
        assertTrue(sut.args.isEmpty())
    }

    @Test
    fun `given Dynamic instances with same text when compared then they are equal`() {
        val a = UiText.Dynamic("hello")
        val b = UiText.Dynamic("hello")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `given Empty when referenced twice then both references point to the same singleton`() {
        assertSame(UiText.Empty, UiText.Empty)
    }

    @Test
    fun `given Resource and Dynamic with comparable contents when compared then they are not equal`() {
        val resource: UiText = UiText.Resource(id = 1)
        val dynamic: UiText = UiText.Dynamic("any")
        assertNotEquals(resource, dynamic)
    }

    @Test
    fun `given id when factory invoke called then returns Resource with no args`() {
        val sut = UiText(123)
        assertEquals(UiText.Resource(id = 123, args = emptyList()), sut)
    }

    @Test
    fun `given id and vararg args when factory of called then returns Resource with args list`() {
        val sut = UiText.of(7, "name", 12)
        assertEquals(UiText.Resource(id = 7, args = listOf("name", 12)), sut)
    }
}
