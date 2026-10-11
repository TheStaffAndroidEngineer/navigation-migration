package staff.android.navigation.solution

import staff.android.navigation.ReportPickerEvent
import staff.android.navigation.ReportPickerState

fun reduceReportPicker(
    state: ReportPickerState,
    event: ReportPickerEvent,
): ReportPickerState = when (event) {
    is ReportPickerEvent.OpenReport -> {
        require(state.entries.none { it.id == event.entry.id })
        state.copy(entries = state.entries + event.entry)
    }
    is ReportPickerEvent.CloseReport -> state.copy(
        entries = state.entries.filterNot { it.id == event.entryId },
        pending = state.pending?.takeUnless { it.entryId == event.entryId },
    )
    is ReportPickerEvent.OpenPicker -> {
        require(state.entries.any { it.id == event.request.entryId })
        state.copy(pending = event.request)
    }
    is ReportPickerEvent.Selected -> {
        val request = state.pending
        if (request == null || request.requestId != event.requestId ||
            state.entries.none { it.id == request.entryId }
        ) {
            state
        } else {
            state.copy(
                entries = state.entries.map { entry ->
                    if (entry.id == request.entryId) {
                        entry.copy(equipmentId = event.equipmentId)
                    } else entry
                },
                pending = null,
            )
        }
    }
    is ReportPickerEvent.Cancelled ->
        if (state.pending?.requestId == event.requestId) {
            state.copy(pending = null)
        } else state
}
