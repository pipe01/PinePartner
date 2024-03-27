package net.pipe01.pinepartner.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch")
    suspend fun getAll(): List<Watch>

    @Query("SELECT * FROM watch WHERE address = :address")
    suspend fun getByAddress(address: String): Watch?

    @Query("SELECT COUNT(*) FROM watch WHERE address = :address")
    suspend fun countByAddress(address: String): Int

    @Query("INSERT INTO watch (address, name) VALUES (:address, :name)")
    suspend fun insert(address: String, name: String)

    @Query("DELETE FROM watch WHERE address = :address")
    suspend fun delete(address: String)

    @Query("UPDATE watch SET autoConnect = :autoConnect WHERE address = :address")
    suspend fun setAutoConnect(address: String, autoConnect: Boolean)

    @Query("SELECT reconnect FROM watch WHERE address = :address")
    suspend fun getReconnect(address: String): Boolean

    @Query("UPDATE watch SET reconnect = :reconnect WHERE address = :address")
    suspend fun setReconnect(address: String, reconnect: Boolean)
}