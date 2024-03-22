package net.pipe01.pinepartner.scripting

import fuel.Fuel
import fuel.get
import net.pipe01.pinepartner.data.Plugin

suspend fun downloadPlugin(url: String): Plugin {
    //TODO: Catch errors
    val resp = Fuel.get(url)

    //TODO: Check response code

    return Plugin.parse(resp.body, url, false)
}