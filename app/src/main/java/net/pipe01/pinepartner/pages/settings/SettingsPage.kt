package net.pipe01.pinepartner.pages.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.pipe01.pinepartner.components.Header

@Composable
fun SettingsPage(
    onNotificationSettings: () -> Unit,
) {
    Column {
        Header(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = "Settings",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNotificationSettings() }
                .padding(16.dp),
        ) {
            Text(text = "Notification settings", fontSize = 18.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPagePreview() {
    SettingsPage(
        onNotificationSettings = {},
    )
}
