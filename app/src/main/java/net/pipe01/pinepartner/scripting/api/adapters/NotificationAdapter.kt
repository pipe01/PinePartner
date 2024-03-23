package net.pipe01.pinepartner.scripting.api.adapters

import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import net.pipe01.pinepartner.service.Notification
import org.mozilla.javascript.annotations.JSGetter

class NotificationAdapter : ApiScriptableObject(NotificationAdapter::class) {
    private lateinit var notification: Notification

    internal fun init(notification: Notification) {
        this.notification = notification
    }

    @JSGetter
    fun getPackageName() = notification.packageName

    @JSGetter
    fun getAppLabel() = notification.appLabel

    @JSGetter
    fun getTitle() = notification.title

    @JSGetter
    fun getText() = notification.text

    @JSGetter
    fun getTime() = notification.time //TODO: Convert to JS Date

    @JSGetter
    fun getIsAllowed() = notification.isAllowed
}