package net.pipe01.pinepartner.scripting.api.adapters

import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattService
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import java.util.UUID

class BLEServiceAdapter : ApiScriptableObject(BLEServiceAdapter::class) {
    private lateinit var service: ClientBleGattService

    fun init(service: ClientBleGattService) {
        this.service = service
    }

    @JSGetter
    fun getUuid() = service.uuid.toString()

    @JSFunction
    fun getCharacteristic(uuidStr: String): BLECharacteristicAdapter? {
        val uuid = UUID.fromString(uuidStr)
        val characteristic = service.findCharacteristic(uuid) ?: return null

        return newObject(BLECharacteristicAdapter::class) {
            init(characteristic)
        }
    }
}