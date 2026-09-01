package app.knotwork.android.domain.usecases.automation

import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [BuildExternalAutomationJournalExportUseCase] renders the request
 * journal into a correct JSON document: the header, every status and refusal
 * reason as the discriminator already published as the callback contract, the two
 * sender columns kept apart, and the caller's order preserved.
 */
class BuildExternalAutomationJournalExportUseCaseTest {

    private val useCase = BuildExternalAutomationJournalExportUseCase()

    private val label = "2026-08-30 21:30:00"

    private fun entry(
        id: String,
        requestId: String = "req-1",
        receivedAt: Long = 0L,
        action: String = "app.knotwork.android.action.RUN_PIPELINE",
        target: ExternalAutomationTarget? = null,
        declaredReturnPackage: String? = null,
        returnAction: String = "app.knotwork.android.action.RUN_RESULT",
        attestedSenderPackage: String? = null,
        status: ExternalAutomationStatus = ExternalAutomationStatus.Accepted,
        runId: String? = null,
        repeatCount: Int = 1,
    ) = ExternalAutomationJournalEntry(
        id = id,
        requestId = requestId,
        receivedAt = receivedAt,
        action = action,
        target = target,
        declaredReturnPackage = declaredReturnPackage,
        returnAction = returnAction,
        attestedSenderPackage = attestedSenderPackage,
        status = status,
        runId = runId,
        repeatCount = repeatCount,
    )

    private fun render(entries: List<ExternalAutomationJournalEntry>) =
        Json.parseToJsonElement(useCase(entries, label)).jsonObject

    private fun requests(entries: List<ExternalAutomationJournalEntry>) = render(entries).getValue("requests").jsonArray

    @Test
    fun `given an empty journal when rendered then the header is present and the list is empty`() {
        val document = render(emptyList())

        assertEquals(1, document.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(label, document.getValue("generatedAt").jsonPrimitive.content)
        assertTrue(document.getValue("localOnly").jsonPrimitive.content.toBoolean())
        assertEquals(0, document.getValue("totalRequests").jsonPrimitive.int)
        assertTrue(document.getValue("requests").jsonArray.isEmpty())
    }

    @Test
    fun `given an admitted request when rendered then every column is carried verbatim`() {
        val row = requests(
            listOf(
                entry(
                    id = "row-1",
                    requestId = "tsk-42",
                    receivedAt = 1_724_000_000_000L,
                    target = ExternalAutomationTarget.ById("pipe-7"),
                    declaredReturnPackage = "net.dinglisch.android.taskerm",
                    attestedSenderPackage = "com.example.attested",
                    status = ExternalAutomationStatus.Accepted,
                    runId = "run-9",
                    repeatCount = 1,
                ),
            ),
        ).first().jsonObject

        assertEquals("row-1", row.getValue("id").jsonPrimitive.content)
        assertEquals("tsk-42", row.getValue("requestId").jsonPrimitive.content)
        assertEquals(1_724_000_000_000L, row.getValue("receivedAt").jsonPrimitive.long)
        assertEquals("app.knotwork.android.action.RUN_PIPELINE", row.getValue("action").jsonPrimitive.content)
        assertEquals("ID", row.getValue("targetKind").jsonPrimitive.content)
        assertEquals("pipe-7", row.getValue("targetValue").jsonPrimitive.content)
        assertEquals("net.dinglisch.android.taskerm", row.getValue("declaredReturnPackage").jsonPrimitive.content)
        assertEquals("app.knotwork.android.action.RUN_RESULT", row.getValue("returnAction").jsonPrimitive.content)
        assertEquals("com.example.attested", row.getValue("attestedSenderPackage").jsonPrimitive.content)
        assertEquals("Accepted", row.getValue("status").jsonPrimitive.content)
        assertEquals(JsonNull, row.getValue("statusReason"))
        assertEquals("run-9", row.getValue("runId").jsonPrimitive.content)
        assertEquals(1, row.getValue("repeatCount").jsonPrimitive.int)
    }

    @Test
    fun `given a request naming its target by name when rendered then the kind says so`() {
        val row = requests(listOf(entry("row-1", target = ExternalAutomationTarget.ByName("Morning digest"))))
            .first().jsonObject

        // Kind and value stay separate keys: an analyst reading a
        // TARGET_NOT_ALLOWED refusal needs to know which coordinate the caller used.
        assertEquals("NAME", row.getValue("targetKind").jsonPrimitive.content)
        assertEquals("Morning digest", row.getValue("targetValue").jsonPrimitive.content)
    }

    @Test
    fun `given a request that named no target when rendered then both target keys are explicit nulls`() {
        val row = requests(listOf(entry("row-1", target = null))).first().jsonObject

        // Present-and-null rather than absent: naming no pipeline is itself a
        // refusal reason, and "the key is missing" would read as a shape change.
        assertEquals(JsonNull, row.getValue("targetKind"))
        assertEquals(JsonNull, row.getValue("targetValue"))
    }

    @Test
    fun `given every status when rendered then the published discriminator is emitted`() {
        val entries = listOf(
            entry("a", status = ExternalAutomationStatus.Accepted),
            entry("b", status = ExternalAutomationStatus.Completed),
            entry("c", status = ExternalAutomationStatus.Failed),
            entry(
                "d",
                status = ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.CONTRACT_DISABLED),
            ),
            entry("e", status = ExternalAutomationStatus.Blocked(ExternalAutomationRejectionReason.RATE_LIMITED)),
        )

        val statuses = requests(entries).map { it.jsonObject.getValue("status").jsonPrimitive.content }

        // Exactly the vocabulary docs/external-automation.md freezes for the
        // callback, so one consumer can read both a callback and a dump.
        assertEquals(listOf("Accepted", "Completed", "Failed", "Rejected", "Blocked"), statuses)
    }

    @Test
    fun `given every refusal reason when rendered then its constant name is emitted`() {
        val entries = ExternalAutomationRejectionReason.entries.mapIndexed { index, reason ->
            entry("row-$index", status = ExternalAutomationStatus.Rejected(reason))
        }

        val reasons = requests(entries).map { it.jsonObject.getValue("statusReason").jsonPrimitive.content }

        // Every reason, not a sample: the enum is the export's vocabulary, so a
        // value added later must fail this test until it is considered.
        assertEquals(ExternalAutomationRejectionReason.entries.map { it.name }, reasons)
    }

    @Test
    fun `given a non-refusal status when rendered then the reason is an explicit null`() {
        val row = requests(listOf(entry("row-1", status = ExternalAutomationStatus.Completed))).first().jsonObject

        assertEquals(JsonNull, row.getValue("statusReason"))
    }

    @Test
    fun `given a collapsed refusal when rendered then the repeat count travels`() {
        val row = requests(
            listOf(
                entry(
                    "row-1",
                    status = ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.UNKNOWN_ACTION),
                    repeatCount = 43,
                ),
            ),
        ).first().jsonObject

        // A looping caller is one recurring problem, and the count is the only
        // thing in the document that says so.
        assertEquals(43, row.getValue("repeatCount").jsonPrimitive.int)
    }

    @Test
    fun `given several entries when rendered then the caller's order is preserved and counted`() {
        val document = render(listOf(entry("first"), entry("second"), entry("third")))

        assertEquals(3, document.getValue("totalRequests").jsonPrimitive.int)
        assertEquals(
            listOf("first", "second", "third"),
            document.getValue("requests").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content },
        )
    }
}
