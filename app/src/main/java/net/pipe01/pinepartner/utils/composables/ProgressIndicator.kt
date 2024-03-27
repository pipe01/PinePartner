package net.pipe01.pinepartner.utils.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.pipe01.pinepartner.service.TransferProgress
import net.pipe01.pinepartner.utils.toMinutesSeconds

@Composable
fun ProgressIndicator(
    task: String,
    progress: TransferProgress?
) {
    if (progress == null) {
        CircularProgressIndicator()
    } else {
        Column {
            Text(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .align(Alignment.CenterHorizontally),
                text = "${task}: ${(progress.totalProgress * 100).toInt()}%",
            )

            LinearProgressIndicator(progress = { progress.totalProgress })

            if (progress.bytesPerSecond != null) {
                Text(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally),
                    text = "${progress.bytesPerSecond} Bytes/s",
                )
            }

            if (progress.timeLeft != null) {
                Text(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally),
                    text = "${progress.timeLeft.toMinutesSeconds()} left",
                )
            }
        }
    }
}