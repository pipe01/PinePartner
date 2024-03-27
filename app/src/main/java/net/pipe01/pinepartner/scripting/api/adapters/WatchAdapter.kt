package net.pipe01.pinepartner.scripting.api.adapters

import net.pipe01.pinepartner.devices.CurrentWeather
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.devices.MAX_LOCATION_LEN
import net.pipe01.pinepartner.devices.WeatherIcon
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import net.pipe01.pinepartner.service.DeviceManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class WatchAdapter : ApiScriptableObject(WatchAdapter::class) {
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
    fun getService(arg: Any): BLEServiceAdapter? {
        val uuid = when (arg) {
            is String -> UUID.fromString(arg)
            is Int -> UUID.fromString("${arg.toString(16).padStart(8, '0')}-0000-1000-8000-00805F9B34FB")
            else -> throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid UUID"))
        }

        return newObject(BLEServiceAdapter::class) {
            init(device, uuid)
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

            else -> throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid time"))
        }

        val localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis.toLong()), ZoneId.systemDefault())

        launch {
            device.setCurrentTime(localTime)
        }
    }

    @JSFunction
    fun setCurrentWeather(obj: NativeObject) {
        val temperature = obj["temp"] as? Double ?: throw IllegalArgumentException("Temperature is required")
        val minTemperature = obj["minTemp"] as? Double ?: throw IllegalArgumentException("Minimum temperature is required")
        val maxTemperature = obj["maxTemp"] as? Double ?: throw IllegalArgumentException("Maximum temperature is required")
        val location = obj["location"] as? String ?: throw IllegalArgumentException("Location is required")
        val iconName = obj["icon"] as? String ?: throw IllegalArgumentException("Icon is required")

        if (location.length > MAX_LOCATION_LEN)
            throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Location should be $MAX_LOCATION_LEN characters long or shorter"))

        val icon = WeatherIcon.entries.find { it.jsName == iconName } ?: throw IllegalArgumentException("Invalid icon")

        val weather = CurrentWeather(
            LocalDateTime.now(),
            temperature,
            minTemperature,
            maxTemperature,
            location,
            icon,
        )

        launch {
            device.setCurrentWeather(weather)
        }
    }
}