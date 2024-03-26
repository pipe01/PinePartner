package net.pipe01.pinepartner.utils

fun ByteArray.joinToHexString() = joinToString(":") { "%02X".format(it) }