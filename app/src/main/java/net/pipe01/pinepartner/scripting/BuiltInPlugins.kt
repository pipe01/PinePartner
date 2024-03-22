package net.pipe01.pinepartner.scripting

import android.content.res.AssetManager
import net.pipe01.pinepartner.data.Plugin

object BuiltInPlugins {
    lateinit var plugins: List<Plugin>
        private set

    fun init(assets: AssetManager) {
        plugins = assets.list("builtins")?.map {
            val code = assets.open("builtins/$it").bufferedReader().use { it.readText() }
            Plugin.parse(code, null, true).copy(enabled = true)
        } ?: emptyList()
    }

    fun get(id: String): Plugin? {
        return plugins.find { it.id == id }
    }
}