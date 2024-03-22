package net.pipe01.pinepartner.scripting.api

import kotlinx.coroutines.CoroutineDispatcher
import net.pipe01.pinepartner.scripting.OnLogEvent
import net.pipe01.pinepartner.scripting.Permission
import net.pipe01.pinepartner.scripting.ScriptDependencies
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable

class Require(
    private val deps: ScriptDependencies,
    private val permissions: Set<Permission>,
    private val contextFactory: ContextFactory,
    private val dispatcher: CoroutineDispatcher,
    private val onEvent: OnLogEvent,
) : BaseFunction(), Finalizeable {
    private val createdInstances = mutableListOf<Finalizeable>()

    override fun finalize() {
        createdInstances.forEach { it.finalize() }
    }

    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
        if (args == null || args.size != 1) {
            throw ScriptRuntime.throwError(cx, scope, "require() needs one argument")
        }

        val className = Context.jsToJava(args[0], String::class.java) as String

        return when (className) {
            "watches" -> {
                createInstance<Watches>(cx, scope) {
                    init(deps.db, deps.deviceManager)
                }
            }

            "notifications" -> {
                if (!permissions.contains(Permission.RECEIVE_NOTIFICATIONS)) {
                    throw ScriptRuntime.throwError(cx, scope, "Permission RECEIVE_NOTIFICATIONS is needed")
                }

                createInstance<Notifications>(cx, scope) {
                    init(deps.notifManager)
                }
            }

            "http" -> {
                if (!permissions.contains(Permission.HTTP)) {
                    throw ScriptRuntime.throwError(cx, scope, "Permission HTTP is needed")
                }

                createInstance<HTTP>(cx, scope) { }
            }

            else -> throw ScriptRuntime.throwError(cx, scope, "Unknown module $className")
        }
    }

    private inline fun <reified T : ApiScriptableObject> createInstance(cx: Context, scope: Scriptable, init: T.() -> Unit): T {
        return (cx.newObject(scope, T::class.java.simpleName) as T).also {
            it.initSuper(contextFactory, dispatcher, onEvent)
            init(it)
            createdInstances.add(it)
        }
    }
}