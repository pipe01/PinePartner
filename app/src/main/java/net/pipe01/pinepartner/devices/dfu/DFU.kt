package net.pipe01.pinepartner.devices.dfu

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.devices.InfiniTime
import net.pipe01.pinepartner.utils.unzip
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.errors.GattOperationException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

suspend fun Device.flashDFU(dfuFile: InputStream, coroutineScope: CoroutineScope, onProgress: (DFUProgress) -> Unit) = doCall {
    val files = dfuFile.unzip()

    val manifestJson = files["manifest.json"]?.decodeToString() ?: throw IllegalArgumentException("No manifest.json found")
    val manifest = Json.decodeFromString<DFUManifest>(manifestJson)

    val binFile = files[manifest.manifest.application.bin_file] ?: throw IllegalArgumentException("No bin file found")
    val datFile = files[manifest.manifest.application.dat_file] ?: throw IllegalArgumentException("No dat file found")

    val packet = InfiniTime.DFUService.PACKET
    val controlPoint = InfiniTime.DFUService.CONTROL_POINT

    val controlChannel = Channel<ByteArray>()
    val controlJob = coroutineScope.launch {
        notify(controlPoint)
            .onCompletion { controlChannel.close() }
            .collect { controlChannel.send(it) }
    }

    suspend fun expectReceive(vararg expect: Byte) {
        val response = controlChannel.receive()
        if (!response.contentEquals(expect)) {
            val got = response.joinToString(":") { "%02x".format(it) }
            val expected = expect.joinToString(":") { "%02x".format(it) }

            throw IllegalArgumentException(
                "Invalid response ${got}, expected ${expected}"
            )
        }
    }

    val TAG = "DFU"

    Log.i(TAG, "Step 1")
    onProgress(DFUProgress.createInitializing(1))
    write(controlPoint, byteArrayOf(0x01, 0x04))

    Log.i(TAG, "Step 2")
    onProgress(DFUProgress.createInitializing(2))
    write(
        packet,
        ByteBuffer
            .allocate(12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0)
            .putInt(0)
            .putInt(binFile.size)
            .array(),
        BleWriteType.NO_RESPONSE
    )

    Log.i(TAG, "Step 3")
    onProgress(DFUProgress.createInitializing(3))
    expectReceive(0x10, 0x01, 0x01)

    Log.i(TAG, "Step 3.1")
    onProgress(DFUProgress.createInitializing(4))
    write(controlPoint, byteArrayOf(0x02, 0x00))

    Log.i(TAG, "Step 4")
    onProgress(DFUProgress.createInitializing(5))
    write(packet, datFile, BleWriteType.NO_RESPONSE)

    Log.i(TAG, "Step 4.1")
    onProgress(DFUProgress.createInitializing(6))
    write(controlPoint, byteArrayOf(0x02, 0x01))

    Log.i(TAG, "Step 5")
    onProgress(DFUProgress.createInitializing(7))
    expectReceive(0x10, 0x02, 0x01)

    val receiptInterval = 10

    Log.i(TAG, "Step 5.1")
    onProgress(DFUProgress.createInitializing(8))
    write(controlPoint, byteArrayOf(0x08, receiptInterval.toByte()))

    Log.i(TAG, "Step 6")
    onProgress(DFUProgress.createInitializing(9))
    write(controlPoint, byteArrayOf(0x03))

    Log.i(TAG, "Step 7")

    var sentChunks = 0
    var lastProgressTime = SystemClock.uptimeMillis()

    val progressReportEveryBytes = 1000

    for (i in binFile.indices step 20) {
        if (i % progressReportEveryBytes == 0) {
            Log.d(TAG, "Offset $i of ${binFile.size}")

            val now = SystemClock.uptimeMillis()
            val elapsedMillis = now - lastProgressTime
            lastProgressTime = now

            val bytesPerSecond = (progressReportEveryBytes.toFloat() / elapsedMillis) * 1000
            val progress = i.toFloat() / binFile.size

            val bytesLeft = binFile.size - i

            onProgress(
                DFUProgress(
                    "Transferring firmware",
                    progress,
                    0.05f + progress * 0.9f,
                    bytesPerSecond.toLong(),
                    (bytesLeft / bytesPerSecond).toInt(),
                )
            )
        }

        val until = if (i + 20 > binFile.size) binFile.size else i + 20

        val chunk = binFile.sliceArray(i until until)
        write(packet, chunk, BleWriteType.NO_RESPONSE)

        sentChunks++

        if (sentChunks % receiptInterval == 0) {
            val expected = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sentChunks * 20).array()

            expectReceive(0x11, *expected)
        }
    }

    Log.i(TAG, "Step 8")
    onProgress(DFUProgress.createFinishing(1))
    expectReceive(0x10, 0x03, 0x01)

    Log.i(TAG, "Step 8.1")
    onProgress(DFUProgress.createFinishing(2))
    write(controlPoint, byteArrayOf(0x04))

    Log.i(TAG, "Step 9")
    onProgress(DFUProgress.createFinishing(3))
    expectReceive(0x10, 0x04, 0x01)

    controlJob.cancel()

    Log.i(TAG, "Step 9.1")
    onProgress(DFUProgress.createFinishing(4))
    try {
        write(controlPoint, byteArrayOf(0x05))
    } catch (e: GattOperationException) {
        // Expected
    }

    onProgress(DFUProgress("Done", 1f, 1f, isDone = true))
}