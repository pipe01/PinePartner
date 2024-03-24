package net.pipe01.pinepartner.devices

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DFUProgress(
    val stageName: String,
    val stageProgress: Float,
    val totalProgress: Float,
    val bytesPerSecond: Long? = null,
    val secondsLeft: Int? = null,
    val isDone: Boolean = false,
) : Parcelable {
    companion object {
        fun createInitializing(step: Int, totalSteps: Int = 9)
            = DFUProgress("Initializing transfer", step.toFloat() / totalSteps, (step.toFloat() / totalSteps) * 0.05f)

        fun createFinishing(step: Int, totalSteps: Int = 4)
            = DFUProgress("Finishing transfer", step.toFloat() / totalSteps, 0.95f + (step.toFloat() / totalSteps) * 0.05f)
    }
}
