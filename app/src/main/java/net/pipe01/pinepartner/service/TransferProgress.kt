package net.pipe01.pinepartner.service

import android.os.Parcelable
import androidx.annotation.FloatRange
import kotlinx.parcelize.Parcelize
import java.time.Duration

@Parcelize
data class TransferProgress(
    @FloatRange(0.0, 1.0) val totalProgress: Float,
    val bytesPerSecond: Long?,
    val timeLeft: Duration?,
    val isDone: Boolean,
    val exception: Exception? = null,
) : Parcelable {
    companion object {
        fun error(e: Exception) = TransferProgress(
            totalProgress = 0f,
            bytesPerSecond = null,
            timeLeft = null,
            isDone = true,
            exception = e,
        )
    }
}
