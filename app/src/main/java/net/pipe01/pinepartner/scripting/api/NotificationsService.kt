package net.pipe01.pinepartner.scripting.api

import net.pipe01.pinepartner.scripting.api.adapters.NotificationAdapter
import net.pipe01.pinepartner.service.NotificationsManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction

class NotificationsService : ApiScriptableObject(NotificationsService::class) {
    private lateinit var notifManager: NotificationsManager

    fun init(notifManager: NotificationsManager) {
        this.notifManager = notifManager
    }

    @JSFunction
    fun addEventListener(event: String, cb: Function) {
        when (event) {
            "received" -> addListener(notifManager.notificationReceived, event, cb) {
                newObject(NotificationAdapter::class) {
                    init(it)
                }
            }

            else -> Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid event"))
        }
    }

    @JSFunction
    fun removeEventListener(event: String, cb: Function) {
        when (event) {
            "received" -> removeListener(event, cb)

            else -> Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid event"))
        }
    }
}