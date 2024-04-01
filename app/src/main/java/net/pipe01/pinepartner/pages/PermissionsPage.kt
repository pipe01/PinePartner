package net.pipe01.pinepartner.pages

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import net.pipe01.pinepartner.components.Header
import net.pipe01.pinepartner.utils.composables.BoxWithFAB

@SuppressLint("InlinedApi")
private enum class PermissionData(val permission: String, val title: String, val description: String) {
    Location(
        Manifest.permission.ACCESS_FINE_LOCATION,
        "Location",
        "Required to scan for nearby Bluetooth devices and for plugins that require location data",
    ),
    BluetoothScan(
        Manifest.permission.BLUETOOTH_SCAN,
        "Scan Bluetooth devices",
        "Required to scan for nearby Bluetooth devices",
    ),
    BluetoothConnect(
        Manifest.permission.BLUETOOTH_CONNECT,
        "Connect to Bluetooth devices",
        "Required to connect to Bluetooth devices",
    ),
    PostNotifications(
        Manifest.permission.POST_NOTIFICATIONS,
        "Post notifications",
        "Required for the persistent background notification and for plugins that post notifications",
    );

    companion object {
        fun of(permission: String): PermissionData? {
            return entries.find { it.permission == permission }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsPage(
    requestedPermissions: List<PermissionState>,
    onRequestMissing: () -> Unit,
) {
    BoxWithFAB(
        fab = {
            ExtendedFloatingActionButton(
                modifier = it,
                onClick = onRequestMissing,
                icon = { Icon(Icons.Filled.Add, "Request") },
                text = { Text(text = "Grant missing permissions") },
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .scrollable(rememberScrollState(), orientation = Orientation.Vertical),
        ) {
            Header(text = "Required permissions")

            Spacer(modifier = Modifier.height(16.dp))

            for (permission in requestedPermissions
                .sortedBy { it.permission }
                .sortedBy { it.status == PermissionStatus.Granted }
            ) {
                val data = PermissionData.of(permission.permission) ?: continue

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (permission.status) {
                            PermissionStatus.Granted -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = data.title,
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Text(text = data.description)
                        }

                        Icon(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .align(Alignment.CenterVertically)
                                .scale(1.5f),
                            imageVector = when (permission.status) {
                                PermissionStatus.Granted -> Icons.Rounded.Check
                                else -> Icons.Rounded.Warning
                            },
                            contentDescription = null,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
@SuppressLint("InlinedApi")
@OptIn(ExperimentalPermissionsApi::class)
fun PermissionsPagePreview() {
    class State(override val permission: String, override val status: PermissionStatus) : PermissionState {
        override fun launchPermissionRequest() {
        }
    }

    PermissionsPage(
        requestedPermissions = listOf(
            State(Manifest.permission.ACCESS_FINE_LOCATION, PermissionStatus.Granted),
            State(Manifest.permission.BLUETOOTH_SCAN, PermissionStatus.Denied(false)),
            State(Manifest.permission.BLUETOOTH_CONNECT, PermissionStatus.Granted),
            State(Manifest.permission.POST_NOTIFICATIONS, PermissionStatus.Denied(false)),
        ),
        onRequestMissing = { },
    )
}
