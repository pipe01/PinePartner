package net.pipe01.pinepartner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.ui.theme.PinePartnerTheme

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private val ENABLE_BLUETOOTH_REQUEST_CODE = 1
    private val RUNTIME_PERMISSION_REQUEST_CODE = 2

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.create(applicationContext)

        BuiltInPlugins.init(assets)

        setContent {
            val navController = rememberNavController()

            PinePartnerTheme {
                Scaffold(
                    bottomBar = {
                        BottomBar(navController = navController)
                    }
                ) { padding ->
                    NavFrame(
                        modifier = Modifier.padding(padding),
                        navController = navController,
                        db = db,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        startBleScan()

        val intent = Intent(this, BackgroundService::class.java)
        startForegroundService(intent)
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            if (!hasRequiredRuntimePermissions()) {
                requestRelevantRuntimePermissions()
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
            }
        }
    }

    private fun startBleScan() {
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        } else if (!hasRequiredRuntimePermissions()) {
            requestRelevantRuntimePermissions()
        }
    }

    @SuppressLint("InlinedApi")
    private fun Activity.requestRelevantRuntimePermissions() {
        if (!hasRequiredBluetoothPermissions()) {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                    requestLocationPermission()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    requestBluetoothPermissions()
                }
            }
        }

        if (!hasRequiredNotificationPermissions()) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                RUNTIME_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestLocationPermission() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Location permission required")
                .setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                    "location access in order to scan for BLE devices.")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                }
                .show()
        }
    }

    private fun requestBluetoothPermissions() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth permissions required")
                .setMessage("Starting from Android 12, the system requires apps to be granted " +
                    "Bluetooth access in order to scan for and connect to BLE devices.")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                }
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
                val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                when {
                    containsPermanentDenial -> {
                        // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                        // Note: The user will need to navigate to App Settings and manually grant
                        // permissions that were permanently denied
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && hasRequiredRuntimePermissions() -> {
                        startBleScan()
                    }
                    else -> {
                        // Unexpected scenario encountered when handling permissions
                        recreate()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    startBleScan()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }
}

fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
}

fun Context.hasRequiredBluetoothPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

fun Context.hasRequiredNotificationPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }
}

fun Context.hasRequiredRuntimePermissions(): Boolean {
    return hasRequiredBluetoothPermissions() && hasRequiredNotificationPermissions()
}
