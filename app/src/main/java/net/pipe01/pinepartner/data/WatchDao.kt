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

    @Query("SELECT sendNotifications FROM watch WHERE address = :address")
    suspend fun getSendNotifications(address: String): Boolean

    @Query("UPDATE watch SET autoConnect = :autoConnect WHERE address = :address")
    suspend fun setAutoConnect(address: String, autoConnect: Boolean)

    @Query("UPDATE watch SET sendNotifications = :sendNotifications WHERE address = :address")
    suspend fun setSendNotifications(address: String, sendNotifications: Boolean)
}