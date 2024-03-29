package net.pipe01.pinepartner.scripting


import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.scripting.api.Finalizeable
import net.pipe01.pinepartner.scripting.api.HTTPService
import net.pipe01.pinepartner.scripting.api.LocationService
import net.pipe01.pinepartner.scripting.api.MediaService
import net.pipe01.pinepartner.scripting.api.NotificationsService
import net.pipe01.pinepartner.scripting.api.Parameters
import net.pipe01.pinepartner.scripting.api.Require
import net.pipe01.pinepartner.scripting.api.TimerService
import net.pipe01.pinepartner.scripting.api.VolumeService
import net.pipe01.pinepartner.scripting.api.WatchesService
import net.pipe01.pinepartner.scripting.api.adapters.BLECharacteristicAdapter
import net.pipe01.pinepartner.scripting.api.adapters.BLEServiceAdapter
import net.pipe01.pinepartner.scripting.api.adapters.LocationAdapter
import net.pipe01.pinepartner.scripting.api.adapters.NotificationAdapter
import net.pipe01.pinepartner.scripting.api.adapters.PlaybackStateAdapter
import net.pipe01.pinepartner.scripting.api.adapters.VolumeStreamAdapter
import net.pipe01.pinepartner.scripting.api.adapters.WatchAdapter
import net.pipe01.pinepartner.service.DeviceManager
import net.pipe01.pinepartner.service.NotificationsManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.NativeConsole
import org.mozilla.javascript.ScriptableObject
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ScriptDependencies(
    val db: AppDatabase,
    val notifManager: NotificationsManager,
    val deviceManager: DeviceManager,
    val audioManager: AudioManager,
    val mediaSessionManager: MediaSessionManager,
    val fusedLocationProviderClient: FusedLocationProviderClient,
)

class Runner(val plugin: Plugin, deps: ScriptDependencies) {
    private lateinit var script: org.mozilla.javascript.Script
    private lateinit var scope: ScriptableObject

    private val contextFactory = ContextFactory()

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("Script ${plugin.id}")

    private val finalizers = mutableListOf<Finalizeable>()

    private val _hasStarted = AtomicBoolean(false)

    private var eventCounter = AtomicInteger()
    private val _events = mutableListOf<LogEvent>()
    val events get() = _events.toList()

    init {
        contextFactory.addListener(object : ContextFactory.Listener {
            override fun contextCreated(cx: Context) {
                cx.optimizationLevel = -1 // Disable code generation because Android doesn't support it
                cx.languageVersion = Context.VERSION_ES6
            }

            override fun contextReleased(cx: Context) {
            }
        })

        contextFactory.call { ctx ->
            scope = if (plugin.permissions.contains(Permission.USE_JAVA)) {
                ctx.initStandardObjects()
            } else {
                ctx.initSafeStandardObjects()
            }

            ScriptableObject.defineClass(scope, WatchesService::class.java)
            ScriptableObject.defineClass(scope, NotificationsService::class.java)
            ScriptableObject.defineClass(scope, HTTPService::class.java)
            ScriptableObject.defineClass(scope, VolumeService::class.java)
            ScriptableObject.defineClass(scope, MediaService::class.java)
            ScriptableObject.defineClass(scope, LocationService::class.java)
            ScriptableObject.defineClass(scope, TimerService::class.java)

            ScriptableObject.defineClass(scope, WatchAdapter::class.java)
            ScriptableObject.defineClass(scope, NotificationAdapter::class.java)
            ScriptableObject.defineClass(scope, BLEServiceAdapter::class.java)
            ScriptableObject.defineClass(scope, BLECharacteristicAdapter::class.java)
            ScriptableObject.defineClass(scope, VolumeStreamAdapter::class.java)
            ScriptableObject.defineClass(scope, PlaybackStateAdapter::class.java)
            ScriptableObject.defineClass(scope, LocationAdapter::class.java)

            val require = Require(deps, plugin.permissions, contextFactory, dispatcher, ::addEvent)

            ScriptableObject.putProperty(scope, "params", Parameters(scope, plugin.id, plugin.parameters, deps.db))
            ScriptableObject.putProperty(scope, "require", require)
            ScriptableObject.putProperty(scope, "timer", require.createInstance(TimerService::class.java, ctx, scope) { })

            NativeConsole.init(scope, true, ConsolePrinter(::addEvent))

            script = ctx.compileString(plugin.sourceCode, plugin.id, 1, null)
        }
    }

    private fun addEvent(severity: EventSeverity, message: String, stackTrace: List<StackTraceEntry>?) {
        //TODO: Limit number of events stored
        //TODO: Maybe lock this whole thing to ensure the _events list is ordered correctly
        _events.add(
            LogEvent(
                index = eventCounter.getAndIncrement(),
                severity = severity,
                message = message,
                stackTrace = stackTrace,
                time = LocalDateTime.now(),
            )
        )
    }

    fun start() {
        if (!_hasStarted.getAndSet(true)) {
            CoroutineScope(dispatcher).launch {
                contextFactory.call {
                    try {
                        script.exec(it, scope)
                    } catch (e: Exception) {
                        Log.e("Runner", "Script threw an exception", e)

                        addEvent(
                            EventSeverity.FATAL,
                            "Script threw an exception: ${e.message}",
                            e.getLogEventStackTrace(),
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        if (!_hasStarted.get()) {
            throw IllegalStateException("Runner has not started")
        }

        try {
            finalizers.forEach { it.finalize() }
        } finally {
            dispatcher.close()
        }
    }
}
