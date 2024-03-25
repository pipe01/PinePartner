package net.pipe01.pinepartner.devices.blefs

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.devices.InfiniTime
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

private const val TAG = "BLEFS"

@SuppressLint("MissingPermission")
private suspend fun Device.doRequest(
    coroutineScope: CoroutineScope,
    onBuildRequest: suspend () -> ByteBuffer,
    onReceiveResponse: suspend (ByteBuffer, ClientBleGattCharacteristic) -> Boolean,
) {
    val fileService = InfiniTime.FileSystemService.RAW_TRANSFER.bind(services)

    val done = CompletableDeferred<Unit>()
    val start = CompletableDeferred<Unit>()

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

    val request = onBuildRequest()
    fileService.write(DataByteArray(request.array()))

    done.await()
    job.cancel()
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
//
//    val job = coroutineScope.launch {
//        fileService
//            .getNotifications()
//            .onStart { start.complete(Unit) }
//            .collect {
//                val buf = ByteBuffer.wrap(it.value).order(ByteOrder.LITTLE_ENDIAN)
//
//                if (buf.get() != 0x11.toByte()) {
//                    Log.e(TAG, "Invalid file response")
//                    return@collect
//                }
//
//                val status = buf.get()
//                buf.getShort() // Padding
//                val offset = buf.getInt()
//                val totalSize = buf.getInt()
//                val chunkSize = buf.getInt()
//
//                if (file == null) {
//                    file = ByteArray(totalSize)
//                }
//
//                it.value.copyInto(file!!, offset, buf.position(), buf.position() + chunkSize)
//
//                Log.d(TAG, "Read response $offset/$totalSize bytes")
//
//                val bytesRemaining = totalSize - offset - chunkSize
//
//                if (bytesRemaining == 0) {
//                    done.complete(Unit)
//                } else {
//                    val continueBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
//                    continueBuf.put(0x12)
//                    continueBuf.put(0x01)
//                    continueBuf.putShort(0) // Padding
//                    continueBuf.putInt(offset + chunkSize)
//                    continueBuf.putInt(wantChunkSize)
//
//                    fileService.write(DataByteArray(continueBuf.array()))
//                }
//            }
//    }

    Log.d(TAG, "File received, ${file?.size} bytes")
    return file ?: throw IllegalStateException("No file received")
}

@SuppressLint("MissingPermission")
suspend fun Device.writeFile(path: String, data: ByteArray, coroutineScope: CoroutineScope) {
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
                .putInt(data.size)
                .put(pathBytes)
        },
        onReceiveResponse = { resp, fs ->
            if (resp.get() != 0x21.toByte()) {
                Log.e(TAG, "Invalid file response")
                return@doRequest true
            }

            val status = resp.get()
            resp.getShort() // Padding
            val offset = resp.getInt()
            resp.getLong() // Timestamp
            val bytesLeft = resp.getInt()

            Log.d(TAG, "Write response status $status $offset/${data.size} bytes, $bytesLeft left")

            if (bytesLeft == 0) {
                true
            } else {
                val chunkSize = bytesLeft.coerceAtMost(mtu - 12)

                val continueBuf = ByteBuffer.allocate(12 + chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                continueBuf.put(0x22)
                continueBuf.put(0x01)
                continueBuf.putShort(0) // Padding
                continueBuf.putInt(offset)
                continueBuf.putInt(chunkSize)
                continueBuf.put(data, offset, chunkSize)

                Log.d(TAG, "Writing $chunkSize bytes")

                fs.write(DataByteArray(continueBuf.array()))

                bytesLeft - chunkSize == 0
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

            files.add(
                File(
                    path = String(pathBuf),
                    isDirectory = flags and 0x01u != 0u,
                    modTime = Instant.ofEpochMilli(timestampNanos.toLong() / 1_000_000),
                    size = size
                )
            )

            entryNumber == totalEntries
        }
    )

    return files
}