package ai.agent.android.domain.constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the engine-side timing/log constants exposed by
 * [PipelineExecutionDefaults]. These values directly influence UI smoothness
 * (the pre-warm delay) and logcat verbosity (the log-char limit), so a silent
 * regression here would be hard to spot in code review.
 */
class PipelineExecutionDefaultsTest {

    @Test
    fun `given node result emit delay when read then matches documented value`() {
        assertEquals(1_000L, PipelineExecutionDefaults.NODE_RESULT_EMIT_DELAY_MS)
    }

    @Test
    fun `given lite rt prewarm delay when read then matches documented value`() {
        assertEquals(500L, PipelineExecutionDefaults.LITE_RT_PREWARM_DELAY_MS)
    }

    @Test
    fun `given node io log char limit when read then matches documented value`() {
        assertEquals(1_000, PipelineExecutionDefaults.NODE_IO_LOG_CHAR_LIMIT)
    }

    @Test
    fun `given prewarm delay when compared with emit delay then strictly shorter`() {
        // A pre-warm pause that exceeds the post-emit delay would defeat the
        // purpose of either: the UI flush window must precede the heavy
        // inference, not the other way around. This guards the ordering.
        assertTrue(
            PipelineExecutionDefaults.LITE_RT_PREWARM_DELAY_MS <
                PipelineExecutionDefaults.NODE_RESULT_EMIT_DELAY_MS,
        )
    }
}
