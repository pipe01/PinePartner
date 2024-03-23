package net.pipe01.pinepartner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PluginDao {
    @Query("SELECT * FROM Plugin")
    suspend fun getAll(): List<Plugin>

    @Query("SELECT * FROM Plugin WHERE id = :id")
    suspend fun getById(id: String): Plugin?

    @Query("SELECT EXISTS(SELECT 1 FROM Plugin WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("DELETE FROM Plugin WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert
    suspend fun insert(plugin: Plugin)

    @Update
    suspend fun update(plugin: Plugin)

    @Query("UPDATE Plugin SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("SELECT * FROM parametervalue WHERE pluginId = :id")
    suspend fun getParameterValues(id: String): List<ParameterValue>?

    @Query("SELECT value FROM ParameterValue WHERE pluginId = :pluginId AND paramName = :paramName")
    suspend fun getParameterValue(pluginId: String, paramName: String): String?

    @Query("INSERT INTO ParameterValue (pluginId, paramName, value) VALUES (:pluginId, :paramName, :value) ON CONFLICT(pluginId, paramName) DO UPDATE SET value = :value")
    suspend fun setParameterValue(pluginId: String, paramName: String, value: String)
}