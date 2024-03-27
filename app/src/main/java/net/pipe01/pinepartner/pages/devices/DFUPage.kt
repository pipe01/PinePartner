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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.pipe01.pinepartner.components.Header
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.service.TransferProgress
import net.pipe01.pinepartner.utils.composables.ErrorDialog
import net.pipe01.pinepartner.utils.toMinutesSeconds
import java.time.Duration
import kotlin.random.Random

@Composable
fun DFUPage(
    backgroundService: BackgroundService,
    deviceAddress: String,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    var uri by remember { mutableStateOf<Uri?>(null) }

    var showErrorDialog by remember { mutableStateOf<Error?>(null) }

    if (showErrorDialog != null) {
        ErrorDialog(
            error = showErrorDialog!!,
            onDismissRequest = { showErrorDialog = null },
        )
    }

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
                onError = {
                    onCancel()
                    showErrorDialog = it
                },
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
    onError: (Error) -> Unit = { },
) {
    var progress by remember { mutableStateOf<TransferProgress?>(null) }

    val jobId = remember { Random.nextInt() }

    if (backgroundService == null) {
        progress = TransferProgress(0.4f, 10000, Duration.ofSeconds(135), false)
    } else {
        LaunchedEffect(uri) {
            onStart()

            launch {
                backgroundService.startWatchDFU(jobId, address, uri).onFailure {
                    onError(Error("Failed to do DFU transfer", it))
                }
            }

            while (true) {
                progress = backgroundService.getTransferProgress(jobId)

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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = "Uploading firmware: ${(progress!!.totalProgress * 100).toInt()}%",
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                progress = { progress?.totalProgress ?: 0f },
            )

            if (progress?.bytesPerSecond != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = "${progress?.bytesPerSecond ?: 0} Bytes/s",
                )
            }

            if (progress?.timeLeft != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = progress?.timeLeft?.toMinutesSeconds() ?: "",
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                runBlocking {
                    backgroundService!!.cancelTransfer(jobId)

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
