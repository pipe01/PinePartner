package net.pipe01.pinepartner.scripting.api.adapters

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.typedarrays.NativeArrayBuffer
import org.mozilla.javascript.typedarrays.NativeUint8Array
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BLECharacteristicAdapter : ApiScriptableObject(BLECharacteristicAdapter::class) {
    private lateinit var device: Device
    private lateinit var serviceId: UUID
    private lateinit var characteristicId: UUID

    private val subscriptionScopes = mutableMapOf<Function, CoroutineScope>()

    fun init(device: Device, serviceId: UUID, characteristicId: UUID) {
        this.device = device
        this.serviceId = serviceId
        this.characteristicId = characteristicId
    }

    override fun finalize() {
        super.finalize()

        subscriptionScopes.values.forEach { it.cancel() }
    }

    @JSGetter
    fun getUuid() = characteristicId.toString()

    @SuppressLint("MissingPermission")
    @JSFunction
    fun write(value: Any) {
        val data = when (value) {
            is NativeArrayBuffer -> value.buffer
            is NativeUint8Array -> value.buffer.buffer
            is String -> value.toByteArray()
            is Long -> ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array()
            is Int -> ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
            else -> throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid value type"))
        }

        runBlocking {
            device.write(serviceId, characteristicId, data)
        }
    }

    @SuppressLint("MissingPermission")
    @JSFunction
    fun read(): NativeArrayBuffer {
        val data = runBlocking {
            device.read(serviceId, characteristicId)
        }

        return getArrayBuffer(data)
    }

    @SuppressLint("MissingPermission")
    @JSFunction
    fun readString(): String {
        val data = runBlocking {
            device.read(serviceId, characteristicId)
        }

        return data.toString()
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
            device.notify(serviceId, characteristicId)
                .onStart { Log.d("BLECharacteristicAdapter", "Start") }
                .onCompletion { Log.d("BLECharacteristicAdapter", "Completed") }
                .collect {
                    enterAndCall(cb) { arrayOf(getArrayBuffer(it)) }
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