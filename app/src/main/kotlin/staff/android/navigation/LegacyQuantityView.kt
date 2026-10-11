package staff.android.navigation

import android.content.Context
import android.text.InputType
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged

class LegacyQuantityView(context: Context,
    onChange: (String) -> Unit, onConfirm: () -> Unit,
    onCancel: () -> Unit, onReview: () -> Unit) {
    private val content = column(context)
    val root = scroll(context, content).apply { id = R.id.quantity_legacy }
    private val product = android.widget.LinearLayout(context).apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        content.addView(this)
    }
    private var photoId: String? = null
    private val title = product.label("").apply {
        textSize = 24f
        typeface = resources.getFont(R.font.sora_bold)
        layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
    }
    private val accepted = content.label("", R.id.accepted)
    val input = EditText(context).apply {
        id = R.id.quantity_input
        setHint(R.string.quantity_label)
        // Request a numeric keyboard without filtering pasted raw input to digits.
        setRawInputType(InputType.TYPE_CLASS_NUMBER)
        minHeight = context.dp(56)
        typeface = resources.getFont(R.font.google_sans_regular)
        setTextColor(context.shopForeground())
        setHintTextColor(context.shopForeground())
        filters += InputFilter { _, start, end, dest, dstart, dend ->
            if (dest.length - (dend - dstart) + end - start > 32)
                dest.subSequence(dstart, dend) else null
        }
    }
    private var rendering = false
    init {
        content.addView(input)
        input.doAfterTextChanged { if (!rendering) onChange(it.toString()) }
    }
    private val confirm = content.action(context.getString(R.string.confirm), R.id.confirm, onConfirm)
    private val problem = content.label("", R.id.problem)
    private val review = content.action(context.getString(R.string.reload), R.id.reload, onReview)
    private val status = content.label("")
    private val cancel = content.action(context.getString(R.string.cancel), R.id.cancel, onCancel)
    fun render(ui: EditorUi) {
        if (photoId != ui.productId) {
            if (product.childCount > 1) product.removeViewAt(0)
            product.productPhoto(ui.productId, 72)
            val image = product.getChildAt(product.childCount - 1)
            product.removeView(image)
            product.addView(image, 0)
            photoId = ui.productId
        }
        title.text = Products.find(ui.productId)?.title ?: root.context.getString(R.string.unknown_product)
        accepted.text = ui.accepted?.let { root.context.getString(R.string.accepted, it.quantity) }.orEmpty()
        val value = ui.raw.orEmpty()
        if (input.text.toString() != value) {
            rendering = true
            input.setText(value)
            input.setSelection(value.length)
            rendering = false
        }
        input.isEnabled = ui.accepted != null && ui.phase == SavePhase.Editing
        confirm.isEnabled = ui.canConfirm
        cancel.setText(if (ui.phase == SavePhase.Editing) R.string.cancel else R.string.leave)
        val problemKind = ui.problem
        val issue = when {
            problemKind != null -> root.context.getString(problemKind.messageId())
            ui.stale && ui.phase == SavePhase.Editing -> root.context.getString(R.string.conflict)
            ui.raw != null && ui.candidate == null -> root.context.getString(R.string.invalid_quantity)
            else -> null
        }
        problem.text = issue.orEmpty()
        input.error = issue
        review.visibility = if (ui.stale && ui.phase == SavePhase.Editing) View.VISIBLE else View.GONE
        status.text = when (ui.phase) {
            SavePhase.Editing -> ""
            SavePhase.Saving -> root.context.getString(R.string.saving)
            SavePhase.Saved -> root.context.getString(R.string.saved)
        }
    }
}
