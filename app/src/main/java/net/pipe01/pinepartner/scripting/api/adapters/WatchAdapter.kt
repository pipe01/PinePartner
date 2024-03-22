package net.pipe01.pinepartner.scripting.api.adapters

import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import net.pipe01.pinepartner.service.DeviceManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class WatchAdapter : ApiScriptableObject("Watch") {
    private lateinit var device: Device
    private lateinit var deviceManager: DeviceManager

    internal fun init(watch: Device, deviceManager: DeviceManager) {
        this.device = watch
        this.deviceManager = deviceManager
    }

    @JSGetter
    fun getAddress() = device.address

    @JSGetter
    fun getIsConnected() = device.isConnected

    @JSFunction
    fun sendNotification(title: String, body: String) {
        val device = deviceManager.get(device.address) ?: throw IllegalStateException("Device not connected")

        launch {
            device.sendNotification(0, 1, title, body)
        }
    }

    @JSFunction
    fun setTime(time: Any) {
        val timeMillis = when {
            time is Double -> time
            time.javaClass.name == "org.mozilla.javascript.NativeDate" -> {
                val field = time.javaClass.getDeclaredField("date")
                field.isAccessible = true
                field.get(time) as Double
            }
            else -> {
                Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid time"))
                0.0
            }
        }

        val localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis.toLong()), ZoneId.systemDefault())

        launch {
            device.setCurrentTime(localTime)
        }
    }

    @JSFunction
    fun getService(uuid: String): Any? {
        val service = device.getBLEService(UUID.fromString(uuid)) ?: return null

        return newObject<BLEServiceAdapter>("BLEService") {
            init(service)
        }
    }
}