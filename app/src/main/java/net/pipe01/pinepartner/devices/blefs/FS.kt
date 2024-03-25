package net.pipe01.pinepartner.devices.blefs

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.devices.InfiniTime
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

private const val TAG = "BLEFS"

//TODO: Make this per-device
private val mutex = Mutex()

fun joinPaths(path1: String, path2: String): String {
    return cleanPath(
        if (path1 == "") {
            path2
        } else {
            "$path1/$path2"
        }
    )
}

fun cleanPath(path: String): String {
    val parts = ArrayDeque(path.split("/"))

    while (!parts.isEmpty()) {
        when (parts.last()) {
            "", "." -> parts.removeLast()
            ".." -> {
                parts.removeLast()
                if (!parts.isEmpty()) {
                    parts.removeLast()
                }
            }

            else -> break
        }
    }

    return parts.joinToString("/")
}

@SuppressLint("MissingPermission")
private suspend fun Device.doRequest(
    coroutineScope: CoroutineScope,
    onBuildRequest: suspend () -> ByteBuffer,
    onReceiveResponse: suspend (ByteBuffer, ClientBleGattCharacteristic) -> Boolean,
) {
    val fileService = InfiniTime.FileSystemService.RAW_TRANSFER.bind(services)

    val done = CompletableDeferred<Unit>()
    val start = CompletableDeferred<Unit>()

    mutex.withLock {
        val job = coroutineScope.launch {
            fileService
                .getNotifications()
                .onStart { start.complete(Unit) }
                .collect {
                    val buf = ByteBuffer.wrap(it.value).order(ByteOrder.LITTLE_ENDIAN)

                    if (onReceiveResponse(buf, fileService)) {
                        done.complete(Unit)
                    }
                }
        }

        start.await()

        delay(200) // Make sure the notifications are coming through

        val request = onBuildRequest()
        fileService.write(DataByteArray(request.array()))

        done.await()
        job.cancel()
    }
}

@SuppressLint("MissingPermission")
suspend fun Device.readFile(path: String, coroutineScope: CoroutineScope): ByteArray {
    var file: ByteArray? = null
    val wantChunkSize = mtu - 12

    doRequest(
        coroutineScope = coroutineScope,
        onBuildRequest = {
            val pathBytes = path.toByteArray()

            ByteBuffer
                .allocate(12 + pathBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x10)
                .put(0)
                .putShort(pathBytes.size.toShort())
                .putInt(0)
                .putInt(wantChunkSize)
                .put(pathBytes)
        },
        onReceiveResponse = { resp, fs ->
            if (resp.get() != 0x11.toByte()) {
                Log.e(TAG, "Invalid file response")
                return@doRequest true
            }

            val status = resp.get()
            resp.getShort() // Padding
            val offset = resp.getInt()
            val totalSize = resp.getInt()
            val chunkSize = resp.getInt()

            if (file == null) {
                file = ByteArray(totalSize)
            }

            resp.get(file!!, offset, chunkSize)

            Log.d(TAG, "Read response $offset/$totalSize bytes")

            val bytesRemaining = totalSize - offset - chunkSize

            if (bytesRemaining == 0) {
                true
            } else {
                val continueBuf = ByteBuffer
                    .allocate(12)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put(0x12)
                    .put(0x01)
                    .putShort(0) // Padding
                    .putInt(offset + chunkSize)
                    .putInt(wantChunkSize)

                fs.write(DataByteArray(continueBuf.array()))

                false
            }
        }
    )

    Log.d(TAG, "File received, ${file?.size} bytes")
    return file ?: throw IllegalStateException("No file received")
}

