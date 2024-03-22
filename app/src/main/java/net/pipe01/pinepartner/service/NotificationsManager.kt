package net.pipe01.pinepartner.service

import net.pipe01.pinepartner.utils.EventEmitter
import java.time.LocalDateTime

data class Notification(val packageName: String, val appLabel: String, val title: String, val text: String, val time: LocalDateTime, val isAllowed: Boolean)

class NotificationsManager {
    val notificationReceived = EventEmitter<Notification>()
}