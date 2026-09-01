package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for [DialogInventoryChecker].
 *
 * Two properties carry the gate. It has to see a dialog composed anywhere in a
 * file — the inventory it replaces missed a whole class of them by matching on
 * declaration names — and it has to ignore the ones written *about* rather than
 * written, because this codebase discusses these composables in prose
 * constantly and a check that fired on its own documentation would be switched
 * off the same day.
 */
class DialogInventoryCheckerTest {

    private fun scan(source: String, allowed: Map<String, String> = emptyMap()) =
        DialogInventoryChecker.scan(mapOf("Screen.kt" to source.trimIndent()), allowed)

    @Test
    fun `given a dialog composed in app then it is reported`() {
        val violations = scan(
            """
            package app

            @Composable
            fun Screen() {
                AlertDialog(onDismissRequest = {}, confirmButton = {})
            }
            """,
        )

        assertEquals(1, violations.size)
        assertEquals("AlertDialog", violations.first().host)
        assertEquals(5, violations.first().line)
    }

    @Test
    fun `given a sheet composed in app then it is reported`() {
        val violations = scan(
            """
            package app

            @Composable
            fun Screen() {
                ModalBottomSheet(onDismissRequest = {}) { Body() }
            }
            """,
        )

        assertEquals(1, violations.size)
        assertEquals("ModalBottomSheet", violations.first().host)
    }

    @Test
    fun `given an allowlisted file then nothing is reported`() {
        val violations = DialogInventoryChecker.scan(
            files = mapOf("Screen.kt" to "package app\n\nfun s() { AlertDialog(x) }\n"),
            allowed = mapOf("Screen.kt" to "Hosts a catalog body; the host owns scrim and IME."),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `given the composable named only in a line comment then it is ignored`() {
        val violations = scan(
            """
            package app

            // The catalog owns the body; AlertDialog( lives in the host.
            fun s() = Unit
            """,
        )

        assertTrue("A mention is not a composition: $violations", violations.isEmpty())
    }

    @Test
    fun `given the composable named inside a doc block then it is ignored`() {
        // KDoc in this codebase routinely explains why a ModalBottomSheet does
        // not lay out under Robolectric. That prose must not read as a call.
        val violations = scan(
            """
            package app

            /**
             * A ModalBottomSheet( does not lay out under Robolectric, so the
             * body is split out. AlertDialog( has the same problem with fields.
             */
            fun s() = Unit
            """,
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `given an import of the composable then it is ignored`() {
        // An import names it without composing it, and every host file has one.
        val violations = scan(
            """
            package app

            import androidx.compose.material3.AlertDialog

            fun s() = Unit
            """,
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `given several call sites in one file then each is reported`() {
        val violations = scan(
            """
            package app

            fun a() { AlertDialog(x) }
            fun b() { ModalBottomSheet(y) }
            """,
        )

        assertEquals(2, violations.size)
    }

    @Test
    fun `given an allowlist entry whose file no longer holds a dialog then it is stale`() {
        // The half a gate usually forgets. An allowlist that outlives what it
        // excused is how the rule rots into decoration.
        val stale = DialogInventoryChecker.staleEntries(
            files = mapOf("Screen.kt" to "package app\n\nfun s() = Unit\n"),
            allowed = mapOf("Screen.kt" to "was hosting a dialog once"),
        )

        assertEquals(listOf("Screen.kt"), stale)
    }

    @Test
    fun `given an allowlist entry for a deleted file then it is stale`() {
        val stale = DialogInventoryChecker.staleEntries(
            files = emptyMap(),
            allowed = mapOf("Gone.kt" to "file no longer exists"),
        )

        assertEquals(listOf("Gone.kt"), stale)
    }

    @Test
    fun `given an allowlist entry that still matches then it is not stale`() {
        val stale = DialogInventoryChecker.staleEntries(
            files = mapOf("Screen.kt" to "package app\n\nfun s() { AlertDialog(x) }\n"),
            allowed = mapOf("Screen.kt" to "hosts a catalog body"),
        )

        assertTrue(stale.isEmpty())
    }
}
