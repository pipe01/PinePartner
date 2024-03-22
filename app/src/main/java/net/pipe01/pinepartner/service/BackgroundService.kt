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
import android.os.Build
import android.os.Bundle
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
import net.pipe01.pinepartner.scripting.PluginManager
import net.pipe01.pinepartner.scripting.ScriptDependencies
import java.time.LocalDateTime
import java.time.ZoneOffset


enum class Action(action: String) {
    GetWatchState("GET_WATCH_STATE"),
    ConnectWatch("CONNECT_WATCH"),
    DisconnectWatch("DISCONNECT_WATCH"),
    SendTestNotification("SEND_TEST_NOTIFICATION"),
    IsWatchConnected("IS_WATCH_CONNECTED"),

    GetPluginEvents("GET_PLUGIN_EVENTS"),
    DeletePlugin("DELETE_PLUGIN"),
    DisablePlugin("DISABLE_PLUGIN"),
    EnablePlugin("ENABLE_PLUGIN"),
    ReloadPlugins("RELOAD_PLUGINS");

    val fullName = "net.pipe01.pinepartner.$action"
}

const val CODE_OK = 10
const val CODE_ERROR = 20
const val CODE_EXCEPTION = 21

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
            if (context == null || intent == null) {
                return
            }

            try {
                handle(intent)

                Log.d(TAG, "Handled intent ${intent.action}, result code: $resultCode")

                if (resultCode == -1 && isOrderedBroadcast) {
                    resultCode = CODE_OK
                }
            } catch (e: Exception) {
                val result = getExceptionResult(e)

                if (isOrderedBroadcast) {
                    setResult(result.first, "", result.second)
                }
            }
        }

        fun getExceptionResult(ex: java.lang.Exception): Pair<Int, Bundle> {
            return when (ex) {
                is ServiceException -> {
                    Log.w(TAG, "Service error handling intent", ex)
                    Pair(CODE_ERROR, Bundle().apply {
                        putString("serviceError", ex.message)
                    })
                }

                else -> {
                    Log.e(TAG, "Exception handling intent", ex)
                    Pair(CODE_EXCEPTION, Bundle().apply {
                        putSerializable("exception", ex)
                    })
                }
            }
        }

        fun goAsync(fn: suspend (PendingResult) -> Unit) {
            val pendingResult = goAsync()
            pendingResult.resultCode = CODE_OK

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    fn(pendingResult)
                } catch (e: Exception) {
                    val result = getExceptionResult(e)

                    pendingResult.setResult(result.first, "", result.second)
                } finally {
                    pendingResult.finish()
                }
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

                Action.SendTestNotification.fullName -> {
                    Log.i(TAG, "Sending test notification")

                    goAsync {
                        for (device in deviceManager.connectedDevices) {
                            device.sendNotification(0, 1, "Test", "This is a test notification")
                        }
                    }
                }

                Action.ConnectWatch.fullName -> {
                    val address = intent.getStringExtra("address") ?: throw ServiceException("Address not provided")

                    goAsync {
                        CoroutineScope(Dispatchers.IO).launch {
                            deviceManager.connect(address, this)
                        }
                    }
                }

                Action.DisconnectWatch.fullName -> {
                    val address = intent.getStringExtra("address") ?: throw ServiceException("Address not provided")

                    deviceManager.disconnect(address)
                }

                Action.GetWatchState.fullName -> {
                    val address = intent.getStringExtra("address") ?: throw ServiceException("Address not provided")

                    val device = deviceManager.get(address)

                    Log.d(TAG, "Get watch $address state")

                    goAsync {
                        val state = if (device?.isConnected == true)
                            WatchState(true, device.getFirmwareRevision(), device.getBatteryLevel())
                        else
                            WatchState(false, "", 0f)

                        val extras = Bundle()
                        extras.putParcelable("data", state)

                        it.setResult(CODE_OK, null, extras)
                    }
                }

                Action.IsWatchConnected.fullName -> {
                    val address = intent.getStringExtra("address") ?: throw ServiceException("Address not provided")

                    val dev = deviceManager.get(address)
                    val connected = dev?.isConnected ?: false

                    val extras = Bundle()
                    extras.putBoolean("connected", connected)

                    setResult(CODE_OK, null, extras)
                }

                Action.GetPluginEvents.fullName -> {
                    val id = intent.getStringExtra("id") ?: throw ServiceException("Id not provided")
                    val startingFrom = intent.getLongExtra("afterTime", 0)

                    Log.d(TAG, "Get plugin $id events starting from $startingFrom")

                    val events = pluginManager.getEvents(id) ?: emptyList()

                    val extras = Bundle()
                    extras.putParcelableArray("data", events.filter { it.time.toEpochSecond(ZoneOffset.UTC) > startingFrom }.toTypedArray())

                    setResult(CODE_OK, null, extras)
                }

                Action.DeletePlugin.fullName -> {
                    val id = intent.getStringExtra("id") ?: throw ServiceException("Id not provided")

                    goAsync {
                        pluginManager.delete(id)
                    }
                }

                Action.EnablePlugin.fullName -> {
                    val id = intent.getStringExtra("id") ?: throw ServiceException("Id not provided")

                    goAsync {
                        pluginManager.enable(id)
                    }
                }

                Action.DisablePlugin.fullName -> {
                    val id = intent.getStringExtra("id") ?: throw ServiceException("Id not provided")

                    goAsync {
                        pluginManager.disable(id)
                    }
                }

                Action.ReloadPlugins.fullName -> {
                    goAsync {
                        pluginManager.reload()
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

        Action.entries.forEach { action ->
            filter.addAction(action.fullName)
        }

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

    override fun onBind(p0: Intent?): IBinder? {
        return null
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