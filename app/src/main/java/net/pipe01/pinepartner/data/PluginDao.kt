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
}