package net.pipe01.pinepartner.utils.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PluginsDisabledDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "OK")
            }
        },
        title = {
            Text(text = "Plugins Disabled")
        },
        text = {
            Text(text = "The background service crashed repeatedly and has been restarted without plugins. To re-enable them, please restart the app.")
        }
    )
}