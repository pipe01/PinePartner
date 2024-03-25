package net.pipe01.pinepartner.devices.blefs

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Parcelize
data class File(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val modTime: Instant,
    val size: UInt,
) : Parcelable
