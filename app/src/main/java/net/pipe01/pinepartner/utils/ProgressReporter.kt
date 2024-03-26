package net.pipe01.pinepartner.utils

import android.util.Log
import net.pipe01.pinepartner.service.TransferProgress
import java.io.Closeable
import java.time.Duration
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicLong

class ProgressReporter(
    var totalSize: Long,
    private val onProgress: (TransferProgress) -> Unit,
    reportInterval: Duration = Duration.ofMillis(2000),
): Closeable {
    private val timer = Timer()
    private val sent = AtomicLong()
    private val remaining = AtomicLong(totalSize)

    init {
        var lastSentTime = System.currentTimeMillis()

        timer.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                val sentBytes = sent.getAndSet(0)

                val now = System.currentTimeMillis()
                val elapsed = now - lastSentTime
                val bytesPerSecond = if (elapsed == 0L) 0 else 1000 * sentBytes / elapsed

                Log.d("ProgressReporter", "Sent $sentBytes bytes in $elapsed ms (${bytesPerSecond}B/s)")
                lastSentTime = now

                val remainingBytes = remaining.get()
                val totalDone = totalSize - remainingBytes

                onProgress(TransferProgress(
                    totalProgress = if (totalSize == 0L) 0f else totalDone.toFloat() / totalSize,
                    bytesPerSecond = bytesPerSecond,
                    timeLeft = if (bytesPerSecond > 0) Duration.ofSeconds((remainingBytes.toFloat() / bytesPerSecond).toLong()) else null,
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

    fun addBytes(count: Long) {
        sent.addAndGet(count)
        remaining.addAndGet(-count)
    }
}