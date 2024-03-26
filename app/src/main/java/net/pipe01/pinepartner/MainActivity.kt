package net.pipe01.pinepartner

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.ui.theme.PinePartnerTheme

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.create(applicationContext)

        BuiltInPlugins.init(assets)

        val intent = Intent(this, BackgroundService::class.java)

        setContent {
            val navController = rememberNavController()

            var service by remember { mutableStateOf<BackgroundService?>(null) }
            var showBottomBar by remember { mutableStateOf(true) }

            DisposableEffect(Unit) {
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        service = (binder as BackgroundService.ServiceBinder).service
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.d("MainActivity", "Service disconnected")
                    }
                }

                bindService(intent, conn, 0)

                onDispose {
                    unbindService(conn)
                }
            }

            PinePartnerTheme {
                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomBar(navController = navController)
                        }
                    }
                ) { padding ->
                    PermissionsFrame {
                        if (service != null) {
                            NavFrame(
                                modifier = Modifier.padding(padding),
                                navController = navController,
                                backgroundService = service!!,
                                onShowBottomBar = { showBottomBar = it },
                                db = db,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val intent = Intent(this, BackgroundService::class.java)
        startForegroundService(intent)
    }
}
