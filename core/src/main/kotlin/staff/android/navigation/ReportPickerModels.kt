package staff.android.navigation

@JvmInline
value class ReportEntryId(val value: String) {
    init { require(value.isNotBlank()) }
}

@JvmInline
value class PickerRequestId(val value: String) {
    init { require(value.isNotBlank()) }
}

val EquipmentIds = setOf("pump-7", "meter-2")

data class ReportEntry(
    val id: ReportEntryId,
    val assignmentId: String,
    val equipmentId: String? = null,
) {
    init {
        require(assignmentId.isNotBlank())
        require(equipmentId == null || equipmentId in EquipmentIds)
    }
}

data class PickerRequest(
    val requestId: PickerRequestId,
    val entryId: ReportEntryId,
)

data class ReportPickerState(
    val entries: List<ReportEntry> = emptyList(),
    val pending: PickerRequest? = null,
) {
    init { require(entries.map { it.id }.distinct().size == entries.size) }
}

sealed interface ReportPickerEvent {
    data class OpenReport(val entry: ReportEntry) : ReportPickerEvent
    data class CloseReport(val entryId: ReportEntryId) : ReportPickerEvent
    data class OpenPicker(val request: PickerRequest) : ReportPickerEvent
    data class Selected(
        val requestId: PickerRequestId,
        val equipmentId: String,
    ) : ReportPickerEvent {
        init { require(equipmentId in EquipmentIds) }
    }
    data class Cancelled(val requestId: PickerRequestId) : ReportPickerEvent
}
