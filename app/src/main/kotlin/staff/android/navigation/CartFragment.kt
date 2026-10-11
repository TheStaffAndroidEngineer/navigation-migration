package staff.android.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import java.io.IOException

class CartFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        scroll(requireContext(), column(requireContext()))
    override fun onViewCreated(view: View, state: Bundle?) {
        val app = requireActivity().application as ShopApplication
        val content = (view as android.widget.ScrollView).getChildAt(0) as android.widget.LinearLayout
        val status = content.label(getString(R.string.loading), R.id.problem)
        val rows = Products.all.associate { product ->
            val row = android.widget.LinearLayout(requireContext()).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, requireContext().dp(8), 0, requireContext().dp(8))
            }
            row.productPhoto(product.id)
            content.addView(row)
            product.id to row.action(product.title,
                if (product.id == "camera") R.id.open_camera else R.id.open_tea) {
                val nav = findNavController()
                if (nav.currentDestination?.id == R.id.cart) {
                    nav.navigate(R.id.edit_quantity, bundleOf(
                        "productId" to product.id,
                        "renderer" to if (app.composeEditor) "compose" else "views",
                        "brokenReturn" to app.brokenReturn,
                    ))
                }
            }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
                isEnabled = false
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    app.repository.records.collect { records ->
                        status.text = ""
                        Products.all.forEach { product ->
                            rows.getValue(product.id).apply {
                                text = getString(R.string.cart_line, product.title,
                                    records.getValue(product.id).quantity)
                                isEnabled = true
                            }
                        }
                    }
                } catch (_: IOException) {
                    status.setText(R.string.cart_load_failed)
                    rows.values.forEach { it.isEnabled = false }
                }
            }
        }
    }
}
