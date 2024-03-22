package net.pipe01.pinepartner.scripting


import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.scripting.api.Finalizeable
import net.pipe01.pinepartner.scripting.api.HTTP
import net.pipe01.pinepartner.scripting.api.Notifications
import net.pipe01.pinepartner.scripting.api.Require
import net.pipe01.pinepartner.scripting.api.Watches
import net.pipe01.pinepartner.scripting.api.adapters.BLEServiceAdapter
import net.pipe01.pinepartner.scripting.api.adapters.NotificationAdapter
import net.pipe01.pinepartner.scripting.api.adapters.WatchAdapter
import net.pipe01.pinepartner.service.DeviceManager
import net.pipe01.pinepartner.service.NotificationsManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ErrorReporter
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.NativeConsole
import org.mozilla.javascript.ScriptableObject
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ScriptDependencies(
    val db: AppDatabase,
    val notifManager: NotificationsManager,
    val deviceManager: DeviceManager,
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

                cx.setErrorReporter(object : ErrorReporter {
                    override fun warning(p0: String?, p1: String?, p2: Int, p3: String?, p4: Int) {
                        TODO("Not yet implemented")
                    }

                    override fun error(p0: String?, p1: String?, p2: Int, p3: String?, p4: Int) {
                        TODO("Not yet implemented")
                    }

                    override fun runtimeError(p0: String?, p1: String?, p2: Int, p3: String?, p4: Int): EvaluatorException {
                        TODO("Not yet implemented")
                    }
                })
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

            ScriptableObject.defineClass(scope, Watches::class.java)
            ScriptableObject.defineClass(scope, Notifications::class.java)
            ScriptableObject.defineClass(scope, HTTP::class.java)

            ScriptableObject.defineClass(scope, WatchAdapter::class.java)
            ScriptableObject.defineClass(scope, NotificationAdapter::class.java)
            ScriptableObject.defineClass(scope, BLEServiceAdapter::class.java)

            ScriptableObject.putProperty(scope, "require", Require(deps, plugin.permissions, contextFactory, dispatcher, ::addEvent))

            NativeConsole.init(scope, true, ConsolePrinter(::addEvent))

            script = ctx.compileString(plugin.sourceCode, plugin.id, 0, null)
        }
    }

    private fun addEvent(severity: EventSeverity, message: String) {
        //TODO: Limit number of events stored
        //TODO: Maybe lock this whole thing to ensure the _events list is ordered correctly
        _events.add(
            LogEvent(
                index = eventCounter.getAndIncrement(),
                severity = severity,
                message = message,
                stackTrace = null,
                time = LocalDateTime.now(),
            )
        )
    }

    fun start() {
        if (!_hasStarted.getAndSet(true)) {
            CoroutineScope(dispatcher).run {
                contextFactory.call {
                    try {
                        script.exec(it, scope)
                    } catch (e: Exception) {
                        Log.e("Runner", "Script threw an exception", e)

                        addEvent(EventSeverity.FATAL, "Script threw an exception: ${e.message}")
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
