package net.pipe01.pinepartner.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.MainActivity
import net.pipe01.pinepartner.NotificationReceivedAction
import net.pipe01.pinepartner.R
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.devices.WatchState
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.scripting.LogEvent
import net.pipe01.pinepartner.scripting.PluginManager
import net.pipe01.pinepartner.scripting.ScriptDependencies
import java.time.LocalDateTime
import java.time.ZoneOffset


class ServiceException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}

class BackgroundService : Service() {
    private val TAG = "BackgroundService"
    private val NOTIF_CHANNEL_ID = "PinePartner"

    private lateinit var db: AppDatabase;

    private val deviceManager = DeviceManager(this)
    private val notifManager = NotificationsManager()
    private lateinit var pluginManager: PluginManager

    val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received intent ${intent?.action}")

            if (context == null || intent == null) {
                return
            }

            try {
                handle(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling intent", e)
            }
        }

        private fun handle(intent: Intent) {
            when (intent.action) {
                NotificationReceivedAction -> {
                    val packageName = intent.getStringExtra("packageName")
                    val appLabel = intent.getStringExtra("appLabel")
                    val title = intent.getStringExtra("title")
                    val text = intent.getStringExtra("text")

                    if (packageName == null || appLabel == null || title == null || text == null) {
                        Log.d(TAG, "Notification missing data, ignoring")
                        return
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        val isAllowed = db.allowedNotifAppDao().isAllowed(packageName)

                        notifManager.notificationReceived.emit(Notification(packageName, appLabel, title, text, LocalDateTime.now(), isAllowed))
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        db = AppDatabase.create(applicationContext)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        BuiltInPlugins.init(assets)

        pluginManager = PluginManager(
            pluginDao = db.pluginDao(),
            scriptDependencies = ScriptDependencies(
                db,
                notifManager,
                deviceManager,
                audioManager,
                mediaSessionManager
            ),
        )

        createNotificationChannel()

        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("PinePartner")
            .setContentText("Service running")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
            .also {
                startForeground(1, it)
            }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")

        val filter = IntentFilter()
        filter.addAction(NotificationReceivedAction)
        registerReceiver(notifReceiver, filter, RECEIVER_NOT_EXPORTED)

        toggleNotificationListenerService()

        CoroutineScope(Dispatchers.Main).launch {
            pluginManager.reload()

            db.watchDao().getAll().forEach {
                if (it.autoConnect) {
                    deviceManager.connect(it.address, this)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(notifReceiver)

        Log.i(TAG, "Service destroyed")
    }

    class ServiceBinder(val service: BackgroundService) : Binder()

    suspend fun sendTestNotification() {
        for (device in deviceManager.connectedDevices) {
            device.sendNotification(0, 1, "Test", "This is a test notification")
        }
    }

    suspend fun connectWatch(address: String) {
        deviceManager.connect(address, CoroutineScope(Dispatchers.IO))
    }

    fun disconnectWatch(address: String) {
        deviceManager.disconnect(address)
    }

    suspend fun getWatchState(address: String): WatchState {
        val device = deviceManager.get(address)

        Log.d(TAG, "Get watch $address state")

        return if (device?.isConnected == true)
            WatchState(true, device.getFirmwareRevision(), device.getBatteryLevel())
        else
            WatchState(false, "", 0f)
    }

    fun isWatchConnected(address: String): Boolean {
        val dev = deviceManager.get(address)
        return dev?.isConnected ?: false
    }

    fun getPluginEvents(id: String, afterTime: Long): Array<LogEvent> {
        val events = pluginManager.getEvents(id) ?: emptyList()

        return events.filter { it.time.toEpochSecond(ZoneOffset.UTC) > afterTime }.toTypedArray()
    }

    suspend fun deletePlugin(id: String) {
        pluginManager.delete(id)
    }

    suspend fun enablePlugin(id: String) {
        pluginManager.enable(id)
    }

    suspend fun disablePlugin(id: String) {
        pluginManager.disable(id)
    }

    suspend fun reloadPlugins() {
        pluginManager.reload()
    }

    override fun onBind(p0: Intent?): IBinder {
        return ServiceBinder(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIF_CHANNEL_ID, "Background Service Notification", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(false)
            enableLights(false)
            importance = NotificationManager.IMPORTANCE_LOW
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun toggleNotificationListenerService() {
        Log.i(TAG, "Toggling notification listener service")

        val thisComponent = ComponentName(
            this,
            BackgroundService::class.java
        )
        val pm = packageManager
        pm.setComponentEnabledSetting(
            thisComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            thisComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}