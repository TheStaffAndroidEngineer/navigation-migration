package staff.android.navigation

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*

object ShopTokens {
    const val primary = 0xff3156d3.toInt()
    const val background = 0xffffffff.toInt()
    const val foreground = 0xff192327.toInt()
    const val spacingDp = 16
}
fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
fun Context.darkShop() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
fun Context.shopBackground() = if (darkShop()) 0xff12181d.toInt() else ShopTokens.background
fun Context.shopForeground() = if (darkShop()) 0xffebeff1.toInt() else ShopTokens.foreground
fun column(context: Context) = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(20))
    setBackgroundColor(context.shopBackground())
}
fun scroll(context: Context, content: LinearLayout) = ScrollView(context).apply {
    id = R.id.screen
    isFillViewport = true
    addView(content)
}
fun LinearLayout.label(value: String, viewId: Int = ViewIdNone) = TextView(context).also {
    if (viewId != ViewIdNone) it.id = viewId
    it.text = value
    it.textSize = 16f
    it.typeface = resources.getFont(R.font.google_sans_regular)
    it.setTextColor(context.shopForeground())
    it.setPadding(0, context.dp(8), 0, context.dp(8))
    addView(it)
}
private const val ViewIdNone = -1
fun LinearLayout.action(label: String, viewId: Int, action: () -> Unit) = Button(context).also {
    it.id = viewId
    it.text = label
    it.isAllCaps = false
    it.textSize = 16f
    it.typeface = resources.getFont(R.font.google_sans_bold)
    it.minimumHeight = context.dp(52)
    it.setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
    it.backgroundTintList = ColorStateList.valueOf(if (context.darkShop()) 0xffb5c4ff.toInt() else ShopTokens.primary)
    it.setTextColor(if (context.darkShop()) 0xff162d77.toInt() else Color.WHITE)
    it.setOnClickListener { action() }
    addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
}
fun LinearLayout.productPhoto(id: String, size: Int = 88) {
    val resource = when (id) {
        "camera" -> R.drawable.product_camera
        "tea" -> R.drawable.product_tea
        else -> return
    }
    addView(ImageView(context).apply {
        setImageResource(resource)
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply { cornerRadius = context.dp(12).toFloat() }
        clipToOutline = true
    }, LinearLayout.LayoutParams(context.dp(size), context.dp(size)).apply { marginEnd = context.dp(12) })
}
fun LinearLayout.productCard(id: String, title: String) {
    val row = LinearLayout(context).apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, context.dp(8), 0, context.dp(8))
    }
    row.productPhoto(id)
    row.label(title).apply {
        textSize = 20f
        typeface = resources.getFont(R.font.google_sans_bold)
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }
    addView(row)
}
fun LinearLayout.labPanel(): LinearLayout {
    val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    action("Lab controls", R.id.lab_controls) {
        val expanded = panel.visibility != View.VISIBLE
        panel.visibility = if (expanded) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.lab_controls).apply {
            text = if (expanded) "Hide lab controls" else "Lab controls"
            androidx.core.view.ViewCompat.setStateDescription(
                this, if (expanded) "Expanded" else "Collapsed")
        }
    }.apply {
        backgroundTintList = ColorStateList.valueOf(if (context.darkShop()) 0xff202a32.toInt() else 0xfff1f4f6.toInt())
        setTextColor(context.shopForeground())
    }
    addView(panel)
    return panel
}

data class ShopHost(val root: LinearLayout, val up: Button, val title: TextView)
fun createShopHost(context: Context): ShopHost = with(context) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(shopBackground())
        }
        root.label("COMMON GOODS").apply {
            textSize = 14f
            typeface = resources.getFont(R.font.google_sans_bold)
            setTextColor(if (darkShop()) 0xffb5c4ff.toInt() else ShopTokens.primary)
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val heading = LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(20), dp(8))
        }
        val up = Button(context).apply {
            id = R.id.up
            setText(R.string.up)
            isAllCaps = false
            setTextColor(shopForeground())
        }
        val title = TextView(context).apply {
            textSize = 28f
            typeface = resources.getFont(R.font.sora_bold)
            setTextColor(shopForeground())
            setPadding(dp(8), 0, 0, 0)
        }
        heading.addView(up)
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(heading)
        ShopHost(root, up, title)
}
