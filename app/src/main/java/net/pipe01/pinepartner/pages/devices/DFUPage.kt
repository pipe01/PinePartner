package net.pipe01.pinepartner.pages.devices

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import net.pipe01.pinepartner.components.LoadingStandIn
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.service.TransferProgress
import net.pipe01.pinepartner.utils.InfiniTimeRelease
import net.pipe01.pinepartner.utils.PineError
import net.pipe01.pinepartner.utils.getInfiniTimeReleases
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
    onError: (PineError) -> Unit,
) {
    var uri by remember { mutableStateOf<Uri?>(null) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Header("Firmware update")

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
                onError = onError,
            )
        }
    }
}

@Composable
private fun FileChooser(
    onFileSelected: (Uri) -> Unit,
) {
    Column(
        modifier = Modifier.scrollable(rememberScrollState(), orientation = Orientation.Vertical),
    ) {
        val pickPictureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { imageUri ->
            if (imageUri != null) {
                onFileSelected(imageUri)
            }
        }

        Text(
            text = "From device",
            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
        )

        Button(onClick = { pickPictureLauncher.launch("application/zip") }) {
            Text(text = "Choose file")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "From InfiniTime releases",
            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
        )

        InfiniTimeReleases(
            onChoseRelease = { onFileSelected(it.firmwareUri) },
            onError = {
                Log.w("DFUPage", "Failed to fetch InfiniTime releases", it)
            },
        )
    }
}

@Composable
private fun InfiniTimeReleases(
    onChoseRelease: (InfiniTimeRelease) -> Unit,
    onError: (PineError) -> Unit,
) {
    val releases = remember { mutableStateListOf<InfiniTimeRelease>() }

    var showConfirmDialog by remember { mutableStateOf<InfiniTimeRelease?>(null) }

    LaunchedEffect(Unit) {
        runCatching { getInfiniTimeReleases() }
            .fold(
                onSuccess = { releases.addAll(it) },
                onFailure = { onError(PineError("Failed to fetch InfiniTime releases", it)) }
            )
    }

    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            confirmButton = {
                TextButton(onClick = { onChoseRelease(showConfirmDialog!!) }) {
                    Text("Install")
                }
            },
            title = { Text("Install ${showConfirmDialog!!.name}?") },
        )
    }

    LoadingStandIn(isLoading = releases.isEmpty()) {
        if (releases.isEmpty()) {
            Text("Loading releases...")
        } else {
            Column {
                releases.forEach {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showConfirmDialog = it }
                            .padding(16.dp),
                    ) {
                        Text(it.name)
                    }
                }
            }
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
    onError: (PineError) -> Unit = { },
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
                    onError(PineError("Failed to do DFU transfer", it))
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
    FileChooser { }
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
