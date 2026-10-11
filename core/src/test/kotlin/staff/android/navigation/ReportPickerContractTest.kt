package staff.android.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

abstract class ReportPickerContractTest {
    protected abstract fun reduce(
        state: ReportPickerState,
        event: ReportPickerEvent,
    ): ReportPickerState

    private val first = ReportEntryId("entry-1")
    private val second = ReportEntryId("entry-2")
    private val request1 = PickerRequestId("request-1")
    private val request2 = PickerRequestId("request-2")

    private fun report(id: ReportEntryId) = ReportEntry(id, "assignment-7")
    private fun open(state: ReportPickerState, id: ReportEntryId) =
        reduce(state, ReportPickerEvent.OpenReport(report(id)))
    private fun pick(
        state: ReportPickerState,
        entry: ReportEntryId,
        request: PickerRequestId,
    ) = reduce(state, ReportPickerEvent.OpenPicker(PickerRequest(request, entry)))

    @Test
    fun selectionTargetsItsRequesterNotTheLatestReportWithTheSameAssignment() {
        val entries = open(open(ReportPickerState(), first), second)
        val waiting = pick(entries, first, request1)
        val result = reduce(waiting, ReportPickerEvent.Selected(request1, "pump-7"))
        assertEquals(
            listOf(report(first).copy(equipmentId = "pump-7"), report(second)),
            result.entries,
        )
        assertNull(result.pending)
        assertEquals(listOf(report(first), report(second)), waiting.entries)
    }

    @Test
    fun cancelClearsOnlyItsRequestWithoutChangingTheReport() {
        val waiting = pick(open(ReportPickerState(), first), first, request1)
        val result = reduce(waiting, ReportPickerEvent.Cancelled(request1))
        assertEquals(listOf(report(first)), result.entries)
        assertNull(result.pending)
    }

    @Test
    fun olderSelectionCannotReplaceANewerRequestForTheSameEntry() {
        val old = pick(open(ReportPickerState(), first), first, request1)
        val current = pick(old, first, request2)
        assertEquals(current, reduce(current, ReportPickerEvent.Selected(request1, "pump-7")))
        val result = reduce(current, ReportPickerEvent.Selected(request2, "meter-2"))
        assertEquals("meter-2", result.entries.single().equipmentId)
        assertNull(result.pending)
    }

    @Test
    fun aPickerForAnotherLiveEntrySupersedesTheGlobalRequest() {
        val entries = open(open(ReportPickerState(), first), second)
        val old = pick(entries, first, request1)
        val current = pick(old, second, request2)
        assertEquals(current, reduce(current, ReportPickerEvent.Selected(request1, "pump-7")))
        val result = reduce(current, ReportPickerEvent.Selected(request2, "meter-2"))
        assertEquals(
            listOf(report(first), report(second).copy(equipmentId = "meter-2")),
            result.entries,
        )
        assertNull(result.pending)
    }

    @Test
    fun closedReportAndNewReportWithSameAssignmentHaveDifferentAuthority() {
        val old = pick(open(ReportPickerState(), first), first, request1)
        val closed = reduce(old, ReportPickerEvent.CloseReport(first))
        assertEquals(ReportPickerState(), closed)
        val current = pick(open(closed, second), second, request2)
        assertEquals(current, reduce(current, ReportPickerEvent.Selected(request1, "pump-7")))
        val result = reduce(current, ReportPickerEvent.Selected(request2, "meter-2"))
        assertEquals(listOf(report(second).copy(equipmentId = "meter-2")), result.entries)
        assertNull(result.pending)
    }

    @Test
    fun aConsumedResultCannotBeAppliedAgainWithAnotherSelection() {
        val waiting = pick(open(ReportPickerState(), first), first, request1)
        val accepted = reduce(waiting, ReportPickerEvent.Selected(request1, "pump-7"))
        assertEquals(accepted, reduce(accepted, ReportPickerEvent.Selected(request1, "meter-2")))
        assertEquals("pump-7", accepted.entries.single().equipmentId)
    }

    @Test
    fun staleCancellationCannotClearTheCurrentRequest() {
        val old = pick(open(ReportPickerState(), first), first, request1)
        val current = pick(old, first, request2)
        assertEquals(current, reduce(current, ReportPickerEvent.Cancelled(request1)))
    }

    @Test
    fun closingAnotherEntryDoesNotCancelTheRequest() {
        val entries = open(open(ReportPickerState(), first), second)
        val waiting = pick(entries, first, request1)
        val result = reduce(waiting, ReportPickerEvent.CloseReport(second))
        assertEquals(listOf(report(first)), result.entries)
        assertEquals(PickerRequest(request1, first), result.pending)
    }

    @Test
    fun callerErrorsDoNotBecomePlausibleSelections() {
        val state = open(ReportPickerState(), first)
        assertThrows(IllegalArgumentException::class.java) { open(state, first) }
        assertThrows(IllegalArgumentException::class.java) { pick(state, second, request1) }
        assertThrows(IllegalArgumentException::class.java) {
            ReportPickerEvent.Selected(request1, "unknown-equipment")
        }
    }
}

class ReportPickerReferenceTest : ReportPickerContractTest() {
    override fun reduce(state: ReportPickerState, event: ReportPickerEvent) =
        staff.android.navigation.solution.reduceReportPicker(state, event)
}

@Tag("exercise")
class ReportPickerLearnerTest : ReportPickerContractTest() {
    override fun reduce(state: ReportPickerState, event: ReportPickerEvent) =
        staff.android.navigation.exercise.reduceReportPicker(state, event)
}
