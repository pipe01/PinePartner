package net.pipe01.pinepartner.scripting

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

enum class EventSeverity {
    INFO,
    WARN,
    ERROR,
    FATAL,
}

@Parcelize
data class LogEvent(
    val index: Int,
    val severity: EventSeverity,
    val message: String,
    val stackTrace: List<String>?,
    val time: LocalDateTime,
) : Parcelable
