package net.pipe01.pinepartner.scripting.api

import fuel.Fuel
import fuel.method
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.annotations.JSFunction

class HTTP : ApiScriptableObject("HTTP") {
    @JSFunction
    fun request(method: String, url: String, arg1: Any, arg2: Any?) {
        val options: NativeObject?
        val cb: Function

        if (Undefined.isUndefined(arg2)) {
            options = null
            cb = arg1 as? Function ?: throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid callback"))
        } else {
            options = arg1 as? NativeObject ?: throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid options"))
            cb = arg2 as? Function ?: throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid callback"))
        }

        launch {
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

            withTimeout(timeout) {
                val resp = Fuel.method(url, method = method, headers = headers)

                enterAndCall(cb) { arrayOf(resp.body) }
            }
        }
    }
}