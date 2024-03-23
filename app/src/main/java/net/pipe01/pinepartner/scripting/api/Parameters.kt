package net.pipe01.pinepartner.scripting.api

import kotlinx.coroutines.runBlocking
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.scripting.Parameter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

class Parameters(
    private var parentScope: Scriptable?,
    private val pluginName: String,
    private val parameters: List<Parameter>,
    private val db: AppDatabase,
) : Scriptable {
    private fun throwReadOnly(): Nothing {
        throw ScriptRuntime.throwError(Context.getCurrentContext(), this, "Parameters are read-only")
    }

    override fun getClassName() = "Parameters"

    override fun get(p0: String?, p1: Scriptable?): Any {
        val param = parameters.find { it.name == p0 } ?: return Undefined.instance

        return runBlocking {
            val str = db.pluginDao().getParameterValue(pluginName, p0!!)

            if (str == null) {
                if (param.defaultValue != null)
                    param.type.unmarshal(param.defaultValue)
                else
                    Undefined.instance
            } else {
                param.type.unmarshal(str)
            }
        }
    }

    override fun get(p0: Int, p1: Scriptable?): Any = Undefined.instance

    override fun has(p0: String?, p1: Scriptable?) = p0 != null && parameters.any { it.name == p0 }

    override fun has(p0: Int, p1: Scriptable?) = false

    override fun put(p0: String?, p1: Scriptable?, p2: Any?) = throwReadOnly()

    override fun put(p0: Int, p1: Scriptable?, p2: Any?) = throwReadOnly()

    override fun delete(p0: String?) = throwReadOnly()

    override fun delete(p0: Int) = throwReadOnly()

    override fun getPrototype(): Scriptable {
        TODO("Not yet implemented")
    }

    override fun setPrototype(p0: Scriptable?) {
        TODO("Not yet implemented")
    }

    override fun getParentScope() = parentScope

    override fun setParentScope(p0: Scriptable?) {
        parentScope = p0
    }

    override fun getIds(): Array<Any> {
        TODO("Not yet implemented")
    }

    override fun getDefaultValue(p0: Class<*>?): Any {
        TODO("Not yet implemented")
    }

    override fun hasInstance(p0: Scriptable?): Boolean {
        TODO("Not yet implemented")
    }

}