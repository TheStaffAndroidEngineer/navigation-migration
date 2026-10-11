package staff.android.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class QuantityFragment : Fragment() {
    val owner: QuantityViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = requireActivity().application as ShopApplication
                QuantityViewModel(createSavedStateHandle(), app.repository)
            }
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val cancel = { findNavController().navigateUp(); Unit }
        if (requireArguments().getString("renderer", "compose") == "views") {
            val legacy = LegacyQuantityView(requireContext(), owner::change,
                owner::confirm, cancel, owner::reviewCurrent)
            legacy.root.setTag(R.id.quantity_legacy, legacy)
            return legacy.root
        }
        return ComposeView(requireContext()).apply {
            id = R.id.quantity_compose
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val ui by owner.state.collectAsStateWithLifecycle()
                ShopTheme { QuantityScreen(ui, owner::change, owner::confirm,
                    cancel, owner::reviewCurrent) }
            }
        }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        val controller = findNavController()
        val entry = controller.getBackStackEntry(R.id.quantity)
        val legacy = view.getTag(R.id.quantity_legacy) as? LegacyQuantityView
        if (legacy != null) viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                owner.state.collect(legacy::render)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(owner.state, entry.lifecycle.currentStateFlow) { ui, lifecycle ->
                    ui to lifecycle
                }.collect { (ui, entryState) ->
                    if (ui.phase == SavePhase.Saved && !owner.navigationHandled &&
                        entryState == Lifecycle.State.RESUMED &&
                        controller.currentBackStackEntry?.id == entry.id) {
                        owner.markNavigationHandled()
                        val returned = if (requireArguments().getBoolean("brokenReturn")) {
                            controller.navigate(R.id.cart)
                            true
                        } else returnToCart(controller)
                        if (!returned) owner.navigationFailed()
                    }
                }
            }
        }
    }
}

fun returnToCart(controller: androidx.navigation.NavController): Boolean =
    controller.popBackStack(R.id.cart, false) || controller.navigateUp()
