package app.knotwork.android.presentation.ui.settings.runlimits

import app.knotwork.android.domain.constants.SettingsDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The token track is logarithmic because the range spans three orders of
 * magnitude. These tests pin the two properties that makes it usable: a value
 * survives a round trip through the track, and the readings are numbers a
 * person would recognise as a limit.
 */
class TokenLimitScaleTest {

    @Test
    fun `given a value on the track then it round-trips`() {
        listOf(10_000, 50_000, 100_000, 1_000_000, 5_000_000, 10_000_000).forEach { tokens ->
            assertEquals(
                "a limit must read back as itself",
                tokens,
                TokenLimitScale.tokensAt(TokenLimitScale.positionOf(tokens)),
            )
        }
    }

    @Test
    fun `given the track ends then they are the permitted bounds`() {
        assertEquals(SettingsDefaults.RUN_MAX_TOKENS_MIN, TokenLimitScale.tokensAt(TokenLimitScale.minPosition))
        assertEquals(SettingsDefaults.RUN_MAX_TOKENS_MAX, TokenLimitScale.tokensAt(TokenLimitScale.maxPosition))
    }

    @Test
    fun `given any position then the reading stays inside the permitted range`() {
        // Including positions off both ends: a slider can overshoot by a hair
        // and the repository must never be handed an out-of-range ceiling.
        listOf(0f, 1f, TokenLimitScale.minPosition - 1f, TokenLimitScale.maxPosition + 1f, 99f).forEach { position ->
            val tokens = TokenLimitScale.tokensAt(position)
            assertTrue(
                "$position produced $tokens",
                tokens in SettingsDefaults.RUN_MAX_TOKENS_MIN..SettingsDefaults.RUN_MAX_TOKENS_MAX,
            )
        }
    }

    @Test
    fun `given a reading then it is rounded to something a person would say`() {
        // The raw output of an exponential is meaningless as a limit: nobody
        // means "1 037 528 tokens". Three significant figures keeps the row
        // looking like a setting rather than a measurement.
        val midpoint = (TokenLimitScale.minPosition + TokenLimitScale.maxPosition) / 2f
        val reading = TokenLimitScale.tokensAt(midpoint)
        val significant = reading.toString().trimEnd('0').length
        assertTrue("$reading carries too much precision for a limit", significant <= SIGNIFICANT_FIGURES)
    }

    @Test
    fun `given the track then a proportional step costs the same travel anywhere`() {
        // The property that makes the log scale worth its complexity: ten times
        // the limit is the same drag distance at the bottom of the track as at
        // the top, where a linear track would bury every useful value in the
        // first few percent.
        val lowDecade = TokenLimitScale.positionOf(100_000) - TokenLimitScale.positionOf(10_000)
        val highDecade = TokenLimitScale.positionOf(10_000_000) - TokenLimitScale.positionOf(1_000_000)
        assertEquals(lowDecade, highDecade, TOLERANCE)
    }

    private companion object {
        const val SIGNIFICANT_FIGURES: Int = 3
        const val TOLERANCE: Float = 0.0001f
    }
}
