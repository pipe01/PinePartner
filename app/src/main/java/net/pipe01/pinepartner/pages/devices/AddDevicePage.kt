package net.pipe01.pinepartner.pages.devices

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.data.AppDatabase
import no.nordicsemi.android.kotlin.ble.core.ServerDevice
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner

@SuppressLint("MissingPermission")
@Composable
fun AddDevicePage(
    db: AppDatabase,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isAddingDevice by remember { mutableStateOf(false) }

    var devices by remember { mutableStateOf(listOf<ServerDevice>()) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            BleScanner(context)
                .scan()
                .filter { it.device.hasName && it.device.name == "InfiniTime" && db.watchDao().countByAddress(it.device.address) == 0 }
                .onEach { dev ->
                    if (devices.find { it.address == dev.device.address } == null) {
                        devices = devices.plus(dev.device)
                    }
                }
                .collect { }
        }
    }

    if (isAddingDevice) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { /*TODO*/ },
            dismissButton = {
                TextButton(onClick = {
                    isAddingDevice = false
                }) {
                    Text(text = "Cancel")
                }
            },
            title = { Text(text = "Adding device") },
            text = {
                CircularProgressIndicator()
            }
        )
    }

    Column {
        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(8.dp),
            text = "Scanning for devices...",
            fontSize = 20.sp,
        )

        devices.forEach { device ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        coroutineScope.launch {
                            db.watchDao().insert(device.address, device.name ?: "Unknown")
                            onDone()
                        }
                    }
                    .padding(8.dp)
            ) {
                Text(text = device.name ?: "Unknown")
                Text(text = device.address)
            }
        }
    }
}