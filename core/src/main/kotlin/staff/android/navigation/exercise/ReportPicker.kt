package staff.android.navigation.exercise

import staff.android.navigation.ReportPickerEvent
import staff.android.navigation.ReportPickerState

// Deliberately defective: routes a result to the latest registered report.
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
        val latest = state.entries.lastOrNull()
        if (state.pending == null || latest == null) state else {
            state.copy(entries = state.entries.map { entry ->
                if (entry.id == latest.id) {
                    entry.copy(equipmentId = event.equipmentId)
                } else entry
            })
        }
    }
    is ReportPickerEvent.Cancelled -> state.copy(pending = null)
}
