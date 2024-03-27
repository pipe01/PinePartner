package net.pipe01.pinepartner.service

import android.annotation.SuppressLint
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
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.MainActivity
import net.pipe01.pinepartner.NotificationReceivedAction
import net.pipe01.pinepartner.R
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.devices.WatchState
import net.pipe01.pinepartner.devices.blefs.createFolder
import net.pipe01.pinepartner.devices.blefs.deleteFile
import net.pipe01.pinepartner.devices.blefs.joinPaths
import net.pipe01.pinepartner.devices.blefs.listFiles
import net.pipe01.pinepartner.devices.blefs.writeFile
import net.pipe01.pinepartner.devices.externalResources.uploadExternalResources
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.scripting.PluginManager
import net.pipe01.pinepartner.scripting.ScriptDependencies
import net.pipe01.pinepartner.utils.runJobThrowing
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random


class ServiceException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}

private data class TransferJob(val job: Job, var progress: TransferProgress)

class BackgroundService : Service() {
    private val TAG = "BackgroundService"
    private val NOTIF_CHANNEL_ID = "PinePartner"

    private lateinit var db: AppDatabase;
    private val deviceManager = DeviceManager(this)
    private val notifManager = NotificationsManager()
    private lateinit var pluginManager: PluginManager

    private val transferJobs = mutableMapOf<Int, TransferJob>()

    private var isStarted = false

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

        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        BuiltInPlugins.init(assets)

        pluginManager = PluginManager(
            pluginDao = db.pluginDao(),
            scriptDependencies = ScriptDependencies(
                db,
                notifManager,
                deviceManager,
                audioManager,
                mediaSessionManager,
                fusedLocationProviderClient,
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
        if (!isStarted) {
            isStarted = true
        } else {
            return START_STICKY
        }

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

    class ServiceBinder(val service: BackgroundService) : Binder()

    suspend fun sendTestNotification() = Result.runCatching {
        for (device in deviceManager.connectedDevices) {
            device.sendNotification(0, 1, "Test", "This is a test notification")
        }
    }

    suspend fun connectWatch(address: String) = Result.runCatching {
        deviceManager.connect(address, CoroutineScope(Dispatchers.IO))
    }

    fun disconnectWatch(address: String) = Result.runCatching {
        deviceManager.disconnect(address)
    }

    suspend fun getWatchState(address: String) = Result.runCatching {
        val device = deviceManager.get(address)

        Log.d(TAG, "Get watch $address state")

        if (device?.isConnected == true)
            WatchState(true, device.getFirmwareRevision(), device.getBatteryLevel())
        else
            WatchState(false, "", 0f)
    }

    suspend fun startWatchDFU(address: String, uri: Uri) = Result.runCatching {
        Log.d(TAG, "Flashing watch $address with $uri")

        val device = deviceManager.get(address) ?: throw ServiceException("Device not found")
        val jobId = Random.nextInt()

        val job = CoroutineScope(Dispatchers.IO).launch {
            contentResolver.openInputStream(uri)!!.use { stream ->
                device.flashDFU(stream, this) {
                    transferJobs[jobId]?.progress = TransferProgress(
                        it.totalProgress,
                        it.bytesPerSecond,
                        it.secondsLeft?.let { Duration.ofSeconds(it.toLong()) },
                        it.isDone,
                    )
                }
            }
        }

        transferJobs[jobId] = TransferJob(job, TransferProgress(0f, null, null, false))

        jobId
    }

    fun getTransferProgress(id: Int) = transferJobs[id]?.progress

    fun cancelTransfer(id: Int) = Result.runCatching {
        Log.i(TAG, "Cancelling transfer $id")

        transferJobs.remove(id)?.job?.cancel() ?: Log.w(TAG, "Transfer $id not found")
    }

    fun getPluginEvents(id: String, afterTime: Long) = Result.runCatching {
        val events = pluginManager.getEvents(id) ?: emptyList()

        events.filter { it.time.toEpochSecond(ZoneOffset.UTC) > afterTime }
    }

    suspend fun deletePlugin(id: String) = Result.runCatching {
        pluginManager.delete(id)
    }

    suspend fun enablePlugin(id: String) = Result.runCatching {
        pluginManager.enable(id)
    }

    suspend fun disablePlugin(id: String) = Result.runCatching {
        pluginManager.disable(id)
    }

    suspend fun reloadPlugins() = Result.runCatching {
        pluginManager.reload()
    }

    suspend fun listFiles(address: String, path: String) = Result.runCatching {
        val device = deviceManager.get(address) ?: throw ServiceException("Device not found")

        coroutineScope {
            device.listFiles(path, this)
        }
    }

    suspend fun writeFile(address: String, path: String, data: ByteArray) = Result.runCatching {
        val device = deviceManager.get(address) ?: throw ServiceException("Device not found")

        coroutineScope {
            device.writeFile(path, ByteArrayInputStream(data), data.size, this)
        }
    }

    suspend fun deleteFile(address: String, path: String) = Result.runCatching {
        val device = deviceManager.get(address) ?: throw ServiceException("Device not found")

        coroutineScope {
            device.deleteFile(path, this)
        }
    }

    @SuppressLint("Range")
    suspend fun sendFile(jobId: Int, address: String, path: String, uri: Uri) = Result.runCatching {
        val device = deviceManager.get(address) ?: throw ServiceException("Device not found")

        var size = 0
        var name = ""

        contentResolver.query(uri, null, null, null, null)?.use {
            if (!it.moveToFirst()) {
                throw ServiceException("Failed to get file info")
            }

            name = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            size = it.getInt(it.getColumnIndex(OpenableColumns.SIZE))
        } ?: throw ServiceException("Failed to get file info")

        val fullPath = joinPaths(path, name)

        runJobThrowing(CoroutineScope(Dispatchers.IO), onStart = {
            transferJobs[jobId] = TransferJob(it, TransferProgress(0f, null, null, false))
        }) {
            contentResolver.openInputStream(uri)?.use { stream ->
                device.writeFile(fullPath, stream, size, this) {
                    transferJobs[jobId]?.progress = it
                }
            }
        }
    }

    suspend fun createFolder(address: String, path: String) = Result.runCatching {
        deviceManager.get(address)?.createFolder(path, CoroutineScope(Dispatchers.IO)) ?: throw ServiceException("Device not found")
    }

    suspend fun uploadResources(jobId: Int, address: String, zipUri: Uri) = Result.runCatching {
        val device = deviceManager.get(address) ?: throw ServiceException("Device not found")

        runJobThrowing(CoroutineScope(Dispatchers.IO), onStart = {
            transferJobs[jobId] = TransferJob(it, TransferProgress(0f, null, null, false))
        }) {
            contentResolver.openInputStream(zipUri)?.use { stream ->
                device.uploadExternalResources(stream, this) {
                    transferJobs[jobId]?.progress = it
                }
            }
        }
    }
}