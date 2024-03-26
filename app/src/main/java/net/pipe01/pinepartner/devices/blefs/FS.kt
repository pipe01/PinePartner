package net.pipe01.pinepartner.devices.blefs

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.devices.InfiniTime
import net.pipe01.pinepartner.service.TransferProgress
import net.pipe01.pinepartner.utils.ProgressReporter
import net.pipe01.pinepartner.utils.joinToHexString
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "BLEFS"

//TODO: Make this per-device
private val mutex = Mutex()

private data class Progress(val sent: Int, val total: Int)

data class BLEFSException(override val message: String) : Exception(message)

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

private class RequestChannel(
    private val characteristic: ClientBleGattCharacteristic,
    coroutineScope: CoroutineScope,
) : Closeable {
    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    private val notifJob: Job

    private var storedResponse = AtomicReference<ByteBuffer?>(null)

    private val tag = "RequestChannel"

    init {
        notifJob = coroutineScope.launch {
            characteristic
                .getNotifications()
                .onCompletion {
                    Log.d(tag, "Notifications done")
                    channel.close()
                }
                .collect {
                    Log.d(tag, "Notification: ${it.value.joinToHexString()}")
                    channel.send(it.value)
                }
        }
    }

    override fun close() {
        notifJob.cancel()
    }

    @SuppressLint("MissingPermission")
    suspend fun send(expectCommand: Byte, req: ByteArray, keepResponse: Boolean = false): ByteBuffer {
        Log.d(tag, "Sending request 0x${expectCommand.toUInt().toString(16)}")

        delay(200) // Wait for notification reception to start

        var triesLeft = 3

        while (triesLeft > 0) {
            Log.d(tag, "Sending request, tries left: $triesLeft")

            triesLeft--

            try {
                characteristic.write(DataByteArray(req))
            } catch (e: Exception) {
                Log.e(tag, "Failed to write request", e)

                delay(500)
                continue
            }

            Log.d(tag, "Request sent, waiting for response")

            try {
                val data = withTimeout(2000) {
                    channel.receive()
                }

                Log.d(tag, "Received response: ${data.joinToHexString()}")

                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                val command = buffer.get()
                if (command != expectCommand) {
                    throw BLEFSException("Invalid response 0x${command.toUInt().toString(16)}")
                }

                if (keepResponse) {
                    storedResponse.set(buffer)
                }

                return buffer
            } catch (e: TimeoutCancellationException) {
                Log.e(tag, "Timeout waiting for response")

                delay(500)
            }
        }

        throw Exception("Failed to send request")
    }

    suspend fun drain(expectCommand: Byte, onReceive: suspend (ByteBuffer) -> Boolean) {
        Log.d(tag, "Draining responses")

        storedResponse.getAndSet(null)?.let {
            Log.d(tag, "Using stored response")

            // We don't need to check the command here since we already did when storing the response

            if (!onReceive(it)) {
                return
            }
        }

        while (true) {
            val data = channel.receiveCatching()
            if (!data.isSuccess) {
                Log.d(tag, "Channel closed")
                break
            }

            Log.d(tag, "Received response: ${data.getOrThrow().joinToHexString()}")

            val buffer = ByteBuffer.wrap(data.getOrThrow()).order(ByteOrder.LITTLE_ENDIAN)

            val command = buffer.get()
            if (command != expectCommand) {
                throw BLEFSException("Invalid response 0x${command.toUInt().toString(16)}")
            }

            if (!onReceive(buffer)) {
                Log.d(tag, "Drain done")
                break
            }
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun Device.readFile(
    path: String,
    output: OutputStream,
    coroutineScope: CoroutineScope,
    onProgress: (TransferProgress) -> Unit = { }
) {
    val channel = RequestChannel(InfiniTime.FileSystemService.RAW_TRANSFER.bind(services), coroutineScope)

    val pathBytes = path.toByteArray()
    val wantChunkSize = mtu - 12

    ProgressReporter(0, onProgress).use { reporter ->
        var resp = channel.send(
            expectCommand = 0x11,
            req = ByteBuffer
                .allocate(12 + pathBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x10)
                .put(0)
                .putShort(pathBytes.size.toShort())
                .putInt(0)
                .putInt(wantChunkSize)
                .put(pathBytes)
                .array(),
        )

        while (true) {
            val status = resp.get()
            resp.getShort() // Padding
            val offset = resp.getInt()
            val totalSize = resp.getInt()
            val chunkSize = resp.getInt()

            reporter.totalSize = totalSize.toLong()

            val chunk = ByteArray(chunkSize)
            resp.get(chunk)

            output.write(chunk)
            reporter.addBytes(chunkSize.toLong())

            Log.d(TAG, "Read response $offset/$totalSize bytes")

            val bytesRemaining = totalSize - offset - chunkSize

            if (bytesRemaining == 0) {
                break
            }

            resp = channel.send(
                expectCommand = 0x11,
                req = ByteBuffer
                    .allocate(12)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put(0x12)
                    .put(0x01)
                    .putShort(0) // Padding
                    .putInt(offset + chunkSize)
                    .putInt(wantChunkSize).array()
            )
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun Device.writeFile(
    path: String,
    inputStream: InputStream,
    totalSize: Int,
    coroutineScope: CoroutineScope,
    onProgress: (TransferProgress) -> Unit = { }
) {
    val headerSize = 12
    val buffer = ByteArray(mtu - 16) // Size determined through experimentation, any larger crashes the watch

    var sent = 0

    Log.d(TAG, "Writing file $path, $totalSize bytes, buffer size is ${buffer.size} bytes")

    //TODO: Lock

    val channel = RequestChannel(InfiniTime.FileSystemService.RAW_TRANSFER.bind(services), coroutineScope)

    val pathBytes = path.toByteArray()

    var resp = channel.send(
        0x21,
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
            .array()
    )

    ProgressReporter(totalSize.toLong(), onProgress).use { reporter ->
        while (true) {
            val status = resp.get()
            val bytesLeft = totalSize - sent

            Log.d(TAG, "Write status $status, $sent/$totalSize bytes")

            if (bytesLeft == 0) {
                break
            } else {
                val readNum = coroutineScope.run {
                    inputStream.read(buffer)
                }
                //TODO: Handle readNum <= 0

                val continueBuf = ByteBuffer.allocate(headerSize + readNum).order(ByteOrder.LITTLE_ENDIAN)
                continueBuf.put(0x22)
                continueBuf.put(0x01)
                continueBuf.putShort(0) // Padding
                continueBuf.putInt(sent)
                continueBuf.putInt(readNum)
                continueBuf.put(buffer, 0, readNum)

                Log.d(TAG, "Writing $readNum bytes")

                sent += readNum
                reporter.addBytes(readNum.toLong())

                resp = channel.send(0x21, continueBuf.array())
            }
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun Device.deleteFile(path: String, coroutineScope: CoroutineScope) {
    val channel = RequestChannel(InfiniTime.FileSystemService.RAW_TRANSFER.bind(services), coroutineScope)

    val pathBytes = path.toByteArray()

    //TODO: Check status
    channel.send(
        0x31,
        ByteBuffer
            .allocate(4 + pathBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(0x30)
            .put(0)
            .putShort(pathBytes.size.toShort())
            .put(pathBytes)
            .array()
    )
}

@SuppressLint("MissingPermission")
suspend fun Device.listFiles(path: String, coroutineScope: CoroutineScope): List<File> {
    val files = mutableListOf<File>()

    RequestChannel(InfiniTime.FileSystemService.RAW_TRANSFER.bind(services), coroutineScope).use { channel ->
        val pathBytes = path.toByteArray()

        fun parseFile(buffer: ByteBuffer): File? {
            val exists = buffer.get() == 0x01.toByte()
            val pathLength = buffer.getShort().toUShort()
            val entryNumber = buffer.getInt().toUInt()
            val totalEntries = buffer.getInt().toUInt()
            val flags = buffer.getInt().toUInt()
            val timestampNanos = buffer.getLong().toULong()
            val size = buffer.getInt().toUInt()

            val pathBuf = ByteArray(pathLength.toInt())
            buffer.get(pathBuf)

            return if (entryNumber < totalEntries) {
                File(
                    name = String(pathBuf),
                    fullPath = joinPaths(path, String(pathBuf)),
                    isDirectory = flags and 0x01u != 0u,
                    modTime = Instant.ofEpochMilli(timestampNanos.toLong() / 1_000_000),
                    size = size
                )
            } else {
                null
            }
        }

        val firstResp = channel.send(
            0x51,
            ByteBuffer
                .allocate(4 + pathBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x50)
                .put(0)
                .putShort(pathBytes.size.toShort())
                .put(pathBytes)
                .array()
        )

        parseFile(firstResp)?.let { files.add(it) } ?: return emptyList()

        channel.drain(0x51) { buffer ->
            parseFile(buffer)?.let { files.add(it) } ?: return@drain false
            true
        }

        return files
    }
}

@SuppressLint("MissingPermission")
suspend fun Device.createFolder(path: String, coroutineScope: CoroutineScope) {
    val channel = RequestChannel(InfiniTime.FileSystemService.RAW_TRANSFER.bind(services), coroutineScope)

    val pathBytes = path.toByteArray()

    //TODO: Check status
    channel.send(
        expectCommand = 0x41,
        req = ByteBuffer
            .allocate(16 + pathBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(0x40)
            .put(0) // Padding
            .putShort(pathBytes.size.toShort())
            .putInt(0) // Padding
            .putLong(0) // Timestamp
            .put(pathBytes)
            .array()
    )
}