@SuppressLint("MissingPermission")
suspend fun Device.writeFile(path: String, inputStream: InputStream, totalSize: Int, coroutineScope: CoroutineScope) {
    val buffer = ByteArray(mtu - 20)

    var sent = 0

    doRequest(
        coroutineScope = coroutineScope,
        onBuildRequest = {
            val pathBytes = path.toByteArray()

            ByteBuffer
                .allocate(20 + pathBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x20)
                .put(0)
                .putShort(pathBytes.size.toShort())
                .putInt(0) // Start offset
                .putLong(0) // Modification time
                .putInt(totalSize)
                .put(pathBytes)
        },
        onReceiveResponse = { resp, fs ->
            if (resp.get() != 0x21.toByte()) {
                Log.e(TAG, "Invalid file response")
                return@doRequest true
            }

            val status = resp.get()
            val bytesLeft = totalSize - sent

            Log.d(TAG, "Write status $status, $sent/$totalSize bytes")

            if (bytesLeft == 0) {
                true
            } else {
                val chunkSize = bytesLeft.coerceAtMost(buffer.size)

                inputStream.read(buffer)

                val continueBuf = ByteBuffer.allocate(12 + chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                continueBuf.put(0x22)
                continueBuf.put(0x01)
                continueBuf.putShort(0) // Padding
                continueBuf.putInt(sent)
                continueBuf.putInt(chunkSize)
                continueBuf.put(buffer)

                Log.d(TAG, "Writing $chunkSize bytes")

                fs.write(DataByteArray(continueBuf.array()))

                sent += chunkSize

                sent == totalSize
            }
        }
    )
}

@SuppressLint("MissingPermission")
suspend fun Device.deleteFile(path: String, coroutineScope: CoroutineScope) {
    doRequest(
        coroutineScope = coroutineScope,
        onBuildRequest = {
            val pathBytes = path.toByteArray()

            ByteBuffer
                .allocate(4 + pathBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x30)
                .put(0)
                .putShort(pathBytes.size.toShort())
                .put(pathBytes)
        },
        onReceiveResponse = { resp, _ ->
            if (resp.get() != 0x31.toByte()) {
                Log.e(TAG, "Invalid delete response")
                return@doRequest true
            }

            val status = resp.get()

            Log.d(TAG, "Delete status $status")

            true
        }
    )
}

@SuppressLint("MissingPermission")
suspend fun Device.listFiles(path: String, coroutineScope: CoroutineScope): List<File> {
    val files = mutableListOf<File>()

    doRequest(
        coroutineScope = coroutineScope,
        onBuildRequest = {
            val pathBytes = path.toByteArray()

            ByteBuffer
                .allocate(4 + pathBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x50)
                .put(0)
                .putShort(pathBytes.size.toShort())
                .put(pathBytes)
        },
        onReceiveResponse = { resp, _ ->
            Log.d(TAG, "File response ${resp.capacity()} bytes: ${resp.array().joinToString(":") { "%02x".format(it) }}")

            if (resp.get() != 0x51.toByte()) {
                Log.e(TAG, "Invalid file response")
                return@doRequest true
            }

            val exists = resp.get() == 0x01.toByte()
            val pathLength = resp.getShort().toUShort()
            val entryNumber = resp.getInt().toUInt()
            val totalEntries = resp.getInt().toUInt()
            val flags = resp.getInt().toUInt()
            val timestampNanos = resp.getLong().toULong()
            val size = resp.getInt().toUInt()

            val pathBuf = ByteArray(pathLength.toInt())
            resp.get(pathBuf)

            if (entryNumber < totalEntries) {
                files.add(
                    File(
                        name = String(pathBuf),
                        fullPath = joinPaths(path, String(pathBuf)),
                        isDirectory = flags and 0x01u != 0u,
                        modTime = Instant.ofEpochMilli(timestampNanos.toLong() / 1_000_000),
                        size = size
                    )
                )
            }

            entryNumber == totalEntries
        }
    )

    return files
}

@SuppressLint("MissingPermission")
suspend fun Device.createFolder(path: String, coroutineScope: CoroutineScope) {
    doRequest(
        coroutineScope = coroutineScope,
        onBuildRequest = {
            val pathBytes = path.toByteArray()

            ByteBuffer
                .allocate(16 + pathBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x40)
                .put(0) // Padding
                .putShort(pathBytes.size.toShort())
                .putInt(0) // Padding
                .putLong(0) // Timestamp
                .put(pathBytes)
        },
        onReceiveResponse = { resp, _ ->
            if (resp.get() != 0x41.toByte()) {
                Log.e(TAG, "Invalid create folder response")
                return@doRequest true
            }

            val status = resp.get()

            Log.d(TAG, "Create folder status $status")

            true
        }
    )
}