package net.pipe01.pinepartner.devices

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import java.time.LocalDateTime
import java.util.UUID

@SuppressLint("MissingPermission")
class Device private constructor(val address: String, private val client: ClientBleGatt, private val services: ClientBleGattServices) {
    private val TAG = "Device"

    private val callMutex = Mutex()

    private var firmwareRevision: String? = null

    private var batteryLevel: Float? = null
    private var batteryLevelCheckTime: LocalDateTime? = null

    val isConnected
        get() = client.isConnected

    companion object {
        suspend fun connect(context: Context, coroutineScope: CoroutineScope, address: String): Device {
            val client = ClientBleGatt.connect(context, address, coroutineScope)

            Log.d("Device", "Connected to $address: ${client.isConnected}")

            //TODO: Handle case where connection fails but no exception is thrown
            val services = client.discoverServices()

            return Device(address, client, services)
        }
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting from device")

        client.disconnect()
    }

    private suspend fun <T> doCall(fn: suspend () -> T): T {
        if (!isConnected) throw IllegalStateException("Device not connected")

        return callMutex.withLock { fn() }
    }

    suspend fun getBatteryLevel(): Float = doCall {
        if (batteryLevel == null || LocalDateTime.now().isAfter(batteryLevelCheckTime!!.plusMinutes(1))) {
            Log.i(TAG, "Reading battery level")

            batteryLevel = InfiniTime.BatteryService.BATTERY_LEVEL.bind(services).read().value[0] / 100f
            batteryLevelCheckTime = LocalDateTime.now()
        }

        return@doCall batteryLevel!!
    }

    suspend fun getFirmwareRevision(): String = doCall {
        if (firmwareRevision == null) {
            Log.i(TAG, "Reading firmware revision")

            firmwareRevision = InfiniTime.DeviceInformationService.FIRMWARE_REVISION.bind(services).read().value.decodeToString()
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

        InfiniTime.AlertNotificationService.NEW_ALERT.bind(services).write(DataByteArray(bytes.toByteArray()))
    }

    suspend fun setCurrentTime(time: LocalDateTime = LocalDateTime.now()) = doCall {
        Log.i(TAG, "Setting current time")

        val bytes = byteArrayOf(
            time.year.toByte(),
            time.year.shr(8).toByte(),
            time.monthValue.toByte(),
            time.dayOfMonth.toByte(),
            (time.hour ).toByte(),
            time.minute.toByte(),
            time.second.toByte(),
            0,
            0,
            0,
        )

        InfiniTime.CurrentTimeService.CURRENT_TIME.bind(services).write(DataByteArray(bytes))
    }

    fun getBLEService(uuid: UUID) = services.findService(uuid)
}