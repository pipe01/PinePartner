package net.pipe01.pinepartner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Watch(
    @PrimaryKey val address: String,
    val name: String,
    @ColumnInfo(defaultValue = "true") val autoConnect: Boolean,
    @ColumnInfo(defaultValue = "true") val reconnect: Boolean,
)
