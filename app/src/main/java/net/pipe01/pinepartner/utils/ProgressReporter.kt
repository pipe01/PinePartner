package net.pipe01.pinepartner.utils

import net.pipe01.pinepartner.service.TransferProgress
import java.io.Closeable
import java.time.Duration
import java.util.Timer
import java.util.TimerTask

class ProgressReporter(
    private val totalSize: Long,
    private val onProgress: (TransferProgress) -> Unit,
    private val reportInterval: Duration = Duration.ofSeconds(1),
): Closeable {
    private val timer = Timer()
    private var sent: Long = 0

    init {
        var lastSent: Long = 0
        var lastSentTime = System.currentTimeMillis()

        timer.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                val now = System.currentTimeMillis()
                val elapsed = now - lastSentTime
                lastSentTime = now

                val bytesPerMS = (sent - lastSent) / elapsed

                onProgress(TransferProgress(
                    totalProgress = sent.toFloat() / totalSize,
                    bytesPerSecond = bytesPerMS * 1000,
                    timeLeft = if (bytesPerMS > 0) Duration.ofMillis((totalSize - sent) / bytesPerMS) else null,
                    isDone = false,
                ))
            }
        }, 0, reportInterval.toMillis())
    }

    override fun close() {
        onProgress(TransferProgress(
            totalProgress = 1.0f,
            bytesPerSecond = null,
            timeLeft = null,
            isDone = true,
        ))
        timer.cancel()
    }

    fun sentBytes(count: Long) {
        sent += count
    }
}