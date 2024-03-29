package net.pipe01.pinepartner.devices

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.pipe01.pinepartner.utils.unzip
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import no.nordicsemi.android.kotlin.ble.core.data.BleGattConnectOptions
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.data.GattConnectionState
import no.nordicsemi.android.kotlin.ble.core.errors.GattOperationException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.scheduleAtFixedRate

@SuppressLint("MissingPermission")
class Device private constructor(
    val address: String,
    private val client: ClientBleGatt,
    private var services: ClientBleGattServices,
) {
    enum class Status {
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
    }

    private val TAG = "Device"

    private val callMutex = Mutex()
    private val reconnectTimer = Timer()

    private var firmwareRevision: String? = null

    private var batteryLevel: Float? = null
    private var batteryLevelCheckTime: LocalDateTime? = null

    var reconnect: Boolean = false

    val mtu
        get() = client.mtu.value

    val status
        get() = when {
            client.isConnected -> Status.CONNECTED
            reconnect -> Status.RECONNECTING
            else -> Status.DISCONNECTED
        }

    companion object {
        suspend fun connect(context: Context, coroutineScope: CoroutineScope, address: String): Device {
            val client = ClientBleGatt.connect(
                context = context,
                macAddress = address,
                scope = coroutineScope,
                options = BleGattConnectOptions(closeOnDisconnect = false),
            )

            client.requestMtu(517) // Needed for BLEFS

            Log.d("Device", "Connected to $address: ${client.isConnected}, MTU: ${client.mtu.value}")

            //TODO: Handle case where connection fails but no exception is thrown
            val services = client.discoverServices()

            return Device(address, client, services)
        }
    }

    init {
        reconnectTimer.scheduleAtFixedRate(5000, 5000) {
            if (!client.isConnected && reconnect) {
                Log.i(TAG, "Reconnecting to device")

                try {
                    client.reconnect()

                    if (client.connectionStateWithStatus.value?.state == GattConnectionState.STATE_CONNECTED) {
                        Log.i(TAG, "Reconnected to device")
                        runBlocking {
                            this@Device.services = client.discoverServices()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reconnect", e)
                }
            }
        }
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting from device")

        reconnectTimer.cancel()
        client.disconnect()
        client.close()
    }

    suspend inline fun write(char: Characteristic, data: ByteArray, writeType: BleWriteType = BleWriteType.DEFAULT) =
        write(char.serviceUuid, char.uuid, data, writeType)

    suspend fun write(serviceId: UUID, characteristicId: UUID, data: ByteArray, writeType: BleWriteType = BleWriteType.DEFAULT) {
        val bleChar =
            services.findService(serviceId)?.findCharacteristic(characteristicId) ?: throw IllegalArgumentException("Characteristic not found")

        bleChar.write(DataByteArray(data), writeType)
    }

    suspend inline fun read(char: Characteristic) = read(char.serviceUuid, char.uuid)
    suspend fun read(serviceId: UUID, characteristicId: UUID): ByteArray {
        val bleChar =
            services.findService(serviceId)?.findCharacteristic(characteristicId) ?: throw IllegalArgumentException("Characteristic not found")

        return bleChar.read().value
    }

    suspend inline fun notify(char: Characteristic) = notify(char.serviceUuid, char.uuid)
    suspend fun notify(serviceId: UUID, characteristicId: UUID) = flow {
        val bleChar =
            services.findService(serviceId)?.findCharacteristic(characteristicId) ?: throw IllegalArgumentException("Characteristic not found")

        bleChar.getNotifications()
            .collect {
                emit(it.value)
            }
    }

    private suspend fun <T> doCall(fn: suspend () -> T): T {
        if (!client.isConnected) throw IllegalStateException("Device not connected")

        return callMutex.withLock { fn() }
    }

    suspend fun getBatteryLevel(): Float = doCall {
        if (batteryLevel == null || LocalDateTime.now().isAfter(batteryLevelCheckTime!!.plusMinutes(1))) {
            Log.i(TAG, "Reading battery level")

            batteryLevel = read(InfiniTime.BatteryService.BATTERY_LEVEL)[0] / 100f
            batteryLevelCheckTime = LocalDateTime.now()
        }

        return@doCall batteryLevel!!
    }

    suspend fun getFirmwareRevision(): String = doCall {
        if (firmwareRevision == null) {
            Log.i(TAG, "Reading firmware revision")

            firmwareRevision = read(InfiniTime.DeviceInformationService.FIRMWARE_REVISION).decodeToString()
        }

        return@doCall firmwareRevision!!
    }

    suspend fun sendNotification(category: Byte, amount: Byte, title: String, body: String) = doCall {
        Log.i(TAG, "Sending notification: $category, $amount, $title, $body")

        val bytes = mutableListOf(category, amount, 0)
        bytes.addAll(title.encodeToByteArray().toList())
        bytes.add(0)
        bytes.addAll(body.encodeToByteArray().toList())
        bytes.add(0)

        write(InfiniTime.AlertNotificationService.NEW_ALERT, bytes.toByteArray())
    }

    suspend fun setCurrentTime(time: LocalDateTime = LocalDateTime.now()) = doCall {
        Log.i(TAG, "Setting current time")

        val bytes = byteArrayOf(
            time.year.toByte(),
            time.year.shr(8).toByte(),
            time.monthValue.toByte(),
            time.dayOfMonth.toByte(),
            (time.hour).toByte(),
            time.minute.toByte(),
            time.second.toByte(),
            0,
            0,
            0,
        )

        write(InfiniTime.CurrentTimeService.CURRENT_TIME, bytes)
    }

    suspend fun setCurrentWeather(weather: CurrentWeather) = doCall {
        val buffer = ByteBuffer.allocate(49).order(ByteOrder.LITTLE_ENDIAN)

        // We want local timestamp, not UTC timestamp
        val timestamp = weather.time.toEpochSecond(ZoneOffset.systemDefault().rules.getOffset(weather.time))

        val locationBytes = weather.location.toByteArray()
        if (locationBytes.size > MAX_LOCATION_LEN) throw IllegalArgumentException("Location is too long")

        buffer.put(0) // Message type
        buffer.put(0) // Message version
        buffer.putLong(timestamp)
        buffer.putShort((weather.currentTemperature * 100).toInt().toShort())
        buffer.putShort((weather.minimumTemperature * 100).toInt().toShort())
        buffer.putShort((weather.maximumTemperature * 100).toInt().toShort())
        buffer.put(locationBytes)
        buffer.position(48)
        buffer.put(weather.icon.id)

        write(InfiniTime.WeatherService.WEATHER_DATA, buffer.array())
    }

    suspend fun flashDFU(dfuFile: InputStream, coroutineScope: CoroutineScope, onProgress: (DFUProgress) -> Unit) = doCall {
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
}