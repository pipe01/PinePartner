package net.pipe01.pinepartner.scripting.api.adapters

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.typedarrays.NativeArrayBuffer
import org.mozilla.javascript.typedarrays.NativeUint8Array

class BLECharacteristicAdapter : ApiScriptableObject("BLECharacteristic") {
    private lateinit var characteristic: ClientBleGattCharacteristic

    private val subscriptionScopes = mutableMapOf<Function, CoroutineScope>()

    fun init(characteristic: ClientBleGattCharacteristic) {
        this.characteristic = characteristic
    }

    override fun finalize() {
        super.finalize()

        subscriptionScopes.values.forEach { it.cancel() }
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

        return getArrayBuffer(data.value)
    }

    @SuppressLint("MissingPermission")
    @JSFunction
    fun readString(): String {
        val data = runBlocking {
            characteristic.read()
        }

        return data.value.toString()
    }

    @JSFunction
    fun addEventListener(event: String, cb: Function) {
        if (event != "newValue") {
            throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid event"))
        }
        if (subscriptionScopes.containsKey(cb)) {
            return
        }

        val coroutineScope = CoroutineScope(Dispatchers.IO)
        subscriptionScopes[cb] = coroutineScope

        coroutineScope.launch {
            characteristic.getNotifications()
                .collect {
                    enterAndCall(cb) { arrayOf(getArrayBuffer(it.value)) }
                }
        }
    }

    @JSFunction
    fun removeEventListener(event: String, cb: Function) {
        if (event != "newValue") {
            throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid event"))
        }

        subscriptionScopes.remove(cb)?.cancel()
    }

    private fun getArrayBuffer(data: ByteArray): NativeArrayBuffer {
        val scope = getTopLevelScope(this)
        val obj = Context.getCurrentContext().newObject(scope, NativeArrayBuffer.CLASS_NAME, arrayOf(data.size)) as NativeArrayBuffer

        data.copyInto(obj.buffer)

        return obj
    }
}