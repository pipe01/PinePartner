package net.pipe01.pinepartner

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import net.pipe01.pinepartner.utils.AppInfoCache


const val NotificationReceivedAction = "net.pipe01.pinepartner.NOTIFICATION_RECEIVED"

class MyNotificationListener : NotificationListenerService() {
    private lateinit var appInfoCache: AppInfoCache

    override fun onListenerConnected() {
        Log.d("NotificationListener", "Listener connected")

        appInfoCache = AppInfoCache(packageManager)

//        activeNotifications.forEach {
//            if (shouldSendNotification(it))
//                Log.d("NotificationListener", "Active notification: ${it.notification} ${it.notification.extras}")
//        }
    }

    override fun onListenerDisconnected() {
        Log.d("NotificationListener", "Listener disconnected")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        Log.d("NotificationListener", "Notification removed: ${sbn?.notification} ${sbn?.notification?.extras}")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d("NotificationListener", "Notification posted: ${sbn.packageName} ${sbn.notification} ${sbn.notification.extras}")

        if (!shouldSendNotification(sbn))
            return

        val i = Intent(NotificationReceivedAction)
        i.setPackage("net.pipe01.pinepartner")
        i.putExtras(getNotificationBundle(sbn))
        sendBroadcast(i)
    }

    private fun shouldSendNotification(sbn: StatusBarNotification): Boolean {
        return !sbn.isOngoing && (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0)
    }

    private fun getNotificationBundle(sbn: StatusBarNotification): Bundle {
        val appInfo = appInfoCache.getAppInfo(sbn.packageName)

        val bundle = Bundle()
        bundle.putString("packageName", sbn.packageName)
        bundle.putString("appLabel", appInfo?.label ?: "")
        bundle.putString("title", sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        bundle.putString("text", sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        return bundle
    }
}