package staff.android.navigation

import android.app.Application
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.fragment.NavHostFragment

class ShopApplication : Application() {
    lateinit var repository: CartRepository
    var composeEditor = true
    var brokenReturn = false
    override fun onCreate() {
        super.onCreate()
        repository = DataStoreCartRepository(this)
    }
}
class MainActivity : FragmentActivity() {
    val navigation get() = (supportFragmentManager.findFragmentById(R.id.nav_host)
        as NavHostFragment).navController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shell = createShopHost(this)
        val root = shell.root
        val up = shell.up.apply { setOnClickListener { navigation.navigateUp() } }
        val title = shell.title
        val container = FragmentContainerView(this).apply { id = R.id.nav_host }
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        // This Views host owns both system and IME space for every destination.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        if (supportFragmentManager.findFragmentById(R.id.nav_host) == null) {
            val host = NavHostFragment.create(R.navigation.shop_graph)
            supportFragmentManager.beginTransaction().replace(R.id.nav_host, host)
                .setPrimaryNavigationFragment(host).commitNow()
        }
        navigation.addOnDestinationChangedListener { _, destination, _ ->
            title.text = destination.label
            up.visibility = if (destination.id == R.id.catalog) View.GONE else View.VISIBLE
        }
    }
}
