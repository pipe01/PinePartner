package net.pipe01.pinepartner.devices.blefs

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import net.pipe01.pinepartner.devices.Characteristic
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.devices.InfiniTime
import net.pipe01.pinepartner.service.TransferProgress
import net.pipe01.pinepartner.utils.ProgressReporter
import net.pipe01.pinepartner.utils.SuspendClosable
import net.pipe01.pinepartner.utils.joinToHexString
import net.pipe01.pinepartner.utils.use
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

private const val TAG = "BLEFS"

//TODO: Make this per-device
private val mutex = Mutex()

enum class LittleFSError(val value: Byte, val message: String) {
    OK(1, "ok"),
    IO(-5, "error during device operation"),
    CORRUPT(-84, "corrupted"),
    NOENT(-2, "no directory entry"),
    EXIST(-17, "entry already exists"),
    NOTDIR(-20, "entry is not a dir"),
    ISDIR(-21, "entry is a dir"),
    NOTEMPTY(-39, "dir is not empty"),
    BADF(-9, "bad file number"),
    FBIG(-27, "file too large"),
    INVAL(-22, "invalid parameter"),
    NOSPC(-28, "no space left on device"),
    NOMEM(-12, "no more memory available"),
    NOATTR(-61, "no data/attr available"),
    NAMETOOLONG(-36, "file name too long");

    companion object {
        fun fromValue(value: Byte): LittleFSError? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

data class BLEFSException(override val message: String, val errorCode: LittleFSError? = null) : Exception(message)

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
    val parts = ArrayDeque(path.removePrefix("/").split("/"))

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
    private val device: Device,
    private val characteristic: Characteristic,
    coroutineScope: CoroutineScope,
) : SuspendClosable {
    private val tag = "RequestChannel-${Random.nextInt(10000, 99999)}"

    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    private val notifJob: Job
    private val closeCompletable = CompletableDeferred<Unit>()

    private var storedResponse = AtomicReference<ByteBuffer?>(null)

    init {
        notifJob = coroutineScope.launch {
            device.notify(characteristic)
                .onStart { Log.d(tag, "Notifications started") }
                .onCompletion {
                    Log.d(tag, "Notifications done")
                    channel.close()
                    closeCompletable.complete(Unit)
                }
                .collect {
                    Log.d(tag, "Notification: ${it.joinToHexString()}")
                    channel.send(it)
                }
        }
    }

    override suspend fun close() {
        notifJob.cancel()

        try {
            withTimeout(3000) {
                closeCompletable.await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(tag, "Failed to wait for notifications to end", e)
        }
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
                device.write(characteristic, req)
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
            } catch (e: ClosedReceiveChannelException) {
                Log.d(tag, "Channel closed")

                throw CancellationException()
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
) = mutex.withLock {
    val pathBytes = path.toByteArray()
    val wantChunkSize = mtu - 12

    Log.d(TAG, "Reading file $path, wantChunkSize=$wantChunkSize")

    RequestChannel(this, InfiniTime.FileSystemService.RAW_TRANSFER, coroutineScope).use { channel ->
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
                if (status != 1.toByte()) {
                    throw BLEFSException("Read failed", LittleFSError.fromValue(status))
                }

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
}

@SuppressLint("MissingPermission")
suspend fun Device.writeFile(
    path: String,
    inputStream: InputStream,
    totalSize: Int,
    coroutineScope: CoroutineScope,
    onProgress: (TransferProgress) -> Unit = { }
) = mutex.withLock {
    val headerSize = 12
    val buffer = ByteArray(mtu - 16) // Size determined through experimentation, any larger crashes the watch

    var sent = 0

    Log.d(TAG, "Writing file $path, $totalSize bytes, buffer size is ${buffer.size} bytes")

    RequestChannel(this, InfiniTime.FileSystemService.RAW_TRANSFER, coroutineScope).use { channel ->
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
                if (status != 1.toByte()) {
                    throw BLEFSException("Write failed", LittleFSError.fromValue(status))
                }

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
}

@SuppressLint("MissingPermission")
suspend fun Device.deleteFile(path: String, coroutineScope: CoroutineScope) = mutex.withLock {
    Log.d(TAG, "Deleting file $path")

    RequestChannel(this, InfiniTime.FileSystemService.RAW_TRANSFER, coroutineScope).use { channel ->
        val pathBytes = path.toByteArray()

        val resp = channel.send(
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

        val status = resp.get()
        if (status != 1.toByte()) {
            throw BLEFSException("Delete failed", LittleFSError.fromValue(status))
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun Device.listFiles(path: String, coroutineScope: CoroutineScope): List<File> = mutex.withLock {
    Log.d(TAG, "Listing files in $path")

    val files = mutableListOf<File>()

    RequestChannel(this, InfiniTime.FileSystemService.RAW_TRANSFER, coroutineScope).use<RequestChannel, List<File>> { channel ->
        val pathBytes = path.toByteArray()

        fun parseFile(buffer: ByteBuffer): File? {
            val status = buffer.get()
            if (status != 1.toByte()) {
                throw BLEFSException("List failed", LittleFSError.fromValue(status))
            }

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
suspend fun Device.createFolder(path: String, coroutineScope: CoroutineScope) = mutex.withLock {
    Log.d(TAG, "Creating folder $path")

    RequestChannel(this, InfiniTime.FileSystemService.RAW_TRANSFER, coroutineScope).use { channel ->
        val pathBytes = path.toByteArray()

        val resp = channel.send(
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

        val status = resp.get()
        if (status != 1.toByte()) {
            throw BLEFSException("Create folder failed", LittleFSError.fromValue(status))
        }
    }
}

suspend fun Device.createAllFolders(path: String, coroutineScope: CoroutineScope) {
    Log.d(TAG, "Creating all folders for $path")

    val parts = path.removePrefix("/").split("/")

    var currentPath = ""

    for (part in parts) {
        currentPath = joinPaths(currentPath, part)

        try {
            createFolder(currentPath, coroutineScope)
        } catch (e: BLEFSException) {
            if (e.errorCode != LittleFSError.EXIST) {
                throw e
            }
        }
    }
}