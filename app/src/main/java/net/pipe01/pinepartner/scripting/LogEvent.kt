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

typealias OnLogEvent = (severity: EventSeverity, message: String, stackTrace: List<StackTraceEntry>?) -> Unit

@Parcelize
data class StackTraceEntry(
    val fileName: String,
    val lineNumber: Int,
    val functionName: String,
) : Parcelable

@Parcelize
data class LogEvent(
    val index: Int,
    val severity: EventSeverity,
    val message: String,
    val stackTrace: List<StackTraceEntry>?,
    val time: LocalDateTime,
) : Parcelable

fun Exception.getLogEventStackTrace(): List<StackTraceEntry> {
    return stackTrace.map {
        StackTraceEntry(
            fileName = it.fileName ?: "Unknown",
            lineNumber = it.lineNumber,
            functionName = it.methodName,
        )
    }
}