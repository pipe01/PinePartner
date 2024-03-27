package net.pipe01.pinepartner.pages.devices

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.data.Watch
import net.pipe01.pinepartner.devices.WatchState
import net.pipe01.pinepartner.service.BackgroundService

@SuppressLint("MissingPermission")
@Composable
fun DevicePage(
    db: AppDatabase,
    deviceAddress: String,
    backgroundService: BackgroundService,
    onUploadFirmware: () -> Unit,
    onBrowseFiles: () -> Unit,
    onError: (Error) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var watch by remember { mutableStateOf<Watch?>(null) }
    var state by remember { mutableStateOf<WatchState?>(null) }

    LaunchedEffect(deviceAddress) {
        coroutineScope.launch {
            watch = db.watchDao().getByAddress(deviceAddress)
            if (watch == null) {
                throw IllegalArgumentException("Device not found")
            }

            backgroundService.connectWatch(deviceAddress).onFailure {
                onError(Error("Failed to connect to watch", it))
                return@launch
            }

            state = backgroundService.getWatchState(deviceAddress).onFailure {
                onError(Error("Failed to get watch state", it))
            }.getOrNull()
        }
    }

    if (state == null) {
        Text(text = "Connecting...")
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Device address: $deviceAddress")
            Text(text = "Firmware version: ${state!!.firmwareVersion}")

            Button(onClick = onUploadFirmware) {
                Text(text = "Upload firmware")
            }

            Button(onClick = onBrowseFiles) {
                Text(text = "Browse files")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = {
                coroutineScope.launch {
                    backgroundService.sendTestNotification().onFailure {
                        onError(Error("Failed to send test notification", it))
                    }
                }
            }) {
                Text(text = "Send test notification")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Setting(
                name = "Connect on app start",
                value = watch!!.autoConnect,
                onValueChange = {
                    watch = watch!!.copy(autoConnect = it)

                    coroutineScope.launch {
                        db.watchDao().setAutoConnect(deviceAddress, it)
                    }
                }
            )
            Setting(
                name = "Reconnect automatically",
                value = watch!!.reconnect,
                onValueChange = {
                    watch = watch!!.copy(reconnect = it)

                    coroutineScope.launch {
                        db.watchDao().setReconnect(deviceAddress, it)
                        backgroundService.setWatchReconnect(deviceAddress, it)
                    }
                }
            )
        }
    }
}

@Composable
private fun Setting(
    name: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onValueChange(!value) }
            .padding(8.dp),
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f),
            text = name,
            fontSize = 20.sp,
        )

        Switch(checked = value, onCheckedChange = onValueChange)
    }
}