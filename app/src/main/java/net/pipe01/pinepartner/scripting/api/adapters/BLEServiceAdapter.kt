package net.pipe01.pinepartner.scripting.api.adapters

import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import java.util.UUID

class BLEServiceAdapter : ApiScriptableObject(BLEServiceAdapter::class) {
    private lateinit var device: Device
    private lateinit var serviceId: UUID

    fun init(device: Device, serviceId: UUID) {
        this.device = device
        this.serviceId = serviceId
    }

    @JSGetter
    fun getUuid() = serviceId.toString()

    @JSFunction
    fun getCharacteristic(uuidStr: String): BLECharacteristicAdapter {
        val uuid = UUID.fromString(uuidStr)

        return newObject(BLECharacteristicAdapter::class) {
            init(device, serviceId, uuid)
        }
    }
}