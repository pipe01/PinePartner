package net.pipe01.pinepartner.utils

import java.io.InputStream
import java.util.zip.ZipInputStream

fun InputStream.unzip(): Map<String, ByteArray> = ZipInputStream(this).use { zip ->
    val files = mutableMapOf<String, ByteArray>()

    while (true) {
        val entry = zip.nextEntry ?: break
        if (entry.isDirectory) continue

        files[entry.name] = zip.readBytes()
    }

    return files
}