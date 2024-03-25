package net.pipe01.pinepartner.devices.blefs

import java.time.Instant

data class File(
    val path: String,
    val isDirectory: Boolean,
    val modTime: Instant,
    val size: UInt,
)
