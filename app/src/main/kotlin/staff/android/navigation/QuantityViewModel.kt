package staff.android.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class QuantityViewModel(
    private val saved: SavedStateHandle,
    private val repository: CartRepository,
) : ViewModel() {
    private val id = saved.get<String>("productId").orEmpty()
    private val mutable = MutableStateFlow(EditorUi(id))
    val state = mutable.asStateFlow()
    var navigationHandled = false
        private set
    init {
        if (Products.find(id) == null) {
            mutable.value = EditorUi(id, problem = EditorProblem.UnknownProduct)
        } else viewModelScope.launch {
            try {
                repository.records.collect { records ->
                    val accepted = records.getValue(id)
                    val raw = saved.get<String>("raw") ?: accepted.quantity.toString()
                    val base = saved.get<Long>("baseRevision") ?: accepted.revision
                    saved["raw"] = raw
                    saved["baseRevision"] = base
                    mutable.value = mutable.value.copy(
                        raw = raw, baseRevision = base, accepted = accepted,
                    )
                }
            } catch (_: IOException) {
                mutable.value = mutable.value.copy(accepted = null, problem = EditorProblem.Load)
            }
        }
    }
    fun change(raw: String) {
        if (mutable.value.phase != SavePhase.Editing || raw.length > 32) return
        saved["raw"] = raw
        mutable.value = mutable.value.copy(raw = raw, problem = null)
    }
    fun reviewCurrent() {
        val current = mutable.value
        if (current.phase != SavePhase.Editing) return
        val accepted = current.accepted ?: return
        saved["raw"] = accepted.quantity.toString()
        saved["baseRevision"] = accepted.revision
        mutable.value = current.copy(raw = accepted.quantity.toString(),
            baseRevision = accepted.revision, problem = null)
    }
    fun confirm() {
        val current = mutable.value
        if (!current.canConfirm) return
        mutable.value = current.copy(phase = SavePhase.Saving, problem = null)
        viewModelScope.launch {
            try {
                repository.confirm(id, current.candidate!!, current.baseRevision!!)
                mutable.value = mutable.value.copy(phase = SavePhase.Saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RevisionMismatch) {
                mutable.value = mutable.value.copy(phase = SavePhase.Editing,
                    problem = EditorProblem.Conflict)
            } catch (_: IOException) {
                mutable.value = mutable.value.copy(phase = SavePhase.Editing,
                    problem = EditorProblem.Save)
            }
        }
    }
    fun markNavigationHandled() { navigationHandled = true }
    fun navigationFailed() {
        mutable.value = mutable.value.copy(problem = EditorProblem.Navigation)
    }
}
