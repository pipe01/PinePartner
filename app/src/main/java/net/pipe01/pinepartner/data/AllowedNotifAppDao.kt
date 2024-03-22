package net.pipe01.pinepartner.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface AllowedNotifAppDao {
    @Query("SELECT * FROM allowedNotifApp")
    suspend fun getAll(): List<AllowedNotifApp>

    @Query("SELECT EXISTS(SELECT * FROM allowedNotifApp WHERE packageName = :packageName)")
    suspend fun isAllowed(packageName: String): Boolean

    @Query("INSERT INTO allowedNotifApp (packageName) VALUES (:packageName)")
    suspend fun add(packageName: String)

    @Query("DELETE FROM allowedNotifApp WHERE packageName = :packageName")
    suspend fun remove(packageName: String)
}