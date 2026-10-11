package staff.android.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Local debug caller outside the navigation host, not a second shipping app. */
class CallerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = column(this).apply { setPadding(dp(16), dp(64), dp(16), dp(16)) }
        content.label("External-entry caller probe")
        content.action("Open Camera link", R.id.open_camera) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("shoplab://cart/edit/camera"))
                .setPackage(packageName))
        }
        setContentView(content)
    }
}
