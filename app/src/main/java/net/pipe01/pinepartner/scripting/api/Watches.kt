package net.pipe01.pinepartner.scripting.api

import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.scripting.api.adapters.WatchAdapter
import net.pipe01.pinepartner.service.DeviceManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter

class Watches : ApiScriptableObject("Watches") {
    private lateinit var db: AppDatabase
    private lateinit var deviceManager: DeviceManager

    internal fun init(db: AppDatabase, deviceManager: DeviceManager) {
        this.db = db
        this.deviceManager = deviceManager
    }

    private fun getWatch(dev: Device): WatchAdapter {
        return newObject("Watch") {
            init(dev, deviceManager)
        }
    }

    @JSGetter
    fun getAll() = deviceManager.connectedDevices.map { getWatch(it) }

    @JSFunction
    fun addEventListener(event: String, cb: Function) {
        when (event) {
            "connected" -> addListener(deviceManager.deviceConnected, event, cb) {
                getWatch(it)
            }
            "disconnected" -> addListener(deviceManager.deviceDisconnected, event, cb)
            else -> Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid event"))
        }
    }

    @JSFunction
    fun removeEventListener(event: String, cb: Function) {
        when (event) {
            "connected" -> removeListener(event, cb)
            "disconnected" -> removeListener(event, cb)
            else -> Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid event"))
        }
    }
}