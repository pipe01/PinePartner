package net.pipe01.pinepartner.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Timer
import kotlin.concurrent.schedule

class ServiceHandle(private val context: Context) {
    private val TAG = "ServiceHandle"

    private var restartsRemaining = 5

    var service by mutableStateOf<BackgroundService?>(null)
        private set
    var hasCrashed by mutableStateOf(false)
        private set

    var pluginsDisabled by mutableStateOf(false)
        private set

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            hasCrashed = false
            service = (binder as BackgroundService.ServiceBinder).service

            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hasCrashed = true
            service = null

            Log.d(TAG, "Service disconnected")

            if (restartsRemaining-- > 0) {
                Timer().schedule(2000) {
                    start(disablePlugins = restartsRemaining < 3)
                }
            }
        }
    }

    fun start(disablePlugins: Boolean = false) {
        if (service != null) {
            Log.w(TAG, "Tried to start service when it was already running")
            return
        }

        pluginsDisabled = disablePlugins

        val intent = Intent(context, BackgroundService::class.java)
        intent.putExtra("disablePlugins", disablePlugins)
        context.startForegroundService(intent)

        context.bindService(intent, conn, Context.BIND_ABOVE_CLIENT or Context.BIND_IMPORTANT)
    }

    fun unbind() {
        context.unbindService(conn)
    }
}