package staff.android.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import java.io.IOException

class CatalogFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val app = requireActivity().application as ShopApplication
        val content = column(requireContext())
        content.label(getString(R.string.catalog_intro))
        content.productCard("camera", "Camera")
        content.productCard("tea", "Tea")
        content.action(getString(R.string.open_cart), R.id.open_cart) {
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.catalog) nav.navigate(R.id.open_cart_flow)
        }
        val lab = content.labPanel()
        lab.addView(Switch(requireContext()).apply {
            minimumHeight = context.dp(48)
            id = R.id.renderer_toggle
            setText(R.string.use_compose)
            setTextColor(requireContext().shopForeground())
            isChecked = app.composeEditor
            setOnCheckedChangeListener { _, value -> app.composeEditor = value }
        })
        lab.addView(Switch(requireContext()).apply {
            minimumHeight = context.dp(48)
            id = R.id.broken_toggle
            setText(R.string.broken_return)
            setTextColor(requireContext().shopForeground())
            isChecked = app.brokenReturn
            setOnCheckedChangeListener { _, value -> app.brokenReturn = value }
        })
        val status = lab.label("", R.id.problem)
        lab.action(getString(R.string.reset), R.id.reset) {
            viewLifecycleOwner.lifecycleScope.launch {
                try { app.repository.reset(); status.text = "" }
                catch (_: IOException) { status.setText(R.string.reset_failed) }
            }
        }
        return scroll(requireContext(), content)
    }
}
