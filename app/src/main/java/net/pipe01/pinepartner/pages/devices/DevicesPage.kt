package net.pipe01.pinepartner.pages.devices

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pipe01.pinepartner.components.Header
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.data.Watch
import net.pipe01.pinepartner.devices.WatchState
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.utils.BoxWithFAB


@Composable
fun DevicesPage(
    db: AppDatabase,
    backgroundService: BackgroundService,
    onAddDevice: () -> Unit,
    onDeviceClick: (address: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val watches = remember { mutableStateListOf<Watch>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            watches.addAll(db.watchDao().getAll())

            Log.d("MainPage", "Loaded watches: ${watches.size}")
        }
    }

    BoxWithFAB(fab = {
        ExtendedFloatingActionButton(
            modifier = it,
            onClick = onAddDevice,
            icon = { Icon(Icons.Filled.Add, "Add device") },
            text = { Text(text = "Add new device") },
        )
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Header(text = "Registered Devices")

            if (watches.isEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .alpha(0.6f),
                    text = "Press the button below to add a new device.",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                watches.forEach { watch ->
                    DeviceItem(
                        watch = watch,
                        coroutineScope = coroutineScope,
                        backgroundService = backgroundService,
                        onClick = { onDeviceClick(watch.address) },
                        onRemoveDevice = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    db.watchDao().delete(watch.address)
                                    watches.remove(watch)
                                    TODO("Tell background service to remove the device")
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceItem(
    watch: Watch,
    coroutineScope: CoroutineScope,
    backgroundService: BackgroundService,
    onClick: () -> Unit,
    onRemoveDevice: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var state by remember { mutableStateOf<WatchState?>(null) }

    var showDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(watch.address) {
        withContext(Dispatchers.Main) {
            while (true) {
                state = backgroundService.getWatchState(watch.address)

                delay(1000)
            }
        }
    }

    var showConfirmRemoveDialog by remember { mutableStateOf(false) }

    if (showConfirmRemoveDialog) {
        AlertDialog(
            title = { Text(text = "Remove device") },
            text = { Text(text = "Are you sure you want to remove this device?") },
            onDismissRequest = { showConfirmRemoveDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveDevice()
                    showConfirmRemoveDialog = false
                }) {
                    Text(text = "Remove")
                }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDropdown = true
                },
            )
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Box {
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove device") },
                        onClick = {
                            showDropdown = false
                            showConfirmRemoveDialog = true
                        },
                    )
                }

                Text(text = watch.name)
            }

            Text(
                modifier = Modifier.alpha(0.5f),
                text = watch.address,
            )

            Text(
                text = if (state?.isConnected == true)
                    "Connected, battery: %.0f%%".format(state!!.batteryLevel * 100)
                else
                    "Not connected",
            )
        }

        if (state?.isConnected == true) {
            Button(onClick = {
                coroutineScope.launch {
                    backgroundService.disconnectWatch(watch.address)
                }

                state = null
            }) {
                Text(text = "Disconnect")
            }
        }
    }
}
