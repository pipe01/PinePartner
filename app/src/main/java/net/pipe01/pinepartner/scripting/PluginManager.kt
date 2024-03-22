package net.pipe01.pinepartner.scripting

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.data.PluginDao

class PluginManager(
    private val pluginDao: PluginDao,
    private val scriptDependencies: ScriptDependencies,
) {
    private val runningPlugins = mutableMapOf<String, Runner>()
    private val runningMutex = Mutex()

    suspend fun reload() {
        val plugins = pluginDao.getAll()

        for (plugin in BuiltInPlugins.plugins.plus(plugins)) {
            if (plugin.enabled) {
                start(plugin)
            } else {
                stop(plugin.id)
            }
        }
    }

    private suspend fun start(plugin: Plugin) {
        runningMutex.withLock {
            runningPlugins[plugin.id]?.let {
                if (it.plugin.checksum == plugin.checksum) {
                    return
                }

                // Plugin has been updated
                it.stop()
            }

            val runner = Runner(plugin, scriptDependencies)
            runningPlugins[plugin.id] = runner
            runner.start()
        }
    }

    private suspend fun stop(id: String) {
        runningMutex.withLock {
            runningPlugins.remove(id)?.stop()
        }
    }

    suspend fun enable(id: String) {
        val plugin = pluginDao.getById(id) ?: return

        pluginDao.setEnabled(id, true)
        start(plugin)
    }

    suspend fun disable(id: String) {
        val plugin = pluginDao.getById(id) ?: return

        pluginDao.setEnabled(id, false)
        stop(plugin.id)
    }

    suspend fun delete(id: String) {
        val plugin = pluginDao.getById(id) ?: return

        pluginDao.deleteById(id)
        stop(plugin.id)
    }

    fun getEvents(id: String) = runningPlugins[id]?.events
}