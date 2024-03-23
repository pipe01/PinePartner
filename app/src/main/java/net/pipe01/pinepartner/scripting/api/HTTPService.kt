package net.pipe01.pinepartner.scripting.api

import fuel.Fuel
import fuel.method
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.annotations.JSFunction

class HTTPService : ApiScriptableObject(HTTPService::class) {
    @JSFunction
    fun request(method: String, url: String, arg1: Any, arg2: Any): Any? {
        var options: NativeObject? = null
        var cb: Function? = null

        if (!Undefined.isUndefined(arg1)) {
            when (arg1) {
                is NativeObject -> {
                    options = arg1
                }
                is Function -> {
                    cb = arg1
                }
                else -> {
                    throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid argument"))
                }
            }
        }
        if (!Undefined.isUndefined(arg2)) {
            if (arg2 is Function) {
                cb = arg2
            } else {
                throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid argument"))
            }
        }

        return if (cb != null) {
            launch {
                val resp = doRequest(method, url, options)

                enterAndCall(cb) { arrayOf(resp) }
            }

            Undefined.instance
        } else {
            runBlocking { doRequest(method, url, options) }
        }
    }

    private suspend fun doRequest(method: String, url: String, options: NativeObject?): String {
        val headers = options?.get("headers")?.let {
            val headers = it as? NativeObject ?: throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid headers"))
            val map = mutableMapOf<String, String>()

            for (key in headers.keys) {
                map[key as String] = headers[key] as String
            }

            map
        } ?: emptyMap()

        val timeout = options?.get("timeout")?.let {
            if (it as? Double != null) it.toLong() else throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid timeout"))
        } ?: 5000

        return withTimeout(timeout) {
            val resp = Fuel.method(url, method = method, headers = headers)

            resp.body
        }
    }
}