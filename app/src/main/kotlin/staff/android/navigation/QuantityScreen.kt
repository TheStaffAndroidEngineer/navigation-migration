package staff.android.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ShopTheme(content: @Composable () -> Unit) {
    CommonGoodsTheme(content = content)
}
@Composable
fun QuantityScreen(ui: EditorUi, onChange: (String) -> Unit,
    onConfirm: () -> Unit, onCancel: () -> Unit, onReview: () -> Unit) {
    val invalid = ui.raw != null && ui.candidate == null
    val problem = ui.problem
    val accepted = ui.accepted
    val message = when {
        problem != null -> stringResource(problem.messageId())
        ui.stale && ui.phase == SavePhase.Editing -> stringResource(R.string.conflict)
        invalid -> stringResource(R.string.invalid_quantity)
        else -> null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        .semantics { testTagsAsResourceId = true },
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProductPhoto(ui.productId, Modifier.size(72.dp))
            Text(Products.find(ui.productId)?.title ?: stringResource(R.string.unknown_product),
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        if (accepted != null) {
            Text(stringResource(R.string.accepted, accepted.quantity), Modifier.testTag("accepted"))
            OutlinedTextField(value = ui.raw.orEmpty(), onValueChange = onChange,
                enabled = ui.phase == SavePhase.Editing, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.quantity_label)) },
                isError = invalid || ui.stale,
                modifier = Modifier.fillMaxWidth().testTag("quantity_input").semantics {
                    if (message != null) error(message)
                })
            Button(onClick = onConfirm, enabled = ui.canConfirm,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm")) {
                Text(stringResource(R.string.confirm))
            }
        } else if (ui.problem == null) Text(stringResource(R.string.loading))
        if (message != null) Text(message, Modifier.testTag("problem").semantics {
            liveRegion = LiveRegionMode.Polite
        })
        if (ui.stale && ui.phase == SavePhase.Editing) {
            OutlinedButton(onClick = onReview, modifier = Modifier.testTag("reload")) {
                Text(stringResource(R.string.reload))
            }
        }
        if (ui.phase != SavePhase.Editing) {
            Text(stringResource(if (ui.phase == SavePhase.Saving) R.string.saving else R.string.saved))
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("cancel")) {
            Text(stringResource(if (ui.phase == SavePhase.Editing) R.string.cancel else R.string.leave))
        }
    }
}
fun EditorProblem.messageId() = when (this) {
    EditorProblem.Load -> R.string.load_failed
    EditorProblem.Save -> R.string.save_failed
    EditorProblem.Conflict -> R.string.conflict
    EditorProblem.UnknownProduct -> R.string.unknown_product
    EditorProblem.Navigation -> R.string.navigation_failed
}
