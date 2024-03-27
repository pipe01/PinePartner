package net.pipe01.pinepartner.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.utils.EventEmitter

class DeviceManager(private val context: Context) {
    private val TAG = "DeviceManager"

    private val devicesMap = mutableMapOf<String, Device>()

    private val _deviceConnected = EventEmitter<Device>()
    val deviceConnected = _deviceConnected.toEvent()

    private val _deviceDisconnected = EventEmitter<String>()
    val deviceDisconnected = _deviceDisconnected.toEvent()

    val connectedDevices: List<Device>
        get() = devicesMap.values.filter { it.isConnected }

    fun get(address: String) = devicesMap[address]

    suspend fun connect(address: String, coroutineScope: CoroutineScope, reconnect: Boolean) {
        Log.i(TAG, "Connecting to watch $address")

        val device = devicesMap[address]
        if (device != null) {
            if (!device.isConnected) {
                Log.d(TAG, "Cleaning up old device")
                devicesMap.remove(address)
            } else {
                Log.d(TAG, "Device already connected")
                return
            }
        }

        val dev = Device.connect(context, coroutineScope, address)

        dev.reconnect = reconnect
        dev.setCurrentTime()

        Log.i(TAG, "Connected to watch $address")
        devicesMap[address] = dev

        _deviceConnected.emit(dev)
    }

    fun disconnect(address: String) {
        val dev = devicesMap[address]
        if (dev != null) {
            dev.disconnect()
            devicesMap.remove(address)

            _deviceDisconnected.emit(address)
        }
    }
}