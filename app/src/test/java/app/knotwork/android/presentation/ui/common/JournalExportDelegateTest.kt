package app.knotwork.android.presentation.ui.common

import app.knotwork.android.domain.models.JournalExportDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Behaviour of the shared journal-export half of the two journal ViewModels.
 *
 * The cases worth pinning are the ones the screen cannot recover from on its own:
 * that a save reports **something** whatever happens (including the empty-journal
 * success and both failure shapes), and that the stream the document picker
 * handed over is closed on every path — an abandoned stream leaves a zero-byte
 * file the user believes is their journal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalExportDelegateTest {

    private val document = JournalExportDocument(json = """{"schemaVersion": 3}""", entryCount = 12)

    /** A stream that records whether it was closed, and can be made to fail on write. */
    private class RecordingStream(private val failOnWrite: Boolean = false) : OutputStream() {
        val bytes = ByteArrayOutputStream()
        var closed = false
            private set

        override fun write(b: Int) {
            if (failOnWrite) throw IOException("no space left on device")
            bytes.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (failOnWrite) throw IOException("no space left on device")
            bytes.write(b, off, len)
        }

        override fun close() {
            closed = true
        }
    }

    /**
     * Builds a delegate hosted on the test scope, with the document write driven by
     * the test scheduler rather than by `Dispatchers.IO` — otherwise
     * `advanceUntilIdle` returns before the bytes land.
     */
    private fun TestScope.delegate(build: suspend (String) -> JournalExportDocument = { document }) =
        JournalExportDelegate(
            scope = this,
            fileNameStem = TRIGGER_JOURNAL_EXPORT_STEM,
            buildDocument = build,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    /**
     * Subscribes to the delegate's one-shot events, appending each into [sink].
     *
     * A live collector has to exist before the action runs: the events channel is a
     * `MutableSharedFlow` with no replay, so an outcome emitted with nobody
     * listening is simply lost — which is also why the screen subscribes for its
     * whole lifetime rather than around each action.
     */
    private fun TestScope.collectInto(sink: MutableList<JournalExportEvent>, delegate: JournalExportDelegate) =
        launch { delegate.events.collect(sink::add) }

    @Test
    fun `given a share when requested then the document and a stemmed filename are emitted`() = runTest {
        val delegate = delegate()
        val received = mutableListOf<JournalExportEvent>()
        val collector = collectInto(received, delegate)

        delegate.share()
        advanceUntilIdle()
        collector.cancel()

        val event = received.single() as JournalExportEvent.Share
        assertEquals(document, event.document)
        // The stem and the extension are what a soak analysis globs for, so they
        // are asserted rather than assumed.
        assertTrue(event.fileName.startsWith("$TRIGGER_JOURNAL_EXPORT_STEM-"))
        assertTrue(event.fileName.endsWith(".json"))
    }

    @Test
    fun `given a picked document when saved then the json is written and the count reported`() = runTest {
        val delegate = delegate()
        val received = mutableListOf<JournalExportEvent>()
        val collector = collectInto(received, delegate)
        val stream = RecordingStream()

        delegate.saveTo(stream)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(document.json, stream.bytes.toString(Charsets.UTF_8.name()))
        assertEquals(JournalExportEvent.Saved(12), received.single())
        assertTrue(stream.closed)
    }

    @Test
    fun `given an empty journal when saved then it is reported as a save of zero entries`() = runTest {
        val empty = JournalExportDocument(json = """{"totalEvaluations": 0}""", entryCount = 0)
        val delegate = delegate { empty }
        val received = mutableListOf<JournalExportEvent>()
        val collector = collectInto(received, delegate)

        delegate.saveTo(RecordingStream())
        advanceUntilIdle()
        collector.cancel()

        // Not silence, and not a failure: the file is real and the journal was
        // empty. Only the count can say that, so the count is what is reported.
        assertEquals(JournalExportEvent.Saved(0), received.single())
    }

    @Test
    fun `given a stream that fails when saved then the failure is reported and the stream is closed`() = runTest {
        val delegate = delegate()
        val received = mutableListOf<JournalExportEvent>()
        val collector = collectInto(received, delegate)
        val stream = RecordingStream(failOnWrite = true)

        delegate.saveTo(stream)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(JournalExportEvent.SaveFailed, received.single())
        assertTrue(stream.closed)
    }

    @Test
    fun `given a journal read that throws when saved then the failure is reported and the stream is closed`() =
        runTest {
            val delegate = delegate { error("the database is unreadable") }
            val received = mutableListOf<JournalExportEvent>()
            val collector = collectInto(received, delegate)
            val stream = RecordingStream()

            delegate.saveTo(stream)
            advanceUntilIdle()
            collector.cancel()

            // The picker already created the file. Leaving its stream open would
            // strand a zero-byte document with nothing said about it.
            assertEquals(JournalExportEvent.SaveFailed, received.single())
            assertTrue(stream.closed)
        }

    @Test
    fun `given nothing requested when observed then no event is emitted`() = runTest {
        val delegate = delegate()
        val received = mutableListOf<JournalExportEvent>()
        val collector = collectInto(received, delegate)

        advanceUntilIdle()
        collector.cancel()

        assertNull(received.firstOrNull())
    }
}
