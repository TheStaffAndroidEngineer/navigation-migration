package staff.android.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun CommonGoodsHeader() {
    Text("COMMON GOODS", style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ProductPhoto(id: String, modifier: Modifier = Modifier) {
    val resource = when (id) {
        "camera" -> R.drawable.product_camera
        "tea" -> R.drawable.product_tea
        else -> return
    }
    Image(painterResource(resource),
        contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(16.dp)))
}

@Composable
fun LabControls(content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(top = 16.dp))
    TextButton(onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().testTag("lab-controls").semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }) {
        Text(if (expanded) "Hide lab controls" else "Lab controls")
    }
    if (expanded) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
