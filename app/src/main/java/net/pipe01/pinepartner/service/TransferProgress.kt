package net.pipe01.pinepartner.service

import android.os.Parcelable
import androidx.annotation.FloatRange
import kotlinx.parcelize.Parcelize
import java.time.Duration

@Parcelize
data class TransferProgress(
    val jobId: Int,
    val stage: String,
    @FloatRange(0.0, 1.0) val totalProgress: Float,
    val bytesPerSecond: Long?,
    val timeLeft: Duration?,
    val isDone: Boolean,
) : Parcelable
