package net.pipe01.pinepartner.scripting.api.adapters

import android.annotation.SuppressLint
import kotlinx.coroutines.runBlocking
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattService
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import java.util.UUID

class BLEServiceAdapter : ApiScriptableObject("BLEService") {
    private lateinit var service: ClientBleGattService

    fun init(service: ClientBleGattService) {
        this.service = service
    }

    @JSGetter
    fun getUuid() = service.uuid.toString()

    @SuppressLint("MissingPermission")
    @JSFunction
    fun writeCharacteristic(characteristicUuid: String, value: String): Boolean {
        val uuid = UUID.fromString(characteristicUuid)
        val characteristic = service.findCharacteristic(uuid) ?: return false

        runBlocking {
            characteristic.write(DataByteArray.from(value))
        }

        return true
    }
}