package net.pipe01.pinepartner.utils.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

interface PopupDialogScope {
    @Composable
    fun action(icon: ImageVector, text: String, onClick: () -> Unit)
}

@Composable
fun PopupDialog(
    title: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable PopupDialogScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { },
        title = title,
        text = {
            Column {
                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                content(object : PopupDialogScope {
                    @Composable
                    override fun action(icon: ImageVector, text: String, onClick: () -> Unit) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick() }
                                .padding(16.dp),
                        ) {
                            Icon(icon, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = text)
                        }
                    }
                })
            }
        },
    )
}