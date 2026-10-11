package staff.android.navigation

data class Product(val id: String, val title: String)
object Products {
    val all = listOf(Product("camera", "Camera"), Product("tea", "Tea"))
    fun find(id: String) = all.find { it.id == id }
}
data class AcceptedQuantity(val quantity: Int = 2, val revision: Long = 0)
enum class SavePhase { Editing, Saving, Saved }
enum class EditorProblem { Load, Save, Conflict, UnknownProduct, Navigation }
data class EditorUi(
    val productId: String,
    val raw: String? = null,
    val baseRevision: Long? = null,
    val accepted: AcceptedQuantity? = null,
    val phase: SavePhase = SavePhase.Editing,
    val problem: EditorProblem? = null,
) {
    val candidate: Int? get() = raw?.trim()?.toIntOrNull()?.takeIf { it in 0..5 }
    val stale: Boolean get() = accepted != null && baseRevision != null &&
        accepted.revision != baseRevision
    val canConfirm: Boolean get() = Products.find(productId) != null &&
        accepted != null && candidate != null && !stale && phase == SavePhase.Editing
}
