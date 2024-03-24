package net.pipe01.pinepartner.pages.devices

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.pipe01.pinepartner.components.Header
import net.pipe01.pinepartner.devices.DFUProgress
import net.pipe01.pinepartner.service.BackgroundService
import kotlin.time.Duration.Companion.seconds

@Composable
fun DFUPage(
    backgroundService: BackgroundService,
    deviceAddress: String,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    var uri by remember { mutableStateOf<Uri?>(null) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Header("Firmware update")

        Spacer(modifier = Modifier.height(48.dp))

        if (uri == null) {
            FileChooser { uri = it }
        } else {
            Uploader(
                backgroundService = backgroundService,
                address = deviceAddress,
                uri = uri!!,
                onStart = onStart,
                onFinish = onFinish,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun FileChooser(
    onFileSelected: (Uri) -> Unit,
) {
    Column {
        val pickPictureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { imageUri ->
            if (imageUri != null) {
                onFileSelected(imageUri)
            }
        }

        Button(onClick = { pickPictureLauncher.launch("application/zip") }) {
            Text(text = "Choose DFU firmware file")
        }
    }
}

@Composable
private fun Uploader(
    backgroundService: BackgroundService?,
    address: String,
    uri: Uri,
    onStart: () -> Unit = { },
    onFinish: () -> Unit = { },
    onCancel: () -> Unit = { },
) {
    var progress by remember { mutableStateOf<DFUProgress?>(null) }

    if (backgroundService == null) {
        progress = DFUProgress("Test test test", 0.4f, 0.6f, 10000, 135)
    } else {
        LaunchedEffect(uri) {
            onStart()

            backgroundService.startWatchDFU(address, uri)

            while (true) {
                progress = backgroundService.getDFUProgress(address)

                if (progress?.isDone == true) {
                    onFinish()
                }

                delay(500)
            }
        }
    }

    Column {
        if (progress?.isDone == true) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = "Firmware update complete",
            )

            onFinish()
        } else if (progress != null) {
            BackHandler { }

            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = progress!!.stageName,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { progress!!.totalProgress },
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { progress!!.stageProgress },
            )

            if (progress!!.bytesPerSecond != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = "${progress!!.bytesPerSecond!!} Bytes/s",
                )
            }

            if (progress!!.secondsLeft != null) {
                Spacer(modifier = Modifier.height(8.dp))

                val duration = progress!!.secondsLeft!!.seconds

                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = duration.toComponents { minutes, seconds, _ ->
                        "%02d:%02d left".format(minutes, seconds)
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                runBlocking {
                    backgroundService!!.cancelDFU(address)

                    onCancel()
                }
            }) {
                Text(text = "Cancel")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FileChooserPreview() {
    FileChooser(
        onFileSelected = { }
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UploaderPreview() {
    Uploader(
        backgroundService = null,
        address = "00:00:00:00:00:00",
        uri = Uri.EMPTY,
    )
}
