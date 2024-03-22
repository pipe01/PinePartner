package net.pipe01.pinepartner.scripting.api.adapters

import android.annotation.SuppressLint
import kotlinx.coroutines.runBlocking
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import org.mozilla.javascript.Context
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.typedarrays.NativeArrayBuffer
import org.mozilla.javascript.typedarrays.NativeUint8Array

class BLECharacteristicAdapter : ApiScriptableObject("BLECharacteristic") {
    private lateinit var characteristic: ClientBleGattCharacteristic

    fun init(characteristic: ClientBleGattCharacteristic) {
        this.characteristic = characteristic
    }

    @JSGetter
    fun getUuid() = characteristic.uuid.toString()

    @SuppressLint("MissingPermission")
    @JSFunction
    fun write(value: Any) {
        val data = when (value) {
            is NativeArrayBuffer -> DataByteArray(value.buffer)
            is NativeUint8Array -> DataByteArray(value.buffer.buffer)
            is String -> DataByteArray.from(value)
            else -> throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid value type"))
        }

        runBlocking {
            characteristic.write(data)
        }
    }

    @SuppressLint("MissingPermission")
    @JSFunction
    fun read(): NativeArrayBuffer {
        val data = runBlocking {
            characteristic.read()
        }

        val scope = getTopLevelScope(this)
        val obj = Context.getCurrentContext().newObject(scope, NativeArrayBuffer.CLASS_NAME, arrayOf(data.size)) as NativeArrayBuffer

        data.value.copyInto(obj.buffer)

        return obj
    }

    @SuppressLint("MissingPermission")
    @JSFunction
    fun readString(): String {
        val data = runBlocking {
            characteristic.read()
        }

        return data.value.toString()
    }
}