package net.pipe01.pinepartner.utils

import java.security.MessageDigest

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")

    md.digest(this.toByteArray()).let { bytes ->
        val hex = StringBuilder(bytes.size * 2)
        bytes.forEach {
            hex.append(String.format("%02x", it))
        }
        return hex.toString()
    }
}