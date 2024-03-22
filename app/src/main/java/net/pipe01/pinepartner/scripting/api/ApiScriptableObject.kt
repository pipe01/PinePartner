package net.pipe01.pinepartner.scripting.api

import androidx.annotation.CallSuper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.scripting.EventSeverity
import net.pipe01.pinepartner.scripting.OnLogEvent
import net.pipe01.pinepartner.scripting.getLogEventStackTrace
import net.pipe01.pinepartner.utils.Event
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.atomic.AtomicBoolean

interface Finalizeable {
    fun finalize()
}

abstract class ApiScriptableObject(private val className: String) : ScriptableObject(), Finalizeable {
    private lateinit var contextFactory: ContextFactory
    private lateinit var dispatcher: CoroutineDispatcher
    private lateinit var onEvent: OnLogEvent

    private var isFinalized = AtomicBoolean(false)

    private val listeners = mutableMapOf<Pair<String, Function>, () -> Unit>()
    private val children = mutableListOf<Finalizeable>()

    override fun getClassName() = className

    fun initSuper(contextFactory: ContextFactory, dispatcher: CoroutineDispatcher, onEvent: OnLogEvent) {
        this.contextFactory = contextFactory
        this.dispatcher = dispatcher
        this.onEvent = onEvent
    }

    @CallSuper
    override fun finalize() {
        if (isFinalized.getAndSet(true)) {
            throw IllegalStateException("Already finalized")
        }

        listeners.values.forEach { it() }
        children.forEach { it.finalize() }
    }

    private fun checkFinalized() {
        if (isFinalized.get()) {
            throw IllegalStateException("Object is finalized")
        }
    }

    protected fun <T : Any> addListener(event: Event<T>, eventName: String, cb: Function, adapter: (T) -> Any = { it }) {
        checkFinalized()

        listeners[Pair(eventName, cb)] = event.addListener {
            enterAndCall(cb) { arrayOf(adapter(it)) }
        }
    }

    protected fun removeListener(eventName: String, cb: Function) {
        checkFinalized()

        listeners.remove(Pair(eventName, cb))?.invoke()
    }

    protected fun enterAndCall(fn: Function, argsFn: (() -> Array<Any>)? = null) {
        checkFinalized()

        launch {
            contextFactory.call { context ->
                val args = argsFn?.invoke() ?: emptyArray()

                fn.call(context, getTopLevelScope(this@ApiScriptableObject), null, args)
            }
        }
    }

    protected fun launch(fn: suspend CoroutineScope.() -> Unit) {
        checkFinalized()

        CoroutineScope(dispatcher).launch {
            try {
                fn()
            } catch (e: Exception) {
                e.printStackTrace()

                onEvent(
                    EventSeverity.ERROR,
                    "Unhandled exception: ${e.message}",
                    e.getLogEventStackTrace(),
                )
            }
        }
    }

    fun <T : ApiScriptableObject> newObject(name: String, init: (T.() -> Unit)? = null): T {
        checkFinalized()

        val scope = getTopLevelScope(this)
        val obj = Context.getCurrentContext().newObject(scope, name) as? T ?: throw IllegalArgumentException("Invalid class")

        obj.initSuper(contextFactory, dispatcher, onEvent)

        if (init != null)
            init(obj)

        children.add(obj)

        return obj
    }
}