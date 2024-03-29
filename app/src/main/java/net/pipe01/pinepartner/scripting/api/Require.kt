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

    private fun checkPermission(cx: Context, permission: Permission) {
        if (!permissions.contains(permission)) {
            throw ScriptRuntime.throwError(cx, this, "Permission $permission is needed")
        }
    }

    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
        if (args == null || args.size != 1) {
            throw ScriptRuntime.throwError(cx, scope, "require() needs one argument")
        }

        val className = Context.jsToJava(args[0], String::class.java) as String

        return when (className) {
            "watches" -> {
                createInstance<WatchesService>(cx, scope) {
                    init(deps.db, deps.deviceManager)
                }
            }

            "notifications" -> {
                checkPermission(cx, Permission.RECEIVE_NOTIFICATIONS)

                createInstance<NotificationsService>(cx, scope) {
                    init(deps.notifManager)
                }
            }

            "http" -> {
                checkPermission(cx, Permission.HTTP)

                createInstance<HTTPService>(cx, scope) { }
            }

            "volume" -> {
                checkPermission(cx, Permission.VOLUME_CONTROL)

                createInstance<VolumeService>(cx, scope) {
                    init(deps.audioManager)
                }
            }

            "media" -> {
                checkPermission(cx, Permission.MEDIA_CONTROL)

                createInstance<MediaService>(cx, scope) {
                    init(deps.mediaSessionManager)
                }
            }

            "location" -> {
                checkPermission(cx, Permission.LOCATION)

                createInstance<LocationService>(cx, scope) {
                    init(deps.fusedLocationProviderClient)
                }
            }

            else -> throw ScriptRuntime.throwError(cx, scope, "Unknown module $className")
        }
    }

    fun <T : ApiScriptableObject> createInstance(clazz: Class<T>, cx: Context, scope: Scriptable, init: T.() -> Unit): T {
        return (cx.newObject(scope, clazz.simpleName) as T).also {
            it.initSuper(contextFactory, dispatcher, onEvent)
            init(it)
            createdInstances.add(it)
        }
    }

    private inline fun <reified T : ApiScriptableObject> createInstance(cx: Context, scope: Scriptable, noinline init: T.() -> Unit): T {
        return createInstance(T::class.java, cx, scope, init)
    }
}