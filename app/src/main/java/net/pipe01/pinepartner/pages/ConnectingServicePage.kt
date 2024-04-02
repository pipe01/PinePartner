package net.pipe01.pinepartner.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ConnectingServicePage(crashed: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        CircularProgressIndicator()

        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = when (crashed) {
                true -> "Background service crashed, restarting it"
                false -> "Connecting to service"
            }
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ConnectingServicePagePreview() {
    ConnectingServicePage(false)
}